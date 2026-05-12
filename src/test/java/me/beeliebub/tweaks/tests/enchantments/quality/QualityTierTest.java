package me.beeliebub.tweaks.tests.enchantments.quality;

import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QualityTierTest {

    @Test
    void enumDeclaresExactlyFourTiersInExpectedOrder() {
        QualityTier[] vals = QualityTier.values();
        assertArrayEquals(
                new QualityTier[]{QualityTier.UNCOMMON, QualityTier.RARE, QualityTier.EPIC, QualityTier.LEGENDARY},
                vals);
    }

    @Test
    void weightsSumToOne() {
        double sum = 0;
        for (QualityTier t : QualityTier.values()) sum += t.getWeight();
        assertEquals(1.0, sum, 1e-9, "tier weights should sum to 1.0");
    }

    @Test
    void perTierFieldValuesMatchSpec() {
        assertEquals("uncommon",  QualityTier.UNCOMMON.getPrefix());
        assertEquals("rare",      QualityTier.RARE.getPrefix());
        assertEquals("epic",      QualityTier.EPIC.getPrefix());
        assertEquals("legendary", QualityTier.LEGENDARY.getPrefix());

        assertEquals(1, QualityTier.UNCOMMON.getRerolls());
        assertEquals(2, QualityTier.RARE.getRerolls());
        assertEquals(3, QualityTier.EPIC.getRerolls());
        assertEquals(5, QualityTier.LEGENDARY.getRerolls());

        assertEquals(2, QualityTier.UNCOMMON.getAreaRadius());
        assertEquals(3, QualityTier.RARE.getAreaRadius());
        assertEquals(4, QualityTier.EPIC.getAreaRadius());
        assertEquals(5, QualityTier.LEGENDARY.getAreaRadius());
    }

    @Test
    void rollTierAlwaysReturnsNonNull() {
        for (int i = 0; i < 200; i++) {
            assertNotNull(QualityTier.rollTier());
        }
    }

    @Test
    void rollTierApproximatesDeclaredWeightsOverManyTrials() {
        Map<QualityTier, Integer> counts = new EnumMap<>(QualityTier.class);
        for (QualityTier t : QualityTier.values()) counts.put(t, 0);

        int trials = 200_000;
        for (int i = 0; i < trials; i++) counts.merge(QualityTier.rollTier(), 1, Integer::sum);

        // Allow 5 percentage points of slack on rare events; the rarer tiers naturally swing more.
        for (QualityTier t : QualityTier.values()) {
            double observed = counts.get(t) / (double) trials;
            double expected = t.getWeight();
            assertTrue(Math.abs(observed - expected) <= 0.05,
                    t + " observed " + observed + " vs expected " + expected);
        }
    }

    @Test
    void rerollsAndAreaRadiusMonotonicallyIncreaseWithRarity() {
        QualityTier prev = QualityTier.UNCOMMON;
        for (QualityTier t : new QualityTier[]{QualityTier.RARE, QualityTier.EPIC, QualityTier.LEGENDARY}) {
            assertTrue(t.getRerolls() >= prev.getRerolls(),
                    t + " rerolls (" + t.getRerolls() + ") should be >= " + prev);
            assertTrue(t.getAreaRadius() >= prev.getAreaRadius(),
                    t + " areaRadius should monotonically grow");
            prev = t;
        }
    }
}
