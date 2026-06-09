package me.beeliebub.tweaks.minigames.blackjack;

import me.beeliebub.tweaks.minigames.cards.Card;
import me.beeliebub.tweaks.minigames.cards.Deck;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure (server-free) head-to-head Blackjack game state for two human players.
 *
 * <p>Unlike {@link BlackjackGame} (player versus an automated dealer), this is a
 * <b>player-versus-player</b> game: two hands ("A" and "B") are dealt from a single
 * shared {@link Deck}. There is no dealer and no hole card — both hands are open
 * information. Each side hits or stands independently; when both sides are finished
 * (stood or busted) the game settles by comparison.
 *
 * <p>Like {@link BlackjackGame}, this class contains <b>no Bukkit calls</b> so the rules
 * can be unit-tested without a running server. Scoring reuses
 * {@link BlackjackGame#handValue(List)} / {@link BlackjackGame#isBust(List)} so the Ace
 * high/low logic stays in one place.
 *
 * <p>Outcome rules ({@link Result}):
 * <ul>
 *   <li>Both sides bust &rarr; {@link Result#PUSH} (no winner; stakes refunded by the caller).</li>
 *   <li>Exactly one side busts &rarr; the other side wins, regardless of total.</li>
 *   <li>Neither busts &rarr; the higher hand value wins; equal totals &rarr; {@link Result#PUSH}.</li>
 * </ul>
 */
public final class BlackjackPvpGame {

    /** Terminal outcome of a settled head-to-head game. */
    public enum Result {
        A_WINS,
        B_WINS,
        PUSH
    }

    /**
     * Pre-game and in-game phases for a PvP session.
     *
     * <p>Pipeline: REGISTERING → BETTING → CONFIRMING_BETS → ACTIVE.
     * <ul>
     *   <li>REGISTERING — table is open; each player must press MIDDLE to claim a seat.
     *       Once both sides are claimed the game advances to BETTING automatically.</li>
     *   <li>BETTING — players place their item / money wagers freely. Both press MIDDLE
     *       to lock in their stakes and advance to CONFIRMING_BETS.</li>
     *   <li>CONFIRMING_BETS — both players' stakes are displayed; both must press MIDDLE
     *       again to confirm and advance to ACTIVE (cards dealt).</li>
     *   <li>ACTIVE — cards have been dealt; gameplay proceeds normally.</li>
     * </ul>
     * Any LEFT or RIGHT press before ACTIVE cancels the session with full refund.
     */
    public enum State {
        REGISTERING,
        BETTING,
        CONFIRMING_BETS,
        ACTIVE
    }

    /** Side index for hand A (the first registered side). */
    public static final int SIDE_A = 0;
    /** Side index for hand B (the opposite side). */
    public static final int SIDE_B = 1;

    private State state = State.REGISTERING;
    private final Deck deck;
    private final List<Card> handA = new ArrayList<>();
    private final List<Card> handB = new ArrayList<>();

    private boolean standA;
    private boolean standB;

    private boolean finished;
    private Result result;

    /** Wall-clock millisecond timestamp of the last action (deal, hit, stand). */
    private long lastInteractionTime;

    public BlackjackPvpGame() {
        this.deck = Deck.standard52();
        this.lastInteractionTime = System.currentTimeMillis();
    }

    public State state() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    // ---- Setup --------------------------------------------------------------

    /** Deal the opening two cards to each side (A, B, A, B). Sets state to ACTIVE. */
    public void dealInitial() {
        this.state = State.ACTIVE;
        lastInteractionTime = System.currentTimeMillis();
        handA.clear();
        handB.clear();
        handA.add(deck.draw());
        handB.add(deck.draw());
        handA.add(deck.draw());
        handB.add(deck.draw());
    }

    // ---- Player actions -----------------------------------------------------

    /**
     * Deal one more card to {@code side}. A no-op if the game is finished or that side
     * has already stood or busted. A bust auto-stands that side; once both sides are
     * done, the game settles.
     *
     * @param side {@link #SIDE_A} or {@link #SIDE_B}
     */
    public void hit(int side) {
        if (finished || done(side)) {
            return;
        }
        lastInteractionTime = System.currentTimeMillis();
        hand(side).add(deck.draw());
        // A bust ends this side's turn automatically.
        if (BlackjackGame.isBust(hand(side))) {
            stand(side, true);
        }
        if (done(SIDE_A) && done(SIDE_B)) {
            settle();
        }
    }

    /**
     * Mark {@code side} as standing. A no-op if the game is finished. Once both sides
     * are done, the game settles.
     *
     * @param side {@link #SIDE_A} or {@link #SIDE_B}
     */
    public void stand(int side) {
        if (finished) {
            return;
        }
        stand(side, false);
        if (done(SIDE_A) && done(SIDE_B)) {
            settle();
        }
    }

    private void stand(int side, boolean fromBust) {
        if (!fromBust) {
            lastInteractionTime = System.currentTimeMillis();
        }
        if (side == SIDE_A) {
            standA = true;
        } else {
            standB = true;
        }
    }

    /** A side is "done" once it has stood or busted. */
    private boolean done(int side) {
        boolean stood = side == SIDE_A ? standA : standB;
        return stood || BlackjackGame.isBust(hand(side));
    }

    // ---- Settlement ---------------------------------------------------------

    /**
     * Compute the {@link Result} from current hands and mark the game finished.
     * Idempotent: once settled, re-calling is a no-op.
     */
    public void settle() {
        if (finished) {
            return;
        }
        finished = true;
        boolean aBust = BlackjackGame.isBust(handA);
        boolean bBust = BlackjackGame.isBust(handB);
        if (aBust && bBust) {
            result = Result.PUSH;
        } else if (aBust) {
            result = Result.B_WINS;
        } else if (bBust) {
            result = Result.A_WINS;
        } else {
            int a = BlackjackGame.handValue(handA);
            int b = BlackjackGame.handValue(handB);
            if (a > b) {
                result = Result.A_WINS;
            } else if (b > a) {
                result = Result.B_WINS;
            } else {
                result = Result.PUSH;
            }
        }
    }

    // ---- Accessors ----------------------------------------------------------

    private List<Card> hand(int side) {
        return side == SIDE_A ? handA : handB;
    }

    /** Live view of hand A (callers must not mutate). */
    public List<Card> handA() {
        return handA;
    }

    /** Live view of hand B (callers must not mutate). */
    public List<Card> handB() {
        return handB;
    }

    public int valueA() {
        return BlackjackGame.handValue(handA);
    }

    public int valueB() {
        return BlackjackGame.handValue(handB);
    }

    public boolean standA() {
        return standA;
    }

    public boolean standB() {
        return standB;
    }

    public boolean isFinished() {
        return finished;
    }

    public Result result() {
        return result;
    }

    /** @see BlackjackGame#lastInteractionTime() */
    public long lastInteractionTime() {
        return lastInteractionTime;
    }

    /** Explicitly set {@link #lastInteractionTime}; intended for clock injection in tests. */
    public void touchInteraction(long now) {
        this.lastInteractionTime = now;
    }
}
