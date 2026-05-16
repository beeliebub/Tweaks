package me.beeliebub.tweaks.protection;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Brigadier-backed /region command tree. Registered via Paper's
// LifecycleEventManager so the dispatcher is synced to clients during the
// COMMANDS lifecycle phase — clients then validate arguments before sending
// the packet, dropping malformed input on the client side and saving
// server-thread cycles.
//
// Why .requires() instead of in-executor permission checks: the requires
// predicate runs during tree synchronization, so players who lack a node's
// permission never see that branch at all (the command literally does not
// exist for them in their client-side tab completion). Falling back to an
// in-executor check would expose the syntax but reject at runtime — less
// pleasant and gives away protection internals to non-admins.
//
// Flag-vs-material unification: /region flag accepts BOTH boolean and material
// flags via a greedy "value" argument. Boolean flags take "true|false [target]";
// material flags take a space-separated list of block materials (which replaces
// the existing list — there's no add/remove, edit by unflagging and re-adding).
// /region flag <name> <flag> with no value lists the current rules/materials.
// /region unflag clears the rule (for boolean: per-target; for material: the
// entire list).
@SuppressWarnings("UnstableApiUsage")
public final class ProtectionCommand {

    // Suggestion providers need access to the manager/permissions, but
    // Brigadier's SuggestionProvider doesn't carry user data. We thread them
    // through ThreadLocals scoped to the lifecycle handler invocation.
    private static final ThreadLocal<ProtectionManager> CURRENT_MANAGER = new ThreadLocal<>();
    private static final ThreadLocal<PermissionManager> CURRENT_PERMISSIONS = new ThreadLocal<>();

    private static final SuggestionProvider<CommandSourceStack> FLAG_SUGGESTIONS = (ctx, builder) -> {
        for (RegionFlag flag : RegionFlag.values()) {
            if (flag.name().toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(flag.name());
            }
        }
        return builder.buildFuture();
    };

    // Suggestions for the `<value>` greedy arg of /region flag. The provider
    // branches on the previously-bound `flag` arg:
    //   * boolean flag → first token suggests "true|false"; once a space has
    //     been typed, subsequent tokens suggest TARGET_SUGGESTIONS.
    //   * material flag → suggest block Material names, space-separated and
    //     re-suggesting only from the last whitespace token so the user can
    //     pile materials onto a single command line without re-completing
    //     the names they have already chosen.
    // The greedy arg shares its prefix with the user's raw input, so we drive
    // off `builder.getRemaining()` rather than building a fresh prefix.
    private static final SuggestionProvider<CommandSourceStack> FLAG_VALUE_SUGGESTIONS = (ctx, builder) -> {
        RegionFlag flag = peekFlag(ctx);
        if (flag == null) return builder.buildFuture();

        String remaining = builder.getRemaining();
        if (flag.isMaterialFlag()) {
            return suggestMaterialTokens(builder, remaining);
        }
        return suggestBoolThenTarget(builder, remaining);
    };

    private static RegionFlag peekFlag(CommandContext<CommandSourceStack> ctx) {
        try {
            String raw = StringArgumentType.getString(ctx, "flag").toUpperCase(Locale.ROOT);
            return RegionFlag.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestMaterialTokens(com.mojang.brigadier.suggestion.SuggestionsBuilder builder, String remaining) {
        int lastSpace = remaining.lastIndexOf(' ');
        String prefix = (lastSpace >= 0 ? remaining.substring(lastSpace + 1) : remaining)
                .toLowerCase(Locale.ROOT);
        com.mojang.brigadier.suggestion.SuggestionsBuilder offset =
                lastSpace >= 0 ? builder.createOffset(builder.getStart() + lastSpace + 1) : builder;
        for (Material m : Material.values()) {
            if (!m.isBlock()) continue;
            String name = m.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(prefix)) offset.suggest(name);
        }
        return offset.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestBoolThenTarget(com.mojang.brigadier.suggestion.SuggestionsBuilder builder, String remaining) {
        int firstSpace = remaining.indexOf(' ');
        if (firstSpace < 0) {
            String prefix = remaining.toLowerCase(Locale.ROOT);
            if ("true".startsWith(prefix)) builder.suggest("true");
            if ("false".startsWith(prefix)) builder.suggest("false");
            return builder.buildFuture();
        }
        String targetPrefix = remaining.substring(firstSpace + 1).toLowerCase(Locale.ROOT);
        com.mojang.brigadier.suggestion.SuggestionsBuilder offset =
                builder.createOffset(builder.getStart() + firstSpace + 1);
        suggestTargets(offset, targetPrefix);
        return offset.buildFuture();
    }

    private static void suggestTargets(com.mojang.brigadier.suggestion.SuggestionsBuilder builder, String prefix) {
        if ("owner".startsWith(prefix)) builder.suggest("owner");
        if ("member".startsWith(prefix)) builder.suggest("member");
        if ("default".startsWith(prefix)) builder.suggest("default");
        PermissionManager pm = CURRENT_PERMISSIONS.get();
        if (pm != null) {
            for (String groupName : pm.getGroups().keySet()) {
                String lower = groupName.toLowerCase(Locale.ROOT);
                if (lower.startsWith(prefix)) builder.suggest(lower);
            }
        }
    }

    // Cap on region-name suggestions per tab keypress. Brigadier's wire format
    // tolerates thousands but the client's scrollable GUI surfaces at most a
    // few dozen at once, and dumping hundreds of names also leaks knowledge of
    // every admin region to the player. 100 is generous for normal use while
    // keeping the suggestion packet small.
    private static final int MAX_REGION_SUGGESTIONS = 100;

    // Region-name suggestion provider with ownership-aware filtering:
    //   * console / players holding PROTECTION_ADMIN see every region;
    //   * everyone else only sees the regions they own.
    // Walking the live cache once per keypress is O(regions), which is fine
    // for any realistic server (the cache is in-memory and the cap below
    // short-circuits the loop on large servers).
    private static final SuggestionProvider<CommandSourceStack> REGION_ID_SUGGESTIONS = (ctx, builder) -> {
        ProtectionManager mgr = CURRENT_MANAGER.get();
        if (mgr == null) return builder.buildFuture();
        CommandSender sender = ctx.getSource().getSender();
        boolean admin = !(sender instanceof Player) || sender.hasPermission(Permissions.PROTECTION_ADMIN);
        java.util.UUID actor = (sender instanceof Player p) ? p.getUniqueId() : null;
        String prefix = builder.getRemainingLowerCase();
        int count = 0;
        for (Map.Entry<String, Region> entry : mgr.regions().entrySet()) {
            if (count >= MAX_REGION_SUGGESTIONS) break;
            if (!admin && (actor == null || !entry.getValue().isOwner(actor))) continue;
            String id = entry.getKey();
            if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(id);
                count++;
            }
        }
        return builder.buildFuture();
    };

    // Online players — used by /region addmember.
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYER_SUGGESTIONS = (ctx, builder) -> {
        String prefix = builder.getRemainingLowerCase();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(p.getName());
            }
        }
        return builder.buildFuture();
    };

    // Members of the region referenced by the `name` argument earlier in the
    // path — used by /region removemember so the player isn't asked to
    // remember UUIDs of offline members.
    private static final SuggestionProvider<CommandSourceStack> REGION_MEMBER_SUGGESTIONS = (ctx, builder) -> {
        ProtectionManager mgr = CURRENT_MANAGER.get();
        String prefix = builder.getRemainingLowerCase();
        if (mgr == null) return builder.buildFuture();
        String regionName;
        try {
            regionName = StringArgumentType.getString(ctx, "name");
        } catch (IllegalArgumentException e) {
            return builder.buildFuture();
        }
        Region r = mgr.regions().get(regionName);
        if (r == null) return builder.buildFuture();
        for (java.util.UUID member : r.members()) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(member);
            String name = p.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> TARGET_SUGGESTIONS = (ctx, builder) -> {
        suggestTargets(builder, builder.getRemainingLowerCase());
        return builder.buildFuture();
    };

    private ProtectionCommand() {}

    public static void register(Tweaks plugin, ProtectionManager protection, RegionSelectionManager selections) {
        plugin.getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS, event -> {
                    CURRENT_MANAGER.set(protection);
                    CURRENT_PERMISSIONS.set(plugin.getPermissionManager());
                    try {
                        event.registrar().register(
                                buildTree(protection, selections, plugin.getPermissionManager()).build(),
                                "Tweaks land protection commands",
                                java.util.List.of("rg")
                        );
                    } finally {
                        CURRENT_MANAGER.remove();
                        CURRENT_PERMISSIONS.remove();
                    }
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTree(
            ProtectionManager protection, RegionSelectionManager selections, PermissionManager permissions) {
        return Commands.literal("region")
                .executes(ProtectionCommand::showRootUsage)
                .then(claimNode(protection, selections))
                .then(clearNode(selections))
                .then(selectNode(protection, selections))
                .then(unclaimNode(protection))
                .then(memberNode(protection, "addmember", Permissions.PROTECTION_MEMBER, true))
                .then(memberNode(protection, "removemember", Permissions.PROTECTION_MEMBER, false))
                .then(flagNode(protection, permissions))
                .then(unflagNode(protection, permissions))
                .then(flagsNode(protection))
                .then(infoNode(protection))
                .then(setParentNode(protection))
                .then(unsetParentNode(protection));
    }

    // ------------------------------------------------------------------
    // Usage / help messaging
    //
    // Brigadier's default "Unknown or incomplete command" error is unhelpful
    // for new players. We add a fallback `.executes(usage)` on every literal
    // node that lacks one of its own; the usage handler prints the relevant
    // syntax in the project's house style (yellow header + dim syntax lines)
    // and is gated by .requires(), so users only see syntax for branches
    // they have permission to run.
    //
    // The root `/region` invocation falls through to showRootUsage, which
    // walks USAGE_ENTRIES and prints one line per subcommand the sender
    // can execute. Entries the sender lacks permission for are silently
    // omitted, preserving the "permissions-aware tab completion" contract.
    // ------------------------------------------------------------------

    private record UsageEntry(String syntax, String description, String permission) {}

    private static final java.util.List<UsageEntry> USAGE_ENTRIES = java.util.List.of(
            new UsageEntry("/region claim <name>",
                    "Claim your wand selection as a named region.", Permissions.PROTECTION_CLAIM),
            new UsageEntry("/region clear",
                    "Drop your active wand selection.", Permissions.PROTECTION_CLAIM),
            new UsageEntry("/region select <name>",
                    "Restore a region's outline onto your selection.", Permissions.PROTECTION_INFO),
            new UsageEntry("/region unclaim <name>",
                    "Delete a region.", Permissions.PROTECTION_UNCLAIM),
            new UsageEntry("/region addmember <name> <player>",
                    "Add a member to a region.", Permissions.PROTECTION_MEMBER),
            new UsageEntry("/region removemember <name> <player>",
                    "Remove a member from a region.", Permissions.PROTECTION_MEMBER),
            new UsageEntry("/region flag <name> <flag> [true|false|materials...]",
                    "Set a flag rule, or list rules when no value is given.", Permissions.PROTECTION_FLAG),
            new UsageEntry("/region unflag <name> <flag> [target]",
                    "Remove a flag rule (clears material list for material flags).", Permissions.PROTECTION_FLAG),
            new UsageEntry("/region flags <name>",
                    "List every flag rule on a region.", Permissions.PROTECTION_FLAG),
            new UsageEntry("/region info [name]",
                    "Show region info here, or by name.", Permissions.PROTECTION_INFO),
            new UsageEntry("/region setparent <child> <parent>",
                    "Nest one region as a sub-region of another.", Permissions.PROTECTION_CLAIM),
            new UsageEntry("/region unsetparent <child>",
                    "Promote a sub-region back to top-level.", Permissions.PROTECTION_CLAIM)
    );

    private static int showRootUsage(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
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
        return Command.SINGLE_SUCCESS;
    }

    // Build a fallback .executes() handler that prints the usage line for one
    // specific subcommand. Bound by syntax-prefix matching against
    // USAGE_ENTRIES so each subcommand picks up the correct line without
    // anyone hand-wiring two strings in two places.
    private static Command<CommandSourceStack> usageFor(String literalPrefix) {
        return ctx -> {
            CommandSender sender = ctx.getSource().getSender();
            for (UsageEntry entry : USAGE_ENTRIES) {
                if (entry.syntax().equals(literalPrefix) || entry.syntax().startsWith(literalPrefix + " ")) {
                    if (!sender.hasPermission(entry.permission())) continue;
                    sender.sendMessage(Component.text("Usage:", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("  " + entry.syntax(), NamedTextColor.GRAY)
                            .append(Component.text(" — " + entry.description(), NamedTextColor.DARK_GRAY)));
                    return Command.SINGLE_SUCCESS;
                }
            }
            return showRootUsage(ctx);
        };
    }

    // /region claim <name>  — reads the wand-driven Pos1/Pos2 selection.
    private static LiteralArgumentBuilder<CommandSourceStack> claimNode(
            ProtectionManager protection, RegionSelectionManager selections) {
        return Commands.literal("claim")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_CLAIM))
                .executes(usageFor("/region claim <name>"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> runClaim(ctx, protection, selections)));
    }

    private static int runClaim(
            CommandContext<CommandSourceStack> ctx,
            ProtectionManager protection,
            RegionSelectionManager selections) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can claim regions.", NamedTextColor.RED));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        if (protection.regions().containsKey(name)) {
            sender.sendMessage(Component.text("Region '" + name + "' already exists.", NamedTextColor.RED));
            return 0;
        }

        RegionSelection sel = selections.get(player.getUniqueId());
        if (sel == null || !sel.isComplete()) {
            sender.sendMessage(Component.text(
                    "Set Pos1 (left-click) and Pos2 (right-click) on chunk corners with your wand first.",
                    NamedTextColor.RED));
            return 0;
        }
        if (sel.world() != player.getWorld()) {
            sender.sendMessage(Component.text(
                    "Your selection is in a different world. Re-select in this world.",
                    NamedTextColor.RED));
            return 0;
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
                return 0;
            }
            case OVERLAPS_FOREIGN_REGION -> {
                sender.sendMessage(Component.text(
                        "Your selection overlaps a region you don't own. "
                                + "Adjust pos1/pos2 or ask the owner to add you.",
                        NamedTextColor.RED));
                return 0;
            }
            case OK -> { /* fall through to success message */ }
        }
        selections.clear(player.getUniqueId());

        int chunks = (Math.abs(cx1 - cx2) + 1) * (Math.abs(cz1 - cz2) + 1);
        sender.sendMessage(Component.text(
                "Claimed region '" + name + "' (" + chunks + " chunk" + (chunks == 1 ? "" : "s") + ").",
                NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    // /region clear  — drop the player's Pos1/Pos2 selection. The particle
    // outline disappears on the next ticker pass (≤5 ticks).
    private static LiteralArgumentBuilder<CommandSourceStack> clearNode(RegionSelectionManager selections) {
        return Commands.literal("clear")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_CLAIM))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text(
                                "Only players have selections to clear.", NamedTextColor.RED));
                        return 0;
                    }
                    if (selections.get(player.getUniqueId()) == null) {
                        sender.sendMessage(Component.text(
                                "You have no active selection.", NamedTextColor.YELLOW));
                        return 0;
                    }
                    selections.clear(player.getUniqueId());
                    sender.sendMessage(Component.text(
                            "Cleared your selection.", NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                });
    }

    // /region select <name>  — restore a region's pos1/pos2 onto the calling
    // player's wand selection so the particle outline renders that region's
    // chunk-AABB. Useful for inspecting an existing claim before reshaping it
    // or as a visual confirmation aid for admins.
    //
    // Gating: the .requires() predicate keeps the branch out of tab completion
    // for players without PROTECTION_INFO, then the executor additionally
    // checks region ownership — only the owner OR PROTECTION_ADMIN may select
    // a region, since the outline visualizes the protected area.
    private static LiteralArgumentBuilder<CommandSourceStack> selectNode(
            ProtectionManager protection, RegionSelectionManager selections) {
        return Commands.literal("select")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_INFO))
                .executes(usageFor("/region select <name>"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .executes(ctx -> runSelect(ctx, protection, selections)));
    }

    private static int runSelect(
            CommandContext<CommandSourceStack> ctx,
            ProtectionManager protection,
            RegionSelectionManager selections) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Only players can hold a selection.", NamedTextColor.RED));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        Region region = protection.regions().get(name);
        if (region == null) {
            sender.sendMessage(Component.text(
                    "No region named '" + name + "'.", NamedTextColor.RED));
            return 0;
        }
        boolean isOwner = region.isOwner(player.getUniqueId());
        if (!isOwner && !player.hasPermission(Permissions.PROTECTION_ADMIN)) {
            sender.sendMessage(Component.text(
                    "Only the region owner or admins can select '" + name + "'.",
                    NamedTextColor.RED));
            return 0;
        }
        Region.RegionBounds bounds = region.bounds();
        if (bounds == null) {
            sender.sendMessage(Component.text(
                    "Region '" + name + "' was claimed before bounds were tracked. "
                            + "Unclaim and re-claim it to refresh.",
                    NamedTextColor.RED));
            return 0;
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
        return Command.SINGLE_SUCCESS;
    }

    // /region unclaim <name>
    private static LiteralArgumentBuilder<CommandSourceStack> unclaimNode(ProtectionManager protection) {
        return Commands.literal("unclaim")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_UNCLAIM))
                .executes(usageFor("/region unclaim <name>"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String name = StringArgumentType.getString(ctx, "name");
                            if (!protection.unclaim(name)) {
                                sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
                                return 0;
                            }
                            sender.sendMessage(Component.text("Unclaimed region '" + name + "'. Pointer cleanup will run lazily.",
                                    NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    // /region addmember|removemember <name> <player>
    private static LiteralArgumentBuilder<CommandSourceStack> memberNode(
            ProtectionManager protection, String literal, String permission, boolean add) {
        SuggestionProvider<CommandSourceStack> playerSuggestions =
                add ? ONLINE_PLAYER_SUGGESTIONS : REGION_MEMBER_SUGGESTIONS;
        String usagePrefix = "/region " + literal + " <name> <player>";
        return Commands.literal(literal)
                .requires(s -> s.getSender().hasPermission(permission))
                .executes(usageFor(usagePrefix))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .executes(usageFor(usagePrefix))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(playerSuggestions)
                                .executes(ctx -> runMember(ctx, protection, add))));
    }

    @SuppressWarnings("deprecation") // getOfflinePlayer(String) is the only way to resolve from a literal name arg
    private static int runMember(CommandContext<CommandSourceStack> ctx, ProtectionManager protection, boolean add) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        String playerName = StringArgumentType.getString(ctx, "player");

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null) {
            sender.sendMessage(Component.text("Unknown player '" + playerName + "'.", NamedTextColor.RED));
            return 0;
        }

        boolean ok = add
                ? protection.addMember(name, target.getUniqueId())
                : protection.removeMember(name, target.getUniqueId());
        if (!ok) {
            sender.sendMessage(Component.text(add
                    ? "Could not add — region missing or player already a member."
                    : "Could not remove — region missing or player not a member.",
                    NamedTextColor.RED));
            return 0;
        }
        sender.sendMessage(Component.text(
                (add ? "Added " : "Removed ") + playerName + " "
                        + (add ? "to" : "from") + " '" + name + "'.",
                NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    // /region flag <name> <flag>                     — list this flag's rules/materials
    // /region flag <name> <flag> true|false [target] — set boolean rule
    // /region flag <name> <flag> <material> [...]    — replace material list (material flags only)
    //
    // value is a greedy string so a multi-material list ("stone dirt grass_block")
    // and the optional [target] both parse via a single arg slot. The executor
    // branches on flag.isMaterialFlag() to interpret the tokens.
    private static LiteralArgumentBuilder<CommandSourceStack> flagNode(
            ProtectionManager protection, PermissionManager permissions) {
        return Commands.literal("flag")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_FLAG))
                .executes(usageFor("/region flag <name> <flag> [true|false|materials...]"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .executes(usageFor("/region flag <name> <flag> [true|false|materials...]"))
                        .then(Commands.argument("flag", StringArgumentType.word())
                                .suggests(FLAG_SUGGESTIONS)
                                .executes(ctx -> runListSingleFlag(ctx, protection))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .suggests(FLAG_VALUE_SUGGESTIONS)
                                        .executes(ctx -> runSetFlag(ctx, protection, permissions)))));
    }

    // /region unflag <name> <flag> [target]
    // For boolean flags: removes the (flag, target) rule (DEFAULT if no target).
    // For material flags: clears the entire material list (target ignored).
    private static LiteralArgumentBuilder<CommandSourceStack> unflagNode(
            ProtectionManager protection, PermissionManager permissions) {
        return Commands.literal("unflag")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_FLAG))
                .executes(usageFor("/region unflag <name> <flag> [target]"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .executes(usageFor("/region unflag <name> <flag> [target]"))
                        .then(Commands.argument("flag", StringArgumentType.word())
                                .suggests(FLAG_SUGGESTIONS)
                                .executes(ctx -> runRemoveFlag(ctx, protection, permissions, null))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(TARGET_SUGGESTIONS)
                                        .executes(ctx -> runRemoveFlag(ctx, protection, permissions,
                                                StringArgumentType.getString(ctx, "target"))))));
    }

    // /region flags <name>  — list every (flag, target, value) rule on a region.
    private static LiteralArgumentBuilder<CommandSourceStack> flagsNode(ProtectionManager protection) {
        return Commands.literal("flags")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_FLAG))
                .executes(usageFor("/region flags <name>"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .executes(ctx -> runListFlags(ctx, protection)));
    }

    // /region info [name]  — summary of a region, or of every region at the
    // sender's current location if no name is given.
    private static LiteralArgumentBuilder<CommandSourceStack> infoNode(ProtectionManager protection) {
        return Commands.literal("info")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_INFO))
                .executes(ctx -> runInfoHere(ctx, protection))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .executes(ctx -> runInfoNamed(ctx, protection)));
    }

    private static int runSetFlag(
            CommandContext<CommandSourceStack> ctx,
            ProtectionManager protection,
            PermissionManager permissions) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        if (!protection.regions().containsKey(name)) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return 0;
        }
        RegionFlag flag = parseFlagArg(sender, ctx);
        if (flag == null) return 0;

        String rawValue = StringArgumentType.getString(ctx, "value").trim();
        if (rawValue.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Missing value. For boolean flags use 'true|false [target]'; "
                            + "for material flags supply a space-separated list of block names.",
                    NamedTextColor.RED));
            return 0;
        }

        return flag.isMaterialFlag()
                ? applyMaterialFlag(sender, protection, name, flag, rawValue)
                : applyBooleanFlag(sender, protection, permissions, name, flag, rawValue);
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
        return Command.SINGLE_SUCCESS;
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
        return Command.SINGLE_SUCCESS;
    }

    private static int runRemoveFlag(
            CommandContext<CommandSourceStack> ctx,
            ProtectionManager protection,
            PermissionManager permissions,
            String rawTarget) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        if (!protection.regions().containsKey(name)) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return 0;
        }
        RegionFlag flag = parseFlagArg(sender, ctx);
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
            return Command.SINGLE_SUCCESS;
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
        return Command.SINGLE_SUCCESS;
    }

    // /region flag <name> <flag>  — list the current rules / material entries
    // for one specific flag. Material flags print their full material set;
    // boolean flags print every (target, value) rule.
    private static int runListSingleFlag(CommandContext<CommandSourceStack> ctx, ProtectionManager protection) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        Region region = protection.regions().get(name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return 0;
        }
        RegionFlag flag = parseFlagArg(sender, ctx);
        if (flag == null) return 0;

        if (flag.isMaterialFlag()) {
            java.util.Set<Material> materials = region.materialsFor(flag);
            if (materials.isEmpty()) {
                sender.sendMessage(Component.text(
                        "'" + name + "' has no " + flag.name() + " materials.",
                        NamedTextColor.YELLOW));
                return Command.SINGLE_SUCCESS;
            }
            sender.sendMessage(Component.text(
                    "'" + name + "' " + flag.name() + " (" + materials.size() + "):",
                    NamedTextColor.AQUA));
            for (Material m : materials) {
                sender.sendMessage(Component.text("  • " + m.name(), NamedTextColor.GRAY));
            }
            return Command.SINGLE_SUCCESS;
        }

        Map<FlagTarget, Boolean> rules = region.rulesFor(flag);
        if (rules.isEmpty()) {
            sender.sendMessage(Component.text(
                    "'" + name + "' has no rules for " + flag.name() + ".",
                    NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text(
                "'" + name + "' " + flag.name() + ":", NamedTextColor.AQUA));
        for (Map.Entry<FlagTarget, Boolean> rule : rules.entrySet()) {
            sender.sendMessage(Component.text(
                    "  [" + rule.getKey().toKey() + "] = " + rule.getValue(),
                    rule.getValue() ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runListFlags(CommandContext<CommandSourceStack> ctx, ProtectionManager protection) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        Region region = protection.regions().get(name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return 0;
        }
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
            for (Map.Entry<RegionFlag, java.util.Set<Material>> e : region.materialFlags().entrySet()) {
                sender.sendMessage(Component.text(
                        "  " + e.getKey().name() + " (" + e.getValue().size() + " entries)",
                        NamedTextColor.GRAY));
            }
            any = true;
        }
        if (!any) {
            sender.sendMessage(Component.text(
                    "Region '" + name + "' has no flag rules.", NamedTextColor.YELLOW));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static RegionFlag parseFlagArg(CommandSender sender, CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "flag").toUpperCase(Locale.ROOT);
        try {
            return RegionFlag.valueOf(raw);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown flag '" + raw + "'.", NamedTextColor.RED));
            return null;
        }
    }

    // Parse a whitespace- or comma-separated list of material names into an
    // EnumSet. Returns null (with an error message already sent) if any token
    // failed to resolve to a Material.
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

    // /region setparent <child> <parent>  — nest <child> under <parent>.
    private static LiteralArgumentBuilder<CommandSourceStack> setParentNode(ProtectionManager protection) {
        return Commands.literal("setparent")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_CLAIM))
                .executes(usageFor("/region setparent <child> <parent>"))
                .then(Commands.argument("child", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .executes(usageFor("/region setparent <child> <parent>"))
                        .then(Commands.argument("parent", StringArgumentType.word())
                                .suggests(REGION_ID_SUGGESTIONS)
                                .executes(ctx -> runSetParent(ctx, protection))));
    }

    // /region unsetparent <child>  — promote <child> back to top-level.
    private static LiteralArgumentBuilder<CommandSourceStack> unsetParentNode(ProtectionManager protection) {
        return Commands.literal("unsetparent")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_CLAIM))
                .executes(usageFor("/region unsetparent <child>"))
                .then(Commands.argument("child", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .executes(ctx -> runUnsetParent(ctx, protection)));
    }

    private static int runSetParent(CommandContext<CommandSourceStack> ctx, ProtectionManager protection) {
        CommandSender sender = ctx.getSource().getSender();
        String child = StringArgumentType.getString(ctx, "child");
        String parent = StringArgumentType.getString(ctx, "parent");
        return reportParentResult(sender, child, parent, protection.setParent(child, parent));
    }

    private static int runUnsetParent(CommandContext<CommandSourceStack> ctx, ProtectionManager protection) {
        CommandSender sender = ctx.getSource().getSender();
        String child = StringArgumentType.getString(ctx, "child");
        return reportParentResult(sender, child, null, protection.setParent(child, null));
    }

    private static int reportParentResult(CommandSender sender, String child, String parent,
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
                return Command.SINGLE_SUCCESS;
            }
            case NO_CHANGE -> {
                sender.sendMessage(Component.text(
                        "No change — region already has that parent.", NamedTextColor.YELLOW));
                return 0;
            }
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
        return 0;
    }

    private static int runInfoNamed(CommandContext<CommandSourceStack> ctx, ProtectionManager protection) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        Region region = protection.regions().get(name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return 0;
        }
        printRegion(sender, region);
        return Command.SINGLE_SUCCESS;
    }

    private static int runInfoHere(CommandContext<CommandSourceStack> ctx, ProtectionManager protection) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Console must supply a region name: /region info <name>.",
                    NamedTextColor.RED));
            return 0;
        }
        List<Region> here = protection.regionsAt(player.getLocation());
        if (here.isEmpty()) {
            sender.sendMessage(Component.text(
                    "You are standing in wilderness — no region claimed here.",
                    NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text(
                "Region" + (here.size() == 1 ? "" : "s") + " at your location:",
                NamedTextColor.AQUA));
        for (Region r : here) {
            printRegion(sender, r);
        }
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("deprecation") // Bukkit#getOfflinePlayer(UUID) is the supported sync path
    private static String resolveName(java.util.UUID uuid) {
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
        if (region.members().isEmpty()) {
            sender.sendMessage(Component.text("    members: (none)", NamedTextColor.GRAY));
        } else {
            StringBuilder sb = new StringBuilder("    members: ");
            boolean first = true;
            for (java.util.UUID m : region.members()) {
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
            for (Map.Entry<RegionFlag, java.util.Set<Material>> e : region.materialFlags().entrySet()) {
                sender.sendMessage(Component.text(
                        "      " + e.getKey().name() + " (" + e.getValue().size() + " entries)",
                        NamedTextColor.GRAY));
            }
        }
    }

    // Parse the user-supplied target arg and verify any GROUP target maps to a
    // real permission group. Returns null and sends an error message on
    // failure so callers can short-circuit with `return 0`.
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
}
