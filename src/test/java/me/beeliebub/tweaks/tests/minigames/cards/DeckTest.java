package me.beeliebub.tweaks.tests.minigames.cards;

import me.beeliebub.tweaks.minigames.cards.Card;
import me.beeliebub.tweaks.minigames.cards.Deck;
import me.beeliebub.tweaks.minigames.cards.Rank;
import me.beeliebub.tweaks.minigames.cards.Suit;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the extracted {@link Deck}. Guards the contract the old
 * inline {@code BlackjackGame} deck satisfied: a fresh deck holds all 52 unique
 * {@link Rank} × {@link Suit} cards, and {@link Deck#draw()} hands them out one at
 * a time until the deck is empty.
 */
class DeckTest {

    @Test
    void standard52StartsWithFiftyTwoCards() {
        assertEquals(52, Deck.standard52().size(), "A fresh deck must hold 52 cards");
    }

    @Test
    void drawDecrementsSizeAndExhaustsDeck() {
        Deck deck = Deck.standard52();
        for (int remaining = 52; remaining > 0; remaining--) {
            assertEquals(remaining, deck.size());
            assertNotNull(deck.draw());
        }
        assertEquals(0, deck.size(), "Deck must be empty after 52 draws");
    }

    @Test
    void deckContainsEveryUniqueCardExactlyOnce() {
        Deck deck = Deck.standard52();
        Set<Card> seen = new HashSet<>();
        while (deck.size() > 0) {
            assertTrue(seen.add(deck.draw()), "Each of the 52 cards must be unique");
        }
        assertEquals(Rank.values().length * Suit.values().length, seen.size());
        assertEquals(52, seen.size());
    }
}
