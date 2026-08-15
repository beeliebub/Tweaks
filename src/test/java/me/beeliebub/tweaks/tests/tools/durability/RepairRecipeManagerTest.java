package me.beeliebub.tweaks.tests.tools.durability;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.durability.RepairKitItem;
import me.beeliebub.tweaks.tools.durability.RepairRecipeManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairRecipeManagerTest {

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
    void rejectsAnIngredientSignatureAlreadyOwnedByAnotherRecipe() {
        Bukkit.removeRecipe(new NamespacedKey(plugin, "repair_kit"));
        ShapedRecipe existing = new ShapedRecipe(
                new NamespacedKey(plugin, "same_ingredients"), new ItemStack(Material.PAPER));
        existing.shape("AAA", "ABA", "AAA")
                .setIngredient('A', Material.IRON_INGOT)
                .setIngredient('B', Material.NETHER_STAR);
        assertTrue(Bukkit.addRecipe(existing));

        RepairRecipeManager manager = new RepairRecipeManager(plugin, new RepairKitItem(plugin));

        assertFalse(manager.registerConfigured());
    }
}
