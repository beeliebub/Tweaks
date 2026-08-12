package me.beeliebub.tweaks.skyblock.command.admin;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.SkyblockDescriptions;
import me.beeliebub.tweaks.skyblock.SkyblockValidation;
import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeAdminService;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeCategory;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRequirement;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeReward;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Challenge and category authoring operations for {@code /isadmin}. */
public final class ChallengeCommands {
    private final AdminCommandContext context;

    public ChallengeCommands(AdminCommandContext context) {
        this.context = context;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) return list(sender, "");
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("inspect") && args.length == 3) return inspect(sender, args[2]);
        if (action.equals("validate")) {
            if (args.length == 3) return inspect(sender, args[2]);
            context.runtime.challengeRegistry().challenges().forEach(challenge -> inspect(sender, challenge.id()));
            return true;
        }
        if (action.equals("category-create") && args.length >= 4) {
            return createCategory(sender, args[2], AdminCommandContext.join(args, 3));
        }
        if (action.equals("category-delete") && args.length >= 3 && args.length <= 4) {
            return deleteCategory(sender, args[2], AdminArgumentParser.hasTrailingConfirm(args));
        }
        if (action.equals("category") && args.length >= 3 && args[2].equalsIgnoreCase("create")) {
            if (args.length < 4) return context.invalid(sender, "challenges category create <id> <display...>");
            return createCategory(sender, args[3], AdminCommandContext.join(args, 4));
        }
        if (action.equals("category") && args.length >= 3 && args[2].equalsIgnoreCase("set")) {
            if (args.length < 6) return context.invalid(sender,
                    "challenges category set <id> <name|order> <value...>");
            String id = args[3];
            String field = args[4].toLowerCase(Locale.ROOT);
            if (field.equals("name")) {
                int order = context.runtime.challengeRegistry().category(id).map(ChallengeCategory::order).orElse(0);
                return report(sender, context.challengeAdmin.editCategory(id, AdminCommandContext.join(args, 5), order),
                        "challenge category");
            }
            if (field.equals("order")) {
                return report(sender, context.challengeAdmin.setCategoryOrder(id, Integer.parseInt(args[5])),
                        "challenge category");
            }
            return context.invalid(sender, "category field " + field);
        }
        if (action.equals("category") && args.length >= 4 && args.length <= 5
                && args[2].equalsIgnoreCase("delete")) {
            return deleteCategory(sender, args[3], AdminArgumentParser.hasTrailingConfirm(args));
        }
        if (action.equals("create") && args.length >= 3) return createChallenge(sender, args);
        if (action.equals("set") && args.length >= 5) {
            String field = args[3].toLowerCase(Locale.ROOT);
            ChallengeAdminService.EditResult result = switch (field) {
                case "name" -> context.challengeAdmin.editDisplayName(args[2], AdminCommandContext.join(args, 4));
                case "description" -> context.challengeAdmin.editDescription(args[2], AdminCommandContext.join(args, 4));
                case "category" -> context.challengeAdmin.setCategory(args[2], args[4]);
                default -> null;
            };
            if (result == null) return context.invalid(sender, "challenge field " + field);
            return report(sender, result, "challenge");
        }
        if (action.equals("edit") && args.length >= 4) {
            return report(sender, context.challengeAdmin.editText(args[2], args[3],
                    args.length >= 5 ? AdminCommandContext.join(args, 4) : ""), "challenge");
        }
        if (action.equals("delete") && args.length >= 3 && args.length <= 4) {
            long references = context.runtime.challengeRegistry().challengeReferenceCount(args[2]);
            if (!context.requireConfirmation(sender, "challenge-delete", "challenge " + args[2], references,
                    "Completed island state is retained, but the challenge definition will be removed.",
                    AdminArgumentParser.hasTrailingConfirm(args))) return true;
            return report(sender, context.challengeAdmin.delete(args[2]), "challenge");
        }
        if (action.equals("requirement") && args.length >= 3
                && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("edit"))) {
            boolean edit = args[2].equalsIgnoreCase("edit");
            if (args.length != (edit ? 8 : 7)) return context.invalid(sender,
                    "challenges requirement " + (edit ? "edit <id> <index>" : "add <id>")
                            + " <tracked|possession> <identifier> <amount>");
            int index = edit ? AdminArgumentParser.requireInt(args[4]) : -1;
            int kind = edit ? 5 : 4;
            ChallengeRequirement requirement = parseRequirement(args[kind], args[kind + 1], args[kind + 2]);
            ChallengeAdminService.EditResult result = edit
                    ? context.challengeAdmin.editRequirement(args[3], index, requirement)
                    : context.challengeAdmin.addRequirement(args[3], requirement);
            return report(sender, result, "challenge requirement");
        }
        if (action.equals("requirement") && args.length == 5 && args[2].equalsIgnoreCase("remove")) {
            return report(sender, context.challengeAdmin.removeRequirement(args[3], AdminArgumentParser.requireInt(args[4])),
                    "challenge requirement");
        }
        if (action.equals("requirement") && args.length == 6 && args[2].equalsIgnoreCase("move")) {
            return report(sender, context.challengeAdmin.moveRequirement(args[3], AdminArgumentParser.requireInt(args[4]),
                    AdminArgumentParser.requireInt(args[5])), "challenge requirement");
        }
        if (action.equals("requirement") && args.length == 6) {
            ChallengeRequirement requirement = parseRequirement(args[3], args[4], args[5]);
            return report(sender, context.challengeAdmin.addRequirement(args[2], requirement), "challenge requirement");
        }
        if (action.equals("requirement-remove") && args.length == 4) {
            return report(sender, context.challengeAdmin.removeRequirement(args[2], AdminArgumentParser.requireInt(args[3])),
                    "challenge requirement");
        }
        if (action.equals("requirement-move") && args.length == 5) {
            return report(sender, context.challengeAdmin.moveRequirement(args[2], AdminArgumentParser.requireInt(args[3]),
                    AdminArgumentParser.requireInt(args[4])), "challenge requirement");
        }
        if (action.equals("reward") && args.length >= 3
                && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("edit"))) {
            boolean edit = args[2].equalsIgnoreCase("edit");
            if (args.length < (edit ? 7 : 6)) return context.invalid(sender, "challenges reward add|edit ...");
            int valueStart = edit ? 6 : 5;
            if (args[valueStart - 1].equalsIgnoreCase("item")) {
                return context.guiOnly(sender, "Challenge item rewards", "challenge rewards");
            }
            ChallengeReward reward = parseReward(args[valueStart - 1], args, valueStart);
            ChallengeAdminService.EditResult result = edit
                    ? context.challengeAdmin.editReward(args[3], AdminArgumentParser.requireInt(args[4]), reward)
                    : context.challengeAdmin.addReward(args[3], reward);
            return report(sender, result, "challenge reward");
        }
        if (action.equals("reward") && args.length == 5 && args[2].equalsIgnoreCase("remove")) {
            return report(sender, context.challengeAdmin.removeReward(args[3], AdminArgumentParser.requireInt(args[4])),
                    "challenge reward");
        }
        if (action.equals("reward") && args.length == 6 && args[2].equalsIgnoreCase("move")) {
            return report(sender, context.challengeAdmin.moveReward(args[3], AdminArgumentParser.requireInt(args[4]),
                    AdminArgumentParser.requireInt(args[5])), "challenge reward");
        }
        if (action.equals("reward") && args.length == 5) {
            if (args[3].equalsIgnoreCase("item")) {
                return context.guiOnly(sender, "Challenge item rewards", "challenge rewards");
            }
            ChallengeReward reward = parseReward(args[3], args, 4);
            return report(sender, context.challengeAdmin.addReward(args[2], reward), "challenge reward");
        }
        if (action.equals("reward-remove") && args.length == 4) {
            return report(sender, context.challengeAdmin.removeReward(args[2], AdminArgumentParser.requireInt(args[3])),
                    "challenge reward");
        }
        if (action.equals("reward-move") && args.length == 5) {
            return report(sender, context.challengeAdmin.moveReward(args[2], AdminArgumentParser.requireInt(args[3]),
                    AdminArgumentParser.requireInt(args[4])), "challenge reward");
        }
        if (action.equals("prerequisites") && args.length >= 4) {
            return report(sender, context.challengeAdmin.setPrerequisites(args[2],
                    Arrays.asList(args).subList(3, args.length)), "challenge prerequisites");
        }
        if (action.equals("types") && args.length >= 4) {
            return report(sender, context.challengeAdmin.setTypes(args[2],
                    new LinkedHashSet<>(Arrays.asList(args).subList(3, args.length))), "challenge type gating");
        }
        if (action.equals("any-of") && args.length >= 5) {
            int required = AdminArgumentParser.requireInt(args[3]);
            Challenge.PrerequisiteGroup group = new Challenge.PrerequisiteGroup(required,
                    new LinkedHashSet<>(Arrays.asList(args).subList(4, args.length)));
            return report(sender, context.challengeAdmin.setAnyOfGroups(args[2], List.of(group)),
                    "challenge prerequisites");
        }
        if (action.equals("category") && args.length == 4) {
            return report(sender, context.challengeAdmin.setCategory(args[2], args[3]), "challenge category");
        }
        if (action.equals("category-order") && args.length == 4) {
            return report(sender, context.challengeAdmin.setCategoryOrder(args[2], AdminArgumentParser.requireInt(args[3])),
                    "challenge category");
        }
        return context.invalidUsage(sender, "challenges");
    }

    private boolean list(CommandSender sender, String filter) {
        String normalized = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        List<String> values = context.runtime.challengeRegistry().challenges().stream()
                .filter(challenge -> challenge.id().contains(normalized)
                        || challenge.displayName().toLowerCase(Locale.ROOT).contains(normalized))
                .map(challenge -> challenge.id() + " - " + challenge.displayName())
                .toList();
        sender.sendMessage(Messages.SKYBLOCK.adminList("challenges", values.isEmpty() ? "(none)" : String.join(", ", values)));
        return true;
    }

    private boolean createCategory(CommandSender sender, String id, String displayName) {
        return report(sender, context.challengeAdmin.createCategory(new ChallengeCategory(id, displayName,
                context.runtime.challengeRegistry().categories().size())), "challenge category");
    }

    private boolean deleteCategory(CommandSender sender, String id, boolean confirmed) {
        long references = context.runtime.challengeRegistry().challengesIn(id).size();
        if (!context.requireConfirmation(sender, "challenge-category-delete", "category " + id, references,
                "Challenges in this category must be moved before deletion.", confirmed)) return true;
        return report(sender, context.challengeAdmin.deleteCategory(id), "challenge category");
    }

    private boolean createChallenge(CommandSender sender, String[] args) {
        String category;
        int displayStart;
        if (args.length >= 4 && context.runtime.challengeRegistry().category(args[3]).isPresent()) {
            category = args[3];
            displayStart = 4;
        } else {
            category = context.runtime.challengeRegistry().categories().stream().findFirst()
                    .map(ChallengeCategory::id).orElse(null);
            if (category == null) return context.invalid(sender, "create a challenge category first");
            displayStart = 3;
        }
        String displayName = displayStart < args.length ? AdminCommandContext.join(args, displayStart) : args[2];
        Challenge challenge = new Challenge(args[2], category, displayName, "",
                List.of(), List.of(), List.of(), List.of(), Set.of());
        return report(sender, context.challengeAdmin.create(challenge), "challenge");
    }

    private boolean inspect(CommandSender sender, String id) {
        Challenge challenge = context.runtime.challengeRegistry().challenge(id).orElse(null);
        if (challenge == null) return context.invalid(sender, "unknown challenge " + id);
        sender.sendMessage(Messages.SKYBLOCK.adminDescription("Challenge " + challenge.id() + ": "
                + challenge.displayName() + " [" + challenge.categoryId() + "]"));
        sender.sendMessage(Messages.SKYBLOCK.adminDescription(challenge.description()));
        challenge.requirements().forEach(value -> sender.sendMessage(Messages.SKYBLOCK.adminDescription(
                "Requirement: " + SkyblockDescriptions.requirement(value))));
        challenge.rewards().forEach(value -> sender.sendMessage(Messages.SKYBLOCK.adminDescription(
                "Reward: " + SkyblockDescriptions.reward(value))));
        List<SkyblockValidation.Problem> problems = SkyblockValidation.validateChallenge(challenge,
                context.runtime.challengeRegistry().challenges(), context.runtime.typeRegistry().types(),
                context.runtime.generatorRegistry().tiers());
        if (problems.isEmpty()) sender.sendMessage(Messages.SKYBLOCK.adminDescription("Validation: valid"));
        else problems.forEach(problem -> sender.sendMessage(Messages.SKYBLOCK.validationProblem(
                problem.code() + " - " + problem.message())));
        return true;
    }

    private boolean report(CommandSender sender, ChallengeAdminService.EditResult result, String subject) {
        return context.report(sender, result.success(), subject, result.message(), result.persistence());
    }

    private static ChallengeRequirement parseRequirement(String kind, String identifier, String amount) {
        long count = AdminArgumentParser.requireLong(amount);
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "tracked" -> new ChallengeRequirement.Tracked(
                    AdminArgumentParser.parseTrackKey(identifier).orElseThrow(
                            () -> new IllegalArgumentException("invalid tracked identifier " + identifier)), count);
            case "possession" -> new ChallengeRequirement.Possession(
                    AdminArgumentParser.requireMaterial(identifier), count);
            default -> throw new IllegalArgumentException("requirement type");
        };
    }

    private static ChallengeReward parseReward(String kind, String[] args, int valueStart) {
        String value = valueStart < args.length ? args[valueStart] : "";
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "money" -> new ChallengeReward.Money(AdminArgumentParser.requireDouble(value));
            case "size" -> new ChallengeReward.SizeUpgrade(IslandSize.valueOf(value.toUpperCase(Locale.ROOT)));
            case "generator" -> new ChallengeReward.GeneratorUnlock(value);
            case "item" -> new ChallengeReward.Items(List.of(new ItemStack(
                    AdminArgumentParser.requireMaterial(value), valueStart + 1 < args.length
                    ? AdminArgumentParser.requireInt(args[valueStart + 1]) : 1)));
            default -> throw new IllegalArgumentException("reward type");
        };
    }
}
