package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.FortuneQualityListener;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import me.beeliebub.tweaks.enchantments.quality.SilkTouchQualityListener;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

// Breaks a 3x3 area of blocks perpendicular to the face you're mining.
// Integrates with Smelter, GemConnoisseur, and Telekinesis enchantments, and drops correct XP.
public class Tunneller implements Listener {

    private static final double RAY_DISTANCE = 6.0;

    private final Enchantment enchantment;
    private final Telekinesis telekinesis;
    private final Smelter smelter;
    private final GemConnoisseur gemConnoisseur;
    private final QualityRegistry qualityRegistry;
    private final FortuneQualityListener fortuneQuality;
    private final SilkTouchQualityListener silkTouchQuality;

    public Tunneller(Tweaks plugin, Telekinesis telekinesis, Smelter smelter,
                     GemConnoisseur gemConnoisseur, QualityRegistry qualityRegistry,
                     FortuneQualityListener fortuneQuality, SilkTouchQualityListener silkTouchQuality) {
        String raw = plugin.getConfig().getString("tunneller");
        this.enchantment = resolveEnchantment(plugin, raw);
        this.telekinesis = telekinesis;
        this.smelter = smelter;
        this.gemConnoisseur = gemConnoisseur;
        this.qualityRegistry = qualityRegistry;
        this.fortuneQuality = fortuneQuality;
        this.silkTouchQuality = silkTouchQuality;
    }

    private Enchantment resolveEnchantment(Tweaks plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning("No 'tunneller' key configured; tunneller enchant disabled.");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid tunneller key '" + raw + "'; tunneller enchant disabled.");
            return null;
        }
        Enchantment resolved = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (resolved == null) {
            plugin.getLogger().warning("Tunneller enchantment '" + raw + "' not found in registry; is the data pack loaded?");
        }
        return resolved;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty()) return;

        // Determine radius: base enchantment = 1 (3x3), quality tiers = 2-5 (5x5 to 11x11)
        int radius = getRadius(tool);
        if (radius <= 0) return;

        RayTraceResult trace = player.rayTraceBlocks(RAY_DISTANCE);
        if (trace == null) return;
        BlockFace face = trace.getHitBlockFace();
        if (face == null) return;

        int[][] axes = axesForFace(face);
        if (axes == null) return;
        int[] a1 = axes[0];
        int[] a2 = axes[1];

        Block origin = event.getBlock();
        boolean useSmelter = smelter != null && smelter.hasEnchant(tool);
        boolean useTelekinesis = telekinesis != null && telekinesis.hasEnchant(tool);
        boolean useGemConnoisseur = gemConnoisseur != null && gemConnoisseur.hasEnchant(tool);
        // Quality fortune re-rolls require the manual break path (breakNaturally
        // bypasses our re-roll hook). Detect it once per event for the loop.
        boolean useFortuneReroll = fortuneQuality != null
                && qualityRegistry != null
                && qualityRegistry.getToolQuality(tool, "fortune") != null;
        boolean useSilkQuality = silkTouchQuality != null
                && qualityRegistry != null
                && qualityRegistry.getToolQuality(tool, "silk_touch") != null;

        int blocksbroken = 0;
        for (int u = -radius; u <= radius; u++) {
            for (int v = -radius; v <= radius; v++) {
                if (u == 0 && v == 0) continue;
                Block target = origin.getRelative(
                        a1[0] * u + a2[0] * v,
                        a1[1] * u + a2[1] * v,
                        a1[2] * u + a2[2] * v
                );
                if (breakBlock(target, tool, player, useSmelter, useTelekinesis, useGemConnoisseur, useFortuneReroll, useSilkQuality)) {
                    blocksbroken++;
                }
            }
        }

        if (blocksbroken > 0) {
            int unbreakingLevel = qualityRegistry != null
                    ? qualityRegistry.getEffectiveUnbreakingLevel(tool)
                    : tool.getEnchantmentLevel(Enchantment.UNBREAKING);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int damageToApply = 0;
            for (int i = 0; i < blocksbroken; i++) {
                if (unbreakingLevel <= 0 || random.nextInt(unbreakingLevel + 1) == 0) {
                    damageToApply++;
                }
            }
            if (damageToApply > 0) {
                player.damageItemStack(EquipmentSlot.HAND, damageToApply);
            }
        }
    }

    // Determine the mining radius from the tool's tunneller enchantment.
    // Returns 1 for common (3x3), 2-5 for quality tiers, or 0 if no tunneller enchant found.
    private int getRadius(ItemStack tool) {
        if (enchantment != null && tool.containsEnchantment(enchantment)) return 1;
        if (qualityRegistry != null) {
            QualityTier tier = qualityRegistry.getToolQualityTier(tool, "tunneller");
            if (tier != null) return tier.getAreaRadius();
        }
        return 0;
    }

    // Break a single block, applying smelter/gem drops and routing to inventory or ground.
    // Returns true if a block was actually broken.
    private boolean breakBlock(Block target, ItemStack tool, Player player, boolean useSmelter, boolean useTelekinesis, boolean useGemConnoisseur, boolean useFortuneReroll, boolean useSilkQuality) {
        Material type = target.getType();
        if (type.isAir() || target.isLiquid()) return false;

        if (type.getHardness() < 0) {
            // Reinforced Deepslate can only be broken by Epic+ Silk Touch
            if (type == Material.REINFORCED_DEEPSLATE && useSilkQuality) {
                QualityTier tier = qualityRegistry.getToolQualityTier(tool, "silk_touch");
                if (tier == null || tier.ordinal() < QualityTier.EPIC.ordinal()) return false;
            } else {
                return false;
            }
        }

        // No modifiers and no quality fortune/silk: breakNaturally handles drops, break effect, and XP in one call
        if (!useSmelter && !useGemConnoisseur && !useTelekinesis && !useFortuneReroll && !useSilkQuality) {
            target.breakNaturally(tool, true, true);
            return true;
        }

        // Manual path so we can apply quality fortune/silk re-rolls and/or smelter/gem/telekinesis
        Collection<ItemStack> drops = target.getDrops(tool, player);
        // Apply Silk Touch quality drops
        if (useSilkQuality) drops = silkTouchQuality.applySilkQuality(target, tool, drops);
        // Apply fortune re-rolls before smelter so smelter sees the boosted drop count
        if (useFortuneReroll) drops = fortuneQuality.applyFortuneRerolls(target, tool, player, drops);
        if (useSmelter) drops = Smelter.smeltDrops(drops);

        List<ItemStack> gemDrops = List.of();
        if (useGemConnoisseur && !drops.isEmpty()) {
            gemDrops = gemConnoisseur.rollDrops(type, tool);
        }

        // Clear the block and play the break particle effect
        Location loc = target.getLocation();
        target.setType(Material.AIR);
        target.getWorld().playEffect(loc, Effect.STEP_SOUND, type);

        // Drop or route items to inventory (telekinesis path consults the player's /itemfilter)
        if (useTelekinesis) {
            for (ItemStack drop : drops) telekinesis.giveOrDrop(player, target, drop);
            for (ItemStack drop : gemDrops) telekinesis.giveOrDrop(player, target, drop);
        } else {
            for (ItemStack drop : drops) loc.getWorld().dropItemNaturally(loc, drop);
            for (ItemStack drop : gemDrops) loc.getWorld().dropItemNaturally(loc, drop);
        }
        return true;
    }

    // Get the two axes perpendicular to the mined face (used to find the 3x3 grid)
    private int[][] axesForFace(BlockFace face) {
        return switch (face) {
            case UP, DOWN -> new int[][]{{1, 0, 0}, {0, 0, 1}};
            case NORTH, SOUTH -> new int[][]{{1, 0, 0}, {0, 1, 0}};
            case EAST, WEST -> new int[][]{{0, 0, 1}, {0, 1, 0}};
            default -> null;
        };
    }
}