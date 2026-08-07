package me.beeliebub.tweaks.profiles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Single source of truth for world-key -&gt; inventory/XP profile plus tab-list display, replacing
 * {@code SeparatorListener}'s substring-match constants and {@code TabManager}'s exact-match
 * constants. Owns (reads <b>and</b> writes) the {@code world-profiles} / {@code
 * world-profile-fallback} / {@code world-profile-sort-order} config.yml sections - no other class
 * may read those sections directly.
 *
 * <p>{@link #profileFor} preserves {@code SeparatorListener}'s historical
 * exact-then-substring-then-fallback resolution; {@link #tagFor} preserves {@code TabManager}'s
 * historical exact-then-fallback resolution. They intentionally differ - unifying them onto one
 * semantic could silently change which profile a world resolves to, and because profile names are
 * literal {@code ec_}/{@code xp_} storage-key prefixes, a wrong resolution reads to a player as
 * their inventory, ender chest, and XP appearing to vanish.
 *
 * <p>{@code world-profiles} is stored as a YAML <b>list</b> of {@code {world, profile, tag,
 * color}} objects rather than a map keyed by world-key. A keyed map would never let an admin
 * actually remove a bundled-default world key: {@code Tweaks#reconcileConfigDefaults}'s sticky
 * {@code copyDefaults(true)} union-merges a live section's child keys with the bundled jar's
 * default section on every {@code getKeys(false)} call, so a deleted default child key would
 * simply resurface on the next reload. A list is read/written atomically at one path (matching
 * every other list-type setting in this project - see the root {@code CLAUDE.md}'s "Bootstrap"
 * section), sidestepping that merge entirely.
 */
public final class WorldProfileTable {

    private static final String LIST_PATH = "world-profiles";
    private static final String FALLBACK_PATH = "world-profile-fallback";
    private static final String SORT_ORDER_PATH = "world-profile-sort-order";

    private static final WorldProfileEntry HARDCODED_FALLBACK =
            new WorldProfileEntry("", "standard", "Survival", NamedTextColor.GREEN);

    private final JavaPlugin plugin;
    private List<WorldProfileEntry> entries = List.of();
    private WorldProfileEntry fallback = HARDCODED_FALLBACK;
    private Map<String, String> sortOrder = Map.of();

    public WorldProfileTable(JavaPlugin plugin) {
        this.plugin = plugin;
        reload(plugin.getConfig());
    }

    // ------------------------------------------------------------ Read API

    /** Exact match (case-insensitive), then substring match in declaration order, then fallback. */
    public String profileFor(String worldKey) {
        if (worldKey == null) return fallback.profile();
        for (WorldProfileEntry entry : entries) {
            if (entry.worldKey().equalsIgnoreCase(worldKey)) return entry.profile();
        }
        for (WorldProfileEntry entry : entries) {
            if (worldKey.contains(entry.worldKey())) return entry.profile();
        }
        return fallback.profile();
    }

    /** Exact match (case-insensitive), then fallback - no substring tier. */
    public Component tagFor(String worldKey) {
        if (worldKey != null) {
            for (WorldProfileEntry entry : entries) {
                if (entry.worldKey().equalsIgnoreCase(worldKey)) return entry.tagComponent();
            }
        }
        return fallback.tagComponent();
    }

    /** Never null - a profile missing from world-profile-sort-order gets a deterministic trailing key. */
    public String sortKeyFor(String profile) {
        String key = sortOrder.get(profile);
        return key != null ? key : "zz_" + profile;
    }

    public Set<String> knownProfiles() {
        Set<String> profiles = new LinkedHashSet<>();
        for (WorldProfileEntry entry : entries) profiles.add(entry.profile());
        profiles.add(fallback.profile());
        return profiles;
    }

    public List<String> knownProfilesSorted() {
        return knownProfiles().stream().sorted().toList();
    }

    public List<String> worldKeysOrdered() {
        return entries.stream().map(WorldProfileEntry::worldKey).toList();
    }

    public Optional<WorldProfileEntry> entry(String worldKey) {
        if (worldKey == null) return Optional.empty();
        return entries.stream().filter(e -> e.worldKey().equalsIgnoreCase(worldKey)).findFirst();
    }

    public WorldProfileEntry fallback() {
        return fallback;
    }

    // ------------------------------------------------------------ Reload

    public void reload(FileConfiguration config) {
        List<WorldProfileEntry> loaded = new ArrayList<>();
        // getList (not getMapList) so a non-Map element is still observable as a warning rather
        // than vanishing indistinguishably from "never configured" - getMapList silently drops
        // anything that isn't a Map, with no log line.
        for (Object raw : config.getList(LIST_PATH, List.of())) {
            if (!(raw instanceof Map<?, ?> map)) {
                plugin.getLogger().warning("Skipping non-map world-profiles entry: " + raw);
                continue;
            }
            WorldProfileEntry entry = parseEntry(map);
            if (entry != null) loaded.add(entry);
        }
        this.entries = List.copyOf(loaded);
        this.fallback = parseFallback(config.getConfigurationSection(FALLBACK_PATH));

        Map<String, String> order = new LinkedHashMap<>();
        ConfigurationSection sortSection = config.getConfigurationSection(SORT_ORDER_PATH);
        if (sortSection != null) {
            for (String profile : sortSection.getKeys(false)) {
                order.put(profile, sortSection.getString(profile));
            }
        }
        this.sortOrder = Map.copyOf(order);
    }

    private WorldProfileEntry parseEntry(Map<?, ?> raw) {
        Object worldKeyObj = raw.get("world");
        Object profileObj = raw.get("profile");
        Object tagObj = raw.get("tag");
        Object colorObj = raw.get("color");
        if (!(worldKeyObj instanceof String worldKey) || worldKey.isBlank()
                || !(profileObj instanceof String profile) || profile.isBlank()
                || !(tagObj instanceof String tag) || tag.isBlank()) {
            plugin.getLogger().warning("Skipping malformed world-profiles entry (missing/blank field): " + raw);
            return null;
        }
        NamedTextColor color = colorObj instanceof String s ? parseColor(s) : null;
        if (color == null) {
            plugin.getLogger().warning("Skipping world-profiles entry for '" + worldKey
                    + "' - unknown color '" + colorObj + "'.");
            return null;
        }
        return new WorldProfileEntry(worldKey, profile, tag, color);
    }

    private WorldProfileEntry parseFallback(ConfigurationSection section) {
        if (section != null) {
            String profile = section.getString("profile");
            String tag = section.getString("tag");
            NamedTextColor color = parseColor(section.getString("color"));
            if (profile != null && !profile.isBlank() && tag != null && !tag.isBlank() && color != null) {
                return new WorldProfileEntry("", profile, tag, color);
            }
        }
        plugin.getLogger().severe("world-profile-fallback is missing or malformed in config.yml - "
                + "falling back to an in-code default (standard/Survival/GREEN). Every world not "
                + "otherwise mapped renders and resolves with this default until config.yml is fixed.");
        return HARDCODED_FALLBACK;
    }

    private static NamedTextColor parseColor(String raw) {
        if (raw == null) return null;
        return NamedTextColor.NAMES.value(raw.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------ Unvalidated mutators
    // (ConfigValueEditor owns validation - see core/config/CLAUDE.md's "world-profiles" section)

    public void putEntry(String worldKey, String profile, String tagText, NamedTextColor color) {
        List<WorldProfileEntry> updated = new ArrayList<>(entries);
        updated.removeIf(e -> e.worldKey().equalsIgnoreCase(worldKey));
        updated.add(new WorldProfileEntry(worldKey, profile, tagText, color));
        writeAndReload(updated);
    }

    public void removeEntry(String worldKey) {
        List<WorldProfileEntry> updated = new ArrayList<>(entries);
        updated.removeIf(e -> e.worldKey().equalsIgnoreCase(worldKey));
        writeAndReload(updated);
    }

    /** Rewrites only tag/color for an existing entry - profile is never touched by this path. */
    public void updateDisplay(String worldKey, String tagText, NamedTextColor color) {
        List<WorldProfileEntry> updated = new ArrayList<>();
        for (WorldProfileEntry e : entries) {
            updated.add(e.worldKey().equalsIgnoreCase(worldKey)
                    ? new WorldProfileEntry(e.worldKey(), e.profile(), tagText, color)
                    : e);
        }
        writeAndReload(updated);
    }

    private void writeAndReload(List<WorldProfileEntry> updated) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (WorldProfileEntry e : updated) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("world", e.worldKey());
            row.put("profile", e.profile());
            row.put("tag", e.tagText());
            String colorName = NamedTextColor.NAMES.key(e.color());
            row.put("color", (colorName == null ? "green" : colorName).toUpperCase(Locale.ROOT));
            serialized.add(row);
        }
        // Always writes the resulting List object (even when empty), never null - see the root
        // CLAUDE.md's "Bootstrap" section on why null resurrects the bundled default under
        // reconcileConfigDefaults' sticky copyDefaults(true).
        plugin.getConfig().set(LIST_PATH, serialized);
        plugin.saveConfig();
        reload(plugin.getConfig());
    }
}
