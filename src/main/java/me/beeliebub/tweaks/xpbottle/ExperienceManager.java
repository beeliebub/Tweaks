package me.beeliebub.tweaks.xpbottle;

import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Objects;

// Adapted from desht/dhutils' ExperienceManager (originally by nisovin, with contributions from
// comphenix). Works around Bukkit's player.giveExp()/getTotalExperience() rounding issues by
// recomputing total XP from level + bar percent and writing the exact target back. Only the
// Apache Commons Validate dependency was stripped; the math is unchanged.
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
                    i >= 30 ? (int) (3.5 * i * i - 151.5 * i + 2220) :
                    i >= 16 ? (int) (1.5 * i * i - 29.5 * i + 360) :
                    17 * i;
        }
    }

    private static int calculateLevelForExp(int exp) {
        int level = 0;
        int curExp = 7;
        int incr = 10;
        while (curExp <= exp) {
            curExp += incr;
            level++;
            incr += (level % 2 == 0) ? 3 : 4;
        }
        return level;
    }

    public void changeExp(int amt) {
        changeExp((double) amt);
    }

    public void changeExp(double amt) {
        setExp(getCurrentFractionalXP(), amt);
    }

    private void setExp(double base, double amt) {
        int xp = (int) Math.max(base + amt, 0);
        Player p = getPlayer();
        int curLvl = p.getLevel();
        int newLvl = getLevelForExp(xp);
        if (curLvl != newLvl) p.setLevel(newLvl);
        if (xp > base) {
            p.setTotalExperience(p.getTotalExperience() + xp - (int) base);
        }
        double pct = (base - getXpForLevel(newLvl) + amt) / (double) getXpNeededToLevelUp(newLvl);
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
        return level > 30 ? 62 + (level - 30) * 7 : level >= 16 ? 17 + (level - 15) * 3 : 17;
    }

    public int getXpForLevel(int level) {
        if (level < 0 || level > hardMaxLevel) {
            throw new IllegalArgumentException("Invalid level " + level + " (must be in range 0.." + hardMaxLevel + ")");
        }
        if (level >= xpTotalToReachLevel.length) initLookupTables(level * 2);
        return xpTotalToReachLevel[level];
    }
}