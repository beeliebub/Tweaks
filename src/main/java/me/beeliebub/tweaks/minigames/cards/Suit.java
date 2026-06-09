package me.beeliebub.tweaks.minigames.cards;

/** Card suits. */
public enum Suit {
    CLUBS("clubs", "flower", false),
    DIAMONDS("diamonds", "pick", true),
    HEARTS("hearts", "redstone", true),
    SPADES("spades", "sword", false);

    private final String key;
    private final String token;
    private final boolean red;

    Suit(String key, String token, boolean red) {
        this.key = key;
        this.token = token;
        this.red = red;
    }

    public String key() {
        return key;
    }

    /**
     * Resource-pack suit token used in {@code minecraft:custom_model_data}
     * strings[0]: "flower" (clubs), "pick" (diamonds), "redstone" (hearts),
     * "sword" (spades).
     */
    public String token() {
        return token;
    }

    /**
     * True for red suits (HEARTS, DIAMONDS); false for black suits (CLUBS, SPADES).
     * Used by the listener to pick the pip colour without any Bukkit dependency here.
     */
    public boolean isRed() {
        return red;
    }
}
