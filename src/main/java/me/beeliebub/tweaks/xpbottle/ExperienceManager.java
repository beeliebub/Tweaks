package me.beeliebub.tweaks.xpbottle;

import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Objects;

// Adapted from desht/dhutils' ExperienceManager (originally by nisovin, with contributions from
// comphenix). Works around Bukkit's player.giveExp()/getTotalExperience() rounding issues by
// recomputing total XP from level + bar percent and writing the exact target back.
//
// Modifications vs. dhutils:
//   - XP curve formulas updated from pre-1.8 (17/level then quadratic at 16/30) to current
//     Minecraft (2L+7 / 5L-38 / 9L-158 per-level; quadratics with breakpoints at 17 and 32).
//     The legacy curves underreported total XP — e.g. cumulative at level 30 came out to 825
//     instead of the correct 1395 — which silently broke any consumer that compared current XP
//     to a known orb cost.
//   - XP mutations round the target (and the previous-XP basis) instead of truncating. The base is
//     reconstructed from p.getExp() (a float) and frequently lands a fraction below the true
//     integer; truncation then drops a whole orb on every read-modify-write cycle and a
//     brew-then-drink round trip lands the player one level short.
public final class ExperienceManager {

    /** Bukkit stores total experience as an int, so levels beyond this point are not representable. */
    private static final int HARD_MAX_LEVEL = findHighestRepresentableLevel();
    private static int[] xpTotalToReachLevel;

    static {
        initLookupTables(25);
    }

    private final WeakReference<Player> player;
    private final String playerName;

    public ExperienceManager(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        this.player = new WeakReference<>(player);
        this.playerName = player.getName();
    }

    public Player getPlayer() {
        Player p = player.get();
        if (p == null) throw new IllegalStateException("Player " + playerName + " is not online");
        return p;
    }

    private static void initLookupTables(int maxLevel) {
        int tableSize = Math.min(Math.max(1, maxLevel), HARD_MAX_LEVEL + 1);
        xpTotalToReachLevel = new int[tableSize];
        for (int i = 0; i < xpTotalToReachLevel.length; i++) {
            xpTotalToReachLevel[i] = Math.toIntExact(xpTotalForLevel(i));
        }
    }

    private static int xpNeededAtLevel(int level) {
        long needed = level >= 31 ? 9L * level - 158 : level >= 16 ? 5L * level - 38 : 2L * level + 7;
        return Math.toIntExact(needed);
    }

    private static long xpTotalForLevel(int level) {
        if (level < 0) throw new IllegalArgumentException("Level may not be negative.");
        if (level >= 32) return (9L * level * level - 325L * level + 4440L) / 2L;
        if (level >= 17) return (5L * level * level - 81L * level + 720L) / 2L;
        return (long) level * level + 6L * level;
    }

    private static int findHighestRepresentableLevel() {
        int level = 0;
        while (xpTotalForLevel(level + 1) <= Integer.MAX_VALUE) level++;
        return level;
    }

    private static int calculateLevelForExp(int exp) {
        int level = 0;
        long cumulative = 0;
        while (true) {
            long next = cumulative + xpNeededAtLevel(level);
            if (next > exp) return level;
            cumulative = next;
            level++;
        }
    }

    public void changeExp(int amt) {
        changeExp((double) amt);
    }

    public void changeExp(double amt) {
        Player p = getPlayer();
        PreparedChange change = prepareChange(p, getCurrentFractionalXP(p), amt);
        applyChange(p, change);
    }

    /** Returns whether {@link #changeExp(double)} can complete without mutating the player. */
    public boolean canChangeExp(double amt) {
        try {
            Player p = getPlayer();
            prepareChange(p, getCurrentFractionalXP(p), amt);
            return true;
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            return false;
        }
    }

    private PreparedChange prepareChange(Player p, double base, double amt) {
        // Round (not truncate) the target. base is recovered from p.getExp() (a float), so it can
        // land a hair below the true integer value — truncating then breaks round-trips: e.g.
        // brew -1395 then drink +1395 lands at 1627.999... → 1627 instead of 1628.
        double target = Math.max(base + amt, 0);
        double previous = Math.max(base, 0);
        if (!Double.isFinite(target) || !Double.isFinite(previous)) {
            throw new IllegalArgumentException("XP target is not finite");
        }
        long roundedTarget = Math.round(target);
        long roundedPrevious = Math.round(previous);
        if (roundedTarget > Integer.MAX_VALUE || roundedPrevious > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("XP target exceeds Bukkit's integer range");
        }
        int xp = (int) roundedTarget;
        int prevXp = (int) roundedPrevious;
        int curLvl = p.getLevel();
        int newLvl = getLevelForExp(xp);
        double pct = (double) (xp - getXpForLevel(newLvl)) / getXpNeededToLevelUp(newLvl);
        long updatedTotal = (long) p.getTotalExperience() + Math.max(0, xp - prevXp);
        if (updatedTotal > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Bukkit total experience would overflow");
        }
        return new PreparedChange(xp, prevXp, newLvl, (float) pct, updatedTotal);
    }

    private void applyChange(Player p, PreparedChange change) {
        if (p.getLevel() != change.newLevel()) p.setLevel(change.newLevel());
        if (change.xp() > change.previousXp()) {
            p.setTotalExperience((int) change.updatedTotal());
        }
        p.setExp(change.progress());
    }

    private record PreparedChange(int xp, int previousXp, int newLevel, float progress, long updatedTotal) {}

    private double getCurrentFractionalXP(Player p) {
        int lvl = p.getLevel();
        return getXpForLevel(lvl) + (double) (getXpNeededToLevelUp(lvl) * p.getExp());
    }

    public int getCurrentExp() {
        Player p = getPlayer();
        int lvl = p.getLevel();
        long xp = (long) getXpForLevel(lvl)
                + Math.round(getXpNeededToLevelUp(lvl) * p.getExp());
        if (xp < 0 || xp > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Current XP exceeds Bukkit's integer range");
        }
        return (int) xp;
    }

    public boolean hasExp(int amt) {
        return getCurrentExp() >= amt;
    }

    public int getLevelForExp(int exp) {
        if (exp <= 0) return 0;
        if (exp > xpTotalToReachLevel[xpTotalToReachLevel.length - 1]) {
            int requiredLevel = calculateLevelForExp(exp);
            int newMax = Math.max(requiredLevel + 1, xpTotalToReachLevel.length * 2);
            initLookupTables(Math.min(HARD_MAX_LEVEL + 1, newMax));
        }
        int pos = Arrays.binarySearch(xpTotalToReachLevel, exp);
        return pos < 0 ? -pos - 2 : pos;
    }

    public int getXpNeededToLevelUp(int level) {
        if (level < 0 || level > HARD_MAX_LEVEL) {
            throw new IllegalArgumentException("Invalid level " + level + " (must be in range 0.."
                    + HARD_MAX_LEVEL + ")");
        }
        return xpNeededAtLevel(level);
    }

    public int getXpForLevel(int level) {
        if (level < 0 || level > HARD_MAX_LEVEL) {
            throw new IllegalArgumentException("Invalid level " + level + " (must be in range 0.."
                    + HARD_MAX_LEVEL + ")");
        }
        if (level >= xpTotalToReachLevel.length) {
            int newMax = Math.max(level + 1, xpTotalToReachLevel.length * 2);
            initLookupTables(Math.min(HARD_MAX_LEVEL + 1, newMax));
        }
        return xpTotalToReachLevel[level];
    }
}
