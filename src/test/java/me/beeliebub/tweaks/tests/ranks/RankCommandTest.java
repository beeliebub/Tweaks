package me.beeliebub.tweaks.tests.ranks;

import me.beeliebub.tweaks.ranks.RankCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RankCommand#parseDisplayName(String)}.
 * These are pure-unit tests — no MockBukkit needed.
 */
class RankCommandTest {

    // ---- Legacy ampersand codes ---------------------------------------------

    @Test
    void legacyAmpersandGreenParsesToGreenComponent() {
        Component result = RankCommand.parseDisplayName("&aAdmin");
        TextColor color = extractColor(result);
        assertEquals(NamedTextColor.GREEN, color,
                "&a should resolve to NamedTextColor.GREEN");
    }

    @Test
    void legacyAmpersandRedParsesToRedComponent() {
        Component result = RankCommand.parseDisplayName("&cOwner");
        TextColor color = extractColor(result);
        assertEquals(NamedTextColor.RED, color,
                "&c should resolve to NamedTextColor.RED");
    }

    @Test
    void legacyAmpersandGoldParsesToGoldComponent() {
        Component result = RankCommand.parseDisplayName("&6VIP");
        TextColor color = extractColor(result);
        assertEquals(NamedTextColor.GOLD, color,
                "&6 should resolve to NamedTextColor.GOLD");
    }

    @Test
    void legacyAmpersandPreservesText() {
        Component result = RankCommand.parseDisplayName("&aAdmin");
        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        assertEquals("Admin", plain, "Plain text should be 'Admin' without the color code");
    }

    // ---- Section-sign codes -------------------------------------------------

    @Test
    void sectionSignGreenParsesToGreenComponent() {
        // §a is the in-game section-sign form of &a
        Component result = RankCommand.parseDisplayName("§aElite");
        TextColor color = extractColor(result);
        assertEquals(NamedTextColor.GREEN, color,
                "§a (section sign) should resolve to NamedTextColor.GREEN");
    }

    // ---- MiniMessage tags ---------------------------------------------------

    @Test
    void miniMessageRedTagParsesToRedComponent() {
        Component result = RankCommand.parseDisplayName("<red>Champion");
        TextColor color = extractColor(result);
        assertEquals(NamedTextColor.RED, color,
                "<red> MiniMessage tag should resolve to NamedTextColor.RED");
    }

    @Test
    void miniMessageGreenTagParsesToGreenComponent() {
        Component result = RankCommand.parseDisplayName("<green>Veteran");
        TextColor color = extractColor(result);
        assertEquals(NamedTextColor.GREEN, color,
                "<green> MiniMessage tag should resolve to NamedTextColor.GREEN");
    }

    @Test
    void miniMessagePreservesText() {
        Component result = RankCommand.parseDisplayName("<gold>Legend");
        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        assertEquals("Legend", plain, "Plain text should be 'Legend' without tags");
    }

    // ---- Edge cases ---------------------------------------------------------

    @Test
    void nullNameReturnsEmptyComponent() {
        Component result = RankCommand.parseDisplayName(null);
        assertEquals(Component.empty(), result, "null name should return Component.empty()");
    }

    @Test
    void emptyNameReturnsEmptyComponent() {
        Component result = RankCommand.parseDisplayName("");
        assertEquals(Component.empty(), result, "empty name should return Component.empty()");
    }

    @Test
    void plainNameWithNoCodesReturnsComponent() {
        Component result = RankCommand.parseDisplayName("Rank I");
        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        assertEquals("Rank I", plain, "Plain name with no codes should round-trip as-is");
    }

    // ---- Helper -------------------------------------------------------------

    /**
     * Extracts the color from a Component. Checks the component itself first;
     * if absent, walks into the first child (MiniMessage wraps content in a child).
     */
    private static TextColor extractColor(Component component) {
        TextColor direct = component.color();
        if (direct != null) {
            return direct;
        }
        // MiniMessage and LegacyComponentSerializer may produce a parent + styled child
        if (!component.children().isEmpty()) {
            return component.children().get(0).color();
        }
        return null;
    }
}
