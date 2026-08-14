package me.beeliebub.tweaks.core;

import java.text.NumberFormat;
import java.util.Locale;

/** Wording for the optional Discord voice-channel stat names. */
public final class DiscordStatMessages {

    private static final int MAX_CHANNEL_NAME_LENGTH = 100;

    DiscordStatMessages() {
    }

    public String houseBalance(long balance) {
        return capped("House Bal: " + money(balance));
    }

    public String lotteryPot(long pot) {
        return capped("Lottery Pot: " + money(pot));
    }

    public String lotteryPotWaiting() {
        return "Lottery Pot: waiting";
    }

    private static String money(long amount) {
        return "$" + NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }

    private static String capped(String name) {
        return name.length() <= MAX_CHANNEL_NAME_LENGTH
                ? name : name.substring(0, MAX_CHANNEL_NAME_LENGTH);
    }
}
