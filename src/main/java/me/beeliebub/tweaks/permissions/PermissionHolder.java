package me.beeliebub.tweaks.permissions;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Holder for /perms GUI inventories. Provides:
//  - the menu kind (so the listener can dispatch by enum, not by parsing titles)
//  - context (group name and/or user UUID, depending on the kind)
//  - the current page (for paginated lists)
//  - explicit slot → payload maps populated by PermissionGUI when items are placed
//
// Click routing is data-driven: the listener checks slot maps first, then falls
// back to a small set of per-kind switch arms for static buttons (back, etc.).
public final class PermissionHolder implements InventoryHolder {

    public enum MenuKind {
        MAIN,
        GROUPS_LIST,
        USERS_LIST,
        GROUP_HUB,
        GROUP_PERMS,
        GROUP_MEMBERS_TOGGLE,
        GROUP_INHERITANCE_PICKER,
        USER_HUB,
        USER_PERMS,
        USER_GROUP_PICKER
    }

    private final MenuKind kind;
    private final String groupName;
    private final UUID userUuid;
    private final int page;

    private Inventory inventory;
    private final Map<Integer, String> slotToString = new HashMap<>();
    private final Map<Integer, UUID> slotToUuid = new HashMap<>();
    private final Map<Integer, String> slotToAction = new HashMap<>();

    public PermissionHolder(MenuKind kind, @Nullable String groupName, @Nullable UUID userUuid, int page) {
        this.kind = kind;
        this.groupName = groupName;
        this.userUuid = userUuid;
        this.page = page;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    public MenuKind kind() {
        return kind;
    }

    public @Nullable String groupName() {
        return groupName;
    }

    public @Nullable UUID userUuid() {
        return userUuid;
    }

    public int page() {
        return page;
    }

    public void mapString(int slot, String value) {
        slotToString.put(slot, value);
    }

    public void mapUuid(int slot, UUID value) {
        slotToUuid.put(slot, value);
    }

    public void mapAction(int slot, String action) {
        slotToAction.put(slot, action);
    }

    public @Nullable String stringAt(int slot) {
        return slotToString.get(slot);
    }

    public @Nullable UUID uuidAt(int slot) {
        return slotToUuid.get(slot);
    }

    public @Nullable String actionAt(int slot) {
        return slotToAction.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}