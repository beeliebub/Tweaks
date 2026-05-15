package me.beeliebub.tweaks.protection;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Owns the per-player RegionSelection map and the particle-outline ticker.
//
// Storage: ConcurrentHashMap because the wand listener writes from the
// main thread but the quit-cleanup hook may be invoked from a slightly
// different lifecycle context; concurrent reads from the ticker thread are
// also covered. (The ticker runs on the main thread via runTaskTimer, but
// using CHM costs nothing and keeps the invariant uniform with the rest
// of ProtectionManager.)
//
// Outline rendering: every refreshTicks (5 ticks / 4Hz by default) we walk
// the perimeter of the selected chunk rectangle and emit DUST particles at
// 1-block intervals at the player's current Y. Particles use Player.spawn
// so only the selecting player can see them — perfect isolation, no packet
// hackery required.
public final class RegionSelectionManager implements Listener {

    private static final long REFRESH_TICKS = 5L;
    private static final double Y_OFFSET = 1.0; // particles slightly above the player's feet read better

    // DUST particles need a Particle.DustOptions(size, color). Picked sizes
    // and colors so a "Pos1 only" anchor reads as red (partial) and a full
    // rectangle reads as green (committable).
    private static final Particle.DustOptions PARTIAL = new Particle.DustOptions(Color.fromRGB(255, 80, 80), 1.0f);
    private static final Particle.DustOptions COMPLETE = new Particle.DustOptions(Color.fromRGB(80, 255, 80), 1.0f);

    private final Tweaks plugin;
    private final ConcurrentHashMap<UUID, RegionSelection> selections = new ConcurrentHashMap<>();
    private BukkitTask ticker;

    public RegionSelectionManager(Tweaks plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (ticker != null) return;
        ticker = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::renderAllOutlines, REFRESH_TICKS, REFRESH_TICKS);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    public RegionSelection get(UUID id) {
        return selections.get(id);
    }

    // Returns the player's selection in the given world, creating a fresh
    // one if absent or if the player switched worlds since the last set.
    public RegionSelection getOrCreate(Player player, World world) {
        RegionSelection existing = selections.get(player.getUniqueId());
        if (existing == null || existing.world() != world) {
            RegionSelection fresh = new RegionSelection(world);
            selections.put(player.getUniqueId(), fresh);
            return fresh;
        }
        return existing;
    }

    public void clear(UUID id) {
        selections.remove(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------
    // Outline rendering
    // ------------------------------------------------------------------

    private void renderAllOutlines() {
        for (var entry : selections.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            RegionSelection sel = entry.getValue();
            if (player.getWorld() != sel.world()) continue;
            renderOutline(player, sel);
        }
    }

    private void renderOutline(Player player, RegionSelection sel) {
        if (!sel.hasPos1()) return;

        int cx1 = GeometryUtil.chunkX(sel.pos1());
        int cz1 = GeometryUtil.chunkZ(sel.pos1());
        int cx2 = sel.hasPos2() ? GeometryUtil.chunkX(sel.pos2()) : cx1;
        int cz2 = sel.hasPos2() ? GeometryUtil.chunkZ(sel.pos2()) : cz1;

        int minCx = Math.min(cx1, cx2);
        int maxCx = Math.max(cx1, cx2);
        int minCz = Math.min(cz1, cz2);
        int maxCz = Math.max(cz1, cz2);

        // Inclusive block AABB: chunk (cx, cz) spans blocks [cx*16, cx*16+15].
        int minBx = minCx << 4;
        int maxBx = (maxCx << 4) + 15;
        int minBz = minCz << 4;
        int maxBz = (maxCz << 4) + 15;

        Particle.DustOptions style = sel.isComplete() ? COMPLETE : PARTIAL;
        double y = player.getLocation().getY() + Y_OFFSET;
        World world = sel.world();

        // North + south edges (constant Z)
        for (int x = minBx; x <= maxBx; x++) {
            player.spawnParticle(Particle.DUST, new Location(world, x + 0.5, y, minBz + 0.5), 1, 0, 0, 0, 0, style);
            player.spawnParticle(Particle.DUST, new Location(world, x + 0.5, y, maxBz + 0.5), 1, 0, 0, 0, 0, style);
        }
        // East + west edges (constant X) — skip the corner blocks already drawn above
        for (int z = minBz + 1; z <= maxBz - 1; z++) {
            player.spawnParticle(Particle.DUST, new Location(world, minBx + 0.5, y, z + 0.5), 1, 0, 0, 0, 0, style);
            player.spawnParticle(Particle.DUST, new Location(world, maxBx + 0.5, y, z + 0.5), 1, 0, 0, 0, 0, style);
        }
    }
}
