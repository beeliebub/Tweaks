package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.PermissionGroup;
import me.beeliebub.tweaks.permissions.PermissionStorage;
import me.beeliebub.tweaks.permissions.UserPermissions;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionStorageTest {

    @TempDir
    File dataFolder;

    private JavaPlugin plugin;
    private PermissionStorage storage;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        storage = new PermissionStorage(plugin);
    }

    @Test
    void loadGroupsReturnsEmptyMapWhenFileMissing() {
        Map<String, PermissionGroup> groups = storage.loadGroups();
        assertNotNull(groups);
        assertTrue(groups.isEmpty());
    }

    @Test
    void loadUsersReturnsEmptyMapWhenFileMissing() {
        Map<UUID, UserPermissions> users = storage.loadUsers();
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    @Test
    void saveAndReloadGroupsPreservesAllFields() {
        PermissionGroup admin = new PermissionGroup("admin");
        admin.setParentName("default");
        admin.addPermission("tweaks.admin.logs");
        admin.addPermission("tweaks.fly");

        storage.saveGroups(List.of(admin));
        Map<String, PermissionGroup> reloaded = storage.loadGroups();

        assertEquals(1, reloaded.size());
        PermissionGroup roundTripped = reloaded.get("admin");
        assertNotNull(roundTripped);
        assertEquals("default", roundTripped.getParentName());
        assertTrue(roundTripped.hasDirectPermission("tweaks.admin.logs"));
        assertTrue(roundTripped.hasDirectPermission("tweaks.fly"));
    }

    @Test
    void saveAndReloadUsersPreservesGroupsAndPerms() {
        UUID uuid = UUID.randomUUID();
        UserPermissions user = new UserPermissions(uuid);
        user.addGroup("admin");
        user.addGroup("vip");
        user.addPermission("tweaks.bypass.homes");

        storage.saveUsers(List.of(user));
        Map<UUID, UserPermissions> reloaded = storage.loadUsers();

        assertEquals(1, reloaded.size());
        UserPermissions rt = reloaded.get(uuid);
        assertNotNull(rt);
        assertTrue(rt.hasGroup("admin"));
        assertTrue(rt.hasGroup("vip"));
        assertTrue(rt.hasDirectPermission("tweaks.bypass.homes"));
    }

    @Test
    void loadUsersReadsLegacySingleStringGroupField() throws Exception {
        UUID uuid = UUID.randomUUID();
        File usersFile = new File(dataFolder, "users.yml");
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set(uuid + ".group", "admin");                    // legacy: bare string
        legacy.set(uuid + ".permissions", List.of("tweaks.fly"));
        legacy.save(usersFile);

        UserPermissions u = storage.loadUsers().get(uuid);
        assertNotNull(u);
        assertTrue(u.hasGroup("admin"));
        assertTrue(u.hasDirectPermission("tweaks.fly"));
    }

    @Test
    void loadUsersSkipsInvalidUuidEntries() throws Exception {
        File usersFile = new File(dataFolder, "users.yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("not-a-uuid.group", List.of("admin"));
        UUID valid = UUID.randomUUID();
        cfg.set(valid + ".group", List.of("admin"));
        cfg.save(usersFile);

        Map<UUID, UserPermissions> users = storage.loadUsers();
        assertEquals(1, users.size());
        assertTrue(users.containsKey(valid));
    }

    @Test
    void groupsFileWrittenAtExpectedPath() {
        storage.saveGroups(List.of(new PermissionGroup("default")));
        assertTrue(new File(dataFolder, "groups.yml").exists());
    }

    @Test
    void usersFileWrittenAtExpectedPath() {
        storage.saveUsers(List.of(new UserPermissions(UUID.randomUUID())));
        assertTrue(new File(dataFolder, "users.yml").exists());
    }

    @Test
    void groupNamesAreLowercasedOnLoad() throws Exception {
        File groupsFile = new File(dataFolder, "groups.yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("ADMIN.permissions", List.of("tweaks.fly"));
        cfg.save(groupsFile);

        Map<String, PermissionGroup> groups = storage.loadGroups();
        assertTrue(groups.containsKey("admin"), "group keys must be lowercased");
    }
}
