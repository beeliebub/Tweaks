package me.beeliebub.tweaks.tests;

import me.beeliebub.tweaks.Tweaks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

class TweaksTest {

    private ServerMock server;
    private Tweaks plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginEnabledSuccessfully() {
        assertTrue(plugin.isEnabled());
    }

    @Test
    void commandsAreRegistered() {
        assertNotNull(server.getCommandMap().getCommand("nick"));
        assertNotNull(server.getCommandMap().getCommand("tpa"));
        assertNotNull(server.getCommandMap().getCommand("back"));
        assertNotNull(server.getCommandMap().getCommand("home"));
        assertNotNull(server.getCommandMap().getCommand("warp"));
        assertNotNull(server.getCommandMap().getCommand("help"));
    }

    @Test
    void managersAreInitialized() {
        assertNotNull(plugin.getTelekinesis());
        assertNotNull(plugin.getReplant());
    }
}
