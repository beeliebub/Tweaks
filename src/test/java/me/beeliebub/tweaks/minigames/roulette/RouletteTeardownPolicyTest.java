package me.beeliebub.tweaks.minigames.roulette;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placed in the production package (no {@code tests.} prefix) so package-private access to
 * {@link RouletteTeardownPolicy} is legal without widening production visibility — mirrors the
 * exception already documented for {@code RouletteGeometryTest}/{@code RouletteStakeValidationTest}.
 * Pure logic, no MockBukkit needed.
 *
 * <p>This class exhaustively pins the {@code State x EndReason} truth table (4 states x 5 reasons
 * = 20 cases, no gaps) — it IS the acceptance criterion for the teardown funnel's "every exit path
 * lands the money somewhere defensible" claim.
 */
class RouletteTeardownPolicyTest {

    // ---- IDLE: never touches money, regardless of reason ----

    @ParameterizedTest
    @EnumSource(RouletteTeardownPolicy.EndReason.class)
    void idleNeverMovesMoney(RouletteTeardownPolicy.EndReason reason) {
        assertEquals(RouletteTeardownPolicy.MoneyAction.NONE,
                RouletteTeardownPolicy.moneyActionFor(RouletteRound.State.IDLE, reason));
    }

    // ---- SETTLED: never touches money, regardless of reason (already paid out) ----

    @ParameterizedTest
    @EnumSource(RouletteTeardownPolicy.EndReason.class)
    void settledNeverMovesMoney(RouletteTeardownPolicy.EndReason reason) {
        assertEquals(RouletteTeardownPolicy.MoneyAction.NONE,
                RouletteTeardownPolicy.moneyActionFor(RouletteRound.State.SETTLED, reason));
    }

    // ---- BETTING: the state with real debited stakes on the line, and the one that matters most ----

    @Test
    void bettingShutdownRefunds() {
        assertEquals(RouletteTeardownPolicy.MoneyAction.REFUND, RouletteTeardownPolicy.moneyActionFor(
                RouletteRound.State.BETTING, RouletteTeardownPolicy.EndReason.SHUTDOWN));
    }

    @Test
    void bettingChunkUnloadDrawsThenSettles() {
        assertEquals(RouletteTeardownPolicy.MoneyAction.DRAW_THEN_SETTLE, RouletteTeardownPolicy.moneyActionFor(
                RouletteRound.State.BETTING, RouletteTeardownPolicy.EndReason.CHUNK_UNLOAD));
    }

    @Test
    void bettingWithAnUnreachableReasonIsANoOpNotASilentRefund() {
        // LINGER_ELAPSED/RECYCLED/BOARD_REMOVED paired with BETTING is unreachable in practice
        // (hasRoundInFlight blocks removal; linger/recycle only fire on SETTLED) — but the policy
        // must resolve it to NONE, never to a value that would double-refund or crash.
        for (RouletteTeardownPolicy.EndReason reason : new RouletteTeardownPolicy.EndReason[] {
                RouletteTeardownPolicy.EndReason.LINGER_ELAPSED,
                RouletteTeardownPolicy.EndReason.RECYCLED,
                RouletteTeardownPolicy.EndReason.BOARD_REMOVED}) {
            assertEquals(RouletteTeardownPolicy.MoneyAction.NONE,
                    RouletteTeardownPolicy.moneyActionFor(RouletteRound.State.BETTING, reason),
                    "expected NONE for BETTING + " + reason);
        }
    }

    // ---- SPINNING: always settles, regardless of reason ----

    @ParameterizedTest
    @EnumSource(RouletteTeardownPolicy.EndReason.class)
    void spinningAlwaysSettles(RouletteTeardownPolicy.EndReason reason) {
        assertEquals(RouletteTeardownPolicy.MoneyAction.SETTLE,
                RouletteTeardownPolicy.moneyActionFor(RouletteRound.State.SPINNING, reason));
    }

    // ---- shouldForceClose ----

    @Test
    void shouldForceCloseIsFalseBeforeTheCeiling() {
        assertFalse(RouletteTeardownPolicy.shouldForceClose(
                RouletteRound.State.BETTING, 100L, 100L + RouletteTeardownPolicy.IDLE_CEILING_TICKS - 1, RouletteTeardownPolicy.IDLE_CEILING_TICKS));
    }

    @Test
    void shouldForceCloseIsTrueExactlyAtTheCeiling() {
        assertTrue(RouletteTeardownPolicy.shouldForceClose(
                RouletteRound.State.BETTING, 100L, 100L + RouletteTeardownPolicy.IDLE_CEILING_TICKS, RouletteTeardownPolicy.IDLE_CEILING_TICKS));
    }

    @Test
    void shouldForceCloseIsTrueAfterTheCeiling() {
        assertTrue(RouletteTeardownPolicy.shouldForceClose(
                RouletteRound.State.BETTING, 100L, 100L + RouletteTeardownPolicy.IDLE_CEILING_TICKS + 1, RouletteTeardownPolicy.IDLE_CEILING_TICKS));
    }

    @Test
    void shouldForceCloseIsFalseWhenNoWindowIsOpen() {
        assertFalse(RouletteTeardownPolicy.shouldForceClose(
                RouletteRound.State.BETTING, -1L, 999_999L, RouletteTeardownPolicy.IDLE_CEILING_TICKS));
    }

    @ParameterizedTest
    @EnumSource(value = RouletteRound.State.class, names = "BETTING", mode = EnumSource.Mode.EXCLUDE)
    void shouldForceCloseIsFalseForAnyNonBettingState(RouletteRound.State state) {
        assertFalse(RouletteTeardownPolicy.shouldForceClose(
                state, 0L, RouletteTeardownPolicy.IDLE_CEILING_TICKS * 10, RouletteTeardownPolicy.IDLE_CEILING_TICKS));
    }
}
