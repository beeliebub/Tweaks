package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.FortuneQualityListener;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.utils.ExternalBlockBreakHook;
import me.beeliebub.tweaks.utils.ExternalBlockBreakGuard;
import me.beeliebub.tweaks.utils.ExternalDurabilityHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

// Chops down entire trees (connected logs of the same type) and large mushrooms (connected
// stem + cap blocks) at once. Respects unbreaking, checks durability before felling,
// and requires an adjacent leaf (trees) or both stem and cap blocks (mushrooms).
public class Lumberjack implements Listener {

    private static final Set<Material> MUSHROOM_BLOCKS = Set.of(
            Material.MUSHROOM_STEM,
            Material.RED_MUSHROOM_BLOCK,
            Material.BROWN_MUSHROOM_BLOCK
    );
    private static final Set<Material> NETHER_LEAVES = Set.of(
            Material.NETHER_WART_BLOCK,
            Material.WARPED_WART_BLOCK,
            Material.SHROOMLIGHT
    );

    private final Tweaks plugin;
    private final Enchantment enchantment;
    private final Telekinesis telekinesis;
    private final QualityRegistry qualityRegistry;
    private final FortuneQualityListener fortuneQuality;
    private ExternalBlockBreakHook externalBlockBreakHook;
    private ExternalBlockBreakGuard externalBlockBreakGuard;
    private ExternalDurabilityHook externalDurabilityHook;

    public Lumberjack(Tweaks plugin, Telekinesis telekinesis, QualityRegistry qualityRegistry,
                      FortuneQualityListener fortuneQuality) {
        this.plugin = plugin;
        this.enchantment = EnchantmentResolver.resolve(plugin, "lumberjack", "lumberjack");
        this.telekinesis = telekinesis;
        this.qualityRegistry = qualityRegistry;
        this.fortuneQuality = fortuneQuality;
    }

    public Enchantment getEnchantment() {
        return enchantment;
    }

    public void setExternalBlockBreakHook(ExternalBlockBreakHook hook) {
        this.externalBlockBreakHook = hook;
    }

    public void setExternalBlockBreakGuard(ExternalBlockBreakGuard guard) {
        this.externalBlockBreakGuard = guard;
    }

    public void setExternalDurabilityHook(ExternalDurabilityHook hook) {
        this.externalDurabilityHook = hook;
    }

    // Package-private (not private) so a config knob test can exercise the clamp/live-read
    // behavior directly without widening visibility to public.
    int maxLogs() {
        return Math.max(1, plugin.getConfig().getInt("enchantments.lumberjack.max-logs", 256));
    }

    public Set<Block> collectConnectedLogs(Block start, Material logType) {
        return findConnected(start, Set.of(logType), maxLogs());
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (enchantment == null) return;

        Block origin = event.getBlock();
        Material originType = origin.getType();

        Set<Material> validTypes;
        boolean isMushroom;
        if (Tag.LOGS.isTagged(originType)) {
            validTypes = Set.of(originType);
            isMushroom = false;
        } else if (MUSHROOM_BLOCKS.contains(originType)) {
            validTypes = MUSHROOM_BLOCKS;
            isMushroom = true;
        } else {
            return;
        }

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty() || !tool.containsEnchantment(enchantment)) return;

        int limit = maxLogs();
        Set<Block> blocks = findConnected(origin, validTypes, limit);
        if (blocks.size() > limit) return;
        if (blocks.size() <= 1) return;
        if (isMushroom) {
            if (!isGiantMushroom(blocks)) return;
        } else {
            if (!hasAdjacentLeaf(blocks)) return;
        }

        List<Block> collateral = new ArrayList<>();
        for (Block block : blocks) {
            if (block.equals(origin)) continue;
            if (externalBlockBreakGuard == null
                    || externalBlockBreakGuard.canBreak(player, block, block.getType())) {
                collateral.add(block);
            }
        }

        int additionalBlocks = collateral.size();
        if (additionalBlocks == 0) return;
        int unbreakingLevel = qualityRegistry != null
                ? qualityRegistry.getEffectiveUnbreakingLevel(tool)
                : tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int damageToApply = 0;
        for (int i = 0; i < additionalBlocks; i++) {
            if (unbreakingLevel <= 0 || random.nextInt(unbreakingLevel + 1) == 0) {
                damageToApply++;
            }
        }

        if (!(tool.getItemMeta() instanceof Damageable meta)) return;
        int maxDurability = tool.getType().getMaxDurability();
        if (externalDurabilityHook != null) {
            if (!externalDurabilityHook.canTakeDamage(tool, damageToApply + 1)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("Your tool isn't durable enough to chop this whole tree!")
                        .color(NamedTextColor.RED));
                return;
            }
        }
        int available = maxDurability - meta.getDamage();

        if (externalDurabilityHook == null && damageToApply > available - 1) {
            event.setCancelled(true);
            String what = isMushroom ? "mushroom" : "tree";
            player.sendMessage(Component.text("Your tool isn't durable enough to chop this whole " + what + "!")
                    .color(NamedTextColor.RED));
            return;
        }

        boolean routeToInventory = telekinesis != null && telekinesis.hasEnchant(tool);
        // Quality fortune re-rolls on the additional blocks require the manual break path
        // (breakNaturally bypasses BlockDropItemEvent, so FortuneQualityListener never fires).
        // Logs/stems aren't fortune-affected, so the per-block check below skips wasted sims.
        boolean hasFortuneQuality = fortuneQuality != null
                && qualityRegistry != null
                && qualityRegistry.getToolQuality(tool, "fortune") != null;

        for (Block block : collateral) {
            breakBlock(block, tool, player, routeToInventory, hasFortuneQuality);
        }

        if (damageToApply > 0) {
            if (externalDurabilityHook != null) {
                externalDurabilityHook.applyDamage(player, org.bukkit.inventory.EquipmentSlot.HAND, damageToApply);
            } else {
                meta.setDamage(meta.getDamage() + damageToApply);
                tool.setItemMeta(meta);
            }
        }
    }

    // Break a single block. Routes to inventory if telekinesis is active, and applies quality
    // fortune re-rolls on mushroom caps (the only fortune-affected blocks Lumberjack handles).
    private void breakBlock(Block block, ItemStack tool, Player player,
                            boolean routeToInventory, boolean hasFortuneQuality) {
        Material type = block.getType();
        if (type.isAir()) return;
        if (externalBlockBreakGuard != null
                && !externalBlockBreakGuard.canBreak(player, block, type)) return;
        if (externalBlockBreakHook != null) {
            externalBlockBreakHook.onExternalBreak(player, block, type);
        }
        boolean useFortuneReroll = hasFortuneQuality
                && (type == Material.RED_MUSHROOM_BLOCK || type == Material.BROWN_MUSHROOM_BLOCK);

        if (!routeToInventory && !useFortuneReroll) {
            block.breakNaturally(tool);
            return;
        }

        Collection<ItemStack> drops = block.getDrops(tool, player);
        if (useFortuneReroll) drops = fortuneQuality.applyFortuneRerolls(block, tool, player, drops);

        Location loc = block.getLocation();
        block.getWorld().playEffect(loc, Effect.STEP_SOUND, type);
        block.setType(Material.AIR);

        if (routeToInventory) {
            // Telekinesis path consults the player's /itemfilter before adding to inventory
            for (ItemStack drop : drops) telekinesis.giveOrDrop(player, block, drop);
        } else {
            for (ItemStack drop : drops) loc.getWorld().dropItemNaturally(loc, drop);
        }
    }

    // Flood-fill search for all matching blocks connected in a 3x3x3 neighborhood
    private Set<Block> findConnected(Block start, Set<Material> validTypes, int maxLogs) {
        Set<Block> blocks = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        blocks.add(start);

        while (!queue.isEmpty()) {
            if (blocks.size() > maxLogs) return blocks;
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (validTypes.contains(neighbor.getType()) && blocks.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return blocks;
    }

    // Verify this is a real tree (not a log structure) by checking for at least one adjacent leaf
    private boolean hasAdjacentLeaf(Set<Block> logs) {
        for (Block log : logs) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Material type = log.getRelative(dx, dy, dz).getType();
                        if (Tag.LEAVES.isTagged(type) || NETHER_LEAVES.contains(type)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // Verify this is a real giant mushroom (not just a placed cap or stem block) by
    // requiring the connected set to contain both stem AND cap blocks.
    private boolean isGiantMushroom(Set<Block> blocks) {
        boolean hasStem = false;
        boolean hasCap = false;
        for (Block block : blocks) {
            Material type = block.getType();
            if (type == Material.MUSHROOM_STEM) {
                hasStem = true;
            } else if (type == Material.RED_MUSHROOM_BLOCK || type == Material.BROWN_MUSHROOM_BLOCK) {
                hasCap = true;
            }
            if (hasStem && hasCap) return true;
        }
        return false;
    }
}
