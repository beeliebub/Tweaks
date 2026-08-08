package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

// Legacy CommandExecutor + TabCompleter implementation for the /region (alias /rg) command
// tree. Dispatch and tab completion are delegated entirely to the RegionSubcommandRegistry, one
// RegionSubcommand per subcommand name (see ui/CLAUDE.md for the handler map) — including
// claim/unclaim, which route through their own dedicated handler classes like every other
// subcommand.
public final class ProtectionCommand implements CommandExecutor, TabCompleter {

    private final RegionCommandContext ctx;
    private final RegionSubcommandRegistry registry;

    public ProtectionCommand(Tweaks plugin, ProtectionManager protection, RegionSelectionManager selections) {
        this.ctx = new RegionCommandContext(plugin, protection, selections);
        this.registry = new RegionSubcommandRegistry();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            registry.showRootUsage(ctx, sender);
            return true;
        }
        String name = args[0];
        RegionSubcommand handler = registry.find(name);
        if (handler == null) {
            sender.sendMessage(Messages.PROTECTION.text(Text.UNKNOWN_SUBCOMMAND, name));
            registry.showRootUsage(ctx, sender);
            return true;
        }
        if (handler.permission() != null && !ctx.hasPerm(sender, handler.permission())) {
            sender.sendMessage(Messages.PROTECTION.text(Text.COMMAND_PERMISSION, handler.name()));
            return true;
        }
        String[] popped = popFirst(args);
        RegionWorldArgument.Result scoped = RegionWorldArgument.strip(ctx, sender, handler, popped);
        RegionCommandContext invocation = scoped.world() == null ? ctx : ctx.withScope(scoped.world());
        handler.execute(invocation, sender, scoped.args());
        return true;
    }

    private static String[] popFirst(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] out = new String[args.length - 1];
        System.arraycopy(args, 1, out, 0, out.length);
        return out;
    }

    // ==================================================================
    // Tab completion
    // ==================================================================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0) return Collections.emptyList();

        if (args.length == 1) {
            return RegionCommandContext.filterByPrefix(registry.visibleNames(ctx, sender), args[0]);
        }

        String subName = args[0].toLowerCase(Locale.ROOT);
        RegionSubcommand handler = registry.find(subName);
        if (handler == null) return Collections.emptyList();
        if (handler.permission() != null && !ctx.hasPerm(sender, handler.permission())) {
            return Collections.emptyList();
        }
        List<String> suggestions = new java.util.ArrayList<>(handler.tabComplete(ctx, sender, args));
        if (ctx.hasPerm(sender, me.beeliebub.tweaks.permissions.Permissions.PROTECTION_ADMIN)
                && args.length - 1 > handler.minArgs()) {
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                if (world.getName().toLowerCase(Locale.ROOT).startsWith(prefix)
                        && !suggestions.contains(world.getName())) suggestions.add(world.getName());
            }
        }
        return suggestions;
    }
}
