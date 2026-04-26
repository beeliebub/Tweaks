package me.beeliebub.tweaks.enchantments;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.enchantments.quality.QualityTier;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

// Applies shovel/hoe/axe actions to a 3x3 area (path making, tilling, stripping).
// Each surrounding block affected costs one durability.
public class Efficacy implements Listener {

    // Blocks that shovels can turn into dirt paths
    private static final Set<Material> SHOVELABLE = EnumSet.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.PODZOL,
            Material.MYCELIUM,
            Material.ROOTED_DIRT
    );

    // Blocks that hoes can turn into farmland
    private static final Set<Material> TILLABLE = EnumSet.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.DIRT_PATH
    );

    // Log/wood -> stripped variant mapping for axes
    private static final Map<Material, Material> STRIPPED;

    static {
        Map<Material, Material> m = new EnumMap<>(Material.class);
        m.put(Material.OAK_LOG, Material.STRIPPED_OAK_LOG);
        m.put(Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG);
        m.put(Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG);
        m.put(Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG);
        m.put(Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG);
        m.put(Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG);
        m.put(Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG);
        m.put(Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG);
        m.put(Material.PALE_OAK_LOG, Material.STRIPPED_PALE_OAK_LOG);
        m.put(Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM);
        m.put(Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM);
        m.put(Material.OAK_WOOD, Material.STRIPPED_OAK_WOOD);
        m.put(Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_WOOD);
        m.put(Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_WOOD);
        m.put(Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD);
        m.put(Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_WOOD);
        m.put(Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_WOOD);
        m.put(Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_WOOD);
        m.put(Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_WOOD);
        m.put(Material.PALE_OAK_WOOD, Material.STRIPPED_PALE_OAK_WOOD);
        m.put(Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_HYPHAE);
        m.put(Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_HYPHAE);
        m.put(Material.BAMBOO_BLOCK, Material.STRIPPED_BAMBOO_BLOCK);
        STRIPPED = Map.copyOf(m);
    }

    private final Enchantment enchantment;
    private final QualityRegistry qualityRegistry;

    public Efficacy(Tweaks plugin, QualityRegistry qualityRegistry) {
        String raw = plugin.getConfig().getString("efficacy");
        this.enchantment = resolveEnchantment(plugin, raw);
        this.qualityRegistry = qualityRegistry;
    }

    private Enchantment resolveEnchantment(Tweaks plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            plugin.getLogger().warning("No 'efficacy' key configured; efficacy enchant disabled.");
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid efficacy key '" + raw + "'; efficacy enchant disabled.");
            return null;
        }
        Enchantment resolved = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
        if (resolved == null) {
            plugin.getLogger().warning("Efficacy enchantment '" + raw + "' not found in registry; is the data pack loaded?");
        }
        return resolved;
    }

    public boolean hasEnchant(ItemStack tool) {
        return enchantment != null && tool != null && !tool.isEmpty() && tool.containsEnchantment(enchantment);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack tool = event.getItem();
        int radius = getRadius(tool);
        if (radius <= 0) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();
        Material toolType = tool.getType();

        if (isShovel(toolType)) {
            handleShovel(clicked, player, radius);
        } else if (isHoe(toolType)) {
            handleHoe(clicked, player, radius);
        } else if (isAxe(toolType)) {
            handleAxe(clicked, event.getBlockFace(), player, radius);
        }
    }

    // Determine the area radius from the tool's efficacy enchantment.
    // Returns 1 for common (3x3), 2-5 for quality tiers, or 0 if no efficacy enchant found.
    private int getRadius(ItemStack tool) {
        if (hasEnchant(tool)) return 1;
        if (qualityRegistry != null && tool != null && !tool.isEmpty()) {
            QualityTier tier = qualityRegistry.getToolQualityTier(tool, "efficacy");
            if (tier != null) return tier.getAreaRadius();
        }
        return 0;
    }

    private void handleShovel(Block clicked, Player player, int radius) {
        if (!SHOVELABLE.contains(clicked.getType())) return;
        if (!clicked.getRelative(BlockFace.UP).getType().isAir()) return;

        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block target = clicked.getRelative(dx, 0, dz);
                if (!SHOVELABLE.contains(target.getType())) continue;
                if (!target.getRelative(BlockFace.UP).getType().isAir()) continue;
                target.setType(Material.DIRT_PATH);
                count++;
            }
        }
        if (count > 0) player.damageItemStack(EquipmentSlot.HAND, count);
    }

    private void handleHoe(Block clicked, Player player, int radius) {
        if (!TILLABLE.contains(clicked.getType())) return;
        if (!clicked.getRelative(BlockFace.UP).getType().isAir()) return;

        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block target = clicked.getRelative(dx, 0, dz);
                if (!TILLABLE.contains(target.getType())) continue;
                if (!target.getRelative(BlockFace.UP).getType().isAir()) continue;
                target.setType(Material.FARMLAND);
                count++;
            }
        }
        if (count > 0) player.damageItemStack(EquipmentSlot.HAND, count);
    }

    private void handleAxe(Block clicked, BlockFace face, Player player, int radius) {
        if (!STRIPPED.containsKey(clicked.getType())) return;

        int[][] axes = perpendicularAxes(face);
        int[] axisA = axes[0];
        int[] axisB = axes[1];

        int count = 0;
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                if (a == 0 && b == 0) continue;
                Block target = clicked.getRelative(
                        a * axisA[0] + b * axisB[0],
                        a * axisA[1] + b * axisB[1],
                        a * axisA[2] + b * axisB[2]
                );
                Material stripped = STRIPPED.get(target.getType());
                if (stripped == null) continue;
                stripBlock(target, stripped);
                count++;
            }
        }
        if (count > 0) player.damageItemStack(EquipmentSlot.HAND, count);
    }

    private void stripBlock(Block b, Material target) {
        BlockData original = b.getBlockData();
        Axis axis = (original instanceof Orientable orientable) ? orientable.getAxis() : null;
        b.setType(target, false);
        if (axis != null && b.getBlockData() instanceof Orientable newOrientable) {
            newOrientable.setAxis(axis);
            b.setBlockData(newOrientable);
        }
    }

    // Get the two directions perpendicular to the clicked face for the 3x3 grid
    private int[][] perpendicularAxes(BlockFace face) {
        if (face.getModY() != 0) {
            return new int[][]{{1, 0, 0}, {0, 0, 1}};
        }
        if (face.getModX() != 0) {
            return new int[][]{{0, 1, 0}, {0, 0, 1}};
        }
        return new int[][]{{1, 0, 0}, {0, 1, 0}};
    }

    private boolean isShovel(Material m) {
        return switch (m) {
            case WOODEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL, GOLDEN_SHOVEL, DIAMOND_SHOVEL, NETHERITE_SHOVEL -> true;
            default -> false;
        };
    }

    private boolean isAxe(Material m) {
        return switch (m) {
            case WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> true;
            default -> false;
        };
    }

    private boolean isHoe(Material m) {
        return switch (m) {
            case WOODEN_HOE, STONE_HOE, IRON_HOE, GOLDEN_HOE, DIAMOND_HOE, NETHERITE_HOE -> true;
            default -> false;
        };
    }
}