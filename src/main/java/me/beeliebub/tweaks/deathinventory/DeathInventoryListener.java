package me.beeliebub.tweaks.deathinventory;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DeathInventoryListener implements Listener, CommandExecutor, TabCompleter {

    private static final int GUI_SIZE = 54;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private final DeathInventoryManager manager;

    // Tracks open death-inventory GUIs: viewer UUID -> context.
    private final Map<UUID, GuiContext> openGuis = new HashMap<>();

    private record GuiContext(UUID targetUuid, String targetName, File file) {}

    public DeathInventoryListener(JavaPlugin plugin, DeathInventoryManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ─── Event: capture inventory on death ───────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ItemStack[] contents = player.getInventory().getContents();
        // Copy so async save is safe from subsequent inventory mutations.
        ItemStack[] snapshot = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            snapshot[i] = contents[i] != null ? contents[i].clone() : null;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> manager.saveInventory(player.getUniqueId(), snapshot));
    }

    // ─── GUI interaction ──────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        GuiContext ctx = openGuis.get(viewer.getUniqueId());
        if (ctx == null) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;
        // Give the clicked item to the admin.
        viewer.getInventory().addItem(event.getCurrentItem().clone());
        viewer.sendMessage(Messages.deathInventoryItemGiven());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openGuis.remove(event.getPlayer().getUniqueId());
    }

    // ─── Command: /deathinventory ─────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_DEATH_INVENTORY)) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender, label);
            return true;
        }

        String targetName = args[0];
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        if (!offlineTarget.hasPlayedBefore()) {
            sender.sendMessage(Messages.deathInventoryPlayerHasNotPlayed(targetName));
            return true;
        }
        UUID targetUuid = offlineTarget.getUniqueId();

        if (args[1].equalsIgnoreCase("list")) {
            handleList(sender, targetName, targetUuid);
            return true;
        }

        String id = args[2 <= args.length ? 1 : 0]; // args[1] is the ID
        boolean restore = args.length >= 3 && args[2].equalsIgnoreCase("restore");
        handleView(sender, targetName, targetUuid, id, restore);
        return true;
    }

    private void handleList(CommandSender sender, String targetName, UUID targetUuid) {
        List<File> files = manager.listInventories(targetUuid);
        if (files.isEmpty()) {
            sender.sendMessage(Messages.deathInventoryNoneFound(targetName));
            return;
        }
        sender.sendMessage(Messages.deathInventoryListHeader(targetName));
        for (File file : files) {
            String stem = stem(file);
            String date;
            try {
                date = DATE_FORMAT.format(new Date(Long.parseLong(stem)));
            } catch (NumberFormatException e) {
                date = stem;
            }
            sender.sendMessage(Messages.deathInventoryListEntry(stem, date));
        }
    }

    private void handleView(CommandSender sender, String targetName, UUID targetUuid,
                             String id, boolean restore) {
        File file = manager.getFile(targetUuid, id);
        if (!file.exists()) {
            sender.sendMessage(Messages.deathInventoryNotFound(id, targetName));
            return;
        }

        ItemStack[] items = manager.loadInventory(file);

        if (restore) {
            Player target = Bukkit.getPlayer(targetUuid);
            if (target == null) {
                sender.sendMessage(Messages.deathInventoryRestoreTargetOffline(targetName));
                return;
            }
            for (int i = 0; i < Math.min(items.length, target.getInventory().getSize()); i++) {
                if (items[i] != null) {
                    target.getInventory().setItem(i, items[i].clone());
                }
            }
            sender.sendMessage(Messages.deathInventoryRestored(targetName));
            target.sendMessage(Messages.deathInventoryRestoreNotice());
            String actorName = sender instanceof Player player ? player.getName() : null;
            UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
            ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
            if (eventLog != null) eventLog.log(LoggingPaths.DEATHINVENTORY_RESTORED, () ->
                    "[DeathInventory] " + ConsoleEventLog.actorLabel(actorName, actorId)
                            + " restored " + id + " for " + targetName);
            return;
        }

        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Messages.deathInventoryGuiRequiresPlayer());
            return;
        }

        openGui(viewer, targetName, targetUuid, file, items);
    }

    private void openGui(Player viewer, String targetName, UUID targetUuid, File file, ItemStack[] items) {
        String stem = stem(file);
        String dateLabel;
        try {
            dateLabel = DATE_FORMAT.format(new Date(Long.parseLong(stem)));
        } catch (NumberFormatException e) {
            dateLabel = stem;
        }
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
                Messages.deathInventoryGuiTitle(targetName, dateLabel));
        for (int i = 0; i < Math.min(items.length, GUI_SIZE); i++) {
            inv.setItem(i, items[i]);
        }
        openGuis.put(viewer.getUniqueId(), new GuiContext(targetUuid, targetName, file));
        viewer.openInventory(inv);
    }

    // ─── Tab completion ───────────────────────────────────────────────────────

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_DEATH_INVENTORY)) return List.of();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) names.add(p.getName());
            }
            return names;
        }

        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            options.add("list");
            // Try to resolve the player and suggest their record IDs.
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target.hasPlayedBefore()) {
                for (File f : manager.listInventories(target.getUniqueId())) {
                    options.add(stem(f));
                }
            }
            return options;
        }

        if (args.length == 3 && !args[1].equalsIgnoreCase("list")) {
            return List.of("restore");
        }

        return List.of();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String stem(File file) {
        String name = file.getName();
        return name.endsWith(".yml") ? name.substring(0, name.length() - 4) : name;
    }

    private static void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Messages.deathInventoryUsage(label));
    }
}
