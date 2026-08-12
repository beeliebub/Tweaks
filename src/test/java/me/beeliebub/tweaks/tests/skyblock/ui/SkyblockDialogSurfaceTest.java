package me.beeliebub.tweaks.tests.skyblock.ui;

import io.papermc.paper.registry.data.dialog.ActionButton;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.SkyblockValidation;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRequirement;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeReward;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeService;
import me.beeliebub.tweaks.skyblock.ui.ChallengeGUI;
import me.beeliebub.tweaks.skyblock.ui.IslandGUI;
import me.beeliebub.tweaks.skyblock.ui.ShopGUI;
import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import me.beeliebub.tweaks.skyblock.tracking.TrackKey;
import me.beeliebub.tweaks.skyblock.type.IslandDifficulty;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.inventory.InventoryType;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.dialog.DialogMock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mechanical coverage for the reachable Skyblock Dialog surface. */
class SkyblockDialogSurfaceTest {
    static Stream<String> screenCases() {
        return Stream.of(
                "admin-hub", "admin-settings", "admin-setup",
                "types", "difficulties", "difficulty-detail", "type-detail", "type-validation",
                "default-difficulty", "default-type", "type-difficulties", "type-template", "type-biome",
                "type-allowed", "create-difficulty", "edit-difficulty", "create-type", "edit-type-name",
                "challenges", "category-detail", "challenge-list", "challenge-detail", "challenge-validation",
                "requirements", "add-requirement", "tracked-category", "tracked-material-picker", "entity-picker",
                "tracked-amount", "edit-requirement", "possession-amount", "edit-possession-amount",
                "create-category", "create-challenge", "edit-challenge-text", "edit-challenge-category",
                "type-gating", "prerequisites", "any-of", "any-of-picker", "add-reward", "rewards",
                "size-reward", "edit-money-reward", "edit-size-reward", "edit-generator-reward",
                "generator-reward", "reward-input",
                "generators", "generator-detail", "generator-output-detail", "generator-output-weight",
                "generator-input", "shop", "shop-detail", "shop-input", "material-picker",
                "templates", "template-detail", "capture-template", "spawn", "islands", "island-detail",
                "island-resize", "island-complete", "island-create", "player-challenges", "player-challenge-category",
                "player-challenge-detail", "player-shop", "player-shop-category", "player-shop-quantity");
    }

    static Stream<InputCase> inputCases() {
        return Stream.of(
                new InputCase("create-difficulty", List.of("identifier", "display", "multiplier", "order")),
                new InputCase("edit-difficulty", List.of("display", "multiplier", "order")),
                new InputCase("difficulty-filter", List.of("filter")),
                new InputCase("type-filter", List.of("filter")),
                new InputCase("create-type", List.of("identifier", "display")),
                new InputCase("edit-type-name", List.of("display")),
                new InputCase("category-edit", List.of("name", "order")),
                new InputCase("challenge-filter", List.of("filter")),
                new InputCase("tracked-amount", List.of("amount")),
                new InputCase("possession-amount", List.of("amount")),
                new InputCase("edit-possession-amount", List.of("amount")),
                new InputCase("create-category", List.of("identifier", "name", "order")),
                new InputCase("create-challenge", List.of("identifier", "name", "description")),
                new InputCase("edit-challenge-text", List.of("name", "description")),
                new InputCase("any-of-required", List.of("required")),
                new InputCase("edit-money-reward", List.of("amount")),
                new InputCase("reward-input", List.of("value")),
                new InputCase("generator-display", List.of("name")),
                new InputCase("generator-output-weight", List.of("weight")),
                new InputCase("generator-input", List.of("identifier", "name")),
                new InputCase("shop-filter", List.of("filter")),
                new InputCase("shop-input", List.of("category", "buy", "sell")),
                new InputCase("material-filter", List.of("filter")),
                new InputCase("entity-filter", List.of("filter")),
                new InputCase("island-filter", List.of("filter")),
                new InputCase("capture-template", List.of("identifier")));
    }

    @ParameterizedTest(name = "dialog: {0}")
    @MethodSource("screenCases")
    void everyReachableEntryPointOpensADialog(String screen) {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            open(screen, fixture);
            DialogTestHelper.pump(fixture.server);
            assertNotNull(DialogTestHelper.requireDialog(fixture.admin),
                    "screen case must leave a Dialog open: " + screen);
        }
    }

    @ParameterizedTest(name = "button: {0}")
    @MethodSource("screenCases")
    void everyReachableButtonHasAnObservableOutcome(String screen) {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            open(screen, fixture);
            DialogTestHelper.pump(fixture.server);
            List<String> labels = DialogTestHelper.buttons(DialogTestHelper.requireDialog(fixture.admin)).stream()
                    .map(DialogTestHelper::label).toList();
            assertFalse(labels.isEmpty(), "screen must expose at least one action: " + screen);

            for (String label : labels) {
                fixture.admin.closeDialog();
                fixture.admin.closeInventory();
                open(screen, fixture);
                DialogTestHelper.pump(fixture.server);
                DialogMock dialog = DialogTestHelper.requireDialog(fixture.admin);
                ActionButton button = DialogTestHelper.buttons(dialog).stream()
                        .filter(candidate -> label.equals(DialogTestHelper.label(candidate)))
                        .findFirst().orElseThrow(() -> new AssertionError("missing button " + label));
                Map<String, String> values = new LinkedHashMap<>();
                for (String key : DialogTestHelper.inputKeys(dialog)) values.put(key, key + "-sentinel");
                int shownBefore = fixture.admin.getShownDialogs().size();
                Location locationBefore = fixture.admin.getLocation().clone();

                DialogTestHelper.click(fixture.admin, button, values);
                DialogTestHelper.pump(fixture.server);

                boolean message = fixture.admin.nextComponentMessage() != null;
                boolean dialogShown = fixture.admin.getOpenDialog() != null
                        || fixture.admin.getShownDialogs().size() > shownBefore;
                boolean inventoryShown = fixture.admin.getOpenInventory().getType() != InventoryType.CRAFTING;
                boolean moved = !locationBefore.equals(fixture.admin.getLocation());
                assertTrue(message || dialogShown || inventoryShown || moved,
                        "button produced no observable outcome: " + screen + " / " + label);
            }
        }
    }

    @ParameterizedTest(name = "input: {0}")
    @MethodSource("inputCases")
    void everyInputScreenDeclaresAndConsumesItsExpectedKeys(InputCase inputCase) {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            openInput(inputCase.name(), fixture);
            DialogMock dialog = DialogTestHelper.requireDialog(fixture.admin);
            assertEquals(inputCase.keys(), DialogTestHelper.inputKeys(dialog), inputCase.name());

            Map<String, String> values = new LinkedHashMap<>();
            for (String key : inputCase.keys()) values.put(key, key + "-sentinel");
            ActionButton save = DialogTestHelper.buttons(dialog).stream()
                    .filter(button -> "Save".equals(DialogTestHelper.label(button)))
                    .findFirst().orElseThrow(() -> new AssertionError("input has no Save action"));
            DialogTestHelper.click(fixture.admin, save, values);
            DialogTestHelper.pump(fixture.server);
            assertTrue(fixture.admin.nextComponentMessage() != null
                            || fixture.admin.getOpenDialog() != null,
                    "sentinel submission had no observable result: " + inputCase.name());
        }
    }

    @Test
    void anAbsentInputKeyIsReportedAsMissingInsteadOfBlank() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            open("create-difficulty", fixture);
            DialogMock dialog = DialogTestHelper.requireDialog(fixture.admin);
            ActionButton save = DialogTestHelper.buttons(dialog).stream()
                    .filter(button -> "Save".equals(DialogTestHelper.label(button)))
                    .findFirst().orElseThrow();
            DialogTestHelper.click(fixture.admin, save, Map.of(
                    "identifier", "custom", "display", "Custom", "multiplier", "1"));
            String message = String.valueOf(fixture.admin.nextComponentMessage());
            assertTrue(message.contains("order"), message);
        }
    }

    private static void openInput(String input, SkyblockDialogFixture fixture) {
        switch (input) {
            case "create-difficulty", "edit-difficulty", "create-type", "edit-type-name", "create-category",
                    "create-challenge", "edit-challenge-text", "tracked-amount", "possession-amount",
                    "edit-possession-amount", "edit-money-reward", "reward-input", "generator-output-weight",
                    "generator-input", "shop-input" -> open(input, fixture);
            case "difficulty-filter" -> clickButton("difficulties", "Filter", fixture);
            case "type-filter" -> clickButton("types", "Filter", fixture);
            case "challenge-filter" -> {
                Object challenges = DialogTestHelper.field(fixture.screens, "challenges");
                DialogTestHelper.invoke(challenges, "challengesIn", fixture.admin, fixture.category.id(), "", 0);
                clickCurrentButton("Filter", fixture);
            }
            case "category-edit" -> {
                open("category-detail", fixture);
                clickCurrentButton("Edit category", fixture);
            }
            case "any-of-required" -> {
                Object challenges = DialogTestHelper.field(fixture.screens, "challenges");
                DialogTestHelper.invoke(challenges, "anyOfGroupPicker", fixture.admin, fixture.challenge.id(),
                        fixture.category.id(), "", 0, new LinkedHashSet<String>());
                clickCurrentButton("Set required count", fixture);
            }
            case "generator-display" -> {
                open("generator-detail", fixture);
                clickCurrentButton("Display name", fixture);
            }
            case "shop-filter" -> clickButton("shop", "Filter", fixture);
            case "material-filter" -> {
                Object economy = DialogTestHelper.field(fixture.screens, "economy");
                DialogTestHelper.invoke(economy, "materialPicker", fixture.admin, "Material", (Consumer<Material>) ignored -> {
                }, (Consumer<org.bukkit.entity.Player>) ignored -> {
                }, 0, "");
                clickCurrentButton("Filter", fixture);
            }
            case "entity-filter" -> {
                Object challenges = DialogTestHelper.field(fixture.screens, "challenges");
                DialogTestHelper.invoke(challenges, "entityPicker", fixture.admin, "Entity", (Consumer<EntityType>) ignored -> {
                }, (Consumer<org.bukkit.entity.Player>) ignored -> {
                }, 0, "");
                clickCurrentButton("Filter", fixture);
            }
            case "island-filter" -> clickButton("islands", "Filter", fixture);
            case "capture-template" -> open("capture-template", fixture);
            default -> throw new IllegalArgumentException("Unknown input case: " + input);
        }
    }

    private static void clickButton(String screen, String label, SkyblockDialogFixture fixture) {
        open(screen, fixture);
        clickCurrentButton(label, fixture);
    }

    private static void clickCurrentButton(String label, SkyblockDialogFixture fixture) {
        DialogMock dialog = DialogTestHelper.requireDialog(fixture.admin);
        ActionButton button = DialogTestHelper.buttons(dialog).stream()
                .filter(candidate -> label.equals(DialogTestHelper.label(candidate)))
                .findFirst().orElseThrow(() -> new AssertionError("missing button " + label));
        DialogTestHelper.click(fixture.admin, button, Map.of());
        DialogTestHelper.pump(fixture.server);
    }

    private static void open(String screen, SkyblockDialogFixture fixture) {
        Object screens = fixture.screens;
        Object types = DialogTestHelper.field(screens, "types");
        Object challenges = DialogTestHelper.field(screens, "challenges");
        Object economy = DialogTestHelper.field(screens, "economy");
        Object worlds = DialogTestHelper.field(screens, "worlds");
        Object islands = DialogTestHelper.field(screens, "islands");
        switch (screen) {
            case "admin-hub" -> fixture.screens.open(fixture.admin);
            case "admin-settings", "admin-setup" -> DialogTestHelper.invoke(screens,
                    screen.equals("admin-settings") ? "openSettings" : "runSetup", fixture.admin);
            case "types" -> DialogTestHelper.invoke(types, "open", fixture.admin);
            case "difficulties" -> DialogTestHelper.invoke(types, "openDifficulties", fixture.admin);
            case "difficulty-detail" -> DialogTestHelper.invoke(types, "difficultyDetail", fixture.admin, fixture.normal.id());
            case "type-detail" -> DialogTestHelper.invoke(types, "typeDetail", fixture.admin, fixture.defaultType.id());
            case "type-validation" -> DialogTestHelper.invoke(types, "showTypeValidation", fixture.admin,
                    fixture.defaultType, List.of(new SkyblockValidation.Problem("TEST", "test validation problem")));
            case "default-difficulty" -> DialogTestHelper.invoke(types, "chooseDefaultDifficulty", fixture.admin, fixture.defaultType);
            case "default-type" -> DialogTestHelper.invoke(types, "chooseDefaultType", fixture.admin, fixture.normal.id());
            case "type-difficulties" -> DialogTestHelper.invoke(types, "chooseTypeDifficulties", fixture.admin,
                    fixture.defaultType.id(), new LinkedHashSet<>(Set.of(fixture.normal.id())));
            case "type-template" -> DialogTestHelper.invoke(types, "editTypeTemplate", fixture.admin, fixture.defaultType.id());
            case "type-biome" -> DialogTestHelper.invoke(types, "editTypeBiome", fixture.admin, fixture.defaultType.id());
            case "type-allowed" -> DialogTestHelper.invoke(types, "editTypeAllowed", fixture.admin, fixture.defaultType.id());
            case "create-difficulty" -> DialogTestHelper.invoke(types, "createDifficulty", fixture.admin);
            case "edit-difficulty" -> DialogTestHelper.invoke(types, "editDifficulty", fixture.admin, fixture.normal);
            case "create-type" -> DialogTestHelper.invoke(types, "createType", fixture.admin);
            case "edit-type-name" -> DialogTestHelper.invoke(types, "editTypeName", fixture.admin, fixture.defaultType.id());
            case "challenges" -> DialogTestHelper.invoke(challenges, "open", fixture.admin);
            case "category-detail" -> DialogTestHelper.invoke(challenges, "categoryDetail", fixture.admin, fixture.category.id());
            case "challenge-list" -> DialogTestHelper.invoke(challenges, "challengesIn", fixture.admin,
                    fixture.category.id(), "", 0);
            case "challenge-detail" -> DialogTestHelper.invoke(challenges, "challengeDetail", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "challenge-validation" -> DialogTestHelper.invoke(challenges, "showValidation", fixture.admin,
                    fixture.challenge, List.of(new SkyblockValidation.Problem("TEST", "test validation problem")),
                    fixture.category.id(), "", 0);
            case "requirements" -> DialogTestHelper.invoke(challenges, "requirementsScreen", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "add-requirement" -> DialogTestHelper.invoke(challenges, "addRequirementChoice", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "tracked-category" -> DialogTestHelper.invoke(challenges, "trackedCategoryChoice", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "tracked-material-picker" -> DialogTestHelper.invoke(challenges, "trackedIdentifierPicker", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0, TrackCategory.KILL,
                    (Consumer<String>) ignored -> fixture.context.show(fixture.admin,
                            Messages.SKYBLOCK.text("Selected", NamedTextColor.AQUA), List.of("Identifier selected."), List.of(), null));
            case "entity-picker" -> DialogTestHelper.invoke(challenges, "entityPicker", fixture.admin, "Entity",
                    (Consumer<EntityType>) ignored -> fixture.context.show(fixture.admin,
                            Messages.SKYBLOCK.text("Selected", NamedTextColor.AQUA), List.of("Entity selected."), List.of(), null),
                    (Consumer<org.bukkit.entity.Player>) ignored -> fixture.context.show(fixture.admin,
                            Messages.SKYBLOCK.text("Back", NamedTextColor.AQUA), List.of("Back selected."), List.of(), null), 0, "");
            case "tracked-amount" -> DialogTestHelper.invoke(challenges, "trackedAmountInput", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0, TrackCategory.COLLECT, "STONE", false, -1);
            case "edit-requirement" -> DialogTestHelper.invoke(challenges, "editRequirementChoice", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0, 0,
                    new ChallengeRequirement.Tracked(new TrackKey(TrackCategory.KILL, "ZOMBIE"), 1));
            case "possession-amount" -> DialogTestHelper.invoke(challenges, "possessionAmount",
                    Material.STONE, fixture.admin, fixture.challenge.id(), fixture.category.id(), "", 0);
            case "edit-possession-amount" -> DialogTestHelper.invoke(challenges, "possessionAmountEdit",
                    Material.STONE, fixture.admin, fixture.challenge.id(), fixture.category.id(), "", 0, 0);
            case "create-category" -> DialogTestHelper.invoke(challenges, "createCategory", fixture.admin);
            case "create-challenge" -> DialogTestHelper.invoke(challenges, "createChallenge", fixture.admin);
            case "edit-challenge-text" -> DialogTestHelper.invoke(challenges, "editChallengeText", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "edit-challenge-category" -> DialogTestHelper.invoke(challenges, "editChallengeCategory", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "type-gating" -> DialogTestHelper.invoke(challenges, "typeGatingPicker", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0, new LinkedHashSet<String>());
            case "prerequisites" -> DialogTestHelper.invoke(challenges, "editPrerequisites", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "any-of" -> DialogTestHelper.invoke(challenges, "anyOfScreen", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "any-of-picker" -> DialogTestHelper.invoke(challenges, "anyOfGroupPicker", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0, new LinkedHashSet<String>());
            case "add-reward" -> DialogTestHelper.invoke(challenges, "editReward", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "rewards" -> DialogTestHelper.invoke(challenges, "rewardsScreen", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "size-reward" -> DialogTestHelper.invoke(challenges, "sizeRewardChoice", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "edit-money-reward" -> DialogTestHelper.invoke(challenges, "editRewardChoice", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0, 0, new ChallengeReward.Money(1));
            case "edit-size-reward" -> DialogTestHelper.invoke(challenges, "editRewardChoice", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0, 0, new ChallengeReward.SizeUpgrade(me.beeliebub.tweaks.skyblock.island.IslandSize.SMALL));
            case "edit-generator-reward" -> DialogTestHelper.invoke(challenges, "editRewardChoice", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0, 0, new ChallengeReward.GeneratorUnlock(fixture.defaultGenerator.id()));
            case "generator-reward" -> DialogTestHelper.invoke(challenges, "generatorRewardChoice", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0);
            case "reward-input" -> DialogTestHelper.invoke(challenges, "rewardInput", fixture.admin,
                    fixture.challenge.id(), fixture.category.id(), "", 0, "money");
            case "generators" -> DialogTestHelper.invoke(economy, "openGenerators", fixture.admin);
            case "generator-detail" -> DialogTestHelper.invoke(economy, "generatorDetail", fixture.admin,
                    fixture.defaultGenerator.id());
            case "generator-output-detail" -> DialogTestHelper.invoke(economy, "generatorOutputDetail", fixture.admin,
                    fixture.defaultGenerator.id(), Material.COBBLESTONE);
            case "generator-output-weight" -> DialogTestHelper.invoke(economy, "generatorOutputWeight",
                    Material.COBBLESTONE, fixture.admin, fixture.defaultGenerator.id());
            case "generator-input" -> DialogTestHelper.invoke(economy, "generatorInput", fixture.admin, false);
            case "shop" -> DialogTestHelper.invoke(economy, "openShop", fixture.admin);
            case "shop-detail" -> DialogTestHelper.invoke(economy, "shopDetail", fixture.admin, Material.COBBLESTONE);
            case "shop-input" -> DialogTestHelper.invoke(economy, "shopEntryInput", fixture.admin, Material.COBBLESTONE);
            case "material-picker" -> DialogTestHelper.invoke(economy, "materialPicker", fixture.admin, "Material",
                    (Consumer<Material>) ignored -> fixture.context.show(fixture.admin,
                            Messages.SKYBLOCK.text("Selected", NamedTextColor.AQUA), List.of("Material selected."), List.of(), null),
                    (Consumer<org.bukkit.entity.Player>) ignored -> fixture.context.show(fixture.admin,
                            Messages.SKYBLOCK.text("Back", NamedTextColor.AQUA), List.of("Back selected."), List.of(), null), 0, "");
            case "templates" -> DialogTestHelper.invoke(worlds, "openTemplates", fixture.admin);
            case "template-detail" -> DialogTestHelper.invoke(worlds, "templateDetail", fixture.admin, fixture.template.id());
            case "capture-template" -> DialogTestHelper.invoke(worlds, "captureTemplate", fixture.admin);
            case "spawn" -> DialogTestHelper.invoke(worlds, "openSpawn", fixture.admin);
            case "islands" -> DialogTestHelper.invoke(islands, "open", fixture.admin);
            case "island-detail" -> DialogTestHelper.invoke(islands, "islandDetail", fixture.admin, fixture.island.id());
            case "island-resize" -> DialogTestHelper.invoke(islands, "resizeIsland", fixture.admin, fixture.island.id());
            case "island-complete" -> DialogTestHelper.invoke(islands, "completeChallenge", fixture.admin, fixture.island.id());
            case "island-create" -> new IslandGUI(fixture.typeRegistry,
                    org.mockito.Mockito.mock(me.beeliebub.tweaks.skyblock.island.IslandCreationService.class)).open(fixture.admin);
            case "player-challenges" -> new ChallengeGUI(fixture.challengeRegistry, fixture.challengeService)
                    .open(fixture.admin, fixture.island);
            case "player-challenge-category" -> {
                ChallengeGUI gui = new ChallengeGUI(fixture.challengeRegistry, fixture.challengeService);
                DialogTestHelper.invoke(gui, "openCategory", fixture.admin, fixture.island, fixture.category.id(), 0);
            }
            case "player-challenge-detail" -> {
                ChallengeGUI gui = new ChallengeGUI(fixture.challengeRegistry, fixture.challengeService);
                DialogTestHelper.invoke(gui, "openDetail", fixture.admin, fixture.island, fixture.challenge);
            }
            case "player-shop" -> new ShopGUI(fixture.shopCatalog, fixture.shopService).open(fixture.admin);
            case "player-shop-category" -> {
                ShopGUI gui = new ShopGUI(fixture.shopCatalog, fixture.shopService);
                DialogTestHelper.invoke(gui, "openCategory", fixture.admin, fixture.shopEntry.category(), 0);
            }
            case "player-shop-quantity" -> {
                ShopGUI gui = new ShopGUI(fixture.shopCatalog, fixture.shopService);
                DialogTestHelper.invoke(gui, "openQuantity", fixture.admin, fixture.shopEntry.category(), fixture.shopEntry);
            }
            default -> throw new IllegalArgumentException("Unknown screen case: " + screen);
        }
    }

    private record InputCase(String name, List<String> keys) {
    }
}
