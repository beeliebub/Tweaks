package me.beeliebub.tweaks.protection;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Legacy CommandExecutor + TabCompleter implementation for the /region (alias
// /rg) command tree. Stays on plugin.yml registration to match the rest of the
// project; the dispatcher walks args[0] for the subcommand and routes to the
// matching handle* method, and onTabComplete mirrors the same layout so each
// argument slot suggests the right value (region IDs, player names, flag
// names, targets, materials, entities, ...).
//
// Permission gating: each subcommand checks its own permission first, returning
// an empty-action no-op message when the sender lacks it. Tab completion also
// filters subcommand names by permission so the visible suggestion list mirrors
// the actions the sender can actually run.
//
// Flag-vs-material unification: /region flag accepts BOTH boolean and material
// flags via a trailing value list. Boolean flags take "true|false [target]";
// material flags take a space-separated list of block materials (which replaces
// the existing list — there's no add/remove, edit by unflagging and re-adding).
// /region flag <name> <flag> with no value lists the current rules/materials.
// /region unflag clears the rule (for boolean: per-target; for material: the
// entire list).
public final class ProtectionCommand implements CommandExecutor, TabCompleter {

    // Cap on region-name suggestions per tab keypress. Walking the live cache
    // is O(regions); 100 stays well under any client-side list rendering pain
    // point while also limiting the leak of admin region names to non-admins.
    private static final int MAX_REGION_SUGGESTIONS = 100;

    private final Tweaks plugin;
    private final ProtectionManager protection;
    private final RegionSelectionManager selections;

    public ProtectionCommand(Tweaks plugin, ProtectionManager protection, RegionSelectionManager selections) {
        this.plugin = plugin;
        this.protection = protection;
        this.selections = selections;
    }

    private PermissionManager permissions() {
        return plugin == null ? null : plugin.getPermissionManager();
    }

    // ------------------------------------------------------------------
    // Subcommand registry — drives both dispatch and tab completion.
    // ------------------------------------------------------------------

    private record Subcommand(String name, String permission, boolean visibleInUsage) {}

    private static final List<Subcommand> SUBCOMMANDS = List.of(
            new Subcommand("claim",         Permissions.PROTECTION_CLAIM,   true),
            new Subcommand("clear",         Permissions.PROTECTION_CLAIM,   true),
            new Subcommand("wand",          Permissions.PROTECTION_CLAIM,   true),
            new Subcommand("select",        Permissions.PROTECTION_INFO,    true),
            new Subcommand("unclaim",       Permissions.PROTECTION_UNCLAIM, true),
            new Subcommand("addmember",     Permissions.PROTECTION_MEMBER,  true),
            new Subcommand("removemember",  Permissions.PROTECTION_MEMBER,  true),
            new Subcommand("am",            Permissions.PROTECTION_MEMBER,  false),
            new Subcommand("rm",            Permissions.PROTECTION_MEMBER,  false),
            new Subcommand("addmanager",    Permissions.PROTECTION_MEMBER,  true),
            new Subcommand("removemanager", Permissions.PROTECTION_MEMBER,  true),
            new Subcommand("aman",          Permissions.PROTECTION_MEMBER,  false),
            new Subcommand("rman",          Permissions.PROTECTION_MEMBER,  false),
            new Subcommand("flag",          Permissions.PROTECTION_FLAG,    true),
            new Subcommand("unflag",        Permissions.PROTECTION_FLAG,    true),
            new Subcommand("flags",         Permissions.PROTECTION_FLAG,    true),
            new Subcommand("info",          Permissions.PROTECTION_INFO,    true),
            new Subcommand("i",             Permissions.PROTECTION_INFO,    false),
            new Subcommand("setparent",     Permissions.PROTECTION_CLAIM,   true),
            new Subcommand("unsetparent",   Permissions.PROTECTION_CLAIM,   true),
            new Subcommand("gui",           Permissions.PROTECTION_INFO,    true)
    );

    private static Subcommand findSubcommand(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        for (Subcommand sc : SUBCOMMANDS) {
            if (sc.name().equals(lower)) return sc;
        }
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            showRootUsage(sender);
            return true;
        }
        Subcommand sc = findSubcommand(args[0]);
        if (sc == null) {
            sender.sendMessage(Component.text("Unknown subcommand: " + args[0], NamedTextColor.RED));
            showRootUsage(sender);
            return true;
        }
        if (!sender.hasPermission(sc.permission())) {
            sender.sendMessage(Component.text(
                    "You don't have permission for /region " + sc.name() + ".",
                    NamedTextColor.RED));
            return true;
        }
        String[] sub = popFirst(args);
        switch (sc.name()) {
            case "claim"          -> handleClaim(sender, sub);
            case "clear"          -> handleClear(sender);
            case "wand"           -> handleWand(sender);
            case "select"         -> handleSelect(sender, sub);
            case "unclaim"        -> handleUnclaim(sender, sub);
            case "addmember", "am"        -> handleMember(sender, sub, true);
            case "removemember", "rm"     -> handleMember(sender, sub, false);
            case "addmanager", "aman"     -> handleManager(sender, sub, true);
            case "removemanager", "rman"  -> handleManager(sender, sub, false);
            case "flag"           -> handleFlag(sender, sub);
            case "unflag"         -> handleUnflag(sender, sub);
            case "flags"          -> handleFlags(sender, sub);
            case "info", "i"      -> handleInfo(sender, sub);
            case "setparent"      -> handleSetParent(sender, sub);
            case "unsetparent"    -> handleUnsetParent(sender, sub);
            case "gui"            -> handleGui(sender, sub);
            default               -> showRootUsage(sender);
        }
        return true;
    }

    private static String[] popFirst(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] out = new String[args.length - 1];
        System.arraycopy(args, 1, out, 0, out.length);
        return out;
    }

    // ------------------------------------------------------------------
    // Usage / help messaging
    // ------------------------------------------------------------------

    private record UsageEntry(String syntax, String description, String permission) {}

    private static final List<UsageEntry> USAGE_ENTRIES = List.of(
            new UsageEntry("/region claim <name>",
                    "Claim your wand selection as a named region.", Permissions.PROTECTION_CLAIM),
            new UsageEntry("/region clear",
                    "Drop your active wand selection.", Permissions.PROTECTION_CLAIM),
            new UsageEntry("/region wand",
                    "Receive a region selection wand.", Permissions.PROTECTION_CLAIM),
            new UsageEntry("/region select <name>",
                    "Restore a region's outline onto your selection.", Permissions.PROTECTION_INFO),
            new UsageEntry("/region unclaim <name>",
                    "Delete a region.", Permissions.PROTECTION_UNCLAIM),
            new UsageEntry("/region addmember <name> <player>",
                    "Add a member to a region.", Permissions.PROTECTION_MEMBER),
            new UsageEntry("/region removemember <name> <player>",
                    "Remove a member from a region.", Permissions.PROTECTION_MEMBER),
            new UsageEntry("/region addmanager <name> <player>",
                    "Promote a player to manager on a region (owner-only).", Permissions.PROTECTION_MEMBER),
            new UsageEntry("/region removemanager <name> <player>",
                    "Demote a manager (owner-only).", Permissions.PROTECTION_MEMBER),
            new UsageEntry("/region flag <name> <flag> [true|false|materials...]",
                    "Set a flag rule, or list rules when no value is given.", Permissions.PROTECTION_FLAG),
            new UsageEntry("/region unflag <name> <flag> [target]",
                    "Remove a flag rule (clears material list for material flags).", Permissions.PROTECTION_FLAG),
            new UsageEntry("/region flags [name]",
                    "List flag rules on a region (defaults to your current location).", Permissions.PROTECTION_FLAG),
            new UsageEntry("/region info [name]",
                    "Show region info here, or by name.", Permissions.PROTECTION_INFO),
            new UsageEntry("/region setparent <child> <parent>",
                    "Nest one region as a sub-region of another.", Permissions.PROTECTION_CLAIM),
            new UsageEntry("/region unsetparent <child>",
                    "Promote a sub-region back to top-level.", Permissions.PROTECTION_CLAIM),
            new UsageEntry("/region gui [name]",
                    "Open the management dialog for a region.", Permissions.PROTECTION_INFO)
    );

    private static void showRootUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Region commands:", NamedTextColor.YELLOW));
        int shown = 0;
        for (UsageEntry entry : USAGE_ENTRIES) {
            if (!sender.hasPermission(entry.permission())) continue;
            sender.sendMessage(Component.text("  " + entry.syntax(), NamedTextColor.GRAY)
                    .append(Component.text(" — " + entry.description(), NamedTextColor.DARK_GRAY)));
            shown++;
        }
        if (shown == 0) {
            sender.sendMessage(Component.text(
                    "  (you don't have permission for any region subcommand)",
                    NamedTextColor.DARK_GRAY));
        }
    }

    private static void showUsage(CommandSender sender, String literalPrefix) {
        for (UsageEntry entry : USAGE_ENTRIES) {
            if (entry.syntax().equals(literalPrefix) || entry.syntax().startsWith(literalPrefix + " ")) {
                if (!sender.hasPermission(entry.permission())) continue;
                sender.sendMessage(Component.text("Usage:", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("  " + entry.syntax(), NamedTextColor.GRAY)
                        .append(Component.text(" — " + entry.description(), NamedTextColor.DARK_GRAY)));
                return;
            }
        }
        showRootUsage(sender);
    }

    // ------------------------------------------------------------------
    // Subcommand handlers
    // ------------------------------------------------------------------

    // /region claim <name>  — reads the wand-driven Pos1/Pos2 selection.
    private void handleClaim(CommandSender sender, String[] args) {
        if (args.length < 1) { showUsage(sender, "/region claim <name>"); return; }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can claim regions.", NamedTextColor.RED));
            return;
        }
        String name = args[0];
        if (protection.byName(player.getWorld(), name) != null) {
            sender.sendMessage(Component.text(
                    "Region '" + name + "' already exists in this world.", NamedTextColor.RED));
            return;
        }
        RegionSelection sel = selections.get(player.getUniqueId());
        if (sel == null || !sel.isComplete()) {
            sender.sendMessage(Component.text(
                    "Set Pos1 (left-click) and Pos2 (right-click) on chunk corners with your wand first.",
                    NamedTextColor.RED));
            return;
        }
        if (sel.world() != player.getWorld()) {
            sender.sendMessage(Component.text(
                    "Your selection is in a different world. Re-select in this world.",
                    NamedTextColor.RED));
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

        Region region = new Region(name, player.getUniqueId(), List.of(),
                EnumSet.noneOf(RegionFlag.class));
        ProtectionManager.ClaimResult result = protection.tryClaim(
                region, player.getWorld(), x1, z1, x2, z2, null);
        switch (result) {
            case ID_TAKEN -> {
                sender.sendMessage(Component.text(
                        "Region '" + name + "' already exists.", NamedTextColor.RED));
                return;
            }
            case OVERLAPS_FOREIGN_REGION -> {
                sender.sendMessage(Component.text(
                        "Your selection overlaps a region you don't own. "
                                + "Adjust pos1/pos2 or ask the owner to add you.",
                        NamedTextColor.RED));
                return;
            }
            case OK -> { /* fall through to success message */ }
        }
        selections.clear(player.getUniqueId());

        int chunks = (Math.abs(cx1 - cx2) + 1) * (Math.abs(cz1 - cz2) + 1);
        sender.sendMessage(Component.text(
                "Claimed region '" + name + "' (" + chunks + " chunk" + (chunks == 1 ? "" : "s") + ").",
                NamedTextColor.GREEN));
    }

    // /region clear  — drop the player's Pos1/Pos2 selection.
    private void handleClear(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Only players have selections to clear.", NamedTextColor.RED));
            return;
        }
        if (selections.get(player.getUniqueId()) == null) {
            sender.sendMessage(Component.text(
                    "You have no active selection.", NamedTextColor.YELLOW));
            return;
        }
        selections.clear(player.getUniqueId());
        sender.sendMessage(Component.text(
                "Cleared your selection.", NamedTextColor.GREEN));
    }

    // /region wand  — hands the player the configured selection-tool material
    // (config: protection.selection-tool) with a labelled display name.
    private void handleWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Only players can receive a wand.", NamedTextColor.RED));
            return;
        }
        Material tool = plugin.getProtectionSelectionTool();
        ItemStack wand = new ItemStack(tool);
        wand.editMeta(meta -> meta.displayName(Component.text("Region Selection Wand", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)));
        var overflow = player.getInventory().addItem(wand);
        if (!overflow.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), wand);
            sender.sendMessage(Component.text(
                    "Inventory full — wand dropped at your feet.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text(
                    "Received a region selection wand. Left-click = Pos1, right-click = Pos2.",
                    NamedTextColor.GREEN));
        }
    }

    // /region select <name>  — restore a region's pos1/pos2 onto the calling
    // player's wand selection.
    private void handleSelect(CommandSender sender, String[] args) {
        if (args.length < 1) { showUsage(sender, "/region select <name>"); return; }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Only players can hold a selection.", NamedTextColor.RED));
            return;
        }
        String name = args[0];
        Region region = resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Component.text(
                    "No region named '" + name + "'.", NamedTextColor.RED));
            return;
        }
        boolean isOwner = region.isOwner(player.getUniqueId());
        if (!isOwner && !player.hasPermission(Permissions.PROTECTION_ADMIN)) {
            sender.sendMessage(Component.text(
                    "Only the region owner or admins can select '" + name + "'.",
                    NamedTextColor.RED));
            return;
        }
        Region.RegionBounds bounds = region.bounds();
        if (bounds == null) {
            sender.sendMessage(Component.text(
                    "Region '" + name + "' was claimed before bounds were tracked. "
                            + "Unclaim and re-claim it to refresh.",
                    NamedTextColor.RED));
            return;
        }

        RegionSelection sel = selections.getOrCreate(player, player.getWorld());
        sel.setPos1(GeometryUtil.chunkKey(bounds.minChunkX(), bounds.minChunkZ()));
        sel.setPos2(GeometryUtil.chunkKey(bounds.maxChunkX(), bounds.maxChunkZ()));

        int chunks = (bounds.maxChunkX() - bounds.minChunkX() + 1)
                * (bounds.maxChunkZ() - bounds.minChunkZ() + 1);
        sender.sendMessage(Component.text(
                "Selected '" + name + "' (" + chunks + " chunk" + (chunks == 1 ? "" : "s")
                        + "). Outline shown in your current world.",
                NamedTextColor.GREEN));
    }

    // /region unclaim <name>  — owner/admin-only. Managers cannot unclaim.
    private void handleUnclaim(CommandSender sender, String[] args) {
        if (args.length < 1) { showUsage(sender, "/region unclaim <name>"); return; }
        String name = args[0];
        Region region = resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return;
        }
        if (!isOwnerOrAdmin(sender, region)) {
            sender.sendMessage(Component.text(
                    "Only the region owner can unclaim '" + name + "'.",
                    NamedTextColor.RED));
            return;
        }
        if (!protection.unclaim(name)) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text(
                "Unclaimed region '" + name + "'. Pointer cleanup will run lazily.",
                NamedTextColor.GREEN));
    }

    // /region addmember|removemember <name> <player>
    @SuppressWarnings("deprecation")
    private void handleMember(CommandSender sender, String[] args, boolean add) {
        String usagePrefix = "/region " + (add ? "addmember" : "removemember") + " <name> <player>";
        if (args.length < 2) { showUsage(sender, usagePrefix); return; }
        String name = args[0];
        String playerName = args[1];

        Region region = resolveRegion(sender, name);
        if (region != null && !isOwnerManagerOrAdmin(sender, region)) {
            sender.sendMessage(Component.text(
                    "Only the region owner, a manager, or an admin can edit members.",
                    NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null) {
            sender.sendMessage(Component.text("Unknown player '" + playerName + "'.", NamedTextColor.RED));
            return;
        }

        boolean ok = add
                ? protection.addMember(name, target.getUniqueId())
                : protection.removeMember(name, target.getUniqueId());
        if (!ok) {
            sender.sendMessage(Component.text(add
                    ? "Could not add — region missing or player already a member."
                    : "Could not remove — region missing or player not a member.",
                    NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text(
                (add ? "Added " : "Removed ") + playerName + " "
                        + (add ? "to" : "from") + " '" + name + "'.",
                NamedTextColor.GREEN));
    }

    // /region addmanager|removemanager <name> <player>  — owner-only by design.
    @SuppressWarnings("deprecation")
    private void handleManager(CommandSender sender, String[] args, boolean add) {
        String usagePrefix = "/region " + (add ? "addmanager" : "removemanager") + " <name> <player>";
        if (args.length < 2) { showUsage(sender, usagePrefix); return; }
        String name = args[0];
        String playerName = args[1];

        Region region = resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return;
        }

        boolean isAdmin = !(sender instanceof Player) || sender.hasPermission(Permissions.PROTECTION_ADMIN);
        if (!isAdmin) {
            Player asPlayer = (Player) sender;
            if (!region.isOwner(asPlayer.getUniqueId())) {
                sender.sendMessage(Component.text(
                        "Only the region owner can edit the manager set.", NamedTextColor.RED));
                return;
            }
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null) {
            sender.sendMessage(Component.text("Unknown player '" + playerName + "'.", NamedTextColor.RED));
            return;
        }
        if (add && region.owner().equals(target.getUniqueId())) {
            sender.sendMessage(Component.text(
                    "The owner is implicitly a manager — promotion is unnecessary.",
                    NamedTextColor.YELLOW));
            return;
        }

        boolean ok = add
                ? protection.addManager(name, target.getUniqueId())
                : protection.removeManager(name, target.getUniqueId());
        if (!ok) {
            sender.sendMessage(Component.text(add
                    ? "Could not add — player is already a manager."
                    : "Could not remove — player is not a manager.",
                    NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text(
                (add ? "Promoted " : "Demoted ") + playerName + " "
                        + (add ? "to manager on " : "from manager on ") + "'" + name + "'.",
                NamedTextColor.GREEN));
    }

    // /region flag <name> <flag> [value tokens...]
    private void handleFlag(CommandSender sender, String[] args) {
        String usagePrefix = "/region flag <name> <flag> [true|false|materials...]";
        if (args.length < 2) { showUsage(sender, usagePrefix); return; }
        String name = args[0];
        String flagToken = args[1];
        String rawValue = joinFrom(args, 2);
        if (rawValue.isEmpty()) {
            runListSingleFlag(sender, name, flagToken);
            return;
        }
        runSetFlag(sender, protection, permissions(), name, flagToken, rawValue);
    }

    // /region unflag <name> <flag> [target]
    private void handleUnflag(CommandSender sender, String[] args) {
        String usagePrefix = "/region unflag <name> <flag> [target]";
        if (args.length < 2) { showUsage(sender, usagePrefix); return; }
        String name = args[0];
        String flagToken = args[1];
        String rawTarget = (args.length >= 3) ? args[2] : null;
        runRemoveFlag(sender, protection, permissions(), name, flagToken, rawTarget);
    }

    // /region flags [name]
    private void handleFlags(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text(
                        "Console must supply a region name: /region flags <name>.",
                        NamedTextColor.RED));
                return;
            }
            List<Region> here = protection.regionsAt(player.getLocation());
            if (here.isEmpty()) {
                sender.sendMessage(Component.text(
                        "You are standing in wilderness — no region to list flags for.",
                        NamedTextColor.YELLOW));
                return;
            }
            for (Region region : here) {
                printRegionFlags(sender, region);
            }
            return;
        }
        String name = args[0];
        Region region = resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return;
        }
        printRegionFlags(sender, region);
    }

    // /region info [name] (alias: /region i)
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text(
                        "Console must supply a region name: /region info <name>.",
                        NamedTextColor.RED));
                return;
            }
            List<Region> here = protection.regionsAt(player.getLocation());
            if (here.isEmpty()) {
                sender.sendMessage(Component.text(
                        "You are standing in wilderness — no region claimed here.",
                        NamedTextColor.YELLOW));
                return;
            }
            sender.sendMessage(Component.text(
                    "Region" + (here.size() == 1 ? "" : "s") + " at your location:",
                    NamedTextColor.AQUA));
            for (Region r : here) {
                printRegion(sender, r);
            }
            return;
        }
        String name = args[0];
        Region region = resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return;
        }
        printRegion(sender, region);
    }

    // /region setparent <child> <parent>
    private void handleSetParent(CommandSender sender, String[] args) {
        if (args.length < 2) { showUsage(sender, "/region setparent <child> <parent>"); return; }
        String child = args[0];
        String parent = args[1];
        reportParentResult(sender, child, parent, protection.setParent(child, parent));
    }

    // /region unsetparent <child>
    private void handleUnsetParent(CommandSender sender, String[] args) {
        if (args.length < 1) { showUsage(sender, "/region unsetparent <child>"); return; }
        String child = args[0];
        reportParentResult(sender, child, null, protection.setParent(child, null));
    }

    // /region gui [name]
    private void handleGui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length == 0) {
                sender.sendMessage(Component.text(
                        "Console must supply a region name: /region gui <name>.",
                        NamedTextColor.RED));
            } else {
                sender.sendMessage(Component.text(
                        "Only players can open the region dialog.", NamedTextColor.RED));
            }
            return;
        }
        if (args.length == 0) {
            List<Region> here = protection.regionsAt(player.getLocation());
            if (here.isEmpty()) {
                player.sendMessage(Component.text(
                        "You are standing in wilderness — stand inside a region, or run /region gui <name>.",
                        NamedTextColor.YELLOW));
                return;
            }
            openGuiFor(player, pickLeaf(here));
            return;
        }
        String name = args[0];
        Region region = resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return;
        }
        openGuiFor(player, region);
    }

    private void openGuiFor(Player player, Region region) {
        if (!isOwnerManagerOrAdmin(player, region)) {
            player.sendMessage(Component.text(
                    "Only the region owner, a manager, or an admin can open '" + region.id() + "'.",
                    NamedTextColor.RED));
            return;
        }
        RegionGUI.openRegionHub(player, region, protection);
    }

    // From a candidate list of overlapping regions at one chunk, return the
    // leaf — a region whose id is not the parentId of any other region in the
    // list.
    private static Region pickLeaf(List<Region> candidates) {
        if (candidates.size() == 1) return candidates.getFirst();
        Set<String> parentIds = new HashSet<>();
        for (Region r : candidates) {
            if (r.hasParent()) parentIds.add(r.parentId());
        }
        for (Region r : candidates) {
            if (!parentIds.contains(r.id())) return r;
        }
        return candidates.getFirst();
    }

    // ------------------------------------------------------------------
    // Flag set / remove / list helpers
    // ------------------------------------------------------------------

    // Package-private for ProtectionCommandTest. The legacy onCommand path
    // calls this with already-resolved tokens; the test bypasses dispatch and
    // exercises this method directly.
    static int runSetFlag(CommandSender sender, ProtectionManager protection,
                          PermissionManager permissions,
                          String name, String flagToken, String rawValue) {
        Region region = resolveRegionFor(sender, protection, name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return 0;
        }
        if (!isOwnerManagerOrAdmin(sender, region)) {
            sender.sendMessage(Component.text(
                    "Only the region owner, a manager, or an admin can edit flags.",
                    NamedTextColor.RED));
            return 0;
        }
        RegionFlag flag = parseFlagToken(sender, flagToken);
        if (flag == null) return 0;

        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Missing value. For boolean flags use 'true|false [target]'; "
                            + "for material flags supply a space-separated list of block names.",
                    NamedTextColor.RED));
            return 0;
        }

        if (flag.isMaterialFlag()) {
            return applyMaterialFlag(sender, protection, name, flag, trimmed);
        }
        if (flag.isEntityFlag()) {
            return applyEntityFlag(sender, protection, name, flag, trimmed);
        }
        return applyBooleanFlag(sender, protection, permissions, name, flag, trimmed);
    }

    static int runRemoveFlag(CommandSender sender, ProtectionManager protection,
                             PermissionManager permissions,
                             String name, String flagToken, String rawTarget) {
        Region region = resolveRegionFor(sender, protection, name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return 0;
        }
        if (!isOwnerManagerOrAdmin(sender, region)) {
            sender.sendMessage(Component.text(
                    "Only the region owner, a manager, or an admin can edit flags.",
                    NamedTextColor.RED));
            return 0;
        }
        RegionFlag flag = parseFlagToken(sender, flagToken);
        if (flag == null) return 0;

        if (flag.isMaterialFlag()) {
            if (rawTarget != null) {
                sender.sendMessage(Component.text(
                        "'" + flag.name() + "' is a material-list flag; targets do not apply. "
                                + "Use /region unflag " + name + " " + flag.name() + " to clear the list.",
                        NamedTextColor.YELLOW));
            }
            if (!protection.clearMaterials(name, flag)) {
                sender.sendMessage(Component.text(
                        "No change — '" + flag.name() + "' list is already empty.",
                        NamedTextColor.YELLOW));
                return 0;
            }
            sender.sendMessage(Component.text(
                    "Cleared '" + name + "' " + flag.name() + " material list.",
                    NamedTextColor.GREEN));
            return 1;
        }

        if (flag.isEntityFlag()) {
            if (rawTarget != null) {
                sender.sendMessage(Component.text(
                        "'" + flag.name() + "' is an entity-list flag; targets do not apply. "
                                + "Use /region unflag " + name + " " + flag.name() + " to clear the list.",
                        NamedTextColor.YELLOW));
            }
            if (!protection.clearEntities(name, flag)) {
                sender.sendMessage(Component.text(
                        "No change — '" + flag.name() + "' list is already empty.",
                        NamedTextColor.YELLOW));
                return 0;
            }
            sender.sendMessage(Component.text(
                    "Cleared '" + name + "' " + flag.name() + " entity list.",
                    NamedTextColor.GREEN));
            return 1;
        }

        FlagTarget target = resolveTarget(sender, permissions, rawTarget);
        if (target == null) return 0;

        if (!protection.removeFlag(name, flag, target)) {
            sender.sendMessage(Component.text(
                    "No rule to remove for flag " + flag.name() + " [" + target.toKey() + "].",
                    NamedTextColor.RED));
            return 0;
        }
        sender.sendMessage(Component.text(
                "Removed rule on '" + name + "' for flag " + flag.name() + " [" + target.toKey() + "].",
                NamedTextColor.GREEN));
        return 1;
    }

    private static int applyMaterialFlag(
            CommandSender sender, ProtectionManager protection,
            String name, RegionFlag flag, String rawValue) {
        EnumSet<Material> materials = parseMaterials(sender, rawValue);
        if (materials == null) return 0;
        if (materials.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Supply at least one block material name.", NamedTextColor.RED));
            return 0;
        }
        if (!protection.setMaterials(name, flag, materials)) {
            sender.sendMessage(Component.text(
                    "No change — material list already matches the requested set.",
                    NamedTextColor.YELLOW));
            return 0;
        }
        sender.sendMessage(Component.text(
                "Set '" + name + "' " + flag.name() + " to " + materials.size()
                        + " material" + (materials.size() == 1 ? "" : "s") + ".",
                NamedTextColor.GREEN));
        return 1;
    }

    private static int applyEntityFlag(
            CommandSender sender, ProtectionManager protection,
            String name, RegionFlag flag, String rawValue) {
        EnumSet<EntityType> entities = parseEntities(sender, rawValue);
        if (entities == null) return 0;
        if (entities.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Supply at least one entity type name.", NamedTextColor.RED));
            return 0;
        }
        if (!protection.setEntities(name, flag, entities)) {
            sender.sendMessage(Component.text(
                    "No change — entity list already matches the requested set.",
                    NamedTextColor.YELLOW));
            return 0;
        }
        sender.sendMessage(Component.text(
                "Set '" + name + "' " + flag.name() + " to " + entities.size()
                        + " entity type" + (entities.size() == 1 ? "" : "s") + ".",
                NamedTextColor.GREEN));
        return 1;
    }

    private static int applyBooleanFlag(
            CommandSender sender, ProtectionManager protection, PermissionManager permissions,
            String name, RegionFlag flag, String rawValue) {
        String[] tokens = rawValue.split("\\s+");
        String boolToken = tokens[0].toLowerCase(Locale.ROOT);
        boolean value;
        if ("true".equals(boolToken)) {
            value = true;
        } else if ("false".equals(boolToken)) {
            value = false;
        } else {
            sender.sendMessage(Component.text(
                    "'" + flag.name() + "' is a boolean flag; expected 'true' or 'false', got '"
                            + tokens[0] + "'.",
                    NamedTextColor.RED));
            return 0;
        }
        if (tokens.length > 2) {
            sender.sendMessage(Component.text(
                    "Too many arguments. Usage: /region flag <name> " + flag.name() + " true|false [target]",
                    NamedTextColor.RED));
            return 0;
        }
        String rawTarget = (tokens.length == 2) ? tokens[1] : null;
        FlagTarget target = resolveTarget(sender, permissions, rawTarget);
        if (target == null) return 0;

        if (!protection.setFlag(name, flag, target, value)) {
            sender.sendMessage(Component.text(
                    "No change — rule already at requested value.",
                    NamedTextColor.YELLOW));
            return 0;
        }
        sender.sendMessage(Component.text(
                "Region '" + name + "' flag " + flag.name() + " ["
                        + target.toKey() + "] set to " + value + ".",
                NamedTextColor.GREEN));
        return 1;
    }

    // /region flag <name> <flag>  — list the current rules / material entries
    // for one specific flag.
    private void runListSingleFlag(CommandSender sender, String name, String flagToken) {
        Region region = resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return;
        }
        RegionFlag flag = parseFlagToken(sender, flagToken);
        if (flag == null) return;

        if (flag.isMaterialFlag()) {
            Set<Material> materials = region.materialsFor(flag);
            if (materials.isEmpty()) {
                sender.sendMessage(Component.text(
                        "'" + name + "' has no " + flag.name() + " materials.",
                        NamedTextColor.YELLOW));
                return;
            }
            sender.sendMessage(Component.text(
                    "'" + name + "' " + flag.name() + " (" + materials.size() + "):",
                    NamedTextColor.AQUA));
            for (Material m : materials) {
                sender.sendMessage(Component.text("  • " + m.name(), NamedTextColor.GRAY));
            }
            return;
        }

        if (flag.isEntityFlag()) {
            Set<EntityType> entities = region.entitiesFor(flag);
            if (entities.isEmpty()) {
                sender.sendMessage(Component.text(
                        "'" + name + "' has no " + flag.name() + " entities.",
                        NamedTextColor.YELLOW));
                return;
            }
            sender.sendMessage(Component.text(
                    "'" + name + "' " + flag.name() + " (" + entities.size() + "):",
                    NamedTextColor.AQUA));
            for (EntityType t : entities) {
                sender.sendMessage(Component.text("  • " + t.name(), NamedTextColor.GRAY));
            }
            return;
        }

        Map<FlagTarget, Boolean> rules = region.rulesFor(flag);
        if (rules.isEmpty()) {
            sender.sendMessage(Component.text(
                    "'" + name + "' has no rules for " + flag.name() + ".",
                    NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text(
                "'" + name + "' " + flag.name() + ":", NamedTextColor.AQUA));
        for (Map.Entry<FlagTarget, Boolean> rule : rules.entrySet()) {
            sender.sendMessage(Component.text(
                    "  [" + rule.getKey().toKey() + "] = " + rule.getValue(),
                    rule.getValue() ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
    }

    private static void printRegionFlags(CommandSender sender, Region region) {
        String name = region.id();
        boolean any = false;
        if (!region.flagRules().isEmpty()) {
            sender.sendMessage(Component.text("Flag rules for '" + name + "':", NamedTextColor.AQUA));
            for (Map.Entry<RegionFlag, Map<FlagTarget, Boolean>> e : region.flagRules().entrySet()) {
                for (Map.Entry<FlagTarget, Boolean> rule : e.getValue().entrySet()) {
                    sender.sendMessage(Component.text(
                            "  " + e.getKey().name() + " [" + rule.getKey().toKey() + "] = " + rule.getValue(),
                            rule.getValue() ? NamedTextColor.GREEN : NamedTextColor.RED));
                }
            }
            any = true;
        }
        if (!region.materialFlags().isEmpty()) {
            sender.sendMessage(Component.text("Material lists for '" + name + "':", NamedTextColor.AQUA));
            for (Map.Entry<RegionFlag, Set<Material>> e : region.materialFlags().entrySet()) {
                sender.sendMessage(Component.text("  ", NamedTextColor.GRAY)
                        .append(materialListLine(e.getKey(), e.getValue())));
            }
            any = true;
        }
        if (!region.entityFlags().isEmpty()) {
            sender.sendMessage(Component.text("Entity lists for '" + name + "':", NamedTextColor.AQUA));
            for (Map.Entry<RegionFlag, Set<EntityType>> e : region.entityFlags().entrySet()) {
                sender.sendMessage(Component.text("  ", NamedTextColor.GRAY)
                        .append(entityListLine(e.getKey(), e.getValue())));
            }
            any = true;
        }
        if (!any) {
            sender.sendMessage(Component.text(
                    "Region '" + name + "' has no flag rules.", NamedTextColor.YELLOW));
        }
    }

    // Hoverable label for one material-list flag entry: "FLAG_NAME (N entries)" — hover
    // text reveals the full Material list so /rg i stays readable when lists are long.
    private static Component materialListLine(RegionFlag flag, Set<Material> materials) {
        String summary = flag.name() + " (" + materials.size() + " entr"
                + (materials.size() == 1 ? "y" : "ies") + ")";
        Component hover = Component.text(joinNames(materials, Material::name), NamedTextColor.WHITE);
        return Component.text(summary, NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(hover));
    }

    // Hoverable label for one entity-list flag entry: "FLAG_NAME (N entries)" — hover
    // text reveals the full EntityType list (e.g. CREEPER, ZOMBIE for ALLOW_MOB_SPAWN).
    private static Component entityListLine(RegionFlag flag, Set<EntityType> entities) {
        String summary = flag.name() + " (" + entities.size() + " entr"
                + (entities.size() == 1 ? "y" : "ies") + ")";
        Component hover = Component.text(joinNames(entities, EntityType::name), NamedTextColor.WHITE);
        return Component.text(summary, NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(hover));
    }

    private static <T> String joinNames(Set<T> values, java.util.function.Function<T, String> nameOf) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (T v : values) {
            if (!first) sb.append(", ");
            sb.append(nameOf.apply(v));
            first = false;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Parent / info
    // ------------------------------------------------------------------

    private static void reportParentResult(CommandSender sender, String child, String parent,
                                           ProtectionManager.SetParentResult result) {
        switch (result) {
            case OK -> {
                if (parent == null) {
                    sender.sendMessage(Component.text(
                            "Region '" + child + "' is now top-level.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text(
                            "Region '" + child + "' is now a sub-region of '" + parent + "'.",
                            NamedTextColor.GREEN));
                }
            }
            case NO_CHANGE -> sender.sendMessage(Component.text(
                    "No change — region already has that parent.", NamedTextColor.YELLOW));
            case UNKNOWN_CHILD -> sender.sendMessage(Component.text(
                    "No region named '" + child + "'.", NamedTextColor.RED));
            case UNKNOWN_PARENT -> sender.sendMessage(Component.text(
                    "Unknown parent region '" + parent + "'.", NamedTextColor.RED));
            case SELF_REFERENCE -> sender.sendMessage(Component.text(
                    "A region cannot be its own parent.", NamedTextColor.RED));
            case CYCLE -> sender.sendMessage(Component.text(
                    "Refused — that would create a parent cycle.", NamedTextColor.RED));
            case DIFFERENT_WORLDS -> sender.sendMessage(Component.text(
                    "Refused — child and parent are in different worlds.", NamedTextColor.RED));
            case NOT_CONTAINED_IN_PARENT -> sender.sendMessage(Component.text(
                    "Refused — child region is not fully contained inside its parent's bounds.",
                    NamedTextColor.RED));
            case OVERLAPS_SIBLING -> sender.sendMessage(Component.text(
                    "Refused — child would overlap another sub-region of the same parent.",
                    NamedTextColor.RED));
        }
    }

    @SuppressWarnings("deprecation")
    private static String resolveName(UUID uuid) {
        if (uuid == null) return "<unknown>";
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        String name = p.getName();
        return (name == null || name.isEmpty()) ? uuid.toString() : name;
    }

    private static void printRegion(CommandSender sender, Region region) {
        sender.sendMessage(Component.text("• " + region.id(), NamedTextColor.GREEN));
        if (region.hasParent()) {
            sender.sendMessage(Component.text(
                    "    parent: " + region.parentId(), NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("    owner: " + resolveName(region.owner()), NamedTextColor.GRAY));
        if (!region.managers().isEmpty()) {
            StringBuilder sb = new StringBuilder("    managers: ");
            boolean first = true;
            for (UUID m : region.managers()) {
                if (!first) sb.append(", ");
                sb.append(resolveName(m));
                first = false;
            }
            sender.sendMessage(Component.text(sb.toString(), NamedTextColor.GRAY));
        }
        if (region.members().isEmpty()) {
            sender.sendMessage(Component.text("    members: (none)", NamedTextColor.GRAY));
        } else {
            StringBuilder sb = new StringBuilder("    members: ");
            boolean first = true;
            for (UUID m : region.members()) {
                if (!first) sb.append(", ");
                sb.append(resolveName(m));
                first = false;
            }
            sender.sendMessage(Component.text(sb.toString(), NamedTextColor.GRAY));
        }
        if (region.flagRules().isEmpty()) {
            sender.sendMessage(Component.text("    flags: (none)", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("    flags:", NamedTextColor.GRAY));
            for (Map.Entry<RegionFlag, Map<FlagTarget, Boolean>> e : region.flagRules().entrySet()) {
                for (Map.Entry<FlagTarget, Boolean> rule : e.getValue().entrySet()) {
                    sender.sendMessage(Component.text(
                            "      " + e.getKey().name() + " [" + rule.getKey().toKey() + "] = " + rule.getValue(),
                            rule.getValue() ? NamedTextColor.GREEN : NamedTextColor.RED));
                }
            }
        }
        if (!region.materialFlags().isEmpty()) {
            sender.sendMessage(Component.text("    material flags:", NamedTextColor.GRAY));
            for (Map.Entry<RegionFlag, Set<Material>> e : region.materialFlags().entrySet()) {
                sender.sendMessage(Component.text("      ", NamedTextColor.GRAY)
                        .append(materialListLine(e.getKey(), e.getValue())));
            }
        }
        if (!region.entityFlags().isEmpty()) {
            sender.sendMessage(Component.text("    entity flags:", NamedTextColor.GRAY));
            for (Map.Entry<RegionFlag, Set<EntityType>> e : region.entityFlags().entrySet()) {
                sender.sendMessage(Component.text("      ", NamedTextColor.GRAY)
                        .append(entityListLine(e.getKey(), e.getValue())));
            }
        }
    }

    // ------------------------------------------------------------------
    // Region resolution / authorization
    // ------------------------------------------------------------------

    private Region resolveRegion(CommandSender sender, String name) {
        return resolveRegionFor(sender, protection, name);
    }

    private static Region resolveRegionFor(CommandSender sender, ProtectionManager protection, String name) {
        if (sender instanceof Player p) {
            Region scoped = protection.byName(p.getWorld(), name);
            if (scoped != null) return scoped;
        }
        return protection.byNameAnyWorld(name);
    }

    private static boolean isOwnerOrAdmin(CommandSender sender, Region region) {
        if (sender.hasPermission(Permissions.PROTECTION_ADMIN)) return true;
        if (!(sender instanceof Player p)) return true;
        return region.isOwner(p.getUniqueId());
    }

    private static boolean isOwnerManagerOrAdmin(CommandSender sender, Region region) {
        if (sender.hasPermission(Permissions.PROTECTION_ADMIN)) return true;
        if (!(sender instanceof Player p)) return true;
        return region.isOwner(p.getUniqueId()) || region.isManager(p.getUniqueId());
    }

    private static RegionFlag parseFlagToken(CommandSender sender, String token) {
        if (token == null) return null;
        String raw = token.toUpperCase(Locale.ROOT);
        try {
            return RegionFlag.valueOf(raw);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown flag '" + raw + "'.", NamedTextColor.RED));
            return null;
        }
    }

    private static EnumSet<Material> parseMaterials(CommandSender sender, String raw) {
        EnumSet<Material> out = EnumSet.noneOf(Material.class);
        if (raw == null || raw.isBlank()) return out;
        for (String token : raw.split("[\\s,]+")) {
            if (token.isEmpty()) continue;
            Material m = Material.matchMaterial(token);
            if (m == null) {
                sender.sendMessage(Component.text(
                        "Unknown material '" + token + "'.", NamedTextColor.RED));
                return null;
            }
            if (!m.isBlock()) {
                sender.sendMessage(Component.text(
                        "'" + token + "' is not a block material.", NamedTextColor.RED));
                return null;
            }
            out.add(m);
        }
        return out;
    }

    private static EnumSet<EntityType> parseEntities(CommandSender sender, String raw) {
        EnumSet<EntityType> out = EnumSet.noneOf(EntityType.class);
        if (raw == null || raw.isBlank()) return out;
        for (String token : raw.split("[\\s,]+")) {
            if (token.isEmpty()) continue;
            try {
                out.add(EntityType.valueOf(token.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Component.text(
                        "Unknown entity type '" + token + "'.", NamedTextColor.RED));
                return null;
            }
        }
        return out;
    }

    private static FlagTarget resolveTarget(CommandSender sender, PermissionManager permissions, String rawTarget) {
        FlagTarget target = FlagTarget.parseCommandArg(rawTarget);
        if (target.type() == FlagTarget.Type.GROUP) {
            if (permissions == null || !permissions.getGroups().containsKey(target.groupName())) {
                sender.sendMessage(Component.text(
                        "Unknown permission group '" + target.groupName()
                                + "'. Use 'owner', 'member', or a registered group name.",
                        NamedTextColor.RED));
                return null;
            }
        }
        return target;
    }

    private static String joinFrom(String[] args, int fromIndex) {
        if (fromIndex >= args.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = fromIndex; i < args.length; i++) {
            if (i > fromIndex) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    // ==================================================================
    // Tab completion
    // ==================================================================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0) return Collections.emptyList();

        if (args.length == 1) {
            return filterByPrefix(visibleSubcommands(sender), args[0]);
        }

        Subcommand sc = findSubcommand(args[0]);
        if (sc == null) return Collections.emptyList();
        if (!sender.hasPermission(sc.permission())) return Collections.emptyList();

        return switch (sc.name()) {
            case "claim" -> Collections.emptyList(); // <name> is freeform
            case "clear", "wand" -> Collections.emptyList();
            case "select", "unclaim" -> {
                if (args.length == 2) yield regionSuggestions(sender, args[1]);
                yield Collections.emptyList();
            }
            case "addmember", "am", "addmanager", "aman" -> memberOrManagerSuggestions(
                    sender, args, /*membersOfRegion=*/false, /*managersOfRegion=*/false);
            case "removemember", "rm" -> memberOrManagerSuggestions(
                    sender, args, /*membersOfRegion=*/true, /*managersOfRegion=*/false);
            case "removemanager", "rman" -> memberOrManagerSuggestions(
                    sender, args, /*membersOfRegion=*/false, /*managersOfRegion=*/true);
            case "flag" -> flagSuggestions(sender, args);
            case "unflag" -> unflagSuggestions(sender, args);
            case "flags", "info", "i", "gui" -> {
                if (args.length == 2) yield regionSuggestions(sender, args[1]);
                yield Collections.emptyList();
            }
            case "setparent" -> {
                if (args.length == 2 || args.length == 3) yield regionSuggestions(sender, args[args.length - 1]);
                yield Collections.emptyList();
            }
            case "unsetparent" -> {
                if (args.length == 2) yield regionSuggestions(sender, args[1]);
                yield Collections.emptyList();
            }
            default -> Collections.emptyList();
        };
    }

    private List<String> visibleSubcommands(CommandSender sender) {
        List<String> out = new ArrayList<>();
        for (Subcommand sc : SUBCOMMANDS) {
            if (!sc.visibleInUsage()) continue; // Hide aliases from the primary suggestion list.
            if (sender.hasPermission(sc.permission())) out.add(sc.name());
        }
        return out;
    }

    // Region ID suggestions: admins see every region in their world (or every
    // region for console); everyone else only sees regions they own in their
    // current world. Mirrors the Brigadier predecessor's ownership-aware filter.
    private List<String> regionSuggestions(CommandSender sender, String partial) {
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

    private List<String> memberOrManagerSuggestions(CommandSender sender, String[] args,
                                                    boolean membersOfRegion, boolean managersOfRegion) {
        if (args.length == 2) {
            return regionSuggestions(sender, args[1]);
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            if (membersOfRegion) {
                return regionMemberNames(args[1], prefix);
            }
            if (managersOfRegion) {
                return regionManagerNames(args[1], prefix);
            }
            return onlinePlayerNames(prefix);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("deprecation")
    private List<String> regionMemberNames(String regionName, String prefix) {
        Region r = protection.byNameAnyWorld(regionName);
        if (r == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (UUID member : r.members()) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(member);
            String name = p.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(name);
            }
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    private List<String> regionManagerNames(String regionName, String prefix) {
        Region r = protection.byNameAnyWorld(regionName);
        if (r == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (UUID manager : r.managers()) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(manager);
            String name = p.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(name);
            }
        }
        return out;
    }

    private static List<String> onlinePlayerNames(String prefix) {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(p.getName());
            }
        }
        return out;
    }

    // Tab completion for /region flag <name> <flag> [value tokens...]
    //
    // Layout:
    //   args[1] = "flag" subcommand (consumed before this method runs)
    //   args[2] = region name        (we suggest region IDs)
    //   args[3] = flag name          (we suggest RegionFlag values)
    //   args[4+] = value tokens      (boolean/target OR material list OR entity list)
    // Note: args[0] in this method is the subcommand literal ("flag"), so
    // when we say "args[1]" we mean the second positional arg overall.
    private List<String> flagSuggestions(CommandSender sender, String[] args) {
        if (args.length == 2) return regionSuggestions(sender, args[1]);
        if (args.length == 3) return flagNameSuggestions(args[2]);

        RegionFlag flag = tryParseFlag(args[2]);
        if (flag == null) return Collections.emptyList();
        String partial = args[args.length - 1];

        if (flag.isMaterialFlag()) {
            return blockMaterialNames(partial);
        }
        if (flag.isEntityFlag()) {
            return entityTypeNames(partial);
        }
        // Boolean: args[3] = true|false, args[4] = target
        if (args.length == 4) {
            return filterByPrefix(List.of("true", "false"), partial);
        }
        if (args.length == 5) {
            return targetNames(partial);
        }
        return Collections.emptyList();
    }

    // Tab completion for /region unflag <name> <flag> [target]
    private List<String> unflagSuggestions(CommandSender sender, String[] args) {
        if (args.length == 2) return regionSuggestions(sender, args[1]);
        if (args.length == 3) return flagNameSuggestions(args[2]);
        if (args.length == 4) {
            RegionFlag flag = tryParseFlag(args[2]);
            if (flag != null && (flag.isMaterialFlag() || flag.isEntityFlag())) {
                return Collections.emptyList(); // targets do not apply
            }
            return targetNames(args[3]);
        }
        return Collections.emptyList();
    }

    private static RegionFlag tryParseFlag(String token) {
        if (token == null) return null;
        try {
            return RegionFlag.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> flagNameSuggestions(String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (RegionFlag flag : RegionFlag.values()) {
            String n = flag.name();
            if (n.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(n);
        }
        return out;
    }

    private static List<String> blockMaterialNames(String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Material m : Material.values()) {
            if (!m.isBlock()) continue;
            String name = m.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(prefix)) out.add(name);
        }
        return out;
    }

    private static List<String> entityTypeNames(String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (EntityType t : EntityType.values()) {
            String name = t.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(prefix)) out.add(name);
        }
        return out;
    }

    private List<String> targetNames(String partial) {
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

    private static List<String> filterByPrefix(List<String> options, String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(opt);
        }
        return out;
    }
}
