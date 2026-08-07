package me.beeliebub.tweaks.tests.ranks;

import me.beeliebub.tweaks.ranks.RankManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link RankManager#getRankDisplayComponent(int)}.
 */
class RankCommandTest {

    @Test
    void legacyAmpersandGreenParsesToGreenComponent() {
        Component result = rankManagerWithName("&aAdmin").getRankDisplayComponent(1);
        assertEquals(NamedTextColor.GREEN, extractColor(result));
    }

    @Test
    void legacyAmpersandRedParsesToRedComponent() {
        Component result = rankManagerWithName("&cOwner").getRankDisplayComponent(1);
        assertEquals(NamedTextColor.RED, extractColor(result));
    }

    @Test
    void legacyAmpersandGoldParsesToGoldComponent() {
        Component result = rankManagerWithName("&6VIP").getRankDisplayComponent(1);
        assertEquals(NamedTextColor.GOLD, extractColor(result));
    }

    @Test
    void legacyAmpersandPreservesText() {
        Component result = rankManagerWithName("&aAdmin").getRankDisplayComponent(1);
        assertEquals("Admin", PlainTextComponentSerializer.plainText().serialize(result));
    }

    @Test
    void plainNameWithNoCodesReturnsComponent() {
        Component result = rankManagerWithName("Rank I").getRankDisplayComponent(1);
        assertEquals("Rank I", PlainTextComponentSerializer.plainText().serialize(result));
    }

    private static RankManager rankManagerWithName(String name) {
        RankManager rankManager = mock(RankManager.class, CALLS_REAL_METHODS);
        doReturn(name).when(rankManager).getRankDisplayName(1);
        return rankManager;
    }

    private static TextColor extractColor(Component component) {
        TextColor direct = component.color();
        if (direct != null) return direct;
        if (!component.children().isEmpty()) {
            return component.children().get(0).color();
        }
        if (component instanceof TextComponent textComponent) {
            return textComponent.color();
        }
        return null;
    }
}
