package me.beeliebub.tweaks.tests.core;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The augment gem-first list and the Repair Kit list both label a tool by its {@code /rename} name
 * when it has one, and otherwise by its ordinary item name rather than the raw material enum.
 */
class ToolMenuNamingTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        MockBukkit.load(Tweaks.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void renamedToolShowsItsRenameInBothMenus() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Old Faithful"));
        item.setItemMeta(meta);

        assertTrue(plain(Messages.TOOLS.augmentTargetItem(item)).contains("Old Faithful"));
        assertTrue(plain(Messages.TOOLS.repairKitTargetName(item)).contains("Old Faithful"));
    }

    @Test
    void unrenamedToolFallsBackToItsOrdinaryItemNameInBothMenus() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);

        TranslatableComponent augmentLabel =
                assertInstanceOf(TranslatableComponent.class, Messages.TOOLS.augmentTargetItem(item));
        assertTrue(augmentLabel.key().contains("diamond_pickaxe"));

        TranslatableComponent repairLabel =
                assertInstanceOf(TranslatableComponent.class, Messages.TOOLS.repairKitTargetName(item));
        assertTrue(repairLabel.key().contains("diamond_pickaxe"));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
