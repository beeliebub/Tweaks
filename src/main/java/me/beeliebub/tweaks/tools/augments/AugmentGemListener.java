package me.beeliebub.tweaks.tools.augments;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Gem-first entry point into the shared augment dialog implementation. */
public final class AugmentGemListener implements Listener {

    private final AugmentGemItem gems;
    private final AugmentDialog dialog;

    public AugmentGemListener(AugmentGemItem gems, AugmentDialog dialog) {
        this.gems = gems;
        this.dialog = dialog;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        // An air interaction reports itself as cancelled, so ignoreCancelled would skip it.
        if (event.useItemInHand() == Event.Result.DENY) return;
        if (!gems.isGem(event.getItem())) return;
        if (!gems.enabled()) return;
        event.setCancelled(true);
        if (event.getPlayer() instanceof Player player) dialog.openGemFirst(player, event.getItem());
    }
}
