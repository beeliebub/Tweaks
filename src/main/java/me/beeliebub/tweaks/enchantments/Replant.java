package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

// Automatically replants crops after harvesting and saplings after tree felling.
// Crop replanting flows through BlockDropItemEvent so other plugins (e.g. Husbandry)
// can mutate the dropped items first; the seed used for replanting carries those mutations.
public class Replant implements Listener {

    /** Hook invoked after a crop is replanted; receives the seed used (with PDC) and the new block. */
    @FunctionalInterface
    public interface ReplantHook {
        void onReplant(ItemStack seed, Block block);
    }

    // Maps crop block type to the seed item needed to replant it
    private static final Map<Material, Material> CROP_SEEDS = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART
    );

    // Maps log type to the sapling that should be planted after felling
    private static final Map<Material, Material> LOG_SAPLINGS = Map.ofEntries(
            Map.entry(Material.OAK_LOG, Material.OAK_SAPLING),
            Map.entry(Material.SPRUCE_LOG, Material.SPRUCE_SAPLING),
            Map.entry(Material.BIRCH_LOG, Material.BIRCH_SAPLING),
            Map.entry(Material.JUNGLE_LOG, Material.JUNGLE_SAPLING),
            Map.entry(Material.ACACIA_LOG, Material.ACACIA_SAPLING),
            Map.entry(Material.DARK_OAK_LOG, Material.DARK_OAK_SAPLING),
            Map.entry(Material.MANGROVE_LOG, Material.MANGROVE_PROPAGULE),
            Map.entry(Material.CHERRY_LOG, Material.CHERRY_SAPLING),
            Map.entry(Material.PALE_OAK_LOG, Material.PALE_OAK_SAPLING)
    );

    // Blocks that saplings can be planted on
    private static final Set<Material> SAPLING_SOIL = EnumSet.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.PODZOL,
            Material.MYCELIUM,
            Material.ROOTED_DIRT,
            Material.MOSS_BLOCK,
            Material.MUD,
            Material.MUDDY_MANGROVE_ROOTS,
            Material.FARMLAND
    );

    private final Tweaks plugin;
    private final Enchantment enchantment;
    private final Telekinesis telekinesis;
    private final Lumberjack lumberjack;

    // Tracks crops that should be replanted on the next BlockDropItemEvent for that block.
    private final Map<Location, PendingReplant> pendingReplants = new HashMap<>();

    private final List<ReplantHook> replantHooks = new CopyOnWriteArrayList<>();

    public Enchantment getEnchantment() {
        return enchantment;
    }

    public Replant(Tweaks plugin, Telekinesis telekinesis, Lumberjack lumberjack) {
        this.plugin = plugin;
        String raw = plugin.getConfig().getString("replant");
        this.enchantment = resolveEnchantment(plugin, raw);
        this.telekinesis = telekinesis;
        this.lumberjack = lumberjack;
    }

    /** Register a callback invoked after each successful crop replant. */
    public void addReplantHook(ReplantHook hook) {
        if (hook != null) replantHooks.add(hook);
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

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty() || !tool.containsEnchantment(enchantment)) return;

        Block block = event.getBlock();
        Material blockType = block.getType();

        if (Tag.LOGS.isTagged(blockType)) {
            handleTreeReplant(tool, block, blockType);
            return;
        }

        Material seedType = CROP_SEEDS.get(blockType);
        if (seedType == null) return;

        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable ageable)) return;

        // Immature crops: cancel the break so players can't waste growth time.
        if (ageable.getAge() < ageable.getMaximumAge()) {
            event.setCancelled(true);
            return;
        }

        // Mark this break for replanting; we'll consume the seed in BlockDropItemEvent
        // after other plugins (Husbandry) have mutated the drops.
        pendingReplants.put(block.getLocation(), new PendingReplant(blockType, seedType));
    }

    // Consume one (now-traited) seed from the dropped items and schedule the replant.
    // Runs at HIGH so trait-mutating listeners (e.g. Husbandry at LOW/NORMAL) finish first,
    // and Telekinesis (HIGHEST) picks up the remaining drops afterwards.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (enchantment == null) return;

        Block block = event.getBlock();
        PendingReplant pending = pendingReplants.remove(block.getLocation());
        if (pending == null) return;

        // Find a seed in the (possibly mutated) drops to use for replanting.
        ItemStack seedToPlant = null;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack.getType() == pending.seedType && stack.getAmount() > 0) {
                ItemStack reduced = stack.clone();
                reduced.setAmount(reduced.getAmount() - 1);
                seedToPlant = stack.clone();
                seedToPlant.setAmount(1);

                if (reduced.getAmount() <= 0) {
                    item.remove();
                } else {
                    item.setItemStack(reduced);
                }
                break;
            }
        }
        if (seedToPlant == null) return;

        final ItemStack seedFinal = seedToPlant;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!block.getType().isAir()) return;
            block.setType(pending.cropType);
            BlockData newData = block.getBlockData();
            if (newData instanceof Ageable fresh) {
                fresh.setAge(0);
                block.setBlockData(fresh);
            }
            for (ReplantHook hook : replantHooks) {
                try {
                    hook.onReplant(seedFinal, block);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Replant hook failed", t);
                }
            }
        });
    }

    // Plant saplings at the base of a felled tree (requires Lumberjack enchant to identify tree logs)
    private void handleTreeReplant(ItemStack tool, Block origin, Material logType) {
        if (lumberjack == null) return;
        Enchantment lumberjackEnchant = lumberjack.getEnchantment();
        if (lumberjackEnchant == null || !tool.containsEnchantment(lumberjackEnchant)) return;

        Material saplingType = LOG_SAPLINGS.get(logType);
        if (saplingType == null) return;

        Set<Block> logs = lumberjack.collectConnectedLogs(origin, logType);
        if (logs.size() <= 1) return;

        List<Location> basePositions = new ArrayList<>();
        for (Block log : logs) {
            if (SAPLING_SOIL.contains(log.getRelative(BlockFace.DOWN).getType())) {
                basePositions.add(log.getLocation());
            }
        }
        if (basePositions.isEmpty()) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Location loc : basePositions) {
                Block b = loc.getBlock();
                if (!b.getType().isAir()) continue;
                if (!SAPLING_SOIL.contains(b.getRelative(BlockFace.DOWN).getType())) continue;
                b.setType(saplingType);
            }
        });
    }

    private record PendingReplant(Material cropType, Material seedType) {}
}