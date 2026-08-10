package me.beeliebub.tweaks.tests.skyblock;

import me.beeliebub.tweaks.skyblock.SkyblockValidation;
import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRequirement;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeReward;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import me.beeliebub.tweaks.skyblock.tracking.TrackKey;
import me.beeliebub.tweaks.skyblock.type.IslandDifficulty;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkyblockValidationTest {

    @Test
    void validChallengeAcceptsEveryRequirementAndRewardVariant() {
        Challenge challenge = challenge("all", List.of(
                new ChallengeRequirement.Tracked(new TrackKey(TrackCategory.COLLECT, "stone"), 4),
                new ChallengeRequirement.Tracked(new TrackKey(TrackCategory.KILL, "zombie"), 2),
                new ChallengeRequirement.Possession(Material.DIAMOND, 1)),
                List.of(), List.of(), List.of(
                        new ChallengeReward.Items(List.of(item(Material.DIAMOND_SWORD, 1))),
                        new ChallengeReward.SizeUpgrade(IslandSize.MEDIUM),
                        new ChallengeReward.GeneratorUnlock("ore-tier"),
                        new ChallengeReward.Money(25.5d)),
                Set.of("starter"));
        IslandType type = new IslandType("starter", "Starter", Set.of("normal"), "starter-template");
        GeneratorTier tier = new GeneratorTier("ore-tier", "Ore", Map.of(Material.IRON_ORE, 1.0d));

        assertEquals(List.of(), SkyblockValidation.challenge(
                challenge, List.of(challenge), List.of(type), List.of(tier)));
    }

    @Test
    void challengeReportsMissingRequirementsReferencesAndGeneratorTiers() {
        Challenge challenge = challenge("main", List.of(),
                List.of("missing-direct"),
                List.of(group("missing-group")),
                List.of(new ChallengeReward.GeneratorUnlock("missing-tier")), Set.of());

        assertEquals(List.of(
                        "has no requirements",
                        "references missing prerequisite(s): missing-direct, missing-group",
                        "references missing generator tier: missing-tier"),
                SkyblockValidation.challenge(challenge, List.of(), List.of(), List.of()));
    }

    @Test
    void challengeReportsUnrecordableMaterialAndEntityTrackingKeys() {
        Challenge challenge = challenge("tracking", List.of(
                new ChallengeRequirement.Tracked(
                        new TrackKey(TrackCategory.COLLECT, "NOT_A_MATERIAL"), 1),
                new ChallengeRequirement.Possession(Material.STONE, 1),
                new ChallengeRequirement.Tracked(
                        new TrackKey(TrackCategory.KILL, "NOT_AN_ENTITY"), 1)),
                List.of(), List.of(), List.of(), Set.of());

        assertEquals(List.of(
                        "tracked key cannot be recorded: collect:NOT_A_MATERIAL",
                        "tracked key cannot be recorded: kill:NOT_AN_ENTITY"),
                SkyblockValidation.challenge(challenge, null, null, null));
    }

    @Test
    void challengeAcceptsKnownPrerequisitesAndSharedAcyclicPaths() {
        Challenge child = challenge("child", List.of(new ChallengeRequirement.Possession(Material.STONE, 1)),
                List.of(), List.of(), List.of(), Set.of());
        Challenge start = challenge("start", List.of(new ChallengeRequirement.Possession(Material.STONE, 1)),
                List.of("child"), List.of(group("child")), List.of(), Set.of());

        assertEquals(List.of(), SkyblockValidation.challenge(start, List.of(child), List.of(), List.of()));
    }

    @Test
    void challengeDetectsDirectPrerequisiteCycles() {
        Challenge first = challenge("first", List.of(new ChallengeRequirement.Possession(Material.STONE, 1)),
                List.of("second"), List.of(), List.of(), Set.of());
        Challenge second = challenge("second", List.of(new ChallengeRequirement.Possession(Material.STONE, 1)),
                List.of("first"), List.of(), List.of(), Set.of());

        assertEquals(List.of("contains a prerequisite cycle"),
                SkyblockValidation.challenge(first, List.of(second), List.of(), List.of()));
    }

    @Test
    void challengeDetectsCyclesThroughAnyOfGroups() {
        Challenge first = challenge("first", List.of(new ChallengeRequirement.Possession(Material.STONE, 1)),
                List.of(), List.of(group("second")), List.of(), Set.of());
        Challenge second = challenge("second", List.of(new ChallengeRequirement.Possession(Material.STONE, 1)),
                List.of("first"), List.of(), List.of(), Set.of());

        assertEquals(List.of("contains a prerequisite cycle"),
                SkyblockValidation.challenge(first, List.of(second), List.of(), List.of()));
    }

    @Test
    void challengeTypeGatingAcceptsUnrestrictedAndAllowedTypes() {
        Challenge unrestricted = challenge("unrestricted",
                List.of(new ChallengeRequirement.Possession(Material.STONE, 1)),
                List.of(), List.of(), List.of(), Set.of());
        Challenge emptyAllowList = challenge("empty-allow-list",
                List.of(new ChallengeRequirement.Possession(Material.STONE, 1)),
                List.of(), List.of(), List.of(), Set.of("starter"));
        Challenge explicitlyAllowed = challenge("explicitly-allowed",
                List.of(new ChallengeRequirement.Possession(Material.STONE, 1)),
                List.of(), List.of(), List.of(), Set.of("restricted"));

        IslandType open = new IslandType("starter", "Starter", Set.of("normal"), "", List.of(),
                "PLAINS", Set.of());
        IslandType restricted = new IslandType("restricted", "Restricted", Set.of("normal"), "", List.of(),
                "PLAINS", Set.of("explicitly-allowed"));

        assertEquals(List.of(), SkyblockValidation.challenge(unrestricted, null, null, null));
        assertEquals(List.of(), SkyblockValidation.challenge(emptyAllowList, List.of(), List.of(open), List.of()));
        assertEquals(List.of(), SkyblockValidation.challenge(
                explicitlyAllowed, List.of(), List.of(restricted), List.of()));
    }

    @Test
    void challengeTypeGatingReportsEmptyUnknownAndExcludedTypeSets() {
        Challenge challenge = challenge("gated", List.of(new ChallengeRequirement.Possession(Material.STONE, 1)),
                List.of(), List.of(), List.of(), Set.of("starter"));
        IslandType excluded = new IslandType("starter", "Starter", Set.of("normal"), "", List.of(),
                "PLAINS", Set.of("other-challenge"));
        IslandType different = new IslandType("different", "Different", Set.of("normal"), "");

        assertEquals(List.of("type gating excludes every configured island type"),
                SkyblockValidation.challenge(challenge, List.of(), List.of(), List.of()));
        assertEquals(List.of("type gating excludes every configured island type"),
                SkyblockValidation.challenge(challenge, List.of(), null, List.of()));
        assertEquals(List.of("type gating excludes every configured island type"),
                SkyblockValidation.challenge(challenge, List.of(), List.of(excluded), List.of()));
        assertEquals(List.of("type gating excludes every configured island type"),
                SkyblockValidation.challenge(challenge, List.of(), List.of(different), List.of()));
    }

    @Test
    void validIslandTypePassesDifficultyTemplateAndKitChecks() {
        IslandType type = new IslandType("starter", "Starter", orderedIds("normal", "hard"), "starter-template",
                List.of(new IslandType.KitItem(item(Material.DIAMOND_PICKAXE, 2))), "PLAINS", Set.of());

        assertEquals(List.of(), SkyblockValidation.islandType(type,
                List.of(new IslandDifficulty("normal", "Normal", 0),
                        new IslandDifficulty("hard", "Hard", 1)),
                Set.of("starter-template")));
    }

    @Test
    void islandTypeReportsMissingDifficultyAndTemplateReferences() {
        IslandType type = new IslandType("broken", "Broken", orderedIds("normal", "missing"),
                "missing-template");

        assertEquals(List.of(
                        "references missing difficulty(s): missing",
                        "references missing template: missing-template"),
                SkyblockValidation.islandType(type,
                        List.of(new IslandDifficulty("normal", "Normal", 0)),
                        Set.of("other-template")));
    }

    @Test
    void islandTypeReportsMissingOptionsAndVoidIsland() {
        IslandType type = new IslandType("void", "Void", Set.of(), "");

        assertEquals(List.of(
                        "has no difficulty options",
                        "produces a void island because it has no template or kit"),
                SkyblockValidation.islandType(type, null, null));
    }

    @Test
    void islandTypeReportsKitEntriesThatCannotBeSerialized() {
        IslandType.KitItem malformed = mock(IslandType.KitItem.class);
        when(malformed.itemStack()).thenThrow(new IllegalStateException("not serializable"));
        IslandType type = new IslandType("kit", "Kit", Set.of("normal"), "",
                List.of(malformed));

        assertEquals(List.of("kit entry 0 cannot be serialized"),
                SkyblockValidation.islandType(type,
                        List.of(new IslandDifficulty("normal", "Normal", 0)), Set.of()));
    }

    @Test
    void validGeneratorTierPassesOutputValidation() {
        GeneratorTier tier = new GeneratorTier("ore", "Ore",
                Map.of(Material.COBBLESTONE, 3.0d, Material.IRON_ORE, 1.0d));

        assertEquals(List.of(), SkyblockValidation.generatorTier(tier));
    }

    @Test
    void generatorTierReportsEmptyTablesAndZeroTotals() {
        GeneratorTier tier = mock(GeneratorTier.class);
        when(tier.outputs()).thenReturn(Map.of());

        assertEquals(List.of(
                        "has an empty output table",
                        "has a non-finite or non-positive total weight"),
                SkyblockValidation.generatorTier(tier));
    }

    @Test
    void generatorTierReportsNullNonPositiveAndNonFiniteOutputWeights() {
        Map<Material, Double> outputs = new LinkedHashMap<>();
        outputs.put(Material.STONE, null);
        outputs.put(Material.DIRT, 0.0d);
        outputs.put(Material.IRON_ORE, Double.NaN);
        outputs.put(Material.GOLD_ORE, 1.0d);
        GeneratorTier tier = mock(GeneratorTier.class);
        when(tier.outputs()).thenReturn(outputs);

        assertEquals(List.of(
                        "has a non-finite or non-positive output weight for STONE",
                        "has a non-finite or non-positive output weight for DIRT",
                        "has a non-finite or non-positive output weight for IRON_ORE"),
                SkyblockValidation.generatorTier(tier));
    }

    @Test
    void generatorTierReportsAnOverflowedTotalWeight() {
        Map<Material, Double> outputs = new LinkedHashMap<>();
        outputs.put(Material.STONE, Double.MAX_VALUE);
        outputs.put(Material.DIRT, Double.MAX_VALUE);
        GeneratorTier tier = mock(GeneratorTier.class);
        when(tier.outputs()).thenReturn(outputs);

        assertEquals(List.of("has a non-finite or non-positive total weight"),
                SkyblockValidation.generatorTier(tier));
    }

    @Test
    void shopEntryRequiresAtLeastOneEnabledDirection() {
        assertEquals(List.of(), SkyblockValidation.shopEntry(
                new ShopCatalog.Entry(Material.STONE, "blocks", 0.0d, -1.0d)));
        assertEquals(List.of(), SkyblockValidation.shopEntry(
                new ShopCatalog.Entry(Material.STONE, "blocks", -1.0d, 0.0d)));
        assertEquals(List.of("has both buy and sell directions disabled"), SkyblockValidation.shopEntry(
                new ShopCatalog.Entry(Material.STONE, "blocks", -1.0d, -1.0d)));
    }

    @Test
    void validatorsRejectNullDefinitions() {
        assertThrows(NullPointerException.class,
                () -> SkyblockValidation.challenge(null, List.of(), List.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> SkyblockValidation.islandType(null, List.of(), Set.of()));
        assertThrows(NullPointerException.class, () -> SkyblockValidation.generatorTier(null));
        assertThrows(NullPointerException.class, () -> SkyblockValidation.shopEntry(null));
    }

    @Test
    void structuredValidatorsExposeStableProblemCodes() {
        Challenge challenge = challenge("empty", List.of(), List.of(), List.of(), List.of(), Set.of());
        IslandType type = new IslandType("void", "Void", Set.of(), "");
        GeneratorTier tier = mock(GeneratorTier.class);
        when(tier.outputs()).thenReturn(Map.of());

        assertEquals(List.of(new SkyblockValidation.Problem("NO_REQUIREMENTS", "has no requirements")),
                SkyblockValidation.validateChallenge(challenge, null, null, null));
        assertEquals(List.of(
                        new SkyblockValidation.Problem("NO_DIFFICULTIES", "has no difficulty options"),
                        new SkyblockValidation.Problem("VOID_ISLAND",
                                "produces a void island because it has no template or kit")),
                SkyblockValidation.validateIslandType(type, null, null));
        assertEquals(List.of(
                        new SkyblockValidation.Problem("EMPTY_OUTPUTS", "has an empty output table"),
                        new SkyblockValidation.Problem("INVALID_TOTAL_WEIGHT",
                                "has a non-finite or non-positive total weight")),
                SkyblockValidation.validateGeneratorTier(tier));
        assertEquals(List.of(new SkyblockValidation.Problem(
                        "NO_ENABLED_DIRECTION", "has both buy and sell directions disabled")),
                SkyblockValidation.validateShopEntry(
                        new ShopCatalog.Entry(Material.STONE, "blocks", -1.0d, -1.0d)));
    }

    private static Challenge challenge(String id, List<ChallengeRequirement> requirements,
                                       List<String> prerequisites,
                                       List<Challenge.PrerequisiteGroup> groups,
                                       List<ChallengeReward> rewards, Set<String> typeIds) {
        return new Challenge(id, "general", id, "", requirements, prerequisites, groups, rewards, typeIds);
    }

    private static Challenge.PrerequisiteGroup group(String... ids) {
        return new Challenge.PrerequisiteGroup(1, new LinkedHashSet<>(List.of(ids)));
    }

    private static Set<String> orderedIds(String... ids) {
        return new LinkedHashSet<>(List.of(ids));
    }

    private static ItemStack item(Material material, int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.clone()).thenReturn(item);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(amount);
        when(item.serialize()).thenReturn(Map.of("type", material.name(), "amount", amount));
        return item;
    }
}
