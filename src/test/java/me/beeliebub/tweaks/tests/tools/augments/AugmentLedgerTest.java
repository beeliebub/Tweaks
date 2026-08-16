package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.AugmentGemItem;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void curseListRoundTripsAndSurvivesUnrelatedLedgerWrites() {
        AugmentLedger ledger = new AugmentLedger(plugin);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        AugmentGemItem.CurseRider curse = new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1);
        AugmentEntry entry = new AugmentEntry(NamespacedKey.minecraft("fortune"), 3, true);

        ledger.appendCurses(item, List.of(curse));
        assertTrue(!AugmentLedger.hasLedger(item));
        ledger.write(item, 4, List.of(entry), true);
        ledger.setSlots(item, 5);

        assertEquals(List.of(curse), ledger.curses(item));
        assertEquals(5, ledger.slots(item));
        assertEquals(List.of(entry), ledger.entries(item));
    }

    @Test
    void malformedCurseEntriesAreSkippedIndividually() {
        AugmentLedger ledger = new AugmentLedger(plugin);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "augment_curses"),
                PersistentDataType.LIST.strings(), List.of(
                        "v9|minecraft:vanishing_curse|1",
                        "v1|minecraft:vanishing_curse|not-a-level",
                        "v1|minecraft:vanishing_curse|1"));
        item.setItemMeta(meta);

        assertEquals(List.of(new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1)), ledger.curses(item));
    }

    @Test
    void recoveryRejectsDuplicateAttachedEntries() {
        AugmentLedger ledger = new AugmentLedger(plugin);
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "augment_attached"),
                PersistentDataType.LIST.strings(), List.of(
                        "v1|minecraft:fortune|3|true",
                        "v1|minecraft:fortune|3|false"));
        item.setItemMeta(meta);

        assertEquals(List.of(
                new AugmentEntry(NamespacedKey.minecraft("fortune"), 3, true),
                new AugmentEntry(NamespacedKey.minecraft("fortune"), 3, false)), ledger.entries(item));
        assertTrue(!augments.ledgerStateValid(item));
    }

    @Test
    void recoveryRejectsCursesStoredAsAttachedAugments() {
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "augment_attached"),
                PersistentDataType.LIST.strings(), List.of("v1|minecraft:binding_curse|1|true"));
        item.setItemMeta(meta);

        assertTrue(!augments.ledgerStateValid(item));
    }

    @Test
    void writerRefusesTheEntryAfterTheSupportedBound() {
        AugmentLedger ledger = new AugmentLedger(plugin);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        List<AugmentEntry> oversized = new ArrayList<>();
        for (int i = 0; i <= AugmentLedger.MAX_ATTACHED_ENTRIES; i++) {
            oversized.add(new AugmentEntry(NamespacedKey.minecraft("fortune"), 3, true));
        }

        ledger.write(item, 1, oversized, true);

        assertFalse(AugmentLedger.hasLedger(item));
    }

    @Test
    void serviceRejectsPurchasedSlotsBeyondTheCurrentCapacity() {
        AugmentLedger ledger = new AugmentLedger(plugin);
        AugmentService augments = new AugmentService(plugin, null);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);

        ledger.write(item, Integer.MAX_VALUE, List.of(), true);

        assertFalse(augments.ledgerStateValid(item));
    }

    @Test
    void curseAppendRefusesOverflowWithoutPartialWrite() {
        AugmentLedger ledger = new AugmentLedger(plugin);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        List<String> existing = new ArrayList<>();
        for (int i = 0; i < AugmentLedger.MAX_CURSE_ENTRIES; i++) {
            existing.add("v1|minecraft:vanishing_curse|1");
        }
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "augment_curses"),
                PersistentDataType.LIST.strings(), existing);
        item.setItemMeta(meta);

        assertFalse(ledger.appendCurses(item,
                List.of(new AugmentGemItem.CurseRider(Enchantment.BINDING_CURSE, 1))));
        assertEquals(existing, item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "augment_curses"), PersistentDataType.LIST.strings()));
    }
}
