package me.beeliebub.tweaks.tools.xp;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.EnchantmentResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Main-thread-published XP configuration snapshot. */
public final class XpSettings {

    private final Tweaks plugin;
    private boolean mobDropsRemove;
    private boolean customDropsEnabled;
    private int amount;
    private double cropChance;
    private double leafBreakChance;
    private double leafDecayChance;
    private double stoneChance;
    private Set<Material> stoneMaterials = Set.of();
    private Set<Material> leafMaterials = Set.of();
    private long taintTtlMillis;
    private boolean mendingEnabled;
    private int dollarsPerXp;
    private Enchantment mending;

    public XpSettings(Tweaks plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        mobDropsRemove = plugin.getConfig().getBoolean("xp.mob-drops.remove", true);
        customDropsEnabled = plugin.getConfig().getBoolean("xp.custom-drops.enabled", true);
        amount = clamp(plugin.getConfig().getInt("xp.custom-drops.amount", 1), 0, 1000);
        cropChance = clamp(plugin.getConfig().getDouble("xp.custom-drops.crop-chance-percent", 5.0));
        leafBreakChance = clamp(plugin.getConfig().getDouble("xp.custom-drops.leaf-break-chance-percent", 5.0));
        leafDecayChance = clamp(plugin.getConfig().getDouble("xp.custom-drops.leaf-decay-chance-percent", 5.0));
        stoneChance = clamp(plugin.getConfig().getDouble("xp.custom-drops.stone-chance-percent", 5.0));
        stoneMaterials = readMaterials("xp.custom-drops.stone-materials");
        leafMaterials = readMaterials("xp.custom-drops.leaf-materials");
        long days = Math.max(1L, Math.min(3650L,
                plugin.getConfig().getLong("xp.custom-drops.taint-ttl-days", 30L)));
        taintTtlMillis = days * 24L * 60L * 60L * 1000L;
        mendingEnabled = plugin.getConfig().getBoolean("xp.mending.enabled", true);
        dollarsPerXp = Math.max(0, Math.min(1000, plugin.getConfig().getInt("xp.mending.dollars-per-xp", 1)));
        mending = EnchantmentResolver.resolve(plugin, "xp.mending.enchantment-key", "mending payout");
    }

    public boolean mobDropsRemove() { return mobDropsRemove; }
    public boolean customDropsEnabled() { return customDropsEnabled; }
    public int amount() { return amount; }
    public double cropChance() { return cropChance; }
    public double leafBreakChance() { return leafBreakChance; }
    public double leafDecayChance() { return leafDecayChance; }
    public double stoneChance() { return stoneChance; }
    public Set<Material> stoneMaterials() { return stoneMaterials; }
    public Set<Material> leafMaterials() { return leafMaterials; }
    public long taintTtlMillis() { return taintTtlMillis; }
    public boolean mendingEnabled() { return mendingEnabled; }
    public int dollarsPerXp() { return dollarsPerXp; }
    public Enchantment mending() { return mending; }

    private Set<Material> readMaterials(String path) {
        EnumSet<Material> result = EnumSet.noneOf(Material.class);
        for (String raw : plugin.getConfig().getStringList(path)) {
            Material material = Material.matchMaterial(raw == null ? "" : raw);
            if (material != null && !material.isAir()) result.add(material);
        }
        return Set.copyOf(result);
    }

    private static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(100.0, value)) : 0.0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
