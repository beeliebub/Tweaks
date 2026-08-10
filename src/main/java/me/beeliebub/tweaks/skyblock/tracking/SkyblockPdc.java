package me.beeliebub.tweaks.skyblock.tracking;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Collection;

/** Owns Skyblock's exact item and chunk PDC compatibility contracts. */
public final class SkyblockPdc {

    public static final String NAMESPACE = "skyblock";
    private static final int Y_OFFSET = 2_048;
    private final NamespacedKey countedKey = NamespacedKey.fromString(NAMESPACE + ":skyblock_counted");
    private final NamespacedKey purchasedKey = NamespacedKey.fromString(NAMESPACE + ":skyblock_purchased");
    private final NamespacedKey purchasedBlockKey = NamespacedKey.fromString(NAMESPACE + ":skyblock_purchased_block");
    private final NamespacedKey generationKey = NamespacedKey.fromString(NAMESPACE + ":taint_generation");

    public NamespacedKey countedKey() {
        return countedKey;
    }

    public NamespacedKey purchasedKey() {
        return purchasedKey;
    }

    public NamespacedKey purchasedBlockKey() {
        return purchasedBlockKey;
    }

    public NamespacedKey taintKey(Material material) {
        if (material == null || material.isAir()) throw new IllegalArgumentException("Taint material is invalid");
        return taintKey(material.name());
    }

    public NamespacedKey taintKey(String material) {
        if (material == null || material.isBlank()) throw new IllegalArgumentException("Taint material is blank");
        String normalized = material.toLowerCase(java.util.Locale.ROOT).replace(':', '_');
        return NamespacedKey.fromString(NAMESPACE + ":taint/" + normalized);
    }

    public NamespacedKey generationKey() {
        return generationKey;
    }

    public boolean isCounted(ItemStack stack) {
        return hasItemTag(stack, countedKey);
    }

    public boolean isPurchased(ItemStack stack) {
        return hasItemTag(stack, purchasedKey);
    }

    /** Returns whether a placed block was made from a purchased stack. */
    public boolean isPurchased(Block block) {
        if (block == null) return false;
        return contains(block.getChunk(), purchasedBlockKey, pack(block.getX() & 15, block.getY(), block.getZ() & 15));
    }

    /** Stores purchased provenance in the chunk PDC until the block is broken. */
    public boolean markPurchased(Block block) {
        if (block == null) return false;
        return add(block.getChunk(), purchasedBlockKey, pack(block.getX() & 15, block.getY(), block.getZ() & 15));
    }

    public boolean consumePurchased(Block block) {
        if (block == null) return false;
        return remove(block.getChunk(), purchasedBlockKey, pack(block.getX() & 15, block.getY(), block.getZ() & 15));
    }

    /** Returns whether an item has only the two Skyblock-owned tags and no foreign state. */
    public boolean foreignStateAllowed(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return true;
        if (meta.hasDisplayName() || meta.hasLore() || !meta.getEnchants().isEmpty()
                || meta.hasCustomModelData() || meta instanceof Damageable damageable && damageable.hasDamage()) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.getKeys().stream().allMatch(key -> key.equals(countedKey) || key.equals(purchasedKey))) {
            return false;
        }
        if (pdc.getKeys().contains(countedKey) && !pdc.has(countedKey, PersistentDataType.BYTE)
                || pdc.getKeys().contains(purchasedKey) && !pdc.has(purchasedKey, PersistentDataType.BYTE)) {
            return false;
        }
        Byte counted = pdc.get(countedKey, PersistentDataType.BYTE);
        Byte purchased = pdc.get(purchasedKey, PersistentDataType.BYTE);
        return (counted == null || counted == 1) && (purchased == null || purchased == 1);
    }

    public boolean markCounted(ItemStack stack) {
        return setItemTag(stack, countedKey);
    }

    public boolean markPurchased(ItemStack stack) {
        return setItemTag(stack, purchasedKey);
    }

    public boolean propagatePurchased(ItemStack output, Collection<ItemStack> inputs) {
        if (output == null || inputs == null) return false;
        for (ItemStack input : inputs) {
            if (isPurchased(input)) return markPurchased(output);
        }
        return false;
    }

    public boolean growthExempt(Block block) {
        if (block == null) return true;
        if (block.getBlockData() instanceof Ageable) return true;
        return switch (block.getType()) {
            case SMALL_AMETHYST_BUD, MEDIUM_AMETHYST_BUD, LARGE_AMETHYST_BUD, AMETHYST_CLUSTER -> true;
            default -> false;
        };
    }

    public boolean stripCounted(ItemStack stack) {
        return removeItemTag(stack, countedKey);
    }

    public long pack(int localX, int y, int localZ) {
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            throw new IllegalArgumentException("Chunk-local coordinates must be 0..15");
        }
        if (y < -Y_OFFSET || y >= Y_OFFSET) throw new IllegalArgumentException("Y is outside taint encoding range");
        return ((long) (y + Y_OFFSET) << 8) | ((long) localX << 4) | localZ;
    }

    public int localX(long packed) {
        return (int) ((packed >>> 4) & 0xF);
    }

    public int localZ(long packed) {
        return (int) (packed & 0xF);
    }

    public int y(long packed) {
        return (int) (packed >>> 8) - Y_OFFSET;
    }

    public long[] read(Chunk chunk, Material material) {
        return read(chunk, taintKey(material));
    }

    public long[] read(Chunk chunk, NamespacedKey key) {
        if (chunk == null || key == null) return new long[0];
        long[] values = chunk.getPersistentDataContainer().get(key, PersistentDataType.LONG_ARRAY);
        return values == null ? new long[0] : values.clone();
    }

    public boolean contains(Chunk chunk, Material material, int localX, int y, int localZ) {
        long packed = pack(localX, y, localZ);
        return Arrays.binarySearch(read(chunk, material), packed) >= 0;
    }

    private boolean contains(Chunk chunk, NamespacedKey key, long packed) {
        return Arrays.binarySearch(read(chunk, key), packed) >= 0;
    }

    public boolean add(Chunk chunk, Material material, int localX, int y, int localZ) {
        return add(chunk, taintKey(material), pack(localX, y, localZ));
    }

    public boolean add(Chunk chunk, NamespacedKey key, long packed) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        long[] current = pdc.get(key, PersistentDataType.LONG_ARRAY);
        if (current == null) current = new long[0];
        int position = Arrays.binarySearch(current, packed);
        if (position >= 0) return false;
        position = -position - 1;
        long[] updated = new long[current.length + 1];
        System.arraycopy(current, 0, updated, 0, position);
        updated[position] = packed;
        System.arraycopy(current, position, updated, position + 1, current.length - position);
        pdc.set(key, PersistentDataType.LONG_ARRAY, updated);
        return true;
    }

    public boolean remove(Chunk chunk, Material material, int localX, int y, int localZ) {
        return remove(chunk, taintKey(material), pack(localX, y, localZ));
    }

    public boolean remove(Chunk chunk, NamespacedKey key, long packed) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        long[] current = pdc.get(key, PersistentDataType.LONG_ARRAY);
        if (current == null) return false;
        int position = Arrays.binarySearch(current, packed);
        if (position < 0) return false;
        if (current.length == 1) {
            pdc.remove(key);
            return true;
        }
        long[] updated = new long[current.length - 1];
        System.arraycopy(current, 0, updated, 0, position);
        System.arraycopy(current, position + 1, updated, position, current.length - position - 1);
        pdc.set(key, PersistentDataType.LONG_ARRAY, updated);
        return true;
    }

    public boolean consume(Block block, Material placedMaterial) {
        return remove(block.getChunk(), placedMaterial, block.getX() & 15, block.getY(), block.getZ() & 15);
    }

    public int generation(Chunk chunk) {
        return chunk.getPersistentDataContainer().getOrDefault(generationKey, PersistentDataType.INTEGER, -1);
    }

    public void setGeneration(Chunk chunk, int generation) {
        chunk.getPersistentDataContainer().set(generationKey, PersistentDataType.INTEGER, generation);
    }

    private static boolean hasItemTag(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        Byte value = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    private static boolean setItemTag(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(key, PersistentDataType.BYTE)) return false;
        pdc.set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return true;
    }

    private static boolean removeItemTag(ItemStack stack, NamespacedKey key) {
        if (!hasItemTag(stack, key)) return false;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().remove(key);
        stack.setItemMeta(meta);
        return true;
    }
}
