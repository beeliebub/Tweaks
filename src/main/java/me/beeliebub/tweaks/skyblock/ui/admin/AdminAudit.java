package me.beeliebub.tweaks.skyblock.ui.admin;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared INFO-level audit formatter for durable Skyblock administrator mutations. */
public final class AdminAudit {
    private AdminAudit() {
    }

    public static void recorded(Logger logger, String actor, String action, String target,
                                String before, String after) {
        Objects.requireNonNull(logger, "logger");
        logger.log(Level.INFO, "Skyblock admin {0} {1} {2}: {3} -> {4}",
                new Object[]{actor == null || actor.isBlank() ? "unknown" : actor,
                        action == null ? "changed" : action,
                        target == null ? "unknown" : target,
                        summary(before), summary(after)});
    }

    public static void recorded(Logger logger, Player actor, String action, String target,
                                String before, String after) {
        recorded(logger, actor == null ? "unknown" : actor.getName(), action, target, before, after);
    }

    public static String itemSummary(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) return "empty";
        return item.getType().name().toLowerCase(java.util.Locale.ROOT) + " x" + item.getAmount();
    }

    private static String summary(String value) {
        if (value == null || value.isBlank()) return "none";
        return value.length() > 160 ? value.substring(0, 157) + "..." : value;
    }
}
