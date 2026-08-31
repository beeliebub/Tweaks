package me.beeliebub.tweaks.tests.itemadmin;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.itemadmin.DisplayChestSystem;
import me.beeliebub.tweaks.tests.MessageAssert;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.World;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisplayChestCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private DisplayChestSystem displayChestManager;
    private DisplayChestSystem displayChestCommand;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        displayChestManager = mock(DisplayChestSystem.class);
        displayChestCommand = displayChestManager;
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void togglesSetupMode() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message
        when(displayChestManager.toggleSetupMode(player.getUniqueId())).thenReturn(true);

        when(displayChestManager.onCommand(player, bukkitCmd, "displaychest", new String[0])).thenCallRealMethod();
        displayChestCommand.onCommand(player, bukkitCmd, "displaychest", new String[0]);

        verify(displayChestManager).toggleSetupMode(player.getUniqueId());
        MessageAssert.assertMessageSent(player, "setup mode ENABLED");

        when(displayChestManager.toggleSetupMode(player.getUniqueId())).thenReturn(false);
        displayChestCommand.onCommand(player, bukkitCmd, "displaychest", new String[0]);
        MessageAssert.assertMessageSent(player, "setup mode DISABLED");
    }

    @Test
    void togglesRemovalMode() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message
        when(displayChestManager.toggleRemovalMode(player.getUniqueId())).thenReturn(true);

        when(displayChestManager.onCommand(player, bukkitCmd, "displaychest", new String[]{"off"})).thenCallRealMethod();
        displayChestCommand.onCommand(player, bukkitCmd, "displaychest", new String[]{"off"});

        verify(displayChestManager).toggleRemovalMode(player.getUniqueId());
        MessageAssert.assertMessageSent(player, "removal mode ENABLED");

        when(displayChestManager.toggleRemovalMode(player.getUniqueId())).thenReturn(false);
        displayChestCommand.onCommand(player, bukkitCmd, "displaychest", new String[]{"off"});
        MessageAssert.assertMessageSent(player, "removal mode DISABLED");
    }

    @Test
    void rejectsMalformedNameAndMaterialsBeforeToggling() {
        DisplayChestSystem manager = new DisplayChestSystem(plugin);
        PlayerMock player = server.addPlayer("DisplayParser");
        player.nextComponentMessage();

        assertTrue(manager.onCommand(player, bukkitCmd, "displaychest", new String[]{"name:\"Rare"}));
        assertFalse(manager.isSetupMode(player.getUniqueId()));
        MessageAssert.assertMessageSent(player, "missing its closing quote");

        assertTrue(manager.onCommand(player, bukkitCmd, "displaychest",
                new String[]{"off", "name:\"Rare"}));
        assertFalse(manager.isSetupMode(player.getUniqueId()));
        assertFalse(manager.isRemovalMode(player.getUniqueId()));
        MessageAssert.assertMessageSent(player, "missing its closing quote");

        assertTrue(manager.onCommand(player, bukkitCmd, "displaychest", new String[]{"AIR"}));
        assertFalse(manager.isSetupMode(player.getUniqueId()));
        MessageAssert.assertMessageSent(player, "Unknown or non-item");

        assertTrue(manager.onCommand(player, bukkitCmd, "displaychest", new String[]{"PISTON_HEAD"}));
        assertFalse(manager.isSetupMode(player.getUniqueId()));
        MessageAssert.assertMessageSent(player, "Unknown or non-item");

        assertTrue(manager.onCommand(player, bukkitCmd, "displaychest",
                new String[]{"DIAMOND", "EMERALD"}));
        assertFalse(manager.isSetupMode(player.getUniqueId()));
        MessageAssert.assertMessageSent(player, "at most one");

        assertTrue(manager.onCommand(player, bukkitCmd, "displaychest",
                new String[]{"name:\"off", "Stock\""}));
        assertTrue(manager.isSetupMode(player.getUniqueId()));
        assertFalse(manager.isRemovalMode(player.getUniqueId()));
    }

    @Test
    void quotedNameAndStrictMaterialOverrideAreAppliedAtRenderTime() {
        DisplayChestSystem manager = new DisplayChestSystem(plugin);
        PlayerMock player = server.addPlayer("DisplayRenderer");
        player.nextComponentMessage();
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        assertTrue(manager.onCommand(player, bukkitCmd, "displaychest",
                new String[]{"side", "NETHERITE_BLOCK", "name:\"&#FF5555Rare", "&lStock\""}));
        assertTrue(manager.isSetupMode(player.getUniqueId()));

        World world = server.addSimpleWorld("display-render");
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();
        chest.getInventory().setItem(0, new ItemStack(Material.EMERALD));

        manager.processChestSide(block, BlockFace.NORTH, player);

        ItemDisplay display = assertInstanceOf(ItemDisplay.class,
                world.getEntitiesByClass(ItemDisplay.class).stream().findFirst().orElseThrow());
        assertEquals(Material.NETHERITE_BLOCK, display.getItemStack().getType());
        Component name = display.customName();
        assertNotNull(name);
        assertEquals("Rare Stock", fullText(name));
        assertTrue(hasDecoration(name, TextDecoration.BOLD));
        assertFalse(display.isCustomNameVisible());
    }

    @Test
    void emptyNameLeavesTheChestItemUnnamed() {
        DisplayChestSystem manager = new DisplayChestSystem(plugin);
        PlayerMock player = server.addPlayer("DisplayEmptyName");
        player.nextComponentMessage();
        assertTrue(manager.onCommand(player, bukkitCmd, "displaychest", new String[]{"name:\"\""}));

        World world = server.addSimpleWorld("display-empty-name");
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);
        ((Chest) block.getState()).getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        manager.processChest(block, player);

        ItemDisplay display = assertInstanceOf(ItemDisplay.class,
                world.getEntitiesByClass(ItemDisplay.class).stream().findFirst().orElseThrow());
        assertEquals(Material.DIAMOND, display.getItemStack().getType());
        assertNull(display.customName());
    }

    @Test
    void setupToggleBranchesClearPendingOverridesAndQuitCleansThemUp() {
        DisplayChestSystem manager = new DisplayChestSystem(plugin);
        PlayerMock player = server.addPlayer("DisplayCleanup");
        player.nextComponentMessage();
        assertTrue(manager.onCommand(player, bukkitCmd, "displaychest",
                new String[]{"NETHERITE_BLOCK", "name:\"Old Name\""}));
        manager.onCommand(player, bukkitCmd, "displaychest", new String[0]);
        assertFalse(manager.isSetupMode(player.getUniqueId()));
        assertTrue(manager.onCommand(player, bukkitCmd, "displaychest", new String[0]));

        World world = server.addSimpleWorld("display-cleanup");
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);
        ((Chest) block.getState()).getInventory().setItem(0, new ItemStack(Material.GOLD_INGOT));
        manager.processChest(block, player);
        ItemDisplay display = assertInstanceOf(ItemDisplay.class,
                world.getEntitiesByClass(ItemDisplay.class).stream().findFirst().orElseThrow());
        assertEquals(Material.GOLD_INGOT, display.getItemStack().getType());
        assertNull(display.customName());

        manager.onCommand(player, bukkitCmd, "displaychest", new String[0]);
        manager.onCommand(player, bukkitCmd, "displaychest",
                new String[]{"DIAMOND", "name:\"Quit Name\""});
        assertTrue(manager.isSetupMode(player.getUniqueId()));
        manager.onPlayerQuit(new PlayerQuitEvent(player, "quit"));
        assertFalse(manager.isSetupMode(player.getUniqueId()));
        assertTrue(manager.toggleSetupMode(player.getUniqueId()));
        manager.processChest(block, player);
        ItemDisplay afterQuit = assertInstanceOf(ItemDisplay.class,
                world.getEntitiesByClass(ItemDisplay.class).stream().findFirst().orElseThrow());
        assertEquals(Material.GOLD_INGOT, afterQuit.getItemStack().getType());
        assertNull(afterQuit.customName());
        manager.toggleSetupMode(player.getUniqueId());
    }

    @Test
    void tabCompletionIncludesMaterialsAndNameStubAtEveryPosition() {
        DisplayChestSystem manager = new DisplayChestSystem(plugin);
        PlayerMock player = server.addPlayer("DisplayCompletion");
        assertTrue(manager.onTabComplete(player, bukkitCmd, "displaychest", new String[]{""})
                .containsAll(java.util.List.of("hand", "side", "off", "DIAMOND_SWORD", "name:\"")));
        assertTrue(manager.onTabComplete(player, bukkitCmd, "displaychest", new String[]{"hand", "D"})
                .contains("DIAMOND_SWORD"));
        assertTrue(manager.onTabComplete(player, bukkitCmd, "displaychest", new String[]{"side", "name"})
                .contains("name:\""));
    }

    private static String fullText(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof TextComponent textComponent) text.append(textComponent.content());
        for (Component child : component.children()) text.append(fullText(child));
        return text.toString();
    }

    private static boolean hasDecoration(Component component, TextDecoration decoration) {
        if (component.decoration(decoration) == TextDecoration.State.TRUE) return true;
        for (Component child : component.children()) {
            if (hasDecoration(child, decoration)) return true;
        }
        return false;
    }
}
