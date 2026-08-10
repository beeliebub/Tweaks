package me.beeliebub.tweaks.skyblock.island;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Main-thread state for the island currently being visited by each player. */
public final class IslandVisits {

    private final Map<UUID, String> islandByVisitor = new HashMap<>();
    private final Map<String, Set<UUID>> visitorsByIsland = new HashMap<>();

    /** Starts or changes a visit. Returns false when the requested visit was already active. */
    public boolean visit(UUID visitor, String islandId) {
        requireVisitor(visitor);
        requireIslandId(islandId);

        String previous = islandByVisitor.put(visitor, islandId);
        if (islandId.equals(previous)) {
            return false;
        }
        if (previous != null) {
            removeFromIsland(previous, visitor);
        }
        visitorsByIsland.computeIfAbsent(islandId, ignored -> new HashSet<>()).add(visitor);
        return true;
    }

    public boolean startVisit(UUID visitor, String islandId) {
        return visit(visitor, islandId);
    }

    public boolean setVisit(UUID visitor, String islandId) {
        return visit(visitor, islandId);
    }

    public java.util.Optional<String> visitedIsland(UUID visitor) {
        requireVisitor(visitor);
        return java.util.Optional.ofNullable(islandByVisitor.get(visitor));
    }

    public java.util.Optional<String> islandVisitedBy(UUID visitor) {
        return visitedIsland(visitor);
    }

    public boolean isVisiting(UUID visitor, String islandId) {
        requireVisitor(visitor);
        requireIslandId(islandId);
        return islandId.equals(islandByVisitor.get(visitor));
    }

    /** Ends a player's visit and returns the removed island id, if any. */
    public java.util.Optional<String> clear(UUID visitor) {
        requireVisitor(visitor);
        String islandId = islandByVisitor.remove(visitor);
        if (islandId == null) {
            return java.util.Optional.empty();
        }
        removeFromIsland(islandId, visitor);
        return java.util.Optional.of(islandId);
    }

    public java.util.Optional<String> endVisit(UUID visitor) {
        return clear(visitor);
    }

    public void onQuit(UUID visitor) {
        clear(visitor);
    }

    public void onWorldChange(UUID visitor) {
        clear(visitor);
    }

    /** Clears every visit to an island and returns the visitors that were removed. */
    public Set<UUID> clearIsland(String islandId) {
        requireIslandId(islandId);
        Set<UUID> visitors = visitorsByIsland.remove(islandId);
        if (visitors == null || visitors.isEmpty()) {
            return Set.of();
        }
        Set<UUID> removed = Set.copyOf(visitors);
        for (UUID visitor : removed) {
            islandByVisitor.remove(visitor, islandId);
        }
        return removed;
    }

    public Set<UUID> removeIsland(String islandId) {
        return clearIsland(islandId);
    }

    public Set<UUID> visitorsOf(String islandId) {
        requireIslandId(islandId);
        Set<UUID> visitors = visitorsByIsland.get(islandId);
        return visitors == null || visitors.isEmpty() ? Set.of() : Set.copyOf(visitors);
    }

    public Set<UUID> visitorsFor(String islandId) {
        return visitorsOf(islandId);
    }

    public int size() {
        return islandByVisitor.size();
    }

    private void removeFromIsland(String islandId, UUID visitor) {
        Set<UUID> visitors = visitorsByIsland.get(islandId);
        if (visitors == null) {
            return;
        }
        visitors.remove(visitor);
        if (visitors.isEmpty()) {
            visitorsByIsland.remove(islandId);
        }
    }

    private static void requireVisitor(UUID visitor) {
        if (visitor == null) {
            throw new NullPointerException("visitor");
        }
    }

    private static void requireIslandId(String islandId) {
        if (islandId == null || islandId.isBlank()) {
            throw new IllegalArgumentException("islandId cannot be blank");
        }
    }
}
