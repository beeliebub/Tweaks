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
//   - setExp() rounds the target (and the previous-XP basis) instead of truncating. The base is
//     reconstructed from p.getExp() (a float) and frequently lands a fraction below the true
//     integer; truncation then drops a whole orb on every read-modify-write cycle and a
//     brew-then-drink round trip lands the player one level short.
public final class ExperienceManager {

    private static int hardMaxLevel = 100_000;
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
        xpTotalToReachLevel = new int[maxLevel];
        for (int i = 0; i < xpTotalToReachLevel.length; i++) {
            xpTotalToReachLevel[i] =
                    i >= 32 ? (int) (4.5 * i * i - 162.5 * i + 2220) :
                    i >= 17 ? (int) (2.5 * i * i - 40.5 * i + 360) :
                    i * i + 6 * i;
        }
    }

    private static int xpNeededAtLevel(int level) {
        return level >= 31 ? 9 * level - 158 : level >= 16 ? 5 * level - 38 : 2 * level + 7;
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
        setExp(getCurrentFractionalXP(), amt);
    }

    private void setExp(double base, double amt) {
        // Round (not truncate) the target. base is recovered from p.getExp() (a float), so it can
        // land a hair below the true integer value — truncating then breaks round-trips: e.g.
        // brew -1395 then drink +1395 lands at 1627.999... → 1627 instead of 1628.
        int xp = (int) Math.round(Math.max(base + amt, 0));
        int prevXp = (int) Math.round(Math.max(base, 0));
        Player p = getPlayer();
        int curLvl = p.getLevel();
        int newLvl = getLevelForExp(xp);
        if (curLvl != newLvl) p.setLevel(newLvl);
        if (xp > prevXp) {
            p.setTotalExperience(p.getTotalExperience() + xp - prevXp);
        }
        double pct = (double) (xp - getXpForLevel(newLvl)) / getXpNeededToLevelUp(newLvl);
        p.setExp((float) pct);
    }

    public int getCurrentExp() {
        Player p = getPlayer();
        int lvl = p.getLevel();
        return getXpForLevel(lvl) + (int) Math.round(getXpNeededToLevelUp(lvl) * p.getExp());
    }

    private double getCurrentFractionalXP() {
        Player p = getPlayer();
        int lvl = p.getLevel();
        return getXpForLevel(lvl) + (double) (getXpNeededToLevelUp(lvl) * p.getExp());
    }

    public boolean hasExp(int amt) {
        return getCurrentExp() >= amt;
    }

    public int getLevelForExp(int exp) {
        if (exp <= 0) return 0;
        if (exp > xpTotalToReachLevel[xpTotalToReachLevel.length - 1]) {
            int newMax = calculateLevelForExp(exp) * 2;
            if (newMax > hardMaxLevel) {
                throw new IllegalArgumentException("Level for exp " + exp + " > hard max level " + hardMaxLevel);
            }
            initLookupTables(newMax);
        }
        int pos = Arrays.binarySearch(xpTotalToReachLevel, exp);
        return pos < 0 ? -pos - 2 : pos;
    }

    public int getXpNeededToLevelUp(int level) {
        if (level < 0) throw new IllegalArgumentException("Level may not be negative.");
        return xpNeededAtLevel(level);
    }

    public int getXpForLevel(int level) {
        if (level < 0 || level > hardMaxLevel) {
            throw new IllegalArgumentException("Invalid level " + level + " (must be in range 0.." + hardMaxLevel + ")");
        }
        if (level >= xpTotalToReachLevel.length) initLookupTables(level * 2);
        return xpTotalToReachLevel[level];
    }
}