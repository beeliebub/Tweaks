package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

// Tags items produced by crafting recipes inside the Resource Hunt worlds with the
// resource_hunt_counted PDC marker so they can't be carried out and re-credited later.
// We defer to next tick because CraftItemEvent fires before the result lands in the
// inventory (or on the cursor) for shift-click crafts.
public class ResourceCraftListener implements Listener {

    private final Tweaks plugin;
    private final ResourceHunt resourceHunt;

    public ResourceCraftListener(Tweaks plugin, ResourceHunt resourceHunt) {
        this.plugin = plugin;
        this.resourceHunt = resourceHunt;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!ResourceHunt.isResourceWorld(player.getWorld().getKey().asString())) return;
        if (event.getRecipe() == null) return;
        Material resultMaterial = event.getRecipe().getResult().getType();
        if (resultMaterial.isAir()) return;

        Bukkit.getScheduler().runTask(plugin, () -> tagCraftedItems(player, resultMaterial));
    }

    // Visible for tests and other resource-package callers that already hold a player ref.
    public void tagCraftedItems(Player player, Material material) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            if (resourceHunt.markItemAsCounted(stack)) {
                player.getInventory().setItem(i, stack);
            }
        }
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (cursor != null && cursor.getType() == material) {
            if (resourceHunt.markItemAsCounted(cursor)) {
                player.getOpenInventory().setCursor(cursor);
            }
        }
    }
}
