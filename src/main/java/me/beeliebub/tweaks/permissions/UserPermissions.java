package me.beeliebub.tweaks.permissions;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-user permission data and group memberships.
 *
 * <p>Users may belong to any number of permission groups simultaneously. A user
 * with no explicit group memberships still resolves to the {@code default} group
 * during effective-permission calculation (see {@link PermissionManager}).
 */
public class UserPermissions {
    private final UUID uuid;
    private final Set<String> groupNames = new HashSet<>();
    private final Set<String> permissions = new HashSet<>();

    public UserPermissions(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Set<String> getGroups() {
        return groupNames;
    }

    public void addGroup(String groupName) {
        if (groupName == null) return;
        groupNames.add(groupName.toLowerCase());
    }

    public void removeGroup(String groupName) {
        if (groupName == null) return;
        groupNames.remove(groupName.toLowerCase());
    }

    public boolean hasGroup(String groupName) {
        return groupName != null && groupNames.contains(groupName.toLowerCase());
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