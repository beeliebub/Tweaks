package me.beeliebub.tweaks.tests.protection.region;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RegionWriterPathSafetyTest {

    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void traversalCannotEscapeRegionsDirectory(@TempDir Path tmp) {
        RegionWriter writer = new RegionWriter(mock(Tweaks.class), tmp.toFile());
        assertThrows(IOException.class, () -> writer.writeNow(region("../outside", null)));
        assertTrue(Files.notExists(tmp.getParent().resolve("outside.yml")));
    }

    @Test
    void differentlyCasedExistingFileIsNotReused(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("Base.yml"), "legacy");
        RegionWriter writer = new RegionWriter(mock(Tweaks.class), tmp.toFile());
        assertThrows(IOException.class, () -> writer.writeNow(region("base", null)));
    }

    @Test
    void worldlessRegionNeverOverwritesWorldDirectory(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("world"));
        Files.writeString(tmp.resolve("world/home.yml"), "world-file");
        new RegionWriter(mock(Tweaks.class), tmp.toFile()).writeNow(region("home", null));
        assertTrue(Files.exists(tmp.resolve("home.yml")));
        org.junit.jupiter.api.Assertions.assertEquals("world-file",
                Files.readString(tmp.resolve("world/home.yml")));
    }

    private static Region region(String id, String world) {
        return new Region(id, OWNER, List.of(), Map.of(), Map.of(), null, null, world);
    }
}
