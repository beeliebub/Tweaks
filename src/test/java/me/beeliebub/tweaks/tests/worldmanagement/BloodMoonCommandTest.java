package me.beeliebub.tweaks.tests.worldmanagement;

import me.beeliebub.tweaks.worldmanagement.MoonSystem;
import me.beeliebub.tweaks.worldmanagement.MoonSystem.ForceResult;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BloodMoonCommandTest {

    private final MoonSystem manager = mock(MoonSystem.class);
    private final MoonSystem cmd = manager;
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void rejectsSenderWithoutAdminBloodmoonPermission() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(Permissions.ADMIN_BLOODMOON)).thenReturn(false);
        when(cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0])).thenCallRealMethod();
        cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0]);
        verify(manager, never()).forceNextFullMoon();
    }

    @Test
    void reportsActivatedResultToSender() {
        CommandSender sender = senderWithPerm();
        when(manager.forceNextFullMoon()).thenReturn(ForceResult.ACTIVATED);
        when(cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0])).thenCallRealMethod();
        cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0]);
        verify(manager).forceNextFullMoon();
        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void reportsAlreadyActiveResultToSender() {
        CommandSender sender = senderWithPerm();
        when(manager.forceNextFullMoon()).thenReturn(ForceResult.ALREADY_ACTIVE);
        when(cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0])).thenCallRealMethod();
        cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0]);
        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void reportsNoWorldResultToSender() {
        CommandSender sender = senderWithPerm();
        when(manager.forceNextFullMoon()).thenReturn(ForceResult.NO_WORLD);
        when(cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0])).thenCallRealMethod();
        cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0]);
        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void alwaysReturnsTrueToSwallowUsageHint() {
        CommandSender sender = senderWithPerm();
        when(manager.forceNextFullMoon()).thenReturn(ForceResult.ACTIVATED);
        when(cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0])).thenCallRealMethod();
        assertTrue(cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0]));
    }

    private CommandSender senderWithPerm() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(Permissions.ADMIN_BLOODMOON)).thenReturn(true);
        return sender;
    }
}
