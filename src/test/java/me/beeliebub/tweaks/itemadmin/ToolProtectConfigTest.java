package me.beeliebub.tweaks.itemadmin;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

// Lives in the production package (not the tests.* mirror, where ToolProtectCommandTest already
// lives) because it exercises ToolProtectCommand's package-private config-knob accessors - see
// the root CLAUDE.md's "Test Layout" section for this project's documented exception.
class ToolProtectConfigTest {

    private Tweaks plugin;
    private ToolProtectCommand command;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        command = new ToolProtectCommand(plugin, mock(QualityRegistry.class));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defaultThresholdDefaultsToBundledOneHundred() {
        assertEquals(100, command.defaultThreshold());
    }

    @Test
    void defaultThresholdLiveEditTakesEffectWithoutReconstruction() {
        plugin.getConfig().set("itemadmin.tool-protect.default-threshold", 250);
        assertEquals(250, command.defaultThreshold());
    }

    @Test
    void defaultThresholdClampsBelowOneToOne() {
        plugin.getConfig().set("itemadmin.tool-protect.default-threshold", 0);
        assertEquals(1, command.defaultThreshold());
    }

    @Test
    void warnCooldownMsDefaultsToBundledTwoThousand() {
        assertEquals(2000L, command.warnCooldownMs());
    }

    @Test
    void warnCooldownMsLiveEditTakesEffectWithoutReconstruction() {
        plugin.getConfig().set("itemadmin.tool-protect.warn-cooldown-ms", 500);
        assertEquals(500L, command.warnCooldownMs());
    }

    @Test
    void warnCooldownMsClampsNegativeToZero() {
        plugin.getConfig().set("itemadmin.tool-protect.warn-cooldown-ms", -10);
        assertEquals(0L, command.warnCooldownMs());
    }
}
