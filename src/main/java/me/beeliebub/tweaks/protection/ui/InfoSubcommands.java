package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.protection.region.Region;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/** The read-only inspection subcommands: {@code info}/{@code i}, {@code flags}. */
final class InfoSubcommands {

    private InfoSubcommands() {}

    static final class Info implements RegionSubcommand {
        @Override public String name() { return "info"; }
        @Override public List<String> aliases() { return List.of("i"); }
        @Override public String permission() { return Permissions.PROTECTION_INFO; }
        @Override public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_INFO_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_INFO_DESCRIPTION), Permissions.PROTECTION_INFO));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Messages.PROTECTION.text(Text.INFO_CONSOLE_NAME_REQUIRED));
                    return;
                }
                List<Region> here = ctx.protection.regionsAt(player.getLocation());
                if (here.isEmpty()) {
                    sender.sendMessage(Messages.PROTECTION.text(Text.INFO_WILDERNESS));
                    return;
                }
                sender.sendMessage(Messages.PROTECTION.text(Text.INFO_AT_LOCATION,
                        here.size() == 1 ? "" : "s"));
                for (Region r : here) {
                    RegionInfoRenderer.printRegion(sender, r);
                }
                return;
            }
            String name = args[0];
            Region region = ctx.resolveRegion(sender, name);
            if (region == null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
                return;
            }
            RegionInfoRenderer.printRegion(sender, region);
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            if (rawArgs.length == 2) return ctx.regionSuggestions(sender, rawArgs[1]);
            return Collections.emptyList();
        }
    }

    static final class Flags implements RegionSubcommand {
        @Override public String name() { return "flags"; }
        @Override public String permission() { return Permissions.PROTECTION_FLAG; }
        @Override public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_FLAGS_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_FLAGS_DESCRIPTION),
                    Permissions.PROTECTION_FLAG));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Messages.PROTECTION.text(Text.FLAGS_CONSOLE_NAME_REQUIRED));
                    return;
                }
                List<Region> here = ctx.protection.regionsAt(player.getLocation());
                if (here.isEmpty()) {
                    sender.sendMessage(Messages.PROTECTION.text(Text.FLAGS_WILDERNESS));
                    return;
                }
                for (Region region : here) {
                    RegionInfoRenderer.printRegionFlags(sender, region);
                }
                return;
            }
            String name = args[0];
            Region region = ctx.resolveRegion(sender, name);
            if (region == null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
                return;
            }
            RegionInfoRenderer.printRegionFlags(sender, region);
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            if (rawArgs.length == 2) return ctx.regionSuggestions(sender, rawArgs[1]);
            return Collections.emptyList();
        }
    }
}
