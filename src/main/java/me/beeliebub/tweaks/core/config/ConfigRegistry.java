package me.beeliebub.tweaks.core.config;

import me.beeliebub.tweaks.logging.LoggingConfigCategories;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Single static ordered category -&gt; settings table backing both {@code /tconfig}'s CLI path
 * grammar and its Dialog GUI. New settings append entries here while each owning package keeps its
 * live-read conversion local; the record types used to describe settings remain stable. The
 * complete flat registry remains the source of truth; menu-specific views are filtered projections
 * so CLI lookup and output retain the full ordered list.
 *
 * <p>{@link #EGGDROP_SENTINEL_PATH}, {@link #SPAWNEREGG_SENTINEL_PATH}, and
 * {@link #RESOURCEITEMS_SENTINEL_PATH} mark the three pre-existing legacy settings whose mutation
 * is special-cased in {@link ConfigValueEditor} to preserve their exact historical CLI response
 * wording (see {@code core/config/CLAUDE.md}) - do not add a generic get/set path for these three.
 */
public final class ConfigRegistry {

    private ConfigRegistry() {}

    public static final String EGGDROP_SENTINEL_PATH = "eggdrop";
    public static final String SPAWNEREGG_SENTINEL_PATH = "spawneregg";
    public static final String RESOURCEITEMS_SENTINEL_PATH = "resourceitems";

    private static final List<ConfigCategory> CATEGORIES = List.of(
            new ConfigCategory("general", "General", List.of(
                    // These three settings' min/max are NOT enforced by ConfigValueEditor.applyScalar -
                    // it dispatches all three straight to legacy methods (setMaxHomes/setMaxChunks/
                    // setEggCollectorDropChance) whose own hardcoded checks are the real validation.
                    // The bounds below are display-only (GUI tooltip, /tconfig list) and happen to
                    // match the legacy checks today; keep them in sync if a legacy check ever changes.
                    ConfigSetting.bounded("max-homes", "max_homes", "Max Homes",
                            EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("max_chunks", "max_chunks", "Max Chunks",
                            EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("egg-collector-drop-chance", "egg_collector_drop_chance",
                            "Egg Collector Drop Chance", EditorType.PERCENT, 0.0, 100.0),
                    ConfigSetting.of(EGGDROP_SENTINEL_PATH, "eggdrop",
                            "Egg Collector Disabled Mobs", EditorType.MOB_LIST),
                    ConfigSetting.of(SPAWNEREGG_SENTINEL_PATH, "spawneregg",
                            "Spawner Egg Disabled Mobs", EditorType.MOB_LIST),
                    ConfigSetting.of(RESOURCEITEMS_SENTINEL_PATH, "resourceitems",
                            "Resource World Allowed Items", EditorType.MATERIAL_LIST)
            )),
            new ConfigCategory("protection", "Protection", List.of(
                    ConfigSetting.of("protection.selection-tool", "protection.selection-tool",
                            "Selection Tool", EditorType.MATERIAL),
                    // Claim-cost changes are not retroactive - Region.cost is persisted per-region
                    // at claim time and drives the unclaim refund, so repricing here never touches
                    // an already-claimed region. decay-rate < 1.0 would invert the pricing formula's
                    // intent (cost grows unbounded instead of tapering); min=1.0 rejects that while
                    // still allowing linear pricing at exactly 1.0.
                    ConfigSetting.bounded("protection.claim-cost.base", "protection.claim-cost.base",
                            "Claim Cost Base", EditorType.DOUBLE, 0.01, Double.MAX_VALUE),
                    ConfigSetting.bounded("protection.claim-cost.decay-rate", "protection.claim-cost.decay-rate",
                            "Claim Cost Decay Rate", EditorType.DOUBLE, 1.0, Double.MAX_VALUE),
                    ConfigSetting.bounded("protection.claim-cost.minimum-per-chunk", "protection.claim-cost.minimum-per-chunk",
                            "Claim Cost Minimum Per Chunk", EditorType.INT, 1, Integer.MAX_VALUE),
                    // Waives only the tweaks.protection.purchaseable requirement for /region claim
                    // in the listed worlds for non-admins. Public non-admin claims still pay and
                    // count against the per-player chunk limit; admins retain their free/unlimited
                    // bypass. Every overlap check still applies, and setparent/unsetparent still
                    // require the permission everywhere. Entries are namespaced world keys,
                    // matched case-insensitively.
                    ConfigSetting.of("protection.public-claim-worlds", "protection.public-claim-worlds",
                            "Public Claim Worlds", EditorType.WORLD_KEY_LIST)
            )),
            new ConfigCategory("playeradmin", "Player Admin", List.of(
                    ConfigSetting.of("fly-worlds", "fly-worlds",
                            "Fly Worlds", EditorType.WORLD_KEY_LIST),
                    ConfigSetting.of("fly-advancement", "fly-advancement",
                            "Fly Advancement", EditorType.NAMESPACED_KEY),
                    // Only 1-minute granularity is meaningful - the idle-check task itself still
                    // polls every 30 seconds (a cosmetic/internal-timing constant, out of scope for
                    // this config-migration round).
                    ConfigSetting.bounded("playeradmin.afk-auto-minutes", "playeradmin.afk-auto-minutes",
                            "AFK Auto Minutes", EditorType.INT, 1, Integer.MAX_VALUE),
                    // Does not retroactively re-validate or truncate an existing nickname stored in
                    // a player's PDC - only gates new /nick calls.
                    ConfigSetting.bounded("playeradmin.max-nick-length", "playeradmin.max-nick-length",
                            "Max Nick Length", EditorType.INT, 1, Integer.MAX_VALUE)
            )),
            new ConfigCategory("worldmanagement", "World Management", List.of(
                    ConfigSetting.of("disabled-end-portal-worlds", "disabled-end-portal-worlds",
                            "Disabled End Portal Worlds", EditorType.WORLD_KEY_LIST),
                    ConfigSetting.of("disabled-nether-portal-worlds", "disabled-nether-portal-worlds",
                            "Disabled Nether Portal Worlds", EditorType.WORLD_KEY_LIST),
                    ConfigSetting.bounded("worldmanagement.blood-moon-chance-percent",
                            "worldmanagement.blood-moon-chance-percent",
                            "Blood Moon Chance", EditorType.PERCENT, 0.0, 100.0)
            )),
            new ConfigCategory("teleport", "Teleport", List.of(
                    ConfigSetting.bounded("teleport.tpa-timeout-seconds", "teleport.tpa-timeout-seconds",
                            "TPA Timeout Seconds", EditorType.INT, 1, Integer.MAX_VALUE),
                    // Removing every entry re-enables /sethome in a periodically-reset resource
                    // world; homes recorded there before a world reset are silently invalidated by
                    // the reset itself, not by this setting.
                    ConfigSetting.of("teleport.sethome-disabled-worlds", "teleport.sethome-disabled-worlds",
                            "Sethome Disabled Worlds", EditorType.WORLD_KEY_LIST)
            )),
            new ConfigCategory("minigames", "Minigames", List.of(
                    // Lowering this while sessions are in progress evicts any session already idle
                    // past the new threshold on the very next sweep, forfeiting that player's
                    // already-deducted bet.
                    ConfigSetting.bounded("minigames.blackjack.inactivity-timeout-minutes",
                            "minigames.blackjack.inactivity-timeout-minutes",
                            "Blackjack Inactivity Timeout (Minutes)", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("minigames.blackjack.auto-clear-seconds",
                            "minigames.blackjack.auto-clear-seconds",
                            "Blackjack Auto Clear Seconds", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("minigames.roulette.betting-window-seconds",
                            "minigames.roulette.betting-window-seconds",
                            "Roulette Betting Window Seconds", EditorType.INT, 1, Integer.MAX_VALUE),
                    // A non-positive multiplier would fire the big-win celebration on every
                    // settlement.
                    ConfigSetting.bounded("minigames.roulette.big-win-multiplier",
                            "minigames.roulette.big-win-multiplier",
                            "Roulette Big Win Multiplier", EditorType.INT, 1, Integer.MAX_VALUE),
                    // Baked into each board at creation (RouletteBoardStore persists it per board)
                    // - changing this only affects boards registered after the change, never an
                    // already-registered board's stored scanRadius.
                    ConfigSetting.bounded("minigames.roulette.scan-radius",
                            "minigames.roulette.scan-radius",
                            "Roulette Scan Radius", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("minigames.resource-hunt.default-reward-multiplier",
                            "minigames.resource-hunt.default-reward-multiplier",
                            "Resource Hunt Default Reward Multiplier (Restart Required)",
                            EditorType.DOUBLE, 1.0, Double.MAX_VALUE)
            )),
            new ConfigCategory("economy", "Economy", List.of(
                    ConfigSetting.bounded("economy.daily-reward-base", "economy.daily-reward-base",
                            "Daily Reward Base", EditorType.DOUBLE, 0.0, Double.MAX_VALUE),
                    // min/max here bound the valid MAP KEY (streak day 1-7, EconomyListener.MAX_STREAK),
                    // not a value - see ConfigValueEditor#mapPut's key-range check.
                    ConfigSetting.bounded("economy.streak-multipliers", "economy.streak-multipliers",
                            "Streak Multipliers", EditorType.NUMBER_MAP, 1.0, 7.0),
                    ConfigSetting.bounded("lottery.pot-multiplier", "lottery.pot-multiplier",
                            "Lottery Pot Multiplier (default 0.6; 1.0+ lowers House Balance)",
                            EditorType.DOUBLE, 0.0, 10.0),
                    ConfigSetting.bounded("lottery.reseed-amount", "lottery.reseed-amount",
                            "Lottery Fallback Floor (base)", EditorType.INT, 1, Integer.MAX_VALUE)
            )),
            new ConfigCategory("blocklog", "Block Log", List.of(
                    ConfigSetting.bounded("blocklog.retention-days", "blocklog.retention-days",
                            "Retention Days", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("blocklog.max-entries-per-chest", "blocklog.max-entries-per-chest",
                            "Max Entries Per Chest", EditorType.INT, 1, Integer.MAX_VALUE)
            )),
            new ConfigCategory("deathinventory", "Death Inventory", List.of(
                    ConfigSetting.bounded("deathinventory.retention-days", "deathinventory.retention-days",
                            "Retention Days", EditorType.INT, 1, Integer.MAX_VALUE)
            )),
            new ConfigCategory("enchantments", "Enchantments", List.of(
                    ConfigSetting.bounded("enchantments.spawner-pickup.drop-chance-percent",
                            "enchantments.spawner-pickup.drop-chance-percent",
                            "Spawner Pickup Drop Chance", EditorType.PERCENT, 0.0, 100.0),
                    ConfigSetting.bounded("enchantments.spawner-pickup.uses", "enchantments.spawner-pickup.uses",
                            "Spawner Pickup Uses", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("enchantments.egg-collector.uses", "enchantments.egg-collector.uses",
                            "Egg Collector Uses", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("enchantments.quality.chance-percent", "enchantments.quality.chance-percent",
                            "Quality Roll Chance", EditorType.PERCENT, 0.0, 100.0),
                    ConfigSetting.bounded("enchantments.quality.blood-moon-chance-percent",
                            "enchantments.quality.blood-moon-chance-percent",
                            "Quality Roll Chance (Blood Moon)", EditorType.PERCENT, 0.0, 100.0),
                    ConfigSetting.bounded("enchantments.lumberjack.max-logs", "enchantments.lumberjack.max-logs",
                            "Lumberjack Max Logs", EditorType.INT, 1, Integer.MAX_VALUE)
            )),
            new ConfigCategory("itemadmin", "Item Admin", List.of(
                    ConfigSetting.bounded("itemadmin.tool-protect.default-threshold",
                            "itemadmin.tool-protect.default-threshold",
                            "Tool Protect Default Threshold", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("itemadmin.tool-protect.warn-cooldown-ms",
                            "itemadmin.tool-protect.warn-cooldown-ms",
                            "Tool Protect Warn Cooldown (ms)", EditorType.LONG, 0, Long.MAX_VALUE),
                    ConfigSetting.bounded("itemadmin.gui-copy.max-distance", "itemadmin.gui-copy.max-distance",
                            "Gui Copy Max Distance", EditorType.INT, 1, 64)
            )),
            new ConfigCategory("discord", "Discord", List.of(
                    ConfigSetting.of("discord.channel-id", "discord.channel-id",
                            "Announcement Channel ID", EditorType.STRING),
                    ConfigSetting.of("discord.webhook-name", "discord.webhook-name",
                            "Webhook Display Name", EditorType.STRING),
                    ConfigSetting.of("discord.webhook-avatar-url", "discord.webhook-avatar-url",
                            "Webhook Avatar URL", EditorType.STRING),
                    ConfigSetting.of("discord.betting-channel-id", "discord.betting-channel-id",
                            "Roulette Betting Channel ID", EditorType.STRING),
                    ConfigSetting.of("discord.betting-enabled", "discord.betting-enabled",
                            "Roulette Betting Enabled", EditorType.BOOLEAN),
                    ConfigSetting.of("discord.house-channel-id", "discord.house-channel-id",
                            "House Stat Voice Channel ID", EditorType.STRING),
                    ConfigSetting.of("discord.lottery-channel-id", "discord.lottery-channel-id",
                            "Lottery Stat Voice Channel ID", EditorType.STRING),
                    ConfigSetting.bounded("discord.group-window-seconds", "discord.group-window-seconds",
                            "Settlement Group Window Seconds", EditorType.INT, 1, 30),
                    ConfigSetting.bounded("discord.stat-refresh-seconds", "discord.stat-refresh-seconds",
                            "Stat Channel Refresh Seconds", EditorType.INT, 300, 3600)
            )),
            new ConfigCategory("xpbottle", "Xp Bottle", List.of(
                    ConfigSetting.bounded("xpbottle.orbs-per-emerald", "xpbottle.orbs-per-emerald",
                            "Orbs Per Emerald (restart required)", EditorType.INT, 1, Integer.MAX_VALUE)
            )),
            new ConfigCategory("skyblock", "Skyblock", List.of(
                    ConfigSetting.of("skyblock.world", "skyblock.world",
                            "Skyblock World (restart required)", EditorType.WORLD_KEY),
                    ConfigSetting.bounded("skyblock.grid-pitch-chunks", "skyblock.grid-pitch-chunks",
                            "Skyblock Grid Pitch (restart required)", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.of("skyblock.default-type", "skyblock.default-type",
                            "Skyblock Default Type", EditorType.STRING),
                    ConfigSetting.of("skyblock.default-difficulty", "skyblock.default-difficulty",
                            "Skyblock Default Difficulty", EditorType.STRING),
                    ConfigSetting.bounded("skyblock.island-y", "skyblock.island-y",
                            "Skyblock Island Y", EditorType.INT, -64, 320),
                    ConfigSetting.bounded("skyblock.max-members", "skyblock.max-members",
                            "Skyblock Max Members", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("skyblock.max-homes", "skyblock.max-homes",
                            "Skyblock Max Homes", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("skyblock.max-homes-purchasable", "skyblock.max-homes-purchasable",
                            "Skyblock Max Purchasable Homes", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("skyblock.invite-timeout-seconds", "skyblock.invite-timeout-seconds",
                            "Skyblock Invite Timeout", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("skyblock.save-interval-seconds", "skyblock.save-interval-seconds",
                            "Skyblock Save Interval", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("skyblock.deletion-chunks-per-tick", "skyblock.deletion-chunks-per-tick",
                            "Skyblock Deletion Chunks Per Tick", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("skyblock.containment-message-cooldown-ms",
                            "skyblock.containment-message-cooldown-ms", "Skyblock Containment Message Cooldown",
                            EditorType.LONG, 0, Long.MAX_VALUE),
                    ConfigSetting.bounded("skyblock.template-max-height", "skyblock.template-max-height",
                            "Skyblock Template Max Height", EditorType.INT, 1, 384),
                    ConfigSetting.bounded("skyblock.template-max-blocks", "skyblock.template-max-blocks",
                            "Skyblock Template Maximum Blocks", EditorType.INT, 1, Integer.MAX_VALUE),
                    ConfigSetting.bounded("skyblock.max-slot-index", "skyblock.max-slot-index",
                            "Skyblock Maximum Slot Index", EditorType.INT, 0, Integer.MAX_VALUE),
                    ConfigSetting.bounded("skyblock.price.biome-change", "skyblock.price.biome-change",
                            "Skyblock Biome Change Price", EditorType.DOUBLE, 0.0, Double.MAX_VALUE),
                    ConfigSetting.bounded("skyblock.price.home-slot", "skyblock.price.home-slot",
                            "Skyblock Home Slot Price", EditorType.DOUBLE, 0.0, Double.MAX_VALUE)
            ))
    );

    public static List<ConfigCategory> categories() {
        return allCategories();
    }

    public static List<ConfigCategory> mainMenuCategories() {
        return allCategories().stream()
                .filter(category -> category.group() == ConfigCategory.Group.MAIN)
                .toList();
    }

    public static List<ConfigCategory> loggingCategories() {
        return allCategories().stream()
                .filter(category -> category.group() == ConfigCategory.Group.LOGGING)
                .toList();
    }

    public static Optional<ConfigCategory> category(String key) {
        return allCategories().stream().filter(c -> c.key().equalsIgnoreCase(key)).findFirst();
    }

    /** Resolves a CLI top-level token (e.g. "max_homes", "eggdrop") to its setting, if any. */
    public static Optional<ConfigSetting> byCliAlias(String alias) {
        for (ConfigCategory category : allCategories()) {
            for (ConfigSetting setting : category.settings()) {
                if (setting.matchesAlias(alias)) return Optional.of(setting);
            }
        }
        return Optional.empty();
    }

    /** Resolves a dotted config.yml path (the generic {@code /tconfig <path> ...} grammar). */
    public static Optional<ConfigSetting> byPath(String path) {
        for (ConfigCategory category : allCategories()) {
            for (ConfigSetting setting : category.settings()) {
                if (setting.path().equalsIgnoreCase(path)) return Optional.of(setting);
            }
        }
        return Optional.empty();
    }

    public static List<String> allCliAliases() {
        List<String> aliases = new ArrayList<>();
        for (ConfigCategory category : allCategories()) {
            for (ConfigSetting setting : category.settings()) {
                aliases.addAll(setting.cliAliases());
            }
        }
        return aliases;
    }

    private static List<ConfigCategory> allCategories() {
        List<ConfigCategory> categories = new ArrayList<>(CATEGORIES);
        categories.addAll(LoggingConfigCategories.categories());
        return List.copyOf(categories);
    }
}
