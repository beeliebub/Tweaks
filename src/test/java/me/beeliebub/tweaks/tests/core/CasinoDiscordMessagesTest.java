package me.beeliebub.tweaks.tests.core;

import me.beeliebub.tweaks.core.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoDiscordMessagesTest {

    @Test
    void blackjackLinesCarrySignedNetAndNaturalSuffix() {
        String win = Messages.CASINO_DISCORD.blackjackHand("Alice", 500, false);
        String natural = Messages.CASINO_DISCORD.blackjackHand("Alice", 750, true);
        String loss = Messages.CASINO_DISCORD.blackjackHand("Alice", -100, false);

        assertEquals("[Blackjack] Alice +$500", win);
        assertTrue(natural.endsWith("+$750 (Blackjack!)"));
        assertEquals("[Blackjack] Alice -$100", loss);
        assertFalse(loss.contains("+-"));
    }

    @Test
    void rouletteBreakEvenIsNeutralAndNamesCannotEscapeFence() {
        String even = Messages.CASINO_DISCORD.rouletteRound("A`\n+ forged", 0);
        String forfeited = Messages.CASINO_DISCORD.blackjackForfeited("Alice", -100);

        assertEquals("[Roulette] A+ forged $0", even);
        assertEquals("[Blackjack] Alice -$100 (forfeited)", forfeited);
    }
}
