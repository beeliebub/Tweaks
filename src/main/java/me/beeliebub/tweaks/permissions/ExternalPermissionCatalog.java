package me.beeliebub.tweaks.permissions;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads trusted, administrator-maintained permission lists from the plugin data directory. */
public final class ExternalPermissionCatalog {

    public static final long MAX_FILE_BYTES = 256L * 1024L;
    public static final int MAX_LINES = 5_000;

    private final File directory;
    private final Logger logger;

    public ExternalPermissionCatalog(JavaPlugin plugin) {
        this(new File(plugin.getDataFolder(), "external-permissions"), plugin.getLogger());
    }

    public ExternalPermissionCatalog(File directory) {
        this(directory, Logger.getLogger(ExternalPermissionCatalog.class.getName()));
    }

    public ExternalPermissionCatalog(Path directory) {
        this(directory.toFile());
    }

    private ExternalPermissionCatalog(File directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    /** A parsed node and the description shown by the permission editor. */
    public record ExternalNode(String node, String description) {}

    /** A parsed file, exposed as one category in the permission editor. */
    public record ExternalCategory(String key, String displayName, List<ExternalNode> nodes) {
        public ExternalCategory {
            nodes = List.copyOf(nodes);
        }
    }

    /** Reads the current {@code *.txt} files in filename order. */
    public List<ExternalCategory> scan() {
        if (!directory.isDirectory()) {
            return List.of();
        }

        File[] files;
        try {
            files = directory.listFiles(file -> file.isFile()
                    && file.getName().toLowerCase(Locale.ROOT).endsWith(".txt"));
        } catch (SecurityException error) {
            logger.log(Level.WARNING, "Could not list external permission files in " + directory, error);
            return List.of();
        }
        if (files == null || files.length == 0) {
            return List.of();
        }

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(File::getName));
        List<ExternalCategory> categories = new ArrayList<>();
        for (File file : files) {
            try {
                ExternalCategory category = parse(file.toPath());
                if (category != null) {
                    categories.add(category);
                }
            } catch (Exception error) {
                logger.log(Level.WARNING,
                        "Could not read external permission file " + file.getName() + "; skipping it", error);
            }
        }
        return List.copyOf(categories);
    }

    private ExternalCategory parse(Path path) throws IOException {
        long size = Files.size(path);
        if (size > MAX_FILE_BYTES) {
            logger.warning("Skipping oversized external permission file " + path.getFileName()
                    + " (maximum is " + MAX_FILE_BYTES + " bytes)");
            return null;
        }

        String stem = fileStem(path.getFileName().toString());
        String displayName = titleCaseStem(stem);
        Map<String, ExternalNode> nodes = new LinkedHashMap<>();
        int lineNumber = 0;

        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(path), decoder))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber > MAX_LINES) {
                    logger.warning("Stopping external permission file " + path.getFileName()
                            + " after " + MAX_LINES + " lines");
                    break;
                }
                if (line.indexOf('\0') >= 0) {
                    throw new IOException("NUL byte in permission file");
                }

                String trimmed = line.trim();
                if (trimmed.regionMatches(true, 0, "# name:", 0, "# name:".length())) {
                    displayName = trimmed.substring("# name:".length()).trim();
                    continue;
                }
                if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
                    continue;
                }

                String node = trimmed;
                String description = null;
                int separator = trimmed.indexOf('|');
                if (separator >= 0) {
                    node = trimmed.substring(0, separator).trim();
                    description = trimmed.substring(separator + 1).trim();
                }
                node = node.toLowerCase(Locale.ROOT);
                if (node.isEmpty() || nodes.containsKey(node)) {
                    continue;
                }
                nodes.put(node, new ExternalNode(node,
                        description == null || description.isEmpty() ? node : description));
            }
        }

        return new ExternalCategory("external:" + stem.toLowerCase(Locale.ROOT), displayName, new ArrayList<>(nodes.values()));
    }

    private static String fileStem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot <= 0 ? filename : filename.substring(0, dot);
    }

    private static String titleCaseStem(String stem) {
        String[] words = stem.replace('-', ' ').replace('_', ' ').trim().split("\\s+");
        if (words.length == 1 && words[0].isEmpty()) {
            return stem;
        }
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            String lower = word.toLowerCase(Locale.ROOT);
            result.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
        }
        return result.toString();
    }
}
