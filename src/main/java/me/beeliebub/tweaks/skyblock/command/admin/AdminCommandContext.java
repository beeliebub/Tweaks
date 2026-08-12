package me.beeliebub.tweaks.skyblock.command.admin;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.economy.ShopAdminService;
import me.beeliebub.tweaks.skyblock.generator.GeneratorAdminService;
import me.beeliebub.tweaks.skyblock.template.TemplateAdminService;
import me.beeliebub.tweaks.skyblock.type.TypeAdminService;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeAdminService;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Shared state and small command primitives for the console-safe administrator handlers. */
public final class AdminCommandContext {
    public final JavaPlugin plugin;
    public final SkyblockBootstrap.Runtime runtime;
    public final SkyblockAdminService admin;
    public final TemplateAdminService templates;
    public final ChallengeAdminService challengeAdmin;
    public final GeneratorAdminService generatorAdmin;
    public final TypeAdminService typeAdmin;
    public final ShopAdminService shopAdmin;
    public final AdminConfirmationStore confirmations;

    public AdminCommandContext(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime,
                               SkyblockAdminServices services, AdminConfirmationStore confirmations) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.admin = services.skyblockAdmin();
        this.templates = services.templateAdmin();
        this.challengeAdmin = services.challengeAdmin();
        this.generatorAdmin = services.generatorAdmin();
        this.typeAdmin = services.typeAdmin();
        this.shopAdmin = services.shopAdmin();
        this.confirmations = confirmations == null ? new AdminConfirmationStore() : confirmations;
    }

    public Path backupDirectory() {
        return plugin.getDataFolder().toPath().toAbsolutePath().normalize()
                .resolve("skyblock").resolve("backups");
    }

    public boolean requireConfirmation(CommandSender sender, String operation, String subject,
                                       long references, String consequence, boolean confirmed) {
        UUID actor = sender instanceof org.bukkit.entity.Player player
                ? player.getUniqueId() : AdminConfirmationStore.CONSOLE_SENTINEL;
        Path backup = backupDirectory();
        if (!confirmed) {
            confirmations.put(actor, operation, subject, Math.max(0, references), backup);
            sender.sendMessage(Messages.SKYBLOCK.adminDescription(consequence));
            sender.sendMessage(Messages.SKYBLOCK.adminConfirmationRequired(subject, references, backup.toString()));
            return false;
        }
        if (confirmations.consume(actor, operation, subject).isEmpty()) {
            sender.sendMessage(Messages.SKYBLOCK.adminConfirmationExpired(subject));
            return false;
        }
        return true;
    }

    public boolean report(CommandSender sender, boolean success, String subject, String message) {
        return report(sender, success, subject, message, CompletableFuture.completedFuture(null));
    }

    public boolean report(CommandSender sender, boolean success, String subject, String message,
                          CompletableFuture<Void> persistence) {
        if (!success) {
            sender.sendMessage(Messages.SKYBLOCK.invalidInput(message == null || message.isBlank()
                    ? "operation rejected" : message));
            return true;
        }
        CompletableFuture<Void> durable = persistence == null
                ? CompletableFuture.completedFuture(null) : persistence;
        durable.whenComplete((ignored, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled() || !reportingActorUsable(sender)) return;
            if (error != null) {
                Throwable cause = unwrap(error);
                sender.sendMessage(Messages.SKYBLOCK.invalidInput(cause.getMessage() == null
                        ? "persistence failed" : cause.getMessage()));
                return;
            }
            sender.sendMessage(Messages.SKYBLOCK.saved(subject));
        }));
        return true;
    }

    private boolean reportingActorUsable(CommandSender sender) {
        return !(sender instanceof Player player) || actorUsable(player);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    public boolean invalid(CommandSender sender, String value) {
        sender.sendMessage(Messages.SKYBLOCK.invalidInput(value));
        return true;
    }

    public boolean invalidUsage(CommandSender sender, String topic) {
        String usage = AdminUsage.syntaxSummary(topic);
        return invalid(sender, usage.isBlank() ? topic : usage);
    }

    public boolean guiOnly(CommandSender sender, String operation, String screen) {
        sender.sendMessage(Messages.SKYBLOCK.guiOnly(operation, screen));
        return true;
    }

    public boolean actorUsable(org.bukkit.entity.Player player) {
        return plugin.isEnabled() && player != null && player.isOnline()
                && player.hasPermission(Permissions.ADMIN_SKYBLOCK)
                && player.getWorld() == runtime.world();
    }

    public static Material requiredMaterial(String value) {
        return AdminArgumentParser.requireMaterial(value);
    }

    public static String join(String[] args, int start) {
        return AdminArgumentParser.joinTail(args, start);
    }
}
