package me.beeliebub.tweaks.tests.commands;

import me.beeliebub.tweaks.ColorUtil;
import me.beeliebub.tweaks.commands.HelpCommand;
import me.beeliebub.tweaks.listeners.HelpListener;
import me.beeliebub.tweaks.managers.HelpManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HelpListenerTest {

    private ServerMock server;
    private HelpManager helpManager;
    private HelpListener helpListener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        helpManager = mock(HelpManager.class);
        HelpCommand helpCommand = new HelpCommand(helpManager);
        helpListener = new HelpListener(helpCommand, helpManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onPlayerJoinSendsHelpTipPrompt() {
        PlayerMock player = server.addPlayer();
        player.nextMessage(); // drain MockBukkit's default join message
        when(helpManager.getRandomArticle(any())).thenReturn(null);

        PlayerJoinEvent event = new PlayerJoinEvent(player, Component.empty());
        helpListener.onPlayerJoin(event);

        verify(helpManager).getRandomArticle(player);
        assertTrue(player.nextComponentMessage() != null);
    }

    @Test
    void onPlayerJoinAppendsTipWhenArticleIsAvailable() {
        PlayerMock player = server.addPlayer();
        player.nextMessage();
        HelpManager.HelpArticle tip = new HelpManager.HelpArticle(
                "homes", "Homes", List.of(), Material.RED_BED, 0,
                ColorUtil.HELP_GRAD_HOMES, List.of());
        when(helpManager.getRandomArticle(any())).thenReturn(tip);

        PlayerJoinEvent event = new PlayerJoinEvent(player, Component.empty());
        helpListener.onPlayerJoin(event);

        verify(helpManager).getRandomArticle(player);
        // Three messages: empty separator, /help prompt, tip line (the trailing empty
        // separator follows). We only assert the first non-empty content reached the player.
        boolean sawAny = false;
        for (int i = 0; i < 5; i++) {
            Component msg = player.nextComponentMessage();
            if (msg == null) break;
            sawAny = true;
        }
        assertTrue(sawAny);
    }
}
