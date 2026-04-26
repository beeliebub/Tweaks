package me.beeliebub.tweaks.enchantments.quality;

import java.util.concurrent.ThreadLocalRandom;

// Represents the quality tier of an enchantment above the default (common) tier.
// Each tier has a weight for the enchanting table roll, re-roll count for fortune/looting,
// and area radius for efficacy/tunneller.
public enum QualityTier {

    UNCOMMON("uncommon", 0.70, 1, 2),
    RARE("rare",         0.20, 2, 3),
    EPIC("epic",         0.09, 3, 4),
    LEGENDARY("legendary", 0.01, 5, 5);

    private final String prefix;
    private final double weight;
    private final int rerolls;
    private final int areaRadius;

    QualityTier(String prefix, double weight, int rerolls, int areaRadius) {
        this.prefix = prefix;
        this.weight = weight;
        this.rerolls = rerolls;
        this.areaRadius = areaRadius;
    }

    public String getPrefix() { return prefix; }
    public double getWeight() { return weight; }
    public int getRerolls() { return rerolls; }
    public int getAreaRadius() { return areaRadius; }

    // Roll a random quality tier using the weighted distribution (70/20/9/1)
    public static QualityTier rollTier() {
        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0;
        for (QualityTier tier : values()) {
            cumulative += tier.weight;
            if (roll < cumulative) return tier;
        }
        return UNCOMMON;
    }
}