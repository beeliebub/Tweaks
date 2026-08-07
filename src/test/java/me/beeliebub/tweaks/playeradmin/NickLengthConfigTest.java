package me.beeliebub.tweaks.playeradmin;

import me.beeliebub.tweaks.Tweaks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code maxNickLength()} is package-private, hence this test lives beside production code rather
 * than under {@code tests/} — see the root {@code CLAUDE.md}'s Test Layout note, matching
 * {@code FlyWorldsLiveTest} in this same package.
 */
class NickLengthConfigTest {

    private Tweaks plugin;
    private PlayerAdminManager manager;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        manager = new PlayerAdminManager(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defaultsToTwentyFour() {
        assertEquals(24, manager.maxNickLength());
    }

    @Test
    void editingAtRuntimeTakesEffectWithoutReconstruction() {
        plugin.getConfig().set("playeradmin.max-nick-length", 10);
        assertEquals(10, manager.maxNickLength(),
                "a /tconfig edit to playeradmin.max-nick-length must take effect immediately");

        plugin.getConfig().set("playeradmin.max-nick-length", 32);
        assertEquals(32, manager.maxNickLength());
    }
}
