package me.beeliebub.tweaks.blocklog;

import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// /logs — toggles "inspector mode" for an admin. While inspector mode is on, the next left-click
// on a loggable container (chest/trapped chest/barrel) is intercepted and the chest's log is
// displayed in chat as a paginated list. Hover any item name to see its full ItemStack details.
//
// Pagination: stateless. The [Prev]/[Next] buttons re-run /logs view <world> <x> <y> <z> <page>,
// which re-reads the chest's log each time. This keeps the system simple and avoids stale caches.
public final class LogsCommand implements CommandExecutor, Listener {

    private static final int ENTRIES_PER_PAGE = 10;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChestLogManager manager;
    private final Set<UUID> inspectors = new HashSet<>();

    public LogsCommand(ChestLogManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
        if (!manager.isLoggable(block)) {
            player.sendMessage(Component.text("That block no longer holds a chest log.", NamedTextColor.RED));
            return;
        }
        sendLogPage(player, manager.anchor(block), page);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!inspectors.contains(player.getUniqueId())) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null || !manager.isLoggable(clicked)) return;
        if (!player.hasPermission(Permissions.ADMIN_LOGS)) {
            inspectors.remove(player.getUniqueId());
            return;
        }

        event.setCancelled(true);
        sendLogPage(player, manager.anchor(clicked), 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inspectors.remove(event.getPlayer().getUniqueId());
    }

    private void sendLogPage(Player player, Block anchor, int page) {
        List<ChestLogEntry> entries = manager.read(anchor);
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

        // Newest first.
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