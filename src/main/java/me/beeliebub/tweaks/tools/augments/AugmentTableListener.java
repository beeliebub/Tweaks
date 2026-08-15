package me.beeliebub.tweaks.tools.augments;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Diverts enchanting-table results into one gem per rolled enchantment. */
public final class AugmentTableListener implements Listener {

    private final AugmentService augments;

    public AugmentTableListener(AugmentService augments) {
        this.augments = augments;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!augments.enabled()) return;
        Map<Enchantment, Integer> rolled = event.getEnchantsToAdd();
        if (rolled == null || rolled.isEmpty()) return;
        List<ItemStack> gems = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : List.copyOf(rolled.entrySet())) {
            gems.add(augments.gemItem().create(entry.getKey(), entry.getValue()));
        }
        Player player = event.getEnchanter();
        if (!augments.canFit(player, gems)) return;
        rolled.clear();
        augments.addGems(player, gems);
    }
}
