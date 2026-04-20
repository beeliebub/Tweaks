package me.beeliebub.tweaks.combos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;

// Sorts and labels players in the tab list by their current world.
// Uses scoreboard teams for ordering and colored prefixes for world identification.
public class TabManager implements Listener {

    // World namespace keys mapped to profile names
    private static final String ARCHIVE_WORLD_KEY = "jass:archive";
    private static final String LOBBY_WORLD_KEY   = "jass:lobby";
    private static final String PI_WORLD_KEY      = "jass:pi";

    private static final String PROFILE_LOBBY    = "lobby";
    private static final String PROFILE_STANDARD = "standard";
    private static final String PROFILE_ARCHIVE  = "archive";
    private static final String PROFILE_PI       = "pi";

    // Alphabetical sort keys determine tab list ordering (a = top, d = bottom)
    private static final Map<String, String> SORT_KEYS = Map.of(
            PROFILE_LOBBY,    "a",
            PROFILE_STANDARD, "b",
            PROFILE_ARCHIVE,  "c",
            PROFILE_PI,       "d"
    );

    // Colored prefix tags shown before player names in the tab list
    private static final Map<String, Component> PROFILE_TAGS = Map.of(
            PROFILE_LOBBY,    Component.text("[Lobby] ",    NamedTextColor.AQUA),
            PROFILE_STANDARD, Component.text("[Survival] ", NamedTextColor.GREEN),
            PROFILE_ARCHIVE,  Component.text("[Archive] ",  NamedTextColor.GOLD),
            PROFILE_PI,       Component.text("[Pi] ",       NamedTextColor.LIGHT_PURPLE)
    );

    private final Scoreboard scoreboard;

    public TabManager() {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private String getProfileForWorldKey(String worldKey) {
        if (worldKey.equalsIgnoreCase(ARCHIVE_WORLD_KEY)) return PROFILE_ARCHIVE;
        if (worldKey.equalsIgnoreCase(LOBBY_WORLD_KEY))   return PROFILE_LOBBY;
        if (worldKey.equalsIgnoreCase(PI_WORLD_KEY))      return PROFILE_PI;
        return PROFILE_STANDARD;
    }

    private String getProfile(Player player) {
        return getProfileForWorldKey(player.getWorld().getKey().asString());
    }

    private String teamName(String profile) {
        return "tab_" + SORT_KEYS.getOrDefault(profile, "b");
    }

    private Team getOrCreateTeam(String profile) {
        String name = teamName(profile);
        Team team = scoreboard.getTeam(name);

        if (team == null) {
            team = scoreboard.registerNewTeam(name);
            // Intentionally no prefix, suffix, or color.
        }

        return team;
    }

    private void cleanupTeamIfEmpty(Team team) {
        if (team != null && team.getEntries().isEmpty()) {
            team.unregister();
        }
    }

    private void assignTeam(Player player) {
        String profile = getProfile(player);

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
        String profile = getProfile(player);
        Component tag = PROFILE_TAGS.getOrDefault(profile,
                Component.text("[???] ", NamedTextColor.GRAY));

        player.playerListName(tag.append(Component.text(player.getName())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        assignTeam(player);
        refreshTabName(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeFromTeam(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        assignTeam(player);
        refreshTabName(player);
    }
}