package me.beeliebub.tweaks.playeradmin;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

// Shared state, listeners, and periodic tasks for the player-administration commands:
// /afk, /fly, /nick (+ tab list ordering + boot trail cosmetics that live alongside it).
//
// Holds the durable PDC keys, the AFK location map, the nickname pending-removal queue,
// the scoreboard team plumbing, and the boot-trail tick loop.
public class PlayerAdminManager implements Listener {

    // ------------------------------------------------------------
    // AFK
    // ------------------------------------------------------------
    private static final double AFK_EXIT_DISTANCE_SQ = 1.0;
    private static final long AFK_AUTO_MILLIS = 10L * 60L * 1000L;
    private static final long AFK_CHECK_PERIOD_TICKS = 20L * 30L;

    private final Map<UUID, Location> afkLocations = new HashMap<>();
    private final Map<UUID, Long> lastMovementMs = new HashMap<>();
    private BukkitTask afkIdleCheckTask;

    // ------------------------------------------------------------
    // Tab list
    // ------------------------------------------------------------
    private static final Component AFK_SUFFIX = Component.text(" [AFK]", NamedTextColor.RED);
    private static final String ARCHIVE_WORLD_KEY = "jass:archive";
    private static final String LOBBY_WORLD_KEY   = "jass:lobby";
    private static final String PI_WORLD_KEY      = "jass:pi";

    private static final String PROFILE_LOBBY    = "lobby";
    private static final String PROFILE_STANDARD = "standard";
    private static final String PROFILE_ARCHIVE  = "archive";
    private static final String PROFILE_PI       = "pi";

    private static final Map<String, String> TAB_SORT_KEYS = Map.of(
            PROFILE_LOBBY,    "a",
            PROFILE_STANDARD, "b",
            PROFILE_ARCHIVE,  "c",
            PROFILE_PI,       "d"
    );

    private static final Component FALLBACK_TAG = Component.text("[Survival] ", NamedTextColor.GREEN);
    private static final Map<String, Component> WORLD_TAGS = Map.of(
            LOBBY_WORLD_KEY,        Component.text("[Lobby] ",    NamedTextColor.AQUA),
            ARCHIVE_WORLD_KEY,      Component.text("[Archive] ",  NamedTextColor.GOLD),
            PI_WORLD_KEY,           Component.text("[Pi] ",       NamedTextColor.LIGHT_PURPLE),
            "minecraft:overworld",  Component.text("[Survival] ", NamedTextColor.GREEN),
            "minecraft:the_nether", Component.text("[Nether] ",   NamedTextColor.LIGHT_PURPLE),
            "minecraft:the_end",    Component.text("[End] ",      NamedTextColor.DARK_PURPLE),
            "jass:resource",        Component.text("[Resource] ", NamedTextColor.AQUA),
            "jass:resource_nether", Component.text("[Resource] ", NamedTextColor.AQUA)
    );

    private final Scoreboard scoreboard;

    // ------------------------------------------------------------
    // Fly
    // ------------------------------------------------------------
    private final NamespacedKey flyAdvancementKey; // nullable when config string is invalid
    private final NamespacedKey flyKey;
    private final Set<String> defaultFlyWorlds = new HashSet<>();

    // ------------------------------------------------------------
    // Nick
    // ------------------------------------------------------------
    private static final LegacyComponentSerializer NICK_COLOR_SERIALIZER =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final int MAX_NICK_LENGTH = 24;
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
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        this.flyKey = new NamespacedKey(plugin, "fly_enabled");
        this.nickKey = new NamespacedKey(plugin, "nickname");
        this.nickPendingFile = new File(plugin.getDataFolder(), "nick-removals.yml");

        String advancementName = plugin.getConfig().getString("fly-advancement", "jass:test");
        this.flyAdvancementKey = NamespacedKey.fromString(advancementName);

        for (String world : plugin.getConfig().getStringList("fly-worlds")) {
            defaultFlyWorlds.add(world.toLowerCase());
        }

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

    void enterAfk(Player player) {
        afkLocations.put(player.getUniqueId(), player.getLocation().clone());
        player.setSleepingIgnored(true);
        refreshTabName(player);
        player.sendMessage(Component.text("You are now AFK.", NamedTextColor.GRAY));
    }

    void exitAfk(Player player, boolean announce) {
        if (afkLocations.remove(player.getUniqueId()) == null) return;
        player.setSleepingIgnored(false);
        refreshTabName(player);
        lastMovementMs.put(player.getUniqueId(), System.currentTimeMillis());
        if (announce) {
            player.sendMessage(Component.text("You are no longer AFK.", NamedTextColor.GRAY));
        }
    }

    private void checkAfkIdle() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAfk(player)) continue;
            Long last = lastMovementMs.get(player.getUniqueId());
            if (last == null) continue;
            if (now - last >= AFK_AUTO_MILLIS) {
                enterAfk(player);
            }
        }
    }

    // ============================================================
    // Tab list
    // ============================================================

    private String getProfileForWorldKey(String worldKey) {
        if (worldKey.equalsIgnoreCase(ARCHIVE_WORLD_KEY)) return PROFILE_ARCHIVE;
        if (worldKey.equalsIgnoreCase(LOBBY_WORLD_KEY))   return PROFILE_LOBBY;
        if (worldKey.equalsIgnoreCase(PI_WORLD_KEY))      return PROFILE_PI;
        return PROFILE_STANDARD;
    }

    private String teamName(String profile) {
        return "tab_" + TAB_SORT_KEYS.getOrDefault(profile, "b");
    }

    private Team getOrCreateTeam(String profile) {
        String name = teamName(profile);
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        return team;
    }

    private void cleanupTeamIfEmpty(Team team) {
        if (team != null && team.getEntries().isEmpty()) {
            team.unregister();
        }
    }

    private void assignTeam(Player player) {
        String profile = getProfileForWorldKey(player.getWorld().getKey().asString());
        Team current = scoreboard.getPlayerTeam(player);
        if (current != null) {
            if (current.getName().equals(teamName(profile))) return;
            current.removePlayer(player);
            cleanupTeamIfEmpty(current);
        }
        player.setScoreboard(scoreboard);
        getOrCreateTeam(profile).addPlayer(player);
    }

    private void removeFromTeam(Player player) {
        Team team = scoreboard.getPlayerTeam(player);
        if (team != null) {
            team.removePlayer(player);
            cleanupTeamIfEmpty(team);
        }
    }

    public void refreshTabName(Player player) {
        String worldKey = player.getWorld().getKey().asString();
        Component tag = WORLD_TAGS.getOrDefault(worldKey, FALLBACK_TAG);
        Component name = tag.append(Component.text(player.getName()));
        if (isAfk(player)) {
            name = name.append(AFK_SUFFIX);
        }
        player.playerListName(name);
    }

    // ============================================================
    // Fly
    // ============================================================

    private boolean isDefaultFlyWorld(String worldKey) {
        return defaultFlyWorlds.contains(worldKey.toLowerCase());
    }

    private boolean hasFlyAdvancement(Player player) {
        if (flyAdvancementKey == null) return false;
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
    int maxNickLength() { return MAX_NICK_LENGTH; }
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
                plugin.getLogger().severe("Failed to save nick-removals.yml: " + e.getMessage());
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
            Location current = player.getLocation();
            Location previous = trailLastLocations.put(player.getUniqueId(), current.clone());
            if (previous == null) continue;
            if (previous.getWorld() == null || !previous.getWorld().equals(current.getWorld())) continue;
            // Horizontal movement only.
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

    // ============================================================
    // Listeners
    // ============================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        lastMovementMs.put(uuid, System.currentTimeMillis());
        trailLastLocations.put(uuid, player.getLocation().clone());

        // Tab list assignment
        assignTeam(player);
        refreshTabName(player);

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
        removeFromTeam(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // Tab list: reassign team and refresh display tag.
        assignTeam(player);
        refreshTabName(player);

        // Fly: revoke if the destination world does not grant access.
        if (!player.getAllowFlight()) return;
        String toWorldKey = player.getWorld().getKey().asString();
        if (isDefaultFlyWorld(toWorldKey)) return;
        if (hasFlyAdvancement(player)) return;
        disableFlight(player);
        player.sendMessage(Component.text("Flight disabled — you don't have access in this world.")
                .color(NamedTextColor.RED));
    }
}
