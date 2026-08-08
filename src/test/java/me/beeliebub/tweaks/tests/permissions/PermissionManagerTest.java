package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.PermissionGroup;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.UserPermissions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionManagerTest {

    @TempDir
    File dataFolder;

    private PermissionManager manager;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
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
    void deleteGroupRemovesUserMembershipsAndParentReferences() {
        PermissionGroup parent = new PermissionGroup("parent");
        PermissionGroup child = new PermissionGroup("child");
        child.setParentName("parent");
        manager.getGroups().put("parent", parent);
        manager.getGroups().put("child", child);

        UUID uuid = UUID.randomUUID();
        manager.getUserPermissions(uuid).addGroup("parent");

        assertTrue(manager.deleteGroup("parent"));
        assertFalse(manager.getGroups().containsKey("parent"));
        assertFalse(manager.getUserPermissions(uuid).hasGroup("parent"));
        assertNull(child.getParentName());
    }

    @Test
    void untrackedUsersInheritDefaultWithoutCreatingAUserRecord() {
        manager.getGroups().get("default").addPermission("tweaks.fly");
        UUID uuid = UUID.randomUUID();

        assertTrue(manager.calculateEffectivePermissions(uuid).contains("tweaks.fly"));
        assertFalse(manager.getUsers().containsKey(uuid));
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
    void defaultPermissionsSupplementExplicitGroupMemberships() {
        manager.getGroups().get("default").addPermission("tweaks.default");
        PermissionGroup builders = new PermissionGroup("builders");
        builders.addPermission("tweaks.builders");
        manager.getGroups().put("builders", builders);

        UUID uuid = UUID.randomUUID();
        manager.getUserPermissions(uuid).addGroup("builders");

        Set<String> perms = manager.calculateEffectivePermissions(uuid);
        assertTrue(perms.contains("tweaks.default"));
        assertTrue(perms.contains("tweaks.builders"));
    }

    @Test
    void untrackedUsersInheritDefaultParents() {
        PermissionGroup base = new PermissionGroup("base");
        base.addPermission("tweaks.base");
        manager.getGroups().put("base", base);
        manager.getGroups().get("default").setParentName("base");

        assertTrue(manager.calculateEffectivePermissions(UUID.randomUUID()).contains("tweaks.base"));
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
    void unknownGroupNameDoesNotSuppressDefaultPermissions() {
        manager.getGroups().get("default").addPermission("tweaks.default");
        UUID uuid = UUID.randomUUID();
        UserPermissions u = manager.getUserPermissions(uuid);
        u.addGroup("does-not-exist");
        Set<String> perms = manager.calculateEffectivePermissions(uuid);
        assertTrue(perms.contains("tweaks.default"));
    }

    @Test
    void refreshAllOnlinePlayersAppliesDefaultToUntrackedPlayers() {
        manager.getGroups().get("default").addPermission("tweaks.fly");
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        PermissionAttachment attachment = mock(PermissionAttachment.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.addAttachment(plugin)).thenReturn(attachment);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            manager.refreshAllOnlinePlayers();
        }

        verify(attachment).setPermission("tweaks.fly", true);
        assertFalse(manager.getUsers().containsKey(uuid));
    }

    @Test
    void refreshAllOnlinePlayersAppliesInheritedPermissionsToChildGroupMembers() {
        PermissionGroup parent = new PermissionGroup("parent");
        parent.addPermission("tweaks.parent");
        PermissionGroup child = new PermissionGroup("child");
        child.setParentName("parent");
        manager.getGroups().put("parent", parent);
        manager.getGroups().put("child", child);

        UUID uuid = UUID.randomUUID();
        manager.getUserPermissions(uuid).addGroup("child");
        Player player = mock(Player.class);
        PermissionAttachment attachment = mock(PermissionAttachment.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.addAttachment(plugin)).thenReturn(attachment);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            manager.refreshAllOnlinePlayers();
        }

        verify(attachment).setPermission("tweaks.parent", true);
    }

    // The PromptType enum and setPrompt/getPrompt API were removed after the
    // /perms GUI migrated to Paper Dialogs (Tweaks-7fov). The two prompts that
    // used them (CREATE_GROUP, SEARCH_USER) are now confirmation dialogs with
    // DialogInput.text fields, handled entirely inside PermissionGUI.
}
