package me.beeliebub.tweaks.permissions;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a permission group with inheritance support.
 */
public class PermissionGroup {
    private final String name;
    private String parentName;
    private final Set<String> permissions = new HashSet<>();

    public PermissionGroup(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
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
