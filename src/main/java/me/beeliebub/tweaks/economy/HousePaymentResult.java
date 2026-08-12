package me.beeliebub.tweaks.economy;

/** Outcome of {@link EconomyManager#applyHousePayment(java.util.UUID, String, long)}. */
public enum HousePaymentResult {
    /** The receipt did not exist yet; the recipient was credited and the receipt persisted. */
    APPLIED,
    /** A matching receipt already existed; nothing was mutated. Safe to treat as success on replay. */
    ALREADY_APPLIED,
    /** A receipt for this payment id already existed with a different amount. Nothing was mutated. */
    REJECTED_MISMATCH,
    /** The recipient's resulting whole-dollar balance would fall outside the supported range. */
    REJECTED_UNREPRESENTABLE,
    /** The receipt/balance mutation was accepted in memory but the durable write failed. */
    REJECTED_WRITE_FAILED
}
