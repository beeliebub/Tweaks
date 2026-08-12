package me.beeliebub.tweaks.skyblock.ui.admin;

import static me.beeliebub.tweaks.skyblock.ui.admin.AdminScreenContext.targetValue;

import io.papermc.paper.registry.data.dialog.ActionButton;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.SkyblockDescriptions;
import me.beeliebub.tweaks.skyblock.SkyblockValidation;
import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeAdminService;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeCategory;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRequirement;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeReward;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import me.beeliebub.tweaks.skyblock.tracking.TrackIdentifierDomain;
import me.beeliebub.tweaks.skyblock.tracking.TrackKey;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.ui.admin.AdminPage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public final class ChallengeScreens {
    private final AdminScreenContext context;
    private final SkyblockBootstrap.Runtime runtime;
    private final ChallengeAdminService challengeAdmin;
    private final AdminItemEditor itemEditor;
    private final Consumer<Player> hub;

    ChallengeScreens(AdminScreenContext context, Consumer<Player> hub) {
        this.context = context;
        this.runtime = context.runtime;
        this.challengeAdmin = context.challengeAdmin;
        this.itemEditor = context.itemEditor;
        this.hub = hub;
    }

    public void open(Player player) {
        openChallenges(player);
    }

    private void openChallenges(Player player) {
        if (!guard(player)) return;
        List<ActionButton> buttons = new ArrayList<>();
        for (ChallengeCategory category : runtime.challengeRegistry().categories()) {
            buttons.add(button(category.displayName(), "Open category settings", target -> categoryDetail(target, category.id())));
        }
        buttons.add(button("Create category", "Add a challenge category", this::createCategory));
        buttons.add(button("Create challenge", "Add a challenge", this::createChallenge));
        show(player, Messages.SKYBLOCK.text("Challenges", NamedTextColor.AQUA),
                List.of("Select a category or create a new definition."), buttons, hub);
    }

    private void categoryDetail(Player player, String id) {
        if (!guard(player)) return;
        ChallengeCategory category = runtime.challengeRegistry().category(id).orElse(null);
        if (category == null) { openChallenges(player); return; }
        long references = runtime.challengeRegistry().challengesIn(id).size();
        List<ActionButton> buttons = List.of(
                button("Open challenges", references + " challenge(s)", target -> challengesIn(target, id, "", 0)),
                button("Edit category", "Edit display name and order", target -> input(target, "Challenge Category",
                        List.of("name", "order"), List.of(), (actor, values) -> {
                            try {
                                reportAndThen(actor, challengeAdmin.editCategory(id, targetValue(values, "name"),
                                        Integer.parseInt(targetValue(values, "order"))), "challenge category",
                                        this::openChallenges);
                            } catch (RuntimeException error) {
                                actor.sendMessage(Messages.SKYBLOCK.invalidInput("challenge category"));
                            }
                        }, actor -> categoryDetail(actor, id))),
                button("Delete", "Delete this category", target -> AdminConfirm.open(target, "category " + id, references,
                        "Challenges in this category must be moved before deletion.", () -> openChallenges(target), actor -> {
                            reportAndThen(actor, challengeAdmin.deleteCategory(id), "challenge category",
                                    this::openChallenges);
                        }, this::guard, context::reportGuardFailure)));
        show(player, Messages.SKYBLOCK.text("Category: " + id, NamedTextColor.AQUA),
                List.of(category.displayName(), "Order: " + category.order(), "Challenges: " + references), buttons,
                this::openChallenges);
    }

    private void challengesIn(Player player, String categoryId, String filter, int pageNumber) {
        if (!guard(player)) return;
        List<Challenge> source = runtime.challengeRegistry().challengesIn(categoryId);
        AdminPage.Page<Challenge> page = AdminPage.create(source, pageNumber, filter,
                challenge -> challenge.id() + " " + challenge.displayName());
        List<ActionButton> buttons = new ArrayList<>();
        for (Challenge challenge : page.values()) {
            List<SkyblockValidation.Problem> problems = SkyblockValidation.validateChallenge(challenge,
                    runtime.challengeRegistry().challenges(), runtime.typeRegistry().types(),
                    runtime.generatorRegistry().tiers());
            String marker = problems.isEmpty() ? "" : "[!] ";
            String tooltip = problems.isEmpty() ? challenge.displayName() : problems.get(0).message();
            buttons.add(button(marker + challenge.id(), tooltip,
                    target -> challengeDetail(target, challenge.id(), categoryId, filter, pageNumber)));
        }
        if (page.hasPrevious()) buttons.add(button("Previous", "Previous page", target -> challengesIn(target, categoryId, filter, page.page() - 1)));
        if (page.hasNext()) buttons.add(button("Next", "Next page", target -> challengesIn(target, categoryId, filter, page.page() + 1)));
        buttons.add(button("Filter", "Literal text filter", target -> filterDialog(target, categoryId)));
        show(player, Messages.SKYBLOCK.text("Challenges: " + categoryId + " " + page.label(), NamedTextColor.AQUA),
                List.of("Filter: " + (filter.isBlank() ? "none" : filter)), buttons, this::openChallenges);
    }

    private void filterDialog(Player player, String categoryId) {
        input(player, "Challenge Filter", List.of("filter"),
                List.of("Filtering is literal text matching."),
                (actor, values) -> challengesIn(actor, categoryId, targetValue(values, "filter"), 0),
                actor -> challengesIn(actor, categoryId, "", 0));
    }

    private void challengeDetail(Player player, String id, String categoryId, String filter, int page) {
        if (!guard(player)) return;
        Challenge challenge = runtime.challengeRegistry().challenge(id).orElse(null);
        if (challenge == null) { challengesIn(player, categoryId, filter, page); return; }
        List<SkyblockValidation.Problem> validation = SkyblockValidation.validateChallenge(challenge,
                runtime.challengeRegistry().challenges(), runtime.typeRegistry().types(),
                runtime.generatorRegistry().tiers());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("Text", "Edit display name and description", target -> editChallengeText(target, id, categoryId, filter, page)));
        buttons.add(button("Category", challenge.categoryId(), target -> editChallengeCategory(target, id, categoryId, filter, page)));
        buttons.add(button("Type gating", String.valueOf(challenge.typeIds().size()), target -> editTypeGating(target, id, categoryId, filter, page)));
        buttons.add(button("Requirements", String.valueOf(challenge.requirements().size()), target -> requirementsScreen(target, id, categoryId, filter, page)));
        buttons.add(button("Prerequisites", String.valueOf(challenge.prerequisites().size()), target -> editPrerequisites(target, id, categoryId, filter, page)));
        buttons.add(button("Any-of groups", String.valueOf(challenge.anyOfGroups().size()), target -> anyOfScreen(target, id, categoryId, filter, page)));
        buttons.add(button("Rewards", String.valueOf(challenge.rewards().size()), target -> rewardsScreen(target, id, categoryId, filter, page)));
        buttons.add(button(validation.isEmpty() ? "Validation: valid" : "Validation: invalid",
                validation.isEmpty() ? "No validation problems" : validation.get(0).message(),
                target -> showValidation(target, challenge, validation, categoryId, filter, page)));
        buttons.add(button("Delete", "Delete this challenge", target -> AdminConfirm.open(target, "challenge " + id,
                runtime.challengeRegistry().challengeReferenceCount(id), "Completed island state is retained, but future definitions cannot use this challenge.",
                () -> challengesIn(target, categoryId, filter, page), actor -> {
                    ChallengeAdminService.EditResult result = challengeAdmin.delete(id);
                    reportAndThen(actor, result, "challenge",
                            next -> challengesIn(next, categoryId, filter, page));
                }, this::guard, context::reportGuardFailure)));
        show(player, Messages.SKYBLOCK.text("Challenge: " + id, NamedTextColor.AQUA),
                List.of(challenge.displayName(), challenge.description(), "Category: " + challenge.categoryId(),
                        "Requirements: " + challenge.requirements().size(), "Rewards: " + challenge.rewards().size()),
                buttons, target -> challengesIn(target, categoryId, filter, page));
    }

    private void showValidation(Player player, Challenge challenge, List<SkyblockValidation.Problem> problems,
                                String categoryId, String filter, int page) {
        List<String> body = new ArrayList<>();
        body.add(challenge.id() + ": " + (problems.isEmpty() ? "valid" : "invalid"));
        if (problems.isEmpty()) body.add("No validation problems.");
        else body.addAll(problems.stream().map(SkyblockValidation.Problem::message).toList());
        show(player, Messages.SKYBLOCK.text("Validation: " + challenge.id(), NamedTextColor.AQUA), body,
                List.of(), target -> challengeDetail(target, challenge.id(), categoryId, filter, page));
    }

    private void requirementsScreen(Player player, String id, String category, String filter, int page) {
        if (!guard(player)) return;
        Challenge challenge = runtime.challengeRegistry().challenge(id).orElse(null);
        if (challenge == null) { challengesIn(player, category, filter, page); return; }
        List<ActionButton> buttons = new ArrayList<>();
        for (int index = 0; index < challenge.requirements().size(); index++) {
            int current = index;
            ChallengeRequirement requirement = challenge.requirements().get(index);
            buttons.add(mutationButton("Remove " + (index + 1), SkyblockDescriptions.requirement(requirement), target -> {
                reportAndThen(target, challengeAdmin.removeRequirement(id, current), "challenge requirement",
                        next -> requirementsScreen(next, id, category, filter, page));
            }));
            if (index > 0) buttons.add(mutationButton("Move " + (index + 1) + " up", "Reorder this requirement",
                    target -> {
                        reportAndThen(target, challengeAdmin.moveRequirement(id, current, current - 1), "challenge requirement",
                                next -> requirementsScreen(next, id, category, filter, page));
                    }));
            buttons.add(button("Edit " + (index + 1), "Replace this requirement in place",
                    target -> editRequirementChoice(target, id, category, filter, page, current, requirement)));
            if (index + 1 < challenge.requirements().size()) buttons.add(mutationButton("Move " + (index + 1) + " down",
                    "Reorder this requirement", target -> {
                        reportAndThen(target, challengeAdmin.moveRequirement(id, current, current + 1), "challenge requirement",
                                next -> requirementsScreen(next, id, category, filter, page));
                    }));
        }
        buttons.add(button("Add", "Add a tracked or possession requirement", target -> addRequirementChoice(target, id, category, filter, page)));
        show(player, Messages.SKYBLOCK.text("Requirements: " + id, NamedTextColor.AQUA),
                List.of("Remove or reorder entries, or add another requirement."), buttons,
                target -> challengeDetail(target, id, category, filter, page));
    }

    private void addRequirementChoice(Player player, String id, String category, String filter, int page) {
        List<ActionButton> buttons = List.of(
                button("Tracked", "Choose one of the tracking categories", target -> trackedCategoryChoice(target, id, category, filter, page)),
                button("Possession", "Choose a material and amount", target -> materialPicker(target, "Possession Material",
                        material -> possessionAmount(material, target, id, category, filter, page),
                        back -> requirementsScreen(back, id, category, filter, page), 0, "")));
        show(player, Messages.SKYBLOCK.text("Add Requirement", NamedTextColor.AQUA),
                List.of("Choose the requirement kind."), buttons,
                target -> requirementsScreen(target, id, category, filter, page));
    }

    private void trackedCategoryChoice(Player player, String id, String category, String filter, int page) {
        List<ActionButton> buttons = Arrays.stream(me.beeliebub.tweaks.skyblock.tracking.TrackCategory.values())
                .map(trackCategory -> button(trackCategory.name(), "Choose this tracking category",
                        target -> trackedIdentifierPicker(target, id, category, filter, page, trackCategory,
                                identifier -> trackedAmountInput(target, id, category, filter, page, trackCategory,
                                        identifier, false, -1)))).toList();
        show(player, Messages.SKYBLOCK.text("Tracked Category", NamedTextColor.AQUA),
                List.of("Choose one of the stable tracking categories."), buttons,
                target -> addRequirementChoice(target, id, category, filter, page));
    }

    private void trackedIdentifierPicker(Player player, String id, String category, String filter, int page,
                                         TrackCategory trackCategory, Consumer<String> selected) {
        if (!guard(player)) return;
        if (trackCategory.identifierDomain() == TrackIdentifierDomain.MATERIAL) {
            materialPicker(player, "Tracked Material", material -> selected.accept(material.name()),
                    target -> trackedCategoryChoice(target, id, category, filter, page), 0, "");
            return;
        }
        entityPicker(player, "Tracked Entity", entity -> selected.accept(entity.name()),
                target -> trackedCategoryChoice(target, id, category, filter, page), 0, "");
    }

    private void entityPicker(Player player, String title, Consumer<EntityType> selected, Consumer<Player> back,
                              int pageNumber, String filter) {
        if (!guard(player)) return;
        List<EntityType> entities = Arrays.stream(EntityType.values()).filter(EntityType::isAlive).toList();
        AdminPage.Page<EntityType> page = AdminPage.create(entities, pageNumber, filter, entity -> entity.name());
        List<ActionButton> buttons = new ArrayList<>();
        for (EntityType entity : page.values()) buttons.add(button(entity.name(), "Choose this entity type",
                target -> selected.accept(entity)));
        if (page.hasPrevious()) buttons.add(button("Previous", "Previous entity page",
                target -> entityPicker(target, title, selected, back, page.page() - 1, filter)));
        if (page.hasNext()) buttons.add(button("Next", "Next entity page",
                target -> entityPicker(target, title, selected, back, page.page() + 1, filter)));
        buttons.add(button("Filter", "Use a literal entity-name filter", target -> input(target, "Entity Filter",
                List.of("filter"), List.of("Filtering is literal text matching."), (actor, values) ->
                        entityPicker(actor, title, selected, back, 0, targetValue(values, "filter")),
                actor -> entityPicker(actor, title, selected, back, page.page(), filter))));
        show(player, Messages.SKYBLOCK.text(title + " " + page.label(), NamedTextColor.AQUA),
                List.of("Identifier domain: entity type", "Filter: " + (filter.isBlank() ? "none" : filter)),
                buttons, back);
    }

    private void trackedAmountInput(Player player, String id, String category, String filter, int page,
                                    TrackCategory trackCategory, String identifier, boolean edit, int index) {
        input(player, edit ? "Edit Tracked Requirement" : "Tracked Requirement", List.of("amount"),
                List.of("Category: " + trackCategory.name(), "Identifier: " + identifier), (target, values) -> {
                    try {
                        ChallengeRequirement requirement = new ChallengeRequirement.Tracked(
                                new TrackKey(trackCategory, identifier),
                                Long.parseLong(targetValue(values, "amount")));
                        reportAndThen(target, edit ? challengeAdmin.editRequirement(id, index, requirement)
                                : challengeAdmin.addRequirement(id, requirement), "challenge requirement",
                                next -> requirementsScreen(next, id, category, filter, page));
                    } catch (RuntimeException error) {
                        target.sendMessage(Messages.SKYBLOCK.invalidInput("tracked requirement"));
                    }
                }, target -> requirementsScreen(target, id, category, filter, page));
    }

    private void editRequirementChoice(Player player, String id, String category, String filter, int page,
                                       int index, ChallengeRequirement current) {
        if (current instanceof ChallengeRequirement.Possession possession) {
            materialPicker(player, "Possession Material", material -> possessionAmountEdit(material, player, id,
                            category, filter, page, index), target -> requirementsScreen(target, id, category, filter, page),
                    0, possession.material().name());
            return;
        }
        ChallengeRequirement.Tracked tracked = (ChallengeRequirement.Tracked) current;
        trackedIdentifierPicker(player, id, category, filter, page, tracked.key().category(),
                identifier -> trackedAmountInput(player, id, category, filter, page,
                        tracked.key().category(), identifier, true, index));
    }

    private void possessionAmountEdit(Material material, Player player, String id, String category,
                                      String filter, int page, int index) {
        input(player, "Edit Possession Requirement", List.of("amount"),
                List.of("Material: " + material.name()), (target, values) -> {
                    try {
                        reportAndThen(target, challengeAdmin.editRequirement(id, index,
                                new ChallengeRequirement.Possession(material,
                                        Long.parseLong(targetValue(values, "amount")))), "challenge requirement",
                                next -> requirementsScreen(next, id, category, filter, page));
                    } catch (RuntimeException error) {
                        target.sendMessage(Messages.SKYBLOCK.invalidInput("possession requirement"));
                    }
                }, target -> requirementsScreen(target, id, category, filter, page));
    }

    private void possessionAmount(Material material, Player player, String id, String category, String filter, int page) {
        input(player, "Possession Amount", List.of("amount"), List.of("Material: " + material.name()), (target, values) -> {
            try {
                reportAndThen(target, challengeAdmin.addRequirement(id, new ChallengeRequirement.Possession(material,
                        Long.parseLong(targetValue(values, "amount")))), "challenge requirement",
                        next -> requirementsScreen(next, id, category, filter, page));
            } catch (RuntimeException error) {
                target.sendMessage(Messages.SKYBLOCK.invalidInput("possession requirement"));
            }
        }, target -> materialPicker(target, "Possession Material", picked -> possessionAmount(picked, target, id, category, filter, page),
                back -> requirementsScreen(back, id, category, filter, page), 0, ""));
    }

    private void materialPicker(Player player, String title, Consumer<Material> selected, Consumer<Player> back,
                                int pageNumber, String filter) {
        if (!guard(player)) return;
        List<Material> materials = Arrays.stream(Material.values())
                .filter(material -> !material.name().endsWith("_AIR") && material != Material.AIR).toList();
        AdminPage.Page<Material> page = AdminPage.create(materials, pageNumber, filter, material -> material.name());
        List<ActionButton> buttons = new ArrayList<>();
        for (Material material : page.values()) buttons.add(button(material.name(), "Choose this material", target -> selected.accept(material)));
        if (page.hasPrevious()) buttons.add(button("Previous", "Previous materials", target -> materialPicker(target, title, selected, back,
                page.page() - 1, filter)));
        if (page.hasNext()) buttons.add(button("Next", "Next materials", target -> materialPicker(target, title, selected, back,
                page.page() + 1, filter)));
        buttons.add(button("Filter", "Use a literal material-name filter", target -> input(target, "Material Filter", List.of("filter"),
                List.of("Filtering is literal text matching."), (actor, values) -> materialPicker(actor, title, selected, back, 0,
                        AdminPage.literalFilter(targetValue(values, "filter"))),
                actor -> materialPicker(actor, title, selected, back, page.page(), filter))));
        show(player, Messages.SKYBLOCK.text(title + " " + page.label(), NamedTextColor.AQUA),
                List.of("Filter: " + (filter.isBlank() ? "none" : filter)), buttons, back);
    }

    private void createCategory(Player player) {
        input(player, "Create Category", List.of("identifier", "name", "order"), List.of("Category ids are lowercase."), (target, values) -> {
            try {
                ChallengeAdminService.EditResult result = challengeAdmin.createCategory(new ChallengeCategory(
                        targetValue(values, "identifier"), targetValue(values, "name"), Integer.parseInt(targetValue(values, "order"))));
                reportAndThen(target, result, "challenge category", this::openChallenges);
            } catch (RuntimeException error) { target.sendMessage(Messages.SKYBLOCK.invalidInput("challenge category")); }
        }, this::openChallenges);
    }

    private void createChallenge(Player player) {
        if (!guard(player)) return;
        String category = runtime.challengeRegistry().categories().stream().findFirst().map(ChallengeCategory::id).orElse(null);
        if (category == null) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput("create a challenge category first"));
            createCategory(player);
            return;
        }
        input(player, "Create Challenge", List.of("identifier", "name", "description"),
                List.of("Category: " + category + ". Change it from the detail screen if needed."), (target, values) -> {
            try {
                ChallengeAdminService.EditResult result = challengeAdmin.create(new Challenge(targetValue(values, "identifier"),
                        category, targetValue(values, "name"), targetValue(values, "description"),
                        List.of(), List.of(), List.of(), List.of(), Set.of()));
                reportAndThen(target, result, "challenge", this::openChallenges);
            } catch (RuntimeException error) {
                target.sendMessage(Messages.SKYBLOCK.invalidInput(
                        error.getMessage() == null ? "challenge" : error.getMessage()));
            }
        }, this::openChallenges);
    }

    private void editChallengeText(Player player, String id, String category, String filter, int page) {
        input(player, "Challenge Text", List.of("name", "description"), List.of(), (target, values) -> {
            reportAndThen(target, challengeAdmin.editText(id, targetValue(values, "name"), targetValue(values, "description")),
                    "challenge", next -> challengeDetail(next, id, category, filter, page));
        }, target -> challengeDetail(target, id, category, filter, page));
    }

    private void editChallengeCategory(Player player, String id, String category, String filter, int page) {
        List<ActionButton> buttons = runtime.challengeRegistry().categories().stream().map(value -> button(value.id(), value.displayName(), target -> {
            reportAndThen(target, challengeAdmin.setCategory(id, value.id()), "challenge category",
                    next -> challengeDetail(next, id, category, filter, page));
        })).toList();
        show(player, Messages.SKYBLOCK.text("Challenge Category", NamedTextColor.AQUA),
                List.of("Choose an existing category."), buttons,
                target -> challengeDetail(target, id, category, filter, page));
    }

    private void editTypeGating(Player player, String id, String category, String filter, int page) {
        Challenge challenge = runtime.challengeRegistry().challenge(id).orElse(null);
        if (challenge == null) { challengesIn(player, category, filter, page); return; }
        typeGatingPicker(player, id, category, filter, page, new LinkedHashSet<>(challenge.typeIds()));
    }

    private void typeGatingPicker(Player player, String id, String category, String filter, int page,
                                  Set<String> selected) {
        List<ActionButton> buttons = new ArrayList<>();
        for (IslandType type : runtime.typeRegistry().types()) {
            boolean active = selected.contains(type.id());
            buttons.add(button((active ? "[x] " : "[ ] ") + type.id(), type.displayName(), target -> {
                Set<String> next = new LinkedHashSet<>(selected);
                if (!next.add(type.id())) next.remove(type.id());
                typeGatingPicker(target, id, category, filter, page, next);
            }));
        }
        buttons.add(mutationButton("Save", "Save challenge type gating", target -> {
            reportAndThen(target, challengeAdmin.setTypes(id, selected), "challenge type gating",
                    next -> challengeDetail(next, id, category, filter, page));
        }));
        show(player, Messages.SKYBLOCK.text("Challenge Type Gating", NamedTextColor.AQUA),
                List.of("No selected types means the challenge is available to every type."), buttons,
                target -> challengeDetail(target, id, category, filter, page));
    }

    private void editPrerequisites(Player player, String id, String category, String filter, int page) {
        Challenge challenge = runtime.challengeRegistry().challenge(id).orElse(null);
        if (challenge == null) { challengesIn(player, category, filter, page); return; }
        prerequisitePicker(player, id, category, filter, page, new LinkedHashSet<>(challenge.prerequisites()));
    }

        private void prerequisitePicker(Player player, String id, String category, String filter, int page,
                                    Set<String> selected) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Challenge candidate : runtime.challengeRegistry().challenges()) {
            if (candidate.id().equals(id)) continue;
            boolean active = selected.contains(candidate.id());
            buttons.add(button((active ? "[x] " : "[ ] ") + candidate.id(), candidate.displayName(), target -> {
                Set<String> next = new LinkedHashSet<>(selected);
                if (!next.add(candidate.id())) next.remove(candidate.id());
                prerequisitePicker(target, id, category, filter, page, next);
            }));
        }
        buttons.add(mutationButton("Save", "Save prerequisites; cycles are rejected", target -> {
            reportAndThen(target, challengeAdmin.setPrerequisites(id, new ArrayList<>(selected)), "challenge prerequisites",
                    next -> challengeDetail(next, id, category, filter, page));
        }));
        show(player, Messages.SKYBLOCK.text("Prerequisites", NamedTextColor.AQUA),
                List.of("Select the challenges that must be complete first."), buttons,
                target -> challengeDetail(target, id, category, filter, page));
    }

    private void anyOfScreen(Player player, String id, String category, String filter, int page) {
        Challenge challenge = runtime.challengeRegistry().challenge(id).orElse(null);
        if (challenge == null) { challengesIn(player, category, filter, page); return; }
        List<ActionButton> buttons = new ArrayList<>();
        for (int index = 0; index < challenge.anyOfGroups().size(); index++) {
            int current = index;
            Challenge.PrerequisiteGroup group = challenge.anyOfGroups().get(index);
            buttons.add(mutationButton("Remove group " + (index + 1), group.required() + " of " + group.challengeIds(), target -> {
                List<Challenge.PrerequisiteGroup> groups = new ArrayList<>(challenge.anyOfGroups());
                groups.remove(current);
                reportAndThen(target, challengeAdmin.setAnyOfGroups(id, groups), "challenge prerequisites",
                        next -> anyOfScreen(next, id, category, filter, page));
            }));
        }
        buttons.add(button("Add group", "Select challenge ids and the required count", target -> anyOfGroupPicker(target, id, category, filter, page,
                new LinkedHashSet<>())));
        show(player, Messages.SKYBLOCK.text("Any-of Groups", NamedTextColor.AQUA),
                List.of("Each group succeeds when its required number of challenges is complete."), buttons,
                target -> challengeDetail(target, id, category, filter, page));
    }

    private void anyOfGroupPicker(Player player, String id, String category, String filter, int page, Set<String> selected) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Challenge candidate : runtime.challengeRegistry().challenges()) {
            if (candidate.id().equals(id)) continue;
            boolean active = selected.contains(candidate.id());
            buttons.add(button((active ? "[x] " : "[ ] ") + candidate.id(), candidate.displayName(), target -> {
                Set<String> next = new LinkedHashSet<>(selected);
                if (!next.add(candidate.id())) next.remove(candidate.id());
                anyOfGroupPicker(target, id, category, filter, page, next);
            }));
        }
        buttons.add(button("Set required count", "Finish this any-of group", target -> input(target, "Any-of Required Count",
                List.of("required"), List.of("Selected ids: " + String.join(", ", selected)), (actor, values) -> {
                    try {
                        Challenge current = runtime.challengeRegistry().challenge(id).orElseThrow();
                        List<Challenge.PrerequisiteGroup> groups = new ArrayList<>(current.anyOfGroups());
                        groups.add(new Challenge.PrerequisiteGroup(Integer.parseInt(targetValue(values, "required")), selected));
                        reportAndThen(actor, challengeAdmin.setAnyOfGroups(id, groups), "challenge prerequisites",
                                next -> anyOfScreen(next, id, category, filter, page));
                    } catch (RuntimeException error) {
                        actor.sendMessage(Messages.SKYBLOCK.invalidInput("any-of group"));
                    }
                }, actor -> anyOfGroupPicker(actor, id, category, filter, page, selected))));
        show(player, Messages.SKYBLOCK.text("Select Any-of Challenges", NamedTextColor.AQUA),
                List.of("Select at least one challenge."), buttons,
                target -> anyOfScreen(target, id, category, filter, page));
    }

    private void editReward(Player player, String id, String category, String filter, int page) {
        List<ActionButton> buttons = List.of(
                button("Money", "Add a money reward", target -> rewardInput(target, id, category, filter, page, "money")),
                button("Size", "Choose a size upgrade", target -> sizeRewardChoice(target, id, category, filter, page)),
                button("Generator", "Choose a generator tier", target -> generatorRewardChoice(target, id, category, filter, page)),
                button("Items", "Author item rewards in a copy-only editor", target -> editItemReward(target, id, category, filter, page)));
        show(player, Messages.SKYBLOCK.text("Add Reward", NamedTextColor.AQUA), List.of("Choose a reward type."), buttons,
                target -> rewardsScreen(target, id, category, filter, page));
    }

    private void rewardsScreen(Player player, String id, String category, String filter, int page) {
        if (!guard(player)) return;
        Challenge challenge = runtime.challengeRegistry().challenge(id).orElse(null);
        if (challenge == null) { challengesIn(player, category, filter, page); return; }
        List<ActionButton> buttons = new ArrayList<>();
        for (int index = 0; index < challenge.rewards().size(); index++) {
            int current = index;
            ChallengeReward reward = challenge.rewards().get(index);
            buttons.add(mutationButton("Remove " + (index + 1), SkyblockDescriptions.reward(reward), target -> {
                reportAndThen(target, challengeAdmin.removeReward(id, current), "challenge reward",
                        next -> rewardsScreen(next, id, category, filter, page));
            }));
            if (index > 0) buttons.add(mutationButton("Move " + (index + 1) + " up", "Reorder this reward", target -> {
                reportAndThen(target, challengeAdmin.moveReward(id, current, current - 1), "challenge reward",
                        next -> rewardsScreen(next, id, category, filter, page));
            }));
            if (index + 1 < challenge.rewards().size()) buttons.add(mutationButton("Move " + (index + 1) + " down", "Reorder this reward", target -> {
                reportAndThen(target, challengeAdmin.moveReward(id, current, current + 1), "challenge reward",
                        next -> rewardsScreen(next, id, category, filter, page));
            }));
            buttons.add(button("Edit " + (index + 1), "Replace this reward in place",
                    target -> editRewardChoice(target, id, category, filter, page, current, reward)));
        }
        buttons.add(button("Add", "Add a reward", target -> editReward(target, id, category, filter, page)));
        show(player, Messages.SKYBLOCK.text("Rewards: " + id, NamedTextColor.AQUA),
                List.of("Remove or reorder entries, or add another reward."), buttons,
                target -> challengeDetail(target, id, category, filter, page));
    }

    private void sizeRewardChoice(Player player, String id, String category, String filter, int page) {
        List<ActionButton> buttons = Arrays.stream(IslandSize.values()).map(size -> mutationButton(size.name(),
                "Add a " + size.name() + " size upgrade", target -> {
                    reportAndThen(target, challengeAdmin.addReward(id, new ChallengeReward.SizeUpgrade(size)), "challenge reward",
                            next -> rewardsScreen(next, id, category, filter, page));
                })).toList();
        show(player, Messages.SKYBLOCK.text("Size Reward", NamedTextColor.AQUA),
                List.of("Choose the size upgrade."), buttons,
                target -> editReward(target, id, category, filter, page));
    }

    private void editRewardChoice(Player player, String id, String category, String filter, int page,
                                  int index, ChallengeReward current) {
        if (current instanceof ChallengeReward.Money money) {
            input(player, "Edit Money Reward", List.of("amount"),
                    List.of("Current amount: " + money.amount()), (target, values) -> {
                        try {
                            reportAndThen(target, challengeAdmin.editReward(id, index,
                                    new ChallengeReward.Money(Double.parseDouble(targetValue(values, "amount")))),
                                    "challenge reward", next -> rewardsScreen(next, id, category, filter, page));
                        } catch (RuntimeException error) {
                            target.sendMessage(Messages.SKYBLOCK.invalidInput("money reward"));
                        }
                    }, target -> rewardsScreen(target, id, category, filter, page));
            return;
        }
        if (current instanceof ChallengeReward.SizeUpgrade size) {
            List<ActionButton> buttons = Arrays.stream(IslandSize.values()).map(value -> mutationButton(value.name(),
                    value == size.size() ? "Current size" : "Replace with this size", target -> {
                        reportAndThen(target, challengeAdmin.editReward(id, index, new ChallengeReward.SizeUpgrade(value)),
                                "challenge reward", next -> rewardsScreen(next, id, category, filter, page));
                    })).toList();
            show(player, Messages.SKYBLOCK.text("Edit Size Reward", NamedTextColor.AQUA),
                    List.of("Current: " + size.size().name()), buttons,
                    target -> rewardsScreen(target, id, category, filter, page));
            return;
        }
        if (current instanceof ChallengeReward.GeneratorUnlock generator) {
            List<ActionButton> buttons = runtime.generatorRegistry().tiers().stream().map(tier -> mutationButton(tier.id(),
                    tier.id().equals(generator.tierId()) ? "Current generator tier" : tier.displayName(), target -> {
                        reportAndThen(target, challengeAdmin.editReward(id, index,
                                new ChallengeReward.GeneratorUnlock(tier.id())), "challenge reward",
                                next -> rewardsScreen(next, id, category, filter, page));
                    })).toList();
            show(player, Messages.SKYBLOCK.text("Edit Generator Reward", NamedTextColor.AQUA),
                    List.of("Current: " + generator.tierId()), buttons,
                    target -> rewardsScreen(target, id, category, filter, page));
            return;
        }
        ChallengeReward.Items items = (ChallengeReward.Items) current;
        editItemReward(player, id, category, filter, page, index, items.items());
    }

    private void generatorRewardChoice(Player player, String id, String category, String filter, int page) {
        List<ActionButton> buttons = runtime.generatorRegistry().tiers().stream().map(tier -> mutationButton(tier.id(), tier.displayName(), target -> {
            reportAndThen(target, challengeAdmin.addReward(id, new ChallengeReward.GeneratorUnlock(tier.id())), "challenge reward",
                    next -> rewardsScreen(next, id, category, filter, page));
        })).toList();
        show(player, Messages.SKYBLOCK.text("Generator Reward", NamedTextColor.AQUA),
                List.of("Choose the tier to unlock."), buttons,
                target -> editReward(target, id, category, filter, page));
    }

    private void rewardInput(Player player, String id, String category, String filter, int page, String kind) {
        input(player, "Add " + kind + " Reward", List.of("value"), List.of(), (target, values) -> {
            try {
                String value = targetValue(values, "value");
                ChallengeReward reward = switch (kind) {
                    case "money" -> new ChallengeReward.Money(Double.parseDouble(value));
                    case "size" -> new ChallengeReward.SizeUpgrade(IslandSize.valueOf(value.toUpperCase(Locale.ROOT)));
                    default -> new ChallengeReward.GeneratorUnlock(value);
                };
                reportAndThen(target, challengeAdmin.addReward(id, reward), "challenge reward",
                        next -> rewardsScreen(next, id, category, filter, page));
            } catch (RuntimeException error) { target.sendMessage(Messages.SKYBLOCK.invalidInput("challenge reward")); }
        }, target -> editReward(target, id, category, filter, page));
    }

    private void editItemReward(Player player, String id, String category, String filter, int page) {
        editItemReward(player, id, category, filter, page, -1, List.of());
    }

    private void editItemReward(Player player, String id, String category, String filter, int page,
                                int index, List<ItemStack> existing) {
        ItemStack held = player.getInventory().getItemInMainHand();
        List<ItemStack> initial = existing == null || existing.isEmpty()
                ? held == null || held.getType().isAir() ? List.of() : List.of(held.clone())
                : existing.stream().map(ItemStack::clone).toList();
        itemEditor.open(player, Messages.SKYBLOCK.text("Item Reward", NamedTextColor.AQUA), initial, 54,
                this::guard, items -> {
                    try {
                        ChallengeReward reward = new ChallengeReward.Items(items);
                        reportAndThen(player, index < 0 ? challengeAdmin.addReward(id, reward)
                                : challengeAdmin.editReward(id, index, reward), "challenge reward",
                                next -> rewardsScreen(next, id, category, filter, page));
                    }
                    catch (RuntimeException error) { player.sendMessage(Messages.SKYBLOCK.invalidInput("item reward")); }
                });
    }

    private boolean guard(Player player) { return context.guard(player); }
    private void input(Player player, String title, List<String> keys, List<String> body,
                       AdminScreenContext.InputAction save, Consumer<Player> cancel) {
        context.input(player, title, keys, body, save, cancel);
    }
    private void show(Player player, Component title, List<String> body, List<ActionButton> buttons,
                      Consumer<Player> back) {
        context.show(player, title, body, buttons, back);
    }
    private ActionButton button(String label, String tooltip, Consumer<Player> action) {
        return context.button(label, tooltip, action);
    }
    private ActionButton mutationButton(String label, String tooltip, Consumer<Player> action) {
        return context.mutationButton(label, tooltip, action);
    }
    private void reportAndThen(Player player, Object result, String subject, Consumer<Player> next) {
        context.report(player, result, subject, next);
    }
}
