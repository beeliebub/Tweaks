package me.beeliebub.tweaks.minigames.roulette;

/**
 * Pure {@code (RouletteRound.State, EndReason) -> MoneyAction} truth table for the teardown
 * funnel — no Bukkit types, unit-testable without MockBukkit, mirroring this package's other
 * pure-logic classes ({@code RouletteWheel}, {@code RouletteSpinMath}). This is the single source
 * of truth for "every exit path lands the money somewhere defensible": {@link RouletteSessionManager
 * #endRound} looks up the action here rather than re-deriving it inline at each call site.
 *
 * <table>
 * <caption>{@link #moneyActionFor} truth table</caption>
 * <tr><th>State \ Reason</th><th>LINGER_ELAPSED / RECYCLED / BOARD_REMOVED</th><th>CHUNK_UNLOAD</th><th>SHUTDOWN</th></tr>
 * <tr><td>IDLE</td><td>NONE</td><td>NONE</td><td>NONE</td></tr>
 * <tr><td>BETTING</td><td>NONE (unreachable — see below)</td><td>DRAW_THEN_SETTLE</td><td>REFUND</td></tr>
 * <tr><td>SPINNING</td><td>SETTLE (unreachable — see below)</td><td>SETTLE</td><td>SETTLE</td></tr>
 * <tr><td>SETTLED</td><td>NONE</td><td>NONE</td><td>NONE</td></tr>
 * </table>
 *
 * <p>{@code BETTING} paired with a non-chunk-unload, non-shutdown reason is unreachable in
 * practice: {@code RouletteListener#hasRoundInFlight} blocks board removal while {@code BETTING},
 * and the linger/recycle reasons only ever fire on a {@code SETTLED} context. {@code SPINNING}
 * paired with the same group is likewise unreachable: {@code finishSpin} always settles before a
 * context can be linger-recycled or board-removed. Both resolve to their state's normal action
 * ({@code NONE}, {@code SETTLE}) rather than a special error value — {@link RouletteSessionManager
 * #endRound} logs {@code SEVERE} if it ever actually observes the {@code BETTING} case, since
 * silently dropping debited stakes would be worse than a loud, correct fallback.
 */
final class RouletteTeardownPolicy {

    /** The idle-sweeper ceiling: 5 minutes, guarding against a lost betting-window task. */
    static final long IDLE_CEILING_TICKS = 6000L;

    /** Every path that can end a round. */
    enum EndReason { LINGER_ELAPSED, RECYCLED, BOARD_REMOVED, CHUNK_UNLOAD, SHUTDOWN }

    /** What {@link RouletteSessionManager#endRound} must do with the round's money. */
    enum MoneyAction { NONE, SETTLE, REFUND, DRAW_THEN_SETTLE }

    private RouletteTeardownPolicy() {
    }

    static MoneyAction moneyActionFor(RouletteRound.State state, EndReason reason) {
        return switch (state) {
            case IDLE, SETTLED -> MoneyAction.NONE;
            case BETTING -> switch (reason) {
                case SHUTDOWN -> MoneyAction.REFUND;
                case CHUNK_UNLOAD -> MoneyAction.DRAW_THEN_SETTLE;
                case LINGER_ELAPSED, RECYCLED, BOARD_REMOVED -> MoneyAction.NONE;
            };
            case SPINNING -> MoneyAction.SETTLE;
        };
    }

    /**
     * True if a {@code BETTING} round has sat open past {@code ceilingTicks} since its window
     * opened — the idle sweeper's force-close trigger, guarding against a lost/
     * cancelled window-expiry task. {@code windowOpenedTick < 0} means no window is open
     * (context not yet in {@code BETTING}, or the field was never set) and never force-closes.
     */
    static boolean shouldForceClose(RouletteRound.State state, long windowOpenedTick, long currentTick,
                                     long ceilingTicks) {
        if (state != RouletteRound.State.BETTING || windowOpenedTick < 0) {
            return false;
        }
        return currentTick - windowOpenedTick >= ceilingTicks;
    }
}
