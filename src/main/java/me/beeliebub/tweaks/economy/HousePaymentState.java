package me.beeliebub.tweaks.economy;

/** Durable state of a {@link HouseJournalEntry}. */
public enum HousePaymentState {
    /** The house has been debited; the recipient credit has not yet been confirmed durable. */
    DEBIT_DURABLE,
    /** A receipt existed with a different amount than this entry records. Terminal; never replayed. */
    NEEDS_RECONCILIATION
}
