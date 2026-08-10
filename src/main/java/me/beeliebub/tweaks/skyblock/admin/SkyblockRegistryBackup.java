package me.beeliebub.tweaks.skyblock.admin;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Writes bounded backups before destructive registry mutations. */
public final class SkyblockRegistryBackup {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private final Path source;
    private final Path backupDirectory;
    private final String fileName;
    private final int retention;

    public SkyblockRegistryBackup(JavaPlugin plugin, String fileName) {
        this(plugin, fileName, 10);
    }

    public SkyblockRegistryBackup(JavaPlugin plugin, String fileName, int retention) {
        this(plugin, Objects.requireNonNull(plugin, "plugin").getDataFolder().toPath()
                        .resolve("skyblock").resolve(fileName), fileName, retention);
    }

    public SkyblockRegistryBackup(JavaPlugin plugin, Path source, String fileName) {
        this(plugin, source, fileName, 10);
    }

    public SkyblockRegistryBackup(JavaPlugin plugin, Path source, String fileName, int retention) {
        Objects.requireNonNull(plugin, "plugin");
        if (fileName == null || !fileName.matches("[a-z0-9_-]+\\.yml")) {
            throw new IllegalArgumentException("Invalid registry file name");
        }
        Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        this.source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        if (!this.source.startsWith(dataFolder)) throw new IllegalArgumentException("Backup source is outside plugin data");
        this.backupDirectory = dataFolder.resolve("skyblock").resolve("backups").normalize();
        this.fileName = fileName;
        this.retention = Math.max(1, retention);
    }

    public CompletableFuture<Void> writeAsync() {
        return CompletableFuture.runAsync(this::writeNow);
    }

    /** The registry file copied before a destructive mutation. */
    public Path source() {
        return source;
    }

    /** Directory retaining the bounded registry backup history. */
    public Path backupDirectory() {
        return backupDirectory;
    }

    /** Synchronous compatibility seam; callers should prefer writeAsync on a live server. */
    public void writeNow() {
        try {
            Path directory = backupDirectory;
            Files.createDirectories(directory);
            if (Files.exists(source)) {
                String timestamp = LocalDateTime.now().format(TIMESTAMP);
                Path target = directory.resolve(fileName.substring(0, fileName.length() - 4)
                        + "-" + timestamp + ".yml");
                int suffix = 1;
                while (Files.exists(target)) {
                    target = directory.resolve(fileName.substring(0, fileName.length() - 4)
                            + "-" + timestamp + "-" + suffix++ + ".yml");
                }
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
            prune(directory);
        } catch (IOException error) {
            throw new IllegalStateException("Could not back up " + fileName, error);
        }
    }

    private void prune(Path directory) throws IOException {
        List<Path> backups = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory,
                fileName.substring(0, fileName.length() - 4) + "-*.yml")) {
            for (Path path : stream) backups.add(path);
        }
        backups.sort(Comparator.comparingLong(this::lastModified).reversed());
        for (int index = retention; index < backups.size(); index++) Files.deleteIfExists(backups.get(index));
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException error) {
            return Long.MIN_VALUE;
        }
    }
}
