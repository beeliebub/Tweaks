package me.beeliebub.tweaks.economy;

/** Result of a player-balance mutation. */
public enum BalanceMutationResult {
    /** The requested balance change was applied and queued for persistence. */
    APPLIED,
    /** The resulting whole-dollar balance would fall outside the supported range. */
    REJECTED_UNREPRESENTABLE
}
