package me.beeliebub.tweaks.minigames.roulette;

/** Result of one Discord bet attempt. */
public record DiscordBetResult(DiscordBetOutcome outcome, int amount, int multiplier) {
}
