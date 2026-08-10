package me.beeliebub.tweaks.skyblock.generator;

import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Weighted cobblestone output table for one island generator tier. */
public record GeneratorTier(String id, String displayName, Map<Material, Double> outputs) {
    public GeneratorTier {
        Objects.requireNonNull(id, "id");
        id = id.trim().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid generator tier: " + id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        Map<Material, Double> copy = new LinkedHashMap<>();
        if (outputs != null) {
            for (Map.Entry<Material, Double> entry : outputs.entrySet()) {
                Material material = Objects.requireNonNull(entry.getKey(), "generator material");
                double weight = Objects.requireNonNull(entry.getValue(), "generator weight");
                if (material.isAir() || !Double.isFinite(weight) || weight <= 0) {
                    throw new IllegalArgumentException("Generator weights must be finite and positive");
                }
                copy.put(material, weight);
            }
        }
        // Empty output tables are allowed while an administrator is authoring a new tier. The
        // readiness checker rejects them, and the generator listener simply leaves the default
        // block state unchanged until an output is added.
        double total = copy.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!copy.isEmpty() && (!Double.isFinite(total) || total <= 0)) {
            throw new IllegalArgumentException("Generator weights overflow");
        }
        outputs = Collections.unmodifiableMap(copy);
    }

    public double totalWeight() {
        return outputs.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
