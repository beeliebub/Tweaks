package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Region authorization checks, consolidated from what were previously three near-identical
 * copies: {@code ProtectionCommand.isOwnerOrAdmin}/{@code isOwnerManagerOrAdmin},
 * {@code RegionGUI}'s own copy of {@code isOwnerOrAdmin}, and {@code handleManager}'s inline
 * reimplementation of the owner-only check. All three were behaviorally identical, so
 * consolidating here is a pure de-duplication, not a behavior change.
 *
 * <p><b>Non-Player senders are always authorized</b> (console, command blocks, RCON) —
 * pre-existing behavior, preserved exactly. This does mean a command block with no attached
 * player can run owner-gated actions like {@code /region unclaim} on any region; that predates
 * this consolidation and is not changed here. {@link Permissions#PROTECTION_ADMIN} also always
 * bypasses, for both Player and non-Player senders.
 *
 * <p><b>{@code isOwnerOrAdmin} is deliberately not group-aware</b>: those are owner/admin-only
 * actions (unclaim, editing the manager set, setparent/unsetparent), and a group promotion only
 * ever grants manager-level capabilities — never owner-level ones — so groups must never satisfy
 * this gate. {@code isOwnerManagerOrAdmin} IS group-aware (via {@link ProtectionManager#groupsOf}),
 * matching {@code ProtectionManager}'s own in-world enforcement (which resolves manager/member
 * status through the group-aware {@code Region#isManager(UUID, java.util.Set)}/{@code isMember}
 * overloads). Before this fix, {@code isOwnerManagerOrAdmin} only checked UUID-listed managers,
 * so a player promoted to manager purely via {@code /region addmanager <region> group:builders}
 * got full in-world manager privileges but was rejected by every command/GUI action gated on
 * this method (`/region gui`, `addmember`/`removemember`, `flag`/`unflag`).
 */
final class RegionAuth {

    private RegionAuth() {}

    static boolean isOwnerOrAdmin(CommandSender sender, Region region) {
        if (sender.hasPermission(Permissions.PROTECTION_ADMIN)) return true;
        if (!(sender instanceof Player p)) return true;
        return region.isOwner(p.getUniqueId());
    }

    static boolean isOwnerManagerOrAdmin(CommandSender sender, Region region, ProtectionManager pm) {
        if (sender.hasPermission(Permissions.PROTECTION_ADMIN)) return true;
        if (!(sender instanceof Player p)) return true;
        java.util.UUID uuid = p.getUniqueId();
        return region.isOwner(uuid) || region.isManager(uuid, pm.groupsOf(uuid));
    }
}
