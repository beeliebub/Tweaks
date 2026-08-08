package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * /region flag <name> <flag> [value tokens...] and /region unflag <name> <flag> [target] —
 * thin dispatchers over {@link RegionFlagEditor}, the reflection-free testable seam.
 */
final class FlagSubcommands {

    private FlagSubcommands() {}

    static final class Flag implements RegionSubcommand {
        @Override public String name() { return "flag"; }
        @Override public String permission() { return Permissions.PROTECTION_FLAG; }
        @Override public int minArgs() { return 3; }
        @Override public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_FLAG_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_FLAG_DESCRIPTION), Permissions.PROTECTION_FLAG));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (args.length < 2) { ctx.showUsage(sender, this); return; }
            String name = args[0];
            String flagToken = args[1];
            if (!ctx.requireNamedRegionWorld(sender, args)) return;
            String rawValue = RegionCommandContext.joinFrom(args, 2);
            if (rawValue.isEmpty()) {
                RegionFlagEditor.listSingleFlag(ctx, sender, name, flagToken);
                return;
            }
            RegionFlagEditor.setFlag(ctx, sender, ctx.protection, ctx.permissions(), name, flagToken, rawValue);
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            // Layout: rawArgs[0]="flag", rawArgs[1]=region name, rawArgs[2]=flag name, rawArgs[3+]=value tokens.
            if (rawArgs.length == 2) return ctx.regionSuggestions(sender, rawArgs[1]);
            if (rawArgs.length == 3) return RegionCommandContext.flagNameSuggestions(rawArgs[2]);

            RegionFlag flag = RegionFlagEditor.tryParseFlag(rawArgs[2]);
            if (flag == null) return Collections.emptyList();
            String partial = rawArgs[rawArgs.length - 1];

            if (flag.isMaterialFlag()) {
                return RegionCommandContext.blockMaterialNames(partial);
            }
            if (flag.isEntityFlag()) {
                return RegionCommandContext.entityTypeNames(partial);
            }
            // Boolean: rawArgs[3] = true|false, rawArgs[4] = target
            if (rawArgs.length == 4) {
                return RegionCommandContext.filterByPrefix(List.of("true", "false"), partial);
            }
            if (rawArgs.length == 5) {
                return ctx.targetNames(partial);
            }
            return Collections.emptyList();
        }
    }

    static final class Unflag implements RegionSubcommand {
        @Override public String name() { return "unflag"; }
        @Override public String permission() { return Permissions.PROTECTION_FLAG; }
        @Override public int minArgs() { return 2; }
        @Override public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_UNFLAG_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_UNFLAG_DESCRIPTION),
                    Permissions.PROTECTION_FLAG));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (args.length < 2) { ctx.showUsage(sender, this); return; }
            String name = args[0];
            String flagToken = args[1];
            String rawTarget = (args.length >= 3) ? args[2] : null;
            RegionFlagEditor.removeFlag(ctx, sender, ctx.protection, ctx.permissions(), name, flagToken, rawTarget);
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            if (rawArgs.length == 2) return ctx.regionSuggestions(sender, rawArgs[1]);
            if (rawArgs.length == 3) return RegionCommandContext.flagNameSuggestions(rawArgs[2]);
            if (rawArgs.length == 4) {
                RegionFlag flag = RegionFlagEditor.tryParseFlag(rawArgs[2]);
                if (flag != null && (flag.isMaterialFlag() || flag.isEntityFlag())) {
                    return Collections.emptyList(); // targets do not apply
                }
                return ctx.targetNames(rawArgs[3]);
            }
            return Collections.emptyList();
        }
    }
}
