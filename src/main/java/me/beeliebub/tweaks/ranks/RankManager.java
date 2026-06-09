package me.beeliebub.tweaks.ranks;

import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.tab.TabManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Config-driven rank manager. Loads rank definitions (name, cost, multiplier, rakeback)
 * from the {@code ranks:} block in config.yml. Rank 0 is always "Unranked" with no cost or bonus.
 * Ranks 1..10 are purchasable in order via /rankup.
 */
public class RankManager {

    public static final int MAX_RANK = 10;

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private TabManager tabManager;

    // Loaded rank definitions keyed by rank number (1..MAX_RANK).
    private final Map<Integer, RankDef> ranks = new HashMap<>();

    // Internal record for a single configured rank.
    private record RankDef(String name, double cost, double multiplier, double rakeback) {}

    public RankManager(JavaPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        load();
    }

    /**
     * Wires the TabManager so that rank-name changes immediately refresh the tab list.
     * Called by Tweaks.onEnable() after both managers are constructed.
     */
    public void setTabManager(TabManager tabManager) {
        this.tabManager = tabManager;
    }

    // ---- Config loading -----------------------------------------------------

    /**
     * (Re-)loads all rank definitions from the current config. Safe to call after
     * a config reload without restarting the server.
     */
    public void reload() {
        ranks.clear();
        load();
    }

    private void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ranks");
        if (section == null) {
            plugin.getLogger().warning("RankManager: no 'ranks' section found in config.yml — all rank lookups will return defaults.");
            return;
        }
        for (String key : section.getKeys(false)) {
            int rank;
            try {
                rank = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("RankManager: ignoring non-integer rank key '" + key + "' in config.yml");
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;
            String name = entry.getString("name", "Rank " + rank);
            double cost = entry.getDouble("cost", 0.0);
            double multiplier = entry.getDouble("multiplier", 0.0);
            double rakeback = entry.getDouble("rakeback", 0.0);
            ranks.put(rank, new RankDef(name, cost, multiplier, rakeback));
        }
    }

    // ---- Accessors ----------------------------------------------------------

    /**
     * Returns the highest configured rank number, or {@link #MAX_RANK} if ranks were loaded
     * successfully. Falls back to the loaded map size so an under-populated config doesn't
     * silently allow rank 10 purchases when only 5 ranks exist.
     */
    public int getMaxRank() {
        return ranks.isEmpty() ? MAX_RANK : ranks.keySet().stream().mapToInt(Integer::intValue).max().orElse(MAX_RANK);
    }

    /**
     * Display name for the given rank (e.g. "I", "V"). Returns "Unranked" for rank 0
     * or any rank not present in config.
     */
    public String getRankDisplayName(int rank) {
        if (rank == 0) return "Unranked";
        RankDef def = ranks.get(rank);
        return def != null ? def.name() : "Unranked";
    }

    /**
     * Cost to purchase the given rank. Returns 0.0 for rank 0 or unknown ranks.
     */
    public double getRankCost(int rank) {
        RankDef def = ranks.get(rank);
        return def != null ? def.cost() : 0.0;
    }

    /**
     * Daily login reward multiplier fraction for the given rank (e.g. 0.01 = 1%).
     * Returns 0.0 for rank 0 or unknown ranks. Name kept stable — EconomyListener calls this.
     */
    public double getRankMultiplierBonus(int rank) {
        RankDef def = ranks.get(rank);
        return def != null ? def.multiplier() : 0.0;
    }

    /**
     * Casino rakeback fraction for the given rank (e.g. 0.05 = 5%).
     * Returns 0.0 for rank 0 or unknown ranks.
     */
    public double getRankRakeback(int rank) {
        RankDef def = ranks.get(rank);
        return def != null ? def.rakeback() : 0.0;
    }

    /**
     * Convenience: returns the rakeback rate for the given player based on their current rank.
     */
    public double getCasinoRakebackRate(UUID player) {
        return getRankRakeback(economyManager.getRank(player));
    }

    // ---- Config-writing setters (admin use only) ----------------------------

    /**
     * Updates the display name for the given rank in memory and persists to config.yml.
     * No-op if the rank is outside the range 1..{@link #MAX_RANK}.
     */
    public void setRankName(int rank, String name) {
        if (rank < 1 || rank > getMaxRank()) {
            plugin.getLogger().warning("RankManager.setRankName: rank " + rank + " is out of range, ignoring.");
            return;
        }
        plugin.getConfig().set("ranks." + rank + ".name", name);
        plugin.saveConfig();
        reload();
        if (tabManager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                tabManager.refreshTab(p);
            }
        }
    }

    /**
     * Updates the purchase cost for the given rank in memory and persists to config.yml.
     * No-op if the rank is outside the range 1..{@link #MAX_RANK}.
     */
    public void setRankCost(int rank, double cost) {
        if (rank < 1 || rank > getMaxRank()) {
            plugin.getLogger().warning("RankManager.setRankCost: rank " + rank + " is out of range, ignoring.");
            return;
        }
        plugin.getConfig().set("ranks." + rank + ".cost", cost);
        plugin.saveConfig();
        reload();
    }

    /**
     * Updates the daily login reward multiplier fraction for the given rank in memory and persists to config.yml.
     * No-op if the rank is outside the range 1..{@link #MAX_RANK}.
     */
    public void setRankMultiplier(int rank, double multiplier) {
        if (rank < 1 || rank > getMaxRank()) {
            plugin.getLogger().warning("RankManager.setRankMultiplier: rank " + rank + " is out of range, ignoring.");
            return;
        }
        plugin.getConfig().set("ranks." + rank + ".multiplier", multiplier);
        plugin.saveConfig();
        reload();
    }

    /**
     * Updates the casino rakeback fraction for the given rank in memory and persists to config.yml.
     * No-op if the rank is outside the range 1..{@link #MAX_RANK}.
     */
    public void setRankRakeback(int rank, double rakeback) {
        if (rank < 1 || rank > getMaxRank()) {
            plugin.getLogger().warning("RankManager.setRankRakeback: rank " + rank + " is out of range, ignoring.");
            return;
        }
        plugin.getConfig().set("ranks." + rank + ".rakeback", rakeback);
        plugin.saveConfig();
        reload();
    }
}
