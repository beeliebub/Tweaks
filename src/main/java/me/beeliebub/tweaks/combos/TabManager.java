package me.beeliebub.tweaks.combos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

public class TabManager implements Listener {

    private static final String ARCHIVE_WORLD_KEY = "jass:archive";
    private static final String LOBBY_WORLD_KEY = "jass:lobby";

    private static final String PROFILE_ARCHIVE = "archive";
    private static final String PROFILE_LOBBY = "lobby";
    private static final String PROFILE_STANDARD = "standard";

    private static final Map<String, String> TEAM_NAMES = Map.of(
            PROFILE_LOBBY, "a_lobby",
            PROFILE_STANDARD, "b_standard",
            PROFILE_ARCHIVE, "c_archive"
    );

    private static final Map<String, Component> PROFILE_PREFIXES = Map.of(
            PROFILE_LOBBY, Component.text("Lobby ", NamedTextColor.AQUA, TextDecoration.BOLD),
            PROFILE_STANDARD, Component.text("Survival ", NamedTextColor.GREEN, TextDecoration.BOLD),
            PROFILE_ARCHIVE, Component.text("Archive ", NamedTextColor.GOLD, TextDecoration.BOLD)
    );

    private static final Map<String, NamedTextColor> PROFILE_COLORS = Map.of(
            PROFILE_LOBBY, NamedTextColor.AQUA,
            PROFILE_STANDARD, NamedTextColor.GREEN,
            PROFILE_ARCHIVE, NamedTextColor.GOLD
    );

    private final Scoreboard scoreboard;

    public TabManager() {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private String getProfileForWorldKey(String worldKey) {
        if (worldKey.equalsIgnoreCase(ARCHIVE_WORLD_KEY)) return PROFILE_ARCHIVE;
        if (worldKey.equalsIgnoreCase(LOBBY_WORLD_KEY)) return PROFILE_LOBBY;
        return PROFILE_STANDARD;
    }

    private Team getOrCreateTeam(String profile) {
        String teamName = TEAM_NAMES.getOrDefault(profile, "b_standard");
        Team team = scoreboard.getTeam(teamName);

        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.prefix(PROFILE_PREFIXES.getOrDefault(profile,
                    Component.text("Unknown ", NamedTextColor.GRAY)));
            team.color(PROFILE_COLORS.getOrDefault(profile, NamedTextColor.WHITE));
        }

        return team;
    }

    private void cleanupTeamIfEmpty(Team team) {
        if (team != null && team.getEntries().isEmpty()) {
            team.unregister();
        }
    }

    public void assignPlayer(Player player) {
        String worldKey = player.getWorld().getKey().asString();
        String profile = getProfileForWorldKey(worldKey);

        // Remove from any existing team first
        Team currentTeam = scoreboard.getPlayerTeam(player);
        if (currentTeam != null) {
            String currentProfile = resolveProfileFromTeam(currentTeam);
            if (profile.equals(currentProfile)) return; // already correct
            currentTeam.removePlayer(player);
            cleanupTeamIfEmpty(currentTeam);
        }

        // Ensure the player is using the main scoreboard
        player.setScoreboard(scoreboard);

        Team newTeam = getOrCreateTeam(profile);
        newTeam.addPlayer(player);
    }


    public void removePlayer(Player player) {
        Team team = scoreboard.getPlayerTeam(player);
        if (team != null) {
            team.removePlayer(player);
            cleanupTeamIfEmpty(team);
        }
    }


    private String resolveProfileFromTeam(Team team) {
        for (Map.Entry<String, String> entry : TEAM_NAMES.entrySet()) {
            if (entry.getValue().equals(team.getName())) return entry.getKey();
        }
        return PROFILE_STANDARD;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        assignPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        assignPlayer(event.getPlayer());
    }
}