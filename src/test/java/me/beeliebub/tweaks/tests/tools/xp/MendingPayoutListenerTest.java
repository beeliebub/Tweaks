package me.beeliebub.tweaks.tests.tools.xp;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.tools.xp.MendingPayoutListener;
import me.beeliebub.tweaks.tools.xp.XpSettings;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.Mockito;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MendingPayoutListenerTest {

    private Tweaks plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void mendIsCancelledWhenPayoutIsEnabled() {
        XpSettings settings = new XpSettings(plugin);
        MendingPayoutListener listener = new MendingPayoutListener(plugin,
                Mockito.mock(EconomyManager.class), settings);
        PlayerItemMendEvent event = Mockito.mock(PlayerItemMendEvent.class);

        listener.onItemMend(event);

        verify(event).setCancelled(true);
    }

    @Test
    void mendRemainsVanillaWhenPayoutIsDisabled() {
        plugin.getConfig().set("xp.mending.enabled", false);
        XpSettings settings = new XpSettings(plugin);
        MendingPayoutListener listener = new MendingPayoutListener(plugin,
                Mockito.mock(EconomyManager.class), settings);
        PlayerItemMendEvent event = Mockito.mock(PlayerItemMendEvent.class);

        listener.onItemMend(event);

        verify(event, never()).setCancelled(true);
    }
}
