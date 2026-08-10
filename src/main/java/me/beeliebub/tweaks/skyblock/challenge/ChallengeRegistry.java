package me.beeliebub.tweaks.skyblock.challenge;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.tracking.MaterialMutations;
import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import me.beeliebub.tweaks.skyblock.tracking.TrackKey;
import me.beeliebub.tweaks.skyblock.tracking.TrackingScope;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Loads challenge definitions and publishes both tracking upper-bound sets atomically. */
public final class ChallengeRegistry implements TrackingScope {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final File file;
    private final TypeRegistry types;
    private final Map<String, Double> multipliers = new HashMap<>();
    private final Map<IslandCacheKey, Derived> derivedCache = new ConcurrentHashMap<>();
    private volatile Snapshot snapshot = new Snapshot(Map.of(),
            Map.of("general", new ChallengeCategory("general", "General", 0)), Set.of(), Set.of(), 0);
    private volatile Set<String> invalidChallengeIds = Set.of();
    private CompletableFuture<Void> saveTail = CompletableFuture.completedFuture(null);

    public ChallengeRegistry(JavaPlugin plugin) {
        this(plugin, null);
    }

    public ChallengeRegistry(JavaPlugin plugin, TypeRegistry types) {
        this(plugin, plugin.getLogger(), new File(plugin.getDataFolder(), "skyblock/challenges.yml"), types);
    }

    public ChallengeRegistry(Logger logger) {
        this(null, logger, null, null);
    }

    public ChallengeRegistry() {
        this(Logger.getLogger(ChallengeRegistry.class.getName()));
    }

    private ChallengeRegistry(JavaPlugin plugin, Logger logger, File file, TypeRegistry types) {
        this.plugin = plugin;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.file = file;
        this.types = types;
    }

    public synchronized void load() {
        if (file == null) return;
        load(YamlConfiguration.loadConfiguration(file));
    }

    public synchronized void load(YamlConfiguration yaml) {
        Objects.requireNonNull(yaml, "yaml");
        Map<String, ChallengeCategory> categories = parseCategories(yaml);
        Map<String, Challenge> definitions = new LinkedHashMap<>();
        if (yaml.isConfigurationSection("challenges")) {
            ConfigurationSection section = yaml.getConfigurationSection("challenges");
            for (String id : section.getKeys(false)) {
                try {
                    Challenge challenge = parseChallenge(id, section.getConfigurationSection(id));
                    definitions.put(challenge.id(), challenge);
                } catch (RuntimeException error) {
                    logger.log(Level.WARNING, "Skipping malformed Skyblock challenge '" + id + "'", error);
                }
            }
        } else {
            for (Map<?, ?> raw : yaml.getMapList("challenges")) {
                try {
                    String id = String.valueOf(raw.get("id"));
                    Challenge challenge = parseChallenge(id, raw);
                    definitions.put(challenge.id(), challenge);
                } catch (RuntimeException error) {
                    logger.log(Level.WARNING, "Skipping malformed Skyblock challenge entry: " + raw, error);
                }
            }
        }
        Set<String> loadedIds = new LinkedHashSet<>(definitions.keySet());
        removeUnknownCategories(categories, definitions);
        removeUnknownTypes(definitions);
        removeUnknownReferences(definitions);
        Set<String> disabled = cyclicIds(definitions);
        for (String id : disabled) {
            logger.severe("Disabling cyclic Skyblock challenge graph involving '" + id + "'.");
            definitions.remove(id);
        }
        removeUnknownReferences(definitions);
        Set<String> invalid = new LinkedHashSet<>(loadedIds);
        invalid.removeAll(definitions.keySet());
        invalidChallengeIds = Set.copyOf(invalid);
        publish(categories, definitions);
    }

    public synchronized void register(Challenge challenge) {
        Objects.requireNonNull(challenge, "challenge");
        Map<String, Challenge> definitions = new LinkedHashMap<>(snapshot.challenges());
        definitions.put(challenge.id(), challenge);
        Map<String, ChallengeCategory> categories = snapshot.categories();
        if (!categories.containsKey(challenge.categoryId())) {
            throw new IllegalArgumentException("Challenge references an unknown category: " + challenge.categoryId());
        }
        if (types != null) {
            for (String typeId : challenge.typeIds()) {
                if (types.type(typeId).isEmpty()) {
                    throw new IllegalArgumentException("Challenge references an unknown island type: " + typeId);
                }
            }
        }
        Set<String> unknown = unknownPrerequisites(challenge, definitions.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Challenge references unknown prerequisite(s): "
                    + String.join(", ", unknown));
        }
        Set<String> disabled = cyclicIds(definitions);
        if (!disabled.isEmpty()) {
            throw new IllegalArgumentException("Challenge edit would introduce a prerequisite cycle: " + challenge.id());
        }
        publish(categories, definitions);
        if (!invalidChallengeIds.isEmpty()) {
            Set<String> repaired = new LinkedHashSet<>(invalidChallengeIds);
            repaired.remove(challenge.id());
            invalidChallengeIds = Set.copyOf(repaired);
        }
    }

    public synchronized void registerCategory(ChallengeCategory category) {
        Objects.requireNonNull(category, "category");
        Map<String, ChallengeCategory> categories = new LinkedHashMap<>(snapshot.categories());
        categories.put(category.id(), category);
        publish(categories, snapshot.challenges());
    }

    public synchronized DeleteResult deleteCategory(String id) {
        if (id == null || id.isBlank()) return new DeleteResult(false, 0, "unknown category");
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!snapshot.categories().containsKey(normalized)) return new DeleteResult(false, 0, "unknown category");
        long references = snapshot.challenges().values().stream()
                .filter(challenge -> normalized.equals(challenge.categoryId())).count();
        if (references > 0) return new DeleteResult(false, references, "category is in use");
        Map<String, ChallengeCategory> categories = new LinkedHashMap<>(snapshot.categories());
        categories.remove(normalized);
        if (categories.isEmpty()) categories.put("general", new ChallengeCategory("general", "General", 0));
        publish(categories, snapshot.challenges());
        return new DeleteResult(true, 0, "deleted");
    }

    public synchronized DeleteResult deleteChallenge(String id) {
        if (id == null || id.isBlank()) return new DeleteResult(false, 0, "unknown challenge");
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!snapshot.challenges().containsKey(normalized)) {
            return new DeleteResult(false, 0, "unknown challenge");
        }
        long references = challengeReferenceCount(normalized);
        if (references > 0) return new DeleteResult(false, references, "challenge is in use");
        Map<String, Challenge> definitions = new LinkedHashMap<>(snapshot.challenges());
        definitions.remove(normalized);
        publish(snapshot.categories(), definitions);
        if (!invalidChallengeIds.isEmpty()) {
            Set<String> remainingInvalid = new LinkedHashSet<>(invalidChallengeIds);
            remainingInvalid.remove(normalized);
            invalidChallengeIds = Set.copyOf(remainingInvalid);
        }
        return new DeleteResult(true, 0, "deleted");
    }

    public synchronized boolean remove(String id) {
        return deleteChallenge(id).deleted();
    }

    public Optional<Challenge> challenge(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(snapshot.challenges().get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<ChallengeCategory> category(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(snapshot.categories().get(id.toLowerCase(Locale.ROOT)));
    }

    public long challengeReferenceCount(String id) {
        if (id == null) return 0;
        String normalized = id.toLowerCase(Locale.ROOT);
        return snapshot.challenges().values().stream().filter(challenge ->
                challenge.prerequisites().contains(normalized)
                        || challenge.anyOfGroups().stream()
                        .anyMatch(group -> group.challengeIds().contains(normalized))).count();
    }

    /** Challenge ids rejected while loading because of malformed references or cycles. */
    public Set<String> invalidChallengeIds() {
        return invalidChallengeIds;
    }

    public boolean hasInvalidDefinitions() {
        return !invalidChallengeIds.isEmpty();
    }

    public List<Challenge> challenges() {
        return snapshot.challenges().values().stream().sorted(Comparator.comparing(Challenge::id)).toList();
    }

    public List<Challenge> challengesIn(String categoryId) {
        return challenges().stream().filter(c -> c.categoryId().equalsIgnoreCase(categoryId)).toList();
    }

    public List<ChallengeCategory> categories() {
        return snapshot.categories().values().stream().sorted(Comparator.comparingInt(ChallengeCategory::order)).toList();
    }

    public synchronized CompletableFuture<Void> saveAsync() {
        if (plugin == null || file == null) {
            saveTail = CompletableFuture.failedFuture(
                    new IllegalStateException("Challenge registry has no persistence target"));
            return saveTail.copy();
        }
        List<Map<String, Object>> categoryRows = categories().stream().map(category -> Map.<String, Object>of(
                "id", category.id(), "name", category.displayName(), "order", category.order())).toList();
        List<Map<String, Object>> challengeRows = new ArrayList<>();
        for (Challenge challenge : challenges()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", challenge.id());
            row.put("category", challenge.categoryId());
            row.put("name", challenge.displayName());
            row.put("description", challenge.description());
            row.put("prerequisites", challenge.prerequisites());
            row.put("types", challenge.typeIds());
            row.put("requirements", challenge.requirements().stream().map(requirement -> {
                Map<String, Object> value = new LinkedHashMap<>();
                if (requirement instanceof ChallengeRequirement.Possession possession) {
                    value.put("type", "possession");
                    value.put("material", possession.material().name());
                    value.put("amount", possession.amount());
                } else if (requirement instanceof ChallengeRequirement.Tracked tracked) {
                    value.put("type", "tracked");
                    value.put("key", tracked.key().format());
                    value.put("amount", tracked.amount());
                }
                return value;
            }).toList());
            row.put("any-of", challenge.anyOfGroups().stream().map(group -> Map.of(
                    "required", group.required(), "ids", group.challengeIds())).toList());
            row.put("rewards", challenge.rewards().stream().map(ChallengeRegistry::serializeReward).toList());
            challengeRows.add(row);
        }
        saveTail = saveTail.handle((ignored, error) -> null).thenRunAsync(() -> {
            if (!YamlStore.saveNow(plugin, file, "skyblock challenge data", yaml -> {
                yaml.set("categories", categoryRows);
                yaml.set("challenges", challengeRows);
            })) throw new IllegalStateException("Challenge registry could not be saved");
        });
        return saveTail.copy();
    }

    /** Returns a detached observer of the ordered save queue for bounded lifecycle waits. */
    public synchronized CompletableFuture<Void> flush() {
        return saveTail.copy();
    }

    public record DeleteResult(boolean deleted, long references, String reason) { }

    @Override
    public Set<TrackKey> activeTrackKeys() {
        return snapshot.activeKeys();
    }

    @Override
    public Set<Material> taintMaterials() {
        return snapshot.taintMaterials();
    }

    @Override
    public int generation() {
        return snapshot.generation();
    }

    public Set<TrackKey> activeTrackKeys(Island island) {
        return derived(island).activeKeys();
    }

    public Set<Material> taintMaterials(Island island) {
        return derived(island).taintMaterials();
    }

    public boolean availableTo(Challenge challenge, Island island) {
        if (challenge == null) return false;
        if (!challenge.typeIds().isEmpty()
                && (island == null || !challenge.typeIds().contains(island.typeId().toLowerCase(Locale.ROOT)))) {
            return false;
        }
        if (island == null || types == null) return true;
        IslandType type = types.type(island.typeId()).orElse(null);
        return type == null || type.allowedChallengeIds().isEmpty()
                || type.allowedChallengeIds().contains(challenge.id());
    }

    public double multiplier(String difficultyId) {
        return difficultyId == null ? 1.0d : multipliers.getOrDefault(difficultyId.toLowerCase(Locale.ROOT), 1.0d);
    }

    private List<Challenge> filtered(Island island) {
        return snapshot.challenges().values().stream()
                .filter(c -> availableTo(c, island))
                .filter(c -> island == null || !island.completedChallenges().contains(c.id()))
                .toList();
    }

    private Derived derived(Island island) {
        if (island == null) return new Derived(activeTrackKeys(), taintMaterials());
        IslandCacheKey key = new IslandCacheKey(island.id(), island.typeId(),
                Set.copyOf(island.completedChallenges()), snapshot.generation());
        return derivedCache.computeIfAbsent(key, ignored -> {
            Set<TrackKey> active = new LinkedHashSet<>();
            Set<Material> materials = new LinkedHashSet<>();
            for (Challenge challenge : filtered(island)) {
                for (ChallengeRequirement requirement : challenge.requirements()) {
                    if (!(requirement instanceof ChallengeRequirement.Tracked tracked)) continue;
                    active.add(tracked.key());
                    Material material = materialFrom(tracked.key());
                    if (material != null) {
                        materials.add(material);
                        materials.addAll(MaterialMutations.preimage(material));
                    }
                }
            }
            return new Derived(Set.copyOf(active), Set.copyOf(materials));
        });
    }

    private void publish(Map<String, ChallengeCategory> categories, Map<String, Challenge> challenges) {
        Set<TrackKey> active = new LinkedHashSet<>();
        Set<Material> materials = new LinkedHashSet<>();
        for (Challenge challenge : challenges.values()) {
            for (ChallengeRequirement requirement : challenge.requirements()) {
                if (!(requirement instanceof ChallengeRequirement.Tracked tracked)) continue;
                active.add(tracked.key());
                Material material = materialFrom(tracked.key());
                if (material != null) {
                    materials.add(material);
                    materials.addAll(MaterialMutations.preimage(material));
                }
            }
        }
        snapshot = new Snapshot(Map.copyOf(challenges), Map.copyOf(categories), Set.copyOf(active),
                Set.copyOf(materials), snapshot.generation() + 1);
        derivedCache.clear();
    }

    private Map<String, ChallengeCategory> parseCategories(YamlConfiguration yaml) {
        Map<String, ChallengeCategory> categories = new LinkedHashMap<>();
        for (Map<?, ?> raw : yaml.getMapList("categories")) {
            try {
                String id = String.valueOf(raw.get("id"));
                String name = raw.containsKey("name") ? String.valueOf(raw.get("name")) : id;
                int order = raw.get("order") instanceof Number n ? n.intValue() : categories.size();
                ChallengeCategory category = new ChallengeCategory(id, name, order);
                categories.put(category.id(), category);
            } catch (RuntimeException error) {
                logger.log(Level.WARNING, "Skipping malformed Skyblock challenge category: " + raw, error);
            }
        }
        if (categories.isEmpty()) categories.put("general", new ChallengeCategory("general", "General", 0));
        return categories;
    }

    public synchronized void setMultiplier(String difficultyId, double multiplier) {
        if (difficultyId == null || !Double.isFinite(multiplier) || multiplier <= 0) return;
        multipliers.put(difficultyId.toLowerCase(Locale.ROOT), multiplier);
    }

    private Challenge parseChallenge(String id, ConfigurationSection section) {
        return parseChallenge(id, section == null ? Map.of() : new SectionMap(section));
    }

    private Challenge parseChallenge(String id, Map<?, ?> raw) {
        String category = string(raw, "category", "general");
        String name = string(raw, "name", id);
        String description = string(raw, "description", "");
        List<ChallengeRequirement> requirements = new ArrayList<>();
        Object reqRaw = raw.get("requirements");
        if (reqRaw instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Requirement must be a map");
                String kind = string(map, "type", string(map, "kind", "tracked"));
                long amount = number(map.get("amount"), 1L);
                if (kind.equalsIgnoreCase("possession")) {
                    Material material = Material.matchMaterial(string(map, "material", ""));
                    if (material == null) throw new IllegalArgumentException("Unknown possession material");
                    requirements.add(new ChallengeRequirement.Possession(material, amount));
                } else {
                    requirements.add(new ChallengeRequirement.Tracked(TrackKey.parse(string(map, "key", "")), amount));
                }
            }
        }
        List<String> prerequisites = strings(raw.get("prerequisites"));
        List<Challenge.PrerequisiteGroup> groups = new ArrayList<>();
        Object anyRaw = raw.get("any-of");
        if (anyRaw == null) anyRaw = raw.get("anyOf");
        if (anyRaw instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (value instanceof Map<?, ?> map) {
                    int required = (int) number(map.get("required"), 1L);
                    Object ids = map.containsKey("ids") ? map.get("ids") : map.get("challenges");
                    groups.add(new Challenge.PrerequisiteGroup(required, new LinkedHashSet<>(strings(ids))));
                } else {
                    groups.add(new Challenge.PrerequisiteGroup(1, Set.of(String.valueOf(value))));
                }
            }
        }
        List<ChallengeReward> rewards = parseRewards(id, raw.get("rewards"));
        return new Challenge(id, category, name, description, requirements, prerequisites, groups, rewards,
                new LinkedHashSet<>(strings(raw.get("types"))));
    }

    private List<ChallengeReward> parseRewards(String challengeId, Object raw) {
        List<ChallengeReward> rewards = new ArrayList<>();
        if (!(raw instanceof Iterable<?> iterable)) return rewards;
        int index = 0;
        for (Object value : iterable) {
            try {
                if (!(value instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException("Reward entry must be a map");
                }
                rewards.add(parseReward(challengeId, map));
            } catch (RuntimeException error) {
                logger.log(Level.WARNING, "Skipping malformed reward " + index
                        + " for Skyblock challenge '" + challengeId + "'", error);
            }
            index++;
        }
        return rewards;
    }

    private ChallengeReward parseReward(String challengeId, Map<?, ?> map) {
        String kind = string(map, "type", "");
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "size", "size-upgrade", "size_upgrade" -> new ChallengeReward.SizeUpgrade(
                    IslandSize.valueOf(string(map, "size", "SMALL").toUpperCase(Locale.ROOT)));
            case "generator", "generator-unlock" -> new ChallengeReward.GeneratorUnlock(
                    string(map, "tier", string(map, "id", "default")));
            case "money" -> new ChallengeReward.Money(Double.parseDouble(String.valueOf(map.get("amount"))));
            case "items" -> new ChallengeReward.Items(parseRewardItems(challengeId, map.get("items")));
            default -> throw new IllegalArgumentException("Unknown reward type: " + kind);
        };
    }

    private List<ItemStack> parseRewardItems(String challengeId, Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("Item reward must contain an item list");
        }
        List<ItemStack> items = new ArrayList<>();
        int index = 0;
        for (Object value : iterable) {
            try {
                ItemStack item = readRewardItem(value);
                items.add(new ChallengeReward.Items(List.of(item)).items().get(0));
            } catch (RuntimeException error) {
                logger.log(Level.WARNING, "Skipping malformed item " + index
                        + " in reward for Skyblock challenge '" + challengeId + "'", error);
            }
            index++;
        }
        return items;
    }

    private static ItemStack readRewardItem(Object value) {
        if (value instanceof ItemStack itemStack) return itemStack.clone();
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Reward item must be an item map");
        if (isSerializedItem(raw)) return ItemStack.deserialize(stringKeyed(raw));
        Material material = Material.matchMaterial(string(raw, "material", string(raw, "type", "")));
        if (material == null) {
            throw new IllegalArgumentException("Unknown reward material; keys=" + raw.keySet());
        }
        return new ItemStack(material, itemAmount(raw.get("amount")));
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

    private static Set<String> cyclicIds(Map<String, Challenge> challenges) {
        Set<String> disabled = new HashSet<>();
        Map<String, Visit> states = new HashMap<>();
        for (String id : challenges.keySet()) dfs(id, challenges, states, disabled, new ArrayList<>());
        return disabled;
    }

    private static Set<String> unknownReferenceIds(Map<String, Challenge> challenges) {
        Set<String> invalid = new HashSet<>();
        for (Challenge challenge : challenges.values()) {
            if (!unknownPrerequisites(challenge, challenges.keySet()).isEmpty()) invalid.add(challenge.id());
        }
        return invalid;
    }

    private static Set<String> unknownPrerequisites(Challenge challenge, Set<String> knownIds) {
        Set<String> unknown = new LinkedHashSet<>();
        for (String prerequisite : challenge.prerequisites()) {
            if (!knownIds.contains(prerequisite)) unknown.add(prerequisite);
        }
        for (Challenge.PrerequisiteGroup group : challenge.anyOfGroups()) {
            for (String prerequisite : group.challengeIds()) {
                if (!knownIds.contains(prerequisite)) unknown.add(prerequisite);
            }
        }
        return unknown;
    }

    private void removeUnknownCategories(Map<String, ChallengeCategory> categories,
                                         Map<String, Challenge> definitions) {
        definitions.entrySet().removeIf(entry -> {
            Challenge challenge = entry.getValue();
            if (categories.containsKey(challenge.categoryId())) return false;
            logger.warning("Skipping Skyblock challenge '" + challenge.id()
                    + "' because it references unknown category '" + challenge.categoryId() + "'.");
            return true;
        });
    }

    private void removeUnknownTypes(Map<String, Challenge> definitions) {
        if (types == null) return;
        definitions.entrySet().removeIf(entry -> {
            Challenge challenge = entry.getValue();
            for (String typeId : challenge.typeIds()) {
                if (types.type(typeId).isPresent()) continue;
                logger.warning("Skipping Skyblock challenge '" + challenge.id()
                        + "' because it references unknown island type '" + typeId + "'.");
                return true;
            }
            return false;
        });
    }

    private void removeUnknownReferences(Map<String, Challenge> definitions) {
        Set<String> invalid;
        while (!(invalid = unknownReferenceIds(definitions)).isEmpty()) {
            for (String id : invalid) {
                logger.warning("Skipping Skyblock challenge '" + id
                        + "' because it references an unknown prerequisite.");
                definitions.remove(id);
            }
        }
    }

    private static void dfs(String id, Map<String, Challenge> challenges, Map<String, Visit> states,
                            Set<String> disabled, List<String> stack) {
        if (!challenges.containsKey(id) || disabled.contains(id)) return;
        Visit visit = states.get(id);
        if (visit == Visit.VISITING) {
            int start = stack.indexOf(id);
            if (start >= 0) disabled.addAll(stack.subList(start, stack.size()));
            disabled.add(id);
            return;
        }
        if (visit == Visit.DONE) return;
        states.put(id, Visit.VISITING);
        stack.add(id);
        Challenge challenge = challenges.get(id);
        for (String prerequisite : challenge.prerequisites()) dfs(prerequisite, challenges, states, disabled, stack);
        for (Challenge.PrerequisiteGroup group : challenge.anyOfGroups()) {
            for (String prerequisite : group.challengeIds()) dfs(prerequisite, challenges, states, disabled, stack);
        }
        stack.remove(stack.size() - 1);
        states.put(id, Visit.DONE);
    }

    private static Material materialFrom(TrackKey key) {
        String id = key.identifier();
        return switch (key.category()) {
            case COLLECT, SMELT, PLACE, FISH, BREW, CRAFT, BARTER, TRADE -> Material.matchMaterial(id);
            default -> null;
        };
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static long number(Object value, long fallback) {
        return value instanceof Number n ? n.longValue() : value == null ? fallback : Long.parseLong(String.valueOf(value));
    }

    private static List<String> strings(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) return List.of();
        return java.util.stream.StreamSupport.stream(iterable.spliterator(), false).map(String::valueOf).toList();
    }

    private static Map<String, Object> serializeReward(ChallengeReward reward) {
        Map<String, Object> value = new LinkedHashMap<>();
        if (reward instanceof ChallengeReward.SizeUpgrade size) {
            value.put("type", "size");
            value.put("size", size.size().name());
        } else if (reward instanceof ChallengeReward.GeneratorUnlock generator) {
            value.put("type", "generator");
            value.put("tier", generator.tierId());
        } else if (reward instanceof ChallengeReward.Money money) {
            value.put("type", "money");
            value.put("amount", money.amount());
        } else if (reward instanceof ChallengeReward.Items items) {
            value.put("type", "items");
            value.put("items", items.items().stream().map(item -> item.serialize()).toList());
        }
        return value;
    }

    private enum Visit { VISITING, DONE }

    private record Snapshot(Map<String, Challenge> challenges, Map<String, ChallengeCategory> categories,
                            Set<TrackKey> activeKeys, Set<Material> taintMaterials, int generation) { }

    private record IslandCacheKey(String id, String typeId, Set<String> completed, int generation) { }

    private record Derived(Set<TrackKey> activeKeys, Set<Material> taintMaterials) { }

    private static final class SectionMap extends LinkedHashMap<String, Object> {
        SectionMap(ConfigurationSection section) {
            for (String key : section.getKeys(false)) put(key, section.get(key));
        }
    }
}
