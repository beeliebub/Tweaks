package me.beeliebub.tweaks.minigames.cards;

/** Card ranks. */
public enum Rank {
    ACE("ace", "A", 11),
    TWO("2", "2", 2),
    THREE("3", "3", 3),
    FOUR("4", "4", 4),
    FIVE("5", "5", 5),
    SIX("6", "6", 6),
    SEVEN("7", "7", 7),
    EIGHT("8", "8", 8),
    NINE("9", "9", 9),
    TEN("10", "X", 10),
    JACK("jack", "J", 10),
    QUEEN("queen", "Q", 10),
    KING("king", "K", 10);

    private final String key;
    private final String token;
    private final int baseValue;

    Rank(String key, String token, int baseValue) {
        this.key = key;
        this.token = token;
        this.baseValue = baseValue;
    }

    /** Lowercase key used in chat messages (e.g. "ace", "jack"). */
    public String key() {
        return key;
    }

    /**
     * Resource-pack rank token used in {@code minecraft:custom_model_data}
     * strings[1]: "A", "2".."9", "X" (ten), "J", "Q", "K".
     */
    public String token() {
        return token;
    }

    /** Nominal value (Ace reported as 11; downgraded to 1 in hand scoring). */
    public int baseValue() {
        return baseValue;
    }
}
