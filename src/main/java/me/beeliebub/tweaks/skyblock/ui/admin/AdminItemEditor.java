package me.beeliebub.tweaks.skyblock.ui.admin;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Copy-only item editor. The player inventory is never used as the editor backing store.
 * Permission and world checks are repeated for every interaction and successful close.
 */
public final class AdminItemEditor implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public AdminItemEditor(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory open(Player player, Component title, List<ItemStack> initial, int capacity,
                          Predicate<Player> access, Consumer<List<ItemStack>> onSave) {
        if (player == null || access == null || !access.test(player)) return null;
        int size = normalizeSize(capacity);
        List<ItemStack> values = initial == null ? List.of() : initial;
        if (values.size() > size) throw new IllegalArgumentException("Too many items for editor");
        Session session = new Session(player, access, onSave);
        Inventory inventory = Bukkit.createInventory(session, size, title == null ? Component.text("Items") : title);
        session.inventory = inventory;
        for (int index = 0; index < values.size(); index++) {
            ItemStack item = values.get(index);
            if (item != null && !item.getType().isAir()) inventory.setItem(index, item.clone());
        }
        Session previous = sessions.put(player.getUniqueId(), session);
        if (previous != null) previous.discard();
        player.openInventory(inventory);
        return inventory;
    }

    public void discard(Player player) {
        if (player != null) {
            Session session = sessions.remove(player.getUniqueId());
            if (session != null) session.discard();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Session session = session(event.getWhoClicked(), event.getView().getTopInventory());
        if (session == null) return;
        Player player = (Player) event.getWhoClicked();
        if (!session.access.test(player)) {
            event.setCancelled(true);
            discardAndClose(player, session);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT
                || event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.DROP
                || event.getClick() == ClickType.CONTROL_DROP || event.getClick() == ClickType.DOUBLE_CLICK
                || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getClick() == ClickType.WINDOW_BORDER_LEFT
                || event.getClick() == ClickType.WINDOW_BORDER_RIGHT) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlot() >= topSize) {
            // Bottom-inventory clicks are a temporary source for the editor only. The
            // snapshot is restored on close, so the admin's own inventory remains intact.
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Session session = session(event.getWhoClicked(), event.getView().getTopInventory());
        if (session == null) return;
        Player player = (Player) event.getWhoClicked();
        int topSize = event.getView().getTopInventory().getSize();
        if (!session.access.test(player)
                || event.getRawSlots().stream().anyMatch(slot -> slot >= topSize)) {
            event.setCancelled(true);
            if (!session.access.test(player)) discardAndClose(player, session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Session session = session(event.getPlayer(), event.getInventory());
        if (session == null || session.discarded || !sessions.remove(session.playerId, session)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        List<ItemStack> contents = copyContents(event.getInventory());
        session.restorePlayerInventory(player);
        if (!session.access.test(player)) return;
        try {
            if (session.onSave != null) session.onSave.accept(contents);
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Skyblock item editor save failed", error);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        discard(event.getPlayer());
    }

    public void shutdown() {
        sessions.values().forEach(Session::discard);
        sessions.clear();
    }

    public static List<ItemStack> copyContents(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>();
        if (inventory == null) return result;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) result.add(item.clone());
        }
        return List.copyOf(result);
    }

    private Session session(org.bukkit.entity.HumanEntity entity, Inventory inventory) {
        if (!(entity instanceof Player player) || inventory == null
                || !(inventory.getHolder() instanceof Session holder)) return null;
        return sessions.get(player.getUniqueId()) == holder ? holder : null;
    }

    private void discardAndClose(Player player, Session session) {
        if (sessions.remove(player.getUniqueId(), session)) session.discard();
        if (player.getOpenInventory().getTopInventory().getHolder() == session) player.closeInventory();
    }

    private static int normalizeSize(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Editor capacity must be positive");
        int size = Math.min(54, Math.max(9, capacity / 9 * 9));
        if (size < capacity && capacity > 54) throw new IllegalArgumentException("Editor capacity exceeds 54");
        return size;
    }

    private static final class Session implements InventoryHolder {
        private final UUID playerId;
        private final Predicate<Player> access;
        private final Consumer<List<ItemStack>> onSave;
        private final ItemStack[] storageContents;
        private final ItemStack[] armorContents;
        private final ItemStack offhand;
        private Inventory inventory;
        private boolean discarded;

        private Session(Player player, Predicate<Player> access, Consumer<List<ItemStack>> onSave) {
            this.playerId = player.getUniqueId();
            this.access = access;
            this.onSave = onSave;
            PlayerInventory playerInventory = player.getInventory();
            this.storageContents = cloneItems(playerInventory.getContents());
            this.armorContents = cloneItems(playerInventory.getArmorContents());
            this.offhand = cloneItem(playerInventory.getItemInOffHand());
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void discard() {
            discarded = true;
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) restorePlayerInventory(player);
        }

        private void restorePlayerInventory(Player player) {
            PlayerInventory playerInventory = player.getInventory();
            playerInventory.setContents(cloneItems(storageContents));
            playerInventory.setArmorContents(cloneItems(armorContents));
            playerInventory.setItemInOffHand(cloneItem(offhand));
        }

        private static ItemStack[] cloneItems(ItemStack[] source) {
            if (source == null) return new ItemStack[0];
            ItemStack[] copy = new ItemStack[source.length];
            for (int index = 0; index < source.length; index++) copy[index] = cloneItem(source[index]);
            return copy;
        }

        private static ItemStack cloneItem(ItemStack item) {
            return item == null ? null : item.clone();
        }
    }
}
