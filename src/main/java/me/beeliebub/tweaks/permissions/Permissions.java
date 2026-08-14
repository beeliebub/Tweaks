package me.beeliebub.tweaks.permissions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * Allows modifying plugin configurations via commands and GUI. The single gate for every
     * runtime-editable plugin setting exposed through {@code /tconfig} (CLI and Dialog GUI alike) —
     * no per-category or per-setting permission exists.
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
     * Allows viewing and administering the server-wide casino house account via /house.
     */
    public static final String HOUSE_ADMIN = "tweaks.admin.house";

    /** Allows drawing and administering the server-wide lottery; viewing entries is public. */
    public static final String LOTTERY_ADMIN = "tweaks.admin.lottery";

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
     * Permission gate for paid/purchasable protection actions: setparent and unsetparent always,
     * plus claim except in worlds listed under {@code protection.public-claim-worlds}. A public
     * world waives only the claim permission check for non-admins; public non-admin claims still
     * pay and count against the per-player chunk limit, while admins retain their free/unlimited
     * bypass. Players without this permission cannot use setparent or unsetparent in any world.
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
     * Allows opting into the session-only in-world protection bypass through
     * {@code /region togglebypass}.
     */
    public static final String PROTECTION_BYPASS = "tweaks.protection.bypass";

    /**
     * Allows viewing and restoring player death inventories via /deathinventory.
     */
    public static final String ADMIN_DEATH_INVENTORY = "tweaks.admin.deathinventory";

    /**
     * Allows editing rank definitions via /ranks edit.
     */
    public static final String ADMIN_RANKS = "tweaks.admin.ranks";

    /**
     * Allows manually setting a player's rank via /rank set.
     */
    public static final String ADMIN_RANK_SET = "tweaks.admin.rank.set";

    /**
     * Allows running the throwaway admin diagnostic {@code /roulettescan}, which reads (never
     * mutates) the physical roulette wheel's BlockDisplay segments and dumps their raw geometry to
     * a TSV file. Deliberately under {@code tweaks.admin.*}, not {@code tweaks.roulette.*} — the
     * real board-management/betting feature reserves that namespace.
     */
    public static final String ADMIN_ROULETTE_SCAN = "tweaks.admin.roulettescan";

    /**
     * Allows starting the admin setup flow for a new Roulette board via /roulette createboard.
     * After running the command, the admin right-clicks a button or lever to register it as the
     * board's admin-only spin control, which also triggers the segment scan and persistence.
     */
    public static final String ROULETTE_CREATEBOARD = "tweaks.roulette.createboard";

    /**
     * Allows starting the removal flow for a Roulette board via /roulette removeboard.
     * After running the command, the admin right-clicks the target board's spin control; the
     * listener handles PDC cleanup, hitbox despawn, and chunk-ticket release.
     */
    public static final String ROULETTE_REMOVEBOARD = "tweaks.roulette.removeboard";

    /**
     * Allows choosing or clearing the single Roulette board exposed to Discord betting.
     */
    public static final String ROULETTE_SETDISCORD = "tweaks.roulette.setdiscord";

    /**
     * Allows using a Roulette board's admin-only spin control to force-close betting and spin
     * immediately, bypassing the normal timer. Checked in the listener at the point of use, not
     * just at command level, since the control is a physical block any player could right-click.
     */
    public static final String ROULETTE_FORCESPIN = "tweaks.roulette.forcespin";

    /** Allows a player to use the player-facing Skyblock commands. */
    public static final String SKYBLOCK_USE = "tweaks.skyblock.use";

    /** Allows a player to ignore Skyblock island containment and membership checks. */
    public static final String SKYBLOCK_BYPASS = "tweaks.skyblock.bypass";

    /** Allows access to the Skyblock administration command suite. */
    public static final String ADMIN_SKYBLOCK = "tweaks.admin.skyblock";

    // ---------------------------------------------------------------- Categories

    /**
     * Display name used in the category picker dialog for each category key.
     * Iteration order is the order buttons appear in the category list.
     *
     * <p>Populated with ordered {@code put} calls rather than {@code Map.of(...)}: {@code Map.of}
     * has unspecified iteration order that is randomized per JVM run, so copying one into a
     * {@link LinkedHashMap} captures an arbitrary order that changes across restarts.
     */
    private static final LinkedHashMap<String, String> CATEGORY_DISPLAY_NAMES = new LinkedHashMap<>();

    static {
        CATEGORY_DISPLAY_NAMES.put("tools",      "Admin & Tools");
        CATEGORY_DISPLAY_NAMES.put("teleport",   "Teleportation");
        CATEGORY_DISPLAY_NAMES.put("minigames",  "Minigames");
        CATEGORY_DISPLAY_NAMES.put("protection", "Protection");
        CATEGORY_DISPLAY_NAMES.put("ranks",      "Ranks");
        CATEGORY_DISPLAY_NAMES.put("bypass",     "Bypasses");
        CATEGORY_DISPLAY_NAMES.put("skyblock",   "Skyblock");
    }

    /** Metadata used to place and describe a permission in the GUI. */
    private record PermissionDetails(String category, String description) {}

    /** Ordered, immutable metadata for every permission constant in this class. */
    private static final Map<String, PermissionDetails> PERMISSION_CATALOG;

    static {
        Map<String, PermissionDetails> m = new LinkedHashMap<>();

        // tools — general admin utilities
        register(m, ADMIN_NICK, "tools", "Set or remove player nicknames.");
        register(m, ADMIN_CONFIG, "tools", "Modify Tweaks runtime configuration through /tconfig.");
        register(m, ADMIN_MORE, "tools", "Maximize the stack size of the held item.");
        register(m, ADMIN_INVSEE, "tools", "View and modify another online player's inventory.");
        register(m, ADMIN_LOGS, "tools", "Toggle chest interaction log inspector mode.");
        register(m, ADMIN_BALANCE, "tools", "View and modify player balances.");
        register(m, HOUSE_ADMIN, "tools", "View and administer the server-wide casino house account.");
        register(m, LOTTERY_ADMIN, "tools", "Draw and administer the server-wide lottery; viewing entries is public.");
        register(m, ADMIN_ITEM_EDIT, "tools", "Edit item display names and lore.");
        register(m, ADMIN_GUI_COPY, "tools", "Save a chest's contents as a GUI layout YAML file.");
        register(m, ADMIN_GAMEMODE, "tools", "Switch gamemodes through commands.");
        register(m, ADMIN_PERMISSIONS, "tools", "Manage permission groups, user overrides, and the permissions GUI.");
        register(m, ADMIN_DEATH_INVENTORY, "tools", "View and restore saved player death inventories.");

        // teleport — home and warp administration
        register(m, ADMIN_HOME, "teleport", "Teleport to any player's home.");
        register(m, ADMIN_HOMES, "teleport", "List the homes of any player.");
        register(m, ADMIN_SETHOME, "teleport", "Set a home for any player.");
        register(m, ADMIN_DELHOME, "teleport", "Delete the home of any player.");
        register(m, ADMIN_SETWARP, "teleport", "Create server warps.");
        register(m, ADMIN_DELWARP, "teleport", "Delete server warps.");

        // minigames — events and game administration
        register(m, ADMIN_REWARD, "minigames", "Manage and claim minigame rewards.");
        register(m, ADMIN_WHACK, "minigames", "Use Whack-an-Andrew administration commands.");
        register(m, ADMIN_BLOODMOON, "minigames", "Force the next full moon to become a Blood Moon.");
        register(m, ADMIN_RESOURCE_SETTARGET_SELF, "minigames", "Set your own Resource Hunt target.");
        register(m, ADMIN_RESOURCE_SETTARGET_OTHER, "minigames", "Set another player's Resource Hunt target.");
        register(m, BLACKJACK_CREATETABLE, "minigames", "Start the Blackjack table creation flow.");
        register(m, BLACKJACK_REMOVETABLE, "minigames", "Start the Blackjack table removal flow.");
        register(m, ADMIN_ROULETTE_SCAN, "minigames", "Run the read-only Roulette board geometry diagnostic.");
        register(m, ROULETTE_CREATEBOARD, "minigames", "Start the Roulette board setup flow.");
        register(m, ROULETTE_REMOVEBOARD, "minigames", "Start the Roulette board removal flow.");
        register(m, ROULETTE_SETDISCORD, "minigames", "Choose or clear the Roulette board exposed to Discord betting.");
        register(m, ROULETTE_FORCESPIN, "minigames", "Force a Roulette board to close betting and spin immediately.");

        // protection — land claim permissions
        register(m, PROTECTION_PURCHASEABLE, "protection", "Claim land and change claim parent relationships.");
        register(m, PROTECTION_UNCLAIM, "protection", "Unclaim owned land regions.");
        register(m, PROTECTION_MEMBER, "protection", "Manage members and managers of a region.");
        register(m, PROTECTION_FLAG, "protection", "Set and unset region protection flags.");
        register(m, PROTECTION_INFO, "protection", "Select regions and view their information and flags.");
        register(m, PROTECTION_ADMIN, "protection", "Administer all regions while bypassing ownership and limits.");
        register(m, PROTECTION_BYPASS, "protection", "Opt into the session-only in-world protection bypass.");

        // ranks — rank editing and assignment
        register(m, ADMIN_RANKS, "ranks", "Edit rank definitions.");
        register(m, ADMIN_RANK_SET, "ranks", "Manually set a player's rank.");

        // bypass — limit overrides
        register(m, BYPASS_HOMES, "bypass", "Bypass the maximum home count limit.");

        // skyblock — island use, containment bypass, and administration
        register(m, SKYBLOCK_USE, "skyblock", "Use Skyblock island, shop, and selling commands.");
        register(m, SKYBLOCK_BYPASS, "skyblock", "Ignore Skyblock containment and island access checks.");
        register(m, ADMIN_SKYBLOCK, "skyblock", "Use the complete Skyblock admin GUI/CLI for islands, registries, templates, and progression data.");

        PERMISSION_CATALOG = Collections.unmodifiableMap(new LinkedHashMap<>(m));
    }

    private static void register(Map<String, PermissionDetails> catalog, String permission, String category, String description) {
        PermissionDetails previous = catalog.put(permission, new PermissionDetails(category, description));
        if (previous != null) {
            throw new IllegalStateException("Duplicate permission catalog entry: " + permission);
        }
    }

    /**
     * Returns the category key for the given permission string, or {@code "tools"}
     * as a safe fallback for any permission not explicitly mapped.
     *
     * @param permission a permission string such as {@code "tweaks.admin.nick"}
     * @return a category key matching a key in {@link #getCategories()}
     */
    public static String getCategory(String permission) {
        PermissionDetails details = PERMISSION_CATALOG.get(permission);
        return details == null ? "tools" : details.category();
    }

    /**
     * Returns the concise explanation shown for a registered permission in the GUI.
     *
     * @param permission a permission string returned by {@link #getAllPermissions()}
     * @return the permission's non-empty description
     * @throws IllegalArgumentException if the permission is not registered in this catalog
     */
    public static String getDescription(String permission) {
        PermissionDetails details = PERMISSION_CATALOG.get(permission);
        if (details == null) {
            throw new IllegalArgumentException("Unknown permission: " + permission);
        }
        return details.description();
    }

    /**
     * Returns an ordered map of category key -> display name for every defined category.
     * The insertion order is the order categories appear in the GUI picker.
     *
     * @return a defensive ordered copy of category key to display name
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
        for (Map.Entry<String, PermissionDetails> entry : PERMISSION_CATALOG.entrySet()) {
            if (entry.getValue().category().equals(category)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- All permissions

    /** Gets all catalogued permissions in their declared GUI order. */
    public static List<String> getAllPermissions() {
        return new ArrayList<>(PERMISSION_CATALOG.keySet());
    }
}
