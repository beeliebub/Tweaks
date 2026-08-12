package me.beeliebub.tweaks.skyblock.type;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runtime registry for difficulty-gated island types loaded from skyblock/types.yml. */
public final class TypeRegistry {

    private final Logger logger;
    private final Map<String, IslandDifficulty> difficulties = new LinkedHashMap<>();
    private final Map<String, IslandType> types = new LinkedHashMap<>();
    private final File file;
    private final JavaPlugin plugin;
    private CompletableFuture<Void> saveTail = CompletableFuture.completedFuture(null);

    public TypeRegistry(JavaPlugin plugin) {
        this(plugin, plugin.getLogger(), new File(plugin.getDataFolder(), "skyblock/types.yml"));
    }

    public TypeRegistry(Logger logger) {
        this(null, logger, null);
    }

    private TypeRegistry(JavaPlugin plugin, Logger logger, File file) {
        this.plugin = plugin;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.file = file;
    }

    public void load() {
        if (file == null) return;
        load(YamlStore.load(file));
    }

    public void load(YamlConfiguration yaml) {
        difficulties.clear();
        types.clear();
        for (Map<?, ?> raw : yaml.getMapList("difficulties")) {
            try {
                Object id = raw.get("id");
                Object name = raw.containsKey("name") ? raw.get("name") : id;
                IslandDifficulty difficulty = new IslandDifficulty(String.valueOf(id),
                        String.valueOf(name),
                        number(raw.get("order"), difficulties.size()),
                        decimal(raw.get("multiplier"), 1.0d));
                difficulties.put(difficulty.id(), difficulty);
            } catch (RuntimeException error) {
                logger.log(Level.WARNING, "Skipping malformed Skyblock difficulty entry: " + raw, error);
            }
        }
        if (difficulties.isEmpty()) registerDifficulty(new IslandDifficulty("normal", "Normal", 0));
        if (yaml.isConfigurationSection("types")) {
            for (String id : yaml.getConfigurationSection("types").getKeys(false)) {
                String path = "types." + id;
                try {
                    Set<String> allowed = new LinkedHashSet<>(yaml.getStringList(path + ".difficulties"));
                    IslandType type = new IslandType(id, yaml.getString(path + ".name", id), allowed,
                            yaml.getString(path + ".template", ""), readKit(yaml, path + ".kit", id),
                            yaml.getString(path + ".biome", "PLAINS"),
                            new LinkedHashSet<>(yaml.getStringList(path + ".allowed-challenges")));
                    registerType(type);
                } catch (RuntimeException error) {
                    logger.log(Level.WARNING, "Skipping malformed Skyblock type '" + id + "'", error);
                }
            }
        }
        if (types.isEmpty()) {
            registerType(new IslandType("default", "Default", Set.of("normal"), "", List.of(), "PLAINS", Set.of()));
        }
    }

    public void registerDifficulty(IslandDifficulty difficulty) {
        difficulties.put(difficulty.id(), Objects.requireNonNull(difficulty, "difficulty"));
    }

    public void registerType(IslandType type) {
        Objects.requireNonNull(type, "type");
        if (type.difficultyIds().stream().anyMatch(id -> !difficulties.containsKey(id))) {
            throw new IllegalArgumentException("Type references an unknown difficulty: " + type.id());
        }
        types.put(type.id(), type);
    }

    public Optional<IslandDifficulty> difficulty(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(difficulties.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<IslandType> type(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(types.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<IslandDifficulty> difficulties() {
        return difficulties.values().stream().sorted(Comparator.comparingInt(IslandDifficulty::order)).toList();
    }

    public List<IslandType> typesFor(String difficultyId) {
        return types.values().stream().filter(type -> type.allowsDifficulty(difficultyId))
                .sorted(Comparator.comparing(IslandType::id)).toList();
    }

    public Collection<IslandType> types() {
        return List.copyOf(types.values());
    }

    public synchronized CompletableFuture<Void> saveAsync() {
        if (plugin == null || file == null) {
            saveTail = CompletableFuture.failedFuture(
                    new IllegalStateException("Type registry has no persistence target"));
            return saveTail.copy();
        }
        List<Map<String, Object>> difficultyRows = difficulties().stream().map(difficulty -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", difficulty.id());
            row.put("name", difficulty.displayName());
            row.put("order", difficulty.order());
            row.put("multiplier", difficulty.multiplier());
            return row;
        }).toList();
        Map<String, Map<String, Object>> typeRows = new LinkedHashMap<>();
        for (IslandType type : types()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", type.displayName());
            row.put("difficulties", List.copyOf(type.difficultyIds()));
            row.put("template", type.templateId());
            row.put("biome", type.biome());
            row.put("allowed-challenges", List.copyOf(type.allowedChallengeIds()));
            row.put("kit", type.kit().stream().map(TypeRegistry::serializeKitItem).toList());
            typeRows.put(type.id(), row);
        }
        saveTail = saveTail.handle((ignored, error) -> null).thenRunAsync(() -> {
            if (!YamlStore.saveNow(plugin, file, "skyblock type data", yaml -> {
                yaml.set("difficulties", difficultyRows);
                yaml.createSection("types", typeRows);
            })) throw new IllegalStateException("Type registry could not be saved");
        });
        return saveTail.copy();
    }

    /** Returns a detached observer of the ordered save queue for bounded lifecycle waits. */
    public synchronized CompletableFuture<Void> flush() {
        return saveTail.copy();
    }

    public DeleteResult deleteType(String id, IslandManager islands) {
        IslandType type = type(id).orElse(null);
        if (type == null) return new DeleteResult(false, 0, "unknown type");
        long count = islands == null ? 0 : islands.all().stream()
                .filter(island -> type.id().equals(island.typeId())).count();
        if (count > 0) return new DeleteResult(false, count, "type is in use");
        types.remove(type.id());
        return new DeleteResult(true, 0, "deleted");
    }

    public DeleteResult deleteDifficulty(String id, IslandManager islands) {
        IslandDifficulty difficulty = difficulty(id).orElse(null);
        if (difficulty == null) return new DeleteResult(false, 0, "unknown difficulty");
        long typeReferences = types.values().stream().filter(type -> type.allowsDifficulty(difficulty.id())).count();
        long islandReferences = islands == null ? 0 : islands.all().stream()
                .filter(island -> difficulty.id().equalsIgnoreCase(island.difficultyId())).count();
        long count = typeReferences + islandReferences;
        if (count > 0) return new DeleteResult(false, count, "difficulty is in use");
        difficulties.remove(difficulty.id());
        return new DeleteResult(true, 0, "deleted");
    }

    public record DeleteResult(boolean deleted, long references, String reason,
                               CompletableFuture<Void> persistence) {
        public DeleteResult(boolean deleted, long references, String reason) {
            this(deleted, references, reason, CompletableFuture.completedFuture(null));
        }

        public DeleteResult {
            persistence = persistence == null ? CompletableFuture.completedFuture(null) : persistence;
        }
    }

    private static int number(Object raw, int fallback) {
        return raw instanceof Number number ? number.intValue() : fallback;
    }

    private static double decimal(Object raw, double fallback) {
        return raw instanceof Number number ? number.doubleValue() : raw == null ? fallback : Double.parseDouble(String.valueOf(raw));
    }

    private List<IslandType.KitItem> readKit(YamlConfiguration yaml, String path, String typeId) {
        List<IslandType.KitItem> kit = new ArrayList<>();
        List<?> entries = yaml.getList(path, List.of());
        for (int index = 0; index < entries.size(); index++) {
            Object value = entries.get(index);
            try {
                if (value instanceof ItemStack itemStack) {
                    kit.add(new IslandType.KitItem(itemStack));
                } else if (value instanceof Map<?, ?> raw) {
                    kit.add(readKitItem(raw));
                } else {
                    throw new IllegalArgumentException("Kit entry must be an item map");
                }
            } catch (RuntimeException error) {
                logger.log(Level.WARNING, "Skipping malformed kit item " + index
                        + " for Skyblock type '" + typeId + "'", error);
            }
        }
        return kit;
    }

    private static IslandType.KitItem readKitItem(Map<?, ?> raw) {
        if (isSerializedItem(raw)) return new IslandType.KitItem(ItemStack.deserialize(stringKeyed(raw)));
        Object material = raw.get("material");
        if (material == null) {
            throw new IllegalArgumentException("Kit item is missing material; keys=" + raw.keySet());
        }
        return new IslandType.KitItem(String.valueOf(material), itemAmount(raw.get("amount")));
    }

    private static Map<String, Object> serializeKitItem(IslandType.KitItem item) {
        if (item.hasMeta()) return new LinkedHashMap<>(item.itemStack().serialize());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("material", item.material());
        value.put("amount", item.amount());
        return value;
    }

    private static boolean isSerializedItem(Map<?, ?> raw) {
        return raw.containsKey("==") || raw.containsKey("v") || raw.containsKey("meta")
                || raw.containsKey("schema_version") || raw.containsKey("components")
                || raw.containsKey("DataVersion") || raw.containsKey("id") && raw.containsKey("count");
    }

    private static Map<String, Object> stringKeyed(Map<?, ?> raw) {
        Map<String, Object> values = new LinkedHashMap<>();
        raw.forEach((key, value) -> values.put(String.valueOf(key), value));
        return values;
    }

    private static int itemAmount(Object raw) {
        if (raw == null) return 1;
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (!Double.isFinite(value) || value != Math.rint(value)
                    || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Item amount must be a whole integer");
            }
            return (int) value;
        }
        return Integer.parseInt(String.valueOf(raw).trim());
    }
}
