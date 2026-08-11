package me.beeliebub.tweaks.logging;

import java.util.List;

/** Single source of truth for event-log paths, aliases, and category presentation. */
public final class LoggingPaths {

    private LoggingPaths() {}

    public static final String ECONOMY_BALANCE_SET = "logging.economy.balance-set";
    public static final String ECONOMY_BALANCE_ADD = "logging.economy.balance-add";
    public static final String ECONOMY_BALANCE_REMOVE = "logging.economy.balance-remove";
    public static final String ECONOMY_DAILY_REWARD = "logging.economy.daily-reward-claimed";
    public static final String ECONOMY_HOUSE_MUTATED = "logging.economy.house-mutated";
    public static final String ECONOMY_HOUSE_PAYMENT = "logging.economy.house-payment-completed";

    public static final String BLACKJACK_BET = "logging.blackjack.bet-placed";
    public static final String BLACKJACK_SETTLED = "logging.blackjack.hand-settled";
    public static final String BLACKJACK_TABLE = "logging.blackjack.table-mutated";

    public static final String ROULETTE_BET = "logging.roulette.bet-placed";
    public static final String ROULETTE_SETTLED = "logging.roulette.spin-settled";
    public static final String ROULETTE_BOARD = "logging.roulette.board-mutated";
    public static final String ROULETTE_SCAN = "logging.roulette.scan-run";

    public static final String LOTTERY_ENTERED = "logging.lottery.entered";
    public static final String LOTTERY_DRAW = "logging.lottery.draw-completed";
    public static final String LOTTERY_BASELINE = "logging.lottery.baseline-changed";
    public static final String LOTTERY_FALLBACK = "logging.lottery.fallback-changed";

    public static final String PROTECTION_CLAIM = "logging.protection.claim-created";
    public static final String PROTECTION_UNCLAIM = "logging.protection.claim-removed";
    public static final String PROTECTION_FLAG = "logging.protection.flag-changed";
    public static final String PROTECTION_MEMBER = "logging.protection.member-changed";
    public static final String PROTECTION_BLOCK_BREAK = "logging.protection.block-break-denied";
    public static final String PROTECTION_BLOCK_PLACE = "logging.protection.block-place-denied";
    public static final String PROTECTION_ENTRY = "logging.protection.entry-denied";
    public static final String PROTECTION_CONTAINER = "logging.protection.container-denied";

    public static final String PERMISSIONS_GROUP = "logging.permissions.group-mutated";
    public static final String PERMISSIONS_PERMISSION = "logging.permissions.permission-mutated";
    public static final String PERMISSIONS_USER_GROUPS = "logging.permissions.user-groups-changed";

    public static final String RANKS_ADMIN_SET = "logging.ranks.admin-set";
    public static final String RANKS_PURCHASED = "logging.ranks.purchased";

    public static final String TELEPORT_TPA_ACCEPTED = "logging.teleport.tpa-accepted";
    public static final String TELEPORT_HOME_SET = "logging.teleport.home-set";
    public static final String TELEPORT_ADMIN_HOME_DELETE = "logging.teleport.admin-home-deleted";
    public static final String TELEPORT_WARP_SET = "logging.teleport.warp-set";
    public static final String TELEPORT_WARP_DELETE = "logging.teleport.warp-deleted";

    public static final String PLAYERADMIN_GAMEMODE = "logging.playeradmin.gamemode-changed";
    public static final String PLAYERADMIN_FLY = "logging.playeradmin.fly-toggled";
    public static final String PLAYERADMIN_NICK = "logging.playeradmin.nick-changed";
    public static final String PLAYERADMIN_AFK = "logging.playeradmin.afk-auto";

    public static final String PROFILES_SWITCH = "logging.profiles.switch-applied";

    public static final String ITEMADMIN_EDIT = "logging.itemadmin.item-edited";
    public static final String ITEMADMIN_GUI_COPY = "logging.itemadmin.gui-copy-saved";
    public static final String ITEMADMIN_CONDENSE = "logging.itemadmin.condense-run";
    public static final String ITEMADMIN_INVSEE = "logging.itemadmin.invsee-opened";

    public static final String DEATHINVENTORY_CAPTURED = "logging.deathinventory.captured";
    public static final String DEATHINVENTORY_RESTORED = "logging.deathinventory.admin-restored";

    public static final String BLOCKLOG_INSPECTOR = "logging.blocklog.inspector-toggled";
    public static final String BLOCKLOG_PURGED = "logging.blocklog.retention-purged";

    public static final String ENCHANTMENTS_QUALITY = "logging.enchantments.quality-roll";
    public static final String ENCHANTMENTS_SPAWNER = "logging.enchantments.spawner-pickup";

    public static final String RESOURCEHUNT_ASSIGNED = "logging.resourcehunt.target-assigned";
    public static final String RESOURCEHUNT_REROLLED = "logging.resourcehunt.target-rerolled";
    public static final String RESOURCEHUNT_FORCED = "logging.resourcehunt.target-admin-forced";

    public static final String WHACK_STARTED = "logging.whack.session-started";
    public static final String WHACK_REWARD = "logging.whack.reward-claimed";

    public static final String WORLDMANAGEMENT_RISEN = "logging.worldmanagement.blood-moon-risen";
    public static final String WORLDMANAGEMENT_ENDED = "logging.worldmanagement.blood-moon-ended";
    public static final String WORLDMANAGEMENT_PORTAL = "logging.worldmanagement.portal-blocked";

    public static final String RECIPES_CURRENCY = "logging.recipes.currency-crafted";

    public static final String XPBOTTLE_BOTTLED = "logging.xpbottle.xp-bottled";
    public static final String XPBOTTLE_RELEASED = "logging.xpbottle.xp-released";

    public static final String CORE_CONFIG_CHANGED = "logging.core.config-changed";

    public record EventDefinition(String path, String displayName) {}

    public record CategoryDefinition(String key, String displayName, List<EventDefinition> events) {
        public CategoryDefinition {
            events = List.copyOf(events);
        }
    }

    private static EventDefinition event(String path, String displayName) {
        return new EventDefinition(path, displayName);
    }

    private static CategoryDefinition category(String key, String displayName, EventDefinition... events) {
        return new CategoryDefinition(key, displayName, List.of(events));
    }

    /** Ordered category definitions used by both the registry and the cache. */
    public static List<CategoryDefinition> categories() {
        return List.of(
                category("logging-economy", "Logging: Economy",
                        event(ECONOMY_BALANCE_SET, "Balance Set"), event(ECONOMY_BALANCE_ADD, "Balance Added"),
                        event(ECONOMY_BALANCE_REMOVE, "Balance Removed"), event(ECONOMY_DAILY_REWARD, "Daily Reward Claimed"),
                        event(ECONOMY_HOUSE_MUTATED, "House Account Mutated"), event(ECONOMY_HOUSE_PAYMENT, "House Payment Completed")),
                category("logging-blackjack", "Logging: Blackjack",
                        event(BLACKJACK_BET, "Bet Placed"), event(BLACKJACK_SETTLED, "Hand Settled"),
                        event(BLACKJACK_TABLE, "Table Mutated")),
                category("logging-roulette", "Logging: Roulette",
                        event(ROULETTE_BET, "Bet Placed"), event(ROULETTE_SETTLED, "Spin Settled"),
                        event(ROULETTE_BOARD, "Board Mutated"), event(ROULETTE_SCAN, "Scan Run")),
                category("logging-lottery", "Logging: Lottery",
                        event(LOTTERY_ENTERED, "Entry Purchased"), event(LOTTERY_DRAW, "Draw Completed"),
                        event(LOTTERY_BASELINE, "Baseline Changed"), event(LOTTERY_FALLBACK, "Fallback Changed")),
                category("logging-protection", "Logging: Protection",
                        event(PROTECTION_CLAIM, "Claim Created"), event(PROTECTION_UNCLAIM, "Claim Removed"),
                        event(PROTECTION_FLAG, "Flag Changed"), event(PROTECTION_MEMBER, "Member Changed"),
                        event(PROTECTION_BLOCK_BREAK, "Block Break Denied"), event(PROTECTION_BLOCK_PLACE, "Block Place Denied"),
                        event(PROTECTION_ENTRY, "Entry Denied"), event(PROTECTION_CONTAINER, "Container Access Denied")),
                category("logging-permissions", "Logging: Permissions",
                        event(PERMISSIONS_GROUP, "Group Mutated"), event(PERMISSIONS_PERMISSION, "Permission Mutated"),
                        event(PERMISSIONS_USER_GROUPS, "User Groups Changed")),
                category("logging-ranks", "Logging: Ranks",
                        event(RANKS_ADMIN_SET, "Rank Set by Admin"), event(RANKS_PURCHASED, "Rank Purchased")),
                category("logging-teleport", "Logging: Teleport",
                        event(TELEPORT_TPA_ACCEPTED, "TPA Accepted"), event(TELEPORT_HOME_SET, "Home Set"),
                        event(TELEPORT_ADMIN_HOME_DELETE, "Admin Home Deleted"), event(TELEPORT_WARP_SET, "Warp Set"),
                        event(TELEPORT_WARP_DELETE, "Warp Deleted")),
                category("logging-playeradmin", "Logging: Player Admin",
                        event(PLAYERADMIN_GAMEMODE, "Gamemode Changed"), event(PLAYERADMIN_FLY, "Fly Toggled"),
                        event(PLAYERADMIN_NICK, "Nickname Changed"), event(PLAYERADMIN_AFK, "AFK Auto-Triggered")),
                category("logging-profiles", "Logging: Profiles",
                        event(PROFILES_SWITCH, "World Profile Switch")),
                category("logging-itemadmin", "Logging: Item Admin",
                        event(ITEMADMIN_EDIT, "Item Edited"), event(ITEMADMIN_GUI_COPY, "GUI Copy Saved"),
                        event(ITEMADMIN_CONDENSE, "Condense Run"), event(ITEMADMIN_INVSEE, "Inventory Opened")),
                category("logging-deathinventory", "Logging: Death Inventory",
                        event(DEATHINVENTORY_CAPTURED, "Inventory Captured"), event(DEATHINVENTORY_RESTORED, "Inventory Restored")),
                category("logging-blocklog", "Logging: Block Log",
                        event(BLOCKLOG_INSPECTOR, "Inspector Toggled"), event(BLOCKLOG_PURGED, "Retention Purged")),
                category("logging-enchantments", "Logging: Enchantments",
                        event(ENCHANTMENTS_QUALITY, "Quality Roll"), event(ENCHANTMENTS_SPAWNER, "Spawner Pickup")),
                category("logging-resourcehunt", "Logging: Resource Hunt",
                        event(RESOURCEHUNT_ASSIGNED, "Target Assigned"), event(RESOURCEHUNT_REROLLED, "Target Rerolled"),
                        event(RESOURCEHUNT_FORCED, "Target Forced by Admin")),
                category("logging-whack", "Logging: Whack",
                        event(WHACK_STARTED, "Session Started"), event(WHACK_REWARD, "Reward Claimed")),
                category("logging-worldmanagement", "Logging: World Management",
                        event(WORLDMANAGEMENT_RISEN, "Blood Moon Risen"), event(WORLDMANAGEMENT_ENDED, "Blood Moon Ended"),
                        event(WORLDMANAGEMENT_PORTAL, "Portal Blocked")),
                category("logging-recipes", "Logging: Recipes",
                        event(RECIPES_CURRENCY, "Currency Crafted")),
                category("logging-xpbottle", "Logging: XP Bottle",
                        event(XPBOTTLE_BOTTLED, "XP Bottled"), event(XPBOTTLE_RELEASED, "XP Released")),
                category("logging-core", "Logging: Core",
                        event(CORE_CONFIG_CHANGED, "Config Changed"))
        );
    }

    /** Every registered logging path in stable order. */
    public static List<String> allPaths() {
        return categories().stream().flatMap(category -> category.events().stream())
                .map(EventDefinition::path).toList();
    }

    /** Converts a path to the display category used by console lines. */
    public static String categoryDisplay(String path) {
        for (CategoryDefinition category : categories()) {
            for (EventDefinition event : category.events()) {
                if (event.path().equals(path)) return category.displayName().replace("Logging: ", "");
            }
        }
        return "Logging";
    }
}
