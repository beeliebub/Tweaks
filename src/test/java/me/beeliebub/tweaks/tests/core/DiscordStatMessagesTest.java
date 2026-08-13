package me.beeliebub.tweaks.tests.core;

import me.beeliebub.tweaks.core.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordStatMessagesTest {

    @Test
    void usesSeparatedUsCurrencyAndWaitingState() {
        assertEquals("House Bal: $113,053", Messages.DISCORD_STATS.houseBalance(113_053));
        assertEquals("Lottery Pot: $25,000", Messages.DISCORD_STATS.lotteryPot(25_000));
        assertEquals("Lottery Pot: waiting", Messages.DISCORD_STATS.lotteryPotWaiting());
    }

    @Test
    void channelNameNeverExceedsDiscordLimit() {
        String name = Messages.DISCORD_STATS.houseBalance(Long.MAX_VALUE);

        assertTrue(name.length() <= 100);
    }
}
