package me.beeliebub.tweaks.tests.enchantments;

import me.beeliebub.tweaks.enchantments.quality.SilkTouchQualityListener;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that SILK_DROP_MAP covers every vanilla silk-touch-affected block
 * and that silkMaterialFor() returns the correct drop material.
 */
class SilkTouchQualityListenerTest {

    // ------------------------------------------------------------------
    // Pre-existing entries (regression guard)
    // ------------------------------------------------------------------

    @Test
    void stone_returnsStoneSelf() {
        assertEquals(Material.STONE, SilkTouchQualityListener.silkMaterialFor(Material.STONE));
    }

    @Test
    void deepslate_returnsDeepslateSelf() {
        assertEquals(Material.DEEPSLATE, SilkTouchQualityListener.silkMaterialFor(Material.DEEPSLATE));
    }

    @Test
    void gravel_returnsGravelSelf() {
        assertEquals(Material.GRAVEL, SilkTouchQualityListener.silkMaterialFor(Material.GRAVEL));
    }

    @Test
    void glowstone_returnsGlotstoneSelf() {
        assertEquals(Material.GLOWSTONE, SilkTouchQualityListener.silkMaterialFor(Material.GLOWSTONE));
    }

    @Test
    void seaLantern_returnsSeaLanternSelf() {
        assertEquals(Material.SEA_LANTERN, SilkTouchQualityListener.silkMaterialFor(Material.SEA_LANTERN));
    }

    @Test
    void ice_returnsIceSelf() {
        assertEquals(Material.ICE, SilkTouchQualityListener.silkMaterialFor(Material.ICE));
    }

    @Test
    void packedIce_returnsPackedIceSelf() {
        assertEquals(Material.PACKED_ICE, SilkTouchQualityListener.silkMaterialFor(Material.PACKED_ICE));
    }

    @Test
    void blueIce_returnsBlueIceSelf() {
        assertEquals(Material.BLUE_ICE, SilkTouchQualityListener.silkMaterialFor(Material.BLUE_ICE));
    }

    @Test
    void glass_returnsGlassSelf() {
        assertEquals(Material.GLASS, SilkTouchQualityListener.silkMaterialFor(Material.GLASS));
    }

    @Test
    void glassPane_returnsGlassPaneSelf() {
        assertEquals(Material.GLASS_PANE, SilkTouchQualityListener.silkMaterialFor(Material.GLASS_PANE));
    }

    @Test
    void stainedGlassVariants_returnSelf() {
        List<Material> stainedGlass = List.of(
                Material.WHITE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS,
                Material.MAGENTA_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
                Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS,
                Material.PINK_STAINED_GLASS, Material.GRAY_STAINED_GLASS,
                Material.LIGHT_GRAY_STAINED_GLASS, Material.CYAN_STAINED_GLASS,
                Material.PURPLE_STAINED_GLASS, Material.BLUE_STAINED_GLASS,
                Material.BROWN_STAINED_GLASS, Material.GREEN_STAINED_GLASS,
                Material.RED_STAINED_GLASS, Material.BLACK_STAINED_GLASS
        );
        for (Material m : stainedGlass) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for " + m);
        }
    }

    @Test
    void stainedGlassPaneVariants_returnSelf() {
        List<Material> panes = List.of(
                Material.WHITE_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
                Material.MAGENTA_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
                Material.PINK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE,
                Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
                Material.BROWN_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE
        );
        for (Material m : panes) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for " + m);
        }
    }

    @Test
    void leaves_returnSelf() {
        List<Material> leaves = List.of(
                Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
                Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
                Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES,
                Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES
        );
        for (Material m : leaves) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for " + m);
        }
    }

    @Test
    void mushroomBlocks_returnSelf() {
        assertEquals(Material.BROWN_MUSHROOM_BLOCK, SilkTouchQualityListener.silkMaterialFor(Material.BROWN_MUSHROOM_BLOCK));
        assertEquals(Material.RED_MUSHROOM_BLOCK,   SilkTouchQualityListener.silkMaterialFor(Material.RED_MUSHROOM_BLOCK));
        assertEquals(Material.MUSHROOM_STEM,        SilkTouchQualityListener.silkMaterialFor(Material.MUSHROOM_STEM));
    }

    @Test
    void oreBlocks_returnSelf() {
        List<Material> ores = List.of(
                Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
                Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
                Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
                Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
                Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
                Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
                Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
                Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
                Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE
        );
        for (Material m : ores) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for ore " + m);
        }
    }

    @Test
    void enderChest_returnsEnderChestSelf() {
        assertEquals(Material.ENDER_CHEST, SilkTouchQualityListener.silkMaterialFor(Material.ENDER_CHEST));
    }

    @Test
    void spawner_returnsSpawnerSelf() {
        assertEquals(Material.SPAWNER, SilkTouchQualityListener.silkMaterialFor(Material.SPAWNER));
    }

    @Test
    void bookshelf_returnsBookshelfSelf() {
        assertEquals(Material.BOOKSHELF, SilkTouchQualityListener.silkMaterialFor(Material.BOOKSHELF));
    }

    @Test
    void melon_returnsMelonSelf() {
        assertEquals(Material.MELON, SilkTouchQualityListener.silkMaterialFor(Material.MELON));
    }

    @Test
    void infestedBlocks_returnSelf() {
        List<Material> infested = List.of(
                Material.INFESTED_STONE, Material.INFESTED_COBBLESTONE,
                Material.INFESTED_STONE_BRICKS, Material.INFESTED_MOSSY_STONE_BRICKS,
                Material.INFESTED_CRACKED_STONE_BRICKS, Material.INFESTED_CHISELED_STONE_BRICKS,
                Material.INFESTED_DEEPSLATE
        );
        for (Material m : infested) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for infested block " + m);
        }
    }

    @Test
    void liveCoralBlocks_returnSelf() {
        List<Material> coralBlocks = List.of(
                Material.TUBE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK,
                Material.BUBBLE_CORAL_BLOCK, Material.FIRE_CORAL_BLOCK,
                Material.HORN_CORAL_BLOCK
        );
        for (Material m : coralBlocks) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for coral block " + m);
        }
    }

    @Test
    void liveCoralPlants_returnSelf() {
        List<Material> corals = List.of(
                Material.TUBE_CORAL, Material.BRAIN_CORAL,
                Material.BUBBLE_CORAL, Material.FIRE_CORAL, Material.HORN_CORAL
        );
        for (Material m : corals) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for coral plant " + m);
        }
    }

    @Test
    void liveCoralFans_returnSelf() {
        List<Material> fans = List.of(
                Material.TUBE_CORAL_FAN, Material.BRAIN_CORAL_FAN,
                Material.BUBBLE_CORAL_FAN, Material.FIRE_CORAL_FAN, Material.HORN_CORAL_FAN
        );
        for (Material m : fans) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for coral fan " + m);
        }
    }

    @Test
    void amethystBuds_returnSelf() {
        List<Material> buds = List.of(
                Material.AMETHYST_CLUSTER, Material.LARGE_AMETHYST_BUD,
                Material.MEDIUM_AMETHYST_BUD, Material.SMALL_AMETHYST_BUD
        );
        for (Material m : buds) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for amethyst bud " + m);
        }
    }

    @Test
    void sculkFamily_returnSelf() {
        List<Material> sculk = List.of(
                Material.SCULK, Material.SCULK_CATALYST,
                Material.SCULK_SENSOR, Material.SCULK_SHRIEKER, Material.SCULK_VEIN
        );
        for (Material m : sculk) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for sculk block " + m);
        }
    }

    // ------------------------------------------------------------------
    // Newly added entries
    // ------------------------------------------------------------------

    @Test
    void clay_returnsClaySelf() {
        assertEquals(Material.CLAY, SilkTouchQualityListener.silkMaterialFor(Material.CLAY));
    }

    @Test
    void snow_returnsSnowSelf() {
        assertEquals(Material.SNOW, SilkTouchQualityListener.silkMaterialFor(Material.SNOW));
    }

    @Test
    void snowBlock_returnsSnowBlockSelf() {
        assertEquals(Material.SNOW_BLOCK, SilkTouchQualityListener.silkMaterialFor(Material.SNOW_BLOCK));
    }

    @Test
    void cobweb_returnsCobwebSelf() {
        assertEquals(Material.COBWEB, SilkTouchQualityListener.silkMaterialFor(Material.COBWEB));
    }

    @Test
    void nylium_returnsNyliumSelf() {
        assertEquals(Material.CRIMSON_NYLIUM, SilkTouchQualityListener.silkMaterialFor(Material.CRIMSON_NYLIUM));
        assertEquals(Material.WARPED_NYLIUM,  SilkTouchQualityListener.silkMaterialFor(Material.WARPED_NYLIUM));
    }

    @Test
    void gildedBlackstone_returnsGildedBlackstoneSelf() {
        assertEquals(Material.GILDED_BLACKSTONE, SilkTouchQualityListener.silkMaterialFor(Material.GILDED_BLACKSTONE));
    }

    @Test
    void beeNest_returnsBeeNestSelf() {
        assertEquals(Material.BEE_NEST, SilkTouchQualityListener.silkMaterialFor(Material.BEE_NEST));
    }

    @Test
    void beehive_returnsBeehiveSelf() {
        assertEquals(Material.BEEHIVE, SilkTouchQualityListener.silkMaterialFor(Material.BEEHIVE));
    }

    @Test
    void campfire_returnsCampfireSelf() {
        assertEquals(Material.CAMPFIRE, SilkTouchQualityListener.silkMaterialFor(Material.CAMPFIRE));
    }

    @Test
    void soulCampfire_returnsSoulCampfireSelf() {
        assertEquals(Material.SOUL_CAMPFIRE, SilkTouchQualityListener.silkMaterialFor(Material.SOUL_CAMPFIRE));
    }

    @Test
    void turtleEgg_returnsTurtleEggSelf() {
        assertEquals(Material.TURTLE_EGG, SilkTouchQualityListener.silkMaterialFor(Material.TURTLE_EGG));
    }

    @Test
    void chiseledBookshelf_returnsChiseledBookshelfSelf() {
        assertEquals(Material.CHISELED_BOOKSHELF, SilkTouchQualityListener.silkMaterialFor(Material.CHISELED_BOOKSHELF));
    }

    @Test
    void deadCoralBlocks_returnSelf() {
        List<Material> deadBlocks = List.of(
                Material.DEAD_TUBE_CORAL_BLOCK, Material.DEAD_BRAIN_CORAL_BLOCK,
                Material.DEAD_BUBBLE_CORAL_BLOCK, Material.DEAD_FIRE_CORAL_BLOCK,
                Material.DEAD_HORN_CORAL_BLOCK
        );
        for (Material m : deadBlocks) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for dead coral block " + m);
        }
    }

    @Test
    void deadCoralPlants_returnSelf() {
        List<Material> deadCorals = List.of(
                Material.DEAD_TUBE_CORAL, Material.DEAD_BRAIN_CORAL,
                Material.DEAD_BUBBLE_CORAL, Material.DEAD_FIRE_CORAL, Material.DEAD_HORN_CORAL
        );
        for (Material m : deadCorals) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for dead coral plant " + m);
        }
    }

    @Test
    void deadCoralFans_returnSelf() {
        List<Material> deadFans = List.of(
                Material.DEAD_TUBE_CORAL_FAN, Material.DEAD_BRAIN_CORAL_FAN,
                Material.DEAD_BUBBLE_CORAL_FAN, Material.DEAD_FIRE_CORAL_FAN,
                Material.DEAD_HORN_CORAL_FAN
        );
        for (Material m : deadFans) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m),
                    "Expected self-drop for dead coral fan " + m);
        }
    }

    @Test
    void wallCoralFans_returnFloorCoralFanItem() {
        assertEquals(Material.TUBE_CORAL_FAN,   SilkTouchQualityListener.silkMaterialFor(Material.TUBE_CORAL_WALL_FAN));
        assertEquals(Material.BRAIN_CORAL_FAN,  SilkTouchQualityListener.silkMaterialFor(Material.BRAIN_CORAL_WALL_FAN));
        assertEquals(Material.BUBBLE_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.BUBBLE_CORAL_WALL_FAN));
        assertEquals(Material.FIRE_CORAL_FAN,   SilkTouchQualityListener.silkMaterialFor(Material.FIRE_CORAL_WALL_FAN));
        assertEquals(Material.HORN_CORAL_FAN,   SilkTouchQualityListener.silkMaterialFor(Material.HORN_CORAL_WALL_FAN));
    }

    @Test
    void deadWallCoralFans_returnDeadFloorCoralFanItem() {
        assertEquals(Material.DEAD_TUBE_CORAL_FAN,   SilkTouchQualityListener.silkMaterialFor(Material.DEAD_TUBE_CORAL_WALL_FAN));
        assertEquals(Material.DEAD_BRAIN_CORAL_FAN,  SilkTouchQualityListener.silkMaterialFor(Material.DEAD_BRAIN_CORAL_WALL_FAN));
        assertEquals(Material.DEAD_BUBBLE_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.DEAD_BUBBLE_CORAL_WALL_FAN));
        assertEquals(Material.DEAD_FIRE_CORAL_FAN,   SilkTouchQualityListener.silkMaterialFor(Material.DEAD_FIRE_CORAL_WALL_FAN));
        assertEquals(Material.DEAD_HORN_CORAL_FAN,   SilkTouchQualityListener.silkMaterialFor(Material.DEAD_HORN_CORAL_WALL_FAN));
    }

    // ------------------------------------------------------------------
    // Explicit exclusions — should NOT be in the map
    // ------------------------------------------------------------------

    @Test
    void snifferEgg_notInMap_dropsItselfWithoutSilk() {
        // Sniffer egg drops itself regardless of silk touch — should NOT be in the map
        assertNull(SilkTouchQualityListener.silkMaterialFor(Material.SNIFFER_EGG),
                "SNIFFER_EGG should not be in SILK_DROP_MAP (drops itself without silk touch)");
    }

    @Test
    void tintedGlass_notInMap_dropsItselfWithoutSilk() {
        // Tinted glass drops itself with any tool — should NOT be in the map
        assertNull(SilkTouchQualityListener.silkMaterialFor(Material.TINTED_GLASS),
                "TINTED_GLASS should not be in SILK_DROP_MAP (drops itself without silk touch)");
    }

    @Test
    void dirt_notInMap() {
        assertNull(SilkTouchQualityListener.silkMaterialFor(Material.DIRT),
                "DIRT drops itself regardless of silk touch");
    }

    @Test
    void cobblestone_notInMap() {
        assertNull(SilkTouchQualityListener.silkMaterialFor(Material.COBBLESTONE),
                "COBBLESTONE drops itself regardless of silk touch");
    }
}
