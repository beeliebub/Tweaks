package me.beeliebub.tweaks.tab;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.BalanceCommand;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.playeradmin.PlayerAdminManager;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import me.beeliebub.tweaks.ranks.RankManager;
import me.beeliebub.tweaks.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;
import java.util.OptionalDouble;

// Single source of truth for the tab-list display name and scoreboard team ordering.
// Owns all tab constants, world-tag mapping, team plumbing, and the refreshTab render logic.
// EconomyManager and PlayerAdminManager delegate back here whenever state that affects the
// tab display changes (balance mutations, AFK state).
public class TabManager implements Listener {

    // ------------------------------------------------------------
    // Tab-list constants (moved from PlayerAdminManager)
    // ------------------------------------------------------------
    private static final Component AFK_SUFFIX = Component.text(" [AFK]", NamedTextColor.RED);

    // ------------------------------------------------------------
    // Dependencies
    // ------------------------------------------------------------
    private final Scoreboard scoreboard;
    private final EconomyManager economyManager;
    private final RankManager rankManager;
    private final PlayerAdminManager playerAdminManager;
    private final WorldProfileTable worldProfileTable;
    private SkyblockBalanceProvider skyblockBalanceProvider;
    private String skyblockWorldKey;

    public TabManager(Tweaks plugin, EconomyManager economyManager, RankManager rankManager,
                      PlayerAdminManager playerAdminManager, WorldProfileTable worldProfileTable) {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        this.economyManager = economyManager;
        this.rankManager = rankManager;
        this.playerAdminManager = playerAdminManager;
        this.worldProfileTable = worldProfileTable;
    }

    public void setSkyblockBalanceProvider(String worldKey, SkyblockBalanceProvider provider) {
        this.skyblockWorldKey = worldKey;
        this.skyblockBalanceProvider = provider;
    }

    // ============================================================
    // Public API
    // ============================================================

    /**
     * Builds and applies the full tab-list display name for the player.
     * Format: [WorldPrefix][RankName] Name [$balance] [AFK]
     * Must be called on the main thread.
     */
    public void refreshTab(Player player) {
        UUID uuid = player.getUniqueId();
        String worldKey = player.getWorld().getKey().asString();
        Component tag = worldProfileTable.tagFor(worldKey);

        // World prefix (e.g. "[Survival] ").
        Component name = tag;

        // Rank bracket (e.g. "[I] ").
        int rank = economyManager.getRank(uuid);
        name = name
                .append(Component.text("[", NamedTextColor.GOLD))
                .append(rankManager.getRankDisplayComponent(rank))
                .append(Component.text("] ", NamedTextColor.GOLD));

        // Player name: nickname if set, otherwise plain name in world-prefix color.
        String rawNick = player.getPersistentDataContainer()
                .get(playerAdminManager.nickKey(), PersistentDataType.STRING);
        if (rawNick != null && !rawNick.isEmpty()) {
            name = name.append(ColorUtil.parse(rawNick));
        } else {
            TextColor nameColor = tag.color();
            if (nameColor == null) nameColor = NamedTextColor.WHITE;
            name = name.append(Component.text(player.getName(), nameColor));
        }

        // Balance (omitted when hidden).
        OptionalDouble skyblockBalance = skyblockWorldKey != null && skyblockWorldKey.equals(worldKey)
                && skyblockBalanceProvider != null ? skyblockBalanceProvider.balance(player) : OptionalDouble.empty();
        if (skyblockWorldKey != null && skyblockWorldKey.equals(worldKey) && skyblockBalanceProvider != null
                && skyblockBalance.isEmpty()) {
            player.playerListName(playerAdminManager.isAfk(player) ? name.append(AFK_SUFFIX) : name);
            return;
        }
        if (!economyManager.isBalanceHidden(uuid) || skyblockBalance.isPresent()) {
            double balance = skyblockBalance.orElseGet(() -> economyManager.getBalance(uuid));
            name = name.append(Component.text(
                    " " + BalanceCommand.formatBalance(balance), NamedTextColor.YELLOW));
        }

        // AFK suffix.
        if (playerAdminManager.isAfk(player)) {
            name = name.append(AFK_SUFFIX);
        }

        player.playerListName(name);
    }

    @FunctionalInterface
    public interface SkyblockBalanceProvider {
        OptionalDouble balance(Player player);
    }

    /**
     * Places the player into the correct scoreboard team for tab-list ordering.
     * Must be called on the main thread.
     */
    public void assignTeam(Player player) {
        String profile = worldProfileTable.profileFor(player.getWorld().getKey().asString());
        Team current = scoreboard.getPlayerTeam(player);
        if (current != null) {
            if (current.getName().equals(teamName(profile))) return;
            current.removePlayer(player);
            cleanupTeamIfEmpty(current);
        }
        player.setScoreboard(scoreboard);
        getOrCreateTeam(profile).addPlayer(player);
    }

    /**
     * Removes the player from their current scoreboard team and cleans up if empty.
     * Must be called on the main thread.
     */
    public void removeFromTeam(Player player) {
        Team team = scoreboard.getPlayerTeam(player);
        if (team != null) {
            team.removePlayer(player);
            cleanupTeamIfEmpty(team);
        }
    }

    // ============================================================
    // Team helpers (moved from PlayerAdminManager)
    // ============================================================

    private String teamName(String profile) {
        return "tab_" + worldProfileTable.sortKeyFor(profile);
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

    // ============================================================
    // Listeners
    // ============================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        assignTeam(player);
        refreshTab(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        assignTeam(player);
        refreshTab(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        removeFromTeam(event.getPlayer());
    }
}
