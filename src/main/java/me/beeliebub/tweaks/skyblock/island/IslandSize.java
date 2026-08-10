package me.beeliebub.tweaks.skyblock.island;

/** The supported concentric island footprints, measured in chunks per side. */
public enum IslandSize {
    SMALL(7),
    MEDIUM(13),
    LARGE(19);

    private final int chunks;

    IslandSize(int chunks) {
        this.chunks = chunks;
    }

    public int chunks() {
        return chunks;
    }

    public int diameterChunks() {
        return chunks;
    }

    public int radiusChunks() {
        return chunks / 2;
    }
}
