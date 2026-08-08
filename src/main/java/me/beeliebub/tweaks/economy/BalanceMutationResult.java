package me.beeliebub.tweaks.economy;

/** Result of a player-balance mutation. */
public enum BalanceMutationResult {
    /** The requested balance change was applied and queued for persistence. */
    APPLIED,
    /** The balance could not represent the requested change without losing precision. */
    REJECTED_UNREPRESENTABLE
}
