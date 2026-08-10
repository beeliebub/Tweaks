package me.beeliebub.tweaks.skyblock.island;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread invite state with expiry; persisted island membership is never inferred from it. */
public final class IslandInviteManager {

    private final Duration timeout;
    private final Map<UUID, Invite> pending = new ConcurrentHashMap<>();

    public IslandInviteManager(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Invite timeout must be positive");
        this.timeout = timeout;
    }

    public void invite(UUID islandOwner, String islandId, UUID invitee) {
        pending.put(invitee, new Invite(islandOwner, islandId, Instant.now().plus(timeout)));
    }

    public Optional<Invite> take(UUID invitee) {
        Invite invite = pending.remove(invitee);
        if (invite == null || invite.expiresAt().isBefore(Instant.now())) return Optional.empty();
        return Optional.of(invite);
    }

    public void clear(UUID player) {
        pending.remove(player);
    }

    public void clearExpired() {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record Invite(UUID owner, String islandId, Instant expiresAt) {}
}
