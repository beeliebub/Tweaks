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

    // One command per bootstrap tier, so a dropped or mis-ordered XxxBootstrap.register
    // call in Tweaks.onEnable() fails a named test instead of silently vanishing.
    @Test
    void commandsAreRegisteredPerBootstrapTier() {
        assertNotNull(server.getCommandMap().getCommand("balance"), "tier 1 - economy");
        assertNotNull(server.getCommandMap().getCommand("region"), "tier 1 - protection");
        assertNotNull(server.getCommandMap().getCommand("resource"), "tier 2 - minigames");
        assertNotNull(server.getCommandMap().getCommand("bloodmoon"), "tier 2 - worldmanagement");
        assertNotNull(server.getCommandMap().getCommand("home"), "tier 3 - teleport");
        assertNotNull(server.getCommandMap().getCommand("invsee"), "tier 3 - itemadmin");
        assertNotNull(server.getCommandMap().getCommand("toolprotect"), "tier 5 - itemadmin straddle");
        assertNotNull(server.getCommandMap().getCommand("deathinventory"), "tier 5 - deathinventory");
    }

    @Test
    void managersAreInitialized() {
        assertNotNull(plugin.getProtectionManager());
        assertNotNull(plugin.getEconomyManager());
        assertNotNull(plugin.getRankManager());
        assertNotNull(plugin.getPermissionManager());
        assertNotNull(plugin.getRegionSelectionManager());
        assertNotNull(plugin.getProtectionSelectionTool());
        assertNotNull(plugin.getTelekinesis());
        assertNotNull(plugin.getReplant());
        assertNotNull(plugin.getBlackjackListener());
    }
}
