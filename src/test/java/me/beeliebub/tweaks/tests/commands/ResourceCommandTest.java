package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.commands.ResourceCommand;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ResourceCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private ResourceHunt resourceHunt;
    private ResourceHuntItems resourceHuntItems;
    private ResourceCommand resourceCommand;
    private final Command bukkitCmd = Mockito.mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        resourceHunt = Mockito.mock(ResourceHunt.class);
        resourceHuntItems = Mockito.mock(ResourceHuntItems.class);
        resourceCommand = new ResourceCommand(resourceHunt, resourceHuntItems);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -------------------------------------------------------------------------
    // Existing teleport tests
    // -------------------------------------------------------------------------

    @Test
    void onlyPlayersCanUse() {
        resourceCommand.onCommand(server.getConsoleSender(), null, "resource", new String[0]);
        MessageAssert.assertMessageSent(server.getConsoleSender(), "Only players can use this command");
    }

    @Test
    void blocksEntryWithDisallowedItems() {
        when(resourceHunt.getActiveWorldKey()).thenReturn("minecraft:overworld");
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // clear join message
        when(resourceHuntItems.getDisallowedItems(player)).thenReturn(List.of(Material.DIAMOND_BLOCK));

        resourceCommand.onCommand(player, null, "resource", new String[0]);

        MessageAssert.assertMessageSent(player, "You cannot enter the resource world with these items: diamond block");
    }

    @Test
    void teleportsToResourceWorld() {
        String worldKey = "minecraft:overworld";
        when(resourceHunt.getActiveWorldKey()).thenReturn(worldKey);
        when(resourceHuntItems.getDisallowedItems(any())).thenReturn(Collections.emptyList());

        var world = server.addSimpleWorld(worldKey);
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // clear join message
        server.addSimpleWorld("minecraft:spawn");
        player.teleport(server.getWorld("minecraft:spawn").getSpawnLocation());

        resourceCommand.onCommand(player, null, "resource", new String[0]);

        server.getScheduler().performOneTick();

        assertEquals(world, player.getWorld());
        MessageAssert.assertMessageSent(player, "Teleporting to the resource world...");
    }

    @Test
    void allowEntryIfAlreadyInResourceWorld() {
        String worldKey = "minecraft:overworld";
        when(resourceHunt.getActiveWorldKey()).thenReturn(worldKey);

        var world = server.addSimpleWorld(worldKey);
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // clear join message
        player.teleport(world.getSpawnLocation());

        when(resourceHuntItems.getDisallowedItems(player)).thenReturn(List.of(Material.DIAMOND_BLOCK));

        resourceCommand.onCommand(player, null, "resource", new String[0]);

        server.getScheduler().performOneTick();

        MessageAssert.assertMessageSent(player, "Teleporting to the resource world...");
    }

    @Test
    void handlesNetherEnvironment() {
        String worldKey = "minecraft:the_nether";
        when(resourceHunt.getActiveWorldKey()).thenReturn(worldKey);
        when(resourceHuntItems.getDisallowedItems(any())).thenReturn(Collections.emptyList());

        var world = server.addSimpleWorld(worldKey);
        world.setEnvironment(World.Environment.NETHER);

        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // clear join message

        resourceCommand.onCommand(player, null, "resource", new String[0]);

        server.getScheduler().performOneTick();

        assertEquals(world, player.getWorld());
    }

    // -------------------------------------------------------------------------
    // settarget subcommand tests
    // -------------------------------------------------------------------------

    @Test
    void settarget_selfPermission_setsOwnTarget() {
        when(resourceHunt.isActive()).thenReturn(true);
        when(resourceHunt.forceTarget(any(UUID.class), any(String.class))).thenReturn(true);

        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // clear join message
        player.addAttachment(plugin, Permissions.ADMIN_RESOURCE_SETTARGET_SELF, true);

        resourceCommand.onCommand(player, bukkitCmd, "resource", new String[]{"settarget", "iron_ore"});

        MessageAssert.assertMessageSent(player, "Your Resource Hunt target has been set to");
    }

    @Test
    void settarget_selfPermission_cannotTargetOtherPlayer() {
        when(resourceHunt.isActive()).thenReturn(true);

        PlayerMock sender = server.addPlayer("Sender");
        sender.nextComponentMessage(); // clear join message
        PlayerMock other  = server.addPlayer("Other");
        other.nextComponentMessage(); // clear join message

        sender.addAttachment(plugin, Permissions.ADMIN_RESOURCE_SETTARGET_SELF, true);

        // args.length == 3 but sender only has SELF — the 3-arg path requires OTHER permission,
        // so it falls back to the usage message branch.
        resourceCommand.onCommand(sender, bukkitCmd, "resource", new String[]{"settarget", "Other", "iron_ore"});

        MessageAssert.assertMessageSent(sender, "Usage:");
    }

    @Test
    void settarget_otherPermission_canTargetOtherPlayer() {
        when(resourceHunt.isActive()).thenReturn(true);
        when(resourceHunt.forceTarget(any(UUID.class), any(String.class))).thenReturn(true);

        PlayerMock sender = server.addPlayer("Sender");
        sender.nextComponentMessage(); // clear join message
        PlayerMock other  = server.addPlayer("TargetPlayer");
        other.nextComponentMessage(); // clear join message

        sender.addAttachment(plugin, Permissions.ADMIN_RESOURCE_SETTARGET_OTHER, true);

        resourceCommand.onCommand(sender, bukkitCmd, "resource",
                new String[]{"settarget", "TargetPlayer", "iron_ore"});

        MessageAssert.assertMessageSent(sender, "TargetPlayer");
        MessageAssert.assertMessageSent(other, "Your Resource Hunt target has been changed to");
    }

    @Test
    void settarget_invalidTargetKey_failsGracefully() {
        when(resourceHunt.isActive()).thenReturn(true);
        when(resourceHunt.forceTarget(any(UUID.class), any(String.class))).thenReturn(false);

        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // clear join message
        player.addAttachment(plugin, Permissions.ADMIN_RESOURCE_SETTARGET_SELF, true);

        resourceCommand.onCommand(player, bukkitCmd, "resource",
                new String[]{"settarget", "not_a_real_target"});

        MessageAssert.assertMessageSent(player, "Unknown target key:");
    }

    @Test
    void settarget_noPermission_denied() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // clear join message
        player.setOp(false);

        resourceCommand.onCommand(player, bukkitCmd, "resource", new String[]{"settarget", "iron_ore"});

        MessageAssert.assertMessageSent(player, "You don't have permission");
    }

    // -------------------------------------------------------------------------
    // Tab completion tests
    // -------------------------------------------------------------------------

    @Test
    void tabComplete_returnsTargetKeys_forSelfPermission() {
        when(resourceHunt.getAvailableTargetKeys()).thenReturn(Set.of("iron_ore", "gold_ore"));

        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, Permissions.ADMIN_RESOURCE_SETTARGET_SELF, true);

        // args: ["settarget", ""] — requesting target key completions (SELF-only path)
        List<String> completions = resourceCommand.onTabComplete(
                player, bukkitCmd, "resource", new String[]{"settarget", ""});

        assertNotNull(completions);
        assertTrue(completions.contains("iron_ore"), "Expected iron_ore in completions");
        assertTrue(completions.contains("gold_ore"), "Expected gold_ore in completions");
    }

    @Test
    void tabComplete_suggestsOnlinePlayers_forOtherPermission() {
        when(resourceHunt.getAvailableTargetKeys()).thenReturn(Set.of("iron_ore"));

        PlayerMock sender  = server.addPlayer("Admin");
        PlayerMock target1 = server.addPlayer("PlayerOne");
        PlayerMock target2 = server.addPlayer("PlayerTwo");
        sender.addAttachment(plugin, Permissions.ADMIN_RESOURCE_SETTARGET_OTHER, true);

        // args: ["settarget", ""] — requesting player name completions
        List<String> completions = resourceCommand.onTabComplete(
                sender, bukkitCmd, "resource", new String[]{"settarget", ""});

        assertNotNull(completions);
        assertTrue(completions.contains("PlayerOne"), "Expected PlayerOne in completions");
        assertTrue(completions.contains("PlayerTwo"), "Expected PlayerTwo in completions");
    }

    @Test
    void tabComplete_returnsTargetKeys_forOtherPermission_arg3() {
        when(resourceHunt.getAvailableTargetKeys()).thenReturn(Set.of("iron_ore", "diamond_ore"));

        PlayerMock sender = server.addPlayer("Admin");
        sender.addAttachment(plugin, Permissions.ADMIN_RESOURCE_SETTARGET_OTHER, true);

        // args: ["settarget", "SomePlayer", ""] — requesting target key completions at arg 3
        List<String> completions = resourceCommand.onTabComplete(
                sender, bukkitCmd, "resource", new String[]{"settarget", "SomePlayer", ""});

        assertNotNull(completions);
        assertTrue(completions.contains("iron_ore"), "Expected iron_ore in completions");
        assertTrue(completions.contains("diamond_ore"), "Expected diamond_ore in completions");
    }

    @Test
    void tabComplete_noPermission_returnsEmpty() {
        PlayerMock player = server.addPlayer();
        player.setOp(false);

        List<String> completions = resourceCommand.onTabComplete(
                player, bukkitCmd, "resource", new String[]{"settarget", ""});

        assertNotNull(completions);
        assertTrue(completions.isEmpty(), "No completions should be offered without permission");
    }
}
