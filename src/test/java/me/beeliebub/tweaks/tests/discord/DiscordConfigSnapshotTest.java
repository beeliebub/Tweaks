package me.beeliebub.tweaks.tests.discord;

import me.beeliebub.tweaks.discord.DiscordConfigSnapshot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordConfigSnapshotTest {

    @Test
    void readsDiscordValuesFromConfigurationSection() {
        YamlConfiguration config = new YamlConfiguration();
        var discord = config.createSection("discord");
        discord.set("betting-channel-id", " 123 ");
        discord.set("betting-enabled", false);
        discord.set("webhook-name", " Casino ");
        discord.set("webhook-avatar-url", " https://example.test/avatar.png ");

        DiscordConfigSnapshot snapshot = DiscordConfigSnapshot.from(discord);

        assertEquals("123", snapshot.bettingChannelId());
        assertFalse(snapshot.bettingEnabled());
        assertEquals("Casino", snapshot.webhookName());
        assertEquals("https://example.test/avatar.png", snapshot.webhookAvatarUrl());
    }

    @Test
    void blankWebhookNameFallsBackToHouse() {
        YamlConfiguration config = new YamlConfiguration();
        var discord = config.createSection("discord");
        discord.set("webhook-name", "  ");

        DiscordConfigSnapshot snapshot = DiscordConfigSnapshot.from(discord);

        assertEquals("House", snapshot.webhookName());
        assertTrue(snapshot.bettingEnabled());
        assertEquals("", snapshot.bettingChannelId());
        assertEquals("", snapshot.webhookAvatarUrl());
    }
}
