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
