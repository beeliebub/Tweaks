package me.beeliebub.tweaks.playeradmin;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tab.TabManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;

// Shared state, listeners, and periodic tasks for the player-administration commands:
// /afk, /fly, /nick (+ boot trail cosmetics that live alongside it).
//
// Holds the durable PDC keys, the AFK location map, the nickname pending-removal queue,
// and the boot-trail tick loop. Tab-list rendering is delegated to TabManager.
public class PlayerAdminManager implements Listener {

    // ------------------------------------------------------------
    // AFK
    // ------------------------------------------------------------
    private static final double AFK_EXIT_DISTANCE_SQ = 1.0;
    private static final long AFK_CHECK_PERIOD_TICKS = 20L * 30L;

    private final Map<UUID, Location> afkLocations = new HashMap<>();
    private final Map<UUID, Long> lastMovementMs = new HashMap<>();
    private BukkitTask afkIdleCheckTask;

    // ------------------------------------------------------------
    // Tab list (delegated to TabManager; set after construction)
    // ------------------------------------------------------------
    private TabManager tabManager;

    // ------------------------------------------------------------
    // Fly
    // ------------------------------------------------------------
    private final NamespacedKey flyKey;
    private final java.util.Map<java.util.UUID, Boolean> flyingStates = new java.util.concurrent.ConcurrentHashMap<>();
    // Dedupes the invalid-fly-advancement warning below so a persistently bad config value logs
    // once per distinct bad string instead of once per canFly() check.
    private String lastWarnedInvalidFlyAdvancement;

    // ------------------------------------------------------------
    // Nick
    // ------------------------------------------------------------
    private static final LegacyComponentSerializer NICK_COLOR_SERIALIZER =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private final NamespacedKey nickKey;
    private final Set<UUID> nickPendingRemovals = ConcurrentHashMap.newKeySet();
    private final File nickPendingFile;

    // ------------------------------------------------------------
    // BootTrail
    // ------------------------------------------------------------
    private static final long TRAIL_TICK_PERIOD = 3L;
    private final Map<UUID, Location> trailLastLocations = new HashMap<>();
    private final Map<TrimMaterial, BiConsumer<Player, Location>> trailEffects = new HashMap<>();
    private BukkitTask trailTask;

    // ------------------------------------------------------------
    // Plugin handle
    // ------------------------------------------------------------
    private final Tweaks plugin;

    public PlayerAdminManager(Tweaks plugin) {
        this.plugin = plugin;
        this.flyKey = new NamespacedKey(plugin, "fly_enabled");
        this.nickKey = new NamespacedKey(plugin, "nickname");
        this.nickPendingFile = new File(plugin.getDataFolder(), "nick-removals.yml");

        loadNickPendingRemovals();
        initTrailEffects();
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    public void start() {
        long now = System.currentTimeMillis();
        for (Player online : Bukkit.getOnlinePlayers()) {
            lastMovementMs.put(online.getUniqueId(), now);
            trailLastLocations.put(online.getUniqueId(), online.getLocation().clone());
        }
        if (afkIdleCheckTask == null) {
            afkIdleCheckTask = Bukkit.getScheduler().runTaskTimer(
                    plugin, this::checkAfkIdle, AFK_CHECK_PERIOD_TICKS, AFK_CHECK_PERIOD_TICKS);
        }
        if (trailTask == null) {
            trailTask = Bukkit.getScheduler().runTaskTimer(
                    plugin, this::trailTick, TRAIL_TICK_PERIOD, TRAIL_TICK_PERIOD);
        }
    }

    // ============================================================
    // AFK
    // ============================================================

    public boolean isAfk(Player player) {
        return afkLocations.containsKey(player.getUniqueId());
    }

    public void setTabManager(TabManager tabManager) {
        this.tabManager = tabManager;
    }

    void enterAfk(Player player) {
        afkLocations.put(player.getUniqueId(), player.getLocation().clone());
        player.setSleepingIgnored(true);
        if (tabManager != null) tabManager.refreshTab(player);
        player.sendMessage(Component.text("You are now AFK.", NamedTextColor.GRAY));
    }

    void exitAfk(Player player, boolean announce) {
        if (afkLocations.remove(player.getUniqueId()) == null) return;
        player.setSleepingIgnored(false);
        if (tabManager != null) tabManager.refreshTab(player);
        lastMovementMs.put(player.getUniqueId(), System.currentTimeMillis());
        if (announce) {
            player.sendMessage(Component.text("You are no longer AFK.", NamedTextColor.GRAY));
        }
    }

    private void checkAfkIdle() {
        long now = System.currentTimeMillis();
        long afkAutoMillis = afkAutoMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (isAfk(player)) continue;
                Long last = lastMovementMs.get(player.getUniqueId());
                if (last == null) continue;
                if (now - last >= afkAutoMillis) {
                    enterAfk(player);
                }
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING,
                        "AFK idle check failed for " + player.getUniqueId(), error);
            }
        }
    }

    // Live read (no constructor-time caching) so a /tconfig edit to playeradmin.afk-auto-minutes
    // takes effect on the next AFK_CHECK_PERIOD_TICKS sweep - matches fly-worlds' pattern below.
    // core/config/ConfigRegistry enforces a minimum of 1 minute (the check period's own 30-second
    // granularity means anything below 1 minute couldn't be honored precisely anyway).
    private long afkAutoMillis() {
        int minutes = plugin.getConfig().getInt("playeradmin.afk-auto-minutes", 10);
        if (minutes < 1) minutes = 10;
        return minutes * 60L * 1000L;
    }

    // ============================================================
    // Fly
    // ============================================================

    // Live read (no constructor-time caching) so a /tconfig edit to fly-worlds takes effect
    // immediately - matches WorldRuleListener's spawner-egg-list pattern.
    private boolean isDefaultFlyWorld(String worldKey) {
        for (String world : plugin.getConfig().getStringList("fly-worlds")) {
            if (world.equalsIgnoreCase(worldKey)) return true;
        }
        return false;
    }

    private boolean hasFlyAdvancement(Player player) {
        String advancementName = plugin.getConfig().getString("fly-advancement", "jass:test");
        NamespacedKey flyAdvancementKey = NamespacedKey.fromString(advancementName);
        if (flyAdvancementKey == null) {
            if (!java.util.Objects.equals(advancementName, lastWarnedInvalidFlyAdvancement)) {
                plugin.getLogger().warning("fly-advancement '" + advancementName + "' is not a valid namespaced key; flight-by-advancement is disabled until it's corrected.");
                lastWarnedInvalidFlyAdvancement = advancementName;
            }
            return false;
        }
        Advancement advancement = Bukkit.getAdvancement(flyAdvancementKey);
        if (advancement == null) return false;
        return player.getAdvancementProgress(advancement).isDone();
    }

    boolean canFly(Player player) {
        String worldKey = player.getWorld().getKey().asString();
        if (isDefaultFlyWorld(worldKey)) return true;
        return hasFlyAdvancement(player);
    }

    void enableFlight(Player player) {
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getPersistentDataContainer().set(flyKey, PersistentDataType.BOOLEAN, true);
    }

    void disableFlight(Player player) {
        player.setFlying(false);
        player.setAllowFlight(false);
        player.getPersistentDataContainer().set(flyKey, PersistentDataType.BOOLEAN, false);
    }

    // ============================================================
    // Nick
    // ============================================================

    public NamespacedKey nickKey() { return nickKey; }

    // Live read (no constructor-time caching) so a /tconfig edit to playeradmin.max-nick-length
    // takes effect on the next /nick call. Only gates new /nick calls - an existing nickname
    // stored in a player's PDC is never retroactively re-validated or truncated.
    int maxNickLength() { return plugin.getConfig().getInt("playeradmin.max-nick-length", 24); }
    LegacyComponentSerializer nickColorSerializer() { return NICK_COLOR_SERIALIZER; }

    static String stripNickColorCodes(String input) {
        String stripped = input.replaceAll("&(?i)#[0-9a-f]{6}", "");
        stripped = stripped.replaceAll("&[0-9a-fA-Fk-oK-OrR]", "");
        return stripped;
    }

    void clearNickname(Player player) {
        player.getPersistentDataContainer().remove(nickKey);
        player.displayName(null);
    }

    void queueOfflineNickRemoval(UUID uuid) {
        nickPendingRemovals.add(uuid);
        saveNickPendingRemovalsAsync();
    }

    private void loadNickPendingRemovals() {
        if (!nickPendingFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(nickPendingFile);
        List<String> uuids = config.getStringList("pending");
        for (String raw : uuids) {
            try {
                nickPendingRemovals.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in nick-removals.yml: " + raw);
            }
        }
    }

    private void saveNickPendingRemovalsAsync() {
        List<String> snapshot = nickPendingRemovals.stream().map(UUID::toString).toList();
        CompletableFuture.runAsync(() -> {
            YamlConfiguration config = new YamlConfiguration();
            config.set("pending", snapshot);
            try {
                config.save(nickPendingFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save nick-removals.yml", e);
            }
        });
    }

    // ============================================================
    // Boot Trail
    // ============================================================

    private void initTrailEffects() {
        RegistryAccess access = RegistryAccess.registryAccess();
        var materialRegistry = access.getRegistry(RegistryKey.TRIM_MATERIAL);

        addTrailEffect(materialRegistry, "redstone", (player, loc) -> {
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(220, 30, 30), 1.0f);
            player.getWorld().spawnParticle(Particle.DUST, loc, 4, 0.18, 0.05, 0.18, 0.0, dust);
        });
        addTrailEffect(materialRegistry, "amethyst", (player, loc) ->
                player.getWorld().spawnParticle(Particle.PORTAL, loc, 6, 0.18, 0.05, 0.18, 0.05));
        addTrailEffect(materialRegistry, "copper", (player, loc) ->
                player.getWorld().spawnParticle(Particle.WAX_ON, loc, 4, 0.18, 0.05, 0.18, 0.05));
        addTrailEffect(materialRegistry, "diamond", (player, loc) ->
                player.getWorld().spawnParticle(Particle.GLOW, loc, 4, 0.18, 0.05, 0.18, 0.05));
        addTrailEffect(materialRegistry, "emerald", (player, loc) ->
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 3, 0.2, 0.1, 0.2, 0.0));
        addTrailEffect(materialRegistry, "gold", (player, loc) ->
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 2, 0.18, 0.1, 0.18, 0.0));
        addTrailEffect(materialRegistry, "iron", (player, loc) ->
                player.getWorld().spawnParticle(Particle.LAVA, loc, 2, 0.15, 0.05, 0.15, 0.0));
        addTrailEffect(materialRegistry, "lapis", (player, loc) ->
                player.getWorld().spawnParticle(Particle.ENCHANT, loc, 6, 0.2, 0.1, 0.2, 0.1));
        addTrailEffect(materialRegistry, "netherite", (player, loc) -> {
            World world = player.getWorld();
            world.spawnParticle(Particle.SMOKE, loc, 3, 0.15, 0.1, 0.15, 0.02);
            world.spawnParticle(Particle.LAVA, loc, 1, 0.15, 0.05, 0.15, 0.0);
            world.spawnParticle(Particle.DUST, loc, 2, 0.18, 0.1, 0.18, 0.0, new Particle.DustOptions(Color.RED, 0.8f));
            world.spawnParticle(Particle.DUST, loc, 2, 0.18, 0.1, 0.18, 0.0, new Particle.DustOptions(Color.YELLOW, 0.8f));
            world.spawnParticle(Particle.DUST, loc, 2, 0.18, 0.1, 0.18, 0.0, new Particle.DustOptions(Color.ORANGE, 0.8f));
        });
        addTrailEffect(materialRegistry, "quartz", (player, loc) ->
                player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 3, 0.15, 0.05, 0.15, 0.02));
        addTrailEffect(materialRegistry, "resin", (player, loc) ->
                player.getWorld().spawnParticle(Particle.LANDING_HONEY, loc, 5, 0.18, 0.05, 0.18, 0.02));
    }

    private void addTrailEffect(Registry<TrimMaterial> registry, String key,
                                BiConsumer<Player, Location> effect) {
        TrimMaterial mat = registry.get(NamespacedKey.minecraft(key));
        if (mat != null) {
            trailEffects.put(mat, effect);
        } else {
            plugin.getLogger().warning("Trim material 'minecraft:" + key + "' not found in registry.");
        }
    }

    private void trailTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Location current = player.getLocation();
                Location previous = trailLastLocations.put(player.getUniqueId(), current.clone());
                if (previous == null) continue;
                if (previous.getWorld() == null || !previous.getWorld().equals(current.getWorld())) continue;
                // Horizontal movement only.
                if (previous.getX() == current.getX() && previous.getZ() == current.getZ()) continue;
                handleTrail(player, current);
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING,
                        "Boot-trail tick failed for " + player.getUniqueId(), error);
            }
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

    // ============================================================
    // Listeners
    // ============================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        lastMovementMs.put(uuid, System.currentTimeMillis());
        trailLastLocations.put(uuid, player.getLocation().clone());

        // Fly restoration
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Boolean stored = pdc.get(flyKey, PersistentDataType.BOOLEAN);
        if (stored != null && stored) {
            if (canFly(player)) enableFlight(player); else disableFlight(player);
        }

        // Nick: pending offline removal, otherwise restore stored display name.
        if (nickPendingRemovals.remove(uuid)) {
            pdc.remove(nickKey);
            player.displayName(null);
            saveNickPendingRemovalsAsync();
        } else {
            String rawNick = pdc.get(nickKey, PersistentDataType.STRING);
            if (rawNick != null && !rawNick.isEmpty()) {
                player.displayName(NICK_COLOR_SERIALIZER.deserialize(rawNick));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        boolean moved = to.getX() != from.getX()
                || to.getY() != from.getY()
                || to.getZ() != from.getZ();
        if (moved) {
            lastMovementMs.put(player.getUniqueId(), System.currentTimeMillis());
        }

        Location origin = afkLocations.get(player.getUniqueId());
        if (origin == null) return;
        if (to.getWorld() == null || !to.getWorld().equals(origin.getWorld())) {
            exitAfk(player, true);
            return;
        }
        if (to.distanceSquared(origin) >= AFK_EXIT_DISTANCE_SQ) {
            exitAfk(player, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        lastMovementMs.put(player.getUniqueId(), System.currentTimeMillis());

        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            flyingStates.put(player.getUniqueId(), player.isFlying());
        }

        Location origin = afkLocations.get(player.getUniqueId());
        if (origin == null) return;
        Location to = event.getTo();
        if (to == null) return;
        if (to.getWorld() == null || !to.getWorld().equals(origin.getWorld())
                || to.distanceSquared(origin) >= AFK_EXIT_DISTANCE_SQ) {
            exitAfk(player, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        lastMovementMs.remove(uuid);
        trailLastLocations.remove(uuid);
        if (afkLocations.remove(uuid) != null) {
            player.setSleepingIgnored(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // Fly: restore or revoke based on PDC preference and eligibility.
        Boolean wasFlying = flyingStates.remove(player.getUniqueId());
        Boolean stored = player.getPersistentDataContainer().get(flyKey, PersistentDataType.BOOLEAN);
        boolean wantsFlight = stored != null && stored;

        if (!wantsFlight) {
            disableFlight(player);
            return;
        }
        if (canFly(player)) {
            player.setAllowFlight(true);
            if (wasFlying != null && wasFlying) {
                player.setFlying(true);
            }
        } else {
            disableFlight(player);
            player.sendMessage(Component.text("Flight disabled — you don't have access in this world.")
                    .color(NamedTextColor.RED));
        }
    }
}
