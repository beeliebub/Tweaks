package me.beeliebub.tweaks.permissions;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized permission constants for the Tweaks plugin.
 */
public final class Permissions {

    private Permissions() {
        // Prevent instantiation
    }

    public static final String ADMIN_NICK = "tweaks.admin.nick";
    public static final String ADMIN_CONFIG = "tweaks.admin.config";
    public static final String ADMIN_DELHOME = "tweaks.admin.delhome";
    public static final String ADMIN_DELWARP = "tweaks.admin.delwarp";
    public static final String ADMIN_HOME = "tweaks.admin.home";
    public static final String ADMIN_HOMES = "tweaks.admin.homes";
    public static final String ADMIN_MORE = "tweaks.admin.more";
    public static final String ADMIN_SETHOME = "tweaks.admin.sethome";
    public static final String BYPASS_HOMES = "tweaks.bypass.homes";
    public static final String ADMIN_SETWARP = "tweaks.admin.setwarp";
    public static final String ADMIN_REWARD = "tweaks.admin.reward";
    public static final String ADMIN_WHACK = "tweaks.admin.whack";
    public static final String ADMIN_INVSEE = "tweaks.admin.invsee";
    public static final String ADMIN_BLOODMOON = "tweaks.admin.bloodmoon";
    public static final String ADMIN_LOGS = "tweaks.admin.logs";
    public static final String ADMIN_ITEM_EDIT = "tweaks.admin.itemedit";
    public static final String ADMIN_GUI_COPY = "tweaks.admin.guicopy";
    public static final String ADMIN_GAMEMODE = "tweaks.admin.gamemode";

    public static final String ADMIN_PERMISSIONS = "tweaks.admin.permissions";

    public static final String PROTECTION_CLAIM = "tweaks.protection.claim";
    public static final String PROTECTION_UNCLAIM = "tweaks.protection.unclaim";
    public static final String PROTECTION_MEMBER = "tweaks.protection.member";
    public static final String PROTECTION_FLAG = "tweaks.protection.flag";
    public static final String PROTECTION_INFO = "tweaks.protection.info";

    /**
     * Gets all permission constants defined in this class using reflection.
     * Useful for GUI lists and tab completion.
     */
    public static List<String> getAllPermissions() {
        List<String> perms = new ArrayList<>();
        for (Field field : Permissions.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    perms.add((String) field.get(null));
                } catch (IllegalAccessException ignored) {}
            }
        }
        return perms;
    }
}
