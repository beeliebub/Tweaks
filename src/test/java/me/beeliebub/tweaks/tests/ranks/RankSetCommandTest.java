package me.beeliebub.tweaks.tests.ranks;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.ranks.RankManager;
import me.beeliebub.tweaks.ranks.RankSetCommand;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RankSetCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private EconomyManager em;
    private RankManager rm;
    private RankSetCommand rankSetCmd;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        em = plugin.getEconomyManager();
        rm = plugin.getRankManager();
        rankSetCmd = new RankSetCommand(em, rm);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rankSetAdminCanSetRankById() {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        admin.addAttachment(plugin, Permissions.ADMIN_RANK_SET, true);
        UUID targetId = target.getUniqueId();

        em.setRank(targetId, 0);

        // Setting by ID "3"
        rankSetCmd.onCommand(admin, bukkitCmd, "rank", new String[]{"set", target.getName(), "3"});

        assertEquals(3, em.getRank(targetId), "Rank should be 3 for player " + target.getName() + " (" + targetId + ")");
        MessageAssert.assertMessageSent(admin, "Successfully set");
    }

    @Test
    void rankSetAdminCanSetRankByName() {
        PlayerMock admin = server.addPlayer();
        PlayerMock target = server.addPlayer();
        admin.addAttachment(plugin, Permissions.ADMIN_RANK_SET, true);
        UUID targetId = target.getUniqueId();

        em.setRank(targetId, 0);

        rankSetCmd.onCommand(admin, bukkitCmd, "rank", new String[]{"set", target.getName(), "III"});

        assertEquals(3, em.getRank(targetId));
        MessageAssert.assertMessageSent(admin, "Successfully set");
    }

    @Test
    void rankSetNoPermissionFails() {
        PlayerMock player = server.addPlayer();
        PlayerMock target = server.addPlayer();
        
        rankSetCmd.onCommand(player, bukkitCmd, "rank", new String[]{"set", target.getName(), "5"});

        assertNotEquals(5, em.getRank(target.getUniqueId()));
        MessageAssert.assertMessageSent(player, "do not have permission");
    }

    @Test
    void rankSetTabCompleteSubcommands() {
        PlayerMock admin = server.addPlayer();
        admin.addAttachment(plugin, Permissions.ADMIN_RANK_SET, true);

        List<String> completions = rankSetCmd.onTabComplete(admin, bukkitCmd, "rank", new String[]{""});
        assertTrue(completions.contains("set"));
    }

    @Test
    void rankSetTabCompletePlayers() {
        PlayerMock admin = server.addPlayer();
        admin.addAttachment(plugin, Permissions.ADMIN_RANK_SET, true);
        PlayerMock target = server.addPlayer();

        List<String> completions = rankSetCmd.onTabComplete(admin, bukkitCmd, "rank", new String[]{"set", ""});
        assertTrue(completions.contains(target.getName()));
    }

    @Test
    void rankSetTabCompleteRanks() {
        PlayerMock admin = server.addPlayer();
        admin.addAttachment(plugin, Permissions.ADMIN_RANK_SET, true);

        List<String> completions = rankSetCmd.onTabComplete(admin, bukkitCmd, "rank", new String[]{"set", "target", "I"});
        assertTrue(completions.contains("I"));
        assertTrue(completions.contains("II"));
        assertTrue(completions.contains("III"));
        assertTrue(completions.contains("IV"));
        assertTrue(completions.contains("IX"));
    }
}
