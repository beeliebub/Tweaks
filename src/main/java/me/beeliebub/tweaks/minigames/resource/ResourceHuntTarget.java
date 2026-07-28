package me.beeliebub.tweaks.minigames.resource;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Objects;

/** Immutable configuration and per-player target data for Resource Hunt. */
final class ResourceHuntTarget {
    private ResourceHuntTarget() {
    }

    static int[] computeTierThresholds(int base, double multiplier, int numberOfTiers) {
        int[] thresholds = new int[numberOfTiers];
        double scaled = base;
        for (int index = 0; index < numberOfTiers; index++) {
            int threshold = (int) Math.round(scaled);
            if (index > 0 && threshold <= thresholds[index - 1]) {
                threshold = thresholds[index - 1] + 1;
            }
            thresholds[index] = threshold;
            scaled *= multiplier;
        }
        return thresholds;
    }

    static final class Definition {
        final ResourceHunt.Category category;
        final Material material;
        final EntityType entityType;
        final int amount;
        final double multiplier;
        final String worldKey;
        final DyeColor sheepColor;

        Definition(ResourceHunt.Category category, Material material, EntityType entityType,
                   int amount, double multiplier, String worldKey, DyeColor sheepColor) {
            this.category = category;
            this.material = material;
            this.entityType = entityType;
            this.amount = amount;
            this.multiplier = multiplier;
            this.worldKey = worldKey;
            this.sheepColor = sheepColor;
        }

        Object identityKey() {
            return sheepColor != null ? Objects.hash(entityType, sheepColor)
                    : material != null ? material : entityType;
        }
    }

    static final class Player {
        final ResourceHunt.Category category;
        final Material material;
        final EntityType entityType;
        final int amount;
        final double multiplier;
        final int[] tierThresholds;
        final DyeColor sheepColor;

        Player(Definition target, int numberOfTiers) {
            this.category = target.category;
            this.material = target.material;
            this.entityType = target.entityType;
            this.amount = target.amount;
            this.multiplier = target.multiplier;
            this.tierThresholds = computeTierThresholds(target.amount, target.multiplier, numberOfTiers);
            this.sheepColor = target.sheepColor;
        }
    }
}
