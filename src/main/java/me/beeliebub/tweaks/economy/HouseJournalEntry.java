package me.beeliebub.tweaks.economy;

import java.util.UUID;

/** One in-flight or unresolved {@code /house pay} intent, persisted in {@code house.yml}. */
public record HouseJournalEntry(UUID recipient, long amount, HousePaymentState state, long createdAt) {
}
