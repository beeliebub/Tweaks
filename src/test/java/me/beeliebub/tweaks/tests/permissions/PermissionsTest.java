package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.Permissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
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
    }
}
