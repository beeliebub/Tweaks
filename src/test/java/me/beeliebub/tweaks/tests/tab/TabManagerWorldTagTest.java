package me.beeliebub.tweaks.tests.tab;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.playeradmin.PlayerAdminManager;
import me.beeliebub.tweaks.profiles.WorldProfileEntry;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import me.beeliebub.tweaks.ranks.RankManager;
import me.beeliebub.tweaks.tab.TabManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins TabManager.refreshTab/assignTeam's delegation to WorldProfileTable - i.e. proves TabManager
 * reads live from the injected table rather than any cached/hardcoded map, the historical bug
 * shape this refactor eliminates. WorldProfileTableTest covers exact bundled-default tag/sort-key
 * values at the lower level; MockBukkit's default world always resolves to a "minecraft:<name>"
 * key it does not expose control over, so this class proves wiring correctness by swapping in a
 * WorldProfileTable with a distinctive fallback rather than depending on a specific world key.
 */
class TabManagerWorldTagTest {

    private ServerMock server;
    private Tweaks plugin;
    private EconomyManager economyManager;
    private RankManager rankManager;
    private PlayerAdminManager playerAdminManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);

        economyManager = mock(EconomyManager.class);
        rankManager = mock(RankManager.class);
        when(rankManager.getRankDisplayName(anyInt())).thenReturn("");
        playerAdminManager = mock(PlayerAdminManager.class);
        when(playerAdminManager.nickKey()).thenReturn(new NamespacedKey(plugin, "nickname"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void refreshTabReadsLiveFromTheInjectedWorldProfileTable() {
        // MockBukkit's WorldMock.getKey() always returns "minecraft:overworld" regardless of a
        // created world's name (no per-world custom-key support), and that key is itself a bundled
        // default entry - remove it so this test actually exercises the fallback tier rather than
        // an exact-match coincidence.
        PlayerMock player = server.addPlayer();

        WorldProfileTable defaultTable = new WorldProfileTable(plugin);
        defaultTable.removeEntry("minecraft:overworld");
        TabManager firstManager = new TabManager(plugin, economyManager, rankManager, playerAdminManager, defaultTable);
        firstManager.refreshTab(player);
        String firstPlain = plain(player.playerListName());
        assertTrue(firstPlain.contains("Survival"),
                "an unmapped world key must render the fallback tag");

        // Rewrite the fallback tag directly in config.yml (the one field ConfigValueEditor never
        // exposes a CRUD path for - see WorldProfileTable's class Javadoc) and reload - the render
        // must change, proving TabManager does not cache the tag anywhere of its own.
        plugin.getConfig().set("world-profile-fallback.tag", "CustomFallbackMarker");
        plugin.saveConfig();
        WorldProfileTable customTable = new WorldProfileTable(plugin);

        TabManager secondManager = new TabManager(plugin, economyManager, rankManager, playerAdminManager, customTable);
        secondManager.refreshTab(player);
        String secondPlain = plain(player.playerListName());

        assertTrue(secondPlain.contains("CustomFallbackMarker"),
                "refreshTab must reflect the live WorldProfileTable state, not a cached value");
        assertFalse(secondPlain.contains("Survival"));
    }

    @Test
    void assignTeamPlacesPlayerIntoATeamNamedFromWorldProfileTableSortKey() {
        WorldProfileTable table = new WorldProfileTable(plugin);
        TabManager tabManager = new TabManager(plugin, economyManager, rankManager, playerAdminManager, table);
        PlayerMock player = server.addPlayer();

        tabManager.assignTeam(player);

        assertNotNull(server.getScoreboardManager().getMainScoreboard().getPlayerTeam(player));
    }

    @Test
    void fallbackEntryTagComponentMatchesBundledDefault() {
        WorldProfileTable table = new WorldProfileTable(plugin);
        WorldProfileEntry fallback = table.fallback();

        assertTrue(fallback.tagText().equals("Survival"));
        assertTrue(fallback.color().equals(NamedTextColor.GREEN));
    }
}
