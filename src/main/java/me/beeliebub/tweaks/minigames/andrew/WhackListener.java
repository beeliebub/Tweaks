package me.beeliebub.tweaks.minigames.andrew;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class WhackListener implements Listener {

    private final WhackGame game;

    public WhackListener(WhackGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMannequinHit(EntityDamageByEntityEvent event) {
        if (event.getEntityType() != EntityType.MANNEQUIN) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (game.getState() != WhackGame.State.RUNNING) return;

        Mannequin mannequin = (Mannequin) event.getEntity();
        if (!game.isGameMannequin(mannequin.getUniqueId())) return;

        // Cancel normal damage - onMannequinHit kills the mannequin directly via setHealth(0)
        // so it plays the death animation. Only the first hit scores (tracked by aliveMannequins).
        event.setCancelled(true);
        game.onMannequinHit(player, mannequin);
    }
}