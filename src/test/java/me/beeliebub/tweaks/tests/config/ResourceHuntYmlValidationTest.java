package me.beeliebub.tweaks.tests.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ResourceHuntYmlValidationTest {

    private static Map<String, Object> root;

    private static final Pattern AMOUNT_AND_MULT = Pattern.compile("^\\d+(:\\d+(\\.\\d+)?)?$");

    @BeforeAll
    static void load() throws IOException {
        try (var in = Files.newInputStream(Path.of("src/main/resources/resource_hunt.yml"))) {
            root = new Yaml().load(in);
        }
    }

    @Test
    void overworldAndNetherSectionsExist() {
        assertNotNull(root.get("overworld"), "overworld section required");
        assertNotNull(root.get("nether"), "nether section required");
        assertInstanceOf(Map.class, root.get("overworld"));
        assertInstanceOf(Map.class, root.get("nether"));
    }

    @Test
    void overworldEntriesAreWellFormed() {
        validateSection("overworld");
    }

    @Test
    void netherEntriesAreWellFormed() {
        validateSection("nether");
    }

    @Test
    void allEntryMaterialsLookLikeBukkitConstants() {
        Pattern materialName = Pattern.compile("[a-z][a-z0-9_]*");
        for (String section : new String[]{"overworld", "nether"}) {
            @SuppressWarnings("unchecked")
            Map<String, Object> entries = (Map<String, Object>) root.get(section);
            for (String mat : entries.keySet()) {
                assertTrue(materialName.matcher(mat).matches(),
                        section + ": material '" + mat + "' is not a valid lower_snake_case name");
            }
        }
    }

    @Test
    void multiplierIsNeverBelowOne() {
        for (String section : new String[]{"overworld", "nether"}) {
            @SuppressWarnings("unchecked")
            Map<String, Object> entries = (Map<String, Object>) root.get(section);
            for (var e : entries.entrySet()) {
                String value = String.valueOf(e.getValue());
                if (!value.contains(":")) continue;
                String[] parts = value.split(":");
                double multiplier = Double.parseDouble(parts[1]);
                assertTrue(multiplier >= 1.0,
                        section + "/" + e.getKey() + ": multiplier " + multiplier + " < 1.0");
            }
        }
    }

    private void validateSection(String name) {
        @SuppressWarnings("unchecked")
        Map<String, Object> entries = (Map<String, Object>) root.get(name);
        assertNotNull(entries);
        assertFalse(entries.isEmpty(), name + " must have at least one entry");

        for (var e : entries.entrySet()) {
            String value = String.valueOf(e.getValue());
            assertTrue(AMOUNT_AND_MULT.matcher(value).matches(),
                    name + "/" + e.getKey() + ": '" + value + "' must match '<int>' or '<int>:<float>'");
            int amount = Integer.parseInt(value.contains(":") ? value.substring(0, value.indexOf(':')) : value);
            assertTrue(amount > 0, name + "/" + e.getKey() + ": amount must be > 0");
        }
    }
}
