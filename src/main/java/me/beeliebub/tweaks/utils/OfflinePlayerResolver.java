package me.beeliebub.tweaks.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Resolves command target names without allowing uncached Bukkit lookups to block the server thread. */
public final class OfflinePlayerResolver {

    private static final Set<CommandSender> ACTIVE_SENDERS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private OfflinePlayerResolver() {
    }

    /**
     * Returns an online target immediately, or performs the potentially blocking offline lookup
     * asynchronously. A sender may have only one lookup in flight so command spam cannot create an
     * unbounded queue of name lookups.
     */
    public static CompletableFuture<OfflinePlayer> resolve(JavaPlugin plugin, CommandSender sender,
                                                            String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return CompletableFuture.completedFuture(online);

        // A disabled plugin cannot have a live command path or a scheduler continuation. This
        // fallback exists for teardown/test contexts only; normal enabled-plugin resolution below
        // is always asynchronous.
        if (plugin != null && !plugin.isEnabled()) {
            return CompletableFuture.completedFuture(Bukkit.getOfflinePlayer(name));
        }

        synchronized (ACTIVE_SENDERS) {
            if (!ACTIVE_SENDERS.add(sender)) {
                return failedFuture(new LookupInProgressException());
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);
            return target.isOnline() || target.hasPlayedBefore() ? target : null;
        }).whenComplete((ignored, error) -> {
            synchronized (ACTIVE_SENDERS) {
                ACTIVE_SENDERS.remove(sender);
            }
        });
    }

    public static boolean isSenderOnline(CommandSender sender) {
        return !(sender instanceof Player player) || player.isOnline();
    }

    public static boolean isLookupInProgress(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof LookupInProgressException) return true;
            cursor = cursor.getCause();
        }
        return false;
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    public static final class LookupInProgressException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
