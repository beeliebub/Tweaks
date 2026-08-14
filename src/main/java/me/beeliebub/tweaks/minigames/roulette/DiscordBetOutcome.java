package me.beeliebub.tweaks.minigames.roulette;

/** Stable outcome codes returned by the main-thread Discord betting gateway. */
public enum DiscordBetOutcome {
    NO_DESIGNATED_BOARD,
    BOARD_UNAVAILABLE,
    BETTING_CLOSED,
    INVALID_BET,
    EXPOSURE_LIMIT,
    INSUFFICIENT_FUNDS,
    BALANCE_UNREPRESENTABLE,
    REJECTED,
    PLACED
}
