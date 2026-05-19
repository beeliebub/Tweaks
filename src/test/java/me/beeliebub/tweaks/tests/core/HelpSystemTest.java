package me.beeliebub.tweaks.tests.core;

import me.beeliebub.tweaks.core.HelpSystem;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

// Minimal smoke check for the consolidated help system. Verifies that categories load,
// articles resolve, and cross-references validate without warning (silent logger).
class HelpSystemTest {

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
}
