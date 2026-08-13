package me.beeliebub.tweaks.discord;

import org.bukkit.OfflinePlayer;

/** Narrow feature-facing seam for optional Discord integration. */
public interface DiscordAnnouncer {

    DiscordAnnouncer NOOP = new DiscordAnnouncer() {
        @Override
        public void announceCard(String message, int color, OfflinePlayer subject) {
        }

        @Override
        public void announceSettlement(SettlementLine line) {
        }

        @Override
        public void renameChannel(String channelId, String name) {
        }
    };

    void announceCard(String message, int color, OfflinePlayer subject);

    void announceSettlement(SettlementLine line);

    void renameChannel(String channelId, String name);

    /**
     * Requests a rename and reports the asynchronous outcome. Implementations must invoke the
     * callbacks on the server thread; the default keeps simple test and no-op implementations
     * compatible with the original fire-and-forget seam.
     */
    default void renameChannel(String channelId, String name, Runnable onSuccess, Runnable onFailure) {
        renameChannel(channelId, name);
        onSuccess.run();
    }
}
