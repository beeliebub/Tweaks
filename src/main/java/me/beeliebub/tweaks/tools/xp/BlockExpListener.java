package me.beeliebub.tweaks.tools.xp;

import me.beeliebub.tweaks.utils.BlockTaintStore;
import me.beeliebub.tweaks.utils.ExternalBlockBreakHook;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Adds the configured independent XP rolls to eligible block paths. */
public final class BlockExpListener implements Listener, ExternalBlockBreakHook {

    private final XpSettings settings;
    private final BlockTaintStore taintStore;
    private final Map<String, Long> recentCropBreaks = new ConcurrentHashMap<>();

    public BlockExpListener(XpSettings settings, BlockTaintStore taintStore) {
        this.settings = settings;
        this.taintStore = taintStore;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!settings.customDropsEnabled() || settings.amount() <= 0) return;
        Block block = event.getBlock();
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            if (ageable.getAge() == ageable.getMaximumAge()) {
                long now = System.currentTimeMillis();
                recentCropBreaks.entrySet().removeIf(entry -> now - entry.getValue() > 5_000L);
                // Replant invokes its hook after the same BlockBreakEvent. Record that
                // evaluation so the hook can affirm the already-counted harvest without
                // creating a second independent roll.
                recentCropBreaks.put(blockKey(block), now);
                if (roll(settings.cropChance())) {
                    event.setExpToDrop(event.getExpToDrop() + settings.amount());
                }
            }
            return;
        }
        Material material = block.getType();
        if (settings.leafMaterials().contains(material)) {
            if (taintStore.consume(block)) return;
            if (roll(settings.leafBreakChance())) {
                event.setExpToDrop(event.getExpToDrop() + settings.amount());
            }
            return;
        }
        if (settings.stoneMaterials().contains(material)) {
            if (taintStore.consume(block)) return;
            if (roll(settings.stoneChance())) {
                event.setExpToDrop(event.getExpToDrop() + settings.amount());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material material = event.getBlockPlaced().getType();
        if (settings.leafMaterials().contains(material) || settings.stoneMaterials().contains(material)) {
            taintStore.mark(event.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (!settings.customDropsEnabled() || settings.amount() <= 0) return;
        Block block = event.getBlock();
        if (!settings.leafMaterials().contains(block.getType())) return;
        if (taintStore.consume(block)) return;
        if (roll(settings.leafDecayChance())) spawnXp(block, settings.amount());
    }

    /** Replant invokes this after the crop is placed at age zero; it represents its harvested crop. */
    public void onReplantedCrop(Block block) {
        if (!settings.customDropsEnabled() || settings.amount() <= 0) return;
        Long originalBreak = block == null ? null : recentCropBreaks.remove(blockKey(block));
        if (originalBreak != null && System.currentTimeMillis() - originalBreak <= 10_000L) return;
        if (block != null && roll(settings.cropChance())) spawnXp(block, settings.amount());
    }

    /** Called by Tunneller/Lumberjack for collateral blocks that bypass BlockBreakEvent. */
    @Override
    public void onExternalBreak(org.bukkit.entity.Player player, Block block, Material originalMaterial) {
        if (!settings.customDropsEnabled() || settings.amount() <= 0 || block == null) return;
        if (settings.leafMaterials().contains(originalMaterial)) {
            if (taintStore.consume(block)) return;
            if (roll(settings.leafBreakChance())) spawnXp(block, settings.amount());
        } else if (settings.stoneMaterials().contains(originalMaterial)) {
            if (taintStore.consume(block)) return;
            if (roll(settings.stoneChance())) spawnXp(block, settings.amount());
        }
    }

    private static boolean roll(double chancePercent) {
        return chancePercent >= 100.0
                || (chancePercent > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < chancePercent);
    }

    private static void spawnXp(Block block, int amount) {
        block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), ExperienceOrb.class,
                orb -> orb.setExperience(amount));
    }

    private static String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
