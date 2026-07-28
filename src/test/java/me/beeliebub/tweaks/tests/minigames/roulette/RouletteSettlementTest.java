package me.beeliebub.tweaks.tests.minigames.roulette;

import me.beeliebub.tweaks.minigames.roulette.BetType;
import me.beeliebub.tweaks.minigames.roulette.RouletteBet;
import me.beeliebub.tweaks.minigames.roulette.RouletteRound;
import me.beeliebub.tweaks.minigames.roulette.RouletteRound.PlayerCredit;
import me.beeliebub.tweaks.minigames.roulette.RouletteRound.Settlement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link RouletteRound#computeSettlement}'s money math: the stake-inclusive payout
 * convention, pocket 0 losing every bet family, house winnings being gross of losing wagers only,
 * and rakeback applying strictly to a player's net loss.
 */
class RouletteSettlementTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    // ---- payout convention: amount * (multiplier + 1), stake returned + winnings ----

    @Test
    void winningStraightBetCreditsStakePlusThirtySixTimes() {
        RouletteBet win = new RouletteBet(PLAYER, BetType.STRAIGHT, 17, 10);
        Settlement settlement = RouletteRound.computeSettlement(List.of(win), 17, Map.of());
        assertEquals(370L, settlement.credits().get(PLAYER).payout(), "10 * (36 + 1) = 370");
    }

    @Test
    void winningDozenBetCreditsStakePlusThreeTimes() {
        RouletteBet win = new RouletteBet(PLAYER, BetType.DOZEN, 1, 10);
        Settlement settlement = RouletteRound.computeSettlement(List.of(win), 5, Map.of());
        assertEquals(40L, settlement.credits().get(PLAYER).payout(), "10 * (3 + 1) = 40");
    }

    @Test
    void winningColorBetCreditsStakePlusTwoTimes() {
        RouletteBet win = new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_RED, 10);
        Settlement settlement = RouletteRound.computeSettlement(List.of(win), 1, Map.of());
        assertEquals(30L, settlement.credits().get(PLAYER).payout(), "10 * (2 + 1) = 30, pocket 1 is red");
    }

    @Test
    void losingBetCreditsNothing() {
        RouletteBet lose = new RouletteBet(PLAYER, BetType.STRAIGHT, 17, 10);
        Settlement settlement = RouletteRound.computeSettlement(List.of(lose), 18, Map.of());
        assertEquals(0L, settlement.credits().get(PLAYER).payout());
    }

    // ---- pocket 0 loses every bet family ----

    @Test
    void pocketZeroLosesStraightDozenAndColorBets() {
        RouletteBet straightOnZero = new RouletteBet(PLAYER, BetType.STRAIGHT, 5, 10);
        RouletteBet dozen = new RouletteBet(PLAYER, BetType.DOZEN, 1, 10);
        RouletteBet color = new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_RED, 10);
        Settlement settlement = RouletteRound.computeSettlement(
                List.of(straightOnZero, dozen, color), 0, Map.of());
        assertEquals(0L, settlement.credits().get(PLAYER).payout(), "pocket 0 must not push any bet family");
        assertEquals(30L, settlement.houseCredit(), "all three losing wagers (10+10+10) go to the house");
    }

    @Test
    void straightBetOnPocketZeroItselfCanWin() {
        RouletteBet straightOnZero = new RouletteBet(PLAYER, BetType.STRAIGHT, 0, 10);
        Settlement settlement = RouletteRound.computeSettlement(List.of(straightOnZero), 0, Map.of());
        assertEquals(510L, settlement.credits().get(PLAYER).payout(),
                "a straight-up bet ON 0 wins the Green rate when 0 is drawn: 10 * (50 + 1) = 510");
    }

    // ---- house winnings: gross of losing wagers only, never offset by a win ----

    @Test
    void houseCreditIsGrossOfLosingWagersOnly() {
        RouletteBet win = new RouletteBet(PLAYER, BetType.STRAIGHT, 17, 10);
        RouletteBet lose = new RouletteBet(PLAYER, BetType.STRAIGHT, 18, 5);
        Settlement settlement = RouletteRound.computeSettlement(List.of(win, lose), 17, Map.of());
        assertEquals(5L, settlement.houseCredit(), "the winning bet must not offset the house credit");
    }

    @Test
    void houseCreditNeverGoesNegative() {
        RouletteBet win = new RouletteBet(PLAYER, BetType.STRAIGHT, 17, 1000);
        Settlement settlement = RouletteRound.computeSettlement(List.of(win), 17, Map.of());
        assertEquals(0L, settlement.houseCredit(), "a round that only pays out must floor the house credit at 0");
    }

    // ---- rakeback: net loss only, floored, never on a net win ----

    @Test
    void rakebackAppliesToNetLossAcrossTheWholeRound() {
        RouletteBet lose = new RouletteBet(PLAYER, BetType.STRAIGHT, 18, 100);
        Settlement settlement = RouletteRound.computeSettlement(
                List.of(lose), 17, Map.of(PLAYER, 0.05));
        assertEquals(0L, settlement.credits().get(PLAYER).payout());
        assertEquals(5L, settlement.credits().get(PLAYER).rakeback(), "floor(100 * 0.05) = 5");
        assertEquals(100L, settlement.houseCredit(),
                "rakeback is minted, not taken from the house's cut — the house still "
                        + "gets the full losing wager even though this same loss also paid rakeback");
    }

    @Test
    void rakebackFlooringTruncates() {
        RouletteBet lose = new RouletteBet(PLAYER, BetType.STRAIGHT, 18, 333);
        Settlement settlement = RouletteRound.computeSettlement(
                List.of(lose), 17, Map.of(PLAYER, 0.05));
        assertEquals(16L, settlement.credits().get(PLAYER).rakeback(), "floor(333 * 0.05) = 16.65 -> 16");
    }

    @Test
    void rakebackNeverAppliesOnANetWin() {
        RouletteBet win = new RouletteBet(PLAYER, BetType.STRAIGHT, 17, 10);
        Settlement settlement = RouletteRound.computeSettlement(
                List.of(win), 17, Map.of(PLAYER, 0.5));
        assertEquals(0L, settlement.credits().get(PLAYER).rakeback(), "a net win must never receive rakeback");
    }

    @Test
    void rakebackNeverAppliesOnAnEvenSplitAcrossMultipleBets() {
        // Player stakes 30 total across two bets: a 10-stake color win pays 30 (10 * (2 + 1)),
        // exactly offsetting the 20-stake losing straight bet — net 0, not a net loss.
        RouletteBet win = new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_RED, 10);
        RouletteBet lose = new RouletteBet(PLAYER, BetType.STRAIGHT, 18, 20);
        Settlement settlement = RouletteRound.computeSettlement(
                List.of(win, lose), 1, Map.of(PLAYER, 0.1));
        // payout = 30 (color win), wagered = 30, net = 0 -> not a net loss.
        assertEquals(0L, settlement.credits().get(PLAYER).rakeback(), "breaking even is not a net loss");
    }

    @Test
    void rakebackDefaultsToZeroWhenPlayerHasNoRate() {
        RouletteBet lose = new RouletteBet(PLAYER, BetType.STRAIGHT, 18, 100);
        Settlement settlement = RouletteRound.computeSettlement(List.of(lose), 17, Map.of());
        assertEquals(0L, settlement.credits().get(PLAYER).rakeback());
    }

    // ---- multi-player rounds settle independently ----

    @Test
    void multiplePlayersSettleIndependently() {
        RouletteBet playerWins = new RouletteBet(PLAYER, BetType.STRAIGHT, 17, 10);
        RouletteBet otherLoses = new RouletteBet(OTHER, BetType.STRAIGHT, 18, 10);
        Settlement settlement = RouletteRound.computeSettlement(
                List.of(playerWins, otherLoses), 17, Map.of());
        PlayerCredit playerCredit = settlement.credits().get(PLAYER);
        PlayerCredit otherCredit = settlement.credits().get(OTHER);
        assertEquals(370L, playerCredit.payout());
        assertEquals(0L, otherCredit.payout());
        assertEquals(10L, settlement.houseCredit());
    }

    // ---- covering every pocket: exactly one of many stacked bets can ever win ----

    /**
     * A player who places an equal straight-up bet on all 37 pockets is only ever exposed to the
     * single pocket that's actually drawn — every other bet in the stack is a separate ledger
     * entry that independently loses. This pins that {@code computeSettlement} never double-counts
     * (crediting more than the one winning bet's payout) or under-counts (losing money it never
     * should have, e.g. by treating the whole stack as a single bet).
     */
    @Test
    void coveringEveryPocketOnlyEverPaysTheOneWinningBet() {
        int amount = 10;
        List<RouletteBet> everyPocket = new java.util.ArrayList<>();
        for (int pocket = 0; pocket <= 36; pocket++) {
            everyPocket.add(new RouletteBet(PLAYER, BetType.STRAIGHT, pocket, amount));
        }
        int totalWagered = 37 * amount;

        // A non-zero pocket wins at the standard 36:1 rate: payout exactly refunds the whole
        // stack (37 * 10 wagered, 10 * 37 paid back) — net zero, not a profit and not a loss.
        Settlement onNonZero = RouletteRound.computeSettlement(everyPocket, 5, Map.of());
        assertEquals((long) amount * 37, onNonZero.credits().get(PLAYER).payout(),
                "the pocket-5 bet alone pays 10 * (36 + 1) = 370, matching the 370 wagered overall");
        assertEquals(totalWagered, onNonZero.credits().get(PLAYER).payout(),
                "covering every pocket at equal stakes must break exactly even on any non-zero pocket");
        assertEquals(0L, onNonZero.credits().get(PLAYER).rakeback(), "breaking even is not a net loss");
        assertEquals((long) amount * 36, onNonZero.houseCredit(), "the 36 losing bets fund the house credit");

        // Pocket 0 wins at the Green rate (50:1) instead — the stack turns a real profit, not the
        // 36:1 rate the other 36 losing bets might suggest if the override were dropped.
        Settlement onGreen = RouletteRound.computeSettlement(everyPocket, 0, Map.of());
        long greenPayout = (long) amount * (RouletteBet.GREEN_PAYOUT_MULTIPLIER + 1);
        assertEquals(greenPayout, onGreen.credits().get(PLAYER).payout(), "10 * (50 + 1) = 510");
        assertTrue(greenPayout > totalWagered, "the Green bonus must turn covering every pocket into a net win");
        assertEquals((long) amount * 36, onGreen.houseCredit(),
                "the house credit is unaffected by which pocket won — still the 36 losing bets");
    }

    /**
     * Mixing bet families in one round settles each independently: a straight-up win, a dozen
     * win, and a color loss must each be evaluated on their own terms, not conflated.
     */
    @Test
    void mixedBetFamiliesInOneRoundEachSettleOnTheirOwnMerits() {
        RouletteBet straightWin = new RouletteBet(PLAYER, BetType.STRAIGHT, 5, 10);
        RouletteBet dozenWin = new RouletteBet(PLAYER, BetType.DOZEN, 1, 20);
        RouletteBet colorLoss = new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_BLACK, 15);
        Settlement settlement = RouletteRound.computeSettlement(
                List.of(straightWin, dozenWin, colorLoss), 5, Map.of());

        long expectedPayout = 10L * (36 + 1) + 20L * (3 + 1); // straight + dozen both hit; color (black) misses on odd pocket 5
        assertEquals(expectedPayout, settlement.credits().get(PLAYER).payout());
        assertEquals(15L, settlement.houseCredit(), "only the losing colour bet funds the house credit");
    }
}
