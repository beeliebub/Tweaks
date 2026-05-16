package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.protection.FlagTarget;
import me.beeliebub.tweaks.protection.Region;
import me.beeliebub.tweaks.protection.RegionFlag;
import me.beeliebub.tweaks.protection.RegionLoader;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class RegionLoaderTargetedFlagsTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final RegionLoader loader = new RegionLoader(Logger.getLogger("test"));

    private static void write(Path file, String yaml) throws Exception {
        Files.writeString(file, yaml);
    }

    @Test
    void loadsTargetedMapSchema(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("spawn.yml"), """
                id: spawn
                owner: %s
                flags:
                  BLOCK_BREAK:
                    default: false
                    owner: true
                    "group:staff": true
                  PVP:
                    default: false
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);

        Region r = cache.get("spawn");
        Map<FlagTarget, Boolean> bb = r.rulesFor(RegionFlag.BLOCK_BREAK);
        assertEquals(Boolean.FALSE, bb.get(FlagTarget.DEFAULT));
        assertEquals(Boolean.TRUE, bb.get(FlagTarget.OWNER));
        assertEquals(Boolean.TRUE, bb.get(FlagTarget.group("staff")));
        assertEquals(Boolean.FALSE, r.rulesFor(RegionFlag.PVP).get(FlagTarget.DEFAULT));
    }

    @Test
    void legacyListSchemaTranslatesToDefaultTrueRules(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("home.yml"), """
                id: home
                owner: %s
                flags:
                  - PVP
                  - EXPLOSION
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);

        Region r = cache.get("home");
        assertEquals(Boolean.TRUE, r.rulesFor(RegionFlag.PVP).get(FlagTarget.DEFAULT));
        assertEquals(Boolean.TRUE, r.rulesFor(RegionFlag.EXPLOSION).get(FlagTarget.DEFAULT));
        assertTrue(r.hasFlag(RegionFlag.PVP));
        assertTrue(r.hasFlag(RegionFlag.EXPLOSION));
    }

    @Test
    void unknownTargetKeysAreDroppedWithoutFailingTheLoad(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("home.yml"), """
                id: home
                owner: %s
                flags:
                  PVP:
                    default: true
                    bogus: true
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);

        Region r = cache.get("home");
        Map<FlagTarget, Boolean> rules = r.rulesFor(RegionFlag.PVP);
        assertEquals(1, rules.size());
        assertEquals(Boolean.TRUE, rules.get(FlagTarget.DEFAULT));
    }

    @Test
    void unknownFlagInTargetedSchemaIsSkipped(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("home.yml"), """
                id: home
                owner: %s
                flags:
                  PVP:
                    default: true
                  WALK_ON_GRASS:
                    default: true
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);
        Region r = cache.get("home");
        assertTrue(r.hasFlag(RegionFlag.PVP));
        assertEquals(1, r.flagRules().size());
    }

    @Test
    void materialFlagsAreParsedFromDedicatedSection(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("spawn.yml"), """
                id: spawn
                owner: %s
                material_flags:
                  ALLOW_BLOCK_BREAK:
                    - GRASS_BLOCK
                    - DIRT
                  DENY_BLOCK_BREAK:
                    - BEDROCK
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);

        Region r = cache.get("spawn");
        assertEquals(java.util.Set.of(Material.GRASS_BLOCK, Material.DIRT),
                r.materialsFor(RegionFlag.ALLOW_BLOCK_BREAK));
        assertEquals(java.util.Set.of(Material.BEDROCK),
                r.materialsFor(RegionFlag.DENY_BLOCK_BREAK));
    }

    @Test
    void unknownMaterialsAreDroppedButRegionLoads(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("spawn.yml"), """
                id: spawn
                owner: %s
                material_flags:
                  ALLOW_BLOCK_BREAK:
                    - GRASS_BLOCK
                    - NOT_A_REAL_MATERIAL
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);
        assertEquals(java.util.Set.of(Material.GRASS_BLOCK),
                cache.get("spawn").materialsFor(RegionFlag.ALLOW_BLOCK_BREAK));
    }

    @Test
    void booleanFlagUnderMaterialFlagsSectionIsRejected(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("spawn.yml"), """
                id: spawn
                owner: %s
                material_flags:
                  PVP:
                    - GRASS_BLOCK
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);
        assertTrue(cache.get("spawn").materialFlags().isEmpty());
    }

    @Test
    void materialFlagUnderFlagsSectionIsRejected(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("spawn.yml"), """
                id: spawn
                owner: %s
                flags:
                  ALLOW_BLOCK_BREAK:
                    default: true
                  PVP:
                    default: true
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);
        Region r = cache.get("spawn");
        // Material flag rejected, but PVP boolean rule still present.
        assertTrue(r.rulesFor(RegionFlag.ALLOW_BLOCK_BREAK).isEmpty());
        assertEquals(Boolean.TRUE, r.rulesFor(RegionFlag.PVP).get(FlagTarget.DEFAULT));
    }

    @Test
    void parentFieldIsParsedWhenPresent(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("sub.yml"), """
                id: sub
                owner: %s
                parent: spawn
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);

        Region r = cache.get("sub");
        assertEquals("spawn", r.parentId());
        assertTrue(r.hasParent());
    }

    @Test
    void selfReferentialParentIsStripped(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("home.yml"), """
                id: home
                owner: %s
                parent: home
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);
        assertNull(cache.get("home").parentId());
    }

    @Test
    void missingParentFieldLeavesRegionTopLevel(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("home.yml"), """
                id: home
                owner: %s
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);
        assertFalse(cache.get("home").hasParent());
    }

    @Test
    void nonBooleanTargetValuesAreDropped(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("home.yml"), """
                id: home
                owner: %s
                flags:
                  PVP:
                    default: maybe
                    owner: true
                """.formatted(OWNER));
        ConcurrentHashMap<String, Region> cache = new ConcurrentHashMap<>();

        int loaded = loader.load(tmp.toFile(), cache);
        assertEquals(1, loaded);
        Map<FlagTarget, Boolean> rules = cache.get("home").rulesFor(RegionFlag.PVP);
        assertEquals(1, rules.size());
        assertEquals(Boolean.TRUE, rules.get(FlagTarget.OWNER));
    }
}
