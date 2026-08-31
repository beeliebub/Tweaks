package me.beeliebub.tweaks.tests.core;

import me.beeliebub.tweaks.core.HelpSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

// Minimal smoke check for the consolidated help system. Verifies that categories load,
// articles resolve, and cross-references validate without warning (silent logger).
class HelpSystemTest {

    private static String fullContent(Component component) {
        StringBuilder content = new StringBuilder();
        if (component instanceof TextComponent text) content.append(text.content());
        for (Component child : component.children()) content.append(fullContent(child));
        return content.toString();
    }

    @Test void categoriesLoadInExpectedOrder() {
        HelpSystem help = new HelpSystem(Logger.getAnonymousLogger());
        var ids = help.getCategories().stream().map(HelpSystem.HelpCategory::id).toList();
        assertTrue(ids.contains("teleportation"));
        assertTrue(ids.contains("enchantments"));
        assertTrue(ids.contains("quality"));
        assertTrue(ids.contains("features"));
        assertTrue(ids.contains("minigames"));
        assertTrue(ids.contains("permissions"));
        assertTrue(ids.contains("protection"));
    }

    @Test void getArticleResolvesById() {
        HelpSystem help = new HelpSystem(Logger.getAnonymousLogger());
        HelpSystem.HelpArticle homes = help.getArticle("homes");
        assertNotNull(homes);
        assertEquals("Homes", homes.title());
    }

    @Test void getCategoryReturnsNullForUnknown() {
        HelpSystem help = new HelpSystem(Logger.getAnonymousLogger());
        assertNull(help.getCategory("does-not-exist"));
        assertNull(help.getArticle("does-not-exist"));
        assertNull(help.getCategory(null));
        assertNull(help.getArticle(null));
    }

    @Test
    void permissionsGuiHelpDescribesScrollingWithoutPageNavigation() {
        HelpSystem help = new HelpSystem(Logger.getAnonymousLogger());
        HelpSystem.HelpArticle article = help.getArticle("permissions_gui");
        assertNotNull(article);

        String content = article.content().stream()
                .map(HelpSystemTest::fullContent)
                .collect(Collectors.joining("\n"))
                .toLowerCase();

        assertTrue(content.contains("scroll in place"));
        assertTrue(content.contains("no page buttons"));
        assertFalse(content.contains("prev page"));
        assertFalse(content.contains("next page"));
        assertFalse(content.contains("pagination"));
    }
}
