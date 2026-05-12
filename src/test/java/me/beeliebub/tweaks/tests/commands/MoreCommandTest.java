package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.MoreCommand;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MoreCommandTest {

    private final MoreCommand cmd = new MoreCommand();
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void rejectsNonPlayerSender() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        boolean result = cmd.onCommand(console, bukkitCmd, "more", new String[0]);
        assertTrue(result, "command must always return true");
        verify(console).sendMessage(any(net.kyori.adventure.text.Component.class));
        // Console never has its game-mode changed; ensure no Player methods invoked.
        verifyNoMoreInteractions(console);
    }

    @Test
    void rejectsPlayerWithoutAdminMorePermission() {
        Player player = mock(Player.class);
        when(player.hasPermission(Permissions.ADMIN_MORE)).thenReturn(false);
        cmd.onCommand(player, bukkitCmd, "more", new String[0]);
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        // Inventory should never be touched without permission.
        verify(player, never()).getInventory();
    }

    @Test
    void rejectsEmptyHand() {
        Player player = playerWithItem(emptyStack());
        cmd.onCommand(player, bukkitCmd, "more", new String[0]);
        verify(player.getInventory().getItemInMainHand(), never()).setAmount(anyInt());
    }

    @Test
    void maximizesStackToMaxStackSize() {
        ItemStack held = mock(ItemStack.class);
        when(held.isEmpty()).thenReturn(false);
        when(held.getMaxStackSize()).thenReturn(64);
        Player player = playerWithItem(held);

        cmd.onCommand(player, bukkitCmd, "more", new String[0]);
        verify(held).setAmount(64);
    }

    @Test
    void honoursMaterialSpecificStackCap() {
        // Snowballs stack to 16; verify the command uses the held item's reported cap.
        ItemStack snowballs = mock(ItemStack.class);
        when(snowballs.isEmpty()).thenReturn(false);
        when(snowballs.getMaxStackSize()).thenReturn(16);
        Player player = playerWithItem(snowballs);

        cmd.onCommand(player, bukkitCmd, "more", new String[0]);
        verify(snowballs).setAmount(16);
    }

    private Player playerWithItem(ItemStack stack) {
        Player player = mock(Player.class);
        when(player.hasPermission(Permissions.ADMIN_MORE)).thenReturn(true);
        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.getItemInMainHand()).thenReturn(stack);
        when(player.getInventory()).thenReturn(inv);
        return player;
    }

    private ItemStack emptyStack() {
        ItemStack s = mock(ItemStack.class);
        when(s.isEmpty()).thenReturn(true);
        return s;
    }
}
