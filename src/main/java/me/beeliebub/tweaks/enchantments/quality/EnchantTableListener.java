package me.beeliebub.tweaks.enchantments.quality;

import me.beeliebub.tweaks.managers.BloodMoonManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// Intercepts enchanting table results and rolls a per-enchantment chance to upgrade
// to a quality variant (uncommon/rare/epic/legendary). The roll chance is boosted
// while a Blood Moon event is active.
public class EnchantTableListener implements Listener {

    private static final double QUALITY_CHANCE = 0.10;
    private static final double BLOOD_MOON_QUALITY_CHANCE = 0.50;

    private final QualityRegistry registry;
    private final BloodMoonManager bloodMoon;

    public EnchantTableListener(QualityRegistry registry, BloodMoonManager bloodMoon) {
        this.registry = registry;
        this.bloodMoon = bloodMoon;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Map<Enchantment, Integer> enchantsToAdd = event.getEnchantsToAdd();
        List<Enchantment> toRemove = new ArrayList<>();
        Map<Enchantment, Integer> toAdd = new HashMap<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double chance = bloodMoon.isActive() ? BLOOD_MOON_QUALITY_CHANCE : QUALITY_CHANCE;

        for (var entry : enchantsToAdd.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();

            String name = registry.matchEnchantName(enchant);
            if (name == null) continue;

            if (random.nextDouble() >= chance) continue;

            QualityTier tier = QualityTier.rollTier();
            Enchantment qualityEnchant = registry.getVariant(name, tier);
            if (qualityEnchant == null) continue;

            int cappedLevel = Math.min(level, qualityEnchant.getMaxLevel());
            toRemove.add(enchant);
            toAdd.put(qualityEnchant, cappedLevel);

            if (tier == QualityTier.EPIC || tier == QualityTier.LEGENDARY) {
                announceQualityRoll(event.getEnchanter(), tier, name, cappedLevel, qualityEnchant.getMaxLevel());
            }
        }

        for (Enchantment e : toRemove) {
            enchantsToAdd.remove(e);
        }
        enchantsToAdd.putAll(toAdd);
    }

    private void announceQualityRoll(Player player, QualityTier tier, String enchantName,
                                     int level, int maxLevel) {
        NamedTextColor tierColor = tier == QualityTier.LEGENDARY
                ? NamedTextColor.GOLD
                : NamedTextColor.LIGHT_PURPLE;
        String article = tier == QualityTier.EPIC ? "an" : "a";
        // Match vanilla: single-level enchants (e.g. Multishot, Channeling) don't show a numeral.
        String levelSuffix = maxLevel > 1 ? " " + roman(level) : "";

        Component message = Component.text(player.getName(), NamedTextColor.WHITE)
                .append(Component.text(" just rolled " + article + " ", NamedTextColor.GRAY))
                .append(Component.text(tier.name() + " " + prettyEnchantName(enchantName) + levelSuffix + "!", tierColor)
                        .decorate(TextDecoration.BOLD));

        Bukkit.getServer().broadcast(message);
    }

    // "luck_of_the_sea" -> "Luck Of The Sea"
    private static String prettyEnchantName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            sb.append(parts[i].substring(1));
        }
        return sb.toString();
    }

    // Roman numerals for 1..10 (covers all vanilla and current quality enchant levels).
    // Levels outside that range fall back to arabic so we never silently truncate.
    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(level);
        };
    }
}