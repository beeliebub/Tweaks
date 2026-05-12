package me.beeliebub.tweaks.tests.blocklog;

import me.beeliebub.tweaks.blocklog.LogAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogActionTest {

    @Test
    void byteRoundTripPreservesAdd() {
        assertEquals(LogAction.ADD, LogAction.fromByte(LogAction.ADD.toByte()));
    }

    @Test
    void byteRoundTripPreservesRemove() {
        assertEquals(LogAction.REMOVE, LogAction.fromByte(LogAction.REMOVE.toByte()));
    }

    @Test
    void addOrdinalIsZero() {
        assertEquals((byte) 0, LogAction.ADD.toByte());
    }

    @Test
    void removeOrdinalIsOne() {
        assertEquals((byte) 1, LogAction.REMOVE.toByte());
    }

    @Test
    void unknownByteValuesFallBackToAdd() {
        // Documented behaviour: only byte == 1 maps to REMOVE; everything else is ADD.
        assertEquals(LogAction.ADD, LogAction.fromByte((byte) 0));
        assertEquals(LogAction.ADD, LogAction.fromByte((byte) 2));
        assertEquals(LogAction.ADD, LogAction.fromByte((byte) -1));
        assertEquals(LogAction.ADD, LogAction.fromByte((byte) 127));
    }

    @Test
    void enumHasExactlyTwoValues() {
        assertEquals(2, LogAction.values().length);
    }
}
