package me.beeliebub.tweaks.skyblock.economy;

import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Buy/sell price catalog. A price of -1 disables that direction. */
public final class ShopCatalog {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<Material, Entry> entries = new LinkedHashMap<>();
    private CompletableFuture<Void> saveTail = CompletableFuture.completedFuture(null);

    public ShopCatalog(JavaPlugin plugin) {
        this(plugin, new File(plugin.getDataFolder(), "skyblock/shop.yml"));
    }

    public ShopCatalog(File file) { this(null, file); }

    private ShopCatalog(JavaPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = Objects.requireNonNull(file, "file");
    }

    public synchronized void load() { load(YamlStore.load(file)); }

    public synchronized void load(YamlConfiguration yaml) {
        entries.clear();
        for (Map<?, ?> raw : yaml.getMapList("items")) {
            Material material = Material.matchMaterial(String.valueOf(raw.get("material")));
            if (material == null || material.isAir()) continue;
            String category = raw.get("category") == null ? "general" : String.valueOf(raw.get("category"));
            double buy = number(raw.get("buyPrice"), raw.get("buy-price"), -1.0d);
            double sell = number(raw.get("sellPrice"), raw.get("sell-price"), -1.0d);
            if (!validPrice(buy) || !validPrice(sell)) continue;
            entries.put(material, new Entry(material, category, buy, sell));
        }
    }

    public synchronized void set(Entry entry) { entries.put(Objects.requireNonNull(entry, "entry").material(), entry); }
    public synchronized Optional<Entry> entry(Material material) { return Optional.ofNullable(entries.get(material)); }
    public synchronized Collection<Entry> entries() { return List.copyOf(entries.values()); }
    public synchronized List<Entry> category(String category) { return entries.values().stream().filter(e -> e.category().equalsIgnoreCase(category)).toList(); }
    public synchronized boolean remove(Material material) { return entries.remove(material) != null; }

    public synchronized CompletableFuture<Void> saveAsync() {
        if (plugin == null) {
            saveTail = CompletableFuture.failedFuture(
                    new IllegalStateException("Shop catalog has no persistence target"));
            return saveTail.copy();
        }
        List<Map<String, Object>> rows = entries.values().stream().map(entry -> Map.<String, Object>of(
                "material", entry.material().name(), "category", entry.category(),
                "buyPrice", entry.buyPrice(), "sellPrice", entry.sellPrice())).toList();
        saveTail = saveTail.handle((ignored, error) -> null).thenRunAsync(() -> {
            if (!YamlStore.saveNow(plugin, file, "skyblock shop data", yaml -> yaml.set("items", rows))) {
                throw new IllegalStateException("Shop catalog could not be saved");
            }
        });
        return saveTail.copy();
    }

    /** Returns a detached observer of the ordered save queue for bounded lifecycle waits. */
    public synchronized CompletableFuture<Void> flush() {
        return saveTail.copy();
    }

    private static double number(Object first, Object second, double fallback) {
        Object value = first != null ? first : second;
        return value == null ? fallback : Double.parseDouble(String.valueOf(value));
    }
    private static boolean validPrice(double value) { return value == -1.0d || Double.isFinite(value) && value >= 0 && value <= SkyblockEconomy.MAX_EXACT_DOUBLE_INTEGER; }

    public record Entry(Material material, String category, double buyPrice, double sellPrice) {
        public Entry {
            Objects.requireNonNull(material, "material");
            category = category == null || category.isBlank() ? "general" : category.trim().toLowerCase(Locale.ROOT);
            if (!validPrice(buyPrice) || !validPrice(sellPrice)) throw new IllegalArgumentException("Invalid shop price");
        }
        public boolean buyable() { return buyPrice >= 0; }
        public boolean sellable() { return sellPrice >= 0; }
    }
}
