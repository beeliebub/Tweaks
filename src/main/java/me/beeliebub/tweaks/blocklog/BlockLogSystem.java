package me.beeliebub.tweaks.blocklog;

import me.beeliebub.tweaks.blocklog.BlockLogData.ChestLogEntry;
import me.beeliebub.tweaks.blocklog.BlockLogData.ChunkLogStore;
import me.beeliebub.tweaks.blocklog.BlockLogData.LogAction;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// All behavior for the block-log subsystem: the manager API, the open/close diff listener,
// the chunk-load prune listener, and the /logs command + punch-to-view inspector.
public final class BlockLogSystem implements CommandExecutor, Listener {

    static final long RETENTION_MILLIS = TimeUnit.DAYS.toMillis(30);
    private static final int ENTRIES_PER_PAGE = 10;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChunkLogStore store;
    // (player UUID, anchor key) -> snapshot. See diff strategy notes in blocklog/CLAUDE.md.
    private final Map<SnapshotKey, ItemStack[]> snapshots = new HashMap<>();
    private final Set<UUID> inspectors = new HashSet<>();

    public BlockLogSystem(Plugin plugin) {
        this.store = new ChunkLogStore(plugin);
    }

    // ============================================================
    // Manager API (anchor / loggable / read / append / prune)
    // ============================================================

    public boolean isLoggable(Material material) {
        return switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL -> true;
            default -> false;
        };
    }

    public boolean isLoggable(Block block) {
        return block != null && isLoggable(block.getType());
    }

    public Block anchor(Block block) {
        if (block == null) return null;
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) return block;

        Inventory inv = chest.getInventory();
        if (inv instanceof DoubleChestInventory doubleInv) {
            DoubleChest doubleChest = (DoubleChest) doubleInv.getHolder();
            if (doubleChest != null) {
                InventoryHolder left = doubleChest.getLeftSide();
                if (left instanceof Chest leftChest) {
                    return leftChest.getBlock();
                }
            }
        }
        return block;
    }

    public @Nullable Block anchorOf(Inventory inv) {
        if (inv == null) return null;
        InventoryHolder holder = inv.getHolder();
        if (holder == null) return null;

        if (holder instanceof DoubleChest doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof Chest leftChest) {
                Block b = leftChest.getBlock();
                return isLoggable(b) ? b : null;
            }
            return null;
        }
        if (holder instanceof BlockInventoryHolder blockHolder) {
            Block b = blockHolder.getBlock();
            return isLoggable(b) ? b : null;
        }
        return null;
    }

    public List<ChestLogEntry> read(Block anchor) {
        return store.read(anchor);
    }

    public void appendAll(Block anchor, List<ChestLogEntry> entries) {
        if (entries.isEmpty()) return;
        store.appendAll(anchor, entries);
    }

    public int pruneChunk(org.bukkit.Chunk chunk, long cutoffMillis) {
        return store.pruneChunk(chunk, cutoffMillis);
    }

    // ============================================================
    // Open/close diff listener
    // ============================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        Block anchor = anchorOf(inv);
        if (anchor == null) return;
        snapshots.put(SnapshotKey.of(player.getUniqueId(), anchor), copy(inv.getContents()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player)) return;
        Inventory inv = event.getInventory();
        Block anchor = anchorOf(inv);
        if (anchor == null) return;

        SnapshotKey key = SnapshotKey.of(player.getUniqueId(), anchor);
        ItemStack[] before = snapshots.remove(key);
        if (before == null) return;

        ItemStack[] after = inv.getContents();
        List<ChestLogEntry> entries = diff(before, after, player);
        if (entries.isEmpty()) return;
        appendAll(anchor, entries);
    }

    private List<ChestLogEntry> diff(ItemStack[] before, ItemStack[] after, Player player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        List<ChestLogEntry> out = new ArrayList<>();

        int slots = Math.max(before.length, after.length);
        for (int i = 0; i < slots; i++) {
            ItemStack a = i < before.length ? before[i] : null;
            ItemStack b = i < after.length ? after[i] : null;

            if (isEmpty(a) && isEmpty(b)) continue;
            if (!isEmpty(a) && !isEmpty(b) && a.equals(b)) continue;

            if (!isEmpty(a) && !isEmpty(b) && a.isSimilar(b)) {
                int delta = b.getAmount() - a.getAmount();
                if (delta == 0) continue;
                ItemStack template = a.clone();
                template.setAmount(Math.abs(delta));
                LogAction action = delta > 0 ? LogAction.ADD : LogAction.REMOVE;
                out.add(new ChestLogEntry(now, action, uuid, name, template));
            } else {
                if (!isEmpty(a)) {
                    out.add(new ChestLogEntry(now, LogAction.REMOVE, uuid, name, a.clone()));
                }
                if (!isEmpty(b)) {
                    out.add(new ChestLogEntry(now, LogAction.ADD, uuid, name, b.clone()));
                }
            }
        }
        return out;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static ItemStack[] copy(ItemStack[] src) {
        ItemStack[] dst = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i] == null ? null : src[i].clone();
        }
        return dst;
    }

    private record SnapshotKey(UUID player, UUID world, int x, int y, int z) {
        static SnapshotKey of(UUID player, Block anchor) {
            return new SnapshotKey(player, anchor.getWorld().getUID(), anchor.getX(), anchor.getY(), anchor.getZ());
        }
    }

    // ============================================================
    // Chunk-load prune listener
    // ============================================================

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        pruneChunk(event.getChunk(), cutoff);
    }

    // ============================================================
    // /logs command + punch listener
    // ============================================================

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /logs.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(Permissions.ADMIN_LOGS)) {
            player.sendMessage(Component.text("You don't have permission to use /logs.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            toggleInspector(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("view") && args.length == 6) {
            handleViewSubcommand(player, args);
            return true;
        }

        player.sendMessage(Component.text("Usage: /logs", NamedTextColor.YELLOW));
        return true;
    }

    private void toggleInspector(Player player) {
        if (inspectors.remove(player.getUniqueId())) {
            player.sendMessage(Component.text("[BlockLog] ", NamedTextColor.DARK_AQUA)
                    .append(Component.text("Inspector mode disabled.", NamedTextColor.GRAY)));
        } else {
            inspectors.add(player.getUniqueId());
            player.sendMessage(Component.text("[BlockLog] ", NamedTextColor.DARK_AQUA)
                    .append(Component.text("Inspector mode enabled. ", NamedTextColor.YELLOW))
                    .append(Component.text("Punch a chest to view its log.", NamedTextColor.GRAY)));
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    private void handleViewSubcommand(Player player, String[] args) {
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            player.sendMessage(Component.text("Unknown world: " + args[1], NamedTextColor.RED));
            return;
        }
        int x, y, z, page;
        try {
            x = Integer.parseInt(args[2]);
            y = Integer.parseInt(args[3]);
            z = Integer.parseInt(args[4]);
            page = Math.max(1, Integer.parseInt(args[5]));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid coordinates or page.", NamedTextColor.RED));
            return;
        }
        Block block = world.getBlockAt(x, y, z);
        if (!isLoggable(block)) {
            player.sendMessage(Component.text("That block no longer holds a chest log.", NamedTextColor.RED));
            return;
        }
        sendLogPage(player, anchor(block), page);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!inspectors.contains(player.getUniqueId())) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null || !isLoggable(clicked)) return;
        if (!player.hasPermission(Permissions.ADMIN_LOGS)) {
            inspectors.remove(player.getUniqueId());
            return;
        }

        event.setCancelled(true);
        sendLogPage(player, anchor(clicked), 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inspectors.remove(event.getPlayer().getUniqueId());
    }

    private void sendLogPage(Player player, Block anchor, int page) {
        List<ChestLogEntry> entries = read(anchor);
        Location loc = anchor.getLocation();
        Component header = Component.text("─── ", NamedTextColor.DARK_GRAY)
                .append(Component.text("BlockLog ", NamedTextColor.DARK_AQUA, TextDecoration.BOLD))
                .append(Component.text("at " + formatLocation(loc), NamedTextColor.GRAY))
                .append(Component.text(" ───", NamedTextColor.DARK_GRAY));

        if (entries.isEmpty()) {
            player.sendMessage(Component.empty());
            player.sendMessage(header);
            player.sendMessage(Component.text("No log entries for this chest.", NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }

        int totalPages = (entries.size() + ENTRIES_PER_PAGE - 1) / ENTRIES_PER_PAGE;
        int clamped = Math.min(page, totalPages);
        int startIdxFromNewest = (clamped - 1) * ENTRIES_PER_PAGE;

        player.sendMessage(Component.empty());
        player.sendMessage(header);
        player.sendMessage(Component.text("Page " + clamped + "/" + totalPages
                + "  (" + entries.size() + " entries)", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());

        for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
            int idxFromNewest = startIdxFromNewest + i;
            if (idxFromNewest >= entries.size()) break;
            int actualIdx = entries.size() - 1 - idxFromNewest;
            player.sendMessage(formatEntry(entries.get(actualIdx)));
        }

        player.sendMessage(Component.empty());
        player.sendMessage(buildPageNav(loc, clamped, totalPages));
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.0f);
    }

    private Component formatEntry(ChestLogEntry entry) {
        String when = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.timestamp()), ZoneId.systemDefault())
                .format(TIME_FMT);

        ItemStack item = entry.item();
        Component itemDisplay = item.displayName()
                .hoverEvent(item.asHoverEvent())
                .colorIfAbsent(NamedTextColor.WHITE);

        boolean add = entry.action() == LogAction.ADD;
        Component actionTag = Component.text(add ? "+" : "-", add ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD);
        Component amount = Component.text(item.getAmount() + "x", NamedTextColor.GOLD);
        Component playerName = Component.text(entry.playerName(), NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text(entry.playerUuid().toString(), NamedTextColor.GRAY)));
        Component time = Component.text(when, NamedTextColor.DARK_GRAY);

        return Component.text(" ")
                .append(actionTag)
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(time)
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(playerName)
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(amount)
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(itemDisplay);
    }

    private Component buildPageNav(Location loc, int page, int totalPages) {
        Component prev;
        Component next;

        if (page > 1) {
            prev = Component.text("[<- Prev]", NamedTextColor.YELLOW)
                    .hoverEvent(HoverEvent.showText(Component.text("Page " + (page - 1), NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand(viewCommand(loc, page - 1)));
        } else {
            prev = Component.text("[<- Prev]", NamedTextColor.DARK_GRAY);
        }

        if (page < totalPages) {
            next = Component.text("[Next ->]", NamedTextColor.YELLOW)
                    .hoverEvent(HoverEvent.showText(Component.text("Page " + (page + 1), NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand(viewCommand(loc, page + 1)));
        } else {
            next = Component.text("[Next ->]", NamedTextColor.DARK_GRAY);
        }

        return Component.text("  ").append(prev)
                .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                .append(next);
    }

    private String viewCommand(Location loc, int page) {
        return "/logs view " + loc.getWorld().getName()
                + " " + loc.getBlockX()
                + " " + loc.getBlockY()
                + " " + loc.getBlockZ()
                + " " + page;
    }

    private String formatLocation(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
