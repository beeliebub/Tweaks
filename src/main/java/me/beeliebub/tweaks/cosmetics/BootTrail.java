package me.beeliebub.tweaks.cosmetics;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

// Cosmetic trails: players whose equipped boots carry any armor trim with specific materials
// leave a particle trail at their feet while moving horizontally.
public class BootTrail implements Listener {

    private static final long TICK_PERIOD = 3L;

    private final Tweaks plugin;
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<TrimMaterial, BiConsumer<Player, Location>> trailEffects = new HashMap<>();
    private BukkitTask task;

    public BootTrail(Tweaks plugin) {
        this.plugin = plugin;
        initEffects();
    }

    private void initEffects() {
        RegistryAccess access = RegistryAccess.registryAccess();
        var materialRegistry = access.getRegistry(RegistryKey.TRIM_MATERIAL);

        // Redstone -> Dust (Red)
        addEffect(materialRegistry, "redstone", (player, loc) -> {
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(220, 30, 30), 1.0f);
            player.getWorld().spawnParticle(Particle.DUST, loc, 4, 0.18, 0.05, 0.18, 0.0, dust);
        });

        // Amethyst -> Portal
        addEffect(materialRegistry, "amethyst", (player, loc) -> {
            player.getWorld().spawnParticle(Particle.PORTAL, loc, 6, 0.18, 0.05, 0.18, 0.05);
        });

        // Copper -> Wax On
        addEffect(materialRegistry, "copper", (player, loc) -> {
            player.getWorld().spawnParticle(Particle.WAX_ON, loc, 4, 0.18, 0.05, 0.18, 0.05);
        });

        // Diamond -> Glow Squid Ink
        addEffect(materialRegistry, "diamond", (player, loc) -> {
            player.getWorld().spawnParticle(Particle.GLOW_SQUID_INK, loc, 4, 0.18, 0.05, 0.18, 0.05);
        });

        // Emerald -> Happy Villager
        addEffect(materialRegistry, "emerald", (player, loc) -> {
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 3, 0.2, 0.1, 0.2, 0.0);
        });

        // Gold -> Goldheart_0 (fallback to Heart if unavailable)
        Particle goldParticle = resolveParticle("GOLDHEART_0", Particle.HEART);
        addEffect(materialRegistry, "gold", (player, loc) -> {
            player.getWorld().spawnParticle(goldParticle, loc, 2, 0.18, 0.1, 0.18, 0.0);
        });

        // Iron -> Lava
        addEffect(materialRegistry, "iron", (player, loc) -> {
            player.getWorld().spawnParticle(Particle.LAVA, loc, 2, 0.15, 0.05, 0.15, 0.0);
        });

        // Lapis -> Enchant Table
        addEffect(materialRegistry, "lapis", (player, loc) -> {
            player.getWorld().spawnParticle(Particle.ENCHANT, loc, 6, 0.2, 0.1, 0.2, 0.1);
        });

        // Netherite -> Smoke, Lava, Red/Yellow/Orange Dust
        addEffect(materialRegistry, "netherite", (player, loc) -> {
            World world = player.getWorld();
            world.spawnParticle(Particle.SMOKE, loc, 3, 0.15, 0.1, 0.15, 0.02);
            world.spawnParticle(Particle.LAVA, loc, 1, 0.15, 0.05, 0.15, 0.0);
            world.spawnParticle(Particle.DUST, loc, 2, 0.18, 0.1, 0.18, 0.0, new Particle.DustOptions(Color.RED, 0.8f));
            world.spawnParticle(Particle.DUST, loc, 2, 0.18, 0.1, 0.18, 0.0, new Particle.DustOptions(Color.YELLOW, 0.8f));
            world.spawnParticle(Particle.DUST, loc, 2, 0.18, 0.1, 0.18, 0.0, new Particle.DustOptions(Color.ORANGE, 0.8f));
        });

        // Quartz -> Soul Fire Flame
        addEffect(materialRegistry, "quartz", (player, loc) -> {
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 3, 0.15, 0.05, 0.15, 0.02);
        });

        // Resin -> Block Slide (Honey)
        addEffect(materialRegistry, "resin", (player, loc) -> {
            player.getWorld().spawnParticle(Particle.LANDING_HONEY, loc, 5, 0.18, 0.05, 0.18, 0.02);
        });
    }

    private void addEffect(org.bukkit.Registry<TrimMaterial> registry, String key, BiConsumer<Player, Location> effect) {
        TrimMaterial mat = registry.get(NamespacedKey.minecraft(key));
        if (mat != null) {
            trailEffects.put(mat, effect);
        } else {
            plugin.getLogger().warning("Trim material 'minecraft:" + key + "' not found in registry.");
        }
    }

    private Particle resolveParticle(String name, Particle fallback) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public void start() {
        if (task != null) return;
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

            // Horizontal movement only
            if (previous.getX() == current.getX() && previous.getZ() == current.getZ()) continue;

            handleTrail(player, current);
        }
    }

    private void handleTrail(Player player, Location at) {
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.isEmpty()) return;
        if (!(boots.getItemMeta() instanceof ArmorMeta armorMeta)) return;
        if (!armorMeta.hasTrim()) return;

        TrimMaterial material = armorMeta.getTrim().getMaterial();
        BiConsumer<Player, Location> effect = trailEffects.get(material);
        if (effect != null) {
            effect.accept(player, at.clone().add(0.0, 0.1, 0.0));
        }
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
