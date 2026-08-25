package me.beeliebub.tweaks.tools.xp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** Removes vanilla XP from mobs while deliberately preserving PlayerDeathEvent XP. */
public final class MobExpListener implements Listener {

    private final XpSettings settings;

    public MobExpListener(XpSettings settings) {
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!settings.mobDropsRemove()) return;
        if (event.getEntity() instanceof Player) return;
        event.setDroppedExp(0);
    }
}
