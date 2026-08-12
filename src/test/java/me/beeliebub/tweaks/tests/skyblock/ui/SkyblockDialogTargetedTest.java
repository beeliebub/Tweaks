package me.beeliebub.tweaks.tests.skyblock.ui;

import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import me.beeliebub.tweaks.skyblock.SkyblockSetupStatus;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeAdminService;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRegistry;
import me.beeliebub.tweaks.skyblock.economy.ShopAdminService;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.generator.GeneratorAdminService;
import me.beeliebub.tweaks.skyblock.generator.GeneratorRegistry;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import me.beeliebub.tweaks.skyblock.island.IslandCreationService;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.skyblock.type.IslandDifficulty;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.type.TypeAdminService;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import me.beeliebub.tweaks.skyblock.ui.admin.AdminScreenContext;
import me.beeliebub.tweaks.skyblock.ui.IslandGUI;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkyblockDialogTargetedTest {
    @Test
    void islandCreationDialogFiltersEmptyDifficultiesAndRequeriesTypes() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            IslandDifficulty empty = new IslandDifficulty("empty", "Empty", 1);
            IslandDifficulty hard = new IslandDifficulty("hard", "Hard", 2);
            IslandType hardType = new IslandType("hard-type", "Hard Type",
                    java.util.Set.of("hard"), "", List.of(), "PLAINS", java.util.Set.of());
            when(fixture.typeRegistry.difficulties()).thenReturn(List.of(fixture.normal, empty, hard));
            when(fixture.typeRegistry.typesFor("empty")).thenReturn(List.of());
            when(fixture.typeRegistry.typesFor("hard")).thenReturn(List.of(hardType));

            IslandCreationService creation = mock(IslandCreationService.class);
            when(creation.canUse(fixture.admin)).thenReturn(true);
            when(creation.begin(any(), eq("default"), eq("normal"), any()))
                    .thenReturn(IslandCreationService.CreationResult.failed("expected validation"));

            new IslandGUI(fixture.typeRegistry, creation).open(fixture.admin);
            assertEquals(List.of("Normal", "Hard"), DialogTestHelper.buttons(
                    DialogTestHelper.requireDialog(fixture.admin)).stream()
                    .map(DialogTestHelper::label).toList());

            clickNamed(fixture, "Normal");
            assertEquals(List.of("Default", "Back"), DialogTestHelper.buttons(
                    DialogTestHelper.requireDialog(fixture.admin)).stream()
                    .map(DialogTestHelper::label).toList());
            clickNamed(fixture, "Default");
            verify(creation).begin(any(), eq("default"), eq("normal"), any());

            new IslandGUI(fixture.typeRegistry, creation).open(fixture.admin);
            clickNamed(fixture, "Hard");
            assertEquals(List.of("Hard Type", "Back"), DialogTestHelper.buttons(
                    DialogTestHelper.requireDialog(fixture.admin)).stream()
                    .map(DialogTestHelper::label).toList());
        }
    }

    @Test
    void islandCreationDialogOffersBackWhenTypesDisappearAfterDifficultySelection() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            when(fixture.typeRegistry.typesFor("normal"))
                    .thenReturn(List.of(fixture.defaultType), List.of(), List.of(fixture.defaultType));
            IslandCreationService creation = mock(IslandCreationService.class);

            new IslandGUI(fixture.typeRegistry, creation).open(fixture.admin);
            clickNamed(fixture, "Normal");

            assertEquals(List.of("No choices", "Back"), DialogTestHelper.buttons(
                    DialogTestHelper.requireDialog(fixture.admin)).stream()
                    .map(DialogTestHelper::label).toList());
            clickNamed(fixture, "Back");
            assertTrue(DialogTestHelper.buttons(DialogTestHelper.requireDialog(fixture.admin)).stream()
                    .map(DialogTestHelper::label).toList().contains("Normal"));
        }
    }

    private static void clickNamed(SkyblockDialogFixture fixture, String label) {
        var dialog = DialogTestHelper.requireDialog(fixture.admin);
        var button = DialogTestHelper.buttons(dialog).stream()
                .filter(candidate -> label.equals(DialogTestHelper.label(candidate)))
                .findFirst().orElseThrow();
        DialogTestHelper.click(fixture.admin, button);
        DialogTestHelper.pump(fixture.server);
    }

    @Test
    void everySuccessfulRegistryMutationCarriesItsExactWriteFuture() {
        CompletableFuture<Void> typeWrite = new CompletableFuture<>();
        TypeRegistry types = mock(TypeRegistry.class);
        when(types.type("custom")).thenReturn(Optional.empty());
        when(types.saveAsync()).thenReturn(typeWrite);
        TypeAdminService.EditResult typeResult = new TypeAdminService(types, mock(IslandManager.class))
                .createType(new IslandType("custom", "Custom", java.util.Set.of(), "", List.of(), "PLAINS", java.util.Set.of()));
        assertTrue(typeResult.success());
        assertTrue(typeResult.persistence() == typeWrite);
        assertFalse(typeResult.persistence().isDone());

        CompletableFuture<Void> challengeWrite = new CompletableFuture<>();
        ChallengeRegistry challenges = mock(ChallengeRegistry.class);
        when(challenges.challenge("custom")).thenReturn(Optional.empty());
        when(challenges.saveAsync()).thenReturn(challengeWrite);
        ChallengeAdminService.EditResult challengeResult = new ChallengeAdminService(challenges).create(
                new me.beeliebub.tweaks.skyblock.challenge.Challenge("custom", "general", "Custom", "",
                        List.of(), List.of(), List.of(), List.of(), java.util.Set.of()));
        assertTrue(challengeResult.success());
        assertTrue(challengeResult.persistence() == challengeWrite);

        CompletableFuture<Void> generatorWrite = new CompletableFuture<>();
        GeneratorRegistry generators = mock(GeneratorRegistry.class);
        when(generators.tier("custom")).thenReturn(Optional.empty());
        when(generators.saveAsync()).thenReturn(generatorWrite);
        GeneratorAdminService.EditResult generatorResult = new GeneratorAdminService(generators,
                mock(IslandManager.class)).create("custom", "Custom");
        assertTrue(generatorResult.success());
        assertTrue(generatorResult.persistence() == generatorWrite);

        CompletableFuture<Void> shopWrite = new CompletableFuture<>();
        ShopCatalog shop = mock(ShopCatalog.class);
        when(shop.saveAsync()).thenReturn(shopWrite);
        ShopAdminService.EditResult shopResult = new ShopAdminService(shop).set(Material.STONE, "general", 1, 0.5);
        assertTrue(shopResult.success());
        assertTrue(shopResult.persistence() == shopWrite);
    }

    @Test
    void failedYamlWriteRemainsVisibleOnTheOperationFuture(@TempDir Path directory) throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        File dataFolder = directory.resolve("data-folder").toFile();
        assertTrue(dataFolder.createNewFile());
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        TypeRegistry registry = new TypeRegistry(plugin);

        try {
            TypeAdminService.EditResult result = new TypeAdminService(registry, mock(IslandManager.class))
                    .createDifficulty(new IslandDifficulty("hard", "Hard", 1));
            result.persistence().join();
            assertTrue(false, "the forced YAML failure must complete the operation exceptionally");
        } catch (CompletionException expected) {
            assertNotNull(expected.getCause());
        }
    }

    @Test
    void reportWaitsForDurabilityBeforeMessagingOrAuditing() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            CompletableFuture<Void> write = new CompletableFuture<>();
            fixture.context.report(fixture.admin, new AdminScreenContext.Outcome(true, "saved", "change", write), "difficulty");
            assertNull(fixture.admin.nextMessage());

            write.complete(null);
            DialogTestHelper.pump(fixture.server);
            assertTrue(fixture.admin.nextMessage().toString().contains("Saved"));
        }
    }

    @Test
    void persistenceFailureProducesFeedbackWithoutSuccess() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            CompletableFuture<Void> write = new CompletableFuture<>();
            fixture.context.report(fixture.admin, new AdminScreenContext.Outcome(true, "saved", "change", write), "difficulty");
            write.completeExceptionally(new IllegalStateException("yaml write failed"));
            DialogTestHelper.pump(fixture.server);
            String message = fixture.admin.nextMessage().toString();
            assertTrue(message.contains("yaml write failed"));
            assertFalse(message.contains("Saved"));
        }
    }

    @Test
    void forcedYamlFailureIsReportedToTheAdmin(@TempDir Path directory) throws IOException {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            JavaPlugin plugin = mock(JavaPlugin.class);
            File dataFolder = directory.resolve("blocked-data-folder").toFile();
            assertTrue(dataFolder.createNewFile());
            when(plugin.getDataFolder()).thenReturn(dataFolder);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            TypeRegistry registry = new TypeRegistry(plugin);
            TypeAdminService.EditResult result = new TypeAdminService(registry, mock(IslandManager.class))
                    .createDifficulty(new IslandDifficulty("hard", "Hard", 1));

            fixture.context.report(fixture.admin, result, "difficulty");
            try {
                result.persistence().join();
            } catch (CompletionException expected) {
                // The player-facing assertion below is the contract under test.
            }
            DialogTestHelper.pump(fixture.server);
            String message = String.valueOf(fixture.admin.nextComponentMessage());
            assertTrue(message.toLowerCase(Locale.ROOT).contains("could not be saved"), message);
            assertFalse(message.contains("Saved"), message);
        }
    }

    @Test
    void guardFailureNamesWorldPermissionAndOfflineState() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            PlayerMock wrongWorld = fixture.wrongWorldPlayer();
            fixture.context.reportGuardFailure(wrongWorld);
            assertTrue(wrongWorld.nextMessage().toString().toLowerCase(Locale.ROOT).contains("skyblock"));

            PlayerMock noPermission = fixture.noPermissionPlayer();
            fixture.context.reportGuardFailure(noPermission);
            assertTrue(noPermission.nextMessage().toString().toLowerCase(Locale.ROOT).contains("permission"));

            org.bukkit.entity.Player offline = mock(org.bukkit.entity.Player.class);
            when(offline.isOnline()).thenReturn(false);
            fixture.context.reportGuardFailure(offline);
            org.mockito.Mockito.verify(offline).sendMessage(me.beeliebub.tweaks.core.Messages.SKYBLOCK.adminOffline());
        }
    }

    @Test
    void failedGeneratorCreationDoesNotRunTheSuccessContinuation() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            AtomicReference<org.bukkit.entity.Player> navigated = new AtomicReference<>();
            fixture.context.report(fixture.admin,
                    new GeneratorAdminService.EditResult(false, "generator tier already exists"),
                    "generator tier", navigated::set);
            DialogTestHelper.pump(fixture.server);
            assertNull(navigated.get());
            assertTrue(String.valueOf(fixture.admin.nextComponentMessage()).contains("already exists"));
        }
    }

    @Test
    void difficultyValidationReasonsRemainDistinct() {
        IllegalArgumentException badId = assertThrows(IllegalArgumentException.class,
                () -> new IslandDifficulty("bad id", "Bad", 0, 1));
        IllegalArgumentException badMultiplier = assertThrows(IllegalArgumentException.class,
                () -> new IslandDifficulty("hard", "Hard", 0, 0));
        assertTrue(badId.getMessage().toLowerCase(Locale.ROOT).contains("difficulty id"));
        assertTrue(badMultiplier.getMessage().toLowerCase(Locale.ROOT).contains("multiplier"));
    }

    @Test
    void shopDeletionReportsAfterDurabilityAndAuditsTheDelete() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            CompletableFuture<Void> write = new CompletableFuture<>();
            List<LogRecord> records = new ArrayList<>();
            Handler handler = new Handler() {
                @Override public void publish(LogRecord record) { records.add(record); }
                @Override public void flush() { }
                @Override public void close() { }
            };
            fixture.plugin.getLogger().addHandler(handler);
            try {
                fixture.context.report(fixture.admin,
                        new ShopAdminService.DeleteResult(true, "deleted", write), "shop entry");
                assertNull(fixture.admin.nextMessage());
                write.complete(null);
                DialogTestHelper.pump(fixture.server);
                assertTrue(String.valueOf(fixture.admin.nextComponentMessage()).contains("Saved"));
                assertTrue(records.stream().anyMatch(record -> record.getParameters() != null
                        && Arrays.stream(record.getParameters()).map(String::valueOf)
                        .anyMatch(value -> value.equals("delete"))));
            } finally {
                fixture.plugin.getLogger().removeHandler(handler);
            }
        }
    }

    @Test
    void wizardModeStillReportsAndAdvancesAfterDurability() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            SkyblockSetupStatus.Check incomplete = new SkyblockSetupStatus.Check(
                    "types", "Types", SkyblockSetupStatus.State.INCOMPLETE, "needs types", "types");
            SkyblockSetupStatus.Check satisfied = new SkyblockSetupStatus.Check(
                    "types", "Types", SkyblockSetupStatus.State.SATISFIED, "ready", "types");
            when(fixture.checker.check()).thenReturn(new SkyblockSetupStatus(List.of(incomplete)),
                    new SkyblockSetupStatus(List.of(satisfied)));
            DialogTestHelper.invoke(fixture.screens, "runSetup", fixture.admin);
            fixture.context.report(fixture.admin,
                    new AdminScreenContext.Outcome(true, "saved", "change"), "difficulty");
            DialogTestHelper.pump(fixture.server);
            DialogTestHelper.pump(fixture.server);
            assertTrue(String.valueOf(fixture.admin.nextComponentMessage()).contains("Saved"));
            assertNotNull(DialogTestHelper.openDialog(fixture.admin));
        }
    }

    @Test
    void inputSaveIsUnlimitedButAppliesOnlyOnce() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            AtomicInteger saves = new AtomicInteger();
            fixture.context.input(fixture.admin, "Double Save", List.of("value"), List.of(),
                    (player, values) -> saves.incrementAndGet(), ignored -> { });
            var dialog = DialogTestHelper.requireDialog(fixture.admin);
            var save = DialogTestHelper.buttons(dialog).stream()
                    .filter(button -> "Save".equals(DialogTestHelper.label(button))).findFirst().orElseThrow();
            DialogTestHelper.click(fixture.admin, save, Map.of("value", "first"));
            DialogTestHelper.click(fixture.admin, save, Map.of("value", "second"));
            assertEquals(1, saves.get());
            assertEquals(me.beeliebub.tweaks.core.Messages.SKYBLOCK.adminAlreadySubmitted(),
                    fixture.admin.nextComponentMessage());
        }
    }

    @Test
    void defaultSelectionReturnsFalseWhenConfigWriteFails(@TempDir Path directory) throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration configuration = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(configuration);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(configuration.getString(anyString())).thenReturn(null);
        when(configuration.getInt(anyString(), any(Integer.class))).thenReturn(0);
        org.mockito.Mockito.doThrow(new IOException("config write failed"))
                .when(configuration).save(any(File.class));
        assertFalse(new SkyblockConfig(plugin).setDefaultSelection("default", "normal"));
    }

    @Test
    void registryLookupsDoNotDependOnDefaultLocale() {
        TypeRegistry registry = new TypeRegistry(Logger.getAnonymousLogger());
        registry.registerDifficulty(new IslandDifficulty("normal", "Normal", 0));
        registry.registerType(new IslandType("default", "Default", java.util.Set.of("normal"), ""));
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertTrue(registry.difficulty("NORMAL").isPresent());
            assertTrue(registry.type("DEFAULT").isPresent());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void secondCreateGetsAValidationOutcomeInsteadOfApplyingTwice() {
        TypeRegistry types = mock(TypeRegistry.class);
        IslandType value = new IslandType("custom", "Custom", java.util.Set.of(), "");
        when(types.type("custom")).thenReturn(Optional.of(value));
        TypeAdminService.EditResult result = new TypeAdminService(types, mock(IslandManager.class)).createType(value);
        assertFalse(result.success());
        assertEquals("type already exists", result.message());
    }
}
