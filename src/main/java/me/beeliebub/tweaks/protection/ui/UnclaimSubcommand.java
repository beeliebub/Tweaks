package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.utils.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /region unclaim &lt;name&gt; — owner/admin-only. Managers cannot unclaim.
 *
 * <p>Refund flow: regions claimed under the pricing system carry the full purchase price in
 * {@code Region#cost}. On unclaim we refund the entire amount (no depreciation) to the owner via
 * {@code InventoryUtil#addResourceRupees}.
 *
 * <p>Recipient resolution:
 * <ul>
 *   <li>If the unclaim caller IS the owner, refund them directly.</li>
 *   <li>If the caller is an admin and the owner is online, refund the owner.</li>
 *   <li>If the owner is offline, the refund is skipped silently — we never drop currency items
 *       at arbitrary coordinates because that opens an economy-exploit door (admin unclaims
 *       someone's region while they're offline -&gt; a chest somewhere fills with their rupees).</li>
 *   <li>Admin-claimed regions store cost = 0, so admins reclaiming their own free claims correctly
 *       refund nothing.</li>
 * </ul>
 *
 * <p><b>Shared refund path:</b> {@link RegionGUI}'s unclaim-confirmation dialog previously deleted
 * the region via {@code ProtectionManager.unclaim} directly with zero refund, while this CLI path
 * always refunded correctly — a real bug (the GUI is the more commonly used surface). Both now
 * route through {@link #unclaimWithRefund}.
 */
final class UnclaimSubcommand implements RegionSubcommand {

    @Override public String name() { return "unclaim"; }
    @Override public String permission() { return Permissions.PROTECTION_UNCLAIM; }
    @Override public List<RegionUsageEntry> usage() {
        return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_UNCLAIM_SYNTAX),
                Messages.PROTECTION.value(Text.USAGE_UNCLAIM_DESCRIPTION),
                Permissions.PROTECTION_UNCLAIM));
    }

    @Override
    public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
        if (args.length < 1) { ctx.showUsage(sender, this); return; }
        String name = args[0];
        if (ProtectionManager.GLOBAL_REGION_ID.equals(name)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.GLOBAL_UNCLAIM_DENIED));
            return;
        }
        Region region = ctx.resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
            return;
        }
        if (!RegionAuth.isOwnerOrAdmin(sender, region)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.UNCLAIM_OWNER_ONLY, name));
            return;
        }
        unclaimWithRefund(sender, ctx.protection, region);
    }

    @Override
    public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
        if (rawArgs.length == 2) return ctx.regionSuggestions(sender, rawArgs[1]);
        return Collections.emptyList();
    }

    /**
     * Unclaims {@code region} and refunds its cost, if any, to the appropriate recipient. Shared
     * by the CLI {@link #execute} above (after its own name-resolution/authorization) and
     * {@link RegionGUI}'s unclaim-confirmation dialog (which has already resolved and
     * authorized the region by the time it calls this).
     */
    static void unclaimWithRefund(CommandSender sender, ProtectionManager pm, Region region) {
        String name = region.id();
        int refund = region.cost();
        Player refundRecipient = resolveRefundRecipient(sender, region);

        if (!pm.unclaim(name)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
            return;
        }

        boolean refunded = false;
        if (refund > 0 && refundRecipient != null) {
            InventoryUtil.addResourceRupees(refundRecipient, refund);
            refunded = true;
        }

        if (refunded) {
            sender.sendMessage(Messages.PROTECTION.unclaimRefunded(name, refund, refundRecipient.getName()));
        } else if (refund > 0) {
            // A refund was owed but the owner is offline and the caller isn't them — the currency
            // is genuinely lost (see resolveRefundRecipient's Javadoc on why we never drop it at
            // arbitrary coordinates instead). Distinct message + log line so this isn't silently
            // indistinguishable from the "nothing was owed" case below — a player disputing "I
            // unclaimed and never got my rupees back" needs an actual record to check against.
            pm.plugin().getLogger().log(Level.WARNING,
                    "Unclaimed region '" + name + "' had a " + refund
                            + " Resource Rupee refund owed, but the owner (" + region.owner()
                            + ") was offline — refund was NOT issued (caller: " + sender.getName() + ")");
            sender.sendMessage(Messages.PROTECTION.unclaimOfflineRefund(name, refund));
        } else {
            sender.sendMessage(Messages.PROTECTION.text(Text.UNCLAIM_SUCCESS, name));
        }
    }

    // Determine who receives the unclaim refund. Returns null when the owner is
    // offline and the caller is not the owner — refunds are dropped silently in
    // that case rather than materialised at arbitrary coordinates.
    private static Player resolveRefundRecipient(CommandSender sender, Region region) {
        UUID ownerId = region.owner();
        if (ownerId == null) return null;
        if (sender instanceof Player p && ownerId.equals(p.getUniqueId())) {
            return p;
        }
        return Bukkit.getPlayer(ownerId);
    }
}
