package me.beeliebub.tweaks.tests;

import me.beeliebub.tweaks.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ColorUtilTest {

    private static final Pattern HEX_TOKEN = Pattern.compile("#[0-9A-Fa-f]{6}");

    @Test
    void parseStripsAmpersandLegacyCodes() {
        Component result = ColorUtil.parse("&aHello");
        assertEquals("Hello", PlainTextComponentSerializer.plainText().serialize(result));
        assertEquals(NamedTextColor.GREEN, result.color());
    }

    @Test
    void parseHandlesHexColors() {
        Component result = ColorUtil.parse("&#FF00AAtext");
        assertEquals("text", PlainTextComponentSerializer.plainText().serialize(result));
        assertEquals(TextColor.color(0xFF, 0x00, 0xAA), result.color());
    }

    @Test
    void parseSuppressesItalicByDefault() {
        Component result = ColorUtil.parse("plain");
        assertEquals(TextDecoration.State.FALSE, result.decoration(TextDecoration.ITALIC));
    }

    @Test
    void parseEmptyStringReturnsEmptyComponent() {
        Component result = ColorUtil.parse("");
        assertEquals("", PlainTextComponentSerializer.plainText().serialize(result));
    }

    @Test
    void allHelpGradientConstantsAreWellFormed() throws IllegalAccessException {
        int checked = 0;
        for (Field f : ColorUtil.class.getDeclaredFields()) {
            if (!f.getName().startsWith("HELP_GRAD_")) continue;
            assertTrue(Modifier.isStatic(f.getModifiers()), f.getName() + " must be static");
            assertTrue(Modifier.isFinal(f.getModifiers()), f.getName() + " must be final");
            assertEquals(String.class, f.getType(), f.getName() + " must be String");
            String value = (String) f.get(null);
            assertNotNull(value, f.getName() + " must not be null");
            assertFalse(value.isBlank(), f.getName() + " must not be blank");
            for (String token : value.split(":")) {
                assertTrue(HEX_TOKEN.matcher(token).matches(),
                        f.getName() + " has malformed token: " + token);
            }
            checked++;
        }
        assertTrue(checked >= 10, "expected many HELP_GRAD_* constants, found " + checked);
    }

    @Test
    void colorUtilCannotBeInstantiated() throws Exception {
        var ctor = ColorUtil.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()), "constructor must be private");
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }
}