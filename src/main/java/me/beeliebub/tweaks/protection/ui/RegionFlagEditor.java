package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.protection.region.FlagTarget;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The flag mutation engine backing {@code /region flag}/{@code unflag} — extracted verbatim
 * (formerly {@code ProtectionCommand.runSetFlag}/{@code runRemoveFlag}) so
 * {@code RegionFlagEditorTest} (production package, formerly {@code ProtectionCommandTest})
 * can call it directly without reflection.
 */
final class RegionFlagEditor {

    private RegionFlagEditor() {}

    static int setFlag(CommandSender sender, ProtectionManager protection,
                       PermissionManager permissions,
                       String name, String flagToken, String rawValue) {
        RegionCommandContext context = new RegionCommandContext(protection.plugin(), protection, null)
                .legacyBareLookup();
        return setFlag(context, sender, protection, permissions, name, flagToken, rawValue);
    }

    static int setFlag(RegionCommandContext context, CommandSender sender, ProtectionManager protection,
                       PermissionManager permissions,
                       String name, String flagToken, String rawValue) {
        Region region = context.resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
            return 0;
        }
        if (!RegionAuth.isOwnerManagerOrAdmin(context, sender, region)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_EDIT_AUTH));
            return 0;
        }
        RegionFlag flag = parseFlagToken(sender, flagToken);
        if (flag == null) return 0;

        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_MISSING_VALUE));
            return 0;
        }

        if (flag.isMaterialFlag()) {
            return applyMaterialFlag(sender, protection, context.scopeWorld(sender), name, flag, trimmed);
        }
        if (flag.isEntityFlag()) {
            return applyEntityFlag(sender, protection, context.scopeWorld(sender), name, flag, trimmed);
        }
        return applyBooleanFlag(sender, protection, permissions, context.scopeWorld(sender), name, flag, trimmed);
    }

    static int removeFlag(CommandSender sender, ProtectionManager protection,
                          PermissionManager permissions,
                          String name, String flagToken, String rawTarget) {
        RegionCommandContext context = new RegionCommandContext(protection.plugin(), protection, null)
                .legacyBareLookup();
        return removeFlag(context, sender, protection, permissions, name, flagToken, rawTarget);
    }

    static int removeFlag(RegionCommandContext context, CommandSender sender, ProtectionManager protection,
                          PermissionManager permissions,
                          String name, String flagToken, String rawTarget) {
        Region region = context.resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
            return 0;
        }
        if (!RegionAuth.isOwnerManagerOrAdmin(context, sender, region)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_EDIT_AUTH));
            return 0;
        }
        RegionFlag flag = parseFlagToken(sender, flagToken);
        if (flag == null) return 0;

        World world = context.scopeWorld(sender);
        if (flag.isMaterialFlag()) {
            if (rawTarget != null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.MATERIAL_TARGET_WARNING,
                        flag.name(), name, flag.name()));
            }
            if (!protection.clearMaterials(world, name, flag)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_LIST_EMPTY, flag.name()));
                return 0;
            }
            sender.sendMessage(Messages.PROTECTION.text(Text.MATERIAL_LIST_CLEARED, name, flag.name()));
            logFlagMutation(protection, sender, name, flag, "cleared material list");
            return 1;
        }

        if (flag.isEntityFlag()) {
            if (rawTarget != null) {
                sender.sendMessage(Messages.PROTECTION.text(Text.ENTITY_TARGET_WARNING,
                        flag.name(), name, flag.name()));
            }
            if (!protection.clearEntities(world, name, flag)) {
                sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_LIST_EMPTY, flag.name()));
                return 0;
            }
            sender.sendMessage(Messages.PROTECTION.text(Text.ENTITY_LIST_CLEARED, name, flag.name()));
            logFlagMutation(protection, sender, name, flag, "cleared entity list");
            return 1;
        }

        FlagTarget target = resolveTarget(sender, permissions, rawTarget);
        if (target == null) return 0;

        if (!protection.removeFlag(world, name, flag, target)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_REMOVE_MISSING,
                    flag.name(), target.toKey()));
            return 0;
        }
        sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_REMOVED, name,
                flag.name(), target.toKey()));
        logFlagMutation(protection, sender, name, flag, "removed rule for " + target.toKey());
        return 1;
    }

    /** /region flag <name> <flag> — list the current rules / material entries for one flag. */
    static void listSingleFlag(RegionCommandContext ctx, CommandSender sender, String name, String flagToken) {
        Region region = ctx.resolveRegion(sender, name);
        if (region == null) {
            sender.sendMessage(Messages.PROTECTION.text(Text.REGION_NOT_FOUND, name));
            return;
        }
        RegionFlag flag = parseFlagToken(sender, flagToken);
        if (flag == null) return;

        if (flag.isMaterialFlag()) {
            Set<Material> materials = region.materialsFor(flag);
            if (materials.isEmpty()) {
                sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_NO_MATERIALS, name, flag.name()));
                return;
            }
            sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_LIST_HEADER,
                    name, flag.name(), materials.size()));
            for (Material m : materials) {
                sender.sendMessage(Messages.PROTECTION.text(Text.BULLET, m.name()));
            }
            return;
        }

        if (flag.isEntityFlag()) {
            Set<EntityType> entities = region.entitiesFor(flag);
            if (entities.isEmpty()) {
                sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_NO_ENTITIES, name, flag.name()));
                return;
            }
            sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_LIST_HEADER,
                    name, flag.name(), entities.size()));
            for (EntityType t : entities) {
                sender.sendMessage(Messages.PROTECTION.text(Text.BULLET, t.name()));
            }
            return;
        }

        Map<FlagTarget, Boolean> rules = region.rulesFor(flag);
        if (rules.isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_NO_RULES, name, flag.name()));
            return;
        }
        sender.sendMessage(Messages.PROTECTION.text(Text.FLAG_HEADER, name, flag.name()));
        for (Map.Entry<FlagTarget, Boolean> rule : rules.entrySet()) {
            sender.sendMessage(Messages.PROTECTION.targetBooleanRule(
                    rule.getKey().toKey(), rule.getValue()));
        }
    }

    private static int applyMaterialFlag(
            CommandSender sender, ProtectionManager protection,
            World world, String name, RegionFlag flag, String rawValue) {
        EnumSet<Material> materials = parseMaterials(sender, rawValue);
        if (materials == null) return 0;
        if (materials.isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.MATERIAL_REQUIRED));
            return 0;
        }
        if (!protection.setMaterials(world, name, flag, materials)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.MATERIAL_NO_CHANGE));
            return 0;
        }
        sender.sendMessage(Messages.PROTECTION.text(Text.MATERIAL_SET,
                name, flag.name(), materials.size(), materials.size() == 1 ? "" : "s"));
        logFlagMutation(protection, sender, name, flag, "set " + materials.size() + " material(s)");
        return 1;
    }

    private static int applyEntityFlag(
            CommandSender sender, ProtectionManager protection,
            World world, String name, RegionFlag flag, String rawValue) {
        EnumSet<EntityType> entities = parseEntities(sender, rawValue);
        if (entities == null) return 0;
        if (entities.isEmpty()) {
            sender.sendMessage(Messages.PROTECTION.text(Text.ENTITY_REQUIRED));
            return 0;
        }
        if (!protection.setEntities(world, name, flag, entities)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.ENTITY_NO_CHANGE));
            return 0;
        }
        sender.sendMessage(Messages.PROTECTION.text(Text.ENTITY_SET,
                name, flag.name(), entities.size(), entities.size() == 1 ? "" : "s"));
        logFlagMutation(protection, sender, name, flag, "set " + entities.size() + " entit(ies)");
        return 1;
    }

    private static int applyBooleanFlag(
            CommandSender sender, ProtectionManager protection, PermissionManager permissions,
            World world, String name, RegionFlag flag, String rawValue) {
        String[] tokens = rawValue.split("\\s+");
        String boolToken = tokens[0].toLowerCase(Locale.ROOT);
        boolean value;
        if ("true".equals(boolToken)) {
            value = true;
        } else if ("false".equals(boolToken)) {
            value = false;
        } else {
            sender.sendMessage(Messages.PROTECTION.text(Text.BOOLEAN_EXPECTED,
                    flag.name(), tokens[0]));
            return 0;
        }
        if (tokens.length > 2) {
            sender.sendMessage(Messages.PROTECTION.text(Text.BOOLEAN_TOO_MANY, flag.name()));
            return 0;
        }
        String rawTarget = (tokens.length == 2) ? tokens[1] : null;
        FlagTarget target = resolveTarget(sender, permissions, rawTarget);
        if (target == null) return 0;
        warnDefaultGroupTarget(sender, target, name);

        if (!protection.setFlag(world, name, flag, target, value)) {
            sender.sendMessage(Messages.PROTECTION.text(Text.BOOLEAN_NO_CHANGE,
                    name, flag.name(), target.toKey(), value, name, flag.name()));
            return 0;
        }
        sender.sendMessage(Messages.PROTECTION.text(Text.BOOLEAN_SET,
                name, flag.name(), target.toKey(), value));
        logFlagMutation(protection, sender, name, flag,
                "set " + target.toKey() + " to " + value);
        return 1;
    }

    private static void logFlagMutation(ProtectionManager protection, CommandSender sender,
                                         String region, RegionFlag flag, String detail) {
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(protection.plugin());
        if (eventLog == null) return;
        String actorName = sender instanceof org.bukkit.entity.Player player ? player.getName() : null;
        java.util.UUID actorId = sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null;
        eventLog.log(LoggingPaths.PROTECTION_FLAG, () ->
                "[Protection] " + ConsoleEventLog.actorLabel(actorName, actorId)
                        + " changed " + flag.name() + " on region " + region + ": " + detail);
    }

    static RegionFlag parseFlagToken(CommandSender sender, String token) {
        if (token == null) return null;
        String raw = token.toUpperCase(Locale.ROOT);
        try {
            return RegionFlag.valueOf(raw);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Messages.PROTECTION.text(Text.UNKNOWN_FLAG, raw));
            return null;
        }
    }

    static RegionFlag tryParseFlag(String token) {
        if (token == null) return null;
        try {
            return RegionFlag.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
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
                sender.sendMessage(Messages.PROTECTION.text(Text.UNKNOWN_MATERIAL, token));
                return null;
            }
            if (!m.isBlock()) {
                sender.sendMessage(Messages.PROTECTION.text(Text.NOT_BLOCK_MATERIAL, token));
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
                sender.sendMessage(Messages.PROTECTION.text(Text.UNKNOWN_ENTITY, token));
                return null;
            }
        }
        return out;
    }

    static FlagTarget resolveTarget(CommandSender sender, PermissionManager permissions, String rawTarget) {
        FlagTarget target = FlagTarget.parseCommandArg(rawTarget);
        if (target.type() == FlagTarget.Type.GROUP) {
            if (permissions == null || !permissions.getGroups().containsKey(target.groupName())) {
                sender.sendMessage(Messages.PROTECTION.text(Text.UNKNOWN_GROUP, target.groupName()));
                return null;
            }
        }
        return target;
    }

    static void warnDefaultGroupTarget(CommandSender sender, FlagTarget target, String regionName) {
        if (target.type() == FlagTarget.Type.GROUP && "default".equals(target.groupName())) {
            sender.sendMessage(Messages.PROTECTION.text(Text.MEMBER_GROUP_DEFAULT_WARNING, regionName));
        }
    }
}
