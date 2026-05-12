package me.beeliebub.tweaks.tests.combos;

import me.beeliebub.tweaks.combos.TabManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

class TabManagerTest {

    private ServerMock server;
    private TabManager tabManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        tabManager = new TabManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock playerInWorldKeyed(String namespace, String key, String name) {
        WorldMock world = new WorldMock(Material.GRASS_BLOCK, 0) {
            @Override
            public NamespacedKey getKey() {
                return new NamespacedKey(namespace, key);
            }
        };
        server.addWorld(world);
        PlayerMock player = server.addPlayer(name);
        player.teleport(world.getSpawnLocation());
        return player;
    }

    @Test
    void joiningPlayerIsAssignedToStandardTabTeamForOverworld() {
        PlayerMock player = playerInWorldKeyed("minecraft", "overworld", "Bee");
        tabManager.onPlayerJoin(new org.bukkit.event.player.PlayerJoinEvent(player, ""));

        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
        assertNotNull(team, "join handler must place the player on a tab team");
        assertEquals("tab_b", team.getName(), "overworld → standard profile → team tab_b");
    }

    @Test
    void lobbyWorldGetsLobbyTeamPrefixSorting() {
        PlayerMock player = playerInWorldKeyed("jass", "lobby", "Bee");
        tabManager.onPlayerJoin(new org.bukkit.event.player.PlayerJoinEvent(player, ""));
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
        assertEquals("tab_a", team.getName(), "lobby world maps to team tab_a (top of list)");
    }

    @Test
    void archiveWorldGetsArchiveTeam() {
        PlayerMock player = playerInWorldKeyed("jass", "archive", "Bee");
        tabManager.onPlayerJoin(new org.bukkit.event.player.PlayerJoinEvent(player, ""));
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
        assertEquals("tab_c", team.getName());
    }

    @Test
    void piWorldGetsPiTeam() {
        PlayerMock player = playerInWorldKeyed("jass", "pi", "Bee");
        tabManager.onPlayerJoin(new org.bukkit.event.player.PlayerJoinEvent(player, ""));
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
        assertEquals("tab_d", team.getName());
    }

    @Test
    void unknownWorldFallsBackToStandardTeam() {
        PlayerMock player = playerInWorldKeyed("custom", "spaghetti", "Bee");
        tabManager.onPlayerJoin(new org.bukkit.event.player.PlayerJoinEvent(player, ""));
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
        assertEquals("tab_b", team.getName(), "unknown worlds default to the standard profile");
    }

    @Test
    void quitHandlerRemovesPlayerFromTeamAndUnregistersEmptyTeam() {
        PlayerMock player = playerInWorldKeyed("minecraft", "overworld", "Bee");
        tabManager.onPlayerJoin(new org.bukkit.event.player.PlayerJoinEvent(player, ""));
        assertNotNull(Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player));

        tabManager.onPlayerQuit(new org.bukkit.event.player.PlayerQuitEvent(player, (net.kyori.adventure.text.Component) null));
        assertNull(Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player));
        // Team itself should be cleaned up since it's now empty.
        assertNull(Bukkit.getScoreboardManager().getMainScoreboard().getTeam("tab_b"));
    }

    @Test
    void refreshTabNamePrefixesPlayerListName() {
        PlayerMock player = playerInWorldKeyed("minecraft", "the_nether", "Bee");
        tabManager.refreshTabName(player);
        net.kyori.adventure.text.Component name = player.playerListName();
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(name);
        assertTrue(plain.startsWith("[Nether]"), "expected nether prefix in tab name, got: " + plain);
        assertTrue(plain.contains("Bee"));
    }

    @Test
    void refreshTabNameAppendsAfkSuffixWhenPredicateTrips() {
        PlayerMock player = playerInWorldKeyed("minecraft", "overworld", "Bee");
        tabManager.setAfkPredicate(p -> true);
        tabManager.refreshTabName(player);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(player.playerListName());
        assertTrue(plain.endsWith("[AFK]"), "expected [AFK] suffix, got: " + plain);
    }

    @Test
    void unknownWorldGetsSurvivalFallbackPrefix() {
        PlayerMock player = playerInWorldKeyed("custom", "spaghetti", "Bee");
        tabManager.refreshTabName(player);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(player.playerListName());
        assertTrue(plain.startsWith("[Survival]"), "fallback prefix is [Survival], got: " + plain);
    }
}
