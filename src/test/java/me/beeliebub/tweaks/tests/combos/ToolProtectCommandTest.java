package me.beeliebub.tweaks.tests.combos;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.combos.ToolProtectCommand;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import me.beeliebub.tweaks.tests.MessageAssert;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolProtectCommandTest {

    private ServerMock server;
    private Tweaks plugin;
    private QualityRegistry qualityRegistry;
    private ToolProtectCommand toolProtectCommand;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        qualityRegistry = mock(QualityRegistry.class);
        toolProtectCommand = new ToolProtectCommand(plugin, qualityRegistry);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void togglesOnOff() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message

        toolProtectCommand.onCommand(player, bukkitCmd, "toolprotect", new String[]{"off"});
        MessageAssert.assertMessageSent(player, "OFF");

        toolProtectCommand.onCommand(player, bukkitCmd, "toolprotect", new String[]{"on"});
        MessageAssert.assertMessageSent(player, "ON");
    }

    @Test
    void setsDurabilityThreshold() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message

        toolProtectCommand.onCommand(player, bukkitCmd, "toolprotect", new String[]{"durability", "50"});
        MessageAssert.assertMessageSent(player, "threshold set to 50");
    }

    @Test
    void protectsLowDurabilityEpicTool() {
        PlayerMock player = server.addPlayer();
        player.nextComponentMessage(); // Clear join message
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        Damageable meta = (Damageable) tool.getItemMeta();
        // Diamond pickaxe max durability is 1561.
        // Set damage to 1500, remaining = 61.
        // Default threshold is 100.
        meta.setDamage(1500);
        tool.setItemMeta(meta);
        tool.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        player.getInventory().setItemInMainHand(tool);

        when(qualityRegistry.getTier(Enchantment.EFFICIENCY)).thenReturn(QualityTier.EPIC);

        BlockBreakEvent event = new BlockBreakEvent(player.getWorld().getBlockAt(0, 0, 0), player);
        toolProtectCommand.onBlockBreak(event);

        assertTrue(event.isCancelled());
        assertNotNull(player.nextActionBar());
    }

    @Test
    void doesNotProtectHighDurabilityTool() {
        PlayerMock player = server.addPlayer();
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        Damageable meta = (Damageable) tool.getItemMeta();
        meta.setDamage(0); // 1561 remaining
        tool.setItemMeta(meta);
        tool.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        player.getInventory().setItemInMainHand(tool);

        when(qualityRegistry.getTier(Enchantment.EFFICIENCY)).thenReturn(QualityTier.EPIC);

        BlockBreakEvent event = new BlockBreakEvent(player.getWorld().getBlockAt(0, 0, 0), player);
        toolProtectCommand.onBlockBreak(event);

        assertFalse(event.isCancelled());
    }
}
