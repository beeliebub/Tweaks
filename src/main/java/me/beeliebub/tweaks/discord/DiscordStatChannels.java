package me.beeliebub.tweaks.discord;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.lottery.LotteryManager;
import me.beeliebub.tweaks.lottery.LotteryMath;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/** Main-thread refresh logic for the optional Discord voice-channel statistics. */
public final class DiscordStatChannels {

    public static final int MIN_REFRESH_SECONDS = 300;

    private final Tweaks plugin;
    private final HouseAccount houseAccount;
    private final LotteryManager lotteryManager;
    private final DiscordAnnouncer announcer;
    private final Map<String, String> lastChannelIds = new HashMap<>();
    private final Map<String, String> lastNames = new HashMap<>();
    private final Map<String, RenameRequest> inFlight = new HashMap<>();

    public DiscordStatChannels(Tweaks plugin, HouseAccount houseAccount,
                               LotteryManager lotteryManager, DiscordAnnouncer announcer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.houseAccount = Objects.requireNonNull(houseAccount, "houseAccount");
        this.lotteryManager = Objects.requireNonNull(lotteryManager, "lotteryManager");
        this.announcer = announcer == null ? DiscordAnnouncer.NOOP : announcer;
    }

    /** Refreshes both configured channels from their live, main-thread-owned sources. */
    public void refresh() {
        String houseChannelId = configured("house-channel-id");
        if (houseChannelId.isBlank()) {
            forget("house");
        } else if (houseAccount.isLoaded()) {
            pushIfChanged("house", houseChannelId, Messages.DISCORD_STATS.houseBalance(houseAccount.balance()));
        }

        String lotteryChannelId = configured("lottery-channel-id");
        if (lotteryChannelId.isBlank()) {
            forget("lottery");
        } else if (lotteryManager.isLoaded()) {
            String name;
            LotteryMath.PotOutcome outcome = lotteryManager.currentPot();
            if (outcome instanceof LotteryMath.PotOutcome.Payable payable) {
                name = Messages.DISCORD_STATS.lotteryPot(payable.pot());
            } else {
                name = Messages.DISCORD_STATS.lotteryPotWaiting();
            }
            pushIfChanged("lottery", lotteryChannelId, name);
        }
    }

    public int refreshSeconds() {
        return refreshSeconds(plugin);
    }

    public static int refreshSeconds(Tweaks plugin) {
        return Math.max(MIN_REFRESH_SECONDS,
                plugin.getConfig().getInt("discord.stat-refresh-seconds", MIN_REFRESH_SECONDS));
    }

    private void pushIfChanged(String key, String channelId, String name) {
        if (channelId.equals(lastChannelIds.get(key)) && name.equals(lastNames.get(key))) return;
        RenameRequest request = new RenameRequest(channelId, name);
        if (request.equals(inFlight.get(key))) return;
        inFlight.put(key, request);
        try {
            announcer.renameChannel(channelId, name,
                    () -> renameSucceeded(key, request),
                    () -> renameFailed(key, request));
        } catch (Throwable throwable) {
            renameFailed(key, request);
            plugin.getLogger().log(Level.WARNING,
                    "Discord stat-channel rename failed for " + key, throwable);
        }
    }

    private void renameSucceeded(String key, RenameRequest request) {
        if (inFlight.get(key) != request) return;
        inFlight.remove(key);
        lastChannelIds.put(key, request.channelId());
        lastNames.put(key, request.name());
    }

    private void renameFailed(String key, RenameRequest request) {
        if (inFlight.get(key) == request) inFlight.remove(key);
    }

    private void forget(String key) {
        inFlight.remove(key);
        lastChannelIds.remove(key);
        lastNames.remove(key);
    }

    private String configured(String key) {
        String value = plugin.getConfig().getString("discord." + key, "");
        return value == null ? "" : value.trim();
    }

    private record RenameRequest(String channelId, String name) {
    }
}
