package me.beeliebub.tweaks.minigames.blackjack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BlackjackSessionManager#computeSettlement}, the pure payout/rakeback
 * settlement calculation extracted from {@code finish()} when the original monolithic
 * {@code BlackjackListener} was split into persistence ({@code BlackjackTableStore}), rendering
 * ({@code BlackjackRenderer}), and session/economy ({@code BlackjackSessionManager}) collaborators.
 *
 * <p>Placed in the production package (like {@link BlackjackTableStoreTest}) so that
 * package-private access to {@code computeSettlement} and {@link BlackjackSessionManager.Settlement}
 * is legal without widening production visibility purely for test convenience.
 *
 * <p>This closes the coverage gap {@code BlackjackRakebackTest} previously documented: that gap
 * covered only the standalone rakeback arithmetic, not the actual production settlement code path
 * (which also decides payout crediting and player-facing message wording). This test exercises
 * that real code path directly. The entity/scheduler half of {@code finish()} (mannequin reaction,
 * the actual {@code addBalance} calls, auto-clear scheduling) remains a real-server smoke-test
 * concern, since MockBukkit cannot drive {@code ItemDisplay}/{@code TextDisplay}/scheduled tasks.
 */
class BlackjackSessionManagerSettlementTest {

    private static String summaryText(BlackjackSessionManager.Settlement settlement) {
        return PlainTextComponentSerializer.plainText().serialize(settlement.summaryMessage());
    }

    // -------------------------------------------------------------------------
    // Non-free tables: payout crediting
    // -------------------------------------------------------------------------

    @Test
    void playerBlackjack_nonFree_paysBetPlusOnePointFiveX() {
        var settlement = BlackjackSessionManager.computeSettlement(
                BlackjackGame.Result.PLAYER_BLACKJACK, 100, 250, 0.0);

        assertEquals(250, settlement.payoutAmount(), "payout must be bet + floor(bet*1.5) = 250");
        assertEquals(0, settlement.rakebackAmount(), "no rakeback on a player win");
        assertEquals(0, settlement.houseWinnings(), "a player win must never debit the house");
        assertTrue(summaryText(settlement).contains("BLACKJACK"));
        assertTrue(summaryText(settlement).contains("won $150"),
                "net winnings must be payout - bet = 150");
    }

    @Test
    void playerWin_nonFree_paysDoubleBet() {
        var settlement = BlackjackSessionManager.computeSettlement(
                BlackjackGame.Result.PLAYER_WIN, 100, 200, 0.0);

        assertEquals(200, settlement.payoutAmount(), "payout must be bet * 2 = 200");
        assertEquals(0, settlement.rakebackAmount());
        assertEquals(0, settlement.houseWinnings());
        assertTrue(summaryText(settlement).contains("Payout: $200"));
        assertTrue(summaryText(settlement).contains("net +$100"));
    }

    @Test
    void push_nonFree_returnsBet() {
        var settlement = BlackjackSessionManager.computeSettlement(
                BlackjackGame.Result.PUSH, 100, 100, 0.0);

        assertEquals(100, settlement.payoutAmount(), "push must return exactly the bet");
        assertEquals(0, settlement.rakebackAmount());
        assertEquals(0, settlement.houseWinnings(), "a push must not credit the house");
        assertTrue(summaryText(settlement).contains("bet of $100 is returned"));
    }

    // -------------------------------------------------------------------------
    // Dealer win: rakeback gate
    // -------------------------------------------------------------------------

    @Test
    void dealerWin_nonFree_withRakebackRate_creditsFlooredRakeback() {
        var settlement = BlackjackSessionManager.computeSettlement(
                BlackjackGame.Result.DEALER_WIN, 100, 0, 0.05);

        assertEquals(0, settlement.payoutAmount(), "no payout on a dealer win");
        assertEquals(5, settlement.rakebackAmount(), "floor(100 * 0.05) = 5");
        assertEquals(100, settlement.houseWinnings(), "rakeback is minted and must not reduce house proceeds");
        assertTrue(summaryText(settlement).contains("lost $100"));
        assertTrue(summaryText(settlement).contains("Rakeback: +$5"));
    }

    @Test
    void dealerWin_nonFree_zeroRakebackRate_creditsNothingAndOmitsSuffix() {
        var settlement = BlackjackSessionManager.computeSettlement(
                BlackjackGame.Result.DEALER_WIN, 100, 0, 0.0);

        assertEquals(0, settlement.rakebackAmount());
        assertEquals(100, settlement.houseWinnings());
        assertTrue(summaryText(settlement).contains("lost $100"));
        assertFalse(summaryText(settlement).contains("Rakeback"),
                "no rakeback suffix must appear when the rate is zero");
    }

    @Test
    void dealerWin_nonFree_flooredRakebackTruncates() {
        // 333 * 0.05 = 16.65 -> floor = 16.
        var settlement = BlackjackSessionManager.computeSettlement(
                BlackjackGame.Result.DEALER_WIN, 333, 0, 0.05);

        assertEquals(16, settlement.rakebackAmount(), "floor(333 * 0.05) must equal 16");
        assertEquals(333, settlement.houseWinnings());
    }

    @Test
    void playerWin_rakebackRateIgnoredOnNonDealerWinResult() {
        // Rakeback only applies to DEALER_WIN; a nonzero rate on any other result must not credit.
        var settlement = BlackjackSessionManager.computeSettlement(
                BlackjackGame.Result.PLAYER_WIN, 100, 200, 0.10);

        assertEquals(0, settlement.rakebackAmount(),
                "rakeback must never apply to a non-DEALER_WIN result");
        assertEquals(0, settlement.houseWinnings());
    }

    // -------------------------------------------------------------------------
    // Free/practice tables: no economy movement regardless of result or rate
    // -------------------------------------------------------------------------

    @Test
    void freeTable_playerBlackjack_creditsNothingAndNotesPractice() {
        var settlement = BlackjackSessionManager.computeSettlement(
                BlackjackGame.Result.PLAYER_BLACKJACK, 0, 0, 0.0);

        assertEquals(0, settlement.payoutAmount());
        assertEquals(0, settlement.rakebackAmount());
        assertEquals(0, settlement.houseWinnings());
        assertTrue(summaryText(settlement).contains("Practice table"));
    }

    @Test
    void freeTable_dealerWin_ignoresNonzeroRakebackRate() {
        // Even if the caller (incorrectly) passed a nonzero rate for a free table, bet == 0
        // must force both payout and rakeback to zero.
        var settlement = BlackjackSessionManager.computeSettlement(
                BlackjackGame.Result.DEALER_WIN, 0, 0, 0.10);

        assertEquals(0, settlement.payoutAmount());
        assertEquals(0, settlement.rakebackAmount());
        assertEquals(0, settlement.houseWinnings());
        assertTrue(summaryText(settlement).contains("Practice table"));
        assertFalse(summaryText(settlement).contains("Rakeback"));
    }

    @Test
    void freeTable_push_notesTieGame() {
        var settlement = BlackjackSessionManager.computeSettlement(
                BlackjackGame.Result.PUSH, 0, 0, 0.0);

        assertEquals(0, settlement.payoutAmount());
        assertEquals(0, settlement.houseWinnings());
        assertTrue(summaryText(settlement).contains("Tie game"));
        assertTrue(summaryText(settlement).contains("Practice table"));
    }
}
