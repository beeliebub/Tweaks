package me.beeliebub.tweaks.tests.discord;

import me.beeliebub.tweaks.discord.SettlementLine;
import me.beeliebub.tweaks.discord.SettlementLineBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementLineBufferTest {

    @Test
    void rendersInsertionOrderAndDiffPrefixes() {
        SettlementLineBuffer buffer = new SettlementLineBuffer(message -> {});
        buffer.add(new SettlementLine("win", SettlementLine.Sign.WIN));
        buffer.add(new SettlementLine("loss", SettlementLine.Sign.LOSS));
        buffer.add(new SettlementLine("even", SettlementLine.Sign.NEUTRAL));

        assertEquals(List.of("```diff\n+ win\n- loss\n  even\n```"), buffer.drainBlocks());
        assertFalse(buffer.hasPending());
    }

    @Test
    void splitsByLineCountWithoutDroppingRemainder() {
        SettlementLineBuffer buffer = new SettlementLineBuffer(message -> {});
        for (int i = 0; i < SettlementLineBuffer.MAX_BLOCK_LINES + 1; i++) {
            buffer.add(new SettlementLine("line-" + i, SettlementLine.Sign.NEUTRAL));
        }

        List<String> blocks = buffer.drainBlocks();
        assertEquals(2, blocks.size());
        assertTrue(blocks.get(0).contains("line-0"));
        assertTrue(blocks.get(1).contains("line-40"));
    }

    @Test
    void splitsByCharactersWithoutTruncatingLines() {
        SettlementLineBuffer buffer = new SettlementLineBuffer(message -> {});
        String first = "x".repeat(1_000);
        String second = "y".repeat(1_000);
        buffer.add(new SettlementLine(first, SettlementLine.Sign.NEUTRAL));
        buffer.add(new SettlementLine(second, SettlementLine.Sign.NEUTRAL));

        List<String> blocks = buffer.drainBlocks();
        assertEquals(2, blocks.size());
        assertTrue(blocks.get(0).contains(first));
        assertTrue(blocks.get(1).contains(second));
    }

    @Test
    void overflowLogsOnlyOnEntryAndRecoveryTransitions() {
        List<String> warnings = new ArrayList<>();
        List<String> infos = new ArrayList<>();
        SettlementLineBuffer buffer = new SettlementLineBuffer(warnings::add, infos::add);
        for (int i = 0; i < SettlementLineBuffer.MAX_PENDING_LINES + 5; i++) {
            buffer.add(new SettlementLine("line-" + i, SettlementLine.Sign.NEUTRAL));
        }
        assertEquals(1, warnings.size());
        buffer.add(new SettlementLine("another", SettlementLine.Sign.NEUTRAL));
        assertEquals(1, warnings.size());
        buffer.drainBlocks();
        assertEquals(1, infos.size());
    }

    @Test
    void headerRendersWithoutDiffPrefixAndStaysWithGroupedLines() {
        SettlementLineBuffer buffer = new SettlementLineBuffer(message -> {});
        buffer.add(SettlementLine.header("@@ Roulette — 17 Black @@", 9L));
        buffer.add(SettlementLine.forNet("[Roulette] Alice +$100", 100, 9L));

        assertEquals(List.of("```diff\n@@ Roulette — 17 Black @@\n+ [Roulette] Alice +$100\n```"),
                buffer.drainBlocks());
    }

    @Test
    void oversizedGroupRepeatsHeaderOnContinuation() {
        SettlementLineBuffer buffer = new SettlementLineBuffer(message -> {});
        buffer.add(SettlementLine.header("@@ Roulette — 17 Black @@", 2L));
        for (int i = 0; i < SettlementLineBuffer.MAX_BLOCK_LINES; i++) {
            buffer.add(SettlementLine.forNet("bettor-" + i + " " + "x".repeat(80), -1, 2L));
        }

        List<String> blocks = buffer.drainBlocks();
        assertTrue(blocks.size() > 1);
        for (int i = 1; i < blocks.size(); i++) {
            assertTrue(blocks.get(i).contains("@@ Roulette — 17 Black @@"));
        }
    }

    @Test
    void headerRejectsFenceBreakingText() {
        assertThrows(IllegalArgumentException.class, () -> SettlementLine.header("bad`", 1L));
        assertThrows(IllegalArgumentException.class, () -> SettlementLine.header("bad\nline", 1L));
    }
}
