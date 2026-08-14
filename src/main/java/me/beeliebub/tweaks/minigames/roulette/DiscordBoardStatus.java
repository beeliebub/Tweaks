package me.beeliebub.tweaks.minigames.roulette;

/** Snapshot of the single board exposed to Discord, safe to read after a Bukkit-thread hop. */
public record DiscordBoardStatus(boolean exists, boolean active, RouletteRound.State state,
                                 int secondsRemaining, int minBet, int maxBet,
                                 LastResult lastResult) {
}
