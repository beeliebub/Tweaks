package me.beeliebub.tweaks.enchantments.quality;

import me.beeliebub.tweaks.enchantments.Telekinesis;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Handles drops for quality Silk Touch variants.
 * <p>
 * Quality silk variants (jass:*_silk_touch) are separate registry entries from vanilla
 * silk_touch, so vanilla's silk-touch loot logic does NOT fire for tools that only have a
 * quality variant. This listener restores vanilla silk drops on every silk-touchable block,
 * and on top of that grants the tier-specific bonus drops below.
 * <p>
 * Tier-specific bonuses (block drops itself):
 * - Uncommon+: Dirt Path
 * - Rare+:     Farmland
 * - Epic+:     Reinforced Deepslate
 * - Legendary: Budding Amethyst
 * <p>
 * Runs at LOWEST priority so Smelter (LOW) sees event.isDropItems() == false and steps aside —
 * silk takes precedence over smelter when both apply to the same block.
 */
public class SilkTouchQualityListener implements Listener {

    /**
     * Maps every vanilla silk-touch-affected block to the material it drops under silk touch.
     * <p>
     * This is the deterministic lookup that replaces the old {@code block.getDrops(syntheticSilk)}
     * approach. The old approach called {@code block.getDrops(tool)} as a baseline and compared
     * materials — but for gravel that baseline is non-deterministic (sometimes flint, sometimes
     * gravel), so the "are they the same?" check would randomly return true and skip the silk
     * override, letting the random flint drop through.
     * <p>
     * Blocks deliberately EXCLUDED from this map (i.e. silk-touch has no distinguishable effect
     * on drops, so vanilla behavior passes through unchanged):
     * - DIRT, SAND, RED_SAND, GRAVEL (sand and dirt drop themselves anyway; gravel IS included
     *   because silk touch suppresses the flint roll — see entry below)
     * - All planks, slabs, stairs, walls, fences — drop themselves regardless of silk
     * - All wool, concrete, terracotta, sandstone, bricks — drop themselves regardless
     * - All ore blocks that were already "drop-self" under silk touch are included explicitly
     * - COBBLESTONE drops cobblestone with or without silk; not in map
     * - Blocks that require silk touch but drop themselves (glass, ice) ARE included
     * - DIRT excluded: silk touch has no effect (drops dirt either way)
     * - SAND, RED_SAND: drop themselves regardless — excluded
     * - COBBLESTONE_SLAB, all cobblestone variants: drop themselves — excluded
     * - NETHER_WART_BLOCK, WARPED_WART_BLOCK: drop themselves — excluded
     */
    static final Map<Material, Material> SILK_DROP_MAP;

    static {
        EnumMap<Material, Material> m = new EnumMap<>(Material.class);

        // --- Blocks that would otherwise drop a different item without silk touch ---

        // Stone → cobblestone without silk; stone with silk
        m.put(Material.STONE, Material.STONE);
        m.put(Material.DEEPSLATE, Material.DEEPSLATE);

        // Gravel → may drop flint without silk; gravel with silk
        m.put(Material.GRAVEL, Material.GRAVEL);

        // Grass / biome surface → dirt/mycelium/podzol without silk
        m.put(Material.GRASS_BLOCK,   Material.GRASS_BLOCK);
        m.put(Material.MYCELIUM,      Material.MYCELIUM);
        m.put(Material.PODZOL,        Material.PODZOL);

        // Glowstone → glowstone dust without silk; glowstone block with silk
        m.put(Material.GLOWSTONE, Material.GLOWSTONE);

        // Sea Lantern → prismarine crystals without silk; sea lantern with silk
        m.put(Material.SEA_LANTERN, Material.SEA_LANTERN);

        // Ice variants — melt in warm biomes / break into nothing without silk
        m.put(Material.ICE,        Material.ICE);
        m.put(Material.PACKED_ICE, Material.PACKED_ICE);
        m.put(Material.BLUE_ICE,   Material.BLUE_ICE);

        // Glass / stained glass — drop nothing without silk
        m.put(Material.GLASS,              Material.GLASS);
        m.put(Material.GLASS_PANE,         Material.GLASS_PANE);
        m.put(Material.WHITE_STAINED_GLASS,       Material.WHITE_STAINED_GLASS);
        m.put(Material.ORANGE_STAINED_GLASS,      Material.ORANGE_STAINED_GLASS);
        m.put(Material.MAGENTA_STAINED_GLASS,     Material.MAGENTA_STAINED_GLASS);
        m.put(Material.LIGHT_BLUE_STAINED_GLASS,  Material.LIGHT_BLUE_STAINED_GLASS);
        m.put(Material.YELLOW_STAINED_GLASS,      Material.YELLOW_STAINED_GLASS);
        m.put(Material.LIME_STAINED_GLASS,        Material.LIME_STAINED_GLASS);
        m.put(Material.PINK_STAINED_GLASS,        Material.PINK_STAINED_GLASS);
        m.put(Material.GRAY_STAINED_GLASS,        Material.GRAY_STAINED_GLASS);
        m.put(Material.LIGHT_GRAY_STAINED_GLASS,  Material.LIGHT_GRAY_STAINED_GLASS);
        m.put(Material.CYAN_STAINED_GLASS,        Material.CYAN_STAINED_GLASS);
        m.put(Material.PURPLE_STAINED_GLASS,      Material.PURPLE_STAINED_GLASS);
        m.put(Material.BLUE_STAINED_GLASS,        Material.BLUE_STAINED_GLASS);
        m.put(Material.BROWN_STAINED_GLASS,       Material.BROWN_STAINED_GLASS);
        m.put(Material.GREEN_STAINED_GLASS,       Material.GREEN_STAINED_GLASS);
        m.put(Material.RED_STAINED_GLASS,         Material.RED_STAINED_GLASS);
        m.put(Material.BLACK_STAINED_GLASS,       Material.BLACK_STAINED_GLASS);
        m.put(Material.WHITE_STAINED_GLASS_PANE,       Material.WHITE_STAINED_GLASS_PANE);
        m.put(Material.ORANGE_STAINED_GLASS_PANE,      Material.ORANGE_STAINED_GLASS_PANE);
        m.put(Material.MAGENTA_STAINED_GLASS_PANE,     Material.MAGENTA_STAINED_GLASS_PANE);
        m.put(Material.LIGHT_BLUE_STAINED_GLASS_PANE,  Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        m.put(Material.YELLOW_STAINED_GLASS_PANE,      Material.YELLOW_STAINED_GLASS_PANE);
        m.put(Material.LIME_STAINED_GLASS_PANE,        Material.LIME_STAINED_GLASS_PANE);
        m.put(Material.PINK_STAINED_GLASS_PANE,        Material.PINK_STAINED_GLASS_PANE);
        m.put(Material.GRAY_STAINED_GLASS_PANE,        Material.GRAY_STAINED_GLASS_PANE);
        m.put(Material.LIGHT_GRAY_STAINED_GLASS_PANE,  Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        m.put(Material.CYAN_STAINED_GLASS_PANE,        Material.CYAN_STAINED_GLASS_PANE);
        m.put(Material.PURPLE_STAINED_GLASS_PANE,      Material.PURPLE_STAINED_GLASS_PANE);
        m.put(Material.BLUE_STAINED_GLASS_PANE,        Material.BLUE_STAINED_GLASS_PANE);
        m.put(Material.BROWN_STAINED_GLASS_PANE,       Material.BROWN_STAINED_GLASS_PANE);
        m.put(Material.GREEN_STAINED_GLASS_PANE,       Material.GREEN_STAINED_GLASS_PANE);
        m.put(Material.RED_STAINED_GLASS_PANE,         Material.RED_STAINED_GLASS_PANE);
        m.put(Material.BLACK_STAINED_GLASS_PANE,       Material.BLACK_STAINED_GLASS_PANE);

        // Leaves — drop nothing (or sapling/apple) without silk; leaf block with silk
        m.put(Material.OAK_LEAVES,               Material.OAK_LEAVES);
        m.put(Material.SPRUCE_LEAVES,             Material.SPRUCE_LEAVES);
        m.put(Material.BIRCH_LEAVES,              Material.BIRCH_LEAVES);
        m.put(Material.JUNGLE_LEAVES,             Material.JUNGLE_LEAVES);
        m.put(Material.ACACIA_LEAVES,             Material.ACACIA_LEAVES);
        m.put(Material.DARK_OAK_LEAVES,           Material.DARK_OAK_LEAVES);
        m.put(Material.MANGROVE_LEAVES,           Material.MANGROVE_LEAVES);
        m.put(Material.CHERRY_LEAVES,             Material.CHERRY_LEAVES);
        m.put(Material.AZALEA_LEAVES,             Material.AZALEA_LEAVES);
        m.put(Material.FLOWERING_AZALEA_LEAVES,   Material.FLOWERING_AZALEA_LEAVES);

        // Mushroom blocks — drop mushroom items without silk; full block with silk
        m.put(Material.BROWN_MUSHROOM_BLOCK, Material.BROWN_MUSHROOM_BLOCK);
        m.put(Material.RED_MUSHROOM_BLOCK,   Material.RED_MUSHROOM_BLOCK);
        m.put(Material.MUSHROOM_STEM,        Material.MUSHROOM_STEM);

        // Ore blocks — drop gems/ingots without silk; ore block with silk
        m.put(Material.COAL_ORE,            Material.COAL_ORE);
        m.put(Material.DEEPSLATE_COAL_ORE,  Material.DEEPSLATE_COAL_ORE);
        m.put(Material.IRON_ORE,            Material.IRON_ORE);
        m.put(Material.DEEPSLATE_IRON_ORE,  Material.DEEPSLATE_IRON_ORE);
        m.put(Material.COPPER_ORE,          Material.COPPER_ORE);
        m.put(Material.DEEPSLATE_COPPER_ORE,Material.DEEPSLATE_COPPER_ORE);
        m.put(Material.GOLD_ORE,            Material.GOLD_ORE);
        m.put(Material.DEEPSLATE_GOLD_ORE,  Material.DEEPSLATE_GOLD_ORE);
        m.put(Material.REDSTONE_ORE,        Material.REDSTONE_ORE);
        m.put(Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE);
        m.put(Material.LAPIS_ORE,           Material.LAPIS_ORE);
        m.put(Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
        m.put(Material.DIAMOND_ORE,         Material.DIAMOND_ORE);
        m.put(Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
        m.put(Material.EMERALD_ORE,         Material.EMERALD_ORE);
        m.put(Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
        m.put(Material.NETHER_GOLD_ORE,     Material.NETHER_GOLD_ORE);
        m.put(Material.NETHER_QUARTZ_ORE,   Material.NETHER_QUARTZ_ORE);

        // Ender chest → 8 obsidian without silk; ender chest with silk
        m.put(Material.ENDER_CHEST, Material.ENDER_CHEST);

        // Spawner — drops nothing without silk; spawner with silk
        m.put(Material.SPAWNER, Material.SPAWNER);

        // Bookshelf → 3 books without silk; bookshelf with silk
        m.put(Material.BOOKSHELF, Material.BOOKSHELF);

        // Melon — melon blocks drop slices; silk touch drops block
        m.put(Material.MELON, Material.MELON);

        // Infested stone — drops clean stone variant without silk; infested with silk
        m.put(Material.INFESTED_STONE,                 Material.INFESTED_STONE);
        m.put(Material.INFESTED_COBBLESTONE,           Material.INFESTED_COBBLESTONE);
        m.put(Material.INFESTED_STONE_BRICKS,          Material.INFESTED_STONE_BRICKS);
        m.put(Material.INFESTED_MOSSY_STONE_BRICKS,    Material.INFESTED_MOSSY_STONE_BRICKS);
        m.put(Material.INFESTED_CRACKED_STONE_BRICKS,  Material.INFESTED_CRACKED_STONE_BRICKS);
        m.put(Material.INFESTED_CHISELED_STONE_BRICKS, Material.INFESTED_CHISELED_STONE_BRICKS);
        m.put(Material.INFESTED_DEEPSLATE,             Material.INFESTED_DEEPSLATE);

        // Coral / coral blocks — drop dead variants (or nothing) without silk; live with silk
        m.put(Material.TUBE_CORAL_BLOCK,    Material.TUBE_CORAL_BLOCK);
        m.put(Material.BRAIN_CORAL_BLOCK,   Material.BRAIN_CORAL_BLOCK);
        m.put(Material.BUBBLE_CORAL_BLOCK,  Material.BUBBLE_CORAL_BLOCK);
        m.put(Material.FIRE_CORAL_BLOCK,    Material.FIRE_CORAL_BLOCK);
        m.put(Material.HORN_CORAL_BLOCK,    Material.HORN_CORAL_BLOCK);
        m.put(Material.TUBE_CORAL,          Material.TUBE_CORAL);
        m.put(Material.BRAIN_CORAL,         Material.BRAIN_CORAL);
        m.put(Material.BUBBLE_CORAL,        Material.BUBBLE_CORAL);
        m.put(Material.FIRE_CORAL,          Material.FIRE_CORAL);
        m.put(Material.HORN_CORAL,          Material.HORN_CORAL);
        m.put(Material.TUBE_CORAL_FAN,      Material.TUBE_CORAL_FAN);
        m.put(Material.BRAIN_CORAL_FAN,     Material.BRAIN_CORAL_FAN);
        m.put(Material.BUBBLE_CORAL_FAN,    Material.BUBBLE_CORAL_FAN);
        m.put(Material.FIRE_CORAL_FAN,      Material.FIRE_CORAL_FAN);
        m.put(Material.HORN_CORAL_FAN,      Material.HORN_CORAL_FAN);

        // Amethyst cluster varieties — silk drops cluster; normal drops shards
        m.put(Material.AMETHYST_CLUSTER,        Material.AMETHYST_CLUSTER);
        m.put(Material.LARGE_AMETHYST_BUD,      Material.LARGE_AMETHYST_BUD);
        m.put(Material.MEDIUM_AMETHYST_BUD,     Material.MEDIUM_AMETHYST_BUD);
        m.put(Material.SMALL_AMETHYST_BUD,      Material.SMALL_AMETHYST_BUD);

        // Sculk family — drop XP or nothing without silk; block with silk
        m.put(Material.SCULK,          Material.SCULK);
        m.put(Material.SCULK_CATALYST, Material.SCULK_CATALYST);
        m.put(Material.SCULK_SENSOR,   Material.SCULK_SENSOR);
        m.put(Material.SCULK_SHRIEKER, Material.SCULK_SHRIEKER);
        m.put(Material.SCULK_VEIN,     Material.SCULK_VEIN);

        // Clay — drops 4 clay balls without silk; block with silk
        m.put(Material.CLAY, Material.CLAY);

        // Snow — drops snowballs without silk; snow layer/block with silk
        m.put(Material.SNOW,       Material.SNOW);
        m.put(Material.SNOW_BLOCK, Material.SNOW_BLOCK);

        // Cobweb — drops string without silk; cobweb block with silk
        m.put(Material.COBWEB, Material.COBWEB);

        // Nylium — drops netherrack without silk; nylium block with silk
        m.put(Material.CRIMSON_NYLIUM, Material.CRIMSON_NYLIUM);
        m.put(Material.WARPED_NYLIUM,  Material.WARPED_NYLIUM);

        // Gilded Blackstone — 10% chance gold nuggets without silk; always itself with silk
        m.put(Material.GILDED_BLACKSTONE, Material.GILDED_BLACKSTONE);

        // Bee Nest / Beehive — drop nothing without silk; block with silk (bees preserved)
        m.put(Material.BEE_NEST, Material.BEE_NEST);
        m.put(Material.BEEHIVE,  Material.BEEHIVE);

        // Campfire / Soul Campfire — drop charcoal/soul soil without silk; block with silk
        m.put(Material.CAMPFIRE,      Material.CAMPFIRE);
        m.put(Material.SOUL_CAMPFIRE, Material.SOUL_CAMPFIRE);

        // Turtle Egg — drops nothing without silk; egg with silk
        m.put(Material.TURTLE_EGG, Material.TURTLE_EGG);

        // Chiseled Bookshelf — drops nothing without silk; block with silk
        m.put(Material.CHISELED_BOOKSHELF, Material.CHISELED_BOOKSHELF);

        // Dead coral blocks — drop nothing without silk; dead coral block with silk
        m.put(Material.DEAD_TUBE_CORAL_BLOCK,   Material.DEAD_TUBE_CORAL_BLOCK);
        m.put(Material.DEAD_BRAIN_CORAL_BLOCK,  Material.DEAD_BRAIN_CORAL_BLOCK);
        m.put(Material.DEAD_BUBBLE_CORAL_BLOCK, Material.DEAD_BUBBLE_CORAL_BLOCK);
        m.put(Material.DEAD_FIRE_CORAL_BLOCK,   Material.DEAD_FIRE_CORAL_BLOCK);
        m.put(Material.DEAD_HORN_CORAL_BLOCK,   Material.DEAD_HORN_CORAL_BLOCK);

        // Dead coral plants — drop nothing without silk; dead coral with silk
        m.put(Material.DEAD_TUBE_CORAL,   Material.DEAD_TUBE_CORAL);
        m.put(Material.DEAD_BRAIN_CORAL,  Material.DEAD_BRAIN_CORAL);
        m.put(Material.DEAD_BUBBLE_CORAL, Material.DEAD_BUBBLE_CORAL);
        m.put(Material.DEAD_FIRE_CORAL,   Material.DEAD_FIRE_CORAL);
        m.put(Material.DEAD_HORN_CORAL,   Material.DEAD_HORN_CORAL);

        // Dead coral fans — drop nothing without silk; dead coral fan with silk
        m.put(Material.DEAD_TUBE_CORAL_FAN,   Material.DEAD_TUBE_CORAL_FAN);
        m.put(Material.DEAD_BRAIN_CORAL_FAN,  Material.DEAD_BRAIN_CORAL_FAN);
        m.put(Material.DEAD_BUBBLE_CORAL_FAN, Material.DEAD_BUBBLE_CORAL_FAN);
        m.put(Material.DEAD_FIRE_CORAL_FAN,   Material.DEAD_FIRE_CORAL_FAN);
        m.put(Material.DEAD_HORN_CORAL_FAN,   Material.DEAD_HORN_CORAL_FAN);

        // Wall coral fans — wall-placed block; silk touch drops the corresponding floor fan item
        m.put(Material.TUBE_CORAL_WALL_FAN,   Material.TUBE_CORAL_FAN);
        m.put(Material.BRAIN_CORAL_WALL_FAN,  Material.BRAIN_CORAL_FAN);
        m.put(Material.BUBBLE_CORAL_WALL_FAN, Material.BUBBLE_CORAL_FAN);
        m.put(Material.FIRE_CORAL_WALL_FAN,   Material.FIRE_CORAL_FAN);
        m.put(Material.HORN_CORAL_WALL_FAN,   Material.HORN_CORAL_FAN);

        // Dead wall coral fans — wall-placed block; silk touch drops the corresponding dead floor fan item
        m.put(Material.DEAD_TUBE_CORAL_WALL_FAN,   Material.DEAD_TUBE_CORAL_FAN);
        m.put(Material.DEAD_BRAIN_CORAL_WALL_FAN,  Material.DEAD_BRAIN_CORAL_FAN);
        m.put(Material.DEAD_BUBBLE_CORAL_WALL_FAN, Material.DEAD_BUBBLE_CORAL_FAN);
        m.put(Material.DEAD_FIRE_CORAL_WALL_FAN,   Material.DEAD_FIRE_CORAL_FAN);
        m.put(Material.DEAD_HORN_CORAL_WALL_FAN,   Material.DEAD_HORN_CORAL_FAN);

        // Nether wart block / shroomlight — drop themselves regardless; NOT included
        // (no distinction between silk and non-silk drops)

        SILK_DROP_MAP = m;
    }

    /**
     * Returns the material that a block of the given type drops under silk touch,
     * or {@code null} if silk touch has no distinguishable effect on this block's drops
     * (i.e. it is not in {@link #SILK_DROP_MAP}).
     * <p>
     * This is package-accessible so tests can verify map completeness without needing
     * an instance.
     */
    public static Material silkMaterialFor(Material blockType) {
        return SILK_DROP_MAP.get(blockType);
    }

    private final QualityRegistry registry;
    private final Telekinesis telekinesis;
    private final ResourceHunt resourceHunt;

    public SilkTouchQualityListener(QualityRegistry registry, Telekinesis telekinesis, ResourceHunt resourceHunt) {
        this.registry = registry;
        this.telekinesis = telekinesis;
        this.resourceHunt = resourceHunt;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.isDropItems()) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.isEmpty()) return;

        QualityTier tier = registry.getToolQualityTier(tool, "silk_touch");
        if (tier == null) return;

        Block block = event.getBlock();

        Collection<ItemStack> finalDrops;
        Material bonus = getQualitySilkTouchDrop(block, tool);
        if (bonus != null) {
            finalDrops = List.of(new ItemStack(bonus));
        } else if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            // Vanilla silk_touch already produces correct silk drops; let BlockDropItemEvent
            // run untouched so ResourceHunt and Telekinesis pick them up there.
            return;
        } else {
            Collection<ItemStack> silkDrops = computeSilkDrops(block);
            // Only override when the block is in SILK_DROP_MAP — otherwise leave vanilla
            // drops untouched (non-silk-affected blocks like dirt or wood pass through).
            if (silkDrops == null) return;
            finalDrops = silkDrops;
        }

        event.setDropItems(false);

        // Credit drops toward Resource Hunt before the block becomes air — recordExternalDrops
        // reads the placed-by-player taint marker off the chunk by block position, and we want
        // to consume it while the block still resolves to its real coordinates.
        if (resourceHunt != null) {
            resourceHunt.recordExternalDrops(player, block, finalDrops);
        }

        boolean useTelekinesis = telekinesis != null && telekinesis.hasEnchant(tool);
        if (useTelekinesis) {
            for (ItemStack drop : finalDrops) {
                telekinesis.giveOrDrop(player, block, drop);
            }
        } else {
            for (ItemStack drop : finalDrops) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }
        }
    }

    /**
     * Determines if a block should drop a tier-specific bonus based on quality Silk Touch.
     * @return The material to drop, or null if no special quality drop applies.
     */
    public Material getQualitySilkTouchDrop(Block block, ItemStack tool) {
        QualityTier tier = registry.getToolQualityTier(tool, "silk_touch");
        if (tier == null) return null;

        Material type = block.getType();
        if (type == Material.BUDDING_AMETHYST && tier == QualityTier.LEGENDARY) {
            return Material.BUDDING_AMETHYST;
        }
        if (type == Material.REINFORCED_DEEPSLATE && tier.ordinal() >= QualityTier.EPIC.ordinal()) {
            return Material.REINFORCED_DEEPSLATE;
        }
        if (type == Material.FARMLAND && tier.ordinal() >= QualityTier.RARE.ordinal()) {
            return Material.FARMLAND;
        }
        if (type == Material.DIRT_PATH && tier.ordinal() >= QualityTier.UNCOMMON.ordinal()) {
            return Material.DIRT_PATH;
        }
        return null;
    }

    /**
     * Applies quality Silk Touch logic to a manually-collected drop list (used by Tunneller).
     * Returns the tier bonus when applicable, otherwise vanilla silk drops when the block is
     * silk-touchable, otherwise the original drops unchanged.
     */
    public Collection<ItemStack> applySilkQuality(Block block, ItemStack tool, Player player,
                                                  Collection<ItemStack> baseDrops) {
        QualityTier tier = registry.getToolQualityTier(tool, "silk_touch");
        if (tier == null) return baseDrops;

        Material bonus = getQualitySilkTouchDrop(block, tool);
        if (bonus != null) return List.of(new ItemStack(bonus));

        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) return baseDrops;

        Collection<ItemStack> silkDrops = computeSilkDrops(block);
        return silkDrops != null ? silkDrops : baseDrops;
    }

    /**
     * Returns a single-item drop list for {@code block} based on {@link #SILK_DROP_MAP},
     * or {@code null} if the block type is not in the map (meaning silk touch has no
     * distinguishable effect on its drops and vanilla behaviour should be left untouched).
     * <p>
     * This is an O(1) {@link EnumMap} lookup. It is intentionally deterministic — unlike the
     * previous implementation that called {@code block.getDrops(tool)} as a baseline and
     * compared, which was non-deterministic for gravel (random flint roll) and could silently
     * skip the silk override when the random baseline happened to match the silk result.
     */
    public Collection<ItemStack> computeSilkDrops(Block block) {
        Material silkMaterial = SILK_DROP_MAP.get(block.getType());
        if (silkMaterial == null) return null;
        return List.of(new ItemStack(silkMaterial));
    }
}