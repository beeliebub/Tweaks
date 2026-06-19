package me.beeliebub.tweaks.tests.minigames.cards;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import me.beeliebub.tweaks.minigames.cards.Card;
import me.beeliebub.tweaks.minigames.cards.CardItemFactory;
import me.beeliebub.tweaks.minigames.cards.Rank;
import me.beeliebub.tweaks.minigames.cards.Suit;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the card-back color overload in {@link CardItemFactory}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>2-arg overload still produces exactly one color entry (backward compatibility).</li>
 *   <li>3-arg overload with a non-null color produces exactly two color entries.</li>
 *   <li>The second color entry in the 3-arg result matches the RGB value passed in.</li>
 *   <li>3-arg overload with {@code null} backColor behaves identically to the 2-arg overload.</li>
 *   <li>High bits beyond 0xFFFFFF in the backColor int are masked out.</li>
 * </ul>
 */
class CardItemFactoryColorTest {

    private static final Card TEST_CARD = new Card(Rank.SEVEN, Suit.CLUBS);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---- Helper ---------------------------------------------------------------

    private static CustomModelData cmd(ItemStack stack) {
        return stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
    }

    // ---- Tests ----------------------------------------------------------------

    @Test
    void twoArgOverloadProducesExactlyOneColor() {
        ItemStack stack = CardItemFactory.createCardItem(TEST_CARD, false);
        CustomModelData data = cmd(stack);
        assertNotNull(data, "CUSTOM_MODEL_DATA must be present");
        assertEquals(1, data.colors().size(),
                "2-arg overload must produce exactly one color entry (pip color only)");
    }

    @Test
    void threeArgOverloadWithNullBackColorProducesExactlyOneColor() {
        ItemStack stack = CardItemFactory.createCardItem(TEST_CARD, false, null);
        CustomModelData data = cmd(stack);
        assertNotNull(data);
        assertEquals(1, data.colors().size(),
                "3-arg overload with null backColor must produce exactly one color (pip color only)");
    }

    @Test
    void threeArgOverloadWithNonNullColorProducesTwoColors() {
        int backRgb = 0xFF8800; // orange
        ItemStack stack = CardItemFactory.createCardItem(TEST_CARD, false, backRgb);
        CustomModelData data = cmd(stack);
        assertNotNull(data);
        assertEquals(2, data.colors().size(),
                "3-arg overload with non-null backColor must produce exactly two color entries");
    }

    @Test
    void secondColorEntryMatchesSuppliedRgb() {
        int backRgb = 0xFF8800; // orange: R=255, G=136, B=0
        ItemStack stack = CardItemFactory.createCardItem(TEST_CARD, false, backRgb);
        CustomModelData data = cmd(stack);
        assertNotNull(data);

        Color second = data.colors().get(1);
        Color expected = Color.fromRGB(backRgb & 0xFFFFFF);
        assertEquals(expected.asRGB(), second.asRGB(),
                "colors[1] must match the supplied backColor RGB");
    }

    @Test
    void highBitsInBackColorAreMasked() {
        // Supply a value with bits set above 0xFFFFFF — only lower 24 bits should matter.
        int backRgb = 0x01_00_FF00; // bits above 0xFFFFFF set; effective color is 0x00FF00 (green)
        ItemStack stack = CardItemFactory.createCardItem(TEST_CARD, false, backRgb);
        CustomModelData data = cmd(stack);
        assertNotNull(data);

        Color second = data.colors().get(1);
        Color expected = Color.fromRGB(0x00FF00);
        assertEquals(expected.asRGB(), second.asRGB(),
                "High bits beyond 0xFFFFFF must be masked out when building the back color");
    }

    @Test
    void firstColorEntryIsPipColorRegardlessOfBackColor() {
        // Black pip color for a CLUBS card.
        Color expectedPip = Color.fromRGB(0x1A, 0x1A, 0x1A);
        ItemStack stack = CardItemFactory.createCardItem(TEST_CARD, false, 0xFF0000);
        CustomModelData data = cmd(stack);
        assertNotNull(data);

        Color first = data.colors().get(0);
        assertEquals(expectedPip.asRGB(), first.asRGB(),
                "colors[0] must always be the pip color, unaffected by backColor");
    }

    @Test
    void faceDownCardWithBackColorStillHasTwoColors() {
        // Verify the back-color path is not accidentally skipped for face-down cards.
        ItemStack stack = CardItemFactory.createCardItem(new Card(Rank.ACE, Suit.SPADES), true, 0x0000FF);
        CustomModelData data = cmd(stack);
        assertNotNull(data);
        assertEquals(2, data.colors().size(),
                "Face-down card with backColor must still carry two color entries");
    }
}
