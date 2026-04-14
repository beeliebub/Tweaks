package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class Replant implements Listener {

    private static final Map<Material, Material> CROP_SEEDS = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART
    );

    private final Tweaks plugin;
    private final Enchantment enchantment;
    private final Telekinesis telekinesis;

    public Replant(Tweaks plugin, Telekinesis telekinesis) {
        this.plugin = plugin;
        String raw = plugin.getConfig().getString("replant");
        this.enchantment = resolveEnchantment(plugin, raw);
        this.telekinesis = telekinesis;
    }

    private Enchantment resolveEnchantment(Tweaks plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning("No 'replant' key configured; replant enchant disabled.");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid replant key '" + raw + "'; replant enchant disabled.");
            return null;
        }
        Enchantment resolved = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (resolved == null) {
            plugin.getLogger().warning("Replant enchantment '" + raw + "' not found in registry; is the data pack loaded?");
        }
        return resolved;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (enchantment == null) return;
        if (!event.isDropItems()) return;

        Block block = event.getBlock();
        Material cropType = block.getType();
        Material seedType = CROP_SEEDS.get(cropType);
        if (seedType == null) return;

        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable ageable)) return;
        if (ageable.getAge() < ageable.getMaximumAge()) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty() || !tool.containsEnchantment(enchantment)) return;

        Collection<ItemStack> drops = block.getDrops(tool, player);
        List<ItemStack> remaining = new ArrayList<>(drops.size());
        boolean consumed = false;
        for (ItemStack drop : drops) {
            if (!consumed && drop.getType() == seedType && drop.getAmount() > 0) {
                ItemStack clone = drop.clone();
                clone.setAmount(clone.getAmount() - 1);
                consumed = true;
                if (clone.getAmount() > 0) remaining.add(clone);
            } else {
                remaining.add(drop);
            }
        }
        if (!consumed) return;

        event.setDropItems(false);

        boolean useTelekinesis = telekinesis != null && telekinesis.hasEnchant(tool);
        Location loc = block.getLocation();
        World world = block.getWorld();
        for (ItemStack drop : remaining) {
            if (useTelekinesis) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
                for (ItemStack overflow : leftover.values()) {
                    world.dropItemNaturally(loc, overflow);
                }
            } else {
                world.dropItemNaturally(loc, drop);
            }
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            block.setType(cropType);
            BlockData newData = block.getBlockData();
            if (newData instanceof Ageable fresh) {
                fresh.setAge(0);
                block.setBlockData(fresh);
            }
        });
    }
}