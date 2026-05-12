package me.beeliebub.tweaks.tests.minigames;

import me.beeliebub.tweaks.minigames.RewardCommand;
import me.beeliebub.tweaks.minigames.RewardListener;
import me.beeliebub.tweaks.minigames.RewardManager;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RewardListenerTest {

    private ServerMock server;
    private RewardManager rewardManager;
    private RewardCommand rewardCommand;
    private RewardListener listener;
    private Map<UUID, String> editingSessions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        rewardManager = mock(RewardManager.class);
        rewardCommand = mock(RewardCommand.class);
        editingSessions = new HashMap<>();
        when(rewardCommand.getEditingSessions()).thenReturn(editingSessions);
        listener = new RewardListener(rewardManager, rewardCommand);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void joinHandlerNotifiesPlayerAboutPendingRewards() {
        PlayerMock player = server.addPlayer();
        when(rewardManager.getPendingRewards(player.getUniqueId()))
                .thenReturn(List.of("loot", "more_loot"));

        listener.onPlayerJoin(new PlayerJoinEvent(player, ""));
        // Notification mentions the count and the claim command — assert via captured messages.
        String allMessages = String.join("\n", capturedMessages(player));
        assertTrue(allMessages.contains("2"), "expected pending count, saw: " + allMessages);
        assertTrue(allMessages.contains("/reward claim"),
                "expected /reward claim hint, saw: " + allMessages);
    }

    @Test
    void joinHandlerStaysQuietWhenNoPendingRewards() {
        PlayerMock player = server.addPlayer();
        when(rewardManager.getPendingRewards(player.getUniqueId()))
                .thenReturn(List.of());

        listener.onPlayerJoin(new PlayerJoinEvent(player, ""));
        String allMessages = String.join("\n", capturedMessages(player));
        assertFalse(allMessages.contains("/reward claim"),
                "no notification expected when pending list is empty");
    }

    @Test
    void inventoryCloseSavesContentsForActiveEditingSession() {
        PlayerMock player = server.addPlayer();
        editingSessions.put(player.getUniqueId(), "loot");

        Inventory inv = mock(Inventory.class);
        ItemStack[] contents = new ItemStack[27];
        when(inv.getContents()).thenReturn(contents);

        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(inv);

        listener.onInventoryClose(event);

        verify(rewardManager).setRewardItems("loot", contents);
        assertFalse(editingSessions.containsKey(player.getUniqueId()),
                "session must be removed after save");
    }

    @Test
    void inventoryCloseIsNoOpWhenPlayerHasNoActiveEditSession() {
        PlayerMock player = server.addPlayer();
        // No entry for player in editingSessions

        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onInventoryClose(event);
        verify(rewardManager, never()).setRewardItems(any(), any());
    }

    @Test
    void inventoryCloseIgnoresNonPlayerHumans() {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        // getPlayer() returns a HumanEntity that's not a Player (e.g., armor stand, NPC).
        org.bukkit.entity.HumanEntity nonPlayer = mock(org.bukkit.entity.HumanEntity.class);
        when(event.getPlayer()).thenReturn(nonPlayer);

        listener.onInventoryClose(event);
        verify(rewardManager, never()).setRewardItems(any(), any());
    }

    private List<String> capturedMessages(PlayerMock player) {
        List<String> out = new java.util.ArrayList<>();
        try {
            // PlayerMock keeps a queue of messages; drain them.
            while (true) {
                String msg = player.nextMessage();
                if (msg == null) break;
                out.add(msg);
            }
        } catch (Exception ignored) {}
        return out;
    }
}
