package me.beeliebub.tweaks.tests.skyblock.challenge;

import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeCategory;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRegistry;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRequirement;
import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import me.beeliebub.tweaks.skyblock.tracking.TrackKey;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChallengeRegistryTest {
    @Test
    void malformedReferencesAndCyclesAreDisabled() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("challenges.good.name", "Good");
        yaml.set("challenges.good.requirements", List.of(Map.of("type", "tracked", "key", "collect:STONE", "amount", 1)));
        yaml.set("challenges.unknown.prerequisites", List.of("missing"));
        yaml.set("challenges.cycle-a.prerequisites", List.of("cycle-b"));
        yaml.set("challenges.cycle-b.prerequisites", List.of("cycle-a"));

        ChallengeRegistry registry = new ChallengeRegistry();
        registry.load(yaml);

        assertTrue(registry.challenge("good").isPresent());
        assertFalse(registry.challenge("unknown").isPresent());
        assertFalse(registry.challenge("cycle-a").isPresent());
        assertFalse(registry.challenge("cycle-b").isPresent());
    }

    @Test
    void trackedRequirementsPublishMatchingActiveAndTaintSets() {
        ChallengeRegistry registry = new ChallengeRegistry();
        TrackKey key = new TrackKey(TrackCategory.COLLECT, Material.COBBLESTONE.name());
        registry.register(new Challenge("stone", "general", "Stone", "", 
                List.of(new ChallengeRequirement.Tracked(key, 4)), List.of(), List.of(), List.of(), Set.of()));

        assertTrue(registry.activeTrackKeys().contains(key));
        assertTrue(registry.taintMaterials().contains(Material.COBBLESTONE));
        assertTrue(registry.taintMaterials().contains(Material.STONE));
        assertTrue(registry.taintMaterials().contains(Material.COBBLESTONE));
    }

    @Test
    void adminEditsRejectUnknownCategoriesReferencesAndCyclesAtomically() {
        ChallengeRegistry registry = new ChallengeRegistry();
        Challenge first = challenge("first", "general", List.of());
        registry.register(first);
        registry.register(challenge("second", "general", List.of("first")));

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(challenge("wrong-category", "missing", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(challenge("unknown-reference", "general", List.of("missing"))));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(challenge("unknown-group", "general", List.of(),
                        List.of(new Challenge.PrerequisiteGroup(1, Set.of("missing"))))));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(challenge("first", "general", List.of("second"))));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(challenge("first", "general", List.of(),
                        List.of(new Challenge.PrerequisiteGroup(1, Set.of("second"))))));

        assertEquals(first, registry.challenge("first").orElseThrow());
        assertFalse(registry.challenge("wrong-category").isPresent());
        assertFalse(registry.challenge("unknown-reference").isPresent());
        assertFalse(registry.challenge("unknown-group").isPresent());
    }

    @Test
    void loadSkipsUnknownCategoriesAndChallengesThatDependOnThem() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("challenges.good.name", "Good");
        yaml.set("challenges.unknown-category.category", "missing");
        yaml.set("challenges.dependent.prerequisites", List.of("unknown-category"));

        ChallengeRegistry registry = new ChallengeRegistry();
        registry.load(yaml);

        assertTrue(registry.challenge("good").isPresent());
        assertFalse(registry.challenge("unknown-category").isPresent());
        assertFalse(registry.challenge("dependent").isPresent());
    }

    @Test
    void challengeDeletionRefusesReferencedDefinitions() {
        ChallengeRegistry registry = new ChallengeRegistry();
        registry.register(challenge("first", "general", List.of()));
        registry.register(challenge("second", "general", List.of("first")));

        ChallengeRegistry.DeleteResult result = registry.deleteChallenge("first");

        assertFalse(result.deleted());
        assertEquals(1, result.references());
        assertTrue(registry.challenge("first").isPresent());
    }

    @Test
    void categoryLookupSupportsValidatedAdminReassignment() {
        ChallengeRegistry registry = new ChallengeRegistry();
        registry.registerCategory(new ChallengeCategory("advanced", "Advanced", 5));

        assertTrue(registry.category("ADVANCED").isPresent());
        assertFalse(registry.category("missing").isPresent());
    }

    private static Challenge challenge(String id, String category, List<String> prerequisites) {
        return challenge(id, category, prerequisites, List.of());
    }

    private static Challenge challenge(String id, String category, List<String> prerequisites,
                                       List<Challenge.PrerequisiteGroup> groups) {
        return new Challenge(id, category, id, "", List.of(), prerequisites, groups, List.of(), Set.of());
    }
}
