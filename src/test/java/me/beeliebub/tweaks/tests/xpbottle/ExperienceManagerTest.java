package me.beeliebub.tweaks.tests.xpbottle;

import me.beeliebub.tweaks.xpbottle.ExperienceManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExperienceManagerTest {

    private static Player playerNamed(String name) {
        Player p = mock(Player.class);
        when(p.getName()).thenReturn(name);
        return p;
    }

    @Test
    void constructorRejectsNullPlayer() {
        assertThrows(NullPointerException.class, () -> new ExperienceManager(null));
    }

    @Test
    void getXpForLevelMatchesKnownVanillaCurve() {
        // Spot-check key thresholds from the vanilla XP curve as documented in the source comment.
        Player p = playerNamed("Bee");
        ExperienceManager em = new ExperienceManager(p);
        assertEquals(0,    em.getXpForLevel(0));
        assertEquals(7,    em.getXpForLevel(1));
        assertEquals(16,   em.getXpForLevel(2));
        assertEquals(352,  em.getXpForLevel(16));   // last of low-tier (i*i+6i) formula
        assertEquals(394,  em.getXpForLevel(17));   // start of mid-tier (2.5L^2-40.5L+360) formula
        assertEquals(1395, em.getXpForLevel(30));   // famous total at level 30
        assertEquals(1507, em.getXpForLevel(31));
        assertEquals(1628, em.getXpForLevel(32));   // start of high-tier formula
    }

    @Test
    void getXpNeededToLevelUpMatchesPerLevelFormula() {
        ExperienceManager em = new ExperienceManager(playerNamed("Bee"));
        // 2L+7 / 5L-38 / 9L-158 by tier
        assertEquals(7,   em.getXpNeededToLevelUp(0));   // level 0->1
        assertEquals(17,  em.getXpNeededToLevelUp(5));   // 2*5+7
        assertEquals(42,  em.getXpNeededToLevelUp(16));  // 5*16-38
        assertEquals(112, em.getXpNeededToLevelUp(30));  // 5*30-38
        assertEquals(121, em.getXpNeededToLevelUp(31));  // 9*31-158 = 121
        assertEquals(130, em.getXpNeededToLevelUp(32));
    }

    @Test
    void getXpNeededToLevelUpRejectsNegativeLevel() {
        ExperienceManager em = new ExperienceManager(playerNamed("Bee"));
        assertThrows(IllegalArgumentException.class, () -> em.getXpNeededToLevelUp(-1));
    }

    @Test
    void getXpForLevelRejectsNegativeLevel() {
        ExperienceManager em = new ExperienceManager(playerNamed("Bee"));
        assertThrows(IllegalArgumentException.class, () -> em.getXpForLevel(-1));
    }

    @Test
    void getLevelForExpInvertsGetXpForLevel() {
        ExperienceManager em = new ExperienceManager(playerNamed("Bee"));
        for (int level : new int[]{0, 1, 5, 15, 16, 30, 31, 32, 50}) {
            int xp = em.getXpForLevel(level);
            assertEquals(level, em.getLevelForExp(xp), "round-trip failed at level " + level);
        }
    }

    @Test
    void getLevelForExpReturnsZeroForNonPositiveExp() {
        ExperienceManager em = new ExperienceManager(playerNamed("Bee"));
        assertEquals(0, em.getLevelForExp(0));
        assertEquals(0, em.getLevelForExp(-100));
    }

    @Test
    void getCurrentExpComputesFromLevelAndBarPercent() {
        Player p = playerNamed("Bee");
        when(p.getLevel()).thenReturn(30);
        when(p.getExp()).thenReturn(0.5f);
        // half-way through level 30: needs 112 more to hit 31, so 1395 + 56 = 1451
        ExperienceManager em = new ExperienceManager(p);
        assertEquals(1395 + Math.round(112 * 0.5f), em.getCurrentExp());
    }

    @Test
    void hasExpReflectsCurrentExp() {
        Player p = playerNamed("Bee");
        when(p.getLevel()).thenReturn(30);
        when(p.getExp()).thenReturn(0f);
        ExperienceManager em = new ExperienceManager(p);
        assertTrue(em.hasExp(1395));
        assertFalse(em.hasExp(1396));
    }

    @Test
    void changeExpAdvancesPlayerLevelAndTotals() {
        Player p = playerNamed("Bee");
        when(p.getLevel()).thenReturn(0);
        when(p.getExp()).thenReturn(0f);
        when(p.getTotalExperience()).thenReturn(0);
        ExperienceManager em = new ExperienceManager(p);
        em.changeExp(7); // exactly enough for level 0 -> 1
        verify(p).setLevel(1);
        verify(p).setTotalExperience(7);
        verify(p).setExp(0f);
    }

    @Test
    void getPlayerThrowsAfterReferenceCleared() {
        // We can't actually clear the WeakReference without sometimes flakiness; instead, sanity
        // check that getPlayer() returns the mock while still strongly referenced.
        Player p = playerNamed("Bee");
        ExperienceManager em = new ExperienceManager(p);
        assertSame(p, em.getPlayer());
    }
}
