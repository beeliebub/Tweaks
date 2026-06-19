package me.beeliebub.tweaks.minigames.cards;

/** Immutable playing card. */
public record Card(Rank rank, Suit suit) {
    /** Resource-pack rank token delegated from {@link Rank#token()}. */
    public String rankToken() {
        return rank.token();
    }

    /** Resource-pack suit token delegated from {@link Suit#token()}. */
    public String suitToken() {
        return suit.token();
    }

    /**
     * True when this card's suit is red (HEARTS or DIAMONDS).
     * Delegated from {@link Suit#isRed()} — kept pure (no Bukkit).
     */
    public boolean isRed() {
        return suit.isRed();
    }
}
