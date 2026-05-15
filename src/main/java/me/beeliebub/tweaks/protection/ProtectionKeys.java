package me.beeliebub.tweaks.protection;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.NamespacedKey;

// Holds the protection subsystem's NamespacedKey constants. NamespacedKey
// requires a live Plugin instance at construction time, so the keys cannot
// be true compile-time constants; init(plugin) must be called once during
// onEnable before any accessor fires.
public final class ProtectionKeys {

    private static NamespacedKey regionPointers;

    private ProtectionKeys() {}

    public static void init(Tweaks plugin) {
        regionPointers = new NamespacedKey(plugin, "region_pointers");
    }

    // The key under which a chunk's PDC stores the list of RegionID strings
    // that mathematically cover that chunk. Set is the only mutation path
    // into protected territory; reads back the same list verbatim for
    // O(1) event-resolution lookups.
    public static NamespacedKey regionPointers() {
        if (regionPointers == null) {
            throw new IllegalStateException("ProtectionKeys.init(plugin) not called");
        }
        return regionPointers;
    }
}
