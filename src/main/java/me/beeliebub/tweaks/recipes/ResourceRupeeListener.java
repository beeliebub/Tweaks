package me.beeliebub.tweaks.recipes;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

// Intercepts vanilla emerald <-> emerald-block crafting when all ingredients carry the Resource
// Rupee PDC marker, swapping the vanilla result for the currency variant so the marker is
// preserved across the crafting boundary. Plain emerald grids still produce plain emerald blocks
// (and vice versa) — only fully-marked grids are routed to the Rupee variants.
public class ResourceRupeeListener implements Listener {

    private final ResourceRupee resourceRupee;

    public ResourceRupeeListener(ResourceRupee resourceRupee) {
        this.resourceRupee = resourceRupee;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null) return;

        int rupees = 0;
        int rupeeBlocks = 0;
        int nonEmpty = 0;

        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType().isAir()) continue;
            nonEmpty++;
            if (resourceRupee.isRupee(stack)) {
                rupees++;
            } else if (resourceRupee.isRupeeBlock(stack)) {
                rupeeBlocks++;
            }
        }

        // 9 Rupees -> 1 Rupee Block (vanilla emerald-block recipe shape).
        if (nonEmpty == 9 && rupees == 9) {
            inv.setResult(resourceRupee.createRupeeBlock(1));
            return;
        }

        // 1 Rupee Block -> 9 Rupees (vanilla emerald-block uncraft shape).
        if (nonEmpty == 1 && rupeeBlocks == 1) {
            inv.setResult(resourceRupee.createRupee(9));
        }
    }
}