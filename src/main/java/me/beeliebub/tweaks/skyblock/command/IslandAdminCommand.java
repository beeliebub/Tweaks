package me.beeliebub.tweaks.skyblock.command;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.SkyblockSetupChecker;
import me.beeliebub.tweaks.skyblock.SkyblockSetupStatus;
import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.command.admin.AdminArgumentParser;
import me.beeliebub.tweaks.skyblock.command.admin.AdminCommandContext;
import me.beeliebub.tweaks.skyblock.command.admin.AdminConfirmationStore;
import me.beeliebub.tweaks.skyblock.command.admin.AdminUsage;
import me.beeliebub.tweaks.skyblock.command.admin.AdminTabCompletion;
import me.beeliebub.tweaks.skyblock.command.admin.ChallengeCommands;
import me.beeliebub.tweaks.skyblock.command.admin.EconomyCommands;
import me.beeliebub.tweaks.skyblock.command.admin.IslandCommands;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminServices;
import me.beeliebub.tweaks.skyblock.command.admin.TypeCommands;
import me.beeliebub.tweaks.skyblock.command.admin.WorldCommands;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import me.beeliebub.tweaks.skyblock.type.IslandDifficulty;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.ui.IslandAdminGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Permission and world-gate router for the console-safe Skyblock administrator command. */
public final class IslandAdminCommand implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final AdminCommandContext context;
    private final IslandAdminGUI gui;
    private final WorldCommands worldCommands;
    private final IslandCommands islandCommands;
    private final TypeCommands typeCommands;
    private final ChallengeCommands challengeCommands;
    private final EconomyCommands economyCommands;

    public IslandAdminCommand(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime) {
        this(plugin, runtime, SkyblockAdminServices.create(plugin, runtime));
    }

    public IslandAdminCommand(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime,
                              SkyblockAdminServices services) {
        this(plugin, runtime, services, new AdminConfirmationStore());
    }

    public IslandAdminCommand(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime,
                              SkyblockAdminServices services, AdminConfirmationStore confirmations) {
        this.context = new AdminCommandContext(plugin, runtime, services, confirmations);
        this.gui = new IslandAdminGUI(plugin, runtime, services);
        this.worldCommands = new WorldCommands(context);
        this.islandCommands = new IslandCommands(context);
        this.typeCommands = new TypeCommands(context);
        this.challengeCommands = new ChallengeCommands(context);
        this.economyCommands = new EconomyCommands(context);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Player player = sender instanceof Player value ? value : null;
        if (player != null && !player.hasPermission(Permissions.ADMIN_SKYBLOCK)) {
            sender.sendMessage(Messages.SKYBLOCK.adminNoPermission());
            return true;
        }
        if (player != null && player.getWorld() != context.runtime.world()) {
            sender.sendMessage(Messages.SKYBLOCK.wrongWorld(context.runtime.config().worldKey()));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) return help(sender, args);
        if (args.length > 0 && args[0].equalsIgnoreCase("status")) return status(sender);
        if (args.length == 0 || args[0].equalsIgnoreCase("spawn")) {
            return player == null ? context.invalid(sender, "spawn requires an in-game administrator")
                    : worldCommands.spawn(player);
        }
        if (args[0].equalsIgnoreCase("clearspawn")) return clearSpawn(sender, args);
        if (args[0].equalsIgnoreCase("reload")) {
            var result = context.admin.reload();
            return context.report(sender, result.success(), "registries", result.message());
        }
        if (args[0].equalsIgnoreCase("gui")) {
            if (player == null) return context.invalid(sender, "gui requires an in-game administrator");
            gui.open(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("island")) return islandCommands.handle(sender, player, args);
        if (args[0].equalsIgnoreCase("templates")) return worldCommands.templates(sender, args);
        String registry = args[0].toLowerCase(Locale.ROOT);
        if (List.of("types", "difficulties", "challenges", "generators", "shop").contains(registry)) {
            return registry(sender, args, registry);
        }
        return context.invalid(sender, args[0]);
    }

    private boolean help(CommandSender sender, String[] args) {
        String topic = args.length <= 1 ? "" : AdminArgumentParser.joinTail(args, 1);
        List<String> lines = AdminUsage.help(topic);
        if (lines.isEmpty()) return context.invalid(sender, "unknown admin help topic");
        lines.forEach(line -> sender.sendMessage(Messages.SKYBLOCK.adminHelp(line)));
        return true;
    }

    private boolean status(CommandSender sender) {
        SkyblockSetupStatus status = new SkyblockSetupChecker(context.runtime).check();
        sender.sendMessage(Messages.SKYBLOCK.adminDescription("Skyblock setup status ("
                + status.checks().size() + " checks):"));
        status.checks().forEach(check -> sender.sendMessage(Messages.SKYBLOCK.adminStatus(
                check.id(), check.state().name(), check.reason(), check.targetScreen())));
        return true;
    }

    private boolean clearSpawn(CommandSender sender, String[] args) {
        if (!context.requireConfirmation(sender, "clearspawn", "spawn", 0,
                "The recorded Skyblock spawn and its bounds will be removed.",
                AdminArgumentParser.hasTrailingConfirm(args))) return true;
        var result = context.admin.clearSpawn();
        sender.sendMessage(result.success() ? Messages.SKYBLOCK.saved("spawn fallback")
                : Messages.SKYBLOCK.invalidInput(result.message()));
        return true;
    }

    private boolean registry(CommandSender sender, String[] args, String registry) {
        if (args.length == 1 || args[1].equalsIgnoreCase("list")) {
            return listRegistry(sender, registry, args.length <= 2 ? "" : AdminArgumentParser.joinTail(args, 2));
        }
        try {
            return switch (registry) {
                case "challenges" -> challengeCommands.handle(sender, args);
                case "generators" -> economyCommands.handleGenerator(sender, args);
                case "types" -> typeCommands.handleType(sender, args);
                case "difficulties" -> typeCommands.handleDifficulty(sender, args);
                case "shop" -> economyCommands.handleShop(sender, args);
                default -> context.invalid(sender, registry);
            };
        } catch (RuntimeException error) {
            return context.invalid(sender, error.getMessage() == null
                    ? "invalid Skyblock authoring input" : error.getMessage());
        }
    }

    private boolean listRegistry(CommandSender sender, String registry, String filter) {
        List<String> values = new ArrayList<>();
        String normalized = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        switch (registry) {
            case "types" -> context.runtime.typeRegistry().types().forEach(type -> {
                if (matches(type.id(), type.displayName(), normalized)) {
                    values.add(type.id() + " - " + type.displayName());
                }
            });
            case "difficulties" -> context.runtime.typeRegistry().difficulties().forEach(difficulty -> {
                if (matches(difficulty.id(), difficulty.displayName(), normalized)) {
                    values.add(difficulty.id() + " - " + difficulty.displayName());
                }
            });
            case "challenges" -> context.runtime.challengeRegistry().challenges().forEach(challenge -> {
                if (matches(challenge.id(), challenge.displayName(), normalized)) {
                    values.add(challenge.id() + " - " + challenge.displayName());
                }
            });
            case "generators" -> context.runtime.generatorRegistry().tiers().forEach(tier -> {
                if (matches(tier.id(), tier.displayName(), normalized)) {
                    values.add(tier.id() + " - " + tier.displayName());
                }
            });
            case "shop" -> context.runtime.shopCatalog().entries().forEach(entry -> {
                if (matches(entry.material().name(), entry.category(), normalized)) {
                    values.add(me.beeliebub.tweaks.skyblock.SkyblockDescriptions.shopEntry(entry));
                }
            });
            default -> { }
        }
        sender.sendMessage(Messages.SKYBLOCK.adminList(registry,
                values.isEmpty() ? "(none)" : String.join(", ", values)));
        return true;
    }

    private static boolean matches(String first, String second, String filter) {
        return filter == null || filter.isBlank()
                || first.toLowerCase(Locale.ROOT).contains(filter)
                || second != null && second.toLowerCase(Locale.ROOT).contains(filter);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return AdminTabCompletion.complete(context, sender, args);
    }
}
