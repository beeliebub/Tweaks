package me.beeliebub.tweaks.tests.core;

import me.beeliebub.tweaks.core.Messages;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LotteryDiscordMessagesTest {

    @Test
    void winnerUsesLotteryPrefixAndUsMoneyFormatting() {
        String discord = Messages.LOTTERY_DISCORD.winner("Winner", 1_234_567);
        String inGame = PlainTextComponentSerializer.plainText().serialize(
                me.beeliebub.tweaks.core.Messages.LOTTERY.winner("Winner", 1_234_567));

        assertTrue(discord.startsWith("[Lottery] Winner won $1,234,567"));
        assertTrue(inGame.contains("$1,234,567"));
        assertEquals(0xFFFF00, Messages.LOTTERY_DISCORD.YELLOW);
    }

    @Test
    void winnerNameMarkdownIsEscapedWithoutDiscordDependencyInCore() {
        String value = Messages.LOTTERY_DISCORD.winner("bad`*_~|>\\name\nnext", 10);

        assertEquals("[Lottery] bad\\`\\*\\_\\~\\|\\>\\\\name next won $10", value);
    }

}
