package me.beeliebub.tweaks.skyblock.tracking;

import me.beeliebub.tweaks.skyblock.island.Island;
import org.bukkit.Material;

import java.util.Set;

/** Registry-wide upper bounds supplied to listeners without coupling them to challenges. */
public interface TrackingScope {
    Set<TrackKey> activeTrackKeys();

    Set<Material> taintMaterials();

    default Set<TrackKey> activeTrackKeys(Island island) {
        return activeTrackKeys();
    }

    default Set<Material> taintMaterials(Island island) {
        return taintMaterials();
    }

    default int generation() {
        return 0;
    }
}
