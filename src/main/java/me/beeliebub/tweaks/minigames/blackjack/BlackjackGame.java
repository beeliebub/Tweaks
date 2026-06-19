package me.beeliebub.tweaks.minigames.blackjack;

import me.beeliebub.tweaks.minigames.cards.Card;
import me.beeliebub.tweaks.minigames.cards.Deck;
import me.beeliebub.tweaks.minigames.cards.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure (server-free) Blackjack game state and scoring logic.
 *
 * <p>This class deliberately contains <b>no Bukkit calls</b>. Every method that
 * deals, draws, scores, or settles a hand is plain Java so the rules can be
 * exercised by unit tests without a running server. The rendering of cards as
 * {@code ItemDisplay} entities and all economy settlement live in
 * {@link BlackjackListener}/{@code BlackjackCommand}, which consume this state.
 *
 * <p>Rules implemented:
 * <ul>
 *   <li>Standard single 52-card deck, reshuffled per game.</li>
 *   <li>Face cards (J/Q/K) are worth 10; Aces are 1 or 11, whichever yields the
 *       best total {@code <= 21} ({@link #handValue(List)}).</li>
 *   <li>A natural 21 on the opening two cards is a Blackjack
 *       ({@link #isBlackjack(List)}).</li>
 *   <li>The dealer hits until reaching at least 17 (stands on all 17s,
 *       including soft 17) via {@link #playDealer()}.</li>
 * </ul>
 */
public final class BlackjackGame {

    /** Terminal outcome of a settled game from the player's perspective. */
    public enum Result {
        PLAYER_BLACKJACK,
        PLAYER_WIN,
        DEALER_WIN,
        PUSH
    }

    private final UUID playerId;
    private final int bet;

    private final Deck deck;
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> dealerHand = new ArrayList<>();

    private boolean finished;
    private Result result;

    /** Wall-clock millisecond timestamp of the last player action (deal, hit, stand). */
    private long lastInteractionTime;

    public BlackjackGame(UUID playerId, int bet) {
        this.playerId = playerId;
        this.bet = bet;
        this.deck = Deck.standard52();
        this.lastInteractionTime = System.currentTimeMillis();
    }

    // ---- Setup --------------------------------------------------------------

    /** Deal the opening two cards each to player and dealer. */
    public void dealInitial() {
        lastInteractionTime = System.currentTimeMillis();
        playerHand.clear();
        dealerHand.clear();
        playerHand.add(deck.draw());
        dealerHand.add(deck.draw());
        playerHand.add(deck.draw());
        dealerHand.add(deck.draw());

        // Immediate naturals settle the game right away.
        if (isBlackjack(playerHand) || isBlackjack(dealerHand)) {
            settle();
        }
    }

    // ---- Scoring (pure, unit-tested) ----------------------------------------

    /**
     * Best hand value {@code <= 21} when possible. Aces count as 11 unless that
     * would bust, in which case as many Aces as needed drop to 1.
     */
    public static int handValue(List<Card> hand) {
        int total = 0;
        int aces = 0;
        for (Card card : hand) {
            total += card.rank().baseValue();
            if (card.rank() == Rank.ACE) {
                aces++;
            }
        }
        // Each downgrade of an Ace (11 -> 1) removes 10 from the total.
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    /** True if the hand totals over 21. */
    public static boolean isBust(List<Card> hand) {
        return handValue(hand) > 21;
    }

    /** True for a two-card natural 21. */
    public static boolean isBlackjack(List<Card> hand) {
        return hand.size() == 2 && handValue(hand) == 21;
    }

    // ---- Player actions -----------------------------------------------------

    /** Deal one more card to the player. Auto-settles if the player busts. */
    public void playerHit() {
        if (finished) {
            return;
        }
        lastInteractionTime = System.currentTimeMillis();
        playerHand.add(deck.draw());
        if (isBust(playerHand)) {
            settle();
        }
    }

    /**
     * Player stands: the dealer plays out their hand and the game is settled.
     */
    public void playerStand() {
        if (finished) {
            return;
        }
        lastInteractionTime = System.currentTimeMillis();
        playDealer();
        settle();
    }

    /** Dealer draws until reaching a hard/soft 17 or busting. */
    void playDealer() {
        while (handValue(dealerHand) < 17) {
            dealerHand.add(deck.draw());
        }
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
        this.result = computeResult();
    }

    private Result computeResult() {
        boolean playerBj = isBlackjack(playerHand);
        boolean dealerBj = isBlackjack(dealerHand);

        if (playerBj && dealerBj) {
            return Result.PUSH;
        }
        if (playerBj) {
            return Result.PLAYER_BLACKJACK;
        }
        if (dealerBj) {
            return Result.DEALER_WIN;
        }
        if (isBust(playerHand)) {
            return Result.DEALER_WIN;
        }
        if (isBust(dealerHand)) {
            return Result.PLAYER_WIN;
        }

        int playerTotal = handValue(playerHand);
        int dealerTotal = handValue(dealerHand);
        if (playerTotal > dealerTotal) {
            return Result.PLAYER_WIN;
        }
        if (playerTotal < dealerTotal) {
            return Result.DEALER_WIN;
        }
        return Result.PUSH;
    }

    /**
     * Net payout the player should be CREDITED for this game's result, given the
     * bet was already deducted up front.
     * <ul>
     *   <li>Blackjack: bet returned + 1.5x bet winnings = 2.5x bet.</li>
     *   <li>Win: bet returned + 1x bet winnings = 2x bet.</li>
     *   <li>Push: bet returned = 1x bet.</li>
     *   <li>Loss: nothing credited.</li>
     * </ul>
     */
    public int payout() {
        if (result == null) {
            return 0;
        }
        return switch (result) {
            case PLAYER_BLACKJACK -> bet + (int) Math.floor(bet * 1.5);
            case PLAYER_WIN -> bet * 2;
            case PUSH -> bet;
            case DEALER_WIN -> 0;
        };
    }

    // ---- Accessors ----------------------------------------------------------

    public UUID playerId() {
        return playerId;
    }

    public int bet() {
        return bet;
    }

    public boolean isFinished() {
        return finished;
    }

    public Result result() {
        return result;
    }

    /** Live view of the player's hand (callers must not mutate). */
    public List<Card> playerHand() {
        return playerHand;
    }

    /** Live view of the dealer's hand (callers must not mutate). */
    public List<Card> dealerHand() {
        return dealerHand;
    }

    public int playerValue() {
        return handValue(playerHand);
    }

    public int dealerValue() {
        return handValue(dealerHand);
    }

    /**
     * Returns the wall-clock millisecond timestamp of the last player action
     * (construction, {@link #dealInitial}, {@link #playerHit}, {@link #playerStand}).
     * Used by the inactivity sweeper in {@code BlackjackListener}.
     */
    public long lastInteractionTime() {
        return lastInteractionTime;
    }

    /**
     * Explicitly sets {@link #lastInteractionTime} to {@code now}.
     * Intended for clock injection in tests so the inactivity timeout can be
     * exercised deterministically without real wall-clock delays.
     *
     * @param now the simulated "current time" in milliseconds
     */
    public void touchInteraction(long now) {
        this.lastInteractionTime = now;
    }
}
