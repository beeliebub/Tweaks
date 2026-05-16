package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;

class ProtectionKeysTest {

    @Test
    void initConstructsRegionPointersKeyAgainstSuppliedPlugin() {
        Tweaks plugin = mock(Tweaks.class);
        try (MockedConstruction<NamespacedKey> mocked =
                     mockConstruction(NamespacedKey.class, (instance, ctx) -> {
                         assertSame(plugin, ctx.arguments().get(0));
                         assertEquals("region_pointers", ctx.arguments().get(1));
                     })) {
            ProtectionKeys.init(plugin);
            assertNotNull(ProtectionKeys.regionPointers());
            assertEquals(1, mocked.constructed().size());
        }
    }
}
