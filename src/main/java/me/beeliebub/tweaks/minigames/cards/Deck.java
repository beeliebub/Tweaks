package me.beeliebub.tweaks.minigames.cards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A standard 52-card deck.
 *
 * <p>Use {@link #standard52()} to obtain a new shuffled deck. The deck is
 * Bukkit-free and may be used in unit tests without a running server.
 */
public final class Deck {

    private final List<Card> cards;

    private Deck(List<Card> cards) {
        this.cards = cards;
    }

    /**
     * Returns a new {@link Deck} containing all 52 {@link Suit} × {@link Rank}
     * combinations, already shuffled with {@link ThreadLocalRandom}.
     */
    public static Deck standard52() {
        List<Card> list = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                list.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(list, ThreadLocalRandom.current());
        return new Deck(list);
    }

    /**
     * Shuffle the deck in-place using {@link ThreadLocalRandom}, matching the
     * randomness source used during construction.
     */
    public void shuffle() {
        Collections.shuffle(cards, ThreadLocalRandom.current());
    }

    /**
     * Draw the top card from the deck by removing from the end of the internal
     * list, matching the original {@code deck.removeLast()} draw order.
     *
     * @throws java.util.NoSuchElementException if the deck is empty
     */
    public Card draw() {
        return cards.removeLast();
    }

    /** Returns the number of cards remaining in the deck. */
    public int size() {
        return cards.size();
    }
}
