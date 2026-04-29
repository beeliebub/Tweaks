package me.beeliebub.tweaks.combos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// /invsee <target> — opens a 45-slot mirror GUI of an online target's inventory and propagates
// edits back to the target on the same tick. Force-closes when the target switches profiles,
// dies, or quits so SeparatorListener's profile swap can't race the mirror.
public class InvSeeCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String PERMISSION = "tweaks.admin.invsee";
    private static final int GUI_SIZE = 45;

    // GUI slot -> PlayerInventory slot. -1 means decorative filler (clicks cancelled).
    // 0-8   : hotbar      (PlayerInventory 0-8)
    // 9-35  : main        (PlayerInventory 9-35)
    // 36    : helmet      (PlayerInventory 39)
    // 37    : chestplate  (PlayerInventory 38)
    // 38    : leggings    (PlayerInventory 37)
    // 39    : boots       (PlayerInventory 36)
    // 40    : offhand     (PlayerInventory 40)
    // 41-44 : filler glass panes
    private static final int[] GUI_TO_PLAYER = new int[GUI_SIZE];
    static {
        for (int i = 0; i < GUI_SIZE; i++) GUI_TO_PLAYER[i] = -1;
        for (int i = 0; i <= 35; i++) GUI_TO_PLAYER[i] = i;
        GUI_TO_PLAYER[36] = 39;
        GUI_TO_PLAYER[37] = 38;
        GUI_TO_PLAYER[38] = 37;
        GUI_TO_PLAYER[39] = 36;
        GUI_TO_PLAYER[40] = 40;
    }

    private final JavaPlugin plugin;
    // Keyed by viewer UUID. Bukkit events are single-threaded; the concurrent map is defensive only.
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public InvSeeCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text("Only players can use /invsee.", NamedTextColor.RED));
            return true;
        }
        if (!viewer.hasPermission(PERMISSION)) {
            viewer.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            viewer.sendMessage(Component.text("Usage: /invsee <player>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            viewer.sendMessage(Component.text("Player '" + args[0] + "' is not online.", NamedTextColor.RED));
            return true;
        }
        if (target.getUniqueId().equals(viewer.getUniqueId())) {
            viewer.sendMessage(Component.text("You can't /invsee yourself.", NamedTextColor.RED));
            return true;
        }

        openSession(viewer, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .filter(name -> !sender.getName().equalsIgnoreCase(name))
                .collect(Collectors.toList());
    }

    private void openSession(Player viewer, Player target) {
        Holder holder = new Holder(target.getUniqueId());
        Inventory gui = Bukkit.createInventory(holder, GUI_SIZE,
                Component.text(target.getName() + "'s Inventory", NamedTextColor.GOLD));
        holder.bind(gui);
        renderFiller(gui);
        renderTargetInto(gui, target);

        Session session = new Session(viewer.getUniqueId(), target.getUniqueId(), gui);
        // Insert before openInventory so the close event for any previous /invsee GUI on this
        // viewer (which fires synchronously inside openInventory) doesn't race the new entry.
        // onClose only removes a session whose gui equals the closing inventory, so the prior
        // session's close is still cleaned up correctly.
        sessions.put(viewer.getUniqueId(), session);
        viewer.openInventory(gui);
    }

    private void renderTargetInto(Inventory gui, Player target) {
        PlayerInventory inv = target.getInventory();
        for (int guiSlot = 0; guiSlot < GUI_SIZE; guiSlot++) {
            int playerSlot = GUI_TO_PLAYER[guiSlot];
            if (playerSlot < 0) continue;
            ItemStack item = inv.getItem(playerSlot);
            gui.setItem(guiSlot, item == null ? null : item.clone());
        }
    }

    private void renderFiller(Inventory gui) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            pane.setItemMeta(meta);
        }
        for (int i = 41; i <= 44; i++) {
            gui.setItem(i, pane);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) return;
        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        Session session = sessions.get(viewer.getUniqueId());
        if (session == null || !session.gui.equals(top)) return;

        Player target = Bukkit.getPlayer(session.target);
        if (target == null || !target.isOnline()) {
            event.setCancelled(true);
            viewer.sendMessage(Component.text("Target is no longer online. Closing.", NamedTextColor.RED));
            Bukkit.getScheduler().runTask(plugin, (Runnable) viewer::closeInventory);
            return;
        }

        // Cancel direct interactions with the decorative filler region.
        Inventory clicked = event.getClickedInventory();
        if (clicked != null && clicked.equals(top)) {
            int rawSlot = event.getRawSlot();
            if (rawSlot >= 41 && rawSlot <= 44) {
                event.setCancelled(true);
                return;
            }
        }

        // Run on the next tick so we read the inventory after Bukkit has applied the click.
        Bukkit.getScheduler().runTask(plugin, () -> reconcile(session, viewer));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) return;
        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        Session session = sessions.get(viewer.getUniqueId());
        if (session == null || !session.gui.equals(top)) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 41 && rawSlot <= 44) {
                event.setCancelled(true);
                return;
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> reconcile(session, viewer));
    }

    // Push GUI changes into the target and rescue any stray items dropped into filler slots
    // (which can happen via shift-click overflow). Skips work if the session was force-closed
    // between the original click and this scheduled tick.
    private void reconcile(Session session, Player viewer) {
        if (sessions.get(session.viewer) != session) return;

        Player target = Bukkit.getPlayer(session.target);
        if (target == null || !target.isOnline()) return;

        PlayerInventory inv = target.getInventory();
        for (int guiSlot = 0; guiSlot <= 40; guiSlot++) {
            int playerSlot = GUI_TO_PLAYER[guiSlot];
            if (playerSlot < 0) continue;
            ItemStack guiItem = session.gui.getItem(guiSlot);
            ItemStack current = inv.getItem(playerSlot);
            if (!itemsEqual(guiItem, current)) {
                inv.setItem(playerSlot, guiItem == null ? null : guiItem.clone());
            }
        }

        // If shift-click overflow placed an admin item into a filler slot, give it back to
        // the admin instead of letting it sit in a slot that doesn't sync to the target.
        for (int i = 41; i <= 44; i++) {
            ItemStack guiItem = session.gui.getItem(i);
            if (guiItem != null && guiItem.getType() != Material.GRAY_STAINED_GLASS_PANE
                    && guiItem.getType() != Material.AIR) {
                Map<Integer, ItemStack> overflow = viewer.getInventory().addItem(guiItem.clone());
                for (ItemStack drop : overflow.values()) {
                    viewer.getWorld().dropItemNaturally(viewer.getLocation(), drop);
                }
                session.gui.setItem(i, null);
            }
        }
        renderFiller(session.gui);
    }

    private static boolean itemsEqual(@Nullable ItemStack a, @Nullable ItemStack b) {
        boolean aEmpty = a == null || a.getType() == Material.AIR;
        boolean bEmpty = b == null || b.getType() == Material.AIR;
        if (aEmpty && bEmpty) return true;
        if (aEmpty != bEmpty) return false;
        return a.equals(b);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        if (!(event.getPlayer() instanceof Player viewer)) return;
        Session session = sessions.get(viewer.getUniqueId());
        // Only remove if this is the session's own GUI — opening a fresh /invsee on an existing
        // viewer fires the prior GUI's close and we must not strip the new entry.
        if (session != null && session.gui.equals(event.getView().getTopInventory())) {
            sessions.remove(viewer.getUniqueId());
        }
    }

    // LOWEST priority so we close the mirror before SeparatorListener swaps profile data
    // (PlayerQuit/PlayerChangedWorld at NORMAL, PlayerDeath at MONITOR).
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTargetQuit(PlayerQuitEvent event) {
        forceCloseSessionsTargeting(event.getPlayer().getUniqueId(),
                "InvSee closed: target quit.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTargetWorldChange(PlayerChangedWorldEvent event) {
        forceCloseSessionsTargeting(event.getPlayer().getUniqueId(),
                "InvSee closed: target switched profile.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTargetDeath(PlayerDeathEvent event) {
        forceCloseSessionsTargeting(event.getPlayer().getUniqueId(),
                "InvSee closed: target died.");
    }

    private void forceCloseSessionsTargeting(UUID targetUUID, String reason) {
        // Snapshot first — closeInventory() fires InventoryCloseEvent synchronously, which
        // mutates the sessions map.
        List<Session> toClose = new ArrayList<>();
        for (Session s : sessions.values()) {
            if (s.target.equals(targetUUID)) toClose.add(s);
        }
        for (Session s : toClose) {
            Player viewer = Bukkit.getPlayer(s.viewer);
            if (viewer != null && viewer.isOnline()) {
                viewer.sendMessage(Component.text(reason, NamedTextColor.YELLOW));
                viewer.closeInventory();
            } else {
                sessions.remove(s.viewer);
            }
        }
    }

    private static final class Session {
        final UUID viewer;
        final UUID target;
        final Inventory gui;

        Session(UUID viewer, UUID target, Inventory gui) {
            this.viewer = viewer;
            this.target = target;
            this.gui = gui;
        }
    }

    private static final class Holder implements InventoryHolder {
        private final UUID target;
        private Inventory inventory;

        Holder(UUID target) { this.target = target; }
        void bind(Inventory inv) { this.inventory = inv; }
        @Override @NotNull public Inventory getInventory() { return inventory; }
    }
}
