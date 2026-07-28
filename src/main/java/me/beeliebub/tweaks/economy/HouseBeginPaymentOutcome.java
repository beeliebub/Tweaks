package me.beeliebub.tweaks.economy;

/** Outcome of {@link HouseAccount#beginPayment(String, java.util.UUID, long)}. */
public enum HouseBeginPaymentOutcome {
    /** The house was debited and a {@code DEBIT_DURABLE} journal entry was persisted. */
    ACCEPTED,
    /** The house balance is less than the requested amount; nothing was mutated. */
    INSUFFICIENT_FUNDS
}
