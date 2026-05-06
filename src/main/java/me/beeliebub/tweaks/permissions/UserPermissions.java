package me.beeliebub.tweaks.permissions;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents per-user permission data and their assigned group.
 */
public class UserPermissions {
    private final UUID uuid;
    private String groupName;
    private final Set<String> permissions = new HashSet<>();

    public UserPermissions(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void addPermission(String permission) {
        permissions.add(permission.toLowerCase());
    }

    public void removePermission(String permission) {
        permissions.remove(permission.toLowerCase());
    }

    public boolean hasDirectPermission(String permission) {
        return permissions.contains(permission.toLowerCase());
    }
}
