package me.beeliebub.tweaks.skyblock.ui.admin;

import static me.beeliebub.tweaks.skyblock.ui.admin.AdminScreenContext.targetValue;

import io.papermc.paper.registry.data.dialog.ActionButton;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.protection.ui.RegionSelection;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.SkyblockDescriptions;
import me.beeliebub.tweaks.skyblock.SkyblockValidation;
import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminService;
import me.beeliebub.tweaks.skyblock.type.IslandDifficulty;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.type.TypeAdminService;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public final class TypeScreens {
    private final AdminScreenContext context;
    private final JavaPlugin plugin;
    private final SkyblockBootstrap.Runtime runtime;
    private final TypeAdminService typeAdmin;
    private final AdminItemEditor itemEditor;
    private final Set<UUID> setupWizards;
    private final Consumer<Player> hub;

    TypeScreens(AdminScreenContext context, Consumer<Player> hub) {
        this.context = context;
        this.plugin = context.plugin;
        this.runtime = context.runtime;
        this.typeAdmin = context.typeAdmin;
        this.itemEditor = context.itemEditor;
        this.setupWizards = context.setupWizards;
        this.hub = hub;
    }

    public void open(Player player) {
        openTypes(player, 0, "");
    }

    public void openDifficulties(Player player) {
        openDifficulties(player, 0, "");
    }

        private void openDifficulties(Player player, int pageNumber, String filter) {
        if (!guard(player)) return;
        List<IslandDifficulty> difficulties = runtime.typeRegistry().difficulties();
        AdminPage.Page<IslandDifficulty> page = AdminPage.create(difficulties, pageNumber, filter,
                difficulty -> difficulty.id() + " " + difficulty.displayName());
        List<ActionButton> buttons = new ArrayList<>();
        for (IslandDifficulty difficulty : page.values()) buttons.add(button(difficulty.id(), difficulty.displayName(),
                target -> difficultyDetail(target, difficulty.id())));
        if (page.hasPrevious()) buttons.add(button("Previous", "Previous difficulty page",
                target -> openDifficulties(target, page.page() - 1, filter)));
        if (page.hasNext()) buttons.add(button("Next", "Next difficulty page",
                target -> openDifficulties(target, page.page() + 1, filter)));
        buttons.add(button("Filter", "Literal difficulty filter", target -> input(target, "Difficulty Filter", List.of("filter"),
                List.of("Filtering uses literal text matching."), (actor, values) -> openDifficulties(actor, 0,
                        AdminPage.literalFilter(targetValue(values, "filter"))),
                actor -> openDifficulties(actor, page.page(), filter))));
        buttons.add(button("Create", "Create a difficulty", this::createDifficulty));
        show(player, Messages.SKYBLOCK.text("Difficulties", NamedTextColor.AQUA),
                List.of("Choose a difficulty. " + page.label()), buttons, hub);
    }

    private void difficultyDetail(Player player, String id) {
        if (!guard(player)) return;
        IslandDifficulty difficulty = runtime.typeRegistry().difficulty(id).orElse(null);
        if (difficulty == null) { openDifficulties(player); return; }
        long references = runtime.typeRegistry().types().stream()
                .filter(type -> type.difficultyIds().contains(id)).count()
                + runtime.islandManager().all().stream()
                .filter(island -> id.equalsIgnoreCase(island.difficultyId())).count();
        List<ActionButton> buttons = new ArrayList<>(List.of(button("Edit", "Edit display name, multiplier, and order",
                        target -> editDifficulty(target, difficulty)),
                button("Delete", "Delete this difficulty", target ->
                AdminConfirm.open(target, "difficulty " + id, references,
                        "Deleting a difficulty can prevent types from being edited.",
                        () -> openDifficulties(target), actor -> {
                            TypeRegistry.DeleteResult result = typeAdmin.deleteDifficulty(id);
                            report(actor, result, "difficulty");
                            openDifficulties(actor);
                        }, this::guard))));
        buttons.add(button("Make default", "Use this difficulty for the configured default type when allowed",
                target -> chooseDefaultType(target, difficulty.id())));
        show(player, Messages.SKYBLOCK.text(difficulty.displayName(), NamedTextColor.AQUA),
                List.of("Id: " + difficulty.id(), "Multiplier: " + difficulty.multiplier(),
                        "Order: " + difficulty.order()), buttons, this::openDifficulties);
    }

    private void createDifficulty(Player player) {
        if (!guard(player)) return;
        input(player, "Create Difficulty", List.of("id", "display", "multiplier", "order"),
                List.of("Use a lowercase id and a positive multiplier."), (target, values) -> {
                    try {
                        String id = targetValue(values, "id");
                        double multiplier = Double.parseDouble(targetValue(values, "multiplier"));
                        int order = Integer.parseInt(targetValue(values, "order"));
                        report(target, typeAdmin.createDifficulty(new IslandDifficulty(id, targetValue(values, "display"), order, multiplier)),
                                "difficulty");
                        openDifficulties(target);
                    } catch (RuntimeException error) {
                        target.sendMessage(Messages.SKYBLOCK.invalidInput("difficulty"));
                    }
                }, this::openDifficulties);
    }

    private void editDifficulty(Player player, IslandDifficulty current) {
        input(player, "Edit Difficulty", List.of("display", "multiplier", "order"), List.of(), (target, values) -> {
            try {
                report(target, typeAdmin.registerDifficulty(new IslandDifficulty(current.id(), targetValue(values, "display"),
                        Integer.parseInt(targetValue(values, "order")), Double.parseDouble(targetValue(values, "multiplier")))),
                        "difficulty");
            } catch (RuntimeException error) {
                target.sendMessage(Messages.SKYBLOCK.invalidInput("difficulty"));
            }
            openDifficulties(target);
        }, target -> difficultyDetail(target, current.id()));
    }

    private void openTypes(Player player) {
        openTypes(player, 0, "");
    }

        private void openTypes(Player player, int pageNumber, String filter) {
        if (!guard(player)) return;
        List<IslandType> types = new ArrayList<>(runtime.typeRegistry().types());
        AdminPage.Page<IslandType> page = AdminPage.create(types, pageNumber, filter,
                type -> type.id() + " " + type.displayName());
        List<ActionButton> buttons = new ArrayList<>();
        for (IslandType type : page.values()) buttons.add(button(type.id(), type.displayName(),
                target -> typeDetail(target, type.id())));
        if (page.hasPrevious()) buttons.add(button("Previous", "Previous type page",
                target -> openTypes(target, page.page() - 1, filter)));
        if (page.hasNext()) buttons.add(button("Next", "Next type page",
                target -> openTypes(target, page.page() + 1, filter)));
        buttons.add(button("Filter", "Literal type filter", target -> input(target, "Type Filter", List.of("filter"),
                List.of("Filtering uses literal text matching."), (actor, values) -> openTypes(actor, 0,
                        AdminPage.literalFilter(targetValue(values, "filter"))),
                actor -> openTypes(actor, page.page(), filter))));
        buttons.add(button("Create", "Create an island type", this::createType));
        show(player, Messages.SKYBLOCK.text("Island Types", NamedTextColor.AQUA),
                List.of("Types: " + types.size() + " " + page.label()), buttons, hub);
    }

    private void typeDetail(Player player, String id) {
        if (!guard(player)) return;
        IslandType type = runtime.typeRegistry().type(id).orElse(null);
        if (type == null) { openTypes(player); return; }
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("Display name", "Edit display name", target -> editTypeName(target, id)));
        buttons.add(button("Difficulties", String.join(", ", type.difficultyIds()), target -> editTypeDifficulties(target, id)));
        buttons.add(button("Template", type.templateId().isBlank() ? "none" : type.templateId(), target -> editTypeTemplate(target, id)));
        buttons.add(button("Biome", type.biome(), target -> editTypeBiome(target, id)));
        buttons.add(button("Allowed challenges", String.valueOf(type.allowedChallengeIds().size()),
                target -> editTypeAllowed(target, id)));
        List<SkyblockValidation.Problem> problems = SkyblockValidation.validateIslandType(type,
                runtime.typeRegistry().difficulties(), new LinkedHashSet<>(runtime.templateStore().ids()));
        buttons.add(button(problems.isEmpty() ? "Validation: valid" : "Validation: invalid",
                problems.isEmpty() ? "No validation problems" : problems.get(0).message(),
                target -> showTypeValidation(target, type, problems)));
        buttons.add(button("Make default", "Choose this type and one of its difficulties as the bare-island default",
                target -> chooseDefaultDifficulty(target, type)));
        buttons.add(button("Edit Kit", type.kit().size() + " item(s), copy-only", target -> editKit(target, id)));
        long references = runtime.islandManager().all().stream().filter(island -> id.equalsIgnoreCase(island.typeId())).count();
        buttons.add(button("Delete", "Delete this island type", target -> AdminConfirm.open(target, "type " + id, references,
                "This removes the type definition and cannot create islands using it.", () -> openTypes(target), actor -> {
                    TypeRegistry.DeleteResult result = typeAdmin.deleteType(id);
                    report(actor, result, "island type");
                    openTypes(actor);
                }, this::guard)));
        show(player, Messages.SKYBLOCK.text("Type: " + id, NamedTextColor.AQUA),
                List.of("Template: " + type.templateId(), "Biome: " + type.biome(),
                        "Kit entries: " + type.kit().size()), buttons, this::openTypes);
    }

    private void showTypeValidation(Player player, IslandType type, List<SkyblockValidation.Problem> problems) {
        List<String> body = new ArrayList<>();
        body.add(SkyblockDescriptions.islandType(type));
        body.addAll(problems.isEmpty() ? List.of("No validation problems.")
                : problems.stream().map(SkyblockValidation.Problem::message).toList());
        show(player, Messages.SKYBLOCK.text("Validation: " + type.id(), NamedTextColor.AQUA), body,
                List.of(), target -> typeDetail(target, type.id()));
    }

    private void chooseDefaultDifficulty(Player player, IslandType type) {
        if (!guard(player)) return;
        List<ActionButton> buttons = new ArrayList<>();
        for (String difficultyId : type.difficultyIds()) {
            IslandDifficulty difficulty = runtime.typeRegistry().difficulty(difficultyId).orElse(null);
            if (difficulty == null) continue;
            buttons.add(button(difficulty.id(), difficulty.displayName(), target -> {
                runtime.config().setDefaultSelection(type.id(), difficulty.id());
                context.report(target, Boolean.TRUE, "default selection");
                typeDetail(target, type.id());
            }));
        }
        show(player, Messages.SKYBLOCK.text("Default Difficulty", NamedTextColor.AQUA),
                List.of("Choose the difficulty paired with type " + type.id() + "."), buttons,
                target -> typeDetail(target, type.id()));
    }

    private void chooseDefaultType(Player player, String difficultyId) {
        if (!guard(player)) return;
        List<ActionButton> buttons = new ArrayList<>();
        for (IslandType type : runtime.typeRegistry().types()) {
            if (!type.allowsDifficulty(difficultyId)) continue;
            buttons.add(button(type.id(), type.displayName(), target -> {
                runtime.config().setDefaultSelection(type.id(), difficultyId);
                context.report(target, Boolean.TRUE, "default selection");
                difficultyDetail(target, difficultyId);
            }));
        }
        show(player, Messages.SKYBLOCK.text("Default Type", NamedTextColor.AQUA),
                List.of("Choose a type that allows difficulty " + difficultyId + "."), buttons,
                target -> difficultyDetail(target, difficultyId));
    }

    private void createType(Player player) {
        input(player, "Create Island Type", List.of("id", "display"),
                List.of("After creation, choose its difficulties, template, biome, allowed challenges, and kit."), (target, values) -> {
                    try {
                        String id = targetValue(values, "id");
                        TypeAdminService.EditResult result = typeAdmin.createType(new IslandType(id, targetValue(values, "display"),
                                Set.of(), "", List.of(), "PLAINS", Set.of()));
                        report(target, result, "island type");
                        if (result.success()) typeDetail(target, id);
                        else openTypes(target);
                    } catch (RuntimeException error) {
                        target.sendMessage(Messages.SKYBLOCK.invalidInput(error.getMessage() == null ? "island type" : error.getMessage()));
                    }
                }, this::openTypes);
    }

    private void editTypeName(Player player, String id) {
        input(player, "Display Name", List.of("display"), List.of("Free text display name."), (target, values) -> {
            TypeAdminService.EditResult result = typeAdmin.setDisplayName(id, targetValue(values, "display"));
            report(target, result, "island type");
            afterTypeSave(target, id);
        }, target -> typeDetail(target, id));
    }

    private void editTypeDifficulties(Player player, String id) {
        IslandType type = runtime.typeRegistry().type(id).orElse(null);
        if (type == null) { openTypes(player); return; }
        chooseTypeDifficulties(player, id, new LinkedHashSet<>(type.difficultyIds()));
    }

    private void editTypeTemplate(Player player, String id) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("(none)", "Use the default empty template", target -> saveTypeTemplate(target, id, "")));
        for (String templateId : runtime.templateStore().ids()) {
            buttons.add(button(templateId, "Choose this template", target -> saveTypeTemplate(target, id, templateId)));
        }
        show(player, Messages.SKYBLOCK.text("Type Template", NamedTextColor.AQUA),
                List.of("Choose a captured template."), buttons, target -> typeDetail(target, id));
    }

    private void editTypeBiome(Player player, String id) {
        List<Biome> biomes = Arrays.asList(Biome.values());
        chooseBiome(player, id, biomes, 0);
    }

    private void editTypeAllowed(Player player, String id) {
        IslandType type = runtime.typeRegistry().type(id).orElse(null);
        if (type == null) { openTypes(player); return; }
        chooseAllowedChallenges(player, id, new LinkedHashSet<>(type.allowedChallengeIds()));
    }

    private void chooseTypeDifficulties(Player player, String id, Set<String> selected) {
        List<ActionButton> buttons = new ArrayList<>();
        for (IslandDifficulty difficulty : runtime.typeRegistry().difficulties()) {
            boolean active = selected.contains(difficulty.id());
            buttons.add(button((active ? "[x] " : "[ ] ") + difficulty.id(), difficulty.displayName(), target -> {
                Set<String> next = new LinkedHashSet<>(selected);
                if (!next.add(difficulty.id())) next.remove(difficulty.id());
                chooseTypeDifficulties(target, id, next);
            }));
        }
        buttons.add(button("Save", "Save selected difficulties", target -> {
            report(target, typeAdmin.setDifficulties(id, selected), "island type");
            afterTypeSave(target, id);
        }));
        show(player, Messages.SKYBLOCK.text("Type Difficulties", NamedTextColor.AQUA),
                List.of("Select at least one difficulty before using the type."), buttons,
                target -> typeDetail(target, id));
    }

    private void saveTypeTemplate(Player player, String id, String templateId) {
        report(player, typeAdmin.setTemplate(id, templateId), "island type");
        afterTypeSave(player, id);
    }

    private void chooseBiome(Player player, String id, List<Biome> biomes, int pageNumber) {
        AdminPage.Page<Biome> page = AdminPage.create(biomes, pageNumber, "", biome -> biome.name());
        List<ActionButton> buttons = new ArrayList<>();
        for (Biome biome : page.values()) buttons.add(button(biome.name(), "Choose this biome", target -> {
            report(target, typeAdmin.setBiome(id, biome.name()), "island type");
            afterTypeSave(target, id);
        }));
        if (page.hasPrevious()) buttons.add(button("Previous", "Previous biome page", target -> chooseBiome(target, id, biomes, page.page() - 1)));
        if (page.hasNext()) buttons.add(button("Next", "Next biome page", target -> chooseBiome(target, id, biomes, page.page() + 1)));
        show(player, Messages.SKYBLOCK.text("Type Biome " + page.label(), NamedTextColor.AQUA),
                List.of("Choose a validated Bukkit biome."), buttons, target -> typeDetail(target, id));
    }

    private void chooseAllowedChallenges(Player player, String id, Set<String> selected) {
        List<ActionButton> buttons = new ArrayList<>();
        for (var challenge : runtime.challengeRegistry().challenges()) {
            boolean active = selected.contains(challenge.id());
            buttons.add(button((active ? "[x] " : "[ ] ") + challenge.id(), challenge.displayName(), target -> {
                Set<String> next = new LinkedHashSet<>(selected);
                if (!next.add(challenge.id())) next.remove(challenge.id());
                chooseAllowedChallenges(target, id, next);
            }));
        }
        buttons.add(button("Save", "Save allowed challenges", target -> {
            report(target, typeAdmin.setAllowedChallenges(id, selected), "island type");
            afterTypeSave(target, id);
        }));
        show(player, Messages.SKYBLOCK.text("Allowed Challenges", NamedTextColor.AQUA),
                List.of("No selected challenges means the type allows every challenge."), buttons,
                target -> typeDetail(target, id));
    }

    private void editKit(Player player, String id) {
        IslandType type = runtime.typeRegistry().type(id).orElse(null);
        if (type == null) { openTypes(player); return; }
        itemEditor.open(player, Messages.SKYBLOCK.text("Kit: " + id, NamedTextColor.AQUA),
                type.kit().stream().map(IslandType.KitItem::itemStack).toList(), IslandType.KIT_CONTAINER_SIZE,
                this::guard, items -> {
                    try {
                        TypeAdminService.EditResult result = typeAdmin.setKit(id,
                                items.stream().map(IslandType.KitItem::new).toList());
                        report(player, result, "island kit");
                    } catch (RuntimeException error) {
                        player.sendMessage(Messages.SKYBLOCK.invalidInput("kit item"));
                    }
                    if (!setupWizards.contains(player.getUniqueId())) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> typeDetail(player, id));
                    }
                });
    }

    private void afterTypeSave(Player player, String id) {
        if (setupWizards.contains(player.getUniqueId())) return;
        runtime.typeRegistry().flush().whenComplete((ignored, error) -> plugin.getServer().getScheduler().runTask(plugin,
                () -> { if (guard(player)) typeDetail(player, id); }));
    }

    private boolean guard(Player player) { return context.guard(player); }
    private void advanceSetup(Player player, boolean success) { context.advanceSetup(player, success); }
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
    private void report(Player player, Object result, String subject) {
        context.report(player, result, subject);
    }
}
