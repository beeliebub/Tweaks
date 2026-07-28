package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared collaborators and boilerplate every {@link RegionSubcommand} needs — the "9 of 14
 * handlers" region-lookup/permission/tab-suggestion helpers that used to live directly on
 * {@code ProtectionCommand}.
 */
final class RegionCommandContext {

    /**
     * Cap on region-name suggestions per tab keypress. Applied BEFORE prefix filtering (walking
     * the live cache in iteration order and breaking once this many candidates have been
     * collected) — on a server with more than this many regions, suggestions are effectively
     * arbitrary-order-truncated. Pre-existing behavior, preserved as-is; see ui/CLAUDE.md.
     */
    private static final int MAX_REGION_SUGGESTIONS = 100;

    final Tweaks plugin;
    final ProtectionManager protection;
    final RegionSelectionManager selections;

    RegionCommandContext(Tweaks plugin, ProtectionManager protection, RegionSelectionManager selections) {
        this.plugin = plugin;
        this.protection = protection;
        this.selections = selections;
    }

    PermissionManager permissions() {
        return plugin == null ? null : plugin.getPermissionManager();
    }

    /**
     * Checks whether sender holds `perm`, consulting both the Bukkit attachment (set on join)
     * and PermissionManager's live effective-permission calculation (handles the case where
     * permissions were granted after the player joined and the attachment hasn't been
     * refreshed yet).
     */
    boolean hasPerm(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) return true;
        if (!(sender instanceof Player player)) return false;
        PermissionManager pm = permissions();
        return pm != null && pm.calculateEffectivePermissions(player.getUniqueId()).contains(perm);
    }

    Region resolveRegion(CommandSender sender, String name) {
        // Per-world globals are addressed via the caller's world. Lazy-init so an admin's
        // first /region flag __global__ ... in a fresh world materialises the region instead
        // of returning "no region named '__global__'".
        if (ProtectionManager.GLOBAL_REGION_ID.equals(name)) {
            if (sender instanceof Player p) {
                return protection.globalRegion(p.getWorld());
            }
            return null;
        }
        if (sender instanceof Player p) {
            Region scoped = protection.byName(p.getWorld(), name);
            if (scoped != null) return scoped;
        }
        return protection.byNameAnyWorld(name);
    }

    /**
     * World context for the mutator overloads. Returns the player's world for player senders
     * so global-region writes land in the right per-world entry; null for console (which falls
     * back to byNameAnyWorld for non-globals).
     */
    static World senderWorld(CommandSender sender) {
        return (sender instanceof Player p) ? p.getWorld() : null;
    }

    /**
     * Sends {@code handler}'s own usage entry (permission-filtered). Unlike the pre-split
     * {@code showUsage(sender, literalPrefix)}, this takes the handler directly rather than
     * string-matching a syntax prefix against a shared table — a rebuilt syntax string that
     * differed by one character used to silently fall through to the full root-usage listing
     * instead of the intended single-line usage message. Passing the handler removes that failure
     * mode entirely.
     *
     * <p>Every entry in {@code handler.usage()} today shares the same permission the caller
     * already checked before invoking {@code execute()}, so the loop's {@code continue} branch
     * never actually fires in practice — but nothing enforces that the two stay in sync. If a
     * future usage entry is ever given a stricter permission than the handler's own gate, the
     * fallback below still shows the first entry unconditionally rather than sending nothing:
     * the sender already passed the handler's permission gate to reach this call, so there's no
     * information-disclosure risk in displaying its usage.
     */
    void showUsage(CommandSender sender, RegionSubcommand handler) {
        List<RegionUsageEntry> entries = handler.usage();
        RegionUsageEntry toShow = null;
        for (RegionUsageEntry entry : entries) {
            if (entry.permission() != null && !hasPerm(sender, entry.permission())) continue;
            toShow = entry;
            break;
        }
        if (toShow == null && !entries.isEmpty()) toShow = entries.get(0);
        if (toShow == null) return;
        sender.sendMessage(Messages.PROTECTION.text(Text.USAGE_HEADER));
        sender.sendMessage(Messages.PROTECTION.usageLine(toShow.syntax(), toShow.description()));
    }

    static String joinFrom(String[] args, int fromIndex) {
        if (fromIndex >= args.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = fromIndex; i < args.length; i++) {
            if (i > fromIndex) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    // ---- Tab-completion shared helpers ------------------------------------------

    static List<String> filterByPrefix(List<String> options, String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(opt);
        }
        return out;
    }

    /**
     * Region ID suggestions: admins see every region in their world (or every region for
     * console); everyone else only sees regions they own in their current world.
     *
     * <p><b>Known asymmetry (pre-existing, preserved):</b> this filters by sender's world and
     * ownership, but {@link #resolveRegion} falls back to any world — a typed-out region name
     * can work where tab completion offered nothing. Intentional name-leak reduction, not a bug
     * to converge.
     */
    List<String> regionSuggestions(CommandSender sender, String partial) {
        boolean admin = !(sender instanceof Player) || sender.hasPermission(Permissions.PROTECTION_ADMIN);
        UUID actor = (sender instanceof Player p) ? p.getUniqueId() : null;
        String senderWorld = (sender instanceof Player p) ? p.getWorld().getName() : null;
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);

        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, Region> entry : protection.regions().entrySet()) {
            if (out.size() >= MAX_REGION_SUGGESTIONS) break;
            Region region = entry.getValue();
            if (!admin && (actor == null || !region.isOwner(actor))) continue;
            if (senderWorld != null && region.worldName() != null
                    && !region.worldName().equals(senderWorld)) continue;
            String id = region.id();
            if (!seen.add(id)) continue;
            if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(id);
            }
        }
        return out;
    }

    /** Suggest "group:<name>" for every group currently listed on the region, prefix-filtered. */
    List<String> regionGroupSuggestions(String regionName, String prefix, boolean managers) {
        Region r = protection.byNameAnyWorld(regionName);
        if (r == null) return List.of();
        List<String> groups = managers ? r.managerGroups() : r.memberGroups();
        List<String> out = new ArrayList<>();
        for (String g : groups) {
            String suggestion = "group:" + g;
            if (suggestion.startsWith(prefix)) out.add(suggestion);
        }
        return out;
    }

    /** Suggest "group:<name>" for every known permission group, prefix-filtered. */
    List<String> allGroupSuggestions(String prefix) {
        PermissionManager pm = permissions();
        if (pm == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String groupName : pm.getGroups().keySet()) {
            String suggestion = "group:" + groupName.toLowerCase(Locale.ROOT);
            if (suggestion.startsWith(prefix)) out.add(suggestion);
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    List<String> regionMemberNames(String regionName, String prefix) {
        Region r = protection.byNameAnyWorld(regionName);
        if (r == null) return List.of();
        List<String> out = new ArrayList<>();
        for (UUID member : r.members()) {
            var p = Bukkit.getOfflinePlayer(member);
            String name = p.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(name);
            }
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    List<String> regionManagerNames(String regionName, String prefix) {
        Region r = protection.byNameAnyWorld(regionName);
        if (r == null) return List.of();
        List<String> out = new ArrayList<>();
        for (UUID manager : r.managers()) {
            var p = Bukkit.getOfflinePlayer(manager);
            String name = p.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(name);
            }
        }
        return out;
    }

    static List<String> onlinePlayerNames(String prefix) {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(p.getName());
            }
        }
        return out;
    }

    List<String> targetNames(String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String fixed : List.of("owner", "manager", "member", "default")) {
            if (fixed.startsWith(prefix)) out.add(fixed);
        }
        PermissionManager pm = permissions();
        if (pm != null) {
            for (String groupName : pm.getGroups().keySet()) {
                String lower = groupName.toLowerCase(Locale.ROOT);
                if (lower.startsWith(prefix)) out.add(lower);
            }
        }
        return out;
    }

    static List<String> blockMaterialNames(String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Material m : Material.values()) {
            if (!m.isBlock()) continue;
            String name = m.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(prefix)) out.add(name);
        }
        return out;
    }

    static List<String> entityTypeNames(String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (EntityType t : EntityType.values()) {
            String name = t.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(prefix)) out.add(name);
        }
        return out;
    }

    static List<String> flagNameSuggestions(String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (var flag : me.beeliebub.tweaks.protection.region.RegionFlag.values()) {
            String n = flag.name();
            if (n.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(n);
        }
        return out;
    }
}
