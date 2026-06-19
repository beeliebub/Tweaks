package me.beeliebub.tweaks.minigames.cards;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Bukkit-aware factory that converts a {@link Card} model into a display
 * {@link ItemStack} for use by in-world card renderers.
 *
 * <p>The {@link Card}, {@link Rank}, {@link Suit}, and {@link Deck} model types
 * remain Bukkit-free; this class is the sole rendering layer for the cards package.
 */
public final class CardItemFactory {

    /** Item-model namespace for the card models (DiceRP-style pack). */
    private static final String CARD_NAMESPACE = "dqc.cards";

    private CardItemFactory() {}

    /**
     * Build the display {@link ItemStack} for {@code card}.
     *
     * <ul>
     *   <li>Sets the {@code dqc.cards:card} item model.</li>
     *   <li>Attaches {@code CUSTOM_MODEL_DATA} with suit token, rank token,
     *       face-down flag, and pip color — always present, including on face cards,
     *       so J/Q/K still render their suit symbols.</li>
     *   <li>For face-up J/Q/K cards only: attaches an id-only {@code PROFILE} and
     *       a {@code TOOLTIP_DISPLAY} that hides the profile line. The profile is
     *       omitted on face-down cards to prevent clients inferring the hidden card
     *       is a face card from the visible {@link ItemStack} data.</li>
     * </ul>
     *
     * <p>Delegates to {@link #createCardItem(Card, boolean, Integer)} with a {@code null}
     * back color, meaning the resource pack uses its default card-back design.
     *
     * @param card     the card to render
     * @param faceDown {@code true} to render the card back (dealer hole card)
     * @return a fully configured {@link ItemStack} ready for an {@link org.bukkit.entity.ItemDisplay}
     */
    public static ItemStack createCardItem(Card card, boolean faceDown) {
        return createCardItem(card, faceDown, null);
    }

    /**
     * Build the display {@link ItemStack} for {@code card}, optionally with a custom card-back color.
     *
     * <p>When {@code backColor} is non-null it is appended as the second entry in the
     * {@code CUSTOM_MODEL_DATA} colors array ({@code colors:[pipColor, backColor]}), giving the
     * resource pack a second tint slot to colorize the card back.  When {@code backColor} is
     * {@code null} the array contains only the pip color (identical to pre-existing behavior).
     *
     * @param card      the card to render
     * @param faceDown  {@code true} to render the card back (dealer hole card)
     * @param backColor optional RGB integer for the card-back tint (e.g. {@code 0xFF8800}),
     *                  or {@code null} to use the pack's default back color
     * @return a fully configured {@link ItemStack} ready for an {@link org.bukkit.entity.ItemDisplay}
     */
    public static ItemStack createCardItem(Card card, boolean faceDown, Integer backColor) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(CARD_NAMESPACE, "card"));

        Color pipColor = card.isRed()
                ? Color.fromRGB(0xD4, 0x00, 0x00)
                : Color.fromRGB(0x1A, 0x1A, 0x1A);

        CustomModelData.Builder cmd = CustomModelData.customModelData()
                .addString(card.suitToken())
                .addString(card.rankToken())
                .addFlag(faceDown)
                .addColor(pipColor);

        if (backColor != null) {
            cmd.addColor(Color.fromRGB(backColor & 0xFFFFFF));
        }

        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, cmd.build());

        // Face-card cosmetics: attach the matching player skull profile for J/Q/K.
        // CUSTOM_MODEL_DATA (suit symbols) is preserved — only the profile is added.
        // Skip face-down cards: the ItemStack is sent in full to clients, so attaching
        // the PROFILE to the dealer's hidden hole card would leak that it is a face card.
        if (!faceDown
                && (card.rank() == Rank.KING || card.rank() == Rank.QUEEN || card.rank() == Rank.JACK)) {
            UUID profileUuid = faceCardProfileId(card.rank());
            if (profileUuid != null) {
                stack.setData(DataComponentTypes.PROFILE,
                        ResolvableProfile.resolvableProfile()
                                .uuid(profileUuid)
                                .build());
                stack.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                        TooltipDisplay.tooltipDisplay()
                                .addHiddenComponents(DataComponentTypes.PROFILE)
                                .build());
            }
        }

        return stack;
    }

    /**
     * Returns the id-only profile UUID for a face-card rank, or {@code null} for
     * non-face ranks. UUIDs are derived from the NBT integer-array encoding
     * {@code {id:[I; a,b,c,d]}} used by Minecraft to store player profiles.
     *
     * <ul>
     *   <li>KING  — Videowiz92</li>
     *   <li>QUEEN — Skylexia</li>
     *   <li>JACK  — beeliebub</li>
     * </ul>
     */
    private static UUID faceCardProfileId(Rank rank) {
        return switch (rank) {
            case KING  -> profileId(1253964080,  1970949871, -1380893507,  -871262800);
            case QUEEN -> profileId(1017036314, -1869069315, -1140022423, -1217914950);
            case JACK  -> profileId(-312656436,  1106919611, -1572456359,  1844848245);
            default    -> null;
        };
    }

    /**
     * Converts four signed 32-bit integers (Minecraft's NBT integer-array UUID encoding)
     * into a {@link UUID}.
     *
     * <p>Minecraft encodes a UUID as {@code {id:[I; a,b,c,d]}} where the 128-bit value is
     * split into four big-endian 32-bit chunks: most-significant word first.
     *
     * @param a bits 127-96 (most significant)
     * @param b bits  95-64
     * @param c bits  63-32
     * @param d bits  31-0  (least significant)
     */
    private static UUID profileId(int a, int b, int c, int d) {
        return new UUID(
                ((long) a << 32) | (b & 0xFFFFFFFFL),
                ((long) c << 32) | (d & 0xFFFFFFFFL));
    }
}
