package me.beeliebub.tweaks.tests.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WhackYmlValidationTest {

    private static Map<String, Object> root;

    @BeforeAll
    static void load() throws IOException {
        try (var in = Files.newInputStream(Path.of("src/main/resources/whack.yml"))) {
            root = new Yaml().load(in);
        }
    }

    @Test
    void roundDurationIsPositive() {
        assertInstanceOf(Integer.class, root.get("round-duration"));
        assertTrue((Integer) root.get("round-duration") > 0);
    }

    @Test
    void difficultyInOneToTen() {
        Object value = root.get("difficulty");
        assertInstanceOf(Integer.class, value);
        int d = (Integer) value;
        assertTrue(d >= 1 && d <= 10, "difficulty must be 1..10, was " + d);
    }

    @Test
    void profileNameIsNonEmpty() {
        Object value = root.get("profile-name");
        assertNotNull(value);
        assertFalse(String.valueOf(value).isBlank());
    }

    @Test
    void mannequinLifespanAndIntervalsArePositive() {
        for (String key : new String[]{"mannequin-lifespan", "base-spawn-interval", "min-spawn-interval", "max-alive"}) {
            Object value = root.get(key);
            assertInstanceOf(Integer.class, value, key + " must be int");
            assertTrue((Integer) value > 0, key + " must be > 0");
        }
    }

    @Test
    void minSpawnIntervalNotGreaterThanBase() {
        int base = (Integer) root.get("base-spawn-interval");
        int min = (Integer) root.get("min-spawn-interval");
        assertTrue(min <= base,
                "min-spawn-interval (" + min + ") must be <= base-spawn-interval (" + base + ")");
    }

    @Test
    void booleanTogglesAreBooleans() {
        for (String key : new String[]{"show-actionbar", "announce-results"}) {
            assertInstanceOf(Boolean.class, root.get(key), key + " must be a boolean");
        }
    }

    @Test
    void secondaryAndTernaryProfilesAreWellFormed() {
        for (String key : new String[]{"secondary-profile", "ternary-profile"}) {
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) root.get(key);
            assertNotNull(profile, key + " missing");
            assertFalse(String.valueOf(profile.get("name")).isBlank(),
                    key + ".name must be non-empty");
            assertInstanceOf(Integer.class, profile.get("points"), key + ".points must be int");

            Object chance = profile.get("chance");
            assertNotNull(chance);
            double c = ((Number) chance).doubleValue();
            assertTrue(c >= 0.0 && c <= 100.0,
                    key + ".chance must be in [0, 100], was " + c);
        }
    }

    @Test
    void rewardsBlockHasFirstSecondThird() {
        @SuppressWarnings("unchecked")
        Map<String, Object> rewards = (Map<String, Object>) root.get("rewards");
        assertNotNull(rewards, "rewards block missing");
        for (String slot : new String[]{"first", "second", "third"}) {
            assertTrue(rewards.containsKey(slot), "rewards." + slot + " must exist");
        }
    }
}
