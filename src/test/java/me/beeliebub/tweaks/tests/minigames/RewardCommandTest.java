package me.beeliebub.tweaks.tests.minigames;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.RewardCommand;
import me.beeliebub.tweaks.minigames.RewardManager;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RewardCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private RewardManager rewardManager;
    private RewardCommand rewardCommand;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        rewardManager = mock(RewardManager.class);
        rewardCommand = new RewardCommand(rewardManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createRewardRequiresPermission() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.ADMIN_REWARD, false);

        rewardCommand.onCommand(player, bukkitCmd, "reward", new String[]{"create", "test"});

        verify(rewardManager, never()).createReward(anyString());
        assertNotNull(player.nextMessage());
    }

    @Test
    void createRewardSuccess() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.ADMIN_REWARD, true);
        when(rewardManager.rewardExists("test")).thenReturn(false);

        rewardCommand.onCommand(player, bukkitCmd, "reward", new String[]{"create", "test"});

        verify(rewardManager).createReward("test");
        assertNotNull(player.nextMessage());
    }

    @Test
    void editRewardOpensInventory() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.ADMIN_REWARD, true);
        when(rewardManager.rewardExists("test")).thenReturn(true);
        when(rewardManager.getRewardItems("test")).thenReturn(new ItemStack[0]);

        rewardCommand.onCommand(player, bukkitCmd, "reward", new String[]{"edit", "test"});

        assertNotNull(player.getOpenInventory());
        assertTrue(rewardCommand.getEditingSessions().containsKey(player.getUniqueId()));
    }

    @Test
    void giveRewardSuccess() {
        PlayerMock admin = server.addPlayer();
        admin.addAttachment(plugin, Permissions.ADMIN_REWARD, true);
        PlayerMock target = server.addPlayer();
        when(rewardManager.rewardExists("test")).thenReturn(true);

        rewardCommand.onCommand(admin, bukkitCmd, "reward", new String[]{"give", target.getName(), "test", "2"});

        verify(rewardManager, times(2)).grantReward(target.getUniqueId(), "test");
        assertNotNull(admin.nextMessage());
        assertNotNull(target.nextMessage());
    }

    @Test
    void claimRewardSuccess() {
        PlayerMock player = server.addPlayer();
        when(rewardManager.getPendingRewards(player.getUniqueId())).thenReturn(List.of("test"));
        when(rewardManager.getRewardItems("test")).thenReturn(new ItemStack[]{new ItemStack(Material.DIAMOND)});

        rewardCommand.onCommand(player, bukkitCmd, "reward", new String[]{"claim"});

        assertTrue(player.getInventory().contains(Material.DIAMOND));
        verify(rewardManager).clearPendingRewards(player.getUniqueId());
        assertNotNull(player.nextMessage());
    }

    @Test
    void tabCompleteAdmins() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.ADMIN_REWARD, true);

        List<String> results = rewardCommand.onTabComplete(player, bukkitCmd, "reward", new String[]{""});
        
        assertNotNull(results);
        assertTrue(results.contains("create"));
        assertTrue(results.contains("edit"));
        assertTrue(results.contains("give"));
        assertTrue(results.contains("claim"));
    }
}
