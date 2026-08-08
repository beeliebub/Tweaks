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
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /region unclaim &lt;name&gt; - owner/admin-only. Managers cannot unclaim.
 *
 * <p>The shared unclaim path archives the targeted region and every descendant deepest-first, then
 * refunds each destroyed region's recorded claim cost to its own owner when that owner is online.
 * Offline owners receive a warning and no currency is materialized at an arbitrary location.
 * Persistence failure aborts the operation before cache mutation or refund.
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
        int descendantCount = ctx.protection.descendantsOf(name).size();
        if (descendantCount > 0) {
            sender.sendMessage(Messages.PROTECTION.text(Text.UNCLAIM_CASCADE_WARNING, descendantCount));
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
    static ProtectionManager.UnclaimOutcome unclaimWithRefund(CommandSender sender, ProtectionManager pm,
                                                              Region region) {
        String name = region.id();
        ProtectionManager.UnclaimOutcome outcome = pm.unclaim(name);
        switch (outcome.result()) {
            case GLOBAL_DENIED -> sender.sendMessage(Messages.PROTECTION.text(Text.GLOBAL_UNCLAIM_DENIED));
            case UNKNOWN_REGION -> sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
            case PERSISTENCE_FAILED -> sender.sendMessage(
                    Messages.PROTECTION.text(Text.UNCLAIM_PERSISTENCE_FAILED, name));
            case OK -> {
                // Refunds are resolved independently so a cascade can pay each region's owner.
            }
        }
        if (outcome.result() != ProtectionManager.UnclaimResult.OK) {
            return outcome;
        }

        List<Region> destroyed = outcome.destroyed();
        int totalRefunded = 0;
        int owedButUnpaid = 0;
        for (int i = 0; i < destroyed.size(); i++) {
            Region destroyedRegion = destroyed.get(i);
            int refund = destroyedRegion.cost();
            Player refundRecipient = resolveRefundRecipient(sender, destroyedRegion);
            if (refund > 0 && refundRecipient != null) {
                InventoryUtil.addResourceRupees(refundRecipient, refund);
                totalRefunded += refund;
            } else if (refund > 0) {
                owedButUnpaid += refund;
                pm.plugin().getLogger().log(Level.WARNING,
                        "Unclaimed region '" + destroyedRegion.id() + "' had a " + refund
                                + " Resource Rupee refund owed, but the owner (" + destroyedRegion.owner()
                                + ") was offline — refund was NOT issued (caller: " + sender.getName() + ")");
            }

            if (i < destroyed.size() - 1
                    && !Objects.equals(region.owner(), destroyedRegion.owner())) {
                pm.plugin().getLogger().log(Level.WARNING,
                        "Unclaimed region '" + region.id() + "' cascaded into descendant '"
                                + destroyedRegion.id() + "' with a different owner (parent owner: "
                                + region.owner() + ", descendant owner: " + destroyedRegion.owner() + ")");
            }
        }

        if (destroyed.size() == 1) {
            Region destroyedRegion = destroyed.getFirst();
            int refund = destroyedRegion.cost();
            Player refundRecipient = resolveRefundRecipient(sender, destroyedRegion);
            if (refund > 0 && refundRecipient != null) {
                sender.sendMessage(Messages.PROTECTION.unclaimRefunded(
                        name, refund, refundRecipient.getName()));
            } else if (refund > 0) {
                sender.sendMessage(Messages.PROTECTION.unclaimOfflineRefund(name, refund));
            } else {
                sender.sendMessage(Messages.PROTECTION.text(Text.UNCLAIM_SUCCESS, name));
            }
        } else {
            sender.sendMessage(Messages.PROTECTION.unclaimCascade(
                    name, destroyed.size() - 1, totalRefunded));
            if (owedButUnpaid > 0) {
                sender.sendMessage(Messages.PROTECTION.unclaimOfflineRefund(name, owedButUnpaid));
            }
        }
        return outcome;
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
