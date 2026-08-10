package me.beeliebub.tweaks.skyblock.command.admin;

import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeCategory;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Prefix-filtered, registry-aware completion for every declared administrator argument depth. */
public final class AdminTabCompletion {
    private AdminTabCompletion() {
    }

    public static List<String> complete(AdminCommandContext context, CommandSender sender, String[] args) {
        if (sender instanceof Player player && (!player.hasPermission(me.beeliebub.tweaks.permissions.Permissions.ADMIN_SKYBLOCK)
                || player.getWorld() != context.runtime.world())) return List.of();
        if (args == null || args.length == 0) return literal(topLevel(), "");
        String root = lower(args[0]);
        String partial = args[args.length - 1] == null ? "" : args[args.length - 1];
        if (args.length == 1) return literal(topLevel(), partial);
        if (root.equals("help")) return helpCompletion(partial);
        if (root.equals("clearspawn") && args.length == 2) {
            return literal(List.of(AdminArgumentParser.CONFIRM), partial);
        }
        if (root.equals("island")) return island(context, args, partial);
        if (root.equals("templates")) return templates(context, args, partial);
        return switch (root) {
            case "types" -> types(context, args, partial);
            case "difficulties" -> difficulties(context, args, partial);
            case "generators" -> generators(context, args, partial);
            case "shop" -> shop(context, args, partial);
            case "challenges" -> challenges(context, args, partial);
            default -> List.of();
        };
    }

    private static List<String> island(AdminCommandContext context, String[] args, String partial) {
        if (args.length == 2) return AdminUsage.childSuggestions("island", partial);
        String action = lower(args[1]);
        if (args.length == 3) {
            if (action.equals("size") || action.equals("force-complete") || action.equals("force-delete")
                    || action.equals("inspect") || action.equals("teleport")) {
                return literal(islandReferences(context), partial);
            }
        }
        if (args.length == 4) {
            if (action.equals("size")) return literal(enumNames(IslandSize.values()), partial);
            if (action.equals("force-complete")) return literal(challengeIds(context), partial);
            if (action.equals("force-delete")) return literal(List.of(AdminArgumentParser.CONFIRM), partial);
        }
        return List.of();
    }

    private static List<String> templates(AdminCommandContext context, String[] args, String partial) {
        if (args.length == 2) return AdminUsage.childSuggestions("templates", partial);
        if (args.length == 3 && (lower(args[1]).equals("delete") || lower(args[1]).equals("preview"))) {
            return literal(context.runtime.templateStore().ids(), partial);
        }
        if (args.length == 4 && lower(args[1]).equals("delete")) {
            return literal(List.of(AdminArgumentParser.CONFIRM), partial);
        }
        return List.of();
    }

    private static List<String> types(AdminCommandContext context, String[] args, String partial) {
        if (args.length == 2) return AdminUsage.childSuggestions("types", partial);
        String action = lower(args[1]);
        if (args.length == 3) {
            if (action.equals("set") || action.equals("inspect") || action.equals("delete")
                    || action.equals("edit") || action.equals("kit") || action.equals("allowed")) {
                return literal(typeIds(context), partial);
            }
            if (action.equals("list")) return literal(typeIds(context), partial);
        }
        if (args.length == 4 && action.equals("set")) {
            return AdminUsage.childSuggestions("types set", partial);
        }
        if (args.length == 5 && action.equals("set")) return typeFieldValues(context, args[3], partial);
        if (args.length >= 4 && action.equals("allowed")) return literal(challengeIds(context), partial);
        if (args.length == 4 && action.equals("delete")) return literal(List.of(AdminArgumentParser.CONFIRM), partial);
        return List.of();
    }

    private static List<String> difficulties(AdminCommandContext context, String[] args, String partial) {
        if (args.length == 2) return AdminUsage.childSuggestions("difficulties", partial);
        String action = lower(args[1]);
        if (args.length == 3) {
            if (action.equals("set") || action.equals("inspect") || action.equals("delete") || action.equals("edit")) {
                return literal(difficultyIds(context), partial);
            }
            if (action.equals("list")) return literal(difficultyIds(context), partial);
        }
        if (args.length == 4 && action.equals("set")) return AdminUsage.childSuggestions("difficulties set", partial);
        if (args.length == 4 && action.equals("delete")) return literal(List.of(AdminArgumentParser.CONFIRM), partial);
        return List.of();
    }

    private static List<String> generators(AdminCommandContext context, String[] args, String partial) {
        if (args.length == 2) return AdminUsage.childSuggestions("generators", partial);
        String action = lower(args[1]);
        if (args.length == 3) {
            if (action.equals("set") || action.equals("inspect") || action.equals("delete") || action.equals("edit")) {
                return literal(generatorIds(context), partial);
            }
            if (action.equals("output")) return AdminUsage.childSuggestions("generators output", partial);
            if (action.equals("list")) return literal(generatorIds(context), partial);
        }
        if (args.length == 4 && action.equals("set")) {
            return AdminUsage.childSuggestions("generators set", partial);
        }
        if (args.length == 4 && action.equals("output")) return literal(generatorIds(context), partial);
        if (args.length == 5 && action.equals("output")) return literal(materialNames(), partial);
        if (args.length == 4 && action.equals("delete")) return literal(List.of(AdminArgumentParser.CONFIRM), partial);
        if (args.length == 6 && action.equals("output") && lower(args[2]).equals("remove")) {
            return literal(List.of(AdminArgumentParser.CONFIRM), partial);
        }
        return List.of();
    }

    private static List<String> shop(AdminCommandContext context, String[] args, String partial) {
        if (args.length == 2) return AdminUsage.childSuggestions("shop", partial);
        String action = lower(args[1]);
        if (args.length == 3) {
            if (action.equals("set") || action.equals("inspect") || action.equals("delete")) {
                return literal(materialNames(), partial);
            }
            if (action.equals("list")) return literal(shopFilters(context), partial);
        }
        if (args.length == 4 && action.equals("set")) return literal(shopCategories(context), partial);
        if (args.length == 4 && action.equals("delete")) return literal(List.of(AdminArgumentParser.CONFIRM), partial);
        return List.of();
    }

    private static List<String> challenges(AdminCommandContext context, String[] args, String partial) {
        if (args.length == 2) return AdminUsage.childSuggestions("challenges", partial);
        String action = lower(args[1]);
        if (args.length == 3) {
            if (action.equals("category")) {
                LinkedHashSet<String> values = new LinkedHashSet<>(AdminUsage.childSuggestions("challenges category", partial));
                values.addAll(categoryIds(context));
                return literal(values, partial);
            }
            if (action.equals("requirement")) return AdminUsage.childSuggestions("challenges requirement", partial);
            if (action.equals("reward")) return AdminUsage.childSuggestions("challenges reward", partial);
            if (action.equals("list")) return literal(challengeFilters(context), partial);
            if (action.equals("validate") || action.equals("inspect") || action.equals("delete")
                    || action.equals("edit") || action.equals("set") || action.equals("prerequisites")
                    || action.equals("types") || action.equals("any-of") || action.equals("requirement-remove")
                    || action.equals("requirement-move") || action.equals("reward-remove") || action.equals("reward-move")) {
                return literal(challengeIds(context), partial);
            }
            if (action.equals("category-create") || action.equals("category-delete") || action.equals("category-order")) {
                return literal(categoryIds(context), partial);
            }
        }
        if (args.length == 4) {
            if (action.equals("category")) {
                String nested = lower(args[2]);
                if (nested.equals("create")) return List.of();
                if (nested.equals("set") || nested.equals("delete")) return literal(categoryIds(context), partial);
                return literal(categoryIds(context), partial);
            }
            if (action.equals("requirement") || action.equals("reward")) {
                return literal(challengeIds(context), partial);
            }
            if (action.equals("category-create")) return List.of();
            if (action.equals("category-delete")) return literal(List.of(AdminArgumentParser.CONFIRM), partial);
            if (action.equals("category-order")) return List.of();
            if (action.equals("delete")) return literal(List.of(AdminArgumentParser.CONFIRM), partial);
        }
        if (args.length == 5) {
            if (action.equals("category")) {
                String nested = lower(args[2]);
                if (nested.equals("set")) return AdminUsage.childSuggestions("challenges category set", partial);
                if (nested.equals("delete")) return literal(List.of(AdminArgumentParser.CONFIRM), partial);
            }
            if (action.equals("requirement")) return requirementNext(context, args, partial);
            if (action.equals("reward")) return rewardNext(context, args, partial);
        }
        if (args.length == 4 && action.equals("set")) {
            return AdminUsage.childSuggestions("challenges set", partial);
        }
        if (args.length == 6 && action.equals("category") && lower(args[2]).equals("set")) {
            return categoryValueSuggestions(context, args, partial);
        }
        if (action.equals("requirement") && args.length >= 6) {
            if (lower(args[2]).equals("edit") && args.length == 6) {
                return literal(List.of("tracked", "possession"), partial);
            }
            return requirementIdentifierSuggestions(context, args, partial);
        }
        if (action.equals("reward") && args.length >= 6) {
            if (lower(args[2]).equals("edit") && args.length == 6) {
                return literal(List.of("money", "size", "generator", "item"), partial);
            }
            return rewardValueSuggestions(context, args, partial);
        }
        if (action.equals("prerequisites") || action.equals("types")) return literal(challengeIds(context), partial);
        if (action.equals("any-of") && args.length >= 5) return literal(challengeIds(context), partial);
        return List.of();
    }

    private static List<String> requirementNext(AdminCommandContext context, String[] args, String partial) {
        String nested = lower(args[2]);
        if (nested.equals("add")) return literal(List.of("tracked", "possession"), partial);
        if (nested.equals("edit")) return List.of();
        if (nested.equals("remove") || nested.equals("move")) return List.of();
        return List.of();
    }

    private static List<String> requirementIdentifierSuggestions(AdminCommandContext context, String[] args,
                                                                   String partial) {
        String nested = lower(args[2]);
        if (!nested.equals("add") && !nested.equals("edit")) return List.of();
        String kind = nested.equals("add") ? lower(args[4]) : args.length > 5 ? lower(args[5]) : "";
        if (kind.equals("possession")) return literal(materialNames(), partial);
        if (kind.equals("tracked")) return literal(trackedKeys(), partial);
        return List.of();
    }

    private static List<String> rewardNext(AdminCommandContext context, String[] args, String partial) {
        String nested = lower(args[2]);
        if (nested.equals("add")) return literal(List.of("money", "size", "generator", "item"), partial);
        if (nested.equals("edit")) return List.of();
        return List.of();
    }

    private static List<String> rewardValueSuggestions(AdminCommandContext context, String[] args, String partial) {
        String nested = lower(args[2]);
        String kind = nested.equals("add") ? lower(args[4]) : args.length > 5 ? lower(args[5]) : "";
        return switch (kind) {
            case "size" -> literal(enumNames(IslandSize.values()), partial);
            case "generator" -> literal(generatorIds(context), partial);
            case "item" -> List.of();
            default -> List.of();
        };
    }

    private static List<String> categoryValueSuggestions(AdminCommandContext context, String[] args, String partial) {
        return lower(args[4]).equals("order") ? List.of() : List.of();
    }

    private static List<String> helpCompletion(String partial) {
        return literal(topLevel().stream().filter(value -> !value.equals("help")).toList(), partial);
    }

    private static List<String> typeFieldValues(AdminCommandContext context, String field, String partial) {
        return switch (lower(field)) {
            case "difficulties" -> literal(difficultyIds(context), partial);
            case "template" -> literal(context.runtime.templateStore().ids(), partial);
            case "biome" -> literal(enumNames(Biome.values()), partial);
            case "allowed" -> literal(challengeIds(context), partial);
            default -> List.of();
        };
    }

    private static List<String> islandReferences(AdminCommandContext context) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        context.runtime.islandManager().all().forEach(island -> {
            values.add(island.id());
            String name = Bukkit.getOfflinePlayer(island.owner()).getName();
            if (name != null) values.add(name);
        });
        return List.copyOf(values);
    }

    private static List<String> typeIds(AdminCommandContext context) {
        return context.runtime.typeRegistry().types().stream().map(value -> value.id()).toList();
    }

    private static List<String> difficultyIds(AdminCommandContext context) {
        return context.runtime.typeRegistry().difficulties().stream().map(value -> value.id()).toList();
    }

    private static List<String> challengeIds(AdminCommandContext context) {
        return context.runtime.challengeRegistry().challenges().stream().map(value -> value.id()).toList();
    }

    private static List<String> categoryIds(AdminCommandContext context) {
        return context.runtime.challengeRegistry().categories().stream().map(ChallengeCategory::id).toList();
    }

    private static List<String> generatorIds(AdminCommandContext context) {
        return context.runtime.generatorRegistry().tiers().stream().map(value -> value.id()).toList();
    }

    private static List<String> shopFilters(AdminCommandContext context) {
        return context.runtime.shopCatalog().entries().stream().map(entry -> entry.material().name()).toList();
    }

    private static List<String> shopCategories(AdminCommandContext context) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add("general");
        context.runtime.shopCatalog().entries().stream().map(entry -> entry.category())
                .filter(value -> value != null && !value.isBlank()).forEach(categories::add);
        return List.copyOf(categories);
    }

    private static List<String> challengeFilters(AdminCommandContext context) {
        return context.runtime.challengeRegistry().challenges().stream().map(value -> value.id()).toList();
    }

    private static List<String> materialNames() {
        return Arrays.stream(Material.values()).filter(value -> !value.isAir()).map(Material::name).toList();
    }

    private static List<String> trackedKeys() {
        List<String> values = new ArrayList<>();
        for (TrackCategory category : TrackCategory.values()) {
            if (category.identifierDomain() == me.beeliebub.tweaks.skyblock.tracking.TrackIdentifierDomain.MATERIAL) {
                for (Material material : Material.values()) if (!material.isAir()) {
                    values.add(category.name().toLowerCase(Locale.ROOT) + ":" + material.name());
                }
            } else {
                for (EntityType entity : EntityType.values()) if (entity.isAlive()) {
                    values.add(category.name().toLowerCase(Locale.ROOT) + ":" + entity.name());
                }
            }
        }
        return values;
    }

    private static List<String> topLevel() {
        return AdminUsage.entries().stream().map(AdminUsage.Entry::token).toList();
    }

    private static List<String> enumNames(Object[] values) {
        return Arrays.stream(values).map(value -> ((Enum<?>) value).name()).toList();
    }

    private static List<String> literal(Collection<String> values, String partial) {
        return AdminArgumentParser.filterPrefix(new LinkedHashSet<>(values == null ? List.of() : values), partial);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
