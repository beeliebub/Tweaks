package me.beeliebub.tweaks.tests;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ServicesTest {

    @Test
    void strictGetterThrowsWhenFieldNotYetPublished() {
        Services services = new Services();
        assertThrows(IllegalStateException.class, services::economyManager);
    }

    @Test
    void setterPublishesFieldForStrictGetter() {
        Services services = new Services();
        EconomyManager economyManager = mock(EconomyManager.class);
        services.setEconomyManager(economyManager);
        assertSame(economyManager, services.economyManager());
    }

    @Test
    void houseAccountIsPublishedThroughWriteOnceServiceSlot() {
        Services services = new Services();
        HouseAccount houseAccount = mock(HouseAccount.class);
        services.setHouseAccount(houseAccount);
        assertSame(houseAccount, services.houseAccount());
        assertThrows(IllegalStateException.class, () -> services.setHouseAccount(mock(HouseAccount.class)));
    }

    @Test
    void setterThrowsOnSecondCall() {
        Services services = new Services();
        services.setProtectionManager(mock(ProtectionManager.class));
        assertThrows(IllegalStateException.class, () -> services.setProtectionManager(mock(ProtectionManager.class)));
    }

    @Test
    void protectionSelectionToolDefaultsToStoneAxeAndAllowsOverwrite() {
        Services services = new Services();
        assertEquals(Material.STONE_AXE, services.protectionSelectionTool());

        services.setProtectionSelectionTool(Material.DIAMOND_AXE);
        assertEquals(Material.DIAMOND_AXE, services.protectionSelectionTool());

        // Unlike every other field, this setter is not write-once - a bad config value
        // falls back to the default, so a second call must not throw.
        services.setProtectionSelectionTool(Material.STONE_AXE);
        assertEquals(Material.STONE_AXE, services.protectionSelectionTool());
    }
}
