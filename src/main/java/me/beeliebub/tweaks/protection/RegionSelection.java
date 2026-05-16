package me.beeliebub.tweaks.protection;

import org.bukkit.World;

// Per-player Pos1/Pos2 selection state used by the wand workflow.
//
// Positions are stored as packed chunk keys (Bukkit's Chunk.getChunkKey
// layout, via GeometryUtil) rather than block coordinates: the wand only
// accepts chunk-corner blocks, and every chunk corner identifies exactly
// one chunk, so the chunk key IS the position. Storing chunks (instead
// of blocks) makes the "whole-chunks only" invariant trivially true and
// removes any later need to renormalize block coords to chunk granularity.
//
// World is captured at construction. Selections are reset on world change
// so a player teleporting between worlds doesn't end up with mismatched
// Pos1/Pos2 anchors.
public final class RegionSelection {

    private final World world;
    private Long pos1;
    private Long pos2;

    public RegionSelection(World world) {
        this.world = world;
    }

    public World world() {
        return world;
    }

    public Long pos1() {
        return pos1;
    }

    public Long pos2() {
        return pos2;
    }

    public void setPos1(long chunkKey) {
        this.pos1 = chunkKey;
    }

    public void setPos2(long chunkKey) {
        this.pos2 = chunkKey;
    }

    public boolean hasPos1() {
        return pos1 != null;
    }

    public boolean hasPos2() {
        return pos2 != null;
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null;
    }
}
