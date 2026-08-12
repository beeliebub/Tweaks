package me.beeliebub.tweaks.economy;

/** Outcome of {@link HousePaymentService#pay(java.util.UUID, long)}. */
public enum HousePayOutcome {
    /** The recipient was credited (or already had a matching receipt) and the journal is compact. */
    SUCCESS,
    /** The house balance is less than the requested amount; nothing was debited. */
    INSUFFICIENT_FUNDS,
    /** The recipient's balance could not accept the credit within the supported range; the house was refunded. */
    UNREPRESENTABLE_REFUNDED,
    /** A receipt already existed under this payment id with a different amount; needs a manual look. */
    NEEDS_RECONCILIATION,
    /** The house was debited but the credit write failed; retained for replay on next startup. */
    WRITE_FAILED_RETAINED,
    /** An unexpected error occurred; the journal entry (if any) is retained for replay. */
    FAILED,
    /** The house account has not finished loading/replaying yet. */
    NOT_READY,
    /** The plugin is shutting down; new payments are rejected. */
    SHUTTING_DOWN
}
