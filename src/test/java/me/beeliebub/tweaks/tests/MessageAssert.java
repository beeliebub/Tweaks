package me.beeliebub.tweaks.tests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageAssert {
    public static void assertMessageSent(PlayerMock player, String expectedText) {
        boolean found = false;
        Component c;
        while ((c = player.nextComponentMessage()) != null) {
            if (PlainTextComponentSerializer.plainText().serialize(c).contains(expectedText)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Expected message containing '" + expectedText + "' not found in player messages");
    }

    public static void assertMessageSent(ConsoleCommandSenderMock console, String expectedText) {
        boolean found = false;
        Component c;
        while ((c = console.nextComponentMessage()) != null) {
            if (PlainTextComponentSerializer.plainText().serialize(c).contains(expectedText)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Expected message containing '" + expectedText + "' not found in console messages");
    }
}
