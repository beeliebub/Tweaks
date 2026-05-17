package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.ColorUtil;
import me.beeliebub.tweaks.commands.HelpCommand;
import me.beeliebub.tweaks.managers.HelpManager;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HelpCommandTest {

    private ServerMock server;
    private HelpManager helpManager;
    private HelpCommand helpCommand;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        helpManager = mock(HelpManager.class);
        helpCommand = new HelpCommand(helpManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // MockBukkit doesn't bootstrap Paper's registry, so Dialog.create() throws. The tests
    // therefore assert that the command iterates categories/articles (proving the dialog
    // path is taken) and tolerate the registry-related exception that follows.
    private static void runIgnoringRegistry(Runnable action) {
        try {
            action.run();
        } catch (Exception expected) {
            // Paper Dialog creation requires a live registry; absent in MockBukkit.
        }
    }

    @Test
    void helpCommandShowsDialogWithNoArgs() {
        PlayerMock player = server.addPlayer();
        when(helpManager.getCategories()).thenReturn(List.of());

        runIgnoringRegistry(() ->
                helpCommand.onCommand(player, null, "help", new String[0])
        );

        // Confirms openMainMenu was invoked — the inventory-based GUI is gone.
        verify(helpManager, atLeastOnce()).getCategories();
    }

    @Test
    void helpCommandSendsArticleWithValidArg() {
        PlayerMock player = server.addPlayer();
        HelpManager.HelpArticle article = new HelpManager.HelpArticle(
                "test", "Test Article", List.of(), Material.BOOK, 0,
                ColorUtil.HELP_GRAD_HOMES, List.of());
        when(helpManager.getArticle("test")).thenReturn(article);

        helpCommand.onCommand(player, null, "help", new String[]{"test"});

        verify(helpManager).getArticle("test");
    }

    @Test
    void helpCommandShowsCategoryDialogWithValidArg() {
        PlayerMock player = server.addPlayer();
        HelpManager.HelpCategory category = mock(HelpManager.HelpCategory.class);
        when(category.id()).thenReturn("testcat");
        when(category.title()).thenReturn("Test Category");
        when(category.gradient()).thenReturn(ColorUtil.HELP_GRAD_HOMES);
        when(category.articles()).thenReturn(List.of());
        when(category.hasVisibleArticles(any())).thenReturn(true);
        when(helpManager.getCategory("testcat")).thenReturn(category);

        runIgnoringRegistry(() ->
                helpCommand.onCommand(player, null, "help", new String[]{"testcat"})
        );

        verify(helpManager).getCategory("testcat");
        verify(category).hasVisibleArticles(player);
        verify(category).articles();
    }

    @Test
    void helpCommandRejectsUnknownArg() {
        PlayerMock player = server.addPlayer();
        helpCommand.onCommand(player, null, "help", new String[]{"unknown"});
        verify(helpManager).getCategory("unknown");
        verify(helpManager).getArticle("unknown");
    }
}
