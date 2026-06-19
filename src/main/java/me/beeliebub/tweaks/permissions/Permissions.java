package me.beeliebub.tweaks.permissions;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * Allows starting the button-linking table creation flow via /blackjack createtable.
     * After running the command, the admin right-clicks the middle control button to
     * finalize geometry detection and PDC persistence for the new table.
     */
    public static final String BLACKJACK_CREATETABLE = "tweaks.blackjack.createtable";

    /**
     * Allows starting the table removal flow via /blackjack removetable.
     * After running the command, the admin right-clicks the middle button of the target
     * table; the listener handles PDC cleanup and entity removal.
     */
    public static final String BLACKJACK_REMOVETABLE = "tweaks.blackjack.removetable";

    /**
     * Allows using /resource settarget to override your own Resource Hunt target.
     */
    public static final String ADMIN_RESOURCE_SETTARGET_SELF = "tweaks.admin.resource.settarget.self";

    /**
     * Allows using /resource settarget [player] to override another player's Resource Hunt target.
     */
    public static final String ADMIN_RESOURCE_SETTARGET_OTHER = "tweaks.admin.resource.settarget.other";

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
     * Allows viewing and modifying other players' balances.
     */
    public static final String ADMIN_BALANCE = "tweaks.admin.balance";

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
     * Permission gate for paid/purchasable protection actions: claim, setparent, and unsetparent.
     * Players with this permission can perform region operations that may incur Resource Rupee
     * costs and counted against the per-player chunk-claim limit.
     */
    public static final String PROTECTION_PURCHASEABLE = "tweaks.protection.purchaseable";

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
     * Allows editing rank definitions via /ranks edit.
     */
    public static final String ADMIN_RANKS = "tweaks.admin.ranks";

    /**
     * Allows manually setting a player's rank via /rank set.
     */
    public static final String ADMIN_RANK_SET = "tweaks.admin.rank.set";

    // ---------------------------------------------------------------- Categories

    /**
     * Display name used in the category picker dialog for each category key.
     * Iteration order is the order buttons appear in the category list.
     */
    private static final LinkedHashMap<String, String> CATEGORY_DISPLAY_NAMES = new LinkedHashMap<>(Map.of(
            "tools",      "Admin & Tools",
            "teleport",   "Teleportation",
            "minigames",  "Minigames",
            "protection", "Protection",
            "ranks",      "Ranks",
            "bypass",     "Bypasses"
    ));

    /**
     * Explicit mapping from each permission string to its category key.
     * Every constant defined in this class must appear exactly once here.
     */
    private static final Map<String, String> PERM_TO_CATEGORY;

    static {
        Map<String, String> m = new LinkedHashMap<>();

        // tools — general admin utilities
        m.put(ADMIN_NICK,         "tools");
        m.put(ADMIN_CONFIG,       "tools");
        m.put(ADMIN_MORE,         "tools");
        m.put(ADMIN_INVSEE,       "tools");
        m.put(ADMIN_LOGS,         "tools");
        m.put(ADMIN_BALANCE,      "tools");
        m.put(ADMIN_ITEM_EDIT,    "tools");
        m.put(ADMIN_GUI_COPY,     "tools");
        m.put(ADMIN_GAMEMODE,     "tools");
        m.put(ADMIN_PERMISSIONS,  "tools");

        // teleport — home and warp administration
        m.put(ADMIN_HOME,         "teleport");
        m.put(ADMIN_HOMES,        "teleport");
        m.put(ADMIN_SETHOME,      "teleport");
        m.put(ADMIN_DELHOME,      "teleport");
        m.put(ADMIN_SETWARP,      "teleport");
        m.put(ADMIN_DELWARP,      "teleport");

        // minigames — events and game administration
        m.put(ADMIN_REWARD,                    "minigames");
        m.put(ADMIN_WHACK,                     "minigames");
        m.put(ADMIN_BLOODMOON,                 "minigames");
        m.put(ADMIN_RESOURCE_SETTARGET_SELF,   "minigames");
        m.put(ADMIN_RESOURCE_SETTARGET_OTHER,  "minigames");
        m.put(BLACKJACK_CREATETABLE,           "minigames");
        m.put(BLACKJACK_REMOVETABLE,           "minigames");

        // protection — land claim permissions
        m.put(PROTECTION_PURCHASEABLE, "protection");
        m.put(PROTECTION_UNCLAIM,      "protection");
        m.put(PROTECTION_MEMBER,       "protection");
        m.put(PROTECTION_FLAG,         "protection");
        m.put(PROTECTION_INFO,         "protection");
        m.put(PROTECTION_ADMIN,        "protection");

        // ranks — rank editing and assignment
        m.put(ADMIN_RANKS,    "ranks");
        m.put(ADMIN_RANK_SET, "ranks");

        // bypass — limit overrides
        m.put(BYPASS_HOMES, "bypass");

        PERM_TO_CATEGORY = Map.copyOf(m);
    }

    /**
     * Returns the category key for the given permission string, or {@code "tools"}
     * as a safe fallback for any permission not explicitly mapped.
     *
     * @param permission a permission string such as {@code "tweaks.admin.nick"}
     * @return a category key matching a key in {@link #getCategories()}
     */
    public static String getCategory(String permission) {
        return PERM_TO_CATEGORY.getOrDefault(permission, "tools");
    }

    /**
     * Returns an ordered map of category key -> display name for every defined category.
     * The insertion order is the order categories appear in the GUI picker.
     *
     * @return unmodifiable ordered map of category key to display name
     */
    public static LinkedHashMap<String, String> getCategories() {
        return new LinkedHashMap<>(CATEGORY_DISPLAY_NAMES);
    }

    /**
     * Returns all permissions that belong to the given category, in declaration order.
     *
     * @param category a category key as returned by {@link #getCategories()}
     * @return list of permission strings in that category, may be empty
     */
    public static List<String> getPermissionsByCategory(String category) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : PERM_TO_CATEGORY.entrySet()) {
            if (entry.getValue().equals(category)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- All permissions

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
