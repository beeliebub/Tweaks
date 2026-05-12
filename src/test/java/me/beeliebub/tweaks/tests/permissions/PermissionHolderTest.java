package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.PermissionHolder;
import me.beeliebub.tweaks.permissions.PermissionHolder.MenuKind;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PermissionHolderTest {

    private final UUID uuid = UUID.fromString("00000000-1111-2222-3333-444444444444");

    @Test
    void constructorRetainsAllFields() {
        PermissionHolder h = new PermissionHolder(MenuKind.GROUP_HUB, "admin", uuid, 3);
        assertEquals(MenuKind.GROUP_HUB, h.kind());
        assertEquals("admin", h.groupName());
        assertEquals(uuid, h.userUuid());
        assertEquals(3, h.page());
    }

    @Test
    void groupNameAndUuidAreNullable() {
        PermissionHolder h = new PermissionHolder(MenuKind.MAIN, null, null, 0);
        assertNull(h.groupName());
        assertNull(h.userUuid());
    }

    @Test
    void attachThenGetInventoryReturnsAttachedInstance() {
        Inventory inv = mock(Inventory.class);
        PermissionHolder h = new PermissionHolder(MenuKind.MAIN, null, null, 0);
        h.attach(inv);
        assertSame(inv, h.getInventory());
    }

    @Test
    void mapStringIsRetrievableByStringAt() {
        PermissionHolder h = new PermissionHolder(MenuKind.MAIN, null, null, 0);
        h.mapString(7, "hello");
        assertEquals("hello", h.stringAt(7));
        assertNull(h.stringAt(8));
    }

    @Test
    void mapUuidIsRetrievableByUuidAt() {
        PermissionHolder h = new PermissionHolder(MenuKind.MAIN, null, null, 0);
        h.mapUuid(2, uuid);
        assertEquals(uuid, h.uuidAt(2));
        assertNull(h.uuidAt(3));
    }

    @Test
    void mapActionIsRetrievableByActionAt() {
        PermissionHolder h = new PermissionHolder(MenuKind.MAIN, null, null, 0);
        h.mapAction(11, "delete");
        assertEquals("delete", h.actionAt(11));
        assertNull(h.actionAt(12));
    }

    @Test
    void slotMapsAreIndependent() {
        PermissionHolder h = new PermissionHolder(MenuKind.MAIN, null, null, 0);
        h.mapString(5, "s");
        h.mapUuid(5, uuid);
        h.mapAction(5, "a");
        assertEquals("s", h.stringAt(5));
        assertEquals(uuid, h.uuidAt(5));
        assertEquals("a", h.actionAt(5));
    }

    @Test
    void menuKindEnumDeclaresAllScreensReferencedByGuiSpec() {
        // The CLAUDE.md doc enumerates the screens; if a refactor drops one of these,
        // this test catches it before the GUI listener silently breaks.
        for (String name : new String[]{
                "MAIN", "GROUPS_LIST", "USERS_LIST",
                "GROUP_HUB", "GROUP_PERMS", "GROUP_MEMBERS_TOGGLE", "GROUP_INHERITANCE_PICKER",
                "USER_HUB", "USER_PERMS", "USER_GROUP_PICKER"
        }) {
            assertDoesNotThrow(() -> MenuKind.valueOf(name),
                    "MenuKind." + name + " is referenced by the permissions GUI spec");
        }
    }
}
