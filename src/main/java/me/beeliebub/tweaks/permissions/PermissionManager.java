package me.beeliebub.tweaks.permissions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages permission logic, inheritance, and attachments.
 */
public class PermissionManager implements Listener {
    private final JavaPlugin plugin;
    private final PermissionStorage storage;
    
    private final Map<String, PermissionGroup> groups;
    private final Map<UUID, UserPermissions> users;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<UUID, PromptType> activePrompts = new ConcurrentHashMap<>();

    public enum PromptType {
        CREATE_GROUP,
        SEARCH_USER
    }

    public PermissionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.storage = new PermissionStorage(plugin);
        this.groups = storage.loadGroups();
        this.users = storage.loadUsers();
        
        // Ensure at least a 'default' group exists
        if (!groups.containsKey("default")) {
            groups.put("default", new PermissionGroup("default"));
            saveGroups();
        }
    }

    public void saveGroups() {
        storage.saveGroups(groups.values());
    }

    public void saveUsers() {
        storage.saveUsers(users.values());
    }

    public Map<String, PermissionGroup> getGroups() {
        return groups;
    }

    public Map<UUID, UserPermissions> getUsers() {
        return users;
    }

    public UserPermissions getUserPermissions(UUID uuid) {
        return users.computeIfAbsent(uuid, UserPermissions::new);
    }

    public Set<String> calculateEffectivePermissions(UUID uuid) {
        Set<String> effective = new HashSet<>();
        UserPermissions user = users.get(uuid);
        if (user == null) return effective;

        // 1. User direct permissions
        effective.addAll(user.getPermissions());

        // 2. Group permissions (including inheritance)
        String groupName = user.getGroupName();
        if (groupName == null) groupName = "default";
        
        addInheritedPermissions(groupName, effective, new HashSet<>());

        return effective;
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

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public void setPrompt(UUID uuid, PromptType type) {
        if (type == null) activePrompts.remove(uuid);
        else activePrompts.put(uuid, type);
    }

    public PromptType getPrompt(UUID uuid) {
        return activePrompts.get(uuid);
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
        saveGroups();
        saveUsers();
    }
}
