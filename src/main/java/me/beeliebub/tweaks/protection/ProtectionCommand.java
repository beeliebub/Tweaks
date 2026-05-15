package me.beeliebub.tweaks.protection;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
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
@SuppressWarnings("UnstableApiUsage")
public final class ProtectionCommand {

    // Suggestion providers need access to the manager/permissions, but
    // Brigadier's SuggestionProvider doesn't carry user data. We thread them
    // through ThreadLocals scoped to the lifecycle handler invocation.
    private static final ThreadLocal<ProtectionManager> CURRENT_MANAGER = new ThreadLocal<>();
    private static final ThreadLocal<PermissionManager> CURRENT_PERMISSIONS = new ThreadLocal<>();

    private static final SuggestionProvider<CommandSourceStack> FLAG_SUGGESTIONS = (ctx, builder) -> {
        for (RegionFlag flag : RegionFlag.values()) {
            if (flag.isMaterialFlag()) continue;
            if (flag.name().toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(flag.name());
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> MATERIAL_FLAG_SUGGESTIONS = (ctx, builder) -> {
        for (RegionFlag flag : RegionFlag.values()) {
            if (!flag.isMaterialFlag()) continue;
            if (flag.name().toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(flag.name());
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> MATERIAL_SUGGESTIONS = (ctx, builder) -> {
        // Suggest only the last whitespace-separated token so multi-material
        // entry doesn't keep re-suggesting the same set over and over.
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        String prefix = (lastSpace >= 0 ? remaining.substring(lastSpace + 1) : remaining)
                .toLowerCase(Locale.ROOT);
        com.mojang.brigadier.suggestion.SuggestionsBuilder offset =
                lastSpace >= 0 ? builder.createOffset(builder.getStart() + lastSpace + 1) : builder;
        for (org.bukkit.Material m : org.bukkit.Material.values()) {
            if (!m.isBlock()) continue;
            String name = m.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(prefix)) offset.suggest(name);
        }
        return offset.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> REGION_ID_SUGGESTIONS = (ctx, builder) -> {
        ProtectionManager mgr = CURRENT_MANAGER.get();
        if (mgr != null) {
            for (String id : mgr.regions().keySet()) {
                if (id.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(id);
                }
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
        String prefix = builder.getRemainingLowerCase();
        // Static role keywords always offered.
        if ("owner".startsWith(prefix)) builder.suggest("owner");
        if ("member".startsWith(prefix)) builder.suggest("member");
        if ("default".startsWith(prefix)) builder.suggest("default");
        // Plus every registered permission group, keyed by its lowercase name.
        PermissionManager pm = CURRENT_PERMISSIONS.get();
        if (pm != null) {
            for (String groupName : pm.getGroups().keySet()) {
                String lower = groupName.toLowerCase(Locale.ROOT);
                if (lower.startsWith(prefix)) builder.suggest(lower);
            }
        }
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
                .then(claimNode(protection, selections))
                .then(unclaimNode(protection))
                .then(memberNode(protection, "addmember", Permissions.PROTECTION_MEMBER, true))
                .then(memberNode(protection, "removemember", Permissions.PROTECTION_MEMBER, false))
                .then(flagNode(protection, permissions))
                .then(unflagNode(protection, permissions))
                .then(flagsNode(protection))
                .then(infoNode(protection))
                .then(setParentNode(protection))
                .then(unsetParentNode(protection))
                .then(matFlagNode(protection));
    }

    // /region claim <name>  — reads the wand-driven Pos1/Pos2 selection.
    private static LiteralArgumentBuilder<CommandSourceStack> claimNode(
            ProtectionManager protection, RegionSelectionManager selections) {
        return Commands.literal("claim")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_CLAIM))
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
        protection.claim(region, player.getWorld(), x1, z1, x2, z2);
        selections.clear(player.getUniqueId());

        int chunks = (Math.abs(cx1 - cx2) + 1) * (Math.abs(cz1 - cz2) + 1);
        sender.sendMessage(Component.text(
                "Claimed region '" + name + "' (" + chunks + " chunk" + (chunks == 1 ? "" : "s") + ").",
                NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    // /region unclaim <name>
    private static LiteralArgumentBuilder<CommandSourceStack> unclaimNode(ProtectionManager protection) {
        return Commands.literal("unclaim")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_UNCLAIM))
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
        return Commands.literal(literal)
                .requires(s -> s.getSender().hasPermission(permission))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
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

    // /region flag <name> <flag> <true|false> [target]
    // Target defaults to "default" (catch-all). "owner" / "member" map to the
    // role-based targets; any other token is treated as a permission group
    // name and validated against the live PermissionManager registry.
    private static LiteralArgumentBuilder<CommandSourceStack> flagNode(
            ProtectionManager protection, PermissionManager permissions) {
        return Commands.literal("flag")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_FLAG))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .then(Commands.argument("flag", StringArgumentType.word())
                                .suggests(FLAG_SUGGESTIONS)
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> runSetFlag(ctx, protection, permissions, null))
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .suggests(TARGET_SUGGESTIONS)
                                                .executes(ctx -> runSetFlag(ctx, protection, permissions,
                                                        StringArgumentType.getString(ctx, "target")))))));
    }

    // /region unflag <name> <flag> [target]
    private static LiteralArgumentBuilder<CommandSourceStack> unflagNode(
            ProtectionManager protection, PermissionManager permissions) {
        return Commands.literal("unflag")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_FLAG))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
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
            PermissionManager permissions,
            String rawTarget) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        String flagName;
        boolean value;
        try {
            flagName = StringArgumentType.getString(ctx, "flag").toUpperCase(Locale.ROOT);
            value = BoolArgumentType.getBool(ctx, "value");
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid arguments.", NamedTextColor.RED));
            return 0;
        }

        RegionFlag flag;
        try {
            flag = RegionFlag.valueOf(flagName);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown flag '" + flagName + "'.", NamedTextColor.RED));
            return 0;
        }

        FlagTarget target = resolveTarget(sender, permissions, rawTarget);
        if (target == null) return 0;

        if (!protection.setFlag(name, flag, target, value)) {
            sender.sendMessage(Component.text(
                    "No change — region missing or rule already at requested value.",
                    NamedTextColor.RED));
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
        String flagName = StringArgumentType.getString(ctx, "flag").toUpperCase(Locale.ROOT);

        RegionFlag flag;
        try {
            flag = RegionFlag.valueOf(flagName);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown flag '" + flagName + "'.", NamedTextColor.RED));
            return 0;
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

    private static int runListFlags(CommandContext<CommandSourceStack> ctx, ProtectionManager protection) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        Region region = protection.regions().get(name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return 0;
        }
        if (region.flagRules().isEmpty()) {
            sender.sendMessage(Component.text("Region '" + name + "' has no flag rules.", NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("Flag rules for '" + name + "':", NamedTextColor.AQUA));
        for (Map.Entry<RegionFlag, Map<FlagTarget, Boolean>> e : region.flagRules().entrySet()) {
            for (Map.Entry<FlagTarget, Boolean> rule : e.getValue().entrySet()) {
                sender.sendMessage(Component.text(
                        "  " + e.getKey().name() + " [" + rule.getKey().toKey() + "] = " + rule.getValue(),
                        rule.getValue() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    // /region matflag <name> <flag> <op> [<materials>]
    //   op = set | add | remove   followed by one or more material tokens
    //   op = clear                drops every entry
    //   op = list                 shows the current materials
    // Material flags are untargeted; they apply to everyone in the region.
    private static LiteralArgumentBuilder<CommandSourceStack> matFlagNode(ProtectionManager protection) {
        return Commands.literal("matflag")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_FLAG))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .then(Commands.argument("flag", StringArgumentType.word())
                                .suggests(MATERIAL_FLAG_SUGGESTIONS)
                                .then(Commands.literal("set")
                                        .then(Commands.argument("materials", StringArgumentType.greedyString())
                                                .suggests(MATERIAL_SUGGESTIONS)
                                                .executes(ctx -> runMatFlagWrite(ctx, protection, MatOp.SET))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("materials", StringArgumentType.greedyString())
                                                .suggests(MATERIAL_SUGGESTIONS)
                                                .executes(ctx -> runMatFlagWrite(ctx, protection, MatOp.ADD))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("materials", StringArgumentType.greedyString())
                                                .suggests(MATERIAL_SUGGESTIONS)
                                                .executes(ctx -> runMatFlagWrite(ctx, protection, MatOp.REMOVE))))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> runMatFlagClear(ctx, protection)))
                                .then(Commands.literal("list")
                                        .executes(ctx -> runMatFlagList(ctx, protection)))));
    }

    private enum MatOp { SET, ADD, REMOVE }

    private static int runMatFlagWrite(
            CommandContext<CommandSourceStack> ctx, ProtectionManager protection, MatOp op) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        RegionFlag flag = parseMaterialFlagArg(sender, ctx);
        if (flag == null) return 0;
        EnumSet<org.bukkit.Material> materials = parseMaterials(
                sender, StringArgumentType.getString(ctx, "materials"));
        if (materials == null) return 0;
        if (materials.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Supply at least one material name.", NamedTextColor.RED));
            return 0;
        }

        boolean changed = switch (op) {
            case SET -> protection.setMaterials(name, flag, materials);
            case ADD -> protection.addMaterials(name, flag, materials);
            case REMOVE -> protection.removeMaterials(name, flag, materials);
        };
        if (!changed) {
            sender.sendMessage(Component.text(
                    "No change — region missing or material set already in requested state.",
                    NamedTextColor.RED));
            return 0;
        }
        sender.sendMessage(Component.text(
                "Updated '" + name + "' " + flag.name() + " (" + op.name().toLowerCase(Locale.ROOT)
                        + " " + materials.size() + " material" + (materials.size() == 1 ? "" : "s") + ").",
                NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int runMatFlagClear(CommandContext<CommandSourceStack> ctx, ProtectionManager protection) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        RegionFlag flag = parseMaterialFlagArg(sender, ctx);
        if (flag == null) return 0;
        if (!protection.clearMaterials(name, flag)) {
            sender.sendMessage(Component.text(
                    "No change — region missing or material list already empty.",
                    NamedTextColor.RED));
            return 0;
        }
        sender.sendMessage(Component.text(
                "Cleared '" + name + "' " + flag.name() + " material list.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int runMatFlagList(CommandContext<CommandSourceStack> ctx, ProtectionManager protection) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        Region region = protection.regions().get(name);
        if (region == null) {
            sender.sendMessage(Component.text("No region named '" + name + "'.", NamedTextColor.RED));
            return 0;
        }
        RegionFlag flag = parseMaterialFlagArg(sender, ctx);
        if (flag == null) return 0;
        java.util.Set<org.bukkit.Material> materials = region.materialsFor(flag);
        if (materials.isEmpty()) {
            sender.sendMessage(Component.text(
                    "'" + name + "' has no entries for " + flag.name() + ".",
                    NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text(
                "'" + name + "' " + flag.name() + " (" + materials.size() + "):",
                NamedTextColor.AQUA));
        for (org.bukkit.Material m : materials) {
            sender.sendMessage(Component.text("  • " + m.name(), NamedTextColor.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static RegionFlag parseMaterialFlagArg(CommandSender sender, CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "flag").toUpperCase(Locale.ROOT);
        RegionFlag flag;
        try {
            flag = RegionFlag.valueOf(raw);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown flag '" + raw + "'.", NamedTextColor.RED));
            return null;
        }
        if (!flag.isMaterialFlag()) {
            sender.sendMessage(Component.text(
                    flag.name() + " is a boolean flag; use /region flag instead.",
                    NamedTextColor.RED));
            return null;
        }
        return flag;
    }

    // Parse a whitespace- or comma-separated list of material names into an
    // EnumSet. Returns null (with an error message already sent) if any token
    // failed to resolve to a Material.
    private static EnumSet<org.bukkit.Material> parseMaterials(CommandSender sender, String raw) {
        EnumSet<org.bukkit.Material> out = EnumSet.noneOf(org.bukkit.Material.class);
        if (raw == null || raw.isBlank()) return out;
        for (String token : raw.split("[\\s,]+")) {
            if (token.isEmpty()) continue;
            org.bukkit.Material m = org.bukkit.Material.matchMaterial(token);
            if (m == null) {
                sender.sendMessage(Component.text(
                        "Unknown material '" + token + "'.", NamedTextColor.RED));
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
                .then(Commands.argument("child", StringArgumentType.word())
                        .suggests(REGION_ID_SUGGESTIONS)
                        .then(Commands.argument("parent", StringArgumentType.word())
                                .suggests(REGION_ID_SUGGESTIONS)
                                .executes(ctx -> runSetParent(ctx, protection))));
    }

    // /region unsetparent <child>  — promote <child> back to top-level.
    private static LiteralArgumentBuilder<CommandSourceStack> unsetParentNode(ProtectionManager protection) {
        return Commands.literal("unsetparent")
                .requires(s -> s.getSender().hasPermission(Permissions.PROTECTION_CLAIM))
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
            for (Map.Entry<RegionFlag, java.util.Set<org.bukkit.Material>> e : region.materialFlags().entrySet()) {
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
