package me.beeliebub.tweaks.skyblock.type;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable island type definition and its optional starting kit. */
public record IslandType(
        String id,
        String displayName,
        Set<String> difficultyIds,
        String templateId,
        List<KitItem> kit,
        String biome,
        Set<String> allowedChallengeIds
) {
    public static final int KIT_CONTAINER_SIZE = 54;

    public IslandType {
        id = normalize(id, "type id");
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        LinkedHashSet<String> difficulties = new LinkedHashSet<>();
        if (difficultyIds != null) {
            for (String difficulty : difficultyIds) difficulties.add(normalize(difficulty, "difficulty id"));
        }
        difficultyIds = Collections.unmodifiableSet(difficulties);
        templateId = templateId == null ? "" : templateId.trim();
        List<KitItem> items = new ArrayList<>();
        if (kit != null) items.addAll(kit.stream().map(Objects::requireNonNull).toList());
        if (items.size() > KIT_CONTAINER_SIZE) {
            throw new IllegalArgumentException("Kit cannot contain more than " + KIT_CONTAINER_SIZE + " items");
        }
        kit = List.copyOf(items);
        biome = biome == null || biome.isBlank() ? "PLAINS" : biome.trim().toUpperCase(Locale.ROOT);
        allowedChallengeIds = normalizeIds(allowedChallengeIds);
    }

    public IslandType(String id, String displayName, Set<String> difficultyIds, String templateId) {
        this(id, displayName, difficultyIds, templateId, List.of(), "PLAINS", Set.of());
    }

    public IslandType(String id, String displayName, Set<String> difficultyIds, String templateId,
                      List<KitItem> kit) {
        this(id, displayName, difficultyIds, templateId, kit, "PLAINS", Set.of());
    }

    public boolean allowsDifficulty(String difficultyId) {
        return difficultyId != null && difficultyIds.contains(difficultyId.toLowerCase(Locale.ROOT));
    }

    private static String normalize(String value, String label) {
        Objects.requireNonNull(value, label);
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return normalized;
    }

    private static Set<String> normalizeIds(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String value : values) ids.add(normalize(value, "challenge id"));
        return Collections.unmodifiableSet(ids);
    }

    public record KitItem(ItemStack itemStack) {
        public KitItem {
            Objects.requireNonNull(itemStack, "itemStack");
            itemStack = itemStack.clone();
            if (isAir(itemStack.getType())) throw new IllegalArgumentException("Kit material cannot be air");
            if (itemStack.getAmount() <= 0) throw new IllegalArgumentException("Kit amount must be positive");
            try {
                itemStack.serialize();
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Kit item meta cannot be serialized", error);
            }
        }

        public KitItem(String material, int amount) {
            this(createItemStack(material, amount));
        }

        @Override
        public ItemStack itemStack() {
            return itemStack.clone();
        }

        public String material() {
            return itemStack.getType().name().toLowerCase(Locale.ROOT);
        }

        public int amount() {
            return itemStack.getAmount();
        }

        public boolean hasMeta() {
            return !itemStack.isSimilar(new ItemStack(itemStack.getType(), itemStack.getAmount()));
        }

        private static ItemStack createItemStack(String materialName, int amount) {
            Objects.requireNonNull(materialName, "kit material");
            Material material = Material.matchMaterial(materialName.trim());
            if (material == null) throw new IllegalArgumentException("Invalid kit material: " + materialName);
            if (isAir(material)) throw new IllegalArgumentException("Kit material cannot be air");
            if (amount <= 0) throw new IllegalArgumentException("Kit amount must be positive");
            return new ItemStack(material, amount);
        }

        private static boolean isAir(Material material) {
            return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
        }
    }
}
