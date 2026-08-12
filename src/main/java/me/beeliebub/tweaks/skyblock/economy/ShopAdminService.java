package me.beeliebub.tweaks.skyblock.economy;

import me.beeliebub.tweaks.skyblock.admin.SkyblockRegistryBackup;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Dialog-free shop catalog authoring operations. */
public final class ShopAdminService {
    private final ShopCatalog catalog;
    private final SkyblockRegistryBackup backup;
    public ShopAdminService(ShopCatalog catalog) { this(catalog, null); }
    public ShopAdminService(ShopCatalog catalog, JavaPlugin plugin) {
        this.catalog = Objects.requireNonNull(catalog);
        this.backup = plugin == null ? null : new SkyblockRegistryBackup(plugin, "shop.yml");
    }
    public EditResult set(Material material, String category, double buy, double sell) {
        if (buy < 0 && buy != -1.0d || sell < 0 && sell != -1.0d) {
            return new EditResult(false, "shop prices must be -1 or non-negative");
        }
        if (buy == -1.0d && sell == -1.0d) {
            return new EditResult(false, "at least one shop direction must be enabled");
        }
        try {
            catalog.set(new ShopCatalog.Entry(material, category, buy, sell));
            return new EditResult(true, "saved", catalog.saveAsync());
        } catch (RuntimeException error) {
            return new EditResult(false, error.getMessage() == null ? "shop entry rejected" : error.getMessage());
        }
    }
    public DeleteResult delete(Material material) {
        return deleteDetailed(material);
    }

    public DeleteResult deleteDetailed(Material material) {
        if (backup != null) {
            try { backup.writeNow(); }
            catch (RuntimeException error) { return new DeleteResult(false, "registry backup failed"); }
        }
        boolean removed = catalog.remove(material);
        return removed
                ? new DeleteResult(true, "deleted", catalog.saveAsync())
                : new DeleteResult(false, "unknown material");
    }

    public record EditResult(boolean success, String message, CompletableFuture<Void> persistence) {
        public EditResult(boolean success, String message) {
            this(success, message, CompletableFuture.completedFuture(null));
        }

        public EditResult {
            persistence = persistence == null ? CompletableFuture.completedFuture(null) : persistence;
        }
    }

    public record DeleteResult(boolean success, String message, CompletableFuture<Void> persistence) {
        public DeleteResult(boolean success, String message) {
            this(success, message, CompletableFuture.completedFuture(null));
        }

        public DeleteResult {
            persistence = persistence == null ? CompletableFuture.completedFuture(null) : persistence;
        }
    }
}
