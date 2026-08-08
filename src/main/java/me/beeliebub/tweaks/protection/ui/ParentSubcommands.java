package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * The hierarchy subcommands: {@code setparent}, {@code unsetparent}.
 *
 * <p>Both handlers resolve the child region and require {@link RegionAuth#isOwnerOrAdmin} before
 * calling {@code ProtectionManager.setParent}. {@code setparent} additionally requires ownership
 * of the target parent; managers of the parent do not qualify. The manager enforces equal child and
 * parent ownership by default, while admins may pass an explicit cross-owner override for intentional
 * server-owned-parent layouts. Legacy regions without bounds remain exempt from geometry checks.
 */
final class ParentSubcommands {

    private ParentSubcommands() {}

    static final class SetParent implements RegionSubcommand {
        @Override public String name() { return "setparent"; }
        @Override public String permission() { return Permissions.PROTECTION_PURCHASEABLE; }
        @Override public int minArgs() { return 2; }
        @Override public boolean supportsWorldArgument() { return true; }
        @Override public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_SET_PARENT_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_SET_PARENT_DESCRIPTION),
                    Permissions.PROTECTION_PURCHASEABLE));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (args.length < 2) { ctx.showUsage(sender, this); return; }
            String child = args[0];
            String parent = args[1];
            if (!ctx.requireNamedRegionWorld(sender, args)) return;
            Region childRegion = ctx.resolveRegion(sender, child);
            if (childRegion == null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, child));
                return;
            }
            if (!RegionAuth.isOwnerOrAdmin(ctx, sender, childRegion)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.PARENT_EDIT_AUTH));
                return;
            }
            Region parentRegion = ctx.resolveRegion(sender, parent);
            if (parentRegion == null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.PARENT_UNKNOWN, parent));
                return;
            }
            if (!RegionAuth.isOwnerOrAdmin(ctx, sender, parentRegion)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.PARENT_TARGET_AUTH));
                return;
            }
            ProtectionManager.SetParentResult result = ctx.scopeWorld(sender) == null
                    ? ctx.protection.setParent(child, parent, RegionAuth.isAdmin(ctx, sender))
                    : ctx.protection.setParent(ctx.scopeWorld(sender), child, parent,
                            RegionAuth.isAdmin(ctx, sender));
            reportParentResult(sender, child, parent, result);
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            if (rawArgs.length == 2 || rawArgs.length == 3) {
                return ctx.regionSuggestions(sender, rawArgs[rawArgs.length - 1]);
            }
            return Collections.emptyList();
        }
    }

    static final class UnsetParent implements RegionSubcommand {
        @Override public String name() { return "unsetparent"; }
        @Override public String permission() { return Permissions.PROTECTION_PURCHASEABLE; }
        @Override public int minArgs() { return 1; }
        @Override public boolean supportsWorldArgument() { return true; }
        @Override public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_UNSET_PARENT_SYNTAX),
                    Messages.PROTECTION.value(Text.USAGE_UNSET_PARENT_DESCRIPTION),
                    Permissions.PROTECTION_PURCHASEABLE));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (args.length < 1) { ctx.showUsage(sender, this); return; }
            String child = args[0];
            if (!ctx.requireNamedRegionWorld(sender, args)) return;
            Region childRegion = ctx.resolveRegion(sender, child);
            if (childRegion == null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, child));
                return;
            }
            if (!RegionAuth.isOwnerOrAdmin(ctx, sender, childRegion)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.PARENT_EDIT_AUTH));
                return;
            }
            ProtectionManager.SetParentResult result = ctx.scopeWorld(sender) == null
                    ? ctx.protection.setParent(child, null, RegionAuth.isAdmin(ctx, sender))
                    : ctx.protection.setParent(ctx.scopeWorld(sender), child, null,
                            RegionAuth.isAdmin(ctx, sender));
            reportParentResult(sender, child, null, result);
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            if (rawArgs.length == 2) return ctx.regionSuggestions(sender, rawArgs[1]);
            return Collections.emptyList();
        }
    }

    // A switch EXPRESSION (not statement) so the compiler enforces exhaustiveness over
    // SetParentResult's branches — a future constant added there without a case here fails the
    // build instead of silently sending the sender nothing.
    private static void reportParentResult(CommandSender sender, String child, String parent,
                                           ProtectionManager.SetParentResult result) {
        Component message = switch (result) {
            case OK -> parent == null
                    ? Messages.PROTECTION.text(Text.PARENT_TOP_LEVEL, child)
                    : Messages.PROTECTION.text(Text.PARENT_SUCCESS, child, parent);
            case NO_CHANGE -> Messages.PROTECTION.text(Text.PARENT_NO_CHANGE);
            case UNKNOWN_CHILD -> Messages.PROTECTION.text(Text.REGION_NOT_FOUND, child);
            case UNKNOWN_PARENT -> Messages.PROTECTION.text(Text.PARENT_UNKNOWN, parent);
            case SELF_REFERENCE -> Messages.PROTECTION.text(Text.PARENT_SELF);
            case CYCLE -> Messages.PROTECTION.text(Text.PARENT_CYCLE);
            case DIFFERENT_WORLDS -> Messages.PROTECTION.text(Text.PARENT_OTHER_WORLD);
            case DIFFERENT_OWNER -> Messages.PROTECTION.text(Text.PARENT_DIFFERENT_OWNER);
            case NOT_CONTAINED_IN_PARENT -> Messages.PROTECTION.text(Text.PARENT_NOT_CONTAINED);
            case OVERLAPS_SIBLING -> Messages.PROTECTION.text(Text.PARENT_SIBLING_OVERLAP);
        };
        sender.sendMessage(message);
    }
}
