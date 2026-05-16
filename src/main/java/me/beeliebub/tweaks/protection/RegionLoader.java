package me.beeliebub.tweaks.protection;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

// Reads every regions/**/<id>.yml file on plugin enable and deserializes into
// the in-memory Region cache. Recursive so admins can shard regions across
// subdirectories (e.g. regions/players/, regions/admin/) without the loader
// caring about layout.
//
// Boundary code on purpose: validates everything the YAML claims because the
// files are admin-editable and may contain typos, missing fields, or bad
// UUIDs. A malformed file is logged and skipped; the rest of the cache loads.
//
// Two flag schemas are accepted so existing region files written before the
// targeted-flag refactor keep loading:
//
//   Legacy (pre-refactor) — list of flag names. Each entry is translated to a
//   DEFAULT-target rule with value=true:
//     flags:
//       - PVP
//       - EXPLOSION
//
//   Targeted (current) — map of flag name -> { target-key: bool, ... }. Target
//   keys are the FlagTarget canonical forms ("default", "owner", "member", or
//   "group:<name>"):
//     flags:
//       BLOCK_BREAK:
//         default: false
//         owner: true
//         group:staff: true
//       PVP:
//         default: false
//
// Expected top-level YAML schema (rest of the file):
//   id: <string>
//   owner: <uuid>
//   members:
//     - <uuid>
//     - <uuid>
//   parent: <string>     # optional; nests this region under another region's
//                        # flag chain (sub-region resolution).
public final class RegionLoader {

    private final Logger logger;

    public RegionLoader(Logger logger) {
        this.logger = logger;
    }

    // Populate the supplied cache from <regionsDir>/**.yml. Returns the
    // number of regions successfully loaded (skipped malformed files do
    // not count). Creates the directory on first boot if absent.
    public int load(File regionsDir, ConcurrentHashMap<String, Region> cache) {
        if (!regionsDir.exists() && !regionsDir.mkdirs()) {
            logger.warning("Could not create regions directory " + regionsDir);
            return 0;
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(regionsDir.toPath())) {
            files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".yml"))
                    .toList();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to walk regions directory " + regionsDir, e);
            return 0;
        }

        int loaded = 0;
        for (Path path : files) {
            Region region = parse(path.toFile());
            if (region == null) continue;

            Region clash = cache.putIfAbsent(region.id(), region);
            if (clash != null) {
                logger.warning("Region id '" + region.id() + "' appears in multiple files; keeping the first load");
            } else {
                loaded++;
            }
        }
        return loaded;
    }

    private Region parse(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String id = yaml.getString("id");
        if (id == null || id.isEmpty()) {
            logger.warning("Skipping region file " + file.getName() + ": missing 'id'");
            return null;
        }

        String ownerStr = yaml.getString("owner");
        if (ownerStr == null) {
            logger.warning("Skipping region '" + id + "': missing 'owner'");
            return null;
        }

        UUID owner;
        try {
            owner = UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            logger.warning("Skipping region '" + id + "': invalid owner UUID '" + ownerStr + "'");
            return null;
        }

        List<UUID> members = new ArrayList<>();
        for (String raw : yaml.getStringList("members")) {
            try {
                members.add(UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                logger.warning("Region '" + id + "': dropping invalid member UUID '" + raw + "'");
            }
        }

        Map<RegionFlag, Map<FlagTarget, Boolean>> flagRules = parseFlags(id, yaml);
        Map<RegionFlag, Set<Material>> materialFlags = parseMaterialFlags(id, yaml);

        String parentRaw = yaml.getString("parent");
        String parent = (parentRaw == null || parentRaw.isBlank()) ? null : parentRaw;
        if (parent != null && parent.equals(id)) {
            logger.warning("Region '" + id + "': dropping self-referential parent");
            parent = null;
        }

        Region.RegionBounds bounds = parseBounds(id, yaml);
        String worldRaw = yaml.getString("world");
        String world = (worldRaw == null || worldRaw.isBlank()) ? null : worldRaw;

        return new Region(id, owner, members, flagRules, materialFlags, parent, bounds, world);
    }

    // Parse the optional `bounds:` section. Returns null when missing (legacy
    // regions written before bounds were stored) — /region select handles the
    // null gracefully by emitting a "re-claim required" error.
    //
    // Schema:
    //   bounds:
    //     min_chunk_x: -3
    //     min_chunk_z: 4
    //     max_chunk_x: 2
    //     max_chunk_z: 7
    private Region.RegionBounds parseBounds(String regionId, YamlConfiguration yaml) {
        if (!yaml.contains("bounds")) return null;
        ConfigurationSection section = yaml.getConfigurationSection("bounds");
        if (section == null) return null;
        for (String key : new String[] {"min_chunk_x", "min_chunk_z", "max_chunk_x", "max_chunk_z"}) {
            if (!section.isInt(key)) {
                logger.warning("Region '" + regionId + "': bounds is missing or non-integer key '"
                        + key + "'; dropping bounds");
                return null;
            }
        }
        int minX = section.getInt("min_chunk_x");
        int minZ = section.getInt("min_chunk_z");
        int maxX = section.getInt("max_chunk_x");
        int maxZ = section.getInt("max_chunk_z");
        if (minX > maxX || minZ > maxZ) {
            logger.warning("Region '" + regionId + "': bounds min > max; dropping bounds");
            return null;
        }
        return new Region.RegionBounds(minX, minZ, maxX, maxZ);
    }

    // Parse the optional `material_flags:` map. Schema:
    //   material_flags:
    //     ALLOW_BLOCK_BREAK:
    //       - GRASS_BLOCK
    //       - DIRT
    //     DENY_BLOCK_BREAK:
    //       - BEDROCK
    private Map<RegionFlag, Set<Material>> parseMaterialFlags(String regionId, YamlConfiguration yaml) {
        if (!yaml.contains("material_flags")) return Map.of();
        ConfigurationSection section = yaml.getConfigurationSection("material_flags");
        if (section == null) return Map.of();

        Map<RegionFlag, Set<Material>> out = new EnumMap<>(RegionFlag.class);
        for (String flagKey : section.getKeys(false)) {
            RegionFlag flag = parseFlag(regionId, flagKey);
            if (flag == null) continue;
            if (!flag.isMaterialFlag()) {
                logger.warning("Region '" + regionId + "': '" + flagKey
                        + "' is not a material-list flag; ignoring under material_flags");
                continue;
            }
            EnumSet<Material> materials = EnumSet.noneOf(Material.class);
            for (String raw : section.getStringList(flagKey)) {
                Material mat = Material.matchMaterial(raw);
                if (mat == null) {
                    logger.warning("Region '" + regionId + "': dropping unknown material '"
                            + raw + "' from " + flagKey);
                    continue;
                }
                materials.add(mat);
            }
            if (!materials.isEmpty()) out.put(flag, materials);
        }
        return out;
    }

    private Map<RegionFlag, Map<FlagTarget, Boolean>> parseFlags(String regionId, YamlConfiguration yaml) {
        if (!yaml.contains("flags")) return Map.of();

        // Legacy list-of-names form. Each entry becomes a DEFAULT=true rule.
        if (yaml.isList("flags")) {
            Map<RegionFlag, Map<FlagTarget, Boolean>> out = new EnumMap<>(RegionFlag.class);
            for (String raw : yaml.getStringList("flags")) {
                RegionFlag flag = parseFlag(regionId, raw);
                if (flag == null) continue;
                if (flag.isMaterialFlag()) {
                    logger.warning("Region '" + regionId + "': '" + raw
                            + "' is a material-list flag and cannot appear under 'flags:'; use 'material_flags:'");
                    continue;
                }
                out.put(flag, Map.of(FlagTarget.DEFAULT, true));
            }
            return out;
        }

        // Targeted map form. flags.<FLAG_NAME>.<target-key> -> boolean.
        ConfigurationSection section = yaml.getConfigurationSection("flags");
        if (section == null) return Map.of();

        Map<RegionFlag, Map<FlagTarget, Boolean>> out = new EnumMap<>(RegionFlag.class);
        for (String flagKey : section.getKeys(false)) {
            RegionFlag flag = parseFlag(regionId, flagKey);
            if (flag == null) continue;
            if (flag.isMaterialFlag()) {
                logger.warning("Region '" + regionId + "': '" + flagKey
                        + "' is a material-list flag and cannot appear under 'flags:'; use 'material_flags:'");
                continue;
            }

            ConfigurationSection ruleSection = section.getConfigurationSection(flagKey);
            if (ruleSection == null) {
                // Scalar value at flags.<NAME> — accept "true" as a shorthand
                // for the DEFAULT=true legacy meaning.
                if (section.isBoolean(flagKey) && section.getBoolean(flagKey)) {
                    out.put(flag, Map.of(FlagTarget.DEFAULT, true));
                }
                continue;
            }

            Map<FlagTarget, Boolean> rules = new HashMap<>();
            for (String targetKey : ruleSection.getKeys(false)) {
                FlagTarget target = FlagTarget.fromKey(targetKey.toLowerCase(Locale.ROOT));
                if (target == null) {
                    logger.warning("Region '" + regionId + "': dropping rule on flag '"
                            + flagKey + "' for unknown target '" + targetKey + "'");
                    continue;
                }
                if (!ruleSection.isBoolean(targetKey)) {
                    logger.warning("Region '" + regionId + "': flag '" + flagKey
                            + "' target '" + targetKey + "' has non-boolean value; dropping");
                    continue;
                }
                rules.put(target, ruleSection.getBoolean(targetKey));
            }
            if (!rules.isEmpty()) out.put(flag, rules);
        }
        return out;
    }

    private RegionFlag parseFlag(String regionId, String raw) {
        try {
            return RegionFlag.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Region '" + regionId + "': dropping unknown flag '" + raw + "'");
            return null;
        }
    }
}
