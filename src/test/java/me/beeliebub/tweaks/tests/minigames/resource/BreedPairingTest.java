package me.beeliebub.tweaks.tests.minigames.resource;

import me.beeliebub.tweaks.minigames.resource.BreedPairing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreedPairingTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String WORLD = "jass:resource";

    @Test
    void nearbyPairCountsOnce() {
        Map<UUID, Integer> result = BreedPairing.pair(List.of(
                candidate("00000000-0000-0000-0000-000000000011", PLAYER, WORLD, 0, 64, 0),
                candidate("00000000-0000-0000-0000-000000000012", PLAYER, WORLD, 2, 64, 0)));

        assertEquals(Map.of(PLAYER, 1), result);
    }

    @Test
    void distantCandidatesCountIndividually() {
        Map<UUID, Integer> result = BreedPairing.pair(List.of(
                candidate("00000000-0000-0000-0000-000000000021", PLAYER, WORLD, 0, 64, 0),
                candidate("00000000-0000-0000-0000-000000000022", PLAYER, WORLD, 40, 64, 0)));

        assertEquals(Map.of(PLAYER, 2), result);
    }

    @Test
    void threeCandidatesProduceTwoTallies() {
        Map<UUID, Integer> result = BreedPairing.pair(List.of(
                candidate("00000000-0000-0000-0000-000000000031", PLAYER, WORLD, 0, 64, 0),
                candidate("00000000-0000-0000-0000-000000000032", PLAYER, WORLD, 2, 64, 0),
                candidate("00000000-0000-0000-0000-000000000033", PLAYER, WORLD, 4, 64, 0)));

        assertEquals(Map.of(PLAYER, 2), result);
    }

    @Test
    void differentPlayersNeverPair() {
        UUID otherPlayer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<UUID, Integer> result = BreedPairing.pair(List.of(
                candidate("00000000-0000-0000-0000-000000000041", PLAYER, WORLD, 0, 64, 0),
                candidate("00000000-0000-0000-0000-000000000042", otherPlayer, WORLD, 2, 64, 0)));

        assertEquals(Map.of(PLAYER, 1, otherPlayer, 1), result);
    }

    @Test
    void differentWorldsNeverPair() {
        Map<UUID, Integer> result = BreedPairing.pair(List.of(
                candidate("00000000-0000-0000-0000-000000000051", PLAYER, WORLD, 0, 64, 0),
                candidate("00000000-0000-0000-0000-000000000052", PLAYER, "jass:resource_nether", 2, 64, 0)));

        assertEquals(Map.of(PLAYER, 2), result);
    }

    @Test
    void resultIsStableRegardlessOfInputOrder() {
        BreedPairing.Candidate first =
                candidate("00000000-0000-0000-0000-000000000061", PLAYER, WORLD, 0, 64, 0);
        BreedPairing.Candidate second =
                candidate("00000000-0000-0000-0000-000000000062", PLAYER, WORLD, 2, 64, 0);
        BreedPairing.Candidate third =
                candidate("00000000-0000-0000-0000-000000000063", PLAYER, WORLD, 40, 64, 0);

        assertEquals(BreedPairing.pair(List.of(first, second, third)),
                BreedPairing.pair(List.of(third, first, second)));
    }

    private static BreedPairing.Candidate candidate(String id, UUID player, String world,
                                                     double x, double y, double z) {
        return new BreedPairing.Candidate(UUID.fromString(id), player, world, x, y, z);
    }
}
