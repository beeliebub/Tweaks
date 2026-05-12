package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.UserPermissions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserPermissionsTest {

    private final UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void uuidIsReturnedFromGetter() {
        assertEquals(uuid, new UserPermissions(uuid).getUuid());
    }

    @Test
    void newUserHasNoGroupsOrPermissions() {
        UserPermissions u = new UserPermissions(uuid);
        assertTrue(u.getGroups().isEmpty());
        assertTrue(u.getPermissions().isEmpty());
    }

    @Test
    void addGroupLowercasesAndStores() {
        UserPermissions u = new UserPermissions(uuid);
        u.addGroup("Admin");
        assertTrue(u.hasGroup("admin"));
        assertTrue(u.hasGroup("ADMIN"));
    }

    @Test
    void addGroupTreatsNullAsNoOp() {
        UserPermissions u = new UserPermissions(uuid);
        u.addGroup(null);
        assertTrue(u.getGroups().isEmpty());
    }

    @Test
    void removeGroupLowercasesAndRemoves() {
        UserPermissions u = new UserPermissions(uuid);
        u.addGroup("admin");
        u.removeGroup("ADMIN");
        assertFalse(u.hasGroup("admin"));
    }

    @Test
    void removeGroupTreatsNullAsNoOp() {
        UserPermissions u = new UserPermissions(uuid);
        u.addGroup("admin");
        u.removeGroup(null);
        assertTrue(u.hasGroup("admin"));
    }

    @Test
    void hasGroupReturnsFalseForNullInput() {
        UserPermissions u = new UserPermissions(uuid);
        u.addGroup("admin");
        assertFalse(u.hasGroup(null));
    }

    @Test
    void addPermissionLowercasesAndDeduplicates() {
        UserPermissions u = new UserPermissions(uuid);
        u.addPermission("Tweaks.Fly");
        u.addPermission("tweaks.fly");
        assertEquals(1, u.getPermissions().size());
        assertTrue(u.hasDirectPermission("TWEAKS.FLY"));
    }

    @Test
    void removePermissionLowercases() {
        UserPermissions u = new UserPermissions(uuid);
        u.addPermission("tweaks.fly");
        u.removePermission("TWEAKS.FLY");
        assertFalse(u.hasDirectPermission("tweaks.fly"));
    }

    @Test
    void multipleGroupsCanBeAssigned() {
        UserPermissions u = new UserPermissions(uuid);
        u.addGroup("admin");
        u.addGroup("mod");
        u.addGroup("vip");
        assertEquals(3, u.getGroups().size());
    }
}
