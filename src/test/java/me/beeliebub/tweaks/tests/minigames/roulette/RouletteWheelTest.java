package me.beeliebub.tweaks.tests.minigames.roulette;

import me.beeliebub.tweaks.minigames.roulette.RouletteWheel;
import me.beeliebub.tweaks.minigames.roulette.RouletteWheel.PocketColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Pins the fixed casino pocket-color/dozen table and the wheel-order/spin contract. */
class RouletteWheelTest {

    @Test
    void pocketZeroIsGreen() {
        assertEquals(PocketColor.GREEN, RouletteWheel.colorOf(0));
    }

    @Test
    void everyOddPocketIsRed() {
        for (int pocket = 1; pocket <= 36; pocket += 2) {
            assertEquals(PocketColor.RED, RouletteWheel.colorOf(pocket), "pocket " + pocket + " must be red");
        }
    }

    @Test
    void everyEvenPocketIsBlack() {
        for (int pocket = 2; pocket <= 36; pocket += 2) {
            assertEquals(PocketColor.BLACK, RouletteWheel.colorOf(pocket), "pocket " + pocket + " must be black");
        }
    }

    @Test
    void redAndBlackAreEighteenEach() {
        long redCount = java.util.stream.IntStream.rangeClosed(0, 36)
                .filter(pocket -> RouletteWheel.colorOf(pocket) == PocketColor.RED)
                .count();
        long blackCount = java.util.stream.IntStream.rangeClosed(0, 36)
                .filter(pocket -> RouletteWheel.colorOf(pocket) == PocketColor.BLACK)
                .count();
        assertEquals(18, redCount);
        assertEquals(18, blackCount);
    }

    @Test
    void colorOfRejectsOutOfRangePocket() {
        assertThrows(IllegalArgumentException.class, () -> RouletteWheel.colorOf(-1));
        assertThrows(IllegalArgumentException.class, () -> RouletteWheel.colorOf(37));
    }

    @Test
    void pocketZeroHasNoDozen() {
        assertEquals(0, RouletteWheel.dozenOf(0));
    }

    @Test
    void dozenBoundariesAreCorrect() {
        assertEquals(1, RouletteWheel.dozenOf(1));
        assertEquals(1, RouletteWheel.dozenOf(12));
        assertEquals(2, RouletteWheel.dozenOf(13));
        assertEquals(2, RouletteWheel.dozenOf(24));
        assertEquals(3, RouletteWheel.dozenOf(25));
        assertEquals(3, RouletteWheel.dozenOf(36));
    }

    @Test
    void dozenOfRejectsOutOfRangePocket() {
        assertThrows(IllegalArgumentException.class, () -> RouletteWheel.dozenOf(-1));
        assertThrows(IllegalArgumentException.class, () -> RouletteWheel.dozenOf(37));
    }

    @Test
    void wheelOrderIsPlainAscendingNumericOrder() {
        List<Integer> order = RouletteWheel.wheelOrder();
        assertEquals(37, order.size());
        for (int pocket = 0; pocket <= 36; pocket++) {
            assertEquals(pocket, order.get(pocket), "measured board order is ascending, not the European sequence");
        }
    }

    @Test
    void spinAlwaysReturnsAValidPocket() {
        Random rng = new Random(1234);
        for (int i = 0; i < 1000; i++) {
            int pocket = RouletteWheel.spin(rng);
            assertTrue(pocket >= 0 && pocket <= 36, "pocket out of range: " + pocket);
        }
    }

    @Test
    void spinIsDeterministicForAPinnedSeed() {
        int first = RouletteWheel.spin(new Random(42));
        int second = RouletteWheel.spin(new Random(42));
        assertEquals(first, second, "the same seed must draw the same pocket");
    }
}
