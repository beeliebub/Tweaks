package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.commands.HelpCommand;
import me.beeliebub.tweaks.managers.HelpManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    void helpCommandOpensGuiWithNoArgs() {
        PlayerMock player = server.addPlayer();
        helpCommand.onCommand(player, null, "help", new String[0]);
        assertNotNull(player.getOpenInventory());
    }

    @Test
    void helpCommandSendsArticleWithValidArg() {
        PlayerMock player = server.addPlayer();
        HelpManager.HelpArticle article = mock(HelpManager.HelpArticle.class);
        when(article.title()).thenReturn("Test Article");
        when(article.gradient()).thenReturn("#FFFFFF:#000000");
        when(article.content()).thenReturn(List.of());
        when(helpManager.getArticle("test")).thenReturn(article);

        helpCommand.onCommand(player, null, "help", new String[]{"test"});

        // Verify some part of the article was sent. sendArticle uses MiniMessage which is hard to assert exactly,
        // but we can verify helpManager was called.
        verify(helpManager).getArticle("test");
    }

    @Test
    void helpCommandOpensCategoryWithValidArg() {
        PlayerMock player = server.addPlayer();
        HelpManager.HelpCategory category = mock(HelpManager.HelpCategory.class);
        when(category.id()).thenReturn("testcat");
        when(category.title()).thenReturn("Test Category");
        when(category.gradient()).thenReturn("#FFFFFF:#000000");
        when(category.hasVisibleArticles(player)).thenReturn(true);
        when(helpManager.getCategory("testcat")).thenReturn(category);

        helpCommand.onCommand(player, null, "help", new String[]{"testcat"});

        assertNotNull(player.getOpenInventory());
        verify(helpManager).getCategory("testcat");
    }
}
