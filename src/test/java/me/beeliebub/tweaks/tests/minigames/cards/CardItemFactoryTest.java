package me.beeliebub.tweaks.tests.minigames.cards;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.beeliebub.tweaks.minigames.cards.Card;
import me.beeliebub.tweaks.minigames.cards.CardItemFactory;
import me.beeliebub.tweaks.minigames.cards.Rank;
import me.beeliebub.tweaks.minigames.cards.Suit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for {@link CardItemFactory}. Guards the player-specified
 * face-card profile identities, the face-up-only / hole-card guard, and that the
 * suit-symbol model data is always present (so face cards keep their suit symbols).
 */
class CardItemFactoryTest {

    // Expected UUIDs decoded from the NBT {id:[I; a,b,c,d]} int arrays supplied for each player.
    private static final UUID KING_VIDEOWIZ92 = UUID.fromString("4abdf930-757a-4eef-adb1-3cbdcc1195b0");
    private static final UUID QUEEN_SKYLEXIA  = UUID.fromString("3c9ebe1a-9098-43fd-bc0c-a369b76817ba");
    private static final UUID JACK_BEELIEBUB  = UUID.fromString("ed5d3dcc-41fa-40bb-a246-38596df62675");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static UUID profileUuid(ItemStack stack) {
        ResolvableProfile profile = stack.getData(DataComponentTypes.PROFILE);
        return profile == null ? null : profile.uuid();
    }

    @Test
    void cardsAreRenderedAsPaper() {
        ItemStack stack = CardItemFactory.createCardItem(new Card(Rank.SEVEN, Suit.CLUBS), false);
        assertEquals(Material.PAPER, stack.getType());
    }

    @Test
    void numberCardsCarrySuitSymbolDataButNoProfile() {
        ItemStack stack = CardItemFactory.createCardItem(new Card(Rank.TEN, Suit.HEARTS), false);
        assertTrue(stack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA),
                "Number cards must carry suit/rank model data");
        assertFalse(stack.hasData(DataComponentTypes.PROFILE),
                "Number cards must not carry a player profile");
    }

    @Test
    void faceCardsCarryCorrectProfileAndKeepSuitSymbols() {
        ItemStack king = CardItemFactory.createCardItem(new Card(Rank.KING, Suit.SPADES), false);
        ItemStack queen = CardItemFactory.createCardItem(new Card(Rank.QUEEN, Suit.DIAMONDS), false);
        ItemStack jack = CardItemFactory.createCardItem(new Card(Rank.JACK, Suit.CLUBS), false);

        assertEquals(KING_VIDEOWIZ92, profileUuid(king), "King -> Videowiz92");
        assertEquals(QUEEN_SKYLEXIA, profileUuid(queen), "Queen -> Skylexia");
        assertEquals(JACK_BEELIEBUB, profileUuid(jack), "Jack -> beeliebub");

        // Suit symbols (CUSTOM_MODEL_DATA) must still be present on face cards.
        assertTrue(king.hasData(DataComponentTypes.CUSTOM_MODEL_DATA));
        // Profile line must be hidden in the tooltip.
        assertTrue(king.hasData(DataComponentTypes.TOOLTIP_DISPLAY),
                "Face cards must hide the profile in their tooltip");
    }

    @Test
    void faceDownFaceCardOmitsProfileToAvoidLeak() {
        // The dealer's face-down hole card must NOT carry the profile, or clients could
        // read the ItemStack and infer the hidden card is a face card.
        ItemStack holeCard = CardItemFactory.createCardItem(new Card(Rank.KING, Suit.SPADES), true);
        assertFalse(holeCard.hasData(DataComponentTypes.PROFILE),
                "Face-down face card must not carry a profile");
    }
}
