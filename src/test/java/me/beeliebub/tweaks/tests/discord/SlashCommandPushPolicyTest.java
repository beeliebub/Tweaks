package me.beeliebub.tweaks.tests.discord;

import me.beeliebub.tweaks.discord.SlashCommandPushPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlashCommandPushPolicyTest {

    private final SlashCommandPushPolicy policy = new SlashCommandPushPolicy();

    @Test
    void unchangedResolvedChannelSkips() {
        assertEquals(SlashCommandPushPolicy.Decision.SKIP_UNCHANGED,
                policy.decide(true, true, 1, "123", "123", true));
    }

    @Test
    void unchangedUnresolvedChannelRetries() {
        assertEquals(SlashCommandPushPolicy.Decision.PUSH,
                policy.decide(true, true, 1, "123", "123", false));
    }

    @Test
    void changedChannelPushes() {
        assertEquals(SlashCommandPushPolicy.Decision.PUSH,
                policy.decide(true, true, 1, "456", "123", true));
    }

    @Test
    void notReadySkipsBeforeGuildInspection() {
        assertEquals(SlashCommandPushPolicy.Decision.SKIP_NOT_READY,
                policy.decide(false, false, 0, "123", null, false));
    }

    @Test
    void emptyGuildCacheSkips() {
        assertEquals(SlashCommandPushPolicy.Decision.SKIP_NO_GUILDS,
                policy.decide(true, true, 0, "123", null, false));
    }

    @Test
    void unchangedBlankChannelStaysDisabledWithoutRetrying() {
        assertEquals(SlashCommandPushPolicy.Decision.SKIP_UNCHANGED,
                policy.decide(true, true, 1, "", "", false));
    }
}
