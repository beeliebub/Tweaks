package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.PendingStampsStore;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Stress tests for the documented thread-safety guarantees:
//   * ConcurrentHashMap iterators are weakly consistent — snapshotting the
//     pendingStamps map MUST NOT throw ConcurrentModificationException even
//     while other threads insert into it.
//   * regions cache reads are lock-free; readers must never block on writers.
//   * Region mutators (addMember/removeMember/setFlag) atomically replace the
//     cache entry so a reader either sees the old Region or the new Region —
//     never a partial state.
//
// These are deliberately racy by design: the assertion is that nothing
// explodes, not that any specific interleaving happens. Failures here mean
// the production code's threading assumptions are wrong.
class ConcurrencyTest {

    @Test
    void snapshotDoesNotCmeWhileMutatorsInsert(@TempDir Path tmp) throws Exception {
        ConcurrentHashMap<Long, java.util.Set<String>> stamps = new ConcurrentHashMap<>();
        Tweaks plugin = mock(Tweaks.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("concurrency"));
        PendingStampsStore store = new PendingStampsStore(plugin, tmp.toFile(), stamps);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        final int writesPerThread = 2000;
        final int snapshotIterations = 30;

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int t = 0; t < 3; t++) {
                final int seed = t;
                pool.submit(() -> {
                    try {
                        long base = seed * 10_000L;
                        for (int i = 0; i < writesPerThread; i++) {
                            java.util.Set<String> set = ConcurrentHashMap.newKeySet();
                            set.add("r" + (base + i));
                            stamps.put(base + i, set);
                        }
                    } catch (Throwable e) {
                        failure.set(e);
                    }
                });
            }
            pool.submit(() -> {
                try {
                    for (int i = 0; i < snapshotIterations; i++) {
                        store.writeNow();
                    }
                } catch (Throwable e) {
                    failure.set(e);
                }
            });

            pool.shutdown();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS), "executor must finish in time");
        } finally {
            pool.shutdownNow();
        }

        if (failure.get() != null) fail("background failure", failure.get());

        // Take one final snapshot so the on-disk file reflects all inserts,
        // then verify the file round-trips back into a populated cache.
        store.writeNow();
        ConcurrentHashMap<Long, java.util.Set<String>> reloaded = new ConcurrentHashMap<>();
        new PendingStampsStore(plugin, tmp.toFile(), reloaded).load();
        assertEquals(3 * writesPerThread, reloaded.size(),
                "every concurrent insert must be in the final snapshot");
    }

    @Test
    void concurrentRegionsAtReadersNeverBlockOrCorrupt() throws Exception {
        ProtectionManager mgr = new ProtectionManager(mock(Tweaks.class));
        UUID owner = UUID.randomUUID();
        for (int i = 0; i < 1000; i++) {
            mgr.regions().put("r" + i,
                    new Region("r" + i, owner, List.of(), EnumSet.noneOf(RegionFlag.class)));
        }

        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch stop = new CountDownLatch(1);
        try {
            // 4 reader threads
            for (int r = 0; r < 4; r++) {
                pool.submit(() -> {
                    try {
                        while (stop.getCount() > 0) {
                            mgr.regions().get("r" + (System.nanoTime() % 1000));
                        }
                    } catch (Throwable e) {
                        failure.set(e);
                    }
                });
            }
            // 4 writer threads doing addMember/setFlag/removeMember loops
            for (int w = 0; w < 4; w++) {
                final int seed = w;
                pool.submit(() -> {
                    try {
                        UUID member = UUID.randomUUID();
                        int n = 0;
                        while (stop.getCount() > 0 && n++ < 500) {
                            String id = "r" + ((seed * 250 + n) % 1000);
                            mgr.addMember(id, member);
                            mgr.setFlag(id, RegionFlag.PVP, n % 2 == 0);
                            mgr.removeMember(id, member);
                        }
                    } catch (Throwable e) {
                        failure.set(e);
                    } finally {
                        if (seed == 3) stop.countDown();
                    }
                });
            }

            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        if (failure.get() != null) fail("background failure", failure.get());
        // Cache integrity: every key still resolves to a Region with the same id.
        for (int i = 0; i < 1000; i++) {
            Region r = mgr.regions().get("r" + i);
            assertNotNull(r, "region r" + i + " disappeared");
            assertEquals("r" + i, r.id());
        }
    }

    @Test
    void pendingStampsInnerSetSurvivesConcurrentAdds() throws Exception {
        ConcurrentHashMap<Long, java.util.Set<String>> stamps = new ConcurrentHashMap<>();
        java.util.Set<String> inner = ConcurrentHashMap.newKeySet();
        stamps.put(0L, inner);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int t = 0; t < 4; t++) {
                final int seed = t;
                pool.submit(() -> {
                    try {
                        for (int i = 0; i < 5000; i++) {
                            inner.add("t" + seed + ":" + i);
                        }
                    } catch (Throwable e) {
                        failure.set(e);
                    }
                });
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        if (failure.get() != null) fail("background failure", failure.get());
        assertEquals(4 * 5000, inner.size(), "every id from every thread must be present");
    }
}
