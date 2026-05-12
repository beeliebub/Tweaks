package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.PermissionGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionGroupTest {

    @Test
    void nameIsImmutableAndReturnedFromGetter() {
        PermissionGroup g = new PermissionGroup("admin");
        assertEquals("admin", g.getName());
    }

    @Test
    void parentNameDefaultsToNull() {
        assertNull(new PermissionGroup("admin").getParentName());
    }

    @Test
    void parentNameIsRoundTripped() {
        PermissionGroup g = new PermissionGroup("admin");
        g.setParentName("default");
        assertEquals("default", g.getParentName());
    }

    @Test
    void newGroupHasNoPermissions() {
        assertTrue(new PermissionGroup("admin").getPermissions().isEmpty());
    }

    @Test
    void addPermissionLowercasesInput() {
        PermissionGroup g = new PermissionGroup("admin");
        g.addPermission("Tweaks.Admin.Logs");
        assertTrue(g.hasDirectPermission("tweaks.admin.logs"));
        assertTrue(g.hasDirectPermission("TWEAKS.ADMIN.LOGS"));
    }

    @Test
    void duplicateAddsAreDeduplicated() {
        PermissionGroup g = new PermissionGroup("admin");
        g.addPermission("a.b");
        g.addPermission("a.b");
        g.addPermission("A.B");
        assertEquals(1, g.getPermissions().size());
    }

    @Test
    void removePermissionLowercasesInput() {
        PermissionGroup g = new PermissionGroup("admin");
        g.addPermission("a.b");
        g.removePermission("A.B");
        assertFalse(g.hasDirectPermission("a.b"));
    }

    @Test
    void hasDirectPermissionReturnsFalseForUnknown() {
        assertFalse(new PermissionGroup("admin").hasDirectPermission("unknown"));
    }
}
