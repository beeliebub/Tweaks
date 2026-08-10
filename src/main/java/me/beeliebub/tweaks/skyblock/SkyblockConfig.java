package me.beeliebub.tweaks.skyblock;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Typed access to Skyblock's config.yml settings.
 *
 * <p>The world key and grid pitch are part of persisted island identity. They are captured once
 * so a live config edit cannot move an existing island or redirect profile data during a server
 * lifetime. The remaining settings are read from the live configuration on each access.
 */
public final class SkyblockConfig {

    public static final String DEFAULT_WORLD = "jass:skyblock";
    public static final int DEFAULT_GRID_PITCH_CHUNKS = 35;
    public static final String DEFAULT_TYPE = "default";
    public static final String DEFAULT_DIFFICULTY = "normal";
    public static final int DEFAULT_ISLAND_Y = 64;
    public static final int DEFAULT_MAX_MEMBERS = 4;
    public static final int DEFAULT_MAX_HOMES = 3;
    public static final int DEFAULT_MAX_HOMES_PURCHASABLE = 10;
    public static final int DEFAULT_INVITE_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_SAVE_INTERVAL_SECONDS = 60;
    public static final int DEFAULT_DELETION_CHUNKS_PER_TICK = 4;
    public static final long DEFAULT_CONTAINMENT_MESSAGE_COOLDOWN_MS = 3000L;
    public static final int DEFAULT_TEMPLATE_MAX_HEIGHT = 128;
    public static final int DEFAULT_TEMPLATE_MAX_BLOCKS = 250_000;
    public static final int DEFAULT_MAX_SLOT_INDEX = 100_000;
    public static final double DEFAULT_BIOME_CHANGE_PRICE = 0.0;
    public static final double DEFAULT_HOME_SLOT_PRICE = 0.0;

    private static final String WORLD_PATH = "skyblock.world";
    private static final String GRID_PITCH_PATH = "skyblock.grid-pitch-chunks";
    private static final String DEFAULT_TYPE_PATH = "skyblock.default-type";
    private static final String DEFAULT_DIFFICULTY_PATH = "skyblock.default-difficulty";
    private static final String ISLAND_Y_PATH = "skyblock.island-y";
    private static final String MAX_MEMBERS_PATH = "skyblock.max-members";
    private static final String MAX_HOMES_PATH = "skyblock.max-homes";
    private static final String MAX_HOMES_PURCHASABLE_PATH = "skyblock.max-homes-purchasable";
    private static final String INVITE_TIMEOUT_PATH = "skyblock.invite-timeout-seconds";
    private static final String SAVE_INTERVAL_PATH = "skyblock.save-interval-seconds";
    private static final String DELETION_CHUNKS_PATH = "skyblock.deletion-chunks-per-tick";
    private static final String CONTAINMENT_COOLDOWN_PATH = "skyblock.containment-message-cooldown-ms";
    private static final String TEMPLATE_MAX_HEIGHT_PATH = "skyblock.template-max-height";
    private static final String TEMPLATE_MAX_BLOCKS_PATH = "skyblock.template-max-blocks";
    private static final String MAX_SLOT_INDEX_PATH = "skyblock.max-slot-index";
    private static final String BIOME_CHANGE_PRICE_PATH = "skyblock.price.biome-change";
    private static final String HOME_SLOT_PRICE_PATH = "skyblock.price.home-slot";

    private final JavaPlugin plugin;
    private final String world;
    private final int gridPitchChunks;

    public SkyblockConfig(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        FileConfiguration config = plugin.getConfig();
        this.world = configuredWorld(config.getString(WORLD_PATH), DEFAULT_WORLD);
        this.gridPitchChunks = config.getInt(GRID_PITCH_PATH, DEFAULT_GRID_PITCH_CHUNKS);
    }

    private static String configuredWorld(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    private static String configuredValue(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    /** Restart-required configured world key. */
    public String world() {
        return world;
    }

    /** Alias that makes the namespaced-key meaning explicit at call sites. */
    public String worldKey() {
        return world;
    }

    /** Restart-required grid pitch in chunks. */
    public int gridPitchChunks() {
        return gridPitchChunks;
    }

    /** Live configured island type used by the bare island-create form. */
    public String defaultType() {
        return configuredValue(config().getString(DEFAULT_TYPE_PATH), DEFAULT_TYPE);
    }

    /** Live configured difficulty used by the bare island-create form. */
    public String defaultDifficulty() {
        return configuredValue(config().getString(DEFAULT_DIFFICULTY_PATH), DEFAULT_DIFFICULTY);
    }

    /** Persists the paired defaults together so a type and difficulty are updated as one change. */
    public void setDefaults(String typeId, String difficultyId) {
        FileConfiguration config = config();
        config.set(DEFAULT_TYPE_PATH, configuredValue(typeId, DEFAULT_TYPE));
        config.set(DEFAULT_DIFFICULTY_PATH, configuredValue(difficultyId, DEFAULT_DIFFICULTY));
        plugin.saveConfig();
    }

    /** Explicitly named alias used by the admin authoring surface. */
    public void setDefaultSelection(String typeId, String difficultyId) {
        setDefaults(typeId, difficultyId);
    }

    public int islandY() {
        return config().getInt(ISLAND_Y_PATH, DEFAULT_ISLAND_Y);
    }

    public int maxMembers() {
        return config().getInt(MAX_MEMBERS_PATH, DEFAULT_MAX_MEMBERS);
    }

    public int maxHomes() {
        return config().getInt(MAX_HOMES_PATH, DEFAULT_MAX_HOMES);
    }

    public int maxHomesPurchasable() {
        return config().getInt(MAX_HOMES_PURCHASABLE_PATH, DEFAULT_MAX_HOMES_PURCHASABLE);
    }

    public int inviteTimeoutSeconds() {
        return config().getInt(INVITE_TIMEOUT_PATH, DEFAULT_INVITE_TIMEOUT_SECONDS);
    }

    public int saveIntervalSeconds() {
        return config().getInt(SAVE_INTERVAL_PATH, DEFAULT_SAVE_INTERVAL_SECONDS);
    }

    public int deletionChunksPerTick() {
        return config().getInt(DELETION_CHUNKS_PATH, DEFAULT_DELETION_CHUNKS_PER_TICK);
    }

    public long containmentMessageCooldownMs() {
        return config().getLong(CONTAINMENT_COOLDOWN_PATH, DEFAULT_CONTAINMENT_MESSAGE_COOLDOWN_MS);
    }

    public int templateMaxHeight() {
        return config().getInt(TEMPLATE_MAX_HEIGHT_PATH, DEFAULT_TEMPLATE_MAX_HEIGHT);
    }

    public int templateMaxBlocks() {
        return config().getInt(TEMPLATE_MAX_BLOCKS_PATH, DEFAULT_TEMPLATE_MAX_BLOCKS);
    }

    public int maxSlotIndex() {
        return config().getInt(MAX_SLOT_INDEX_PATH, DEFAULT_MAX_SLOT_INDEX);
    }

    public double biomeChangePrice() {
        return config().getDouble(BIOME_CHANGE_PRICE_PATH, DEFAULT_BIOME_CHANGE_PRICE);
    }

    public double homeSlotPrice() {
        return config().getDouble(HOME_SLOT_PRICE_PATH, DEFAULT_HOME_SLOT_PRICE);
    }
}
