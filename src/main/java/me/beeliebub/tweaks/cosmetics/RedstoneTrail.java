package me.beeliebub.tweaks.cosmetics;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Cosmetic trail: players whose equipped boots carry the silence armor trim with the redstone
// trim material leave a redstone-dust trail at their feet while moving horizontally.
// Implemented as a periodic task rather than a PlayerMoveEvent listener: PlayerMoveEvent is one
// of the hottest events on the server, and this cosmetic's cadence (a few particles per second)
// does not need sub-tick precision.
public class RedstoneTrail implements Listener {

    // Period between trail samples (ticks). 3 ticks ≈ 6.6 spawns/s — dense enough for a visible
    // trail at sprint speed (~5.6 m/s ⇒ ~0.85 m between particles) without flooding packets.
    private static final long TICK_PERIOD = 3L;

    private static final Particle.DustOptions DUST =
            new Particle.DustOptions(Color.fromRGB(220, 30, 30), 1.0f);

    private final Tweaks plugin;
    private final TrimPattern silencePattern;
    private final TrimMaterial redstoneMaterial;
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private BukkitTask task;

    public RedstoneTrail(Tweaks plugin) {
        this.plugin = plugin;
        this.silencePattern = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.TRIM_PATTERN)
                .get(NamespacedKey.minecraft("silence"));
        this.redstoneMaterial = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.TRIM_MATERIAL)
                .get(NamespacedKey.minecraft("redstone"));
    }

    public void start() {
        if (task != null) return;
        if (silencePattern == null || redstoneMaterial == null) {
            plugin.getLogger().warning("RedstoneTrail disabled: silence trim pattern or redstone trim material missing from registry.");
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            lastLocations.put(online.getUniqueId(), online.getLocation().clone());
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location current = player.getLocation();
            Location previous = lastLocations.put(player.getUniqueId(), current.clone());
            if (previous == null) continue;
            if (previous.getWorld() == null || !previous.getWorld().equals(current.getWorld())) continue;

            // Horizontal movement only — pure vertical falls don't constitute walking/running.
            if (previous.getX() == current.getX() && previous.getZ() == current.getZ()) continue;

            if (!hasRedstoneSilenceBoots(player)) continue;
            spawnTrail(player, current);
        }
    }

    private boolean hasRedstoneSilenceBoots(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.isEmpty()) return false;
        if (!(boots.getItemMeta() instanceof ArmorMeta armorMeta)) return false;
        if (!armorMeta.hasTrim()) return false;
        ArmorTrim trim = armorMeta.getTrim();
        return silencePattern.equals(trim.getPattern())
                && redstoneMaterial.equals(trim.getMaterial());
    }

    private void spawnTrail(Player player, Location at) {
        Location spawn = at.clone().add(0.0, 0.1, 0.0);
        player.getWorld().spawnParticle(Particle.DUST, spawn, 4, 0.18, 0.05, 0.18, 0.0, DUST);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        lastLocations.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation().clone());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastLocations.remove(event.getPlayer().getUniqueId());
    }
}
