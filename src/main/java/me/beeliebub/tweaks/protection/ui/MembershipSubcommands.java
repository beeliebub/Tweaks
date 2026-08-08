package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.utils.OfflinePlayerResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * addmember/removemember/am/rm and addmanager/removemanager/aman/rman. Each pair shares the
 * {@code group:} prefix-routing logic (previously duplicated verbatim between the two old
 * {@code handleMember}/{@code handleManager} methods).
 */
final class MembershipSubcommands {

    private MembershipSubcommands() {}

    static final class Member implements RegionSubcommand {
        private final boolean add;
        Member(boolean add) { this.add = add; }

        @Override public String name() { return add ? "addmember" : "removemember"; }
        @Override public List<String> aliases() { return List.of(add ? "am" : "rm"); }
        @Override public String permission() { return Permissions.PROTECTION_MEMBER; }
        @Override public int minArgs() { return 2; }
        @Override public boolean supportsWorldArgument() { return true; }
        @Override public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(
                    Messages.PROTECTION.value(Text.USAGE_MEMBER_SYNTAX, name()),
                    Messages.PROTECTION.value(add ? Text.USAGE_ADD_MEMBER_DESCRIPTION
                            : Text.USAGE_REMOVE_MEMBER_DESCRIPTION),
                    Permissions.PROTECTION_MEMBER));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (args.length < 2) { ctx.showUsage(sender, this); return; }
            member(ctx, sender, args, add);
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            return memberOrManagerSuggestions(ctx, sender, rawArgs, /*membersOfRegion=*/!add, /*managersOfRegion=*/false);
        }
    }

    static final class Manager implements RegionSubcommand {
        private final boolean add;
        Manager(boolean add) { this.add = add; }

        @Override public String name() { return add ? "addmanager" : "removemanager"; }
        @Override public List<String> aliases() { return List.of(add ? "aman" : "rman"); }
        @Override public String permission() { return Permissions.PROTECTION_MEMBER; }
        @Override public int minArgs() { return 2; }
        @Override public boolean supportsWorldArgument() { return true; }
        @Override public List<RegionUsageEntry> usage() {
            return List.of(new RegionUsageEntry(
                    Messages.PROTECTION.value(Text.USAGE_MANAGER_SYNTAX, name()),
                    Messages.PROTECTION.value(add ? Text.USAGE_ADD_MANAGER_DESCRIPTION
                            : Text.USAGE_REMOVE_MANAGER_DESCRIPTION),
                    Permissions.PROTECTION_MEMBER));
        }

        @Override
        public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
            if (args.length < 2) { ctx.showUsage(sender, this); return; }
            manager(ctx, sender, args, add);
        }

        @Override
        public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
            return memberOrManagerSuggestions(ctx, sender, rawArgs, /*membersOfRegion=*/false, /*managersOfRegion=*/!add);
        }
    }

    // Package-visible (not private) so RegionGroupSupportTest can call it directly instead of
    // via reflection on a private handle* method (the old ProtectionCommand.handleMember shape).
    @SuppressWarnings("deprecation")
    static void member(RegionCommandContext ctx, CommandSender sender, String[] args, boolean add) {
        String name = args[0];
        String target = args[1];

        if (!ctx.requireNamedRegionWorld(sender, args)) return;
        Region region = ctx.resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
            return;
        }
        if (!RegionAuth.isOwnerManagerOrAdmin(ctx, sender, region)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.MEMBER_EDIT_AUTH));
            return;
        }
        World scopeWorld = ctx.scopeWorld(sender);

        if (target.toLowerCase(Locale.ROOT).startsWith("group:")) {
            String groupName = target.substring("group:".length()).trim();
            if (groupName.isEmpty()) {
                sender.sendMessage(Messages.PROTECTION.text(Text.GROUP_NAME_REQUIRED));
                return;
            }
            PermissionManager pm = ctx.permissions();
            if (pm != null && !pm.getGroups().containsKey(groupName.toLowerCase(Locale.ROOT))) {
                sender.sendMessage(Messages.PROTECTION.text(Text.GROUP_MISSING_WARNING, groupName));
            }
            // "default" is the implicit group every player without explicit group assignments
            // resolves to (see ProtectionManager#groupsOf) — adding it here silently grants member
            // access to virtually the entire server. Warn (non-blocking, matching the
            // GROUP_MISSING_WARNING pattern above) rather than silently proceeding.
            if (add && "default".equalsIgnoreCase(groupName)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.MEMBER_GROUP_DEFAULT_WARNING, name));
            }
            boolean ok = scopeWorld == null
                    ? (add ? ctx.protection.addMemberGroup(name, groupName)
                           : ctx.protection.removeMemberGroup(name, groupName))
                    : (add ? ctx.protection.addMemberGroup(scopeWorld, name, groupName)
                           : ctx.protection.removeMemberGroup(scopeWorld, name, groupName));
            if (!ok) {
                sender.sendMessage(Messages.PROTECTION.text(add
                        ? Text.MEMBER_GROUP_DUPLICATE : Text.MEMBER_GROUP_MISSING, groupName, name));
                return;
            }
            sender.sendMessage(Messages.PROTECTION.text(add
                    ? Text.MEMBER_GROUP_ADD : Text.MEMBER_GROUP_REMOVE, groupName, name));
            return;
        }

        resolveTarget(ctx, sender, target, offlineTarget -> {
            boolean ok = scopeWorld == null
                    ? (add ? ctx.protection.addMember(name, offlineTarget.getUniqueId())
                           : ctx.protection.removeMember(name, offlineTarget.getUniqueId()))
                    : (add ? ctx.protection.addMember(scopeWorld, name, offlineTarget.getUniqueId())
                           : ctx.protection.removeMember(scopeWorld, name, offlineTarget.getUniqueId()));
            if (!ok) {
                sender.sendMessage(Messages.PROTECTION.text(add
                        ? Text.MEMBER_ADD_FAILED : Text.MEMBER_REMOVE_FAILED));
                return;
            }
            sender.sendMessage(Messages.PROTECTION.text(Text.MEMBER_SUCCESS,
                    add ? "Added" : "Removed", target, add ? "to" : "from", name));
        });
    }

    // /region addmanager|removemanager — owner-only by design. The owner-only check now
    // routes through RegionAuth.isOwnerOrAdmin instead of a second inline admin/owner check.
    // (`!(sender instanceof Player) || sender.hasPermission(PROTECTION_ADMIN)`, then a manual
    // owner check) — the two were behaviorally identical, so this is pure de-duplication.
    @SuppressWarnings("deprecation")
    static void manager(RegionCommandContext ctx, CommandSender sender, String[] args, boolean add) {
        String name = args[0];
        String target = args[1];

        Region region = ctx.resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
            return;
        }

        if (!RegionAuth.isOwnerOrAdmin(ctx, sender, region)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.MANAGER_EDIT_AUTH));
            return;
        }
        World scopeWorld = ctx.scopeWorld(sender);

        if (target.toLowerCase(Locale.ROOT).startsWith("group:")) {
            String groupName = target.substring("group:".length()).trim();
            if (groupName.isEmpty()) {
                sender.sendMessage(Messages.PROTECTION.text(Text.GROUP_NAME_REQUIRED));
                return;
            }
            PermissionManager pm = ctx.permissions();
            if (pm != null && !pm.getGroups().containsKey(groupName.toLowerCase(Locale.ROOT))) {
                sender.sendMessage(Messages.PROTECTION.text(Text.GROUP_MISSING_WARNING, groupName));
            }
            // See the matching comment in member() above — "default" catches virtually every
            // player on the server, and here it grants MANAGER access (flag editing, membership
            // control), so the warning is even more important to surface.
            if (add && "default".equalsIgnoreCase(groupName)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.MANAGER_GROUP_DEFAULT_WARNING, name));
            }
            boolean ok = scopeWorld == null
                    ? (add ? ctx.protection.addManagerGroup(name, groupName)
                           : ctx.protection.removeManagerGroup(name, groupName))
                    : (add ? ctx.protection.addManagerGroup(scopeWorld, name, groupName)
                           : ctx.protection.removeManagerGroup(scopeWorld, name, groupName));
            if (!ok) {
                sender.sendMessage(Messages.PROTECTION.text(add
                        ? Text.MANAGER_GROUP_DUPLICATE : Text.MANAGER_GROUP_MISSING, groupName, name));
                return;
            }
            sender.sendMessage(Messages.PROTECTION.text(add
                    ? Text.MANAGER_GROUP_ADD : Text.MANAGER_GROUP_REMOVE, groupName, name));
            return;
        }

        resolveTarget(ctx, sender, target, offlineTarget -> {
            if (add && region.owner().equals(offlineTarget.getUniqueId())) {
                sender.sendMessage(Messages.PROTECTION.text(Text.OWNER_MANAGER));
                return;
            }

            boolean ok = scopeWorld == null
                    ? (add ? ctx.protection.addManager(name, offlineTarget.getUniqueId())
                           : ctx.protection.removeManager(name, offlineTarget.getUniqueId()))
                    : (add ? ctx.protection.addManager(scopeWorld, name, offlineTarget.getUniqueId())
                           : ctx.protection.removeManager(scopeWorld, name, offlineTarget.getUniqueId()));
            if (!ok) {
                sender.sendMessage(Messages.PROTECTION.text(add
                        ? Text.MANAGER_ADD_FAILED : Text.MANAGER_REMOVE_FAILED));
                return;
            }
            sender.sendMessage(Messages.PROTECTION.text(Text.MANAGER_SUCCESS,
                    add ? "Promoted" : "Demoted", target,
                    add ? "to manager on" : "from manager on", name));
        });
    }

    private static void resolveTarget(RegionCommandContext ctx, CommandSender sender, String input,
                                      java.util.function.Consumer<OfflinePlayer> action) {
        var future = OfflinePlayerResolver.resolve(ctx.plugin, sender, input);
        boolean asynchronous = !future.isDone();
        future.whenComplete((target, error) -> {
            Runnable continuation = () -> {
                if (asynchronous && !OfflinePlayerResolver.isSenderOnline(sender)) return;
                if (error != null) {
                    if (OfflinePlayerResolver.isLookupInProgress(error)) {
                        sender.sendMessage(Messages.COMMANDS.offlinePlayerLookupBusy());
                    } else {
                        ctx.plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "Region player lookup failed for " + input, error);
                    }
                    return;
                }
                if (target == null) {
                    sender.sendMessage(Messages.PROTECTION.text(Text.UNKNOWN_PLAYER, input));
                    return;
                }
                action.accept(target);
            };
            if (future.isDone()) continuation.run();
            else if (ctx.plugin.isEnabled()) Bukkit.getScheduler().runTask(ctx.plugin, continuation);
        });
    }

    private static List<String> memberOrManagerSuggestions(RegionCommandContext ctx, CommandSender sender,
                                                            String[] rawArgs,
                                                            boolean membersOfRegion, boolean managersOfRegion) {
        if (rawArgs.length == 2) {
            return ctx.regionSuggestions(sender, rawArgs[1]);
        }
        if (rawArgs.length == 3) {
            String partial = rawArgs[2];
            String prefix = partial.toLowerCase(Locale.ROOT);
            World scopeWorld = ctx.scopeWorld(sender);
            if (membersOfRegion) {
                List<String> out = new ArrayList<>(ctx.regionMemberNames(scopeWorld, rawArgs[1], prefix));
                out.addAll(ctx.regionGroupSuggestions(scopeWorld, rawArgs[1], prefix, /*managers=*/false));
                return out;
            }
            if (managersOfRegion) {
                List<String> out = new ArrayList<>(ctx.regionManagerNames(scopeWorld, rawArgs[1], prefix));
                out.addAll(ctx.regionGroupSuggestions(scopeWorld, rawArgs[1], prefix, /*managers=*/true));
                return out;
            }
            List<String> out = new ArrayList<>(RegionCommandContext.onlinePlayerNames(prefix));
            out.addAll(ctx.allGroupSuggestions(prefix));
            return out;
        }
        return Collections.emptyList();
    }
}
