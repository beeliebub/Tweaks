package me.beeliebub.tweaks.itemadmin;

import me.beeliebub.tweaks.Tweaks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Lives in the production package (not the tests.* mirror, where GuiCopyCommandTest already
// lives) because it exercises GuiCopyCommand#maxDistance, a package-private accessor - see the
// root CLAUDE.md's "Test Layout" section for this project's documented exception.
class GuiCopyDistanceConfigTest {

    private Tweaks plugin;
    private GuiCopyCommand command;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        command = new GuiCopyCommand(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defaultsToBundledEight() {
        assertEquals(8, command.maxDistance());
    }

    @Test
    void liveEditTakesEffectWithoutReconstruction() {
        plugin.getConfig().set("itemadmin.gui-copy.max-distance", 20);
        assertEquals(20, command.maxDistance());
    }

    @Test
    void clampsBelowOneToOne() {
        plugin.getConfig().set("itemadmin.gui-copy.max-distance", 0);
        assertEquals(1, command.maxDistance());

        plugin.getConfig().set("itemadmin.gui-copy.max-distance", -5);
        assertEquals(1, command.maxDistance());
    }

    @Test
    void clampsAboveSixtyFourToSixtyFour() {
        plugin.getConfig().set("itemadmin.gui-copy.max-distance", 1000);
        assertEquals(64, command.maxDistance());
    }
}
