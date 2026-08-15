package me.beeliebub.tweaks.tests.ranks;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.ranks.RankEditGUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RankEditGuiDialogTest {

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
    void buildsAndShowsTheRealRankEditorDialog() {
        assertDoesNotThrow(() -> RankEditGUI.openRankList(server.addPlayer("RankDialog"),
                plugin.getRankManager()));
    }
}
