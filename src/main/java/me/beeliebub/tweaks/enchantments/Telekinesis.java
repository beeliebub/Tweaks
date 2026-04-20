package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

// Sends block drops straight to the player's inventory instead of dropping on the ground.
// Also chain-breaks stackable plants (sugar cane, cactus, bamboo, vines, chorus, etc.)
public class Telekinesis implements Listener {

    private static final BlockFace[] CHORUS_FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final Enchantment enchantment;
    // Tracks which block break is pending telekinesis pickup (cleared on BlockDropItemEvent)
    private final Map<UUID, Location> pendingTelekinesis = new HashMap<>();

    public Telekinesis(Tweaks plugin) {
        String raw = plugin.getConfig().getString("telekinesis");
        this.enchantment = resolveEnchantment(plugin, raw);
    }

    private Enchantment resolveEnchantment(Tweaks plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning("No 'telekinesis' key configured; telekinesis enchant disabled.");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid telekinesis key '" + raw + "'; telekinesis enchant disabled.");
            return null;
        }
        Enchantment resolved = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (resolved == null) {
            plugin.getLogger().warning("Telekinesis enchantment '" + raw + "' not found in registry; is the data pack loaded?");
        }
        return resolved;
    }

    public boolean hasEnchant(ItemStack tool) {
        return enchantment != null && !tool.isEmpty() && tool.containsEnchantment(enchantment);
    }

    // Mark the broken block for telekinesis pickup and chain-break any connected plants
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (enchantment == null) return;
        if (!event.isDropItems()) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasEnchant(tool)) return;

        Block block = event.getBlock();
        pendingTelekinesis.put(player.getUniqueId(), block.getLocation());

        chainBreakPlants(block, player, tool);
    }

    // Intercept dropped items and route them to the player's inventory
    @EventHandler(ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (enchantment == null) return;

        Player player = event.getPlayer();
        Location loc = pendingTelekinesis.get(player.getUniqueId());
        if (loc == null || !loc.equals(event.getBlock().getLocation())) return;
        pendingTelekinesis.remove(player.getUniqueId());

        Block block = event.getBlock();
        for (Item item : event.getItems()) {
            giveOrDrop(player, block, item.getItemStack());
        }
        event.getItems().clear();
    }

    // Break all connected segments of stackable plants above/below the origin block
    private void chainBreakPlants(Block origin, Player player, ItemStack tool) {
        List<Block> chain = new ArrayList<>();
        switch (origin.getType()) {
            case SUGAR_CANE, CACTUS, BAMBOO ->
                    collectLinear(origin, BlockFace.UP, EnumSet.of(origin.getType()), chain);
            case KELP, KELP_PLANT ->
                    collectLinear(origin, BlockFace.UP, EnumSet.of(Material.KELP, Material.KELP_PLANT), chain);
            case TWISTING_VINES, TWISTING_VINES_PLANT ->
                    collectLinear(origin, BlockFace.UP, EnumSet.of(Material.TWISTING_VINES, Material.TWISTING_VINES_PLANT), chain);
            case WEEPING_VINES, WEEPING_VINES_PLANT ->
                    collectLinear(origin, BlockFace.DOWN, EnumSet.of(Material.WEEPING_VINES, Material.WEEPING_VINES_PLANT), chain);
            case VINE ->
                    collectLinear(origin, BlockFace.DOWN, EnumSet.of(Material.VINE), chain);
            case POINTED_DRIPSTONE -> {
                collectLinear(origin, BlockFace.UP, EnumSet.of(Material.POINTED_DRIPSTONE), chain);
                collectLinear(origin, BlockFace.DOWN, EnumSet.of(Material.POINTED_DRIPSTONE), chain);
            }
            case CHORUS_PLANT -> collectChorus(origin, chain);
            default -> {
                return;
            }
        }

        for (Block b : chain) {
            Material replacement = (b.getType() == Material.KELP || b.getType() == Material.KELP_PLANT)
                    ? Material.WATER
                    : Material.AIR;
            Collection<ItemStack> chainDrops = b.getDrops(tool, player);
            b.setType(replacement, false);
            for (ItemStack drop : chainDrops) {
                giveOrDrop(player, b, drop);
            }
        }
    }

    private void collectLinear(Block origin, BlockFace face, Set<Material> valid, List<Block> out) {
        Block current = origin.getRelative(face);
        while (valid.contains(current.getType())) {
            out.add(current);
            current = current.getRelative(face);
        }
    }

    private void collectChorus(Block origin, List<Block> out) {
        Deque<Block> stack = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();
        stack.push(origin);
        visited.add(origin);
        while (!stack.isEmpty()) {
            Block b = stack.pop();
            for (BlockFace face : CHORUS_FACES) {
                Block neighbor = b.getRelative(face);
                if (!visited.add(neighbor)) continue;
                if (neighbor.getType() == Material.CHORUS_PLANT) {
                    out.add(neighbor);
                    stack.push(neighbor);
                }
            }
        }
    }

    private void giveOrDrop(Player player, Block block, ItemStack drop) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
        for (ItemStack remaining : leftover.values()) {
            block.getWorld().dropItemNaturally(block.getLocation(), remaining);
        }
    }
}