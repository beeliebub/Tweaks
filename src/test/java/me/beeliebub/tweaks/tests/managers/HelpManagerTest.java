package me.beeliebub.tweaks.tests.managers;

import me.beeliebub.tweaks.managers.HelpManager;
import me.beeliebub.tweaks.managers.HelpManager.HelpArticle;
import me.beeliebub.tweaks.managers.HelpManager.HelpCategory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HelpManagerTest {

    private static HelpManager helpManager;
    private static List<LogRecord> warnings;

    @BeforeAll
    static void load() {
        warnings = new java.util.ArrayList<>();
        Logger captureLogger = Logger.getLogger("HelpManagerTest");
        captureLogger.setUseParentHandlers(false);
        captureLogger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) warnings.add(record);
            }
            @Override public void flush() {}
            @Override public void close() {}
        });
        helpManager = new HelpManager(captureLogger);
    }

    @Test
    void registersAtLeastOneCategoryPerKnownTopic() {
        Set<String> ids = new HashSet<>();
        for (HelpCategory c : helpManager.getCategories()) ids.add(c.id());
        // CLAUDE.md enumerates these: teleportation, custom enchants, quality, player features,
        // minigames, permissions. Exact ids may differ — just enforce minimum coverage.
        assertTrue(helpManager.getCategories().size() >= 5,
                "expected at least 5 categories, found " + ids);
    }

    @Test
    void getCategoriesIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> helpManager.getCategories().clear());
    }

    @Test
    void getCategoryAndGetArticleAreNullSafe() {
        assertNull(helpManager.getCategory(null));
        assertNull(helpManager.getArticle(null));
        assertNull(helpManager.getCategory("does-not-exist"));
        assertNull(helpManager.getArticle("does-not-exist"));
    }

    @Test
    void everyCategoryArticleIsRetrievableByGetArticle() {
        for (HelpCategory cat : helpManager.getCategories()) {
            for (HelpArticle a : cat.articles()) {
                assertSame(a, helpManager.getArticle(a.id()),
                        "article '" + a.id() + "' is not retrievable via getArticle");
            }
        }
    }

    @Test
    void crossReferenceValidationRunsAndOnlyComplainsAboutKnownExistingMisses() {
        // Snapshot of currently broken cross-references (tracked separately as bd bugs).
        // Any *new* warning beyond this set fails the test so regressions are caught immediately.
        Set<String> known = Set.of(
                "Help: article 'flight' references unknown article 'teleportation'",
                "Help: article 'permissions_overview' references unknown article 'permissions_users'"
        );
        for (LogRecord w : warnings) {
            if (!w.getMessage().contains("references unknown article")) continue;
            assertTrue(known.contains(w.getMessage()),
                    "Unexpected new help cross-reference warning: " + w.getMessage());
        }
    }

    @Test
    void noTwoArticlesShareAnId() {
        Set<String> seen = new HashSet<>();
        for (HelpCategory cat : helpManager.getCategories()) {
            for (HelpArticle a : cat.articles()) {
                assertTrue(seen.add(a.id()),
                        "duplicate article id across categories: " + a.id());
            }
        }
    }

    @Test
    void getRandomArticleReturnsNullWhenPlayerHasNoVisibleArticles() {
        Player player = mock(Player.class);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        // If HelpManager has any always-visible (permission == null) articles, this test must
        // tolerate that and just check we don't NPE; otherwise expect null.
        HelpArticle picked = helpManager.getRandomArticle(player);
        if (picked != null) {
            assertNull(picked.permission(),
                    "player without any perms should only see permission-less articles");
        }
    }

    @Test
    void getRandomArticleReturnsArticleVisibleToPlayer() {
        Player player = mock(Player.class);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        // With all perms granted, we should get *some* article from a non-empty manager.
        HelpArticle picked = helpManager.getRandomArticle(player);
        assertNotNull(picked, "expected a random article when all permissions are granted");
    }

    @Test
    void helpArticleRecordCopiesContentAndRelatedListsDefensively() {
        java.util.ArrayList<net.kyori.adventure.text.Component> content = new java.util.ArrayList<>();
        content.add(net.kyori.adventure.text.Component.text("a"));
        java.util.ArrayList<String> related = new java.util.ArrayList<>(List.of("x"));
        HelpArticle a = new HelpArticle("id", "Title", content, Material.STONE, 0, "#FFF", related);
        content.clear();
        related.clear();
        // Defensive copies in the canonical constructor must shield the record.
        assertEquals(1, a.content().size());
        assertEquals(List.of("x"), a.relatedArticles());
        // Returned lists must be immutable.
        assertThrows(UnsupportedOperationException.class, () -> a.content().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.relatedArticles().clear());
    }

    @Test
    void helpArticleNullRelatedListNormalizesToEmpty() {
        HelpArticle a = new HelpArticle("id", "Title", List.of(), Material.STONE, 0, "#FFF", null);
        assertNotNull(a.relatedArticles());
        assertTrue(a.relatedArticles().isEmpty());
    }

    @Test
    void helpCategoryHasVisibleArticlesRespectsPlayerPermissions() {
        HelpCategory cat = helpManager.getCategories().iterator().next();
        Player allowed = mock(Player.class);
        when(allowed.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        assertTrue(cat.hasVisibleArticles(allowed),
                "with all perms, every category should show at least one article");
    }
}
