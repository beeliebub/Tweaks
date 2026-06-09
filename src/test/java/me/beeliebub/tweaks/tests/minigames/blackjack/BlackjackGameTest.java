package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.minigames.blackjack.BlackjackGame;
import me.beeliebub.tweaks.minigames.cards.Card;
import me.beeliebub.tweaks.minigames.cards.Rank;
import me.beeliebub.tweaks.minigames.cards.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure scoring tests for {@link BlackjackGame}. These exercise the rules engine
 * with no running server: Ace high/low, busts, naturals, and face-card hands.
 */
class BlackjackGameTest {

    private static Card c(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }

    @Test
    void aceCountsAsElevenWhenItDoesNotBust() {
        // Ace + 9 = 20 (Ace high).
        int value = BlackjackGame.handValue(List.of(
                c(Rank.ACE, Suit.SPADES),
                c(Rank.NINE, Suit.HEARTS)));
        assertEquals(20, value, "Ace should count as 11 here");
    }

    @Test
    void aceCountsAsOneWhenElevenWouldBust() {
        // Ace + 9 + 5 = 25 with Ace high, so Ace drops to 1 -> 15.
        int value = BlackjackGame.handValue(List.of(
                c(Rank.ACE, Suit.SPADES),
                c(Rank.NINE, Suit.HEARTS),
                c(Rank.FIVE, Suit.CLUBS)));
        assertEquals(15, value, "Ace should downgrade to 1 to avoid busting");
    }

    @Test
    void multipleAcesOnlyOneStaysHigh() {
        // A + A + 9 = best is 11 + 1 + 9 = 21.
        int value = BlackjackGame.handValue(List.of(
                c(Rank.ACE, Suit.SPADES),
                c(Rank.ACE, Suit.HEARTS),
                c(Rank.NINE, Suit.CLUBS)));
        assertEquals(21, value, "Only one Ace should remain valued at 11");
    }

    @Test
    void bustIsDetectedOverTwentyOne() {
        List<Card> hand = List.of(
                c(Rank.KING, Suit.SPADES),
                c(Rank.QUEEN, Suit.HEARTS),
                c(Rank.FIVE, Suit.CLUBS));
        assertEquals(25, BlackjackGame.handValue(hand));
        assertTrue(BlackjackGame.isBust(hand), "25 must be a bust");
        assertFalse(BlackjackGame.isBlackjack(hand), "A three-card hand is never a natural");
    }

    @Test
    void naturalTwentyOneIsBlackjack() {
        List<Card> hand = List.of(
                c(Rank.ACE, Suit.SPADES),
                c(Rank.KING, Suit.HEARTS));
        assertEquals(21, BlackjackGame.handValue(hand));
        assertTrue(BlackjackGame.isBlackjack(hand), "Ace + King on two cards is Blackjack");
        assertFalse(BlackjackGame.isBust(hand));
    }

    @Test
    void threeCardTwentyOneIsNotBlackjack() {
        // 21 reached on three cards is 21 but NOT a natural.
        List<Card> hand = List.of(
                c(Rank.SEVEN, Suit.SPADES),
                c(Rank.SEVEN, Suit.HEARTS),
                c(Rank.SEVEN, Suit.CLUBS));
        assertEquals(21, BlackjackGame.handValue(hand));
        assertFalse(BlackjackGame.isBlackjack(hand), "21 across three cards is not a natural");
    }

    @Test
    void faceCardsAreWorthTen() {
        // J + Q = 20; the face-card hand must total exactly 20.
        int value = BlackjackGame.handValue(List.of(
                c(Rank.JACK, Suit.DIAMONDS),
                c(Rank.QUEEN, Suit.CLUBS)));
        assertEquals(20, value, "Two face cards should total 20");
    }

    // ---- Token / resource-pack contract tests ----------------------------------

    @Test
    void rankTokensMatchResourcePackContract() {
        assertEquals("A",  Rank.ACE.token(),   "ACE token");
        assertEquals("2",  Rank.TWO.token(),   "TWO token");
        assertEquals("5",  Rank.FIVE.token(),  "FIVE token");
        assertEquals("9",  Rank.NINE.token(),  "NINE token");
        assertEquals("X",  Rank.TEN.token(),   "TEN token");
        assertEquals("J",  Rank.JACK.token(),  "JACK token");
        assertEquals("Q",  Rank.QUEEN.token(), "QUEEN token");
        assertEquals("K",  Rank.KING.token(),  "KING token");
    }

    @Test
    void suitTokensMatchResourcePackContract() {
        assertEquals("flower",   Suit.CLUBS.token(),    "CLUBS token");
        assertEquals("sword",    Suit.SPADES.token(),   "SPADES token");
        assertEquals("redstone", Suit.HEARTS.token(),   "HEARTS token");
        assertEquals("pick",     Suit.DIAMONDS.token(), "DIAMONDS token");
    }

    @Test
    void isRedCorrectForAllSuits() {
        assertFalse(Suit.CLUBS.isRed(),    "CLUBS should be black");
        assertFalse(Suit.SPADES.isRed(),   "SPADES should be black");
        assertTrue(Suit.HEARTS.isRed(),    "HEARTS should be red");
        assertTrue(Suit.DIAMONDS.isRed(),  "DIAMONDS should be red");
    }

    @Test
    void cardDelegatesTokensAndRedness() {
        Card aceSpades   = c(Rank.ACE, Suit.SPADES);
        Card tenDiamonds = c(Rank.TEN, Suit.DIAMONDS);

        assertEquals("A",      aceSpades.rankToken());
        assertEquals("sword",  aceSpades.suitToken());
        assertFalse(aceSpades.isRed(), "Ace of Spades should not be red");

        assertEquals("X",      tenDiamonds.rankToken());
        assertEquals("pick",   tenDiamonds.suitToken());
        assertTrue(tenDiamonds.isRed(), "Ten of Diamonds should be red");
    }
}
