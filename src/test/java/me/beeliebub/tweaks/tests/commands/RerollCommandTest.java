package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.commands.RerollCommand;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.recipes.ResourceRupee;
import me.beeliebub.tweaks.tests.MessageAssert;
import me.beeliebub.tweaks.utils.InventoryUtil;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RerollCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private ResourceHunt resourceHunt;
    private RerollCommand cmd;
    private PlayerMock player;
    private final Command bukkitCmd = mock(Command.class);
    private final ResourceRupee rupeeFactory = new ResourceRupee();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        resourceHunt = mock(ResourceHunt.class);
        cmd = new RerollCommand(resourceHunt);
        player = server.addPlayer();
        // Drain any join messages from the real ResourceHunt listener wired by Tweaks.
        player.nextComponentMessage();
        // Default stubs: hunt is active and rerollTarget succeeds.
        when(resourceHunt.isActive()).thenReturn(true);
        when(resourceHunt.rerollTarget(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -------------------------------------------------------------------------
    // Test 1: First reroll is free — target changes, no rupees deducted.
    // -------------------------------------------------------------------------

    @Test
    void firstRerollIsFreeAndCallsRerollTarget() {
        when(resourceHunt.hasUsedFreeReroll(any(UUID.class))).thenReturn(false);

        int balanceBefore = InventoryUtil.getResourceRupeeBalance(player);

        try (MockedStatic<ResourceHunt> staticHunt = mockStatic(ResourceHunt.class)) {
            // Make the world-check pass: player is "in the resource world".
            staticHunt.when(() -> ResourceHunt.isResourceWorld(anyString())).thenReturn(true);

            cmd.onCommand(player, bukkitCmd, "reroll", new String[0]);
        }

        // markFreeRerollUsed must be called to consume the free token.
        verify(resourceHunt).markFreeRerollUsed(player.getUniqueId());
        // rerollTarget must be called with the player.
        verify(resourceHunt).rerollTarget(player);
        // No rupees should have been deducted (balance unchanged).
        assertEquals(balanceBefore, InventoryUtil.getResourceRupeeBalance(player),
                "First reroll must not deduct any rupees");
        // Player receives a success message mentioning the free reroll.
        MessageAssert.assertMessageSent(player, "Free reroll used");
    }

    // -------------------------------------------------------------------------
    // Test 2: Second reroll — 1 rupee deducted, target changes.
    // -------------------------------------------------------------------------

    @Test
    void secondRerollCostsOneRupeeAndCallsRerollTarget() {
        when(resourceHunt.hasUsedFreeReroll(any(UUID.class))).thenReturn(true);
        // Give the player exactly 3 loose rupees.
        player.getInventory().addItem(rupeeFactory.createRupee(3));

        int balanceBefore = InventoryUtil.getResourceRupeeBalance(player);

        try (MockedStatic<ResourceHunt> staticHunt = mockStatic(ResourceHunt.class)) {
            staticHunt.when(() -> ResourceHunt.isResourceWorld(anyString())).thenReturn(true);

            cmd.onCommand(player, bukkitCmd, "reroll", new String[0]);
        }

        // rerollTarget must be called.
        verify(resourceHunt).rerollTarget(player);
        // Exactly 1 rupee should have been deducted.
        assertEquals(balanceBefore - 1, InventoryUtil.getResourceRupeeBalance(player),
                "Paid reroll must deduct exactly 1 Resource Rupee");
        // markFreeRerollUsed must NOT be called again.
        verify(resourceHunt, never()).markFreeRerollUsed(any(UUID.class));
        // Player receives a success message mentioning the deduction.
        MessageAssert.assertMessageSent(player, "1 Resource Rupee deducted");
    }

    // -------------------------------------------------------------------------
    // Test 3: Second reroll with 0 rupees — rejected, target unchanged.
    // -------------------------------------------------------------------------

    @Test
    void rerollRejectedWhenInsufficientRupees() {
        when(resourceHunt.hasUsedFreeReroll(any(UUID.class))).thenReturn(true);
        // Player has no rupees — inventory is empty by default.

        try (MockedStatic<ResourceHunt> staticHunt = mockStatic(ResourceHunt.class)) {
            staticHunt.when(() -> ResourceHunt.isResourceWorld(anyString())).thenReturn(true);

            cmd.onCommand(player, bukkitCmd, "reroll", new String[0]);
        }

        // rerollTarget must NOT be called.
        verify(resourceHunt, never()).rerollTarget(any());
        // Player receives a "not enough" message. Use the literal apostrophe-free fragment
        // that avoids encoding issues across platforms.
        MessageAssert.assertMessageSent(player, "enough Resource Rupees");
    }

    // -------------------------------------------------------------------------
    // Test 4: Player not in the resource world — rejected.
    // -------------------------------------------------------------------------

    @Test
    void rerollRejectedOutsideResourceWorld() {
        // isResourceWorld returns false: player is outside the resource world.
        try (MockedStatic<ResourceHunt> staticHunt = mockStatic(ResourceHunt.class)) {
            staticHunt.when(() -> ResourceHunt.isResourceWorld(anyString())).thenReturn(false);

            cmd.onCommand(player, bukkitCmd, "reroll", new String[0]);
        }

        // Neither rerollTarget nor markFreeRerollUsed should be called.
        verify(resourceHunt, never()).rerollTarget(any());
        verify(resourceHunt, never()).markFreeRerollUsed(any(UUID.class));
        // Player receives a rejection message about being in the resource world.
        MessageAssert.assertMessageSent(player, "must be in the resource world");
    }

    // -------------------------------------------------------------------------
    // Additional edge cases
    // -------------------------------------------------------------------------

    @Test
    void consoleIsRejected() {
        cmd.onCommand(server.getConsoleSender(), bukkitCmd, "reroll", new String[0]);
        verify(resourceHunt, never()).rerollTarget(any());
    }

    @Test
    void inactiveHuntRejectsReroll() {
        when(resourceHunt.isActive()).thenReturn(false);

        cmd.onCommand(player, bukkitCmd, "reroll", new String[0]);

        verify(resourceHunt, never()).rerollTarget(any());
        MessageAssert.assertMessageSent(player, "not active this session");
    }

    @Test
    void freeTokenNotConsumedWhenHuntInactive() {
        when(resourceHunt.isActive()).thenReturn(false);

        cmd.onCommand(player, bukkitCmd, "reroll", new String[0]);

        verify(resourceHunt, never()).markFreeRerollUsed(any(UUID.class));
    }

    // -------------------------------------------------------------------------
    // First-tier lock: once a player has crossed T1, rerolls are refused with
    // the canonical message and rerollTarget is never invoked (even if the free
    // token would otherwise have been available).
    // -------------------------------------------------------------------------

    @Test
    void rerollRejectedAfterFirstTierCompleted() {
        when(resourceHunt.hasCompletedFirstTier(any(UUID.class))).thenReturn(true);
        when(resourceHunt.hasUsedFreeReroll(any(UUID.class))).thenReturn(false);

        try (MockedStatic<ResourceHunt> staticHunt = mockStatic(ResourceHunt.class)) {
            staticHunt.when(() -> ResourceHunt.isResourceWorld(anyString())).thenReturn(true);

            cmd.onCommand(player, bukkitCmd, "reroll", new String[0]);
        }

        verify(resourceHunt, never()).rerollTarget(any());
        verify(resourceHunt, never()).markFreeRerollUsed(any(UUID.class));
        MessageAssert.assertMessageSent(player,
                "You have already completed a tier in your current hunt. You cannot reroll until the next session.");
    }
}
