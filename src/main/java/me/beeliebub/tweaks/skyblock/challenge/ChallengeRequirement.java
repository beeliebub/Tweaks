package me.beeliebub.tweaks.skyblock.challenge;

import me.beeliebub.tweaks.skyblock.tracking.TrackKey;
import org.bukkit.Material;

import java.util.Objects;

/** Requirements evaluated by the challenge service at claim time. */
public sealed interface ChallengeRequirement permits ChallengeRequirement.Tracked,
        ChallengeRequirement.Possession {
    long amount();

    record Tracked(TrackKey key, long amount) implements ChallengeRequirement {
        public Tracked {
            Objects.requireNonNull(key, "key");
            if (amount <= 0) throw new IllegalArgumentException("Tracked amount must be positive");
        }
    }

    record Possession(Material material, long amount) implements ChallengeRequirement {
        public Possession {
            Objects.requireNonNull(material, "material");
            if (material.isAir()) throw new IllegalArgumentException("Possession material cannot be air");
            if (amount <= 0) throw new IllegalArgumentException("Possession amount must be positive");
        }
    }
}
