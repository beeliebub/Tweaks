package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.Permissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PermissionsTest {

    @Test
    void cannotBeInstantiatedFromOutside() throws Exception {
        Constructor<Permissions> ctor = Permissions.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()), "constructor must be private");
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }

    @Test
    void allConstantsHaveTweaksPrefix() throws IllegalAccessException {
        for (Field f : Permissions.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() != String.class) continue;
            String value = (String) f.get(null);
            assertTrue(value.startsWith("tweaks."),
                    f.getName() + " (" + value + ") should start with 'tweaks.'");
        }
    }

    @Test
    void getAllPermissionsContainsEveryDeclaredConstant() throws IllegalAccessException {
        Set<String> declared = new HashSet<>();
        for (Field f : Permissions.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() != String.class) continue;
            declared.add((String) f.get(null));
        }
        List<String> all = Permissions.getAllPermissions();
        Set<String> allSet = new HashSet<>(all);
        assertEquals(declared, allSet, "getAllPermissions must report every declared constant");
        assertEquals(declared.size(), all.size(), "getAllPermissions must not return duplicates");
    }

    @Test
    void allConstantsAreLowercase() throws IllegalAccessException {
        for (Field f : Permissions.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() != String.class) continue;
            String value = (String) f.get(null);
            assertEquals(value.toLowerCase(), value,
                    f.getName() + " (" + value + ") should be lowercase");
        }
    }

    @Test
    void allConstantsAreUnique() throws IllegalAccessException {
        Set<String> seen = new HashSet<>();
        for (Field f : Permissions.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() != String.class) continue;
            String value = (String) f.get(null);
            assertTrue(seen.add(value), "duplicate permission constant: " + value);
        }
    }

    @Test
    void wellKnownConstantsAreStable() {
        // Spot-check a handful so a careless rename produces a clear test failure.
        assertEquals("tweaks.admin.logs", Permissions.ADMIN_LOGS);
        assertEquals("tweaks.admin.permissions", Permissions.ADMIN_PERMISSIONS);
        assertEquals("tweaks.bypass.homes", Permissions.BYPASS_HOMES);
        assertEquals("tweaks.roulette.setdiscord", Permissions.ROULETTE_SETDISCORD);
        assertEquals("minigames", Permissions.getCategory(Permissions.ROULETTE_SETDISCORD));
    }

    // =========================================================================
    // Category API tests (bead i07y)
    // =========================================================================

    private static final Set<String> EXPECTED_CATEGORY_KEYS = Set.of(
            "tools", "teleport", "minigames", "protection", "ranks", "bypass"
    );

    /**
     * getCategories() must contain exactly the 6 known category keys.
     */
    @Test
    void getCategoriesContainsExactlyTheSixExpectedKeys() {
        LinkedHashMap<String, String> categories = Permissions.getCategories();
        assertEquals(6, categories.size(),
                "getCategories() must have exactly 6 entries");
        for (String expected : EXPECTED_CATEGORY_KEYS) {
            assertTrue(categories.containsKey(expected),
                    "getCategories() must contain key: " + expected);
        }
    }

    /**
     * getCategories() must iterate in the declared order, since that order IS the order buttons
     * appear in the permission GUI's category picker. Regression coverage for
     * CATEGORY_DISPLAY_NAMES having been built from a {@code Map.of(...)} copy, whose iteration
     * order is unspecified and randomized per JVM run — the GUI's category buttons silently
     * reshuffled across server restarts.
     */
    @Test
    void getCategoriesIteratesInDeclaredButtonOrder() {
        assertEquals(
                List.of("tools", "teleport", "minigames", "protection", "ranks", "bypass"),
                List.copyOf(Permissions.getCategories().keySet()),
                "getCategories() must iterate in declared button order");
    }

    /**
     * Every permission from getAllPermissions() must return a non-null category via
     * getCategory(), and that category key must exist in getCategories().
     */
    @Test
    void everPermissionHasAValidCategory() {
        LinkedHashMap<String, String> categories = Permissions.getCategories();
        for (String perm : Permissions.getAllPermissions()) {
            String cat = Permissions.getCategory(perm);
            assertNotNull(cat, "getCategory() must not return null for: " + perm);
            assertTrue(categories.containsKey(cat),
                    "Category '" + cat + "' returned for '" + perm + "' is not a valid category key");
        }
    }

    /**
     * The union of getPermissionsByCategory() over ALL categories must equal
     * getAllPermissions() with no duplicates and no omissions (full partition).
     */
    @Test
    void permissionsByCategoryFormAFullPartition() {
        List<String> all = Permissions.getAllPermissions();
        Set<String> allSet = new HashSet<>(all);

        List<String> fromCategories = new ArrayList<>();
        for (String category : Permissions.getCategories().keySet()) {
            List<String> byCategory = Permissions.getPermissionsByCategory(category);
            // No permission should appear twice in the same category list.
            Set<String> catSet = new HashSet<>(byCategory);
            assertEquals(byCategory.size(), catSet.size(),
                    "Duplicate permission found within category '" + category + "': " + byCategory);
            fromCategories.addAll(byCategory);
        }

        // No permission should appear in more than one category.
        Set<String> union = new HashSet<>(fromCategories);
        assertEquals(fromCategories.size(), union.size(),
                "A permission appears in more than one category (cross-category duplicate)");

        // Union must equal the full set of all permissions.
        assertEquals(allSet, union,
                "The union of getPermissionsByCategory over all categories must equal getAllPermissions()");
    }

    @Test
    void everyPermissionHasANonBlankHoverDescription() {
        for (String permission : Permissions.getAllPermissions()) {
            String description = Permissions.getDescription(permission);
            assertNotNull(description, "Description must not be null for: " + permission);
            assertFalse(description.isBlank(), "Description must not be blank for: " + permission);
        }
    }

    @Test
    void getDescriptionRejectsAnUnknownPermission() {
        assertThrows(IllegalArgumentException.class,
                () -> Permissions.getDescription("tweaks.nonexistent.permission.xyz"));
    }

    /**
     * getCategory() must fall back to "tools" for an unknown permission string.
     */
    @Test
    void getCategoryFallsBackToToolsForUnknownPermission() {
        String unknown = "tweaks.nonexistent.permission.xyz";
        String category = Permissions.getCategory(unknown);
        assertEquals("tools", category,
                "getCategory() must return 'tools' as fallback for unknown permissions");
    }

    /**
     * Every category key in getCategories() has a non-null, non-empty display name.
     */
    @Test
    void everyCategoryHasANonEmptyDisplayName() {
        Permissions.getCategories().forEach((key, displayName) -> {
            assertNotNull(displayName,
                    "Display name for category '" + key + "' must not be null");
            assertFalse(displayName.isBlank(),
                    "Display name for category '" + key + "' must not be blank");
        });
    }
}
