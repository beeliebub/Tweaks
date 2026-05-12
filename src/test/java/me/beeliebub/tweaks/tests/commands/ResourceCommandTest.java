package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.ResourceCommand;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.tests.MessageAssert;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ResourceCommandTest {

    private ServerMock server;
    private ResourceHunt resourceHunt;
    private ResourceHuntItems resourceHuntItems;
    private ResourceCommand resourceCommand;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        resourceHunt = Mockito.mock(ResourceHunt.class);
        resourceHuntItems = Mockito.mock(ResourceHuntItems.class);
        resourceCommand = new ResourceCommand(resourceHunt, resourceHuntItems);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onlyPlayersCanUse() {
        resourceCommand.onCommand(server.getConsoleSender(), null, "resource", new String[0]);
        MessageAssert.assertMessageSent(server.getConsoleSender(), "Only players can use this command");
    }

    @Test
    void blocksEntryWithDisallowedItems() {
        when(resourceHunt.getActiveWorldKey()).thenReturn("minecraft:overworld");
        PlayerMock player = server.addPlayer();
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
        // Start player in a different world
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
        player.teleport(world.getSpawnLocation());

        // Even if items are disallowed, it should allow "re-teleporting" within the world
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
        
        resourceCommand.onCommand(player, null, "resource", new String[0]);

        server.getScheduler().performOneTick();

        assertEquals(world, player.getWorld());
    }
}
