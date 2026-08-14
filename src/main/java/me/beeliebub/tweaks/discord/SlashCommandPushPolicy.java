package me.beeliebub.tweaks.discord;

import java.util.Objects;

/** Decides whether the cached Discord slash-command state needs a new push. */
public final class SlashCommandPushPolicy {

    public Decision decide(boolean ready, boolean jdaPresent, int guildCount,
                           String configuredChannelId, String lastPushedChannelId,
                           boolean lastPushResolvedGuild) {
        if (!ready || !jdaPresent) return Decision.SKIP_NOT_READY;
        if (guildCount <= 0) return Decision.SKIP_NO_GUILDS;
        if (configuredChannelId != null && configuredChannelId.isBlank()
                && Objects.equals(configuredChannelId, lastPushedChannelId)) {
            return Decision.SKIP_UNCHANGED;
        }
        if (lastPushResolvedGuild && Objects.equals(configuredChannelId, lastPushedChannelId)) {
            return Decision.SKIP_UNCHANGED;
        }
        return Decision.PUSH;
    }

    public enum Decision {
        PUSH,
        SKIP_NOT_READY,
        SKIP_NO_GUILDS,
        SKIP_UNCHANGED
    }
}
