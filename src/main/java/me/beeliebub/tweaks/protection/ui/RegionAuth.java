package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Authorization checks for region commands and dialogs.
 *
 * <p>Non-player senders are authorized, matching the command layer's console and command-block
 * behavior. {@link Permissions#PROTECTION_ADMIN} bypasses ownership checks for every sender.
 *
 * <p>{@code isOwnerOrAdmin} is intentionally not group-aware: unclaiming, editing managers, and
 * changing hierarchy require the actual owner or an admin. {@code isOwnerManagerOrAdmin} resolves
 * permission groups through {@link ProtectionManager#groupsOf} so group managers receive the same
 * capabilities as UUID-listed managers.
 */
final class RegionAuth {

    private RegionAuth() {}

    static boolean isAdmin(RegionCommandContext context, CommandSender sender) {
        return context.hasPerm(sender, Permissions.PROTECTION_ADMIN);
    }

    static boolean isAdmin(CommandSender sender, ProtectionManager protection) {
        return protection != null && new RegionCommandContext(protection.plugin(), protection, null)
                .hasPerm(sender, Permissions.PROTECTION_ADMIN);
    }

    static boolean isOwnerOrAdmin(RegionCommandContext context, CommandSender sender, Region region) {
        if (isAdmin(context, sender)) return true;
        if (!(sender instanceof Player p)) return true;
        return region.isOwner(p.getUniqueId());
    }

    static boolean isOwnerOrAdmin(CommandSender sender, Region region, ProtectionManager protection) {
        if (isAdmin(sender, protection)) return true;
        if (!(sender instanceof Player p)) return true;
        return region.isOwner(p.getUniqueId());
    }

    static boolean isOwnerManagerOrAdmin(CommandSender sender, Region region, ProtectionManager pm) {
        if (isAdmin(sender, pm)) return true;
        if (!(sender instanceof Player p)) return true;
        java.util.UUID uuid = p.getUniqueId();
        return region.isOwner(uuid) || region.isManager(uuid, pm.groupsOf(uuid));
    }

    static boolean isOwnerManagerOrAdmin(RegionCommandContext context,
                                         CommandSender sender, Region region) {
        if (isAdmin(context, sender)) return true;
        if (!(sender instanceof Player p)) return true;
        java.util.UUID uuid = p.getUniqueId();
        return region.isOwner(uuid)
                || region.isManager(uuid, context.protection.groupsOf(uuid));
    }
}
