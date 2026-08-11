package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region.RegionBounds;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegionAdminSubcommandsTest {

    @Test
    void centerMathHandlesSingleSymmetricAndAsymmetricBounds() {
        assertCentre(new RegionBounds(0, 0, 0, 0), 8.0, 8.0);
        assertCentre(new RegionBounds(-2, -2, 2, 2), 8.0, 8.0);
        assertCentre(new RegionBounds(-2, 4, 3, 7), 16.0, 96.0);
    }

    @Test
    void tpRejectsConsoleBeforeAnyWorldOrTeleportLookup() {
        Tweaks plugin = mock(Tweaks.class);
        RegionCommandContext context = new RegionCommandContext(plugin, mock(ProtectionManager.class),
                mock(RegionSelectionManager.class));
        CommandSender sender = mock(CommandSender.class);

        new RegionAdminSubcommands.Tp().execute(context, sender, new String[]{"claim"});

        verify(sender).sendMessage(org.mockito.ArgumentMatchers.any(net.kyori.adventure.text.Component.class));
    }

    private static void assertCentre(RegionBounds bounds, double expectedX, double expectedZ) {
        assertEquals(expectedX, RegionAdminSubcommands.centerBlockX(bounds));
        assertEquals(expectedZ, RegionAdminSubcommands.centerBlockZ(bounds));
    }
}
