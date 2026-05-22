package me.beeliebub.tweaks.tests.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ResourceHuntYmlValidationTest {

    private static Map<String, Object> root;

    private static final Pattern AMOUNT_AND_MULT = Pattern.compile("^\\d+(:\\d+(\\.\\d+)?)?$");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");

    // Categories permitted in each world section. enchant/shear are overworld-only; barter is nether-only.
    private static final Set<String> OVERWORLD_CATEGORIES =
            Set.of("collect", "kill", "smelt", "enchant", "shear", "breed", "craft");
    private static final Set<String> NETHER_CATEGORIES =
            Set.of("collect", "kill", "smelt", "breed", "craft", "barter");

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
    void overworldCategoriesAreAllowed() {
        validateAllowedCategories("overworld", OVERWORLD_CATEGORIES);
    }

    @Test
    void netherCategoriesAreAllowed() {
        validateAllowedCategories("nether", NETHER_CATEGORIES);
    }

    @Test
    void overworldEntriesAreWellFormed() {
        validateEntries("overworld");
    }

    @Test
    void netherEntriesAreWellFormed() {
        validateEntries("nether");
    }

    @Test
    void allEntryIdentifiersLookValid() {
        for (String section : new String[]{"overworld", "nether"}) {
            @SuppressWarnings("unchecked")
            Map<String, Object> categories = (Map<String, Object>) root.get(section);
            for (var catEntry : categories.entrySet()) {
                Object catValue = catEntry.getValue();
                if (catValue == null) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> entries = (Map<String, Object>) catValue;
                for (String identifier : entries.keySet()) {
                    assertTrue(IDENTIFIER.matcher(identifier).matches(),
                            section + "/" + catEntry.getKey() + ": identifier '" + identifier
                                    + "' is not a valid lower_snake_case name");
                }
            }
        }
    }

    @Test
    void multiplierIsNeverBelowOne() {
        for (String section : new String[]{"overworld", "nether"}) {
            @SuppressWarnings("unchecked")
            Map<String, Object> categories = (Map<String, Object>) root.get(section);
            for (var catEntry : categories.entrySet()) {
                Object catValue = catEntry.getValue();
                if (catValue == null) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> entries = (Map<String, Object>) catValue;
                for (var e : entries.entrySet()) {
                    String value = String.valueOf(e.getValue());
                    if (!value.contains(":")) continue;
                    String[] parts = value.split(":");
                    double multiplier = Double.parseDouble(parts[1]);
                    assertTrue(multiplier >= 1.0,
                            section + "/" + catEntry.getKey() + "/" + e.getKey()
                                    + ": multiplier " + multiplier + " < 1.0");
                }
            }
        }
    }

    private void validateAllowedCategories(String section, Set<String> allowed) {
        @SuppressWarnings("unchecked")
        Map<String, Object> categories = (Map<String, Object>) root.get(section);
        assertNotNull(categories);
        for (String categoryKey : categories.keySet()) {
            assertTrue(allowed.contains(categoryKey),
                    section + ": category '" + categoryKey + "' is not allowed in this world section");
        }
    }

    private void validateEntries(String section) {
        @SuppressWarnings("unchecked")
        Map<String, Object> categories = (Map<String, Object>) root.get(section);
        assertNotNull(categories);

        for (var catEntry : categories.entrySet()) {
            Object catValue = catEntry.getValue();
            if (catValue == null) continue; // empty category block is allowed
            assertInstanceOf(Map.class, catValue,
                    section + "/" + catEntry.getKey() + " must be a map of entries");

            @SuppressWarnings("unchecked")
            Map<String, Object> entries = (Map<String, Object>) catValue;
            for (var e : entries.entrySet()) {
                String value = String.valueOf(e.getValue());
                assertTrue(AMOUNT_AND_MULT.matcher(value).matches(),
                        section + "/" + catEntry.getKey() + "/" + e.getKey()
                                + ": '" + value + "' must match '<int>' or '<int>:<float>'");
                int amount = Integer.parseInt(value.contains(":")
                        ? value.substring(0, value.indexOf(':'))
                        : value);
                assertTrue(amount > 0,
                        section + "/" + catEntry.getKey() + "/" + e.getKey() + ": amount must be > 0");
            }
        }
    }
}
