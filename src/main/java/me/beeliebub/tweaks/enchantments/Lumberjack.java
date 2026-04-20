package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
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
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

// Chops down entire trees at once by breaking all connected logs of the same type.
// Respects unbreaking, checks durability before felling, and requires an adjacent leaf block.
public class Lumberjack implements Listener {

    private static final int MAX_LOGS = 256;

    private final Enchantment enchantment;
    private final Telekinesis telekinesis;

    public Lumberjack(Tweaks plugin, Telekinesis telekinesis) {
        String raw = plugin.getConfig().getString("lumberjack");
        this.enchantment = resolveEnchantment(plugin, raw);
        this.telekinesis = telekinesis;
    }

    public Enchantment getEnchantment() {
        return enchantment;
    }

    public Set<Block> collectConnectedLogs(Block start, Material logType) {
        return findConnectedLogs(start, logType);
    }

    private Enchantment resolveEnchantment(Tweaks plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning("No 'lumberjack' key configured; lumberjack enchant disabled.");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid lumberjack key '" + raw + "'; lumberjack enchant disabled.");
            return null;
        }
        Enchantment resolved = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (resolved == null) {
            plugin.getLogger().warning("Lumberjack enchantment '" + raw + "' not found in registry; is the data pack loaded?");
        }
        return resolved;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (enchantment == null) return;

        Block origin = event.getBlock();
        Material logType = origin.getType();
        if (!Tag.LOGS.isTagged(logType)) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty() || !tool.containsEnchantment(enchantment)) return;

        Set<Block> logs = findConnectedLogs(origin, logType);
        if (logs.size() > MAX_LOGS) return;
        if (logs.size() <= 1) return;
        if (!hasAdjacentLeaf(logs)) return;

        int additionalLogs = logs.size() - 1;
        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int damageToApply = 0;
        for (int i = 0; i < additionalLogs; i++) {
            if (unbreakingLevel <= 0 || random.nextInt(unbreakingLevel + 1) == 0) {
                damageToApply++;
            }
        }

        if (!(tool.getItemMeta() instanceof Damageable meta)) return;
        int maxDurability = tool.getType().getMaxDurability();
        int available = maxDurability - meta.getDamage();

        if (damageToApply > available - 1) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Your tool isn't durable enough to chop this whole tree!")
                    .color(NamedTextColor.RED));
            return;
        }

        boolean routeToInventory = telekinesis != null && telekinesis.hasEnchant(tool);
        for (Block log : logs) {
            if (log.equals(origin)) continue;
            if (routeToInventory) {
                breakIntoInventory(log, tool, player);
            } else {
                log.breakNaturally(tool);
            }
        }

        if (damageToApply > 0) {
            meta.setDamage(meta.getDamage() + damageToApply);
            tool.setItemMeta(meta);
        }
    }

    private void breakIntoInventory(Block block, ItemStack tool, Player player) {
        Material type = block.getType();
        Collection<ItemStack> drops = block.getDrops(tool, player);
        Location loc = block.getLocation();
        block.getWorld().playEffect(loc, Effect.STEP_SOUND, type);
        block.setType(Material.AIR);
        for (ItemStack drop : drops) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
            for (ItemStack remaining : leftover.values()) {
                loc.getWorld().dropItemNaturally(loc, remaining);
            }
        }
    }

    // Flood-fill search for all logs of the same type connected in a 3x3x3 neighborhood
    private Set<Block> findConnectedLogs(Block start, Material logType) {
        Set<Block> logs = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        logs.add(start);

        while (!queue.isEmpty()) {
            if (logs.size() > MAX_LOGS) return logs;
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (neighbor.getType() == logType && logs.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return logs;
    }

    // Verify this is a real tree (not a log structure) by checking for at least one adjacent leaf
    private boolean hasAdjacentLeaf(Set<Block> logs) {
        for (Block log : logs) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (Tag.LEAVES.isTagged(log.getRelative(dx, dy, dz).getType())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}