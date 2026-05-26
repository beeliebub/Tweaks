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

    /**
     * Allows setting/removing nicknames.
     */
    public static final String ADMIN_NICK = "tweaks.admin.nick";

    /**
     * Allows modifying plugin configurations via commands and GUI.
     */
    public static final String ADMIN_CONFIG = "tweaks.admin.config";

    /**
     * Allows deleting the home of any player.
     */
    public static final String ADMIN_DELHOME = "tweaks.admin.delhome";

    /**
     * Allows deleting server warps.
     */
    public static final String ADMIN_DELWARP = "tweaks.admin.delwarp";

    /**
     * Allows teleporting to any player's home.
     */
    public static final String ADMIN_HOME = "tweaks.admin.home";

    /**
     * Allows listing the homes of any player.
     */
    public static final String ADMIN_HOMES = "tweaks.admin.homes";

    /**
     * Allows a player to maximize the stack size of their currently held item.
     */
    public static final String ADMIN_MORE = "tweaks.admin.more";

    /**
     * Allows setting a home for any player.
     */
    public static final String ADMIN_SETHOME = "tweaks.admin.sethome";

    /**
     * Allows bypassing the maximum home count limit.
     */
    public static final String BYPASS_HOMES = "tweaks.bypass.homes";

    /**
     * Allows setting server warps.
     */
    public static final String ADMIN_SETWARP = "tweaks.admin.setwarp";

    /**
     * Allows managing and claiming minigame rewards.
     */
    public static final String ADMIN_REWARD = "tweaks.admin.reward";

    /**
     * Allows access to Whack-an-Andrew minigame admin commands.
     */
    public static final String ADMIN_WHACK = "tweaks.admin.whack";

    /**
     * Allows viewing and modifying another online player's inventory.
     */
    public static final String ADMIN_INVSEE = "tweaks.admin.invsee";

    /**
     * Allows forcing the next full moon to be a Blood Moon.
     */
    public static final String ADMIN_BLOODMOON = "tweaks.admin.bloodmoon";

    /**
     * Allows toggling inspector mode to view chest interaction logs.
     */
    public static final String ADMIN_LOGS = "tweaks.admin.logs";

    /**
     * Allows editing item properties, such as display name and lore.
     */
    public static final String ADMIN_ITEM_EDIT = "tweaks.admin.itemedit";

    /**
     * Allows saving a targeted chest's contents to a YAML file for GUI layouts.
     */
    public static final String ADMIN_GUI_COPY = "tweaks.admin.guicopy";

    /**
     * Allows switching gamemodes via command.
     */
    public static final String ADMIN_GAMEMODE = "tweaks.admin.gamemode";

    /**
     * Grants full access to the custom permission management system, including groups, users, and GUI editor.
     */
    public static final String ADMIN_PERMISSIONS = "tweaks.admin.permissions";

    /**
     * Allows claiming new land regions.
     */
    public static final String PROTECTION_CLAIM = "tweaks.protection.claim";

    /**
     * Allows players to purchase chunk claims, assuming they are under the limit.
     */
    public static final String PROTECTION_CLAIM_PURCHASABLE = "tweaks.protection.claim.purchasable";

    /**
     * Allows unclaiming owned land regions.
     */
    public static final String PROTECTION_UNCLAIM = "tweaks.protection.unclaim";

    /**
     * Allows managing (adding/removing) members and managers of a region.
     */
    public static final String PROTECTION_MEMBER = "tweaks.protection.member";

    /**
     * Allows setting and unsetting region protection flags.
     */
    public static final String PROTECTION_FLAG = "tweaks.protection.flag";

    /**
     * Allows selecting regions and viewing region information/flags.
     */
    public static final String PROTECTION_INFO = "tweaks.protection.info";

    /**
     * Grants full administrative access over all regions, bypassing limits and ownership checks.
     */
    public static final String PROTECTION_ADMIN = "tweaks.protection.admin";

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
