package me.beeliebub.tweaks.discord;

import org.bukkit.configuration.ConfigurationSection;

/** Main-thread-created Discord settings consumed by asynchronous Discord work. */
public record DiscordConfigSnapshot(String bettingChannelId, boolean bettingEnabled,
                                    String webhookName, String webhookAvatarUrl) {

    public DiscordConfigSnapshot {
        bettingChannelId = normalize(bettingChannelId);
        webhookName = normalize(webhookName);
        if (webhookName.isBlank()) webhookName = "House";
        webhookAvatarUrl = normalize(webhookAvatarUrl);
    }

    public static DiscordConfigSnapshot from(ConfigurationSection discord) {
        if (discord == null) return new DiscordConfigSnapshot("", true, "House", "");
        return new DiscordConfigSnapshot(
                discord.getString("betting-channel-id", ""),
                discord.getBoolean("betting-enabled", true),
                discord.getString("webhook-name", "House"),
                discord.getString("webhook-avatar-url", ""));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
