package me.beeliebub.tweaks.tests.enchantments.quality;

import me.beeliebub.tweaks.enchantments.Telekinesis;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import me.beeliebub.tweaks.enchantments.quality.SilkTouchQualityListener;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for SilkTouchQualityListener using the deterministic SILK_DROP_MAP approach.
 * <p>
 * These tests do NOT rely on block.getDrops() — the new implementation only calls
 * block.getType(), which is trivially mockable. This ensures gravel, leaves, glowstone, and
 * any other block with randomised normal drops always resolve correctly.
 */
class SilkTouchQualityListenerTest {

    private QualityRegistry registry;
    private SilkTouchQualityListener listener;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        registry = mock(QualityRegistry.class);
        // Default: no quality silk touch on any tool
        when(registry.getToolQualityTier(any(), eq("silk_touch"))).thenReturn(null);

        listener = new SilkTouchQualityListener(registry, null, null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -------------------------------------------------------------------------
    // SILK_DROP_MAP correctness — deterministic unit tests
    // -------------------------------------------------------------------------

    @Test
    void silkMapContainsGravel_alwaysDropsGravel() {
        // Root cause: gravel normal-drop may be flint. Map must hard-code gravel → gravel.
        assertEquals(Material.GRAVEL, SilkTouchQualityListener.silkMaterialFor(Material.GRAVEL),
                "gravel silk-touch drop must always be GRAVEL, never FLINT");
    }

    @Test
    void silkMapContainsStone_dropsStoneNotCobblestone() {
        assertEquals(Material.STONE, SilkTouchQualityListener.silkMaterialFor(Material.STONE),
                "stone silk-touch drop must be STONE, not COBBLESTONE");
    }

    @Test
    void silkMapContainsGrassBlock_dropsGrassBlockNotDirt() {
        assertEquals(Material.GRASS_BLOCK, SilkTouchQualityListener.silkMaterialFor(Material.GRASS_BLOCK));
    }

    @Test
    void silkMapContainsGlowstone_dropsGlowstoneNotDust() {
        assertEquals(Material.GLOWSTONE, SilkTouchQualityListener.silkMaterialFor(Material.GLOWSTONE),
                "glowstone silk-touch drop must be GLOWSTONE, not GLOWSTONE_DUST");
    }

    @Test
    void silkMapContainsOakLeaves_dropsLeafBlockNotSaplingOrApple() {
        assertEquals(Material.OAK_LEAVES, SilkTouchQualityListener.silkMaterialFor(Material.OAK_LEAVES),
                "leaves with silk touch must drop the leaf block itself");
    }

    @Test
    void silkMapContainsSeaLantern_dropsSeaLanternNotCrystals() {
        assertEquals(Material.SEA_LANTERN, SilkTouchQualityListener.silkMaterialFor(Material.SEA_LANTERN));
    }

    @Test
    void silkMapContainsDiamondOre_dropsOreBlockNotGem() {
        assertEquals(Material.DIAMOND_ORE, SilkTouchQualityListener.silkMaterialFor(Material.DIAMOND_ORE));
        assertEquals(Material.DEEPSLATE_DIAMOND_ORE,
                SilkTouchQualityListener.silkMaterialFor(Material.DEEPSLATE_DIAMOND_ORE));
    }

    @Test
    void silkMapContainsIronOre_dropsOreBlock() {
        assertEquals(Material.IRON_ORE, SilkTouchQualityListener.silkMaterialFor(Material.IRON_ORE));
    }

    @Test
    void silkMapContainsIce_dropsIce() {
        assertEquals(Material.ICE, SilkTouchQualityListener.silkMaterialFor(Material.ICE));
        assertEquals(Material.PACKED_ICE, SilkTouchQualityListener.silkMaterialFor(Material.PACKED_ICE));
        assertEquals(Material.BLUE_ICE, SilkTouchQualityListener.silkMaterialFor(Material.BLUE_ICE));
    }

    @Test
    void silkMapContainsEnderChest_dropsEnderChestNotObsidian() {
        assertEquals(Material.ENDER_CHEST, SilkTouchQualityListener.silkMaterialFor(Material.ENDER_CHEST));
    }

    @Test
    void silkMapContainsBookshelf_dropsBookshelfNotBooks() {
        assertEquals(Material.BOOKSHELF, SilkTouchQualityListener.silkMaterialFor(Material.BOOKSHELF));
    }

    @Test
    void silkMapContainsSpawner_dropsSpawnerNotNothing() {
        assertEquals(Material.SPAWNER, SilkTouchQualityListener.silkMaterialFor(Material.SPAWNER));
    }

    @Test
    void silkMapContainsGlass_dropsGlassNotNothing() {
        assertEquals(Material.GLASS, SilkTouchQualityListener.silkMaterialFor(Material.GLASS));
        assertEquals(Material.RED_STAINED_GLASS,
                SilkTouchQualityListener.silkMaterialFor(Material.RED_STAINED_GLASS));
    }

    @Test
    void silkMapDoesNotContainDirt_notSilkDistinct() {
        // Dirt drops dirt both with and without silk — not in the map.
        assertNull(SilkTouchQualityListener.silkMaterialFor(Material.DIRT),
                "DIRT is not silk-distinct — should not appear in SILK_DROP_MAP");
    }

    @Test
    void silkMapDoesNotContainCobblestoneSlab_notSilkDistinct() {
        assertNull(SilkTouchQualityListener.silkMaterialFor(Material.COBBLESTONE_SLAB));
    }

    // -------------------------------------------------------------------------
    // SILK_DROP_MAP correctness — merged from the now-retired
    // tests/enchantments/SilkTouchQualityListenerTest.java (wrong package depth;
    // SilkTouchQualityListener lives in enchantments.quality, not enchantments).
    // -------------------------------------------------------------------------

    @Test
    void deepslate_returnsDeepslateSelf() {
        assertEquals(Material.DEEPSLATE, SilkTouchQualityListener.silkMaterialFor(Material.DEEPSLATE));
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
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for " + m);
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
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for " + m);
        }
    }

    @Test
    void mushroomBlocks_returnSelf() {
        assertEquals(Material.BROWN_MUSHROOM_BLOCK, SilkTouchQualityListener.silkMaterialFor(Material.BROWN_MUSHROOM_BLOCK));
        assertEquals(Material.RED_MUSHROOM_BLOCK, SilkTouchQualityListener.silkMaterialFor(Material.RED_MUSHROOM_BLOCK));
        assertEquals(Material.MUSHROOM_STEM, SilkTouchQualityListener.silkMaterialFor(Material.MUSHROOM_STEM));
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
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for ore " + m);
        }
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
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for infested block " + m);
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
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for coral block " + m);
        }
    }

    @Test
    void liveCoralPlants_returnSelf() {
        List<Material> corals = List.of(
                Material.TUBE_CORAL, Material.BRAIN_CORAL,
                Material.BUBBLE_CORAL, Material.FIRE_CORAL, Material.HORN_CORAL
        );
        for (Material m : corals) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for coral plant " + m);
        }
    }

    @Test
    void liveCoralFans_returnSelf() {
        List<Material> fans = List.of(
                Material.TUBE_CORAL_FAN, Material.BRAIN_CORAL_FAN,
                Material.BUBBLE_CORAL_FAN, Material.FIRE_CORAL_FAN, Material.HORN_CORAL_FAN
        );
        for (Material m : fans) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for coral fan " + m);
        }
    }

    @Test
    void deadCoralBlocks_returnSelf() {
        List<Material> deadBlocks = List.of(
                Material.DEAD_TUBE_CORAL_BLOCK, Material.DEAD_BRAIN_CORAL_BLOCK,
                Material.DEAD_BUBBLE_CORAL_BLOCK, Material.DEAD_FIRE_CORAL_BLOCK,
                Material.DEAD_HORN_CORAL_BLOCK
        );
        for (Material m : deadBlocks) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for dead coral block " + m);
        }
    }

    @Test
    void deadCoralPlants_returnSelf() {
        List<Material> deadCorals = List.of(
                Material.DEAD_TUBE_CORAL, Material.DEAD_BRAIN_CORAL,
                Material.DEAD_BUBBLE_CORAL, Material.DEAD_FIRE_CORAL, Material.DEAD_HORN_CORAL
        );
        for (Material m : deadCorals) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for dead coral plant " + m);
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
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for dead coral fan " + m);
        }
    }

    @Test
    void wallCoralFans_returnFloorCoralFanItem() {
        assertEquals(Material.TUBE_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.TUBE_CORAL_WALL_FAN));
        assertEquals(Material.BRAIN_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.BRAIN_CORAL_WALL_FAN));
        assertEquals(Material.BUBBLE_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.BUBBLE_CORAL_WALL_FAN));
        assertEquals(Material.FIRE_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.FIRE_CORAL_WALL_FAN));
        assertEquals(Material.HORN_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.HORN_CORAL_WALL_FAN));
    }

    @Test
    void deadWallCoralFans_returnDeadFloorCoralFanItem() {
        assertEquals(Material.DEAD_TUBE_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.DEAD_TUBE_CORAL_WALL_FAN));
        assertEquals(Material.DEAD_BRAIN_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.DEAD_BRAIN_CORAL_WALL_FAN));
        assertEquals(Material.DEAD_BUBBLE_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.DEAD_BUBBLE_CORAL_WALL_FAN));
        assertEquals(Material.DEAD_FIRE_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.DEAD_FIRE_CORAL_WALL_FAN));
        assertEquals(Material.DEAD_HORN_CORAL_FAN, SilkTouchQualityListener.silkMaterialFor(Material.DEAD_HORN_CORAL_WALL_FAN));
    }

    @Test
    void amethystBuds_returnSelf() {
        List<Material> buds = List.of(
                Material.AMETHYST_CLUSTER, Material.LARGE_AMETHYST_BUD,
                Material.MEDIUM_AMETHYST_BUD, Material.SMALL_AMETHYST_BUD
        );
        for (Material m : buds) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for amethyst bud " + m);
        }
    }

    @Test
    void sculkFamily_returnSelf() {
        List<Material> sculk = List.of(
                Material.SCULK, Material.SCULK_CATALYST,
                Material.SCULK_SENSOR, Material.SCULK_SHRIEKER, Material.SCULK_VEIN
        );
        for (Material m : sculk) {
            assertEquals(m, SilkTouchQualityListener.silkMaterialFor(m), "Expected self-drop for sculk block " + m);
        }
    }

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
        assertEquals(Material.WARPED_NYLIUM, SilkTouchQualityListener.silkMaterialFor(Material.WARPED_NYLIUM));
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
    void snifferEgg_notInMap_dropsItselfWithoutSilk() {
        assertNull(SilkTouchQualityListener.silkMaterialFor(Material.SNIFFER_EGG),
                "SNIFFER_EGG should not be in SILK_DROP_MAP (drops itself without silk touch)");
    }

    @Test
    void tintedGlass_notInMap_dropsItselfWithoutSilk() {
        assertNull(SilkTouchQualityListener.silkMaterialFor(Material.TINTED_GLASS),
                "TINTED_GLASS should not be in SILK_DROP_MAP (drops itself with any tool)");
    }

    @Test
    void cobblestone_notInMap() {
        assertNull(SilkTouchQualityListener.silkMaterialFor(Material.COBBLESTONE),
                "COBBLESTONE drops itself regardless of silk touch");
    }

    // -------------------------------------------------------------------------
    // computeSilkDrops — block-level test
    // -------------------------------------------------------------------------

    @Test
    void computeSilkDrops_gravelReturnsGravel_deterministically() {
        Block gravel = mock(Block.class);
        when(gravel.getType()).thenReturn(Material.GRAVEL);

        Collection<ItemStack> drops = listener.computeSilkDrops(gravel);

        assertNotNull(drops, "computeSilkDrops must not return null for a silk-touchable block");
        assertEquals(1, drops.size());
        assertEquals(Material.GRAVEL, drops.iterator().next().getType());
    }

    @Test
    void computeSilkDrops_stoneShovelBreaksGravel_neverFlint() {
        // The bug: shovel breaking gravel with quality silk should drop gravel, not flint.
        // Calling this 1000 times mimics what the event would do — result must be deterministic.
        Block gravel = mock(Block.class);
        when(gravel.getType()).thenReturn(Material.GRAVEL);

        for (int i = 0; i < 1000; i++) {
            final int iteration = i;
            Collection<ItemStack> drops = listener.computeSilkDrops(gravel);
            assertNotNull(drops);
            drops.forEach(stack -> assertEquals(Material.GRAVEL, stack.getType(),
                    "quality silk touch on gravel must always drop GRAVEL, never FLINT (iteration " + iteration + ")"));
        }
    }

    @Test
    void computeSilkDrops_pickaxeBreaksStone_neverCobblestone() {
        Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);

        for (int i = 0; i < 100; i++) {
            final int iteration = i;
            Collection<ItemStack> drops = listener.computeSilkDrops(stone);
            assertNotNull(drops);
            drops.forEach(stack -> assertEquals(Material.STONE, stack.getType(),
                    "quality silk touch on stone must always drop STONE, never COBBLESTONE (iteration " + iteration + ")"));
        }
    }

    @Test
    void computeSilkDrops_nonSilkDistinctBlockReturnsNull() {
        // Sand drops sand both ways — not in the map, so computeSilkDrops returns null,
        // meaning the event handler leaves vanilla drops untouched.
        Block sand = mock(Block.class);
        when(sand.getType()).thenReturn(Material.SAND);

        assertNull(listener.computeSilkDrops(sand),
                "SAND is not silk-distinct — computeSilkDrops must return null to leave vanilla drops alone");
    }

    @Test
    void computeSilkDrops_nonSilkDistinctDirtReturnsNull() {
        Block dirt = mock(Block.class);
        when(dirt.getType()).thenReturn(Material.DIRT);

        assertNull(listener.computeSilkDrops(dirt));
    }

    // -------------------------------------------------------------------------
    // onBlockBreak event handler — integration-style tests via pure Mockito
    // -------------------------------------------------------------------------

    /**
     * Builds a fully wired BlockBreakEvent mock with a player holding the given tool
     * and breaking the given block material.
     */
    private BlockBreakEvent makeBreakEvent(Material blockMaterial, ItemStack tool) {
        World world = mock(World.class);
        Location loc = mock(Location.class);

        Block block = mock(Block.class);
        when(block.getType()).thenReturn(blockMaterial);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(loc);

        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.getItemInMainHand()).thenReturn(tool);

        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(inv);

        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.isDropItems()).thenReturn(true);
        when(event.getPlayer()).thenReturn(player);
        when(event.getBlock()).thenReturn(block);

        return event;
    }

    @Test
    void onBlockBreak_qualitySilkShovelOnGravel_setsDropItemsFalseAndDropsGravel() {
        // Player has quality silk touch shovel (RARE tier), breaks gravel.
        // Must call event.setDropItems(false) and drop GRAVEL into the world (no Telekinesis).
        ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
        when(registry.getToolQualityTier(shovel, "silk_touch")).thenReturn(QualityTier.RARE);

        BlockBreakEvent event = makeBreakEvent(Material.GRAVEL, shovel);
        Block block = event.getBlock();

        listener.onBlockBreak(event);

        verify(event).setDropItems(false);
        // World.dropItemNaturally should be called with a GRAVEL stack
        verify(block.getWorld()).dropItemNaturally(
                eq(block.getLocation()),
                argThat(stack -> stack != null && stack.getType() == Material.GRAVEL)
        );
    }

    @Test
    void onBlockBreak_qualitySilkPickaxeOnGravel_setsDropItemsFalseAndDropsGravel() {
        // Same assertion as shovel test — the fix must work regardless of tool type.
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        when(registry.getToolQualityTier(pickaxe, "silk_touch")).thenReturn(QualityTier.UNCOMMON);

        BlockBreakEvent event = makeBreakEvent(Material.GRAVEL, pickaxe);
        Block block = event.getBlock();

        listener.onBlockBreak(event);

        verify(event).setDropItems(false);
        verify(block.getWorld()).dropItemNaturally(
                eq(block.getLocation()),
                argThat(stack -> stack != null && stack.getType() == Material.GRAVEL)
        );
    }

    @Test
    void onBlockBreak_qualitySilkPickaxeOnStone_dropsStoneNotCobblestone() {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        when(registry.getToolQualityTier(pickaxe, "silk_touch")).thenReturn(QualityTier.EPIC);

        BlockBreakEvent event = makeBreakEvent(Material.STONE, pickaxe);
        Block block = event.getBlock();

        listener.onBlockBreak(event);

        verify(event).setDropItems(false);
        verify(block.getWorld()).dropItemNaturally(
                eq(block.getLocation()),
                argThat(stack -> stack != null && stack.getType() == Material.STONE)
        );
        verify(block.getWorld(), never()).dropItemNaturally(
                any(),
                argThat(stack -> stack != null && stack.getType() == Material.COBBLESTONE)
        );
    }

    @Test
    void onBlockBreak_qualitySilkOnSand_doesNotOverrideVanillaDrops() {
        // Sand drops sand both with and without silk — SILK_DROP_MAP does not contain sand.
        // The event handler must NOT call setDropItems(false) and must leave vanilla alone.
        ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
        when(registry.getToolQualityTier(shovel, "silk_touch")).thenReturn(QualityTier.RARE);

        BlockBreakEvent event = makeBreakEvent(Material.SAND, shovel);

        listener.onBlockBreak(event);

        verify(event, never()).setDropItems(false);
    }

    @Test
    void onBlockBreak_noQualitySilkTool_doesNothing() {
        // Tool has no quality silk touch — listener must return early without touching event.
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        when(registry.getToolQualityTier(pickaxe, "silk_touch")).thenReturn(null);

        BlockBreakEvent event = makeBreakEvent(Material.STONE, pickaxe);

        listener.onBlockBreak(event);

        verify(event, never()).setDropItems(false);
    }

    @Test
    void onBlockBreak_emptyHand_doesNothing() {
        // Use a mocked ItemStack whose isEmpty() returns true — avoids AIR Material edge cases.
        ItemStack empty = mock(ItemStack.class);
        when(empty.isEmpty()).thenReturn(true);

        BlockBreakEvent event = makeBreakEvent(Material.GRAVEL, empty);

        listener.onBlockBreak(event);

        verify(event, never()).setDropItems(false);
    }

    @Test
    void onBlockBreak_toolAlsoHasVanillaSilkTouch_deferToVanillaDrops() {
        // When the tool already carries vanilla silk_touch, the listener must return early
        // and let BlockDropItemEvent handle it — no setDropItems(false) call.
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        pickaxe.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        when(registry.getToolQualityTier(pickaxe, "silk_touch")).thenReturn(QualityTier.RARE);

        BlockBreakEvent event = makeBreakEvent(Material.STONE, pickaxe);

        listener.onBlockBreak(event);

        verify(event, never()).setDropItems(false);
    }

    @Test
    void onBlockBreak_qualitySilkWithTelekinesis_routesDropsToInventoryNotWorld() {
        Telekinesis telekinesis = mock(Telekinesis.class);
        SilkTouchQualityListener listenWithTk = new SilkTouchQualityListener(registry, telekinesis, null);

        ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
        when(registry.getToolQualityTier(shovel, "silk_touch")).thenReturn(QualityTier.UNCOMMON);
        when(telekinesis.hasEnchant(shovel)).thenReturn(true);

        BlockBreakEvent event = makeBreakEvent(Material.GRAVEL, shovel);
        Block block = event.getBlock();
        Player player = event.getPlayer();

        listenWithTk.onBlockBreak(event);

        verify(event).setDropItems(false);
        // Telekinesis.giveOrDrop should be called with a GRAVEL stack
        verify(telekinesis).giveOrDrop(
                eq(player),
                eq(block),
                argThat(stack -> stack != null && stack.getType() == Material.GRAVEL)
        );
        // World.dropItemNaturally must NOT be called when Telekinesis routes items
        verify(block.getWorld(), never()).dropItemNaturally(any(), any());
    }

    @Test
    void onBlockBreak_qualitySilkWithResourceHunt_creditsDropsBeforeBreak() {
        ResourceHunt hunt = mock(ResourceHunt.class);
        SilkTouchQualityListener listenWithHunt = new SilkTouchQualityListener(registry, null, hunt);

        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        when(registry.getToolQualityTier(pickaxe, "silk_touch")).thenReturn(QualityTier.LEGENDARY);

        BlockBreakEvent event = makeBreakEvent(Material.GRAVEL, pickaxe);
        Block block = event.getBlock();
        Player player = event.getPlayer();

        listenWithHunt.onBlockBreak(event);

        verify(event).setDropItems(false);
        verify(hunt).recordExternalDrops(eq(player), eq(block), argThat(drops ->
                drops != null && !drops.isEmpty() &&
                drops.iterator().next().getType() == Material.GRAVEL
        ));
    }

    // -------------------------------------------------------------------------
    // Quality-tier bonus drops (DIRT_PATH, FARMLAND, etc.)
    // -------------------------------------------------------------------------

    @Test
    void getQualitySilkTouchDrop_dirtPathAtUncommon_returnsPath() {
        ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
        when(registry.getToolQualityTier(shovel, "silk_touch")).thenReturn(QualityTier.UNCOMMON);

        Block dirtPath = mock(Block.class);
        when(dirtPath.getType()).thenReturn(Material.DIRT_PATH);

        assertEquals(Material.DIRT_PATH, listener.getQualitySilkTouchDrop(dirtPath, shovel));
    }

    @Test
    void getQualitySilkTouchDrop_farmlandAtRare_returnsFarmland() {
        ItemStack hoe = new ItemStack(Material.DIAMOND_HOE);
        when(registry.getToolQualityTier(hoe, "silk_touch")).thenReturn(QualityTier.RARE);

        Block farmland = mock(Block.class);
        when(farmland.getType()).thenReturn(Material.FARMLAND);

        assertEquals(Material.FARMLAND, listener.getQualitySilkTouchDrop(farmland, hoe));
    }

    @Test
    void getQualitySilkTouchDrop_farmlandAtUncommon_returnsNull() {
        // FARMLAND bonus requires RARE+; UNCOMMON should not grant it.
        ItemStack hoe = new ItemStack(Material.DIAMOND_HOE);
        when(registry.getToolQualityTier(hoe, "silk_touch")).thenReturn(QualityTier.UNCOMMON);

        Block farmland = mock(Block.class);
        when(farmland.getType()).thenReturn(Material.FARMLAND);

        assertNull(listener.getQualitySilkTouchDrop(farmland, hoe));
    }

    @Test
    void getQualitySilkTouchDrop_buddingAmethystAtLegendary_returnsBudding() {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        when(registry.getToolQualityTier(pickaxe, "silk_touch")).thenReturn(QualityTier.LEGENDARY);

        Block budding = mock(Block.class);
        when(budding.getType()).thenReturn(Material.BUDDING_AMETHYST);

        assertEquals(Material.BUDDING_AMETHYST, listener.getQualitySilkTouchDrop(budding, pickaxe));
    }

    @Test
    void getQualitySilkTouchDrop_buddingAmethystAtEpic_returnsNull() {
        // BUDDING_AMETHYST requires exactly LEGENDARY.
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        when(registry.getToolQualityTier(pickaxe, "silk_touch")).thenReturn(QualityTier.EPIC);

        Block budding = mock(Block.class);
        when(budding.getType()).thenReturn(Material.BUDDING_AMETHYST);

        assertNull(listener.getQualitySilkTouchDrop(budding, pickaxe));
    }

    // -------------------------------------------------------------------------
    // applySilkQuality — used by Tunneller
    // -------------------------------------------------------------------------

    @Test
    void applySilkQuality_gravelWithQualitySilk_returnsGravelNotBaseDrops() {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        when(registry.getToolQualityTier(pickaxe, "silk_touch")).thenReturn(QualityTier.UNCOMMON);

        Block gravel = mock(Block.class);
        when(gravel.getType()).thenReturn(Material.GRAVEL);

        Collection<ItemStack> baseDrops = java.util.List.of(new ItemStack(Material.FLINT));
        Player player = mock(Player.class);

        Collection<ItemStack> result = listener.applySilkQuality(gravel, pickaxe, player, baseDrops);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Material.GRAVEL, result.iterator().next().getType(),
                "Tunneller path must also yield deterministic gravel, not randomised flint");
    }

    @Test
    void applySilkQuality_sandWithQualitySilk_returnsBaseDropsUnchanged() {
        // Sand is not in SILK_DROP_MAP — base drops pass through.
        ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
        when(registry.getToolQualityTier(shovel, "silk_touch")).thenReturn(QualityTier.UNCOMMON);

        Block sand = mock(Block.class);
        when(sand.getType()).thenReturn(Material.SAND);

        Collection<ItemStack> baseDrops = java.util.List.of(new ItemStack(Material.SAND));
        Player player = mock(Player.class);

        Collection<ItemStack> result = listener.applySilkQuality(sand, shovel, player, baseDrops);

        assertSame(baseDrops, result, "non-silk-distinct block must return base drops unchanged");
    }

    @Test
    void applySilkQuality_noQualitySilkTool_returnsBaseDropsUnchanged() {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        when(registry.getToolQualityTier(pickaxe, "silk_touch")).thenReturn(null);

        Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);

        Collection<ItemStack> baseDrops = java.util.List.of(new ItemStack(Material.COBBLESTONE));
        Player player = mock(Player.class);

        Collection<ItemStack> result = listener.applySilkQuality(stone, pickaxe, player, baseDrops);

        assertSame(baseDrops, result);
    }

    // -------------------------------------------------------------------------
    // Parameterized: all leaf types
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {
            "OAK_LEAVES", "SPRUCE_LEAVES", "BIRCH_LEAVES", "JUNGLE_LEAVES",
            "ACACIA_LEAVES", "DARK_OAK_LEAVES", "MANGROVE_LEAVES",
            "CHERRY_LEAVES", "AZALEA_LEAVES", "FLOWERING_AZALEA_LEAVES"
    })
    void silkMapContainsAllLeafTypes_dropsLeafNotSaplingOrApple(Material leafMaterial) {
        assertEquals(leafMaterial, SilkTouchQualityListener.silkMaterialFor(leafMaterial),
                leafMaterial + " must map to itself in SILK_DROP_MAP");
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {
            "INFESTED_STONE", "INFESTED_COBBLESTONE", "INFESTED_STONE_BRICKS",
            "INFESTED_MOSSY_STONE_BRICKS", "INFESTED_CRACKED_STONE_BRICKS",
            "INFESTED_CHISELED_STONE_BRICKS", "INFESTED_DEEPSLATE"
    })
    void silkMapContainsInfestedBlocks_dropsCleanStoneVariant(Material infested) {
        assertNotNull(SilkTouchQualityListener.silkMaterialFor(infested),
                infested + " must have a silk-touch drop entry");
    }
}
