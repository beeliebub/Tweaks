package me.beeliebub.tweaks.tests.minigames.blackjack;

import me.beeliebub.tweaks.minigames.blackjack.BlackjackCommand;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Tests for BlackjackCommand's free-table feature: '0' or 'free' as the bet
// argument must be accepted; negative numbers must be rejected; the parsed bet
// value must be 0 so that BlackjackListener.beginTableSetup is called with 0.
//
// NOTE: These tests do not start MockBukkit because BlackjackCommand only calls
// player.hasPermission(), player.sendMessage(), and listener.beginTableSetup().
// All three are easily mocked. If MockBukkit is ever updated to support Paper
// 26.2, these tests can be converted to use a real server environment.
class BlackjackCommandFreeTableTest {

    private BlackjackCommand command;
    private BlackjackListener listener;
    private Player player;
    private Command cmd;

    @BeforeEach
    void setUp() {
        EconomyManager economy = mock(EconomyManager.class);
        listener = mock(BlackjackListener.class);
        command = new BlackjackCommand(economy, listener);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        // Grant all permissions by default.
        when(player.hasPermission(any(String.class))).thenReturn(true);

        cmd = mock(Command.class);
    }

    // ─── Accepted free-table inputs ───────────────────────────────────────────

    @Test
    void betArgZero_callsBeginSetupWithBetZero() {
        command.onCommand(player, cmd, "blackjack", new String[]{"createtable", "0"});
        verify(listener).beginTableSetup(eq(player), eq(0), isNull());
    }

    @Test
    void betArgFree_callsBeginSetupWithBetZero() {
        command.onCommand(player, cmd, "blackjack", new String[]{"createtable", "free"});
        verify(listener).beginTableSetup(eq(player), eq(0), isNull());
    }

    @Test
    void betArgFREE_caseInsensitive_callsBeginSetupWithBetZero() {
        command.onCommand(player, cmd, "blackjack", new String[]{"createtable", "FREE"});
        verify(listener).beginTableSetup(eq(player), eq(0), isNull());
    }

    @Test
    void betArgFree_mixedCase_callsBeginSetupWithBetZero() {
        command.onCommand(player, cmd, "blackjack", new String[]{"createtable", "Free"});
        verify(listener).beginTableSetup(eq(player), eq(0), isNull());
    }

    // ─── Positive bets still work ─────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"10", "100", "500", "1000"})
    void positiveIntBets_callsBeginSetupWithCorrectBet(String bet) {
        command.onCommand(player, cmd, "blackjack", new String[]{"createtable", bet});
        verify(listener).beginTableSetup(eq(player), eq(Integer.parseInt(bet)), isNull());
    }

    // ─── Negative bets are rejected ───────────────────────────────────────────

    @Test
    void negativeBet_neverCallsBeginSetup() {
        command.onCommand(player, cmd, "blackjack", new String[]{"createtable", "-1"});
        verify(listener, never()).beginTableSetup(any(), anyInt(), any());
    }

    @Test
    void negativeLargeBet_neverCallsBeginSetup() {
        command.onCommand(player, cmd, "blackjack", new String[]{"createtable", "-9999"});
        verify(listener, never()).beginTableSetup(any(), anyInt(), any());
    }

    // ─── Invalid non-numeric strings are rejected ─────────────────────────────

    @Test
    void nonNumericBet_neverCallsBeginSetup() {
        command.onCommand(player, cmd, "blackjack", new String[]{"createtable", "banana"});
        verify(listener, never()).beginTableSetup(any(), anyInt(), any());
    }

    // ─── Permission check ─────────────────────────────────────────────────────

    @Test
    void noPermission_neverCallsBeginSetup() {
        when(player.hasPermission(Permissions.BLACKJACK_CREATETABLE)).thenReturn(false);
        command.onCommand(player, cmd, "blackjack", new String[]{"createtable", "free"});
        verify(listener, never()).beginTableSetup(any(), anyInt(), any());
    }

    // ─── Missing bet argument ─────────────────────────────────────────────────

    @Test
    void missingBetArg_neverCallsBeginSetup() {
        command.onCommand(player, cmd, "blackjack", new String[]{"createtable"});
        verify(listener, never()).beginTableSetup(any(), anyInt(), any());
    }

    // ─── Optional hex color still works alongside free bet ───────────────────

    @Test
    void freeBetWithHexColor_callsBeginSetupWithColorAndBetZero() {
        command.onCommand(player, cmd, "blackjack", new String[]{"createtable", "free", "FF8800"});
        // Verify bet=0 and color=0xFF8800
        ArgumentCaptor<Integer> colorCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(listener).beginTableSetup(eq(player), eq(0), colorCaptor.capture());
        assertNotNull(colorCaptor.getValue());
        assertEquals(0xFF8800, colorCaptor.getValue());
    }
}
