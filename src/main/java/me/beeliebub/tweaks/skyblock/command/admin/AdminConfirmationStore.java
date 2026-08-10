package me.beeliebub.tweaks.skyblock.command.admin;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Locale;

/**
 * Thread-safe, one-shot confirmation state for destructive administrator commands.
 *
 * <p>The store has no Bukkit or Dialog dependency.  Player callers use their UUID; console uses
 * {@link #CONSOLE_SENTINEL}.  Confirmation state is intentionally process-local and expires after
 * exactly sixty seconds according to the injected clock.</p>
 */
public final class AdminConfirmationStore {
    public static final Duration TTL = Duration.ofSeconds(60);
    /** Stable actor identity for console commands; it is never generated at runtime. */
    public static final UUID CONSOLE_SENTINEL = new UUID(0L, 0L);
    /** Readable alias for callers that prefer an actor-oriented name. */
    public static final UUID CONSOLE_ACTOR = CONSOLE_SENTINEL;

    private final Clock clock;
    private final ConcurrentMap<Key, PendingConfirmation> pending = new ConcurrentHashMap<>();

    public AdminConfirmationStore() {
        this(Clock.systemUTC());
    }

    public AdminConfirmationStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Clock clock() {
        return clock;
    }

    /** Stores or replaces the pending confirmation for an actor/operation/subject key. */
    public PendingConfirmation put(UUID actor, String operation, String subject,
                                   long referenceCount, Path backupPath) {
        Key key = new Key(actor, operation, subject);
        Instant createdAt = clock.instant();
        PendingConfirmation confirmation = new PendingConfirmation(key, referenceCount, backupPath,
                createdAt, createdAt.plus(TTL));
        pending.put(key, confirmation);
        return confirmation;
    }

    /** Convenience overload for operations with no reference metadata. */
    public PendingConfirmation put(UUID actor, String operation, String subject, long referenceCount) {
        return put(actor, operation, subject, referenceCount, null);
    }

    /** Alias that reads naturally at the call site that begins a confirmation flow. */
    public PendingConfirmation issue(UUID actor, String operation, String subject,
                                     long referenceCount, Path backupPath) {
        return put(actor, operation, subject, referenceCount, backupPath);
    }

    /** Stores an already-normalized key. */
    public PendingConfirmation put(Key key, long referenceCount, Path backupPath) {
        Objects.requireNonNull(key, "key");
        Instant createdAt = clock.instant();
        PendingConfirmation confirmation = new PendingConfirmation(key, referenceCount, backupPath,
                createdAt, createdAt.plus(TTL));
        pending.put(key, confirmation);
        return confirmation;
    }

    /** Reads pending state without consuming it; expired state is removed before returning empty. */
    public Optional<PendingConfirmation> peek(UUID actor, String operation, String subject) {
        return peek(new Key(actor, operation, subject));
    }

    /** Reads pending state by key without consuming it. */
    public Optional<PendingConfirmation> peek(Key key) {
        Objects.requireNonNull(key, "key");
        PendingConfirmation confirmation = pending.get(key);
        if (confirmation == null) return Optional.empty();
        if (confirmation.expired(clock.instant())) {
            pending.remove(key, confirmation);
            return Optional.empty();
        }
        return Optional.of(confirmation);
    }

    /**
     * Atomically consumes a live confirmation.  A successful result can be returned only once,
     * even when two command threads race to consume the same key.
     */
    public Optional<PendingConfirmation> consume(UUID actor, String operation, String subject) {
        return consume(new Key(actor, operation, subject));
    }

    /** Atomically consumes a live confirmation by key. */
    public Optional<PendingConfirmation> consume(Key key) {
        Objects.requireNonNull(key, "key");
        PendingConfirmation confirmation = pending.get(key);
        if (confirmation == null) return Optional.empty();
        if (confirmation.expired(clock.instant())) {
            pending.remove(key, confirmation);
            return Optional.empty();
        }
        return pending.remove(key, confirmation) ? Optional.of(confirmation) : Optional.empty();
    }

    public boolean contains(UUID actor, String operation, String subject) {
        return peek(actor, operation, subject).isPresent();
    }

    public boolean contains(Key key) {
        return peek(key).isPresent();
    }

    /** Removes one pending confirmation without consuming its metadata. */
    public boolean clear(UUID actor, String operation, String subject) {
        return pending.remove(new Key(actor, operation, subject)) != null;
    }

    /** Removes all expired entries and returns how many were discarded. */
    public int purgeExpired() {
        Instant now = clock.instant();
        int removed = 0;
        for (var entry : pending.entrySet()) {
            if (entry.getValue().expired(now) && pending.remove(entry.getKey(), entry.getValue())) removed++;
        }
        return removed;
    }

    /** Returns the number of live entries after pruning expired state. */
    public int size() {
        purgeExpired();
        return pending.size();
    }

    /** Immutable identity used as the map key. */
    public record Key(UUID actor, String operation, String subject) {
        public Key {
            actor = actor == null ? CONSOLE_SENTINEL : actor;
            operation = normalize(operation, "operation");
            subject = normalize(subject, "subject");
        }
    }

    /** Immutable confirmation and destruction-preview metadata. */
    public record PendingConfirmation(Key key, long referenceCount, Path backupPath,
                                      Instant createdAt, Instant expiresAt) {
        public PendingConfirmation {
            key = Objects.requireNonNull(key, "key");
            if (referenceCount < 0) throw new IllegalArgumentException("reference count cannot be negative");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            if (expiresAt.isBefore(createdAt)) throw new IllegalArgumentException("expiry precedes creation");
        }

        public UUID actor() {
            return key.actor();
        }

        public String operation() {
            return key.operation();
        }

        public String subject() {
            return key.subject();
        }

        /** Alias for callers that use the shorter metadata name. */
        public long references() {
            return referenceCount;
        }

        /** Alias for callers that describe the location as a backup location. */
        public Path backupLocation() {
            return backupPath;
        }

        public boolean expired(Instant now) {
            return now == null || !now.isBefore(expiresAt);
        }
    }

    private static String normalize(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
