package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.NightVisionCommand;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class NightVisionCommandTest {

    private ServerMock server;
    private NightVisionCommand cmd;
    private final Command bukkitCmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        cmd = new NightVisionCommand();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rejectsConsoleSender() {
        ConsoleCommandSender console = server.getConsoleSender();
        assertTrue(cmd.onCommand(console, bukkitCmd, "nv", new String[0]));
    }

    @Test
    void appliesNightVisionWhenPlayerDoesNotHaveIt() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "nv", new String[0]);
        assertTrue(player.hasPotionEffect(PotionEffectType.NIGHT_VISION),
                "first invocation must enable night vision");
    }

    @Test
    void removesNightVisionWhenAlreadyActive() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "nv", new String[0]); // enable
        cmd.onCommand(player, bukkitCmd, "nv", new String[0]); // disable
        assertFalse(player.hasPotionEffect(PotionEffectType.NIGHT_VISION),
                "second invocation must clear the effect");
    }

    @Test
    void appliedEffectUsesEssentiallyInfiniteDuration() {
        PlayerMock player = server.addPlayer();
        cmd.onCommand(player, bukkitCmd, "nv", new String[0]);
        var effect = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        assertNotNull(effect);
        // Production code uses Integer.MAX_VALUE so the bar never appears to count down.
        assertEquals(Integer.MAX_VALUE, effect.getDuration());
        assertEquals(0, effect.getAmplifier(), "amplifier 0 = standard night vision");
    }

    @Test
    void alwaysReturnsTrueToSwallowUsageHint() {
        PlayerMock player = server.addPlayer();
        assertTrue(cmd.onCommand(player, bukkitCmd, "nv", new String[0]));
        assertTrue(cmd.onCommand(player, bukkitCmd, "nv", new String[0]));
    }
}
