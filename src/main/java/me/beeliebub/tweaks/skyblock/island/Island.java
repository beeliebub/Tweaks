package me.beeliebub.tweaks.skyblock.island;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable persisted state for one Skyblock island. */
public record Island(
        String id,
        UUID owner,
        List<UUID> members,
        int slotIndex,
        IslandSize size,
        String typeId,
        String difficultyId,
        boolean isPublic,
        String displayName,
        SpawnOffset spawnOffset,
        String generatorTierId,
        Map<UUID, Map<String, Home>> homes,
        int purchasedHomeSlots,
        Instant createdAt,
        Instant lastActive,
        Map<String, Long> counters,
        Set<String> completedChallenges,
        Set<String> notifiedChallenges
) {

    private static final Pattern ID_PATTERN = Pattern.compile("[0-9a-f]{32}");

    public Island {
        if (!isValidId(id)) {
            throw new IllegalArgumentException("Island id must be 32 lowercase hexadecimal characters");
        }
        owner = Objects.requireNonNull(owner, "owner");
        members = immutableMembers(owner, members);
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex cannot be negative");
        }
        size = Objects.requireNonNull(size, "size");
        typeId = nonBlankOrDefault(typeId, "default");
        difficultyId = nonBlankOrDefault(difficultyId, "normal");
        displayName = displayName == null ? "" : displayName;
        spawnOffset = spawnOffset == null ? new SpawnOffset(0, 0, 0) : spawnOffset;
        generatorTierId = nonBlankOrDefault(generatorTierId, "default");
        homes = immutableHomes(homes);
        if (purchasedHomeSlots < 0) {
            throw new IllegalArgumentException("purchasedHomeSlots cannot be negative");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        lastActive = Objects.requireNonNull(lastActive, "lastActive");
        counters = immutableCounterMap(counters);
        completedChallenges = immutableStringSet(completedChallenges);
        notifiedChallenges = immutableStringSet(notifiedChallenges);
    }

    /** Compatibility constructor for loaders that retain members as a set. */
    public Island(String id, UUID owner, Collection<UUID> members, int slotIndex, IslandSize size,
                  String typeId, String difficultyId, boolean isPublic, String displayName,
                  SpawnOffset spawnOffset, String generatorTierId,
                  Map<UUID, Map<String, Home>> homes, int purchasedHomeSlots,
                  Instant createdAt, Instant lastActive, Map<String, Long> counters,
                  Collection<String> completedChallenges, Collection<String> notifiedChallenges) {
        this(id, owner, members == null ? List.of() : List.copyOf(members), slotIndex, size, typeId,
                difficultyId, isPublic, displayName, spawnOffset, generatorTierId, homes,
                purchasedHomeSlots, createdAt, lastActive, counters,
                completedChallenges == null ? Set.of() : Set.copyOf(completedChallenges),
                notifiedChallenges == null ? Set.of() : Set.copyOf(notifiedChallenges));
    }

    /** Compatibility constructor for persisted model callers from before notifications existed. */
    public Island(String id, UUID owner, List<UUID> members, int slotIndex, IslandSize size,
                  String typeId, String difficultyId, boolean isPublic, String displayName,
                  SpawnOffset spawnOffset, String generatorTierId,
                  Map<UUID, Map<String, Home>> homes, int purchasedHomeSlots,
                  Instant createdAt, Instant lastActive, Map<String, Long> counters,
                  Set<String> completedChallenges) {
        this(id, owner, members, slotIndex, size, typeId, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, Set.of());
    }

    public static boolean isValidId(String value) {
        return value != null && ID_PATTERN.matcher(value).matches();
    }

    public static String idFor(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    public static String newId() {
        return idFor(UUID.randomUUID());
    }

    public static Island create(UUID owner, int slotIndex, IslandSize size) {
        return create(owner, slotIndex, size, Clock.systemUTC());
    }

    public static Island create(UUID owner, int slotIndex, IslandSize size, Clock clock) {
        Instant now = Objects.requireNonNull(clock, "clock").instant();
        return new Island(idFor(Objects.requireNonNull(owner, "owner")), owner, List.of(), slotIndex,
                Objects.requireNonNull(size, "size"), "default", "normal", false, "Island",
                new SpawnOffset(0, 0, 0), "default", Map.of(), 0, now, now, Map.of(), Set.of(),
                Set.of());
    }

    public boolean isMember(UUID player) {
        return player != null && (owner.equals(player) || members.contains(player));
    }

    public int memberCount() {
        return members.size() + 1;
    }

    public int homeLimit(int configuredDefault) {
        return Math.max(1, configuredDefault) + purchasedHomeSlots;
    }

    public Set<UUID> memberSet() {
        LinkedHashSet<UUID> all = new LinkedHashSet<>(members);
        all.add(owner);
        return Collections.unmodifiableSet(all);
    }

    public Island withOwner(UUID value) {
        return copy(value, members, slotIndex, size, typeId, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withMembers(Collection<UUID> value) {
        return copy(owner, value == null ? List.of() : List.copyOf(value), slotIndex, size, typeId,
                difficultyId, isPublic, displayName, spawnOffset, generatorTierId, homes,
                purchasedHomeSlots, createdAt, lastActive, counters, completedChallenges,
                notifiedChallenges);
    }

    public Island withSlotIndex(int value) {
        return copy(owner, members, value, size, typeId, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withSize(IslandSize value) {
        return copy(owner, members, slotIndex, value, typeId, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withTypeId(String value) {
        return copy(owner, members, slotIndex, size, value, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withDifficultyId(String value) {
        return copy(owner, members, slotIndex, size, typeId, value, isPublic, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withPublic(boolean value) {
        return copy(owner, members, slotIndex, size, typeId, difficultyId, value, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withIsPublic(boolean value) {
        return withPublic(value);
    }

    public Island withDisplayName(String value) {
        return copy(owner, members, slotIndex, size, typeId, difficultyId, isPublic, value,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withSpawnOffset(SpawnOffset value) {
        return copy(owner, members, slotIndex, size, typeId, difficultyId, isPublic, displayName,
                value, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withGeneratorTierId(String value) {
        return copy(owner, members, slotIndex, size, typeId, difficultyId, isPublic, displayName,
                spawnOffset, value, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withHomes(Map<UUID, Map<String, Home>> value) {
        return copy(owner, members, slotIndex, size, typeId, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, value, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withPurchasedHomeSlots(int value) {
        return copy(owner, members, slotIndex, size, typeId, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, homes, value, createdAt, lastActive, counters,
                completedChallenges, notifiedChallenges);
    }

    public Island withCreatedAt(Instant value) {
        return copy(owner, members, slotIndex, size, typeId, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, value, lastActive,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withLastActive(Instant value) {
        return copy(owner, members, slotIndex, size, typeId, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, value,
                counters, completedChallenges, notifiedChallenges);
    }

    public Island withCounters(Map<String, Long> value) {
        return copy(owner, members, slotIndex, size, typeId, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                value, completedChallenges, notifiedChallenges);
    }

    public Island withCompletedChallenges(Set<String> value) {
        return copy(owner, members, slotIndex, size, typeId, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, value, notifiedChallenges);
    }

    public Island withNotifiedChallenges(Set<String> value) {
        return copy(owner, members, slotIndex, size, typeId, difficultyId, isPublic, displayName,
                spawnOffset, generatorTierId, homes, purchasedHomeSlots, createdAt, lastActive,
                counters, completedChallenges, value);
    }

    private Island copy(UUID newOwner, Collection<UUID> newMembers, int newSlotIndex,
                        IslandSize newSize, String newTypeId, String newDifficultyId,
                        boolean newPublic, String newDisplayName, SpawnOffset newSpawnOffset,
                        String newGeneratorTierId, Map<UUID, Map<String, Home>> newHomes,
                        int newPurchasedHomeSlots, Instant newCreatedAt, Instant newLastActive,
                        Map<String, Long> newCounters, Set<String> newCompletedChallenges,
                        Set<String> newNotifiedChallenges) {
        return new Island(id, newOwner, newMembers == null ? List.of() : List.copyOf(newMembers),
                newSlotIndex, newSize, newTypeId, newDifficultyId, newPublic, newDisplayName,
                newSpawnOffset, newGeneratorTierId, newHomes, newPurchasedHomeSlots, newCreatedAt,
                newLastActive, newCounters, newCompletedChallenges, newNotifiedChallenges);
    }

    private static List<UUID> immutableMembers(UUID owner, Collection<UUID> values) {
        if (values == null || values.isEmpty()) return List.of();
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        for (UUID value : values) {
            UUID member = Objects.requireNonNull(value, "members cannot contain null");
            if (!owner.equals(member)) unique.add(member);
        }
        return List.copyOf(unique);
    }

    private static Map<UUID, Map<String, Home>> immutableHomes(Map<UUID, Map<String, Home>> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<UUID, Map<String, Home>> copy = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, Home>> entry : values.entrySet()) {
            UUID player = Objects.requireNonNull(entry.getKey(), "home owner cannot be null");
            Map<String, Home> playerHomes = new LinkedHashMap<>();
            if (entry.getValue() != null) {
                for (Map.Entry<String, Home> home : entry.getValue().entrySet()) {
                    String name = Objects.requireNonNull(home.getKey(), "home name cannot be null")
                            .toLowerCase(Locale.ROOT);
                    if (name.isBlank()) throw new IllegalArgumentException("home name cannot be blank");
                    playerHomes.put(name, Objects.requireNonNull(home.getValue(), "home cannot be null"));
                }
            }
            if (!playerHomes.isEmpty()) copy.put(player, Collections.unmodifiableMap(playerHomes));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Long> immutableCounterMap(Map<String, Long> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "counter key cannot be null");
            if (key.isBlank()) throw new IllegalArgumentException("counter key cannot be blank");
            long value = Objects.requireNonNull(entry.getValue(), "counter value cannot be null");
            if (value < 0) throw new IllegalArgumentException("counter values cannot be negative");
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<String> immutableStringSet(Collection<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        Set<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = Objects.requireNonNull(value, "challenge id cannot be null");
            if (normalized.isBlank()) throw new IllegalArgumentException("challenge id cannot be blank");
            copy.add(normalized);
        }
        return Collections.unmodifiableSet(copy);
    }

    private static String nonBlankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record SpawnOffset(int x, int y, int z) {
    }

    public record Home(String world, double x, double y, double z, float yaw, float pitch) {
        public Home {
            if (world == null || world.isBlank()) throw new IllegalArgumentException("home world cannot be blank");
        }
    }
}
