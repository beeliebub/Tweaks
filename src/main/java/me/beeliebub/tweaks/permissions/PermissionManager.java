package me.beeliebub.tweaks.permissions;

import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

// Manages permission logic, inheritance, attachments, and YAML persistence.
// Storage was previously a separate PermissionStorage class.
public class PermissionManager implements Listener {
    private final JavaPlugin plugin;
    private final File groupsFile;
    private final File usersFile;
    private final YamlStore permissionStore;

    private final Map<String, PermissionGroup> groups;
    private final Map<UUID, UserPermissions> users;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private volatile boolean storageHealthy = true;

    public PermissionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.groupsFile = new File(plugin.getDataFolder(), "groups.yml");
        this.usersFile = new File(plugin.getDataFolder(), "users.yml");
        this.permissionStore = new YamlStore(plugin, plugin.getDataFolder(), "permission data");
        this.groups = loadGroups();
        this.users = loadUsers();

        // Ensure at least a 'default' group exists
        if (!groups.containsKey("default")) {
            groups.put("default", new PermissionGroup("default"));
            if (storageHealthy) {
                try {
                    saveGroups();
                } catch (IllegalStateException error) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Could not persist the initial default permission group", error);
                }
            }
        }
    }

    public void saveGroups() {
        ensureStorageHealthy();
        if (!saveGroupsToDisk(groups.values())) {
            storageHealthy = false;
            throw new IllegalStateException("groups.yml could not be saved");
        }
    }

    public void saveUsers() {
        ensureStorageHealthy();
        if (!saveUsersToDisk(users.values())) {
            storageHealthy = false;
            throw new IllegalStateException("users.yml could not be saved");
        }
    }

    /**
     * Persists an immutable snapshot of the current groups without doing disk I/O on the server
     * thread. The returned future is the durability signal; a false result means the write did
     * not land and subsequent permission writes are refused until restart.
     */
    public CompletableFuture<Boolean> saveGroupsAsync() {
        ensureStorageHealthy();
        StateSnapshot snapshot = snapshotState();
        try {
            return permissionStore.writeAsync("groups", config -> writeGroups(config, snapshot.groups().values()))
                    .handle((ignored, error) -> {
                        if (error != null) {
                            storageHealthy = false;
                            return false;
                        }
                        return true;
                    });
        } catch (RuntimeException error) {
            storageHealthy = false;
            plugin.getLogger().log(Level.SEVERE, "Could not queue groups.yml for saving", error);
            return CompletableFuture.completedFuture(false);
        }
    }

    /** Persists an immutable snapshot of the current users without blocking the server thread. */
    public CompletableFuture<Boolean> saveUsersAsync() {
        ensureStorageHealthy();
        StateSnapshot snapshot = snapshotState();
        try {
            return permissionStore.writeAsync("users", config -> writeUsers(config, snapshot.users().values()))
                    .handle((ignored, error) -> {
                        if (error != null) {
                            storageHealthy = false;
                            return false;
                        }
                        return true;
                    });
        } catch (RuntimeException error) {
            storageHealthy = false;
            plugin.getLogger().log(Level.SEVERE, "Could not queue users.yml for saving", error);
            return CompletableFuture.completedFuture(false);
        }
    }

    private Map<String, PermissionGroup> loadGroups() {
        Map<String, PermissionGroup> loaded = new ConcurrentHashMap<>();
        if (!groupsFile.exists()) return loaded;

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(groupsFile);
        } catch (IOException | InvalidConfigurationException error) {
            storageHealthy = false;
            plugin.getLogger().log(Level.SEVERE,
                    "Could not load groups.yml; preserving the file and disabling permission writes", error);
            return loaded;
        }
        for (String key : config.getKeys(false)) {
            PermissionGroup group = new PermissionGroup(key);
            group.setParentName(config.getString(key + ".parent"));
            List<String> perms = config.getStringList(key + ".permissions");
            perms.forEach(group::addPermission);
            loaded.put(key.toLowerCase(), group);
        }
        return loaded;
    }

    private boolean saveGroupsToDisk(Collection<PermissionGroup> groups) {
        try {
            permissionStore.writeAsync("groups", config -> writeGroups(config, groups)).join();
            return true;
        } catch (CompletionException error) {
            return false;
        }
    }

    private static void writeGroups(YamlConfiguration config, Collection<PermissionGroup> groups) {
        for (PermissionGroup group : groups) {
            config.set(group.getName() + ".parent", group.getParentName());
            config.set(group.getName() + ".permissions", new ArrayList<>(group.getPermissions()));
        }
    }

    private Map<UUID, UserPermissions> loadUsers() {
        Map<UUID, UserPermissions> loaded = new ConcurrentHashMap<>();
        if (!usersFile.exists()) return loaded;

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(usersFile);
        } catch (IOException | InvalidConfigurationException error) {
            storageHealthy = false;
            plugin.getLogger().log(Level.SEVERE,
                    "Could not load users.yml; preserving the file and disabling permission writes", error);
            return loaded;
        }
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                UserPermissions user = new UserPermissions(uuid);

                // Multi-group: 'group' is now a list. Fall back to a single-string
                // value to migrate users.yml files written before the multi-group
                // refactor.
                Object rawGroup = config.get(key + ".group");
                if (rawGroup instanceof List<?> list) {
                    for (Object entry : list) {
                        if (entry != null) user.addGroup(entry.toString());
                    }
                } else if (rawGroup instanceof String single && !single.isEmpty()) {
                    user.addGroup(single);
                }

                List<String> perms = config.getStringList(key + ".permissions");
                perms.forEach(user::addPermission);
                loaded.put(uuid, user);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid UUID in users.yml: " + key, e);
            }
        }
        return loaded;
    }

    private boolean saveUsersToDisk(Collection<UserPermissions> users) {
        try {
            permissionStore.writeAsync("users", config -> writeUsers(config, users)).join();
            return true;
        } catch (CompletionException error) {
            return false;
        }
    }

    private static void writeUsers(YamlConfiguration config, Collection<UserPermissions> users) {
        for (UserPermissions user : users) {
            String key = user.getUuid().toString();
            config.set(key + ".group", new ArrayList<>(user.getGroups()));
            config.set(key + ".permissions", new ArrayList<>(user.getPermissions()));
        }
    }

    private void ensureStorageHealthy() {
        if (!storageHealthy) {
            throw new IllegalStateException("permission storage is unavailable; refusing to overwrite it");
        }
    }

    public Map<String, PermissionGroup> getGroups() {
        return groups;
    }

    public Map<UUID, UserPermissions> getUsers() {
        return users;
    }

    /** Captures mutable permission state so an administrative write can be rolled back safely. */
    StateSnapshot snapshotState() {
        Map<String, PermissionGroup> groupCopy = new java.util.HashMap<>();
        groups.forEach((key, source) -> {
            PermissionGroup copy = new PermissionGroup(source.getName());
            copy.setParentName(source.getParentName());
            source.getPermissions().forEach(copy::addPermission);
            groupCopy.put(key, copy);
        });

        Map<UUID, UserPermissions> userCopy = new java.util.HashMap<>();
        users.forEach((uuid, source) -> {
            UserPermissions copy = new UserPermissions(uuid);
            source.getGroups().forEach(copy::addGroup);
            source.getPermissions().forEach(copy::addPermission);
            userCopy.put(uuid, copy);
        });
        return new StateSnapshot(groupCopy, userCopy);
    }

    /** Restores a prior mutable state after a failed persistence operation. */
    void restoreState(StateSnapshot snapshot) {
        groups.clear();
        snapshot.groups().forEach((key, source) -> {
            PermissionGroup copy = new PermissionGroup(source.getName());
            copy.setParentName(source.getParentName());
            source.getPermissions().forEach(copy::addPermission);
            groups.put(key, copy);
        });

        users.clear();
        snapshot.users().forEach((uuid, source) -> {
            UserPermissions copy = new UserPermissions(uuid);
            source.getGroups().forEach(copy::addGroup);
            source.getPermissions().forEach(copy::addPermission);
            users.put(uuid, copy);
        });
    }

    /** Returns whether the live state still matches the supplied snapshot. */
    boolean stateMatches(StateSnapshot snapshot) {
        return groupsMatch(snapshot.groups()) && usersMatch(snapshot.users());
    }

    private boolean groupsMatch(Map<String, PermissionGroup> expected) {
        if (!groups.keySet().equals(expected.keySet())) return false;
        for (String key : groups.keySet()) {
            PermissionGroup actual = groups.get(key);
            PermissionGroup wanted = expected.get(key);
            if (!actual.getName().equals(wanted.getName())
                    || !java.util.Objects.equals(actual.getParentName(), wanted.getParentName())
                    || !actual.getPermissions().equals(wanted.getPermissions())) return false;
        }
        return true;
    }

    private boolean usersMatch(Map<UUID, UserPermissions> expected) {
        if (!users.keySet().equals(expected.keySet())) return false;
        for (UUID uuid : users.keySet()) {
            UserPermissions actual = users.get(uuid);
            UserPermissions wanted = expected.get(uuid);
            if (!actual.getGroups().equals(wanted.getGroups())
                    || !actual.getPermissions().equals(wanted.getPermissions())) return false;
        }
        return true;
    }

    record StateSnapshot(Map<String, PermissionGroup> groups, Map<UUID, UserPermissions> users) {}

    public UserPermissions getUserPermissions(UUID uuid) {
        return users.computeIfAbsent(uuid, UserPermissions::new);
    }

    /** Removes a non-default group and every reference that could otherwise revive it. */
    public boolean deleteGroup(String groupName) {
        if (groupName == null || groupName.equalsIgnoreCase("default")) return false;
        String normalized = groupName.toLowerCase();
        if (groups.remove(normalized) == null) return false;

        for (UserPermissions user : users.values()) {
            user.removeGroup(normalized);
        }
        for (PermissionGroup group : groups.values()) {
            if (normalized.equalsIgnoreCase(group.getParentName())) {
                group.setParentName(null);
            }
        }
        return true;
    }

    public Set<String> calculateEffectivePermissions(UUID uuid) {
        Set<String> effective = new HashSet<>();
        UserPermissions user = users.get(uuid);
        if (user != null) {
            effective.addAll(user.getPermissions());
        }

        // Every player receives the default group's permissions. Additional group
        // memberships supplement that baseline and may contribute inherited permissions.
        // A single `visited` set is shared across all entry-point groups so that
        // shared ancestors in the inheritance DAG (e.g. two groups inheriting
        // from a common parent) contribute their permissions exactly once.
        Set<String> visited = new HashSet<>();
        addInheritedPermissions("default", effective, visited);
        if (user != null) {
            for (String groupName : user.getGroups()) {
                addInheritedPermissions(groupName, effective, visited);
            }
        }

        return effective;
    }

    /** Returns permissions supplied by a group's parent chain, excluding the group's direct set. */
    public Set<String> inheritedPermissions(String groupName) {
        Set<String> inherited = new HashSet<>();
        if (groupName == null) return inherited;

        PermissionGroup group = groups.get(groupName.toLowerCase());
        if (group == null) return inherited;

        addInheritedPermissions(group.getParentName(), inherited, new HashSet<>());
        return inherited;
    }

    /** Returns permissions supplied by the user's groups rather than direct user overrides. */
    public Set<String> inheritedPermissions(UUID uuid) {
        Set<String> inherited = calculateEffectivePermissions(uuid);
        UserPermissions user = users.get(uuid);
        if (user != null) inherited.removeAll(user.getPermissions());
        return inherited;
    }

    private void addInheritedPermissions(String groupName, Set<String> effective, Set<String> visited) {
        if (groupName == null || visited.contains(groupName.toLowerCase())) return;

        PermissionGroup group = groups.get(groupName.toLowerCase());
        if (group == null) return;

        visited.add(groupName.toLowerCase());
        effective.addAll(group.getPermissions());

        if (group.getParentName() != null) {
            addInheritedPermissions(group.getParentName(), effective, visited);
        }
    }

    public void refreshPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Remove existing attachment
        PermissionAttachment old = attachments.remove(uuid);
        if (old != null) {
            player.removeAttachment(old);
        }

        // Create new attachment
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(uuid, attachment);

        // Apply calculated permissions
        Set<String> perms = calculateEffectivePermissions(uuid);
        for (String perm : perms) {
            attachment.setPermission(perm, true);
        }
    }

    /** Rebuilds permission attachments after an administrative group change. */
    public void refreshAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        attachments.remove(event.getPlayer().getUniqueId());
    }
    
    public void shutdown() {
        for (UUID uuid : attachments.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.removeAttachment(attachments.get(uuid));
            }
        }
        attachments.clear();
        try {
            saveGroups();
        } catch (IllegalStateException error) {
            plugin.getLogger().log(Level.SEVERE, "Permission groups were not saved during shutdown", error);
        }
        try {
            saveUsers();
        } catch (IllegalStateException error) {
            plugin.getLogger().log(Level.SEVERE, "Permission users were not saved during shutdown", error);
        }
    }
}
