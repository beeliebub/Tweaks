package me.beeliebub.tweaks.minigames.roulette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A single round's state machine and bet ledger, plus the pure settlement math. No Bukkit types
 * — see {@code roulette/CLAUDE.md}. Mirrors {@code BlackjackSessionManager.computeSettlement}'s
 * shape: a pure static method extracted so the payout/rakeback/house-credit math is unit-testable
 * without MockBukkit.
 */
public final class RouletteRound {

    /** {@code IDLE -> BETTING -> SPINNING -> SETTLED}. There is no bet-removal method at any stage. */
    public enum State { IDLE, BETTING, SPINNING, SETTLED }

    /** Worst-case payout multiplier (STRAIGHT on pocket 0, Green, 50:1), used to size the exposure
     *  guard. No {@code + 1} — a winning bet's total credit is {@code amount * multiplier} only,
     *  since the wagered stake itself is never returned. */
    private static final long MAX_EXPOSURE_MULTIPLIER = RouletteBet.GREEN_PAYOUT_MULTIPLIER;

    /**
     * The largest single cumulative round exposure any player can ever reach — the same ceiling
     * {@link #canPlace} enforces per click, published so {@code /roulette stake} can reject a
     * stake no click could ever accept without hardcoding {@link #MAX_EXPOSURE_MULTIPLIER} a
     * second time.
     */
    public static final int MAX_CUMULATIVE_WAGER = (int) (Integer.MAX_VALUE / MAX_EXPOSURE_MULTIPLIER);

    private State state = State.IDLE;
    private final List<RouletteBet> bets = new ArrayList<>();
    private final Map<UUID, Long> wageredByPlayer = new HashMap<>();
    private int drawnPocket = -1;

    public State state() {
        return state;
    }

    /** Immutable snapshot of every bet placed so far this round. */
    public List<RouletteBet> bets() {
        return List.copyOf(bets);
    }

    /**
     * Non-mutating check for whether {@code player} could place a bet of {@code amount} right now
     * — {@link State#IDLE}/{@link State#BETTING} only, and this player's cumulative round exposure
     * ({@code totalWagered * MAX_EXPOSURE_MULTIPLIER}) would not exceed {@code Integer.MAX_VALUE}
     * (an integer-overflow ceiling — the only ceiling this feature enforces). Callers that need to
     * reject before debiting a player's balance should call this first; {@link #placeBet} runs the
     * identical check internally.
     */
    public boolean canPlace(UUID player, int amount) {
        if (state != State.IDLE && state != State.BETTING) {
            return false;
        }
        long newTotal = wageredByPlayer.getOrDefault(player, 0L) + amount;
        return newTotal * MAX_EXPOSURE_MULTIPLIER <= Integer.MAX_VALUE;
    }

    /**
     * Places a bet. The first bet on an {@link State#IDLE} round opens the betting window for
     * everyone — the shared-timed-round rule. Returns {@code false} with no state mutated if
     * {@link #canPlace} rejects it.
     */
    public boolean placeBet(RouletteBet bet) {
        Objects.requireNonNull(bet, "bet");
        if (!canPlace(bet.player(), bet.amount())) {
            return false;
        }
        state = State.BETTING;
        bets.add(bet);
        wageredByPlayer.merge(bet.player(), (long) bet.amount(), Long::sum);
        return true;
    }

    /**
     * Closes betting and records the pocket drawn for this round. The pocket is drawn the instant
     * betting closes (never during or after the animation) so a crash mid-animation settles
     * deterministically from an already-committed outcome instead of needing a re-roll.
     */
    public void closeBetting(int drawnPocket) {
        if (drawnPocket < 0 || drawnPocket > 36) {
            throw new IllegalArgumentException("drawnPocket must be 0-36, was " + drawnPocket);
        }
        requireState(State.BETTING);
        this.drawnPocket = drawnPocket;
        state = State.SPINNING;
    }

    /** The pocket drawn when betting closed, or {@code -1} if betting is still open. */
    public int drawnPocket() {
        return drawnPocket;
    }

    /** Marks the round settled, once the drawn pocket has been credited. */
    public void settle() {
        requireState(State.SPINNING);
        state = State.SETTLED;
    }

    private void requireState(State required) {
        if (state != required) {
            throw new IllegalStateException("Expected state " + required + " but was " + state);
        }
    }

    /**
     * Per-player credit from a settled round: {@code payout} is winnings only — the sum of {@code
     * amount * multiplier} over every bet this player won this round. The wagered stake, already
     * debited at bet-placement time, is never returned as part of this figure — plus any rakeback.
     */
    public record PlayerCredit(long payout, long rakeback) {
    }

    /** Full settlement result, including players whose net round result was a loss. */
    public record Settlement(Map<UUID, PlayerCredit> credits, long houseCredit, Set<UUID> netLosers) {
    }

    /**
     * Pure settlement over an arbitrary bet list — no Bukkit types, no side effects — so the money
     * math is unit-testable without MockBukkit. Returns per-player {@code (payout, rakeback)}
     * credits plus the round's house winnings.
     *
     * <p>A winning bet credits {@code amount * multiplier} — winnings only. The wagered stake was
     * already deducted at placement and is never returned, even on a win, so a 2:1 win on a $1,000
     * bet credits exactly $2,000 (not $3,000). Rakeback applies only to a player's net loss across
     * the whole round (never a net win), floored, using that player's own rate — note that under
     * this no-stake-return rule, "net" (payout minus wagered) already nets out correctly to the
     * player's true profit/loss without needing to separately account for a returned stake. The
     * house is credited the sum of each player's net loss, floored at zero per player, so a player
     * who hedges into a break-even result does not feed the pot. One player's net win never offsets
     * another player's loss, and rakeback is minted rather than taken from the house's cut.
     */
    public static Settlement computeSettlement(
            List<RouletteBet> bets, int pocket, Map<UUID, Double> rakebackRates) {
        Map<UUID, Long> wageredByPlayer = new HashMap<>();
        Map<UUID, Long> payoutByPlayer = new HashMap<>();
        for (RouletteBet bet : bets) {
            wageredByPlayer.merge(bet.player(), (long) bet.amount(), Long::sum);
            if (bet.wins(pocket)) {
                long payout = (long) bet.amount() * bet.payoutMultiplier();
                payoutByPlayer.merge(bet.player(), payout, Long::sum);
            }
        }

        Map<UUID, PlayerCredit> credits = new HashMap<>();
        Set<UUID> netLosers = new HashSet<>();
        long houseCredit = 0L;
        for (Map.Entry<UUID, Long> entry : wageredByPlayer.entrySet()) {
            UUID player = entry.getKey();
            long wagered = entry.getValue();
            long payout = payoutByPlayer.getOrDefault(player, 0L);
            long net = payout - wagered;
            long rakeback = 0L;
            if (net < 0) {
                netLosers.add(player);
                houseCredit += -net;
                double rate = rakebackRates.getOrDefault(player, 0.0);
                rakeback = (long) Math.floor(-net * rate);
            }
            credits.put(player, new PlayerCredit(payout, rakeback));
        }

        return new Settlement(Map.copyOf(credits), Math.max(0L, houseCredit), Set.copyOf(netLosers));
    }
}
