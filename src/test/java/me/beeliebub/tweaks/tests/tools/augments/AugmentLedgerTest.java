package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AugmentLedgerTest {

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
    void roundTripPreservesSlotsEntriesAndMigrationMarker() {
        AugmentLedger ledger = new AugmentLedger(plugin);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        AugmentEntry entry = new AugmentEntry(NamespacedKey.minecraft("fortune"), 3, true);

        ledger.write(item, 4, List.of(entry), true);

        assertEquals(4, ledger.slots(item));
        assertTrue(ledger.migrated(item));
        assertEquals(List.of(entry), ledger.entries(item));
    }

    @Test
    void malformedAndUnknownVersionsAreSkippedWithoutDiscardingValidEntries() {
        AugmentLedger ledger = new AugmentLedger(plugin);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "augment_attached"),
                PersistentDataType.LIST.strings(), List.of(
                        "v9|minecraft:fortune|3|true",
                        "v1|minecraft:fortune|not-a-level|true",
                        "v1|minecraft:fortune|2|false"));
        item.setItemMeta(meta);

        assertEquals(List.of(new AugmentEntry(NamespacedKey.minecraft("fortune"), 2, false)),
                ledger.entries(item));
    }
}
