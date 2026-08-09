package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import me.beeliebub.tweaks.protection.region.RegionNames;
import me.beeliebub.tweaks.utils.GeometryUtil;
import me.beeliebub.tweaks.utils.InventoryUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * /region claim &lt;name&gt; — reads the wand-driven Pos1/Pos2 selection.
 *
 * <p>Pricing &amp; enforcement flow (see {@code Permissions.PROTECTION_PURCHASEABLE} and
 * {@code Permissions.PROTECTION_ADMIN}):
 * <ol>
 *   <li>Resolve chunk count from the wand selection.</li>
 *   <li>Admins ({@code PROTECTION_ADMIN}) bypass the limit, the purchasable gate, and pay
 *       nothing. Their region is stored with cost = 0 so no refund is issued on unclaim either.</li>
 *   <li>Claim authorization is granted by {@code PROTECTION_ADMIN},
 *       {@code PROTECTION_PURCHASEABLE}, or the current world being listed in
 *       {@code protection.public-claim-worlds}. A world-authorized non-admin still owns
 *       (a) &lt; {@code max_chunks} (config) chunks already and (b) pays the same claim cost as a
 *       permission-authorized claimer; both checks run before payment.</li>
 *   <li>Cost = &Sigma; N=1..chunks of max(minimumPerChunk, floor(base / decayRate^(N - 1))), where
 *       {@code base}/{@code decayRate}/{@code minimumPerChunk} come from {@link ClaimPricing},
 *       read live from {@code protection.claim-cost} on every claim (not retroactive — repricing
 *       via {@code /tconfig} never touches an already-claimed region's stored cost). The floor
 *       guarantees every chunk costs at least {@code minimumPerChunk} Resource Rupees even at
 *       extreme scale. Canonical verification with {@link ClaimPricing#DEFAULT}: 5x5 = 25 chunks
 *       -&gt; cost = 89.</li>
 *   <li>{@code InventoryUtil.deductResourceRupees} handles payment atomically; failure aborts the
 *       claim before {@code tryClaim} runs.</li>
 *   <li>On success, the calculated cost is stamped onto the new {@code Region} via
 *       {@code withCost()} so it round-trips through {@code RegionWriter} for refund at
 *       {@code /region unclaim} time.</li>
 * </ol>
 *
 * <p><b>Refund-on-failure guard:</b> any non-{@code OK} {@link
 * ProtectionManager.ClaimResult} refunds the just-deducted cost — not just the two values that
 * happened to exist when this was first written ({@code ID_TAKEN}, {@code OVERLAPS_FOREIGN_REGION}).
 * A future third failure value added to {@code ClaimResult} without touching this method will
 * still refund correctly; only the failure MESSAGE needs a case, enforced by the inner switch
 * expression below being exhaustive.
 */
final class ClaimSubcommand implements RegionSubcommand {

    // Worlds where any player may claim without holding PROTECTION_PURCHASEABLE.
    static final String PUBLIC_CLAIM_WORLDS_PATH = "protection.public-claim-worlds";

    // Live read (no constructor-time caching) so a /tconfig protection.public-claim-worlds edit
    // takes effect on the very next /region claim, matching the sethome-disabled-worlds and
    // portal-world lists. Matching is on the NAMESPACED world key (e.g. "minecraft:overworld"),
    // case-insensitively - deliberately not Region#worldName(), which stores the bare
    // world.getName(); the two are different identifiers and must never be compared to each other.
    static boolean isPublicClaimWorld(Tweaks plugin, World world) {
        if (plugin == null || world == null) return false;
        FileConfiguration config = plugin.getConfig();
        if (config == null) return false;
        String worldKey = world.getKey().asString();
        for (String entry : config.getStringList(PUBLIC_CLAIM_WORLDS_PATH)) {
            if (entry.equalsIgnoreCase(worldKey)) return true;
        }
        return false;
    }

    @Override public String name() { return "claim"; }
    // No dispatcher-level gate: claim authorization is world-dependent, and ProtectionCommand's
    // outer gate runs before any world is resolved, so the whole decision lives in execute()
    // alongside the admin bypass. visibleTo() below keeps the subcommand out of tab completion
    // and root usage for players who cannot claim where they are standing.
    @Override public String permission() { return null; }
    @Override public int minArgs() { return 1; }
    @Override public List<RegionUsageEntry> usage() {
        return List.of(new RegionUsageEntry(Messages.PROTECTION.value(Text.USAGE_CLAIM_SYNTAX),
                Messages.PROTECTION.value(Text.USAGE_CLAIM_DESCRIPTION),
                null));
    }

    @Override
    public boolean visibleTo(RegionCommandContext ctx, CommandSender sender) {
        if (ctx.hasPerm(sender, Permissions.PROTECTION_PURCHASEABLE)) return true;
        if (ctx.hasPerm(sender, Permissions.PROTECTION_ADMIN)) return true;
        return sender instanceof Player player && isPublicClaimWorld(ctx.plugin, player.getWorld());
    }

    @Override
    public void execute(RegionCommandContext ctx, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.CLAIM_ONLY_PLAYERS));
            return;
        }
        // Authorization runs before every other check so an unauthorized player gets an immediate
        // denial instead of first being walked through name/selection validation. Three
        // independent grants: PROTECTION_ADMIN, PROTECTION_PURCHASEABLE, or the player's current
        // world being listed under protection.public-claim-worlds.
        boolean adminBypass = ctx.hasPerm(player, Permissions.PROTECTION_ADMIN);
        if (!adminBypass
                && !ctx.hasPerm(player, Permissions.PROTECTION_PURCHASEABLE)
                && !isPublicClaimWorld(ctx.plugin, player.getWorld())) {
            player.sendMessage(Messages.PROTECTION.text(Text.COMMAND_PERMISSION, name()));
            return;
        }
        if (args.length < 1) { ctx.showUsage(sender, this); return; }
        String name = RegionNames.normalize(args[0]);
        if (!RegionNames.isValid(name)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NAME_INVALID));
            return;
        }
        if (RegionNames.isReserved(name)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NAME_RESERVED, name));
            return;
        }
        if (ctx.protection.byName(player.getWorld(), name) != null) {
            sender.sendMessage(Messages.PROTECTION.text(Text.CLAIM_EXISTS_WORLD, name));
            return;
        }
        RegionSelection sel = ctx.selections.get(player.getUniqueId());
        if (sel == null || !sel.isComplete()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.CLAIM_SELECTION_REQUIRED));
            return;
        }
        if (sel.world() != player.getWorld()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.CLAIM_SELECTION_OTHER_WORLD));
            return;
        }

        int cx1 = GeometryUtil.chunkX(sel.pos1());
        int cz1 = GeometryUtil.chunkZ(sel.pos1());
        int cx2 = GeometryUtil.chunkX(sel.pos2());
        int cz2 = GeometryUtil.chunkZ(sel.pos2());
        int x1 = cx1 << 4;
        int z1 = cz1 << 4;
        int x2 = (cx2 << 4) + 15;
        int z2 = (cz2 << 4) + 15;

        int chunks = (Math.abs(cx1 - cx2) + 1) * (Math.abs(cz1 - cz2) + 1);

        // Admin shortcut: skip limit + purchasable + payment. cost = 0 means
        // unclaim issues no refund (matches the "free" intent).
        int cost = 0;
        if (!adminBypass) {
            int maxChunks = ctx.plugin.getConfig().getInt("max_chunks", 200);
            int alreadyOwned = ctx.protection.getTotalChunksOwned(player.getUniqueId());
            if (alreadyOwned + chunks > maxChunks) {
                player.sendMessage(Messages.PROTECTION.claimLimit(alreadyOwned, maxChunks, chunks));
                return;
            }
            cost = computeClaimCost(chunks, ClaimPricing.from(ctx.plugin.getConfig()));
            if (!InventoryUtil.deductResourceRupees(player, cost)) {
                int balance = InventoryUtil.getResourceRupeeBalance(player);
                player.sendMessage(Messages.PROTECTION.claimUnaffordable(cost, balance));
                return;
            }
        }

        Region region = new Region(name, player.getUniqueId(), List.of(),
                EnumSet.noneOf(RegionFlag.class)).withCost(cost);
        ProtectionManager.ClaimResult result = ctx.protection.tryClaim(
                region, player.getWorld(), x1, z1, x2, z2, null);

        if (result != ProtectionManager.ClaimResult.OK) {
            // ANY non-OK result refunds — see class Javadoc on why this is a boolean gate
            // rather than an enumeration of the two failure values that exist today.
            if (cost > 0) InventoryUtil.addResourceRupees(player, cost);
            Component reason = switch (result) {
                case ID_TAKEN -> Messages.PROTECTION.text(Text.CLAIM_EXISTS, name);
                case OVERLAPS_FOREIGN_REGION -> Messages.PROTECTION.text(Text.CLAIM_OVERLAPS_FOREIGN);
                // Genuinely unreachable given the outer `if` guard — but a `default` branch here
                // would silently swallow any future ClaimResult value instead of failing to
                // compile, defeating the whole point of an exhaustive switch expression. A real
                // `case OK` keeps that safety net while degrading to a logged, player-visible
                // message instead of an uncaught exception surfacing through Bukkit's command
                // dispatcher (which would otherwise show the player a generic internal-error
                // message and hide the real cause) if this invariant is ever violated by a
                // future refactor.
                case OK -> {
                    ctx.plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "ClaimSubcommand reached its refund-guard fallback with result == OK "
                                    + "for region '" + name + "' — this should be unreachable; "
                                    + "the outer `if (result != OK)` guard may have regressed.");
                    yield Messages.PROTECTION.text(Text.CLAIM_UNEXPECTED);
                }
            };
            sender.sendMessage(reason);
            return;
        }
        ctx.selections.clear(player.getUniqueId());

        if (adminBypass) {
            player.sendMessage(Messages.PROTECTION.claimAdminSuccess(name, chunks));
        } else {
            player.sendMessage(Messages.PROTECTION.claimSuccess(name, chunks, cost));
        }
    }

    @Override
    public List<String> tabComplete(RegionCommandContext ctx, CommandSender sender, String[] rawArgs) {
        // claim's <name> is freeform (not an existing region) — nothing sensible to suggest,
        // unchanged from the pre-split behavior.
        return Collections.emptyList();
    }

    // Compute the Resource Rupee price of an N-chunk claim. Per-chunk cost tapers
    // geometrically: the first chunk costs pricing.base(), each subsequent chunk costs
    // floor(base / decayRate^(N - 1)) but never less than minimumPerChunk. The floor is
    // deliberate — it keeps "tax" non-zero at extreme scale so megaclaims still cost
    // something, and with ClaimPricing.DEFAULT it's the property the canonical 5x5 = 89
    // test pins down.
    //
    // Accumulates in `long`, not `int`: base/minimumPerChunk are only bounded above by
    // Double.MAX_VALUE/Integer.MAX_VALUE at the /tconfig edit boundary (core/config/ConfigRegistry
    // has no upper cap tying them to max_chunks), so a single admin-set extreme value - e.g.
    // minimum-per-chunk near 2^30 on a multi-chunk claim - would otherwise overflow a plain `int`
    // accumulator and wrap around to a small or even negative total, letting a claim go through for
    // free. The final result is clamped to Integer.MAX_VALUE rather than allowed to overflow the
    // int this method returns.
    //
    // Package-visible (not private) so ClaimCostTest can call it directly instead of via
    // reflection on the old private ProtectionCommand.computeClaimCost.
    static int computeClaimCost(int chunks, ClaimPricing pricing) {
        if (chunks <= 0) return 0;
        long total = 0L;
        for (int n = 1; n <= chunks; n++) {
            // Math.floor + a double->long narrowing cast saturates at Long.MAX_VALUE for an
            // out-of-range double (JLS 5.1.3) rather than wrapping, so perChunk itself is safe;
            // only the running total needed the int -> long widening above.
            long perChunk = (long) Math.floor(pricing.base() / Math.pow(pricing.decayRate(), n - 1));
            if (perChunk < pricing.minimumPerChunk()) perChunk = pricing.minimumPerChunk();
            total += perChunk;
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }
}
