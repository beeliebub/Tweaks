package me.beeliebub.tweaks.tests.skyblock;

import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.SkyblockSetupChecker;
import me.beeliebub.tweaks.skyblock.SkyblockSetupStatus;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkyblockSetupStatusTest {

    @Test
    void statusCopiesChecksAndComputesPlayableAndComplete() {
        List<SkyblockSetupStatus.Check> source = new ArrayList<>(List.of(
                new SkyblockSetupStatus.Check("ready", "Ready", SkyblockSetupStatus.State.SATISFIED,
                        null, null),
                new SkyblockSetupStatus.Check("todo", "Todo", SkyblockSetupStatus.State.INCOMPLETE,
                        "Do it", "screen")));

        SkyblockSetupStatus status = new SkyblockSetupStatus(source);
        source.clear();

        assertEquals(2, status.checks().size());
        assertTrue(status.playable());
        assertFalse(status.complete());
        assertTrue(status.checks().get(1).actionable());
        assertThrows(UnsupportedOperationException.class,
                () -> status.checks().add(new SkyblockSetupStatus.Check("x", "X",
                        SkyblockSetupStatus.State.SATISFIED, "", null)));
    }

    @Test
    void blockedAndAdvisoryChecksHaveDistinctReadinessSemantics() {
        SkyblockSetupStatus.Check blocked = new SkyblockSetupStatus.Check(
                "blocked", "Blocked", SkyblockSetupStatus.State.BLOCKED, null, "screen");
        SkyblockSetupStatus.Check advisory = new SkyblockSetupStatus.Check(
                "advisory", "Advisory", SkyblockSetupStatus.State.ADVISORY, null, "screen");

        SkyblockSetupStatus status = new SkyblockSetupStatus(List.of(blocked, advisory));

        assertFalse(status.playable());
        assertFalse(status.complete());
        assertTrue(blocked.actionable());
        assertFalse(advisory.actionable());
        assertEquals("", blocked.reason());
    }

    @Test
    void nullRuntimeProducesBlockedStatusWithoutConstructingPaperDialogs() {
        SkyblockSetupStatus status = new SkyblockSetupChecker((SkyblockBootstrap.Runtime) null).check();

        assertEquals(List.of("world"), status.checks().stream()
                .map(SkyblockSetupStatus.Check::id).toList());
        assertEquals(SkyblockSetupStatus.State.BLOCKED, status.checks().getFirst().state());
        assertFalse(status.playable());
    }

    @Test
    void minimalRuntimeProducesDeterministicChecklistWithoutConstructingPaperDialogs() {
        SkyblockBootstrap.Runtime runtime = mock(SkyblockBootstrap.Runtime.class);
        World world = mock(World.class);
        when(runtime.world()).thenReturn(world);
        when(runtime.profile()).thenReturn("skyblock");
        when(world.getKey()).thenReturn(NamespacedKey.fromString("minecraft:overworld"));

        SkyblockSetupStatus status = new SkyblockSetupChecker(runtime).check();

        assertEquals(18, status.checks().size());
        assertEquals(SkyblockSetupStatus.State.SATISFIED, status.checks().get(0).state());
        assertEquals(SkyblockSetupStatus.State.ADVISORY, status.checks().get(1).state());
        assertEquals(SkyblockSetupStatus.State.INCOMPLETE, status.checks().get(4).state());
        assertEquals(SkyblockSetupStatus.State.BLOCKED, status.checks().get(6).state());
        assertEquals(SkyblockSetupStatus.State.BLOCKED, status.checks().get(11).state());
        assertEquals(List.of("world", "nether-portals", "end-portals", "sethome", "spawn", "templates",
                        "difficulties", "types", "type-references", "generators", "shop", "challenges",
                        "default-type", "playable-types", "reachable-challenges", "challenge-integrity",
                        "shop-prices", "generator-weights"),
                status.checks().stream().map(SkyblockSetupStatus.Check::id).toList());
        assertFalse(status.playable());
        assertFalse(status.complete());
    }
}
