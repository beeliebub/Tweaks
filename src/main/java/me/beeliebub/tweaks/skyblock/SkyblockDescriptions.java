package me.beeliebub.tweaks.skyblock;

import me.beeliebub.tweaks.skyblock.challenge.ChallengeRequirement;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeReward;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import me.beeliebub.tweaks.skyblock.type.IslandDifficulty;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Plain-text descriptions shared by administrative and player-facing surfaces. */
public final class SkyblockDescriptions {

    private SkyblockDescriptions() {
    }

    public static String requirement(ChallengeRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        return switch (requirement) {
            case ChallengeRequirement.Tracked tracked ->
                    "Track " + tracked.key().format() + " x" + tracked.amount();
            case ChallengeRequirement.Possession possession ->
                    "Have " + possession.amount() + " " + pretty(possession.material().name());
        };
    }

    public static String reward(ChallengeReward reward) {
        Objects.requireNonNull(reward, "reward");
        return switch (reward) {
            case ChallengeReward.Items items -> "Items: " + items.items().stream()
                    .map(SkyblockDescriptions::item).collect(Collectors.joining(", "));
            case ChallengeReward.SizeUpgrade size -> "Upgrade island size to " + size.size().name();
            case ChallengeReward.GeneratorUnlock generator ->
                    "Unlock generator tier " + generator.tierId();
            case ChallengeReward.Money money -> "Receive " + number(money.amount()) + " Skybucks";
        };
    }

    public static String generatorOutput(GeneratorTier tier, Map.Entry<Material, Double> output) {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(output, "output");
        return generatorOutput(output.getKey(), output.getValue(), tier.totalWeight());
    }

    public static String generatorOutput(Material material, double weight, double totalWeight) {
        Objects.requireNonNull(material, "material");
        double share = totalWeight <= 0 ? 0.0 : weight / totalWeight * 100.0;
        return pretty(material.name()) + " - " + number(weight)
                + " weight (" + String.format(Locale.ROOT, "%.1f", share) + "%)";
    }

    public static String shopEntry(ShopCatalog.Entry entry) {
        Objects.requireNonNull(entry, "entry");
        String buy = entry.buyable() ? number(entry.buyPrice()) : "disabled";
        String sell = entry.sellable() ? number(entry.sellPrice()) : "disabled";
        return pretty(entry.material().name()) + " - " + entry.category()
                + ", buy " + buy + ", sell " + sell;
    }

    public static String islandType(IslandType type) {
        Objects.requireNonNull(type, "type");
        String template = type.templateId().isBlank() ? "no template" : "template " + type.templateId();
        return type.displayName() + " (" + type.id() + ") - " + template
                + ", " + type.difficultyIds().size() + " difficulty option(s), "
                + type.kit().size() + " kit item(s)";
    }

    public static String difficulty(IslandDifficulty difficulty) {
        Objects.requireNonNull(difficulty, "difficulty");
        return difficulty.displayName() + " (" + difficulty.id() + ") x"
                + number(difficulty.multiplier()) + ", order " + difficulty.order();
    }

    public static String material(Material material) {
        return pretty(Objects.requireNonNull(material, "material").name());
    }

    public static String entity(String entityType) {
        return pretty(Objects.requireNonNull(entityType, "entityType"));
    }

    private static String item(ItemStack item) {
        return item.getAmount() + " " + pretty(item.getType().name());
    }

    private static String pretty(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String number(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
