package me.beeliebub.tweaks.minigames.roulette;

/**
 * Parses a single {@code roulette_*} scoreboard tag into its {@link Ring} and selector.
 * Ring membership always comes from the tag text, never from scanned geometry — geometry is
 * what this diagnostic scan is trying to measure, so it can't also be the thing that classifies
 * the input.
 */
final class RouletteTagParser {

    private RouletteTagParser() {}

    /** Which numeric spelling a pocket tag used, e.g. {@code roulette_03} vs {@code roulette_3}. */
    enum NumericForm {
        ZERO_PADDED,
        UNPADDED,
        NOT_APPLICABLE
    }

    record ParsedTag(Ring ring, int selector, String rawTag, NumericForm form) {}

    private static final String THIRDS_PREFIX = "roulette_thirds_";
    private static final String COLOR_RED = "roulette_color_red";
    private static final String COLOR_BLACK = "roulette_color_black";
    private static final String POCKET_PREFIX = "roulette_";

    /**
     * Parses {@code tag}. Never throws — an unrecognised or malformed {@code roulette_*} tag is a
     * finding to record in the scan output, not a crash, so it comes back as {@link Ring#UNKNOWN}.
     */
    static ParsedTag parse(String tag) {
        if (tag == null) {
            return new ParsedTag(Ring.UNKNOWN, -1, "null", NumericForm.NOT_APPLICABLE);
        }
        if (tag.startsWith(THIRDS_PREFIX)) {
            String suffix = tag.substring(THIRDS_PREFIX.length());
            Integer selector = parseIntOrNull(suffix);
            if (selector != null && selector >= 1 && selector <= 3) {
                return new ParsedTag(Ring.MIDDLE, selector, tag, NumericForm.NOT_APPLICABLE);
            }
            return new ParsedTag(Ring.UNKNOWN, -1, tag, NumericForm.NOT_APPLICABLE);
        }
        if (tag.equals(COLOR_RED) || tag.equals(COLOR_BLACK)) {
            return new ParsedTag(Ring.INNER, -1, tag, NumericForm.NOT_APPLICABLE);
        }
        if (tag.startsWith(POCKET_PREFIX)) {
            String suffix = tag.substring(POCKET_PREFIX.length());
            Integer selector = parseIntOrNull(suffix);
            if (selector != null && selector >= 0 && selector <= 36) {
                NumericForm form = (suffix.length() == 2) ? NumericForm.ZERO_PADDED : NumericForm.UNPADDED;
                return new ParsedTag(Ring.OUTER, selector, tag, form);
            }
        }
        return new ParsedTag(Ring.UNKNOWN, -1, tag, NumericForm.NOT_APPLICABLE);
    }

    private static Integer parseIntOrNull(String s) {
        if (s.isEmpty() || s.length() > 3) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return null;
            }
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
