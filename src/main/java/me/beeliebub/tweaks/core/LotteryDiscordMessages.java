package me.beeliebub.tweaks.core;

import java.text.NumberFormat;
import java.util.Locale;

/** Discord wording for lottery draw cards. This class has no DiscordSRV dependency. */
public final class LotteryDiscordMessages {

    public static final int YELLOW = 0xFFFF00;

    LotteryDiscordMessages() {
    }

    public String winner(String name, long amount) {
        return "[Lottery] " + escapeMarkdown(name) + " won " + money(amount);
    }

    private static String money(long amount) {
        return "$" + NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }

    private static String escapeMarkdown(String raw) {
        if (raw == null || raw.isEmpty()) return "unknown player";
        StringBuilder escaped = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char character = raw.charAt(i);
            if (character == '\r' || character == '\n') {
                escaped.append(' ');
                continue;
            }
            if (character == '`' || character == '*' || character == '_' || character == '~'
                    || character == '|' || character == '>' || character == '\\') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
