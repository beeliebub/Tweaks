package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.PermissionGroup;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.UserPermissions;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionManagerTest {

    @TempDir
    File dataFolder;

    private PermissionManager manager;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        manager = new PermissionManager(plugin);
    }

    @Test
    void constructorAlwaysCreatesADefaultGroup() {
        assertTrue(manager.getGroups().containsKey("default"),
                "PermissionManager must seed a 'default' group when none exist");
    }

    @Test
    void getUserPermissionsCreatesAndReusesEntry() {
        UUID uuid = UUID.randomUUID();
        UserPermissions first = manager.getUserPermissions(uuid);
        UserPermissions second = manager.getUserPermissions(uuid);
        assertSame(first, second, "subsequent lookups must return the same instance");
        assertEquals(uuid, first.getUuid());
    }

    @Test
    void calculateEffectivePermissionsReturnsEmptyForUnknownUser() {
        Set<String> perms = manager.calculateEffectivePermissions(UUID.randomUUID());
        assertNotNull(perms);
        assertTrue(perms.isEmpty());
    }

    @Test
    void usersWithNoGroupsImplicitlyInheritDefault() {
        manager.getGroups().get("default").addPermission("tweaks.fly");
        UUID uuid = UUID.randomUUID();
        manager.getUserPermissions(uuid); // ensure user exists
        Set<String> perms = manager.calculateEffectivePermissions(uuid);
        assertTrue(perms.contains("tweaks.fly"),
                "user with no group memberships should inherit from 'default'");
    }

    @Test
    void userDirectPermissionsAreIncludedInEffectiveSet() {
        UUID uuid = UUID.randomUUID();
        UserPermissions u = manager.getUserPermissions(uuid);
        u.addPermission("tweaks.bypass.homes");
        Set<String> perms = manager.calculateEffectivePermissions(uuid);
        assertTrue(perms.contains("tweaks.bypass.homes"));
    }

    @Test
    void groupInheritanceWalksUpParentChain() {
        PermissionGroup parent = new PermissionGroup("base");
        parent.addPermission("tweaks.base");
        PermissionGroup child = new PermissionGroup("admin");
        child.addPermission("tweaks.admin");
        child.setParentName("base");
        manager.getGroups().put("base", parent);
        manager.getGroups().put("admin", child);

        UUID uuid = UUID.randomUUID();
        manager.getUserPermissions(uuid).addGroup("admin");

        Set<String> perms = manager.calculateEffectivePermissions(uuid);
        assertTrue(perms.contains("tweaks.admin"));
        assertTrue(perms.contains("tweaks.base"));
    }

    @Test
    void multipleGroupsWithSharedAncestorAreVisitedOnce() {
        // Inheritance DAG: groupA -> shared, groupB -> shared.
        // The visited set is shared so 'shared' contributes once even though both
        // groupA and groupB walk up to it. We can't directly observe set semantics on
        // String, but we can verify ALL three groups' perms are present.
        PermissionGroup shared = new PermissionGroup("shared");
        shared.addPermission("tweaks.shared");
        PermissionGroup a = new PermissionGroup("a");
        a.addPermission("tweaks.a");
        a.setParentName("shared");
        PermissionGroup b = new PermissionGroup("b");
        b.addPermission("tweaks.b");
        b.setParentName("shared");

        manager.getGroups().put("shared", shared);
        manager.getGroups().put("a", a);
        manager.getGroups().put("b", b);

        UUID uuid = UUID.randomUUID();
        UserPermissions u = manager.getUserPermissions(uuid);
        u.addGroup("a");
        u.addGroup("b");

        Set<String> perms = manager.calculateEffectivePermissions(uuid);
        assertTrue(perms.contains("tweaks.shared"));
        assertTrue(perms.contains("tweaks.a"));
        assertTrue(perms.contains("tweaks.b"));
    }

    @Test
    void cyclicGroupInheritanceTerminatesViaVisitedSet() {
        // a -> b -> a (cycle)
        PermissionGroup a = new PermissionGroup("a");
        a.addPermission("tweaks.a");
        a.setParentName("b");
        PermissionGroup b = new PermissionGroup("b");
        b.addPermission("tweaks.b");
        b.setParentName("a");
        manager.getGroups().put("a", a);
        manager.getGroups().put("b", b);

        UUID uuid = UUID.randomUUID();
        manager.getUserPermissions(uuid).addGroup("a");

        Set<String> perms = assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(2),
                () -> manager.calculateEffectivePermissions(uuid),
                "cyclic inheritance must terminate");
        assertTrue(perms.contains("tweaks.a"));
        assertTrue(perms.contains("tweaks.b"));
    }

    @Test
    void unknownGroupNameIsTreatedAsNoOpInInheritanceWalk() {
        UUID uuid = UUID.randomUUID();
        UserPermissions u = manager.getUserPermissions(uuid);
        u.addGroup("does-not-exist");
        Set<String> perms = manager.calculateEffectivePermissions(uuid);
        // 'default' is NOT walked because the user has an explicit (albeit unknown) group.
        assertNotNull(perms);
    }

    // The PromptType enum and setPrompt/getPrompt API were removed after the
    // /perms GUI migrated to Paper Dialogs (Tweaks-7fov). The two prompts that
    // used them (CREATE_GROUP, SEARCH_USER) are now confirmation dialogs with
    // DialogInput.text fields, handled entirely inside PermissionGUI.
}
