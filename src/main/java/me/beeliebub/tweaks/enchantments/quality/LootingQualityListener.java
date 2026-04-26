package me.beeliebub.tweaks.enchantments.quality;

import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

// Handles looting quality re-rolls.
// Supports both "Quantity" drops (increasing counts of common items) and
// "Probability" drops (adding rare items like Skulls/Tridents if the initial roll failed).
public class LootingQualityListener implements Listener {

    public enum DropType {
        QUANTITY,    // Normal drops: base = random(minBase, maxBase); count = base + random(0, lootingLevel)
        PROBABILITY  // Rare drops: chance = baseChance + (lootingBonus * lootingLevel); max drops = 1
    }

    private record LootDropInfo(
            Material item,
            DropType type,
            int minBase,
            int maxBase,
            double baseChance,
            double lootingBonus,
            Predicate<LivingEntity> condition
    ) {
        // Convenience method for common quantity items
        public static LootDropInfo quantity(Material item, int min, int max) {
            return new LootDropInfo(item, DropType.QUANTITY, min, max, 0, 0, e -> true);
        }

        // Convenience method for unconditional rare chance items
        public static LootDropInfo probability(Material item, double baseChance, double lootingBonus) {
            return new LootDropInfo(item, DropType.PROBABILITY, 0, 1, baseChance, lootingBonus, e -> true);
        }

        // Convenience method for conditional rare chance items (e.g. Tridents)
        public static LootDropInfo probability(Material item, double baseChance, double lootingBonus, Predicate<LivingEntity> condition) {
            return new LootDropInfo(item, DropType.PROBABILITY, 0, 1, baseChance, lootingBonus, condition);
        }
    }

    // Maps EntityType -> List of possible loot drops and their exact vanilla formulas
    private static final Map<EntityType, List<LootDropInfo>> LOOT_TABLES;

    static {
        Map<EntityType, List<LootDropInfo>> m = new EnumMap<>(EntityType.class);

        // Helper to cleanly assign drops to one or multiple entities
        java.util.function.BiConsumer<EntityType[], LootDropInfo[]> register = (entities, drops) -> {
            for (EntityType type : entities) {
                m.computeIfAbsent(type, k -> new ArrayList<>()).addAll(Arrays.asList(drops));
            }
        };

        // ==========================================
        // UNDEAD MOBS
        // ==========================================
        register.accept(new EntityType[]{EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.ROTTEN_FLESH, 0, 2),
                LootDropInfo.probability(Material.IRON_INGOT, 0.025, 0.01),
                LootDropInfo.probability(Material.CARROT, 0.025, 0.01),
                LootDropInfo.probability(Material.POTATO, 0.025, 0.01)
        });

        register.accept(new EntityType[]{EntityType.DROWNED}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.ROTTEN_FLESH, 0, 2),
                LootDropInfo.probability(Material.COPPER_INGOT, 0.11, 0.02),
                LootDropInfo.probability(Material.TRIDENT, 0.085, 0.01, e -> {
                    if (e.getEquipment() == null) return false;
                    // Drowned only drop tridents if they are holding one
                    Material main = e.getEquipment().getItemInMainHand().getType();
                    Material off = e.getEquipment().getItemInOffHand().getType();
                    return main == Material.TRIDENT || off == Material.TRIDENT;
                })
        });

        register.accept(new EntityType[]{EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.BONE, 0, 2),
                LootDropInfo.quantity(Material.ARROW, 0, 2)
        });

        register.accept(new EntityType[]{EntityType.WITHER_SKELETON}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.BONE, 0, 2),
                LootDropInfo.quantity(Material.COAL, 0, 1),
                LootDropInfo.probability(Material.WITHER_SKELETON_SKULL, 0.025, 0.01)
        });

        register.accept(new EntityType[]{EntityType.PHANTOM}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.PHANTOM_MEMBRANE, 0, 1)
        });

        // ==========================================
        // HOSTILE MOBS
        // ==========================================
        register.accept(new EntityType[]{EntityType.CREEPER}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.GUNPOWDER, 0, 2)
        });

        register.accept(new EntityType[]{EntityType.SPIDER, EntityType.CAVE_SPIDER}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.STRING, 0, 2),
                LootDropInfo.quantity(Material.SPIDER_EYE, 0, 1) // Only drops on player kill (which this event ensures)
        });

        register.accept(new EntityType[]{EntityType.ENDERMAN}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.ENDER_PEARL, 0, 1)
        });

        register.accept(new EntityType[]{EntityType.BLAZE}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.BLAZE_ROD, 0, 1)
        });

        register.accept(new EntityType[]{EntityType.GHAST}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.GHAST_TEAR, 0, 1),
                LootDropInfo.quantity(Material.GUNPOWDER, 0, 2)
        });

        register.accept(new EntityType[]{EntityType.MAGMA_CUBE}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.MAGMA_CREAM, 0, 1)
        });

        register.accept(new EntityType[]{EntityType.SLIME}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.SLIME_BALL, 0, 2)
        });

        register.accept(new EntityType[]{EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.PRISMARINE_SHARD, 0, 2),
                LootDropInfo.quantity(Material.PRISMARINE_CRYSTALS, 0, 1)
        });

        register.accept(new EntityType[]{EntityType.SHULKER}, new LootDropInfo[]{
                LootDropInfo.probability(Material.SHULKER_SHELL, 0.50, 0.0625)
        });

        register.accept(new EntityType[]{EntityType.BREEZE}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.BREEZE_ROD, 1, 2)
        });

        register.accept(new EntityType[]{EntityType.WITCH}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.GLASS_BOTTLE, 0, 2),
                LootDropInfo.quantity(Material.GLOWSTONE_DUST, 0, 2),
                LootDropInfo.quantity(Material.GUNPOWDER, 0, 2),
                LootDropInfo.quantity(Material.REDSTONE, 0, 2),
                LootDropInfo.quantity(Material.SPIDER_EYE, 0, 2),
                LootDropInfo.quantity(Material.SUGAR, 0, 2),
                LootDropInfo.quantity(Material.STICK, 0, 2)
        });

        // ==========================================
        // PASSIVE MOBS
        // ==========================================
        register.accept(new EntityType[]{EntityType.COW, EntityType.MOOSHROOM}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.BEEF, 1, 3),
                LootDropInfo.quantity(Material.LEATHER, 0, 2)
        });

        register.accept(new EntityType[]{EntityType.PIG, EntityType.HOGLIN}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.PORKCHOP, 1, 3)
        });

        register.accept(new EntityType[]{EntityType.CHICKEN}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.CHICKEN, 1, 1),
                LootDropInfo.quantity(Material.FEATHER, 0, 2)
        });

        register.accept(new EntityType[]{EntityType.SHEEP}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.MUTTON, 1, 2)
        });

        register.accept(new EntityType[]{EntityType.RABBIT}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.RABBIT, 0, 1),
                LootDropInfo.quantity(Material.RABBIT_HIDE, 0, 1),
                LootDropInfo.probability(Material.RABBIT_FOOT, 0.10, 0.03) // Base 10%, +3% per level
        });

        register.accept(new EntityType[]{EntityType.SQUID}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.INK_SAC, 1, 3)
        });

        register.accept(new EntityType[]{EntityType.GLOW_SQUID}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.GLOW_INK_SAC, 1, 3)
        });

        // ==========================================
        // NETHER RESIDENTS
        // ==========================================
        register.accept(new EntityType[]{EntityType.ZOMBIFIED_PIGLIN}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.ROTTEN_FLESH, 0, 1),
                LootDropInfo.quantity(Material.GOLD_NUGGET, 0, 1),
                LootDropInfo.probability(Material.GOLD_INGOT, 0.025, 0.01)
        });

        register.accept(new EntityType[]{EntityType.ZOGLIN}, new LootDropInfo[]{
                LootDropInfo.quantity(Material.ROTTEN_FLESH, 1, 3)
        });

        LOOT_TABLES = Map.copyOf(m);
    }

    private final QualityRegistry registry;

    public LootingQualityListener(QualityRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        // Baby mobs drop nothing in vanilla; don't add drops via re-rolls
        LivingEntity entity = event.getEntity();
        if (entity instanceof Ageable ageable && !ageable.isAdult()) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon.isEmpty()) return;

        QualityRegistry.QualityInfo quality = registry.getToolQuality(weapon, "looting");
        if (quality == null) return;

        List<LootDropInfo> possibleDrops = LOOT_TABLES.get(entity.getType());
        if (possibleDrops == null || possibleDrops.isEmpty()) return;

        int lootingLevel = quality.level();
        int rerolls = quality.tier().getRerolls();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (LootDropInfo info : possibleDrops) {
            // Check condition (e.g. Drowned must hold a Trident to drop one)
            if (!info.condition().test(event.getEntity())) continue;

            // See if the item natively dropped and capture it to modify
            ItemStack existingDrop = null;
            for (ItemStack drop : event.getDrops()) {
                if (drop.getType() == info.item()) {
                    existingDrop = drop;
                    break;
                }
            }

            int currentCount = existingDrop != null ? existingDrop.getAmount() : 0;
            int bestCount = currentCount;

            // Process based on drop logic
            if (info.type() == DropType.QUANTITY) {
                int maxPossible = info.maxBase() + lootingLevel;
                if (currentCount >= maxPossible) continue;

                for (int r = 0; r < rerolls; r++) {
                    int base = info.minBase() + random.nextInt(info.maxBase() - info.minBase() + 1);
                    int bonus = random.nextInt(lootingLevel + 1);
                    int simulated = base + bonus;
                    bestCount = Math.max(bestCount, simulated);
                }

            } else if (info.type() == DropType.PROBABILITY) {
                // Probabilistic drops are 0 or 1
                if (currentCount >= 1) continue;

                double successChance = info.baseChance() + (info.lootingBonus() * lootingLevel);
                for (int r = 0; r < rerolls; r++) {
                    if (random.nextDouble() < successChance) {
                        bestCount = 1;
                        break; // No need to roll further, max reached
                    }
                }
            }

            // Apply best result
            if (bestCount > currentCount) {
                if (existingDrop != null) {
                    existingDrop.setAmount(bestCount);
                } else {
                    // Item didn't initially drop, but successfully rolled via our plugin. Add it!
                    event.getDrops().add(new ItemStack(info.item(), bestCount));
                }
            }
        }
    }
}