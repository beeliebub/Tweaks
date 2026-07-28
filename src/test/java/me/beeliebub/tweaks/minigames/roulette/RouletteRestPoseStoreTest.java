package me.beeliebub.tweaks.minigames.roulette;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Placed in the production package (no {@code tests.} prefix) so package-private access to
 * {@link RouletteRestPoseStore}'s statics and {@code SegmentPose} record is legal without widening
 * production visibility — mirrors the exception already documented for {@code RouletteGeometryTest}.
 * {@code serializePose}/{@code deserializePose} carry no Bukkit types, so this needs no MockBukkit.
 */
class RouletteRestPoseStoreTest {

    private static final String BOARD_KEY = "world:10:64:-5";

    @Test
    void roundTripsAPoseIncludingNegativeAndNonIntegerCoordinates() {
        UUID entityId = UUID.randomUUID();
        RouletteRestPoseStore.SegmentPose pose =
                new RouletteRestPoseStore.SegmentPose(entityId, -12.375, 63.998, 4.5, 271.5f, -0.001f);

        String serialized = RouletteRestPoseStore.serializePose(BOARD_KEY, pose);
        RouletteRestPoseStore.SegmentPose parsed = RouletteRestPoseStore.deserializePose(serialized, BOARD_KEY);

        assertEquals(entityId, parsed.entityId());
        assertEquals(-12.375, parsed.x());
        assertEquals(63.998, parsed.y());
        assertEquals(4.5, parsed.z());
        assertEquals(271.5f, parsed.yaw());
        assertEquals(-0.001f, parsed.pitch());
    }

    @Test
    void deserializeReturnsNullNeverThrowsOnWrongFieldCount() {
        assertNull(RouletteRestPoseStore.deserializePose("a|b|c", BOARD_KEY));
    }

    @Test
    void deserializeReturnsNullOnUnparseableNumber() {
        UUID entityId = UUID.randomUUID();
        String malformed = BOARD_KEY + '|' + entityId + "|not-a-number|64|4|0|0";
        assertNull(RouletteRestPoseStore.deserializePose(malformed, BOARD_KEY));
    }

    @Test
    void deserializeReturnsNullOnBadUuid() {
        String malformed = BOARD_KEY + "|not-a-uuid|1|2|3|0|0";
        assertNull(RouletteRestPoseStore.deserializePose(malformed, BOARD_KEY));
    }

    @Test
    void deserializeReturnsNullOnMismatchedBoardKey() {
        UUID entityId = UUID.randomUUID();
        RouletteRestPoseStore.SegmentPose pose =
                new RouletteRestPoseStore.SegmentPose(entityId, 1.0, 2.0, 3.0, 0f, 0f);
        String serialized = RouletteRestPoseStore.serializePose(BOARD_KEY, pose);

        assertNull(RouletteRestPoseStore.deserializePose(serialized, "world:99:64:99"),
                "a row belonging to a different board must never be mistaken for this one — the "
                        + "two-simultaneously-spinning-boards isolation guarantee");
    }
}
