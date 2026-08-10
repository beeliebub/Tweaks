package me.beeliebub.tweaks.skyblock;

import java.util.List;
import java.util.Objects;

/** Ordered readiness information shown by the Skyblock administration hub. */
public record SkyblockSetupStatus(List<Check> checks) {
    public SkyblockSetupStatus {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    public boolean playable() {
        return checks.stream().noneMatch(check -> check.state() == State.BLOCKED);
    }

    public boolean complete() {
        return checks.stream().noneMatch(check ->
                check.state() == State.INCOMPLETE || check.state() == State.BLOCKED);
    }

    public enum State {
        SATISFIED,
        INCOMPLETE,
        BLOCKED,
        ADVISORY
    }

    public record Check(String id, String label, State state, String reason, String targetScreen) {
        public Check {
            id = requireText(id, "id");
            label = requireText(label, "label");
            state = Objects.requireNonNull(state, "state");
            reason = reason == null ? "" : reason;
            targetScreen = targetScreen == null || targetScreen.isBlank() ? null : targetScreen;
        }

        public boolean actionable() {
            return targetScreen != null && state != State.SATISFIED && state != State.ADVISORY;
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
            return value;
        }
    }
}
