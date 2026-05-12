package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.WarpsCommand;
import me.beeliebub.tweaks.managers.StorageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class WarpsCommandTest {

    private final StorageManager storage = mock(StorageManager.class);
    private final WarpsCommand cmd = new WarpsCommand(storage);
    private final Command bukkitCmd = mock(Command.class);

    @Test
    void announcesNoWarpsWhenEmpty() {
        when(storage.getWarps()).thenReturn(Set.of());
        CommandSender sender = mock(CommandSender.class);
        boolean handled = cmd.onCommand(sender, bukkitCmd, "warps", new String[0]);
        assertTrue(handled);
        verify(sender).sendMessage(argThat(componentContains("no warps")));
    }

    @Test
    void listsWarpsCommaSeparatedWhenPresent() {
        Set<String> warps = new LinkedHashSet<>();
        warps.add("spawn");
        warps.add("shop");
        when(storage.getWarps()).thenReturn(warps);

        CommandSender sender = mock(CommandSender.class);
        cmd.onCommand(sender, bukkitCmd, "warps", new String[0]);

        verify(sender).sendMessage(argThat(componentContains("spawn")));
        verify(sender).sendMessage(argThat(componentContains("shop")));
    }

    private static org.mockito.ArgumentMatcher<net.kyori.adventure.text.Component> componentContains(String needle) {
        return c -> c != null
                && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(c).toLowerCase().contains(needle.toLowerCase());
    }
}
