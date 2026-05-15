package me.beeliebub.tweaks.listeners;

import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

// Default-deny on creeper block destruction and enderman block manipulation,
// with per-region opt-in via the MOB_GRIEFING protection flag. We intentionally
// ignore the world's mobGriefing gamerule because the contract here is "an
// admin who has explicitly set MOB_GRIEFING=true on a region wants griefing
// there regardless of the world's vanilla state".
//
// Priority HIGH puts us after ProtectionManager's LOWEST listeners (which
// already filtered EXPLOSION-protected blocks out of the creeper blockList)
// so we operate on the already-narrowed candidate set.
public class MobGriefListener implements Listener {

    private final ProtectionManager protection;

    public MobGriefListener(ProtectionManager protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreeperExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Creeper)) return;
        // Keep a block only when the region at its location explicitly permits
        // MOB_GRIEFING (flag=true). Wilderness and silent regions preserve the
        // pre-flag default of "no creeper griefing".
        event.blockList().removeIf(b ->
                !protection.isExplicitlyAllowed(b.getLocation(), null, RegionFlag.MOB_GRIEFING));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEndermanGrief(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Enderman)) return;
        if (protection.isExplicitlyAllowed(
                event.getBlock().getLocation(), null, RegionFlag.MOB_GRIEFING)) {
            return;
        }
        event.setCancelled(true);
    }
}
