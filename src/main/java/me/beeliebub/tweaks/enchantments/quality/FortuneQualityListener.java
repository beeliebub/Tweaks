package me.beeliebub.tweaks.enchantments.quality;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FortuneQualityListener implements Listener {

    private final QualityRegistry registry;
    private final Enchantment vanillaFortune;

    public FortuneQualityListener(QualityRegistry registry) {
        this.registry = registry;
        // Resolve vanilla fortune so we can apply it to a simulation tool.
        // Vanilla block loot tables only check minecraft:fortune, not custom quality variants.
        this.vanillaFortune = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft("fortune"));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty()) return;

        QualityRegistry.QualityInfo quality = registry.getToolQuality(tool, "fortune");
        if (quality == null || vanillaFortune == null) return;

        int rerolls = quality.tier().getRerolls();
        Block block = event.getBlock();

        // Capture current drops for scoring
        List<ItemStack> currentDrops = new ArrayList<>();
        for (Item item : event.getItems()) {
            currentDrops.add(item.getItemStack());
        }

        Collection<ItemStack> bestDrops = currentDrops;
        int highestScore = scoreDrops(bestDrops);

        // Build a simulation tool with vanilla minecraft:fortune applied.
        // Vanilla block loot tables only recognize minecraft:fortune in their
        // apply_bonus functions, not custom quality enchantments like jass:legendary_fortune.
        ItemStack simTool = tool.clone();
        simTool.addUnsafeEnchantment(vanillaFortune, quality.level());

        // The block is already broken (now air). Temporarily restore it so
        // getDrops() can simulate fortune rolls against the original block type.
        BlockData originalData = event.getBlockState().getBlockData();
        block.setBlockData(originalData, false);
        try {
            for (int r = 0; r < rerolls; r++) {
                Collection<ItemStack> simulatedDrops = block.getDrops(simTool, player);
                int score = scoreDrops(simulatedDrops);
                if (score > highestScore) {
                    highestScore = score;
                    bestDrops = simulatedDrops;
                }
            }
        } finally {
            block.setType(Material.AIR, false);
        }

        // If a re-roll beat the original, update the existing item entities
        if (bestDrops != currentDrops) {
            List<ItemStack> bestList = new ArrayList<>(bestDrops);

            // Match existing item entities to new drops by material and update in place
            for (Item item : event.getItems()) {
                for (int i = 0; i < bestList.size(); i++) {
                    if (bestList.get(i).getType() == item.getItemStack().getType()) {
                        item.setItemStack(bestList.remove(i));
                        break;
                    }
                }
            }

            // If the best roll has item types not in the original (rare), spawn new entities
            if (!bestList.isEmpty()) {
                Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
                for (ItemStack remaining : bestList) {
                    Item entity = block.getWorld().dropItemNaturally(dropLoc, remaining);
                    event.getItems().add(entity);
                }
            }
        }
    }

    /**
     * Determines how "good" a drop list is.
     * The simplest way is just to count the total amount of items.
     * (e.g., 3 Redstone dust scores a 3. 5 Redstone dust scores a 5).
     */
    private int scoreDrops(Collection<ItemStack> drops) {
        int score = 0;
        for (ItemStack item : drops) {
            if (item != null && !item.getType().isAir()) {
                score += item.getAmount();
            }
        }
        return score;
    }
}