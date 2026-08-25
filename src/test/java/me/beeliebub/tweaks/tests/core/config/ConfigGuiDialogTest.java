package me.beeliebub.tweaks.tests.core.config;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.config.ConfigGUI;
import me.beeliebub.tweaks.core.config.ConfigValueEditor;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class ConfigGuiDialogTest {

    private ServerMock server;
    private Tweaks plugin;
    private ConfigValueEditor editor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        editor = new ConfigValueEditor(plugin, mock(ResourceHuntItems.class), new WorldProfileTable(plugin));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void buildsAndShowsTheRealConfigDialog() {
        assertDoesNotThrow(() -> ConfigGUI.openMainMenu(server.addPlayer("ConfigDialog"), editor));
    }
}
