package me.beeliebub.tweaks.tests.tools.durability;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentEntry;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.durability.DurabilityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurabilityLoreMarkerTest {

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
    void markerRoundTripPreservesNamesAugmentLoreAndDoesNotDuplicate() {
        DurabilityService durability = new DurabilityService(plugin);
        AugmentService augments = new AugmentService(plugin, null,
                durability::ensureStamped, durability::refreshLoreTail);
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        Damageable meta = (Damageable) item.getItemMeta();
        meta.displayName(Component.text("Renamed tool"));
        meta.lore(List.of(Component.text("Foreign lore")));
        item.setItemMeta(meta);
        augments.ledger().write(item, 1,
                List.of(new AugmentEntry(NamespacedKey.minecraft("efficiency"), 5, true)), true);
        augments.updateLore(item);

        setDamage(item, durability.maxDamage(item) - 1);
        durability.ensureStamped(item);
        durability.ensureStamped(item);
        durability.ensureStamped(item);

        assertEquals(1, markerCount(item, "Out of durability"));
        assertTrue(lastLine(item).contains("Out of durability"));
        assertEquals(Component.text("Renamed tool"), item.getItemMeta().displayName());
        assertTrue(plainLore(item).contains("Efficiency 5"));
        assertTrue(plainLore(item).contains("Foreign lore"));

        assertTrue(durability.repair(item));
        assertEquals(0, markerCount(item, "Out of durability"));
        assertEquals(Component.text("Renamed tool"), item.getItemMeta().displayName());
        assertTrue(plainLore(item).contains("Efficiency 5"));
    }

    @Test
    void augmentLoreRefreshKeepsAnUnchangedMarkerAtTheTail() {
        DurabilityService durability = new DurabilityService(plugin);
        AugmentService augments = new AugmentService(plugin, null,
                durability::ensureStamped, durability::refreshLoreTail);
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        augments.ledger().write(item, 1, List.of(), true);
        augments.updateLore(item);
        setDamage(item, durability.maxDamage(item) - 1);
        durability.ensureStamped(item);

        augments.updateLore(item);

        assertEquals(1, markerCount(item, "Out of durability"));
        assertTrue(lastLine(item).contains("Out of durability"));
    }

    @Test
    void terminalAndRepairableMarkersUseDifferentWordings() {
        DurabilityService durability = new DurabilityService(plugin);
        ItemStack repairable = new ItemStack(Material.IRON_PICKAXE);
        new AugmentLedger(plugin).write(repairable, 0, List.of(), true);
        durability.ensureStamped(repairable);
        setDamage(repairable, durability.maxDamage(repairable) - 1);
        durability.ensureStamped(repairable);

        plugin.getConfig().set("tools.repair-kit.max-tier", 1);
        ItemStack terminal = new ItemStack(Material.IRON_PICKAXE);
        new AugmentLedger(plugin).write(terminal, 0, List.of(), true);
        durability.ensureStamped(terminal);
        setDamage(terminal, durability.maxDamage(terminal) - 1);
        assertTrue(durability.repair(terminal));
        setDamage(terminal, durability.maxDamage(terminal) - 1);
        durability.ensureStamped(terminal);

        assertTrue(plainLore(repairable).contains("use a Repair Kit"));
        assertTrue(plainLore(terminal).contains("cannot be repaired further"));
    }

    @Test
    void malformedOwnershipLeavesExistingLoreUntouched() {
        DurabilityService durability = new DurabilityService(plugin);
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        Damageable meta = (Damageable) item.getItemMeta();
        meta.lore(List.of(Component.text("Foreign lore")));
        NamespacedKey ownedLoreKey = new NamespacedKey(plugin, "durability_owned_lore");
        meta.getPersistentDataContainer().set(ownedLoreKey, PersistentDataType.STRING, "malformed");
        item.setItemMeta(meta);
        List<Component> before = item.getItemMeta().lore();

        new AugmentLedger(plugin).write(item, 0, List.of(), true);
        durability.ensureStamped(item);

        assertEquals(before, item.getItemMeta().lore());
        assertEquals("malformed", item.getItemMeta().getPersistentDataContainer()
                .get(ownedLoreKey, PersistentDataType.STRING));
    }

    private static void setDamage(ItemStack item, int damage) {
        item.setData(DataComponentTypes.DAMAGE, damage);
    }

    private static int markerCount(ItemStack item, String text) {
        return (int) plainLore(item).lines().filter(line -> line.contains(text)).count();
    }

    private static String lastLine(ItemStack item) {
        List<Component> lore = item.getItemMeta().lore();
        return lore == null || lore.isEmpty() ? "" : PlainTextComponentSerializer.plainText().serialize(lore.getLast());
    }

    private static String plainLore(ItemStack item) {
        List<Component> lore = item.getItemMeta().lore();
        return lore == null ? "" : lore.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
