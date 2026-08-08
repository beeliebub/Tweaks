package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RegionWriterArchiveTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static Region region(String id, String world) {
        return new Region(id, OWNER, List.of(), Map.of(), Map.of(), null,
                new Region.RegionBounds(0, 0, 0, 0), world);
    }

    @Test
    void archiveMovesFileToTimestampedWorldArchiveAndTombstonesIt(@TempDir Path tmp) throws Exception {
        RegionWriter writer = new RegionWriter(mock(Tweaks.class), tmp.toFile());
        Region region = region("home", "world");
        writer.writeNow(region);

        writer.archive(region);
        assertFalse(Files.exists(tmp.resolve("world/home.yml")));
        try (var archived = Files.list(tmp.resolve("_deleted/world"))) {
            var files = archived.filter(path -> path.getFileName().toString().endsWith(".yml")).toList();
            assertEquals(1, files.size());
            assertTrue(files.getFirst().getFileName().toString().matches("home-\\d+\\.yml"));
        }

        writer.writeNow(region);
        assertFalse(Files.exists(tmp.resolve("world/home.yml")),
                "a tombstoned region must not be recreated by a queued write");
    }

    @Test
    void archiveWithoutOnDiskFileIsSilentNoOp(@TempDir Path tmp) throws Exception {
        RegionWriter writer = new RegionWriter(mock(Tweaks.class), tmp.toFile());
        writer.archive(region("missing", "world"));
        assertFalse(Files.exists(tmp.resolve("_deleted/world/missing.yml")));
    }

    @Test
    void untombstoneReenablesWriting(@TempDir Path tmp) throws Exception {
        RegionWriter writer = new RegionWriter(mock(Tweaks.class), tmp.toFile());
        Region region = region("home", "world");
        writer.writeNow(region);
        writer.archive(region);
        writer.untombstone("world:home");

        writer.writeNow(region);

        assertTrue(Files.exists(tmp.resolve("world/home.yml")));
    }

    @Test
    void legacyLookupDoesNotReuseArchivedFile(@TempDir Path tmp) throws Exception {
        Path archive = tmp.resolve("_deleted/world/legacy.yml");
        Files.createDirectories(archive.getParent());
        Files.writeString(archive, "id: legacy\nowner: " + OWNER + "\n");

        RegionWriter writer = new RegionWriter(mock(Tweaks.class), tmp.toFile());
        writer.writeNow(new Region("legacy", OWNER, List.of(), Map.of()));

        assertTrue(Files.exists(tmp.resolve("legacy.yml")));
        assertEquals("id: legacy\nowner: " + OWNER + "\n", Files.readString(archive));
    }
}
