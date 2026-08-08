package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.PendingStampsStore;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendingStampsReconcileTest {

    @Test
    void reconcilePrunesMissingRegionIdsAndRecordsThemAsOrphans(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("pending_stamps.yml"), """
                stamps:
                  'world:42':
                    - missing-region
                """);
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        ProtectionManager protection = new ProtectionManager(plugin);
        PendingStampsStore store = new PendingStampsStore(plugin, tmp.toFile(),
                protection.pendingStamps(), protection.orphanedRegions());

        store.load();
        assertEquals(Set.of("missing-region"),
                protection.pendingStamps().get(ProtectionManager.stampKey("world", 42L)));

        int pruned = protection.reconcilePendingStamps();

        assertEquals(1, pruned);
        assertTrue(protection.pendingStamps().isEmpty());
        assertTrue(protection.orphanedRegions().contains("world:missing-region"));
    }
}
