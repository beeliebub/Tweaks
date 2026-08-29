package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentGemItem;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The attach/detach chat feedback follows the same numeral rules as the item lore. */
class AugmentAttachMessageTest {

    private ServerMock server;
    private Tweaks plugin;
    private AugmentService augments;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        augments = new AugmentService(plugin, null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void singleLevelAugmentAttachMessageOmitsTheNumeral() {
        PlayerMock player = server.addPlayer("MendingTester");
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 1, List.of(), true);

        assertTrue(augments.attach(player, item, augments.gemItem().create(Enchantment.MENDING, 1)));

        String message = firstMessageContaining(player, "Attached");
        assertTrue(message.contains("Numismatic"), message);
        assertFalse(message.contains("Numismatic 1"), message);
    }

    @Test
    void multiLevelAugmentAttachMessageUsesARomanNumeral() {
        PlayerMock player = server.addPlayer("EfficiencyTester");
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 5, List.of(), true);

        assertTrue(augments.attach(player, item, augments.gemItem().create(Enchantment.EFFICIENCY, 5)));

        String message = firstMessageContaining(player, "Attached");
        assertTrue(message.contains("Efficiency V"), message);
        assertFalse(message.contains("Efficiency 5"), message);
    }

    @Test
    void detachMessageOmitsTheNumeralForASingleLevelAugment() {
        PlayerMock player = server.addPlayer("DetachTester");
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 1, List.of(), true);
        assertTrue(augments.attach(player, item, augments.gemItem().create(Enchantment.MENDING, 1)));
        drainMessages(player);

        assertTrue(augments.toggle(player, item, 0));

        String message = firstMessageContaining(player, "Disabled");
        assertTrue(message.contains("Numismatic"), message);
        assertFalse(message.contains("Numismatic 1"), message);
    }

    @Test
    void curseAlreadyBoundMessageCarriesNoLevel() {
        PlayerMock player = server.addPlayer("CurseTester");
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        augments.ledger().write(item, 2, List.of(), true);
        item.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);

        ItemStack gem = augments.gemItem().create(Enchantment.EFFICIENCY, 5,
                List.of(new AugmentGemItem.CurseRider(Enchantment.VANISHING_CURSE, 1)));
        assertFalse(augments.attach(player, item, gem));

        String message = firstMessageContaining(player, "already bound");
        assertFalse(message.matches(".*\\d.*"), message);
    }

    private static void drainMessages(PlayerMock player) {
        while (player.nextComponentMessage() != null) {
            // Discard feedback from an earlier step so the assertion reads the next one.
        }
    }

    private static String firstMessageContaining(PlayerMock player, String needle) {
        Component component;
        while ((component = player.nextComponentMessage()) != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(component);
            if (plain.contains(needle)) return plain;
        }
        throw new AssertionError("No message containing '" + needle + "' was sent");
    }
}
