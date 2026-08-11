package me.beeliebub.tweaks.economy;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.ranks.RankManager;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

/**
 * Grants daily login rewards on join.
 *
 * Reward = base * streakMultiplier(day) + rankManager.getRankMultiplierBonus(rank).
 * Requires a clean 24-hour gap since last login; resets the streak after 48 hours of absence.
 * Tab-list rendering is handled exclusively by TabManager.
 */
public class EconomyListener implements Listener {

    private static final long DAY_MS  = 24L * 60 * 60 * 1000;
    private static final long TWO_DAY_MS = 48L * 60 * 60 * 1000;
    private static final int  MAX_STREAK = 7;

    private final Tweaks plugin;
    private final EconomyManager economyManager;
    private final RankManager rankManager;

    public EconomyListener(Tweaks plugin, EconomyManager economyManager, RankManager rankManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.rankManager = rankManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Ensure data is in cache before any reads.
        economyManager.loadPlayer(uuid);

        long lastLogin = economyManager.getLastLogin(uuid);
        long now = System.currentTimeMillis();

        // Grant a daily reward only when 24 hours have elapsed since the last login.
        boolean firstEver = lastLogin == 0L;
        if (firstEver || now - lastLogin >= DAY_MS) {
            grantDailyReward(player, uuid, lastLogin, now, firstEver);
        }
    }

    // ---- Private helpers ----------------------------------------------------

    /**
     * Calculates and applies the daily login reward, then notifies the player.
     */
    private void grantDailyReward(Player player, UUID uuid, long lastLogin, long now,
                                  boolean firstEver) {
        // Determine new streak.
        int newStreak;
        if (firstEver) {
            newStreak = 1;
        } else if (now - lastLogin > TWO_DAY_MS) {
            newStreak = 1;
        } else {
            newStreak = Math.min(economyManager.getLoginStreak(uuid) + 1, MAX_STREAK);
        }

        // Calculate reward.
        double base = plugin.getConfig().getDouble("economy.daily-reward-base", 100.0);
        double multiplier = plugin.getConfig().getDouble(
                "economy.streak-multipliers." + newStreak, 1.0);
        double rankBonus = rankManager.getRankMultiplierBonus(economyManager.getRank(uuid));
        double reward = base * multiplier + rankBonus;

        // Apply reward and update tracking fields.
        economyManager.addBalance(uuid, reward);
        economyManager.setLoginStreak(uuid, newStreak);
        economyManager.setLastLogin(uuid, now);

        // Notify the player.
        player.sendMessage(Messages.dailyReward(BalanceCommand.formatBalance(reward), newStreak));
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) {
            String name = player.getName();
            eventLog.log(LoggingPaths.ECONOMY_DAILY_REWARD, () -> "[Economy] "
                    + ConsoleEventLog.actorLabel(name, uuid) + " claimed daily reward: "
                    + BalanceCommand.formatBalance(reward) + " (streak " + newStreak + ")");
        }
    }

}
