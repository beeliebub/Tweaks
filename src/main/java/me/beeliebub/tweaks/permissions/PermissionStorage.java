package me.beeliebub.tweaks.permissions;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles persistence for groups and users.
 */
public class PermissionStorage {
    private final JavaPlugin plugin;
    private final File groupsFile;
    private final File usersFile;

    public PermissionStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.groupsFile = new File(plugin.getDataFolder(), "groups.yml");
        this.usersFile = new File(plugin.getDataFolder(), "users.yml");
    }

    public Map<String, PermissionGroup> loadGroups() {
        Map<String, PermissionGroup> groups = new ConcurrentHashMap<>();
        if (!groupsFile.exists()) return groups;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(groupsFile);
        for (String key : config.getKeys(false)) {
            PermissionGroup group = new PermissionGroup(key);
            group.setParentName(config.getString(key + ".parent"));
            List<String> perms = config.getStringList(key + ".permissions");
            perms.forEach(group::addPermission);
            groups.put(key.toLowerCase(), group);
        }
        return groups;
    }

    public void saveGroups(Collection<PermissionGroup> groups) {
        YamlConfiguration config = new YamlConfiguration();
        for (PermissionGroup group : groups) {
            config.set(group.getName() + ".parent", group.getParentName());
            config.set(group.getName() + ".permissions", new ArrayList<>(group.getPermissions()));
        }
        try {
            config.save(groupsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save groups.yml: " + e.getMessage());
        }
    }

    public Map<UUID, UserPermissions> loadUsers() {
        Map<UUID, UserPermissions> users = new ConcurrentHashMap<>();
        if (!usersFile.exists()) return users;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(usersFile);
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
                users.put(uuid, user);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in users.yml: " + key);
            }
        }
        return users;
    }

    public void saveUsers(Collection<UserPermissions> users) {
        YamlConfiguration config = new YamlConfiguration();
        for (UserPermissions user : users) {
            String key = user.getUuid().toString();
            config.set(key + ".group", new ArrayList<>(user.getGroups()));
            config.set(key + ".permissions", new ArrayList<>(user.getPermissions()));
        }
        try {
            config.save(usersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save users.yml: " + e.getMessage());
        }
    }
}
