package me.beeliebub.tweaks.skyblock.template;

import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Asynchronous YAML facade for encoded island templates. */
public final class TemplateStore {
    private final JavaPlugin plugin;
    private final YamlStore store;
    private final File directory;

    public TemplateStore(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.directory = new File(plugin.getDataFolder(), "skyblock/templates");
        this.store = new YamlStore(plugin, directory, "skyblock template data");
    }

    public CompletableFuture<Void> saveAsync(String id, IslandTemplate template) {
        validate(id);
        Objects.requireNonNull(template, "template");
        return store.writeAsync(id, yaml -> {
            yaml.set("payload", TemplateCodec.encode(template));
            yaml.set("version", 2);
        });
    }

    public CompletableFuture<IslandTemplate> loadAsync(String id) {
        validate(id);
        return store.readOrderedAsync(id).thenApply(yaml -> decode(id, yaml));
    }

    public IslandTemplate load(String id) {
        validate(id);
        return decode(id, YamlStore.load(filePath(id).toFile()));
    }

    public List<String> ids() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (File file : files) ids.add(file.getName().substring(0, file.getName().length() - 4));
        return List.copyOf(ids);
    }

    public CompletableFuture<Void> deleteAsync(String id) {
        validate(id);
        return store.deleteAsync(id);
    }

    Path filePath(String id) {
        validate(id);
        return new File(directory, id + ".yml").toPath();
    }

    private static IslandTemplate decode(String id, YamlConfiguration yaml) {
        String payload = yaml.getString("payload");
        if (payload == null) throw new IllegalArgumentException("Template " + id + " has no payload");
        IslandTemplate template = TemplateCodec.decode(payload);
        return template.id().isBlank() ? new IslandTemplate(id, template.width(), template.height(), template.depth(),
                template.palette(), template.blockIndices(), template.blockEntities(), template.spawnOffset()) : template;
    }

    private static void validate(String id) {
        if (id == null || id.isBlank() || id.contains("/") || id.contains("\\") || id.contains("..")) {
            throw new IllegalArgumentException("Invalid template id");
        }
    }
}
