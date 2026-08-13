package me.beeliebub.tweaks.core;

import java.text.NumberFormat;
import java.util.Locale;

/** Discord wording for blackjack and roulette settlement lines. */
public final class CasinoDiscordMessages {

    private static final int MAX_NAME_LENGTH = 64;

    CasinoDiscordMessages() {
    }

    public String blackjackHand(String name, long net, boolean natural) {
        String suffix = natural ? " (Blackjack!)" : "";
        return "[Blackjack] " + sanitizeName(name) + " " + signedMoney(net) + suffix;
    }

    public String blackjackForfeited(String name, long net) {
        return "[Blackjack] " + sanitizeName(name) + " " + signedMoney(net) + " (forfeited)";
    }

    public String rouletteRound(String name, long net) {
        return "[Roulette] " + sanitizeName(name) + " " + signedMoney(net);
    }

    private static String signedMoney(long amount) {
        if (amount > 0) return "+" + money(amount);
        if (amount < 0) {
            // Avoid overflow when formatting Long.MIN_VALUE: NumberFormat formats its
            // magnitude as a signed value, so strip only the leading minus after formatting.
            String formatted = money(amount);
            String magnitude = formatted.startsWith("$-") ? formatted.substring(2)
                    : formatted.startsWith("-") ? formatted.substring(1) : formatted;
            return "-$" + magnitude;
        }
        return "$0";
    }

    private static String money(long amount) {
        return "$" + NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }

    private static String sanitizeName(String raw) {
        if (raw == null || raw.isBlank()) return "unknown player";
        String sanitized = raw.replace("`", "").replace("\r", "").replace("\n", "");
        if (sanitized.isBlank()) return "unknown player";
        if (sanitized.length() > MAX_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_NAME_LENGTH);
        }
        return sanitized;
    }
}
