package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;

public class Tunneller implements Listener {

    private static final double RAY_DISTANCE = 6.0;

    private final Enchantment enchantment;
    private final Telekinesis telekinesis;
    private final Smelter smelter;

    public Tunneller(Tweaks plugin, Telekinesis telekinesis, Smelter smelter) {
        String raw = plugin.getConfig().getString("tunneller");
        this.enchantment = resolveEnchantment(plugin, raw);
        this.telekinesis = telekinesis;
        this.smelter = smelter;
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
        if (enchantment == null) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty() || !tool.containsEnchantment(enchantment)) return;

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

        for (int u = -1; u <= 1; u++) {
            for (int v = -1; v <= 1; v++) {
                if (u == 0 && v == 0) continue;
                Block target = origin.getRelative(
                        a1[0] * u + a2[0] * v,
                        a1[1] * u + a2[1] * v,
                        a1[2] * u + a2[2] * v
                );
                breakBlock(target, tool, player, useSmelter, useTelekinesis);
            }
        }
    }

    private void breakBlock(Block target, ItemStack tool, Player player, boolean useSmelter, boolean useTelekinesis) {
        Material type = target.getType();
        if (type.isAir() || target.isLiquid()) return;
        if (type.getHardness() < 0) return;

        Collection<ItemStack> drops = target.getDrops(tool, player);
        if (useSmelter) drops = Smelter.smeltDrops(drops);

        Location loc = target.getLocation();
        target.getWorld().playEffect(loc, Effect.STEP_SOUND, type);
        target.setType(Material.AIR);

        if (useTelekinesis) {
            for (ItemStack drop : drops) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
                for (ItemStack remaining : leftover.values()) {
                    loc.getWorld().dropItemNaturally(loc, remaining);
                }
            }
        } else {
            for (ItemStack drop : drops) {
                loc.getWorld().dropItemNaturally(loc, drop);
            }
        }
    }

    private int[][] axesForFace(BlockFace face) {
        return switch (face) {
            case UP, DOWN -> new int[][]{{1, 0, 0}, {0, 0, 1}};
            case NORTH, SOUTH -> new int[][]{{1, 0, 0}, {0, 1, 0}};
            case EAST, WEST -> new int[][]{{0, 0, 1}, {0, 1, 0}};
            default -> null;
        };
    }
}