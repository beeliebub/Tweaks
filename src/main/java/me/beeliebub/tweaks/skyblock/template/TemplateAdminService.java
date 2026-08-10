package me.beeliebub.tweaks.skyblock.template;

import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import me.beeliebub.tweaks.skyblock.admin.SkyblockRegistryBackup;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.java.JavaPlugin;

/** Dialog-free template authoring operations. */
public final class TemplateAdminService {
    private final TemplateStore store;
    private final TypeRegistry types;
    private final SkyblockConfig config;
    private final JavaPlugin plugin;
    public TemplateAdminService(TemplateStore store) {
        this(store, null, null, null);
    }
    public TemplateAdminService(TemplateStore store, TypeRegistry types) {
        this(store, types, null, null);
    }
    public TemplateAdminService(TemplateStore store, TypeRegistry types, SkyblockConfig config) {
        this(store, types, config, null);
    }
    public TemplateAdminService(TemplateStore store, TypeRegistry types, SkyblockConfig config, JavaPlugin plugin) {
        this.store = Objects.requireNonNull(store);
        this.types = types;
        this.config = config;
        this.plugin = plugin;
    }
    public CompletableFuture<Void> save(String id, IslandTemplate template) { return store.saveAsync(id, template); }
    public IslandTemplate capture(TemplateCapture.BlockSource source, IslandGrid.ChunkBounds bounds,
                                  int minY, int maxY, String id, Island.SpawnOffset spawnOffset) {
        if (config == null) throw new IllegalStateException("Skyblock config is unavailable");
        return new TemplateCapture().capture(source, bounds, minY, maxY, config.templateMaxHeight(),
                config.templateMaxBlocks(), id, spawnOffset);
    }

    public TemplateCapture.CaptureEstimate estimate(IslandGrid.ChunkBounds bounds, int minY, int maxY) {
        if (config == null) throw new IllegalStateException("Skyblock config is unavailable");
        return new TemplateCapture().estimate(bounds, minY, maxY, config.templateMaxHeight());
    }
    public CompletableFuture<Void> delete(String id) { return store.deleteAsync(id); }
    public DeleteResult deleteChecked(String id) {
        if (types == null) return new DeleteResult(false, 0, "template reference checking is unavailable");
        long references = types.types().stream().filter(type -> id != null && id.equalsIgnoreCase(type.templateId())).count();
        if (references > 0) return new DeleteResult(false, references, "template is referenced by island types");
        if (plugin != null) {
            try { backup(id).writeNow(); }
            catch (RuntimeException error) { return new DeleteResult(false, 0, "template backup failed"); }
        }
        store.deleteAsync(id);
        return new DeleteResult(true, 0, "deleted");
    }
    public CompletableFuture<Void> deleteCheckedAsync(String id) {
        if (types == null) return CompletableFuture.failedFuture(
                new IllegalStateException("template reference checking is unavailable"));
        long references = types.types().stream().filter(type -> id != null && id.equalsIgnoreCase(type.templateId())).count();
        if (references > 0) return CompletableFuture.failedFuture(
                new IllegalStateException("template is referenced by " + references + " island type(s)"));
        CompletableFuture<Void> backupFuture = plugin == null
                ? CompletableFuture.completedFuture(null) : backup(id).writeAsync();
        return backupFuture.thenCompose(ignored -> store.deleteAsync(id));
    }
    public List<String> list() { return store.ids(); }

    private SkyblockRegistryBackup backup(String id) {
        String safeId = (id == null ? "unknown" : id.toLowerCase(Locale.ROOT))
                .replaceAll("[^a-z0-9_-]", "_");
        return new SkyblockRegistryBackup(plugin, store.filePath(id), "template-" + safeId + ".yml");
    }

    public record DeleteResult(boolean deleted, long references, String reason) { }
}
