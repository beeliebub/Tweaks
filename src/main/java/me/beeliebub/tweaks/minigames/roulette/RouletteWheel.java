package me.beeliebub.tweaks.minigames.roulette;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Pure rule table for the single-zero (European) wheel: pocket colour, dozen membership, the
 * physical wheel order, and the draw itself. No Bukkit types — see {@code roulette/CLAUDE.md}.
 *
 * <p>Colour and dozen are the fixed casino assignment, independent of the physical board layout —
 * the board's {@code roulette_color_red}/{@code roulette_color_black} segments are single
 * category-bet click targets, not one segment per pocket.
 *
 * <p>Colour is a deliberate house rule, not the real-world casino chart: pocket 0 is
 * {@link PocketColor#GREEN}, and every other pocket alternates strictly by parity (odd = red,
 * even = black). A genuine casino wheel is <em>not</em> a strict alternation — it doubles up at
 * three points (10/11 both black, 18/19 both red, 28/29 both black) to balance colour against
 * high/low and odd/even bets — but this server's board uses the simpler alternating rule instead.
 */
public final class RouletteWheel {

    /** Pocket colour. Pocket 0 is {@link #GREEN} and belongs to no colour bet. */
    public enum PocketColor { RED, BLACK, GREEN }

    private RouletteWheel() {
    }

    /** Colour of the given pocket (0-36): 0 is green, odd is red, even is black. */
    public static PocketColor colorOf(int pocket) {
        validatePocket(pocket);
        if (pocket == 0) {
            return PocketColor.GREEN;
        }
        return pocket % 2 == 1 ? PocketColor.RED : PocketColor.BLACK;
    }

    /** Dozen of the given pocket: 1 (1-12), 2 (13-24), 3 (25-36), or 0 for pocket 0 (no dozen). */
    public static int dozenOf(int pocket) {
        validatePocket(pocket);
        if (pocket == 0) {
            return 0;
        }
        return ((pocket - 1) / 12) + 1;
    }

    /**
     * The physical order pockets are arranged around this board's ring, starting from an
     * arbitrary rotational offset. The 2026-07-27 {@code /roulettescan} of the live build found
     * this board's physical order is plain ascending numeric order (not the canonical single-zero
     * European sequence). A travelling-glow animation can therefore step through consecutive
     * pocket numbers directly; there is no lookup table.
     */
    public static List<Integer> wheelOrder() {
        return List.of(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36);
    }

    /** Draws a pocket 0-36. {@code rng} is a parameter, not a field, so tests can pin the outcome. */
    public static int spin(RandomGenerator rng) {
        return rng.nextInt(37);
    }

    private static void validatePocket(int pocket) {
        if (pocket < 0 || pocket > 36) {
            throw new IllegalArgumentException("pocket must be 0-36, was " + pocket);
        }
    }
}
