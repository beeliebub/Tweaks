package me.beeliebub.tweaks.minigames.resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Matches recently bred animals into deterministic player/world-local pairings.
 */
public final class BreedPairing {

    private static final double MAX_PAIR_DISTANCE_SQUARED = 8.0 * 8.0;

    private BreedPairing() {
    }

    public static Map<UUID, Integer> pair(List<Candidate> candidates) {
        List<Candidate> remaining = new ArrayList<>(candidates);
        remaining.sort(Comparator.comparing(Candidate::id));

        Map<UUID, Integer> pairings = new HashMap<>();
        while (!remaining.isEmpty()) {
            Candidate current = remaining.removeFirst();
            int nearestIndex = nearestCompatibleIndex(current, remaining);
            if (nearestIndex >= 0) {
                remaining.remove(nearestIndex);
            }
            pairings.merge(current.player(), 1, Integer::sum);
        }
        return Map.copyOf(pairings);
    }

    private static int nearestCompatibleIndex(Candidate current, List<Candidate> remaining) {
        int nearestIndex = -1;
        double nearestDistance = Double.POSITIVE_INFINITY;
        UUID nearestId = null;
        for (int index = 0; index < remaining.size(); index++) {
            Candidate candidate = remaining.get(index);
            if (!current.player().equals(candidate.player())
                    || !current.worldKey().equals(candidate.worldKey())) {
                continue;
            }
            double distance = squaredDistance(current, candidate);
            if (distance > MAX_PAIR_DISTANCE_SQUARED) continue;
            if (distance < nearestDistance
                    || (distance == nearestDistance
                    && (nearestId == null || candidate.id().compareTo(nearestId) < 0))) {
                nearestIndex = index;
                nearestDistance = distance;
                nearestId = candidate.id();
            }
        }
        return nearestIndex;
    }

    private static double squaredDistance(Candidate first, Candidate second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public record Candidate(UUID id, UUID player, String worldKey, double x, double y, double z) {
    }
}
