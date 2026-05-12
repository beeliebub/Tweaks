package me.beeliebub.tweaks.tests.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigYmlValidationTest {

    private static Map<String, Object> root;

    @BeforeAll
    static void load() throws IOException {
        try (var in = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            root = new Yaml().load(in);
        }
    }

    @Test
    void maxHomesIsPositiveInt() {
        Object value = root.get("max-homes");
        assertInstanceOf(Integer.class, value, "max-homes must be an int");
        assertTrue((Integer) value > 0, "max-homes must be positive");
    }

    @Test
    void eggCollectorDropChanceInRange() {
        Object value = root.get("egg-collector-drop-chance");
        assertNotNull(value);
        double d = ((Number) value).doubleValue();
        assertTrue(d >= 0.0 && d <= 100.0, "egg-collector-drop-chance must be in [0, 100], was " + d);
    }

    @Test
    void enchantmentNamespaceKeysFollowNamespacedFormat() {
        String[] keys = {
                "telekinesis", "smelter", "lumberjack", "gem-connoisseur", "tunneller",
                "spawner-pickup", "egg-collector", "replant", "efficacy"
        };
        for (String key : keys) {
            Object value = root.get(key);
            assertNotNull(value, key + " must be defined");
            String s = String.valueOf(value);
            assertTrue(s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"),
                    key + " must be a valid namespaced key, was '" + s + "'");
        }
    }

    @Test
    void worldListsAreLists() {
        for (String key : new String[]{"disabled-end-portal-worlds", "fly-worlds"}) {
            Object value = root.get(key);
            assertInstanceOf(List.class, value, key + " must be a list");
            for (Object world : (List<?>) value) {
                assertInstanceOf(String.class, world, key + " entries must be strings");
                assertFalse(((String) world).isBlank(), key + " contains blank entry");
            }
        }
    }

    @Test
    void gemConnoisseurRatesStructureIsValid() {
        Object ratesObj = root.get("gem-connoisseur-rates");
        assertInstanceOf(Map.class, ratesObj, "gem-connoisseur-rates must be a map");
        @SuppressWarnings("unchecked")
        Map<Object, Object> rates = (Map<Object, Object>) ratesObj;
        assertFalse(rates.isEmpty(), "gem-connoisseur-rates must declare at least one tier");

        for (var tierEntry : rates.entrySet()) {
            assertInstanceOf(Integer.class, tierEntry.getKey(),
                    "gem-connoisseur-rates tier key must be int, was " + tierEntry.getKey());
            assertInstanceOf(Map.class, tierEntry.getValue(),
                    "tier " + tierEntry.getKey() + " must map material->drops");

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Integer>> blockGroups =
                    (Map<String, Map<String, Integer>>) tierEntry.getValue();
            for (var group : blockGroups.entrySet()) {
                for (var drop : group.getValue().entrySet()) {
                    assertTrue(drop.getValue() > 0,
                            "drop chance for " + group.getKey() + "/" + drop.getKey() + " must be > 0");
                }
            }
        }
    }

    @Test
    void flyAdvancementIsNamespaced() {
        Object value = root.get("fly-advancement");
        assertNotNull(value);
        assertTrue(String.valueOf(value).contains(":"),
                "fly-advancement must be a namespaced key");
    }
}
