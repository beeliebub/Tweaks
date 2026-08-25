package me.beeliebub.tweaks.tools.augments;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Clears a player's pending augment confirmation when their session ends. */
public final class AugmentConfirmationListener implements Listener {

    private final AugmentPendingConfirmations pending;

    public AugmentConfirmationListener(AugmentPendingConfirmations pending) {
        this.pending = pending;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.cancel(event.getPlayer().getUniqueId());
    }
}
