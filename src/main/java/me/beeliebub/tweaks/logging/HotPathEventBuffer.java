package me.beeliebub.tweaks.logging;

import me.beeliebub.tweaks.protection.region.RegionFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded, synchronized producer/flush handoff for high-frequency event summaries. */
public final class HotPathEventBuffer {

    public static final int MAX_DISTINCT_KEYS = 2_000;
    public static final long WINDOW_SECONDS = 30L;

    /** Small immutable protection event identity; the actor display name is payload metadata. */
    public record HotKey(UUID actor, String regionId, RegionFlag flag) {}

    /** One collapsed event count detached from the active producer window. */
    public record Summary(String path, HotKey key, String actorName, int count) {}

    /** Detached window and its dropped-admission marker. */
    public record Drain(List<Summary> summaries, long droppedAdmissions) {
        public Drain {
            summaries = List.copyOf(summaries);
        }
    }

    private static final class Entry {
        private final String actorName;
        private int count;

        private Entry(String actorName) {
            this.actorName = actorName;
            this.count = 1;
        }
    }

    private final AtomicReference<Map<String, Map<HotKey, Entry>>> active =
            new AtomicReference<>(new HashMap<>());
    private final Object handoffLock = new Object();
    private int activeDistinctKeys;
    private long droppedAdmissions;

    /** Records one event while holding the short producer lock. */
    public void record(String path, HotKey key, String actorName) {
        if (path == null || key == null) return;
        synchronized (handoffLock) {
            Map<String, Map<HotKey, Entry>> current = active.get();
            Map<HotKey, Entry> byKey = current.get(path);
            Entry previous = byKey == null ? null : byKey.get(key);
            if (previous != null) {
                previous.count++;
                return;
            }
            if (activeDistinctKeys >= MAX_DISTINCT_KEYS) {
                droppedAdmissions++;
                return;
            }
            if (byKey == null) {
                byKey = new HashMap<>();
                current.put(path, byKey);
            }
            byKey.put(key, new Entry(actorName));
            activeDistinctKeys++;
        }
    }

    /** Atomically detaches the producer window so new events cannot be lost or double-counted. */
    public Drain drain() {
        synchronized (handoffLock) {
            Map<String, Map<HotKey, Entry>> detached = active.getAndSet(new HashMap<>());
            List<Summary> summaries = new ArrayList<>(activeDistinctKeys);
            for (Map.Entry<String, Map<HotKey, Entry>> pathEntry : detached.entrySet()) {
                for (Map.Entry<HotKey, Entry> entry : pathEntry.getValue().entrySet()) {
                    HotKey key = entry.getKey();
                    Entry value = entry.getValue();
                    summaries.add(new Summary(pathEntry.getKey(), key, value.actorName, value.count));
                }
            }
            activeDistinctKeys = 0;
            long dropped = droppedAdmissions;
            droppedAdmissions = 0L;
            return new Drain(summaries, dropped);
        }
    }
}
