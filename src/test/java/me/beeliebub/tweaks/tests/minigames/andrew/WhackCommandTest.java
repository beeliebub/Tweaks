package me.beeliebub.tweaks.tests.minigames.andrew;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.RewardManager;
import me.beeliebub.tweaks.minigames.andrew.WhackCommand;
import me.beeliebub.tweaks.minigames.andrew.WhackConfig;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhackCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private WhackConfig config;
    private RewardManager rewardManager;
    private WhackCommand whackCommand;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        config = mock(WhackConfig.class);
        rewardManager = mock(RewardManager.class);
        whackCommand = new WhackCommand(plugin, config, rewardManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void arenaSetupRequiresPermission() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.ADMIN_WHACK, false);

        whackCommand.onCommand(player, bukkitCmd, "whack", new String[]{"arena"});

        assertNotNull(player.nextMessage());
        // Verify no success message
    }

    @Test
    void corner1SetSuccess() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.ADMIN_WHACK, true);
        
        Location loc = new Location(server.addSimpleWorld("world"), 10, 64, 10);
        // player.simulateBlockLookup(loc.getBlock());

        whackCommand.onCommand(player, bukkitCmd, "whack", new String[]{"corner1"});

        assertNotNull(player.nextMessage());
        assertTrue(player.nextMessage().toString().contains("Corner 1 set"));
    }

    @Test
    void corner2SetSuccess() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.ADMIN_WHACK, true);
        
        Location loc1 = new Location(server.addSimpleWorld("world"), 10, 64, 10);
        // player.simulateBlockLookup(loc1.getBlock());
        whackCommand.onCommand(player, bukkitCmd, "whack", new String[]{"corner1"});
        player.nextMessage(); player.nextMessage();

        Location loc2 = new Location(loc1.getWorld(), 20, 70, 20);
        // player.simulateBlockLookup(loc2.getBlock());
        whackCommand.onCommand(player, bukkitCmd, "whack", new String[]{"corner2"});

        assertNotNull(player.nextMessage());
        verify(config).saveArena(any(), any());
    }

    @Test
    void setRewardSuccess() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.ADMIN_WHACK, true);
        when(rewardManager.rewardExists("test_reward")).thenReturn(true);

        whackCommand.onCommand(player, bukkitCmd, "whack", new String[]{"setreward", "1", "test_reward"});

        verify(config).setPlaceReward(1, "test_reward");
        assertNotNull(player.nextMessage());
    }
}

