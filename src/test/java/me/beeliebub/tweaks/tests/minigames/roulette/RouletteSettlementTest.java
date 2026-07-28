package me.beeliebub.tweaks.tests.minigames.roulette;

import me.beeliebub.tweaks.minigames.roulette.BetType;
import me.beeliebub.tweaks.minigames.roulette.RouletteBet;
import me.beeliebub.tweaks.minigames.roulette.RouletteRound;
import me.beeliebub.tweaks.minigames.roulette.RouletteRound.PlayerCredit;
import me.beeliebub.tweaks.minigames.roulette.RouletteRound.Settlement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link RouletteRound#computeSettlement}'s money math: the winnings-only payout convention
 * (the wagered stake is never returned, even on a win), pocket 0 losing every bet family, house
 * winnings being gross of losing wagers only, and rakeback applying strictly to a player's net loss.
 */
class RouletteSettlementTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    // ---- payout convention: amount * multiplier, winnings only — the stake is never returned ----

    @Test
    void winningStraightBetCreditsThirtySixTimesTheStake() {
        RouletteBet win = new RouletteBet(PLAYER, BetType.STRAIGHT, 17, 10);
        Settlement settlement = RouletteRound.computeSettlement(List.of(win), 17, Map.of());
        assertEquals(360L, settlement.credits().get(PLAYER).payout(), "10 * 36 = 360");
    }

    @Test
    void winningDozenBetCreditsThreeTimesTheStake() {
        RouletteBet win = new RouletteBet(PLAYER, BetType.DOZEN, 1, 10);
        Settlement settlement = RouletteRound.computeSettlement(List.of(win), 5, Map.of());
        assertEquals(30L, settlement.credits().get(PLAYER).payout(), "10 * 3 = 30");
    }

    @Test
    void winningColorBetCreditsTwoTimesTheStake() {
        RouletteBet win = new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_RED, 10);
        Settlement settlement = RouletteRound.computeSettlement(List.of(win), 1, Map.of());
        assertEquals(20L, settlement.credits().get(PLAYER).payout(), "10 * 2 = 20, pocket 1 is red");
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
        assertEquals(500L, settlement.credits().get(PLAYER).payout(),
                "a straight-up bet ON 0 wins the Green rate when 0 is drawn: 10 * 50 = 500");
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
        // Player stakes 20 total across two equal bets: a 10-stake color win pays 20 (10 * 2,
        // winnings only), exactly offsetting the 10-stake losing straight bet — net 0, not a loss.
        RouletteBet win = new RouletteBet(PLAYER, BetType.COLOR, RouletteBet.COLOR_RED, 10);
        RouletteBet lose = new RouletteBet(PLAYER, BetType.STRAIGHT, 18, 10);
        Settlement settlement = RouletteRound.computeSettlement(
                List.of(win, lose), 1, Map.of(PLAYER, 0.1));
        // payout = 20 (color win), wagered = 20, net = 0 -> not a net loss.
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
        assertEquals(360L, playerCredit.payout());
        assertEquals(0L, otherCredit.payout());
        assertEquals(10L, settlement.houseCredit());
    }

    // ---- covering every pocket: exactly one of many stacked bets can ever win ----

    /**
     * A player who places an equal straight-up bet on all 37 pockets is only ever exposed to the
     * single pocket that's actually drawn — every other bet in the stack is a separate ledger
     * entry that independently loses. This pins that {@code computeSettlement} never double-counts
     * (crediting more than the one winning bet's payout) or under-counts (losing money it never
     * should have, e.g. by treating the whole stack as a single bet). Since no bet ever returns its
     * own stake, covering every non-zero pocket at equal stakes is a guaranteed small loss — the
     * winning bet's 36x payout falls one stake short of the 37 stakes risked.
     */
    @Test
    void coveringEveryPocketOnlyEverPaysTheOneWinningBet() {
        int amount = 10;
        List<RouletteBet> everyPocket = new ArrayList<>();
        for (int pocket = 0; pocket <= 36; pocket++) {
            everyPocket.add(new RouletteBet(PLAYER, BetType.STRAIGHT, pocket, amount));
        }
        int totalWagered = 37 * amount;

        Settlement onNonZero = RouletteRound.computeSettlement(everyPocket, 5, Map.of());
        long nonZeroPayout = (long) amount * 36;
        assertEquals(nonZeroPayout, onNonZero.credits().get(PLAYER).payout(), "the pocket-5 bet alone pays 10 * 36 = 360");
        assertTrue(nonZeroPayout < totalWagered,
                "covering every pocket at equal stakes must fall short of the 370 wagered — no bet returns its own stake");
        assertEquals((long) amount * 36, onNonZero.houseCredit(), "the 36 losing bets fund the house credit");

        // Pocket 0 wins at the Green rate (50:1) instead — the stack turns a real profit.
        Settlement onGreen = RouletteRound.computeSettlement(everyPocket, 0, Map.of());
        long greenPayout = (long) amount * RouletteBet.GREEN_PAYOUT_MULTIPLIER;
        assertEquals(greenPayout, onGreen.credits().get(PLAYER).payout(), "10 * 50 = 500");
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

        long expectedPayout = 10L * 36 + 20L * 3; // straight + dozen both hit; color (black) misses on odd pocket 5
        assertEquals(expectedPayout, settlement.credits().get(PLAYER).payout());
        assertEquals(15L, settlement.houseCredit(), "only the losing colour bet funds the house credit");
    }
}
