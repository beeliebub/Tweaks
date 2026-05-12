package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.BloodMoonCommand;
import me.beeliebub.tweaks.managers.BloodMoonManager;
import me.beeliebub.tweaks.managers.BloodMoonManager.ForceResult;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BloodMoonCommandTest {

    private final BloodMoonManager manager = mock(BloodMoonManager.class);
    private final BloodMoonCommand cmd = new BloodMoonCommand(manager);
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void rejectsSenderWithoutAdminBloodmoonPermission() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(Permissions.ADMIN_BLOODMOON)).thenReturn(false);
        cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0]);
        verify(manager, never()).forceNextFullMoon();
    }

    @Test
    void reportsActivatedResultToSender() {
        CommandSender sender = senderWithPerm();
        when(manager.forceNextFullMoon()).thenReturn(ForceResult.ACTIVATED);
        cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0]);
        verify(manager).forceNextFullMoon();
        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void reportsAlreadyActiveResultToSender() {
        CommandSender sender = senderWithPerm();
        when(manager.forceNextFullMoon()).thenReturn(ForceResult.ALREADY_ACTIVE);
        cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0]);
        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void reportsNoWorldResultToSender() {
        CommandSender sender = senderWithPerm();
        when(manager.forceNextFullMoon()).thenReturn(ForceResult.NO_WORLD);
        cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0]);
        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void alwaysReturnsTrueToSwallowUsageHint() {
        CommandSender sender = senderWithPerm();
        when(manager.forceNextFullMoon()).thenReturn(ForceResult.ACTIVATED);
        assertTrue(cmd.onCommand(sender, bukkitCmd, "bloodmoon", new String[0]));
    }

    private CommandSender senderWithPerm() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(Permissions.ADMIN_BLOODMOON)).thenReturn(true);
        return sender;
    }
}
