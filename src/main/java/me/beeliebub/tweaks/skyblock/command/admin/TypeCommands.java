package me.beeliebub.tweaks.skyblock.command.admin;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.SkyblockDescriptions;
import me.beeliebub.tweaks.skyblock.SkyblockValidation;
import me.beeliebub.tweaks.skyblock.type.IslandDifficulty;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.type.TypeAdminService;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Island-type and difficulty authoring operations for {@code /isadmin}. */
public final class TypeCommands {
    private final AdminCommandContext context;

    public TypeCommands(AdminCommandContext context) {
        this.context = context;
    }

    public boolean handleType(CommandSender sender, String[] args) {
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("inspect") && args.length == 3) return inspectType(sender, args[2]);
        if (action.equals("set") && args.length >= 5) return setTypeField(sender, args);
        if (action.equals("kit")) return context.guiOnly(sender, "Island-type kits", "type kit editor");
        if (action.equals("delete") && args.length >= 3 && args.length <= 4) {
            long references = context.runtime.islandManager().all().stream()
                    .filter(island -> args[2].equalsIgnoreCase(island.typeId())).count();
            if (!context.requireConfirmation(sender, "type-delete", "type " + args[2], references,
                    "Islands using this type retain their state but new islands cannot use it.",
                    AdminArgumentParser.hasTrailingConfirm(args))) return true;
            TypeRegistry.DeleteResult result = context.typeAdmin.deleteType(args[2]);
            return context.report(sender, result.deleted(), "island type", result.reason(), result.persistence());
        }
        if (action.equals("create") && args.length >= 3
                && (args.length < 5 || !looksLikeDifficultyList(args[4]))) {
            String displayName = args.length == 3 ? args[2] : AdminCommandContext.join(args, 3);
            TypeAdminService.EditResult result = context.typeAdmin.createType(new IslandType(args[2], displayName,
                    Set.of(), "", List.of(), "PLAINS", Set.of()));
            return context.report(sender, result.success(), "island type", result.message(), result.persistence());
        }
        if ((action.equals("create") || action.equals("edit")) && args.length >= 5) {
            Set<String> difficulties = new LinkedHashSet<>(Arrays.asList(args[4].toLowerCase(Locale.ROOT).split(",")));
            String template = args.length >= 6 ? args[5] : "";
            String biome = args.length >= 7 ? args[6] : "PLAINS";
            if (action.equals("create")) {
                TypeAdminService.EditResult result = context.typeAdmin.createType(new IslandType(args[2], args[3], difficulties,
                        template, List.of(), biome, Set.of()));
                return context.report(sender, result.success(), "island type", result.message(), result.persistence());
            }
            TypeAdminService.EditResult result = context.typeAdmin.updateType(args[2], current -> new IslandType(current.id(), args[3],
                    difficulties, args.length >= 6 ? template : current.templateId(), current.kit(),
                    args.length >= 7 ? biome : current.biome(), current.allowedChallengeIds()));
            return context.report(sender, result.success(), "island type", result.message(), result.persistence());
        }
        if (action.equals("allowed") && args.length >= 4) {
            TypeAdminService.EditResult result = context.typeAdmin.setAllowedChallenges(args[2],
                    new LinkedHashSet<>(Arrays.asList(args).subList(3, args.length)));
            return context.report(sender, result.success(), "allowed challenges", result.message(), result.persistence());
        }
        return context.invalidUsage(sender, "types");
    }

    public boolean handleDifficulty(CommandSender sender, String[] args) {
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("inspect") && args.length == 3) return inspectDifficulty(sender, args[2]);
        if (action.equals("set") && args.length >= 5) return setDifficultyField(sender, args);
        if (action.equals("delete") && args.length >= 3 && args.length <= 4) {
            long references = context.runtime.typeRegistry().types().stream()
                    .filter(type -> type.difficultyIds().contains(args[2].toLowerCase(Locale.ROOT))).count()
                    + context.runtime.islandManager().all().stream()
                    .filter(island -> args[2].equalsIgnoreCase(island.difficultyId())).count();
            if (!context.requireConfirmation(sender, "difficulty-delete", "difficulty " + args[2], references,
                    "Island types using this difficulty will no longer offer it.",
                    AdminArgumentParser.hasTrailingConfirm(args))) return true;
            TypeRegistry.DeleteResult result = context.typeAdmin.deleteDifficulty(args[2]);
            return context.report(sender, result.deleted(), "difficulty", result.reason(), result.persistence());
        }
        if (action.equals("create") && args.length >= 3 && (args.length < 5 || !isDouble(args[4]))) {
            String displayName = args.length == 3 ? args[2] : AdminCommandContext.join(args, 3);
            TypeAdminService.EditResult result = context.typeAdmin.createDifficulty(new IslandDifficulty(args[2], displayName,
                    context.runtime.typeRegistry().difficulties().size()));
            return context.report(sender, result.success(), "difficulty", result.message(), result.persistence());
        }
        if ((action.equals("create") || action.equals("edit")) && (args.length == 5 || args.length == 6)) {
            double multiplier = AdminArgumentParser.requireDouble(args[4]);
            int order = args.length == 6 ? AdminArgumentParser.requireInt(args[5])
                    : context.runtime.typeRegistry().difficulties().size();
            TypeAdminService.EditResult result = context.typeAdmin.registerDifficulty(
                    new IslandDifficulty(args[2], args[3], order, multiplier));
            return context.report(sender, result.success(), "difficulty", result.message(), result.persistence());
        }
        return context.invalidUsage(sender, "difficulties");
    }

    private boolean inspectType(CommandSender sender, String id) {
        IslandType type = context.runtime.typeRegistry().type(id).orElse(null);
        if (type == null) return context.invalid(sender, "unknown type " + id);
        sender.sendMessage(Messages.SKYBLOCK.adminDescription(SkyblockDescriptions.islandType(type)));
        List<SkyblockValidation.Problem> problems = SkyblockValidation.validateIslandType(type,
                context.runtime.typeRegistry().difficulties(),
                new LinkedHashSet<>(context.runtime.templateStore().ids()));
        if (problems.isEmpty()) sender.sendMessage(Messages.SKYBLOCK.adminDescription("Validation: valid"));
        else problems.forEach(problem -> sender.sendMessage(Messages.SKYBLOCK.validationProblem(
                problem.code() + " - " + problem.message())));
        return true;
    }

    private boolean inspectDifficulty(CommandSender sender, String id) {
        IslandDifficulty difficulty = context.runtime.typeRegistry().difficulty(id).orElse(null);
        if (difficulty == null) return context.invalid(sender, "unknown difficulty " + id);
        sender.sendMessage(Messages.SKYBLOCK.adminDescription(SkyblockDescriptions.difficulty(difficulty)));
        return true;
    }

    private boolean setTypeField(CommandSender sender, String[] args) {
        if (args.length < 5) return context.invalid(sender,
                "types set <id> <name|difficulties|template|biome|allowed> <value...>");
        String id = args[2];
        String field = args[3].toLowerCase(Locale.ROOT);
        TypeAdminService.EditResult result = switch (field) {
            case "name" -> context.typeAdmin.setDisplayName(id, AdminCommandContext.join(args, 4));
            case "difficulties" -> context.typeAdmin.setDifficulties(id, splitIds(args, 4));
            case "template" -> context.typeAdmin.setTemplate(id, AdminCommandContext.join(args, 4));
            case "biome" -> context.typeAdmin.setBiome(id, args[4]);
            case "allowed" -> context.typeAdmin.setAllowedChallenges(id, splitIds(args, 4));
            default -> null;
        };
        if (result == null) return context.invalid(sender, "type field " + field);
        return context.report(sender, result.success(), "island type", result.message(), result.persistence());
    }

    private boolean setDifficultyField(CommandSender sender, String[] args) {
        if (args.length < 5) return context.invalid(sender,
                "difficulties set <id> <name|multiplier|order> <value>");
        IslandDifficulty current = context.runtime.typeRegistry().difficulty(args[2]).orElse(null);
        if (current == null) return context.invalid(sender, "unknown difficulty " + args[2]);
        try {
            IslandDifficulty next = switch (args[3].toLowerCase(Locale.ROOT)) {
                case "name" -> new IslandDifficulty(current.id(), AdminCommandContext.join(args, 4),
                        current.order(), current.multiplier());
                case "multiplier" -> new IslandDifficulty(current.id(), current.displayName(), current.order(),
                        AdminArgumentParser.requireDouble(args[4]));
                case "order" -> new IslandDifficulty(current.id(), current.displayName(),
                        AdminArgumentParser.requireInt(args[4]), current.multiplier());
                default -> throw new IllegalArgumentException("unknown difficulty field " + args[3]);
            };
            TypeAdminService.EditResult result = context.typeAdmin.registerDifficulty(next);
            return context.report(sender, result.success(), "difficulty", result.message(), result.persistence());
        } catch (RuntimeException error) {
            return context.invalid(sender, error.getMessage() == null ? "difficulty" : error.getMessage());
        }
    }

    private Set<String> splitIds(String[] args, int start) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (int index = start; index < args.length; index++) {
            for (String value : args[index].split(",")) if (!value.isBlank()) {
                values.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private boolean looksLikeDifficultyList(String value) {
        if (value == null || value.isBlank()) return false;
        for (String id : value.split(",")) if (context.runtime.typeRegistry().difficulty(id).isEmpty()) return false;
        return true;
    }

    private static boolean isDouble(String value) {
        return AdminArgumentParser.parseDouble(value).isPresent();
    }
}
