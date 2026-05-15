package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import me.beeliebub.tweaks.protection.RegionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class RegionLoaderTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final RegionLoader loader = new RegionLoader(Logger.getLogger("test"));

    private static void write(Path file, String yaml) throws Exception {
        Files.writeString(file, yaml);
    }

    @Test
    void createsMissingDirectoryAndReturnsZero(@TempDir Path tmp) {
        File missing = tmp.resolve("does-not-exist").toFile();
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(missing, cache);

        assertEquals(0, loaded);
        assertTrue(missing.isDirectory());
        assertTrue(cache.isEmpty());
    }

    @Test
    void loadsWellFormedRegion(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("home.yml"), """
                id: home
                owner: %s
                members:
                  - %s
                flags:
                  - PVP
                  - EXPLOSION
                """.formatted(OWNER, MEMBER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);

        assertEquals(1, loaded);
        Region r = cache.get("home");
        assertNotNull(r);
        assertEquals(OWNER, r.owner());
        assertEquals(java.util.List.of(MEMBER), r.members());
        assertTrue(r.hasFlag(RegionFlag.PVP));
        assertTrue(r.hasFlag(RegionFlag.EXPLOSION));
    }

    @Test
    void walksSubdirectories(@TempDir Path tmp) throws Exception {
        Path admin = tmp.resolve("admin");
        Files.createDirectories(admin);
        write(admin.resolve("spawn.yml"), """
                id: spawn
                owner: %s
                """.formatted(OWNER));
        write(tmp.resolve("home.yml"), """
                id: home
                owner: %s
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);

        assertEquals(2, loaded);
        assertTrue(cache.containsKey("home"));
        assertTrue(cache.containsKey("spawn"));
    }

    @Test
    void skipsFilesMissingId(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("bad.yml"), """
                owner: %s
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);

        assertEquals(0, loaded);
        assertTrue(cache.isEmpty());
    }

    @Test
    void skipsFilesMissingOwner(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("bad.yml"), """
                id: ghost
                """);
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);

        assertEquals(0, loaded);
        assertTrue(cache.isEmpty());
    }

    @Test
    void skipsFilesWithMalformedOwnerUuid(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("bad.yml"), """
                id: ghost
                owner: not-a-uuid
                """);
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);

        assertEquals(0, loaded);
        assertTrue(cache.isEmpty());
    }

    @Test
    void dropsUnknownFlagsAndBadMemberUuidsButKeepsRegion(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("home.yml"), """
                id: home
                owner: %s
                members:
                  - %s
                  - not-a-uuid
                flags:
                  - PVP
                  - WALK_ON_GRASS
                """.formatted(OWNER, MEMBER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);

        assertEquals(1, loaded);
        Region r = cache.get("home");
        assertEquals(java.util.List.of(MEMBER), r.members());
        assertTrue(r.hasFlag(RegionFlag.PVP));
        assertFalse(r.hasFlag(RegionFlag.EXPLOSION));
    }

    @Test
    void duplicateIdsAcrossFilesKeepFirstLoaded(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("a_home.yml"), """
                id: home
                owner: %s
                flags:
                  - PVP
                """.formatted(OWNER));
        write(tmp.resolve("b_home.yml"), """
                id: home
                owner: %s
                flags:
                  - EXPLOSION
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);

        assertEquals(1, loaded);
        Region r = cache.get("home");
        assertEquals(1, r.flags().size());
        assertTrue(r.hasFlag(RegionFlag.PVP));
    }

    @Test
    void emptyDirectoryReturnsZero(@TempDir Path tmp) {
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();
        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(0, loaded);
    }

    @Test
    void ignoresNonYmlFiles(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("notes.txt"), "id: home");
        write(tmp.resolve("home.yml"), """
                id: home
                owner: %s
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);

        assertEquals(1, loaded);
    }
}
