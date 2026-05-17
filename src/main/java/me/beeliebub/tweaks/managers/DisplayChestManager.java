package me.beeliebub.tweaks.managers;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DisplayChestManager {
    private final JavaPlugin plugin;
    private final Set<UUID> setupModePlayers = new HashSet<>();
    private final Set<UUID> removalModePlayers = new HashSet<>();
    private final Map<UUID, ItemStack> setupHandOverrides = new HashMap<>();
    private final NamespacedKey pdcKey;

    public DisplayChestManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, "display_chests");
    }

    public boolean toggleSetupMode(UUID playerId) {
        if (setupModePlayers.contains(playerId)) {
            setupModePlayers.remove(playerId);
            setupHandOverrides.remove(playerId);
            return false;
        } else {
            removalModePlayers.remove(playerId); // Disable removal mode if enabling setup mode
            setupModePlayers.add(playerId);
            setupHandOverrides.remove(playerId);
            return true;
        }
    }

    public void setHandOverride(UUID playerId, ItemStack item) {
        if (item == null) {
            setupHandOverrides.remove(playerId);
        } else {
            setupHandOverrides.put(playerId, item);
        }
    }

    public ItemStack getHandOverride(UUID playerId) {
        return setupHandOverrides.get(playerId);
    }

    public boolean isSetupMode(UUID playerId) {
        return setupModePlayers.contains(playerId);
    }

    public boolean toggleRemovalMode(UUID playerId) {
        if (removalModePlayers.contains(playerId)) {
            removalModePlayers.remove(playerId);
            return false;
        } else {
            setupModePlayers.remove(playerId); // Disable setup mode if enabling removal mode
            removalModePlayers.add(playerId);
            return true;
        }
    }

    public boolean isRemovalMode(UUID playerId) {
        return removalModePlayers.contains(playerId);
    }

    public void removeDisplay(Block block) {
        if (!(block.getState() instanceof Chest)) return;
        Chest chest = (Chest) block.getState();
        Inventory inventory = chest.getInventory();

        Location center;
        Block primaryBlock = block;

        if (inventory.getHolder() instanceof DoubleChest) {
            DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
            Chest left = (Chest) doubleChest.getLeftSide();
            Chest right = (Chest) doubleChest.getRightSide();

            Location lLoc = left.getLocation();
            Location rLoc = right.getLocation();

            center = new Location(block.getWorld(),
                    (lLoc.getX() + rLoc.getX()) / 2.0 + 0.5,
                    lLoc.getY() + 1.25,
                    (lLoc.getZ() + rLoc.getZ()) / 2.0 + 0.5);

            if (lLoc.getBlockX() < rLoc.getBlockX() || lLoc.getBlockZ() < rLoc.getBlockZ() || lLoc.getBlockY() < rLoc.getBlockY()) {
                primaryBlock = left.getBlock();
            } else {
                primaryBlock = right.getBlock();
            }
        } else {
            center = block.getLocation().add(0.5, 1.25, 0.5);
        }

        removeExistingDisplaysAt(primaryBlock.getChunk(), center);
    }

    public void processChest(Block block) {
        processChest(block, null);
    }

    public void processChest(Block block, ItemStack handOverride) {
        if (!(block.getState() instanceof Chest)) return;
        Chest chest = (Chest) block.getState();
        Inventory inventory = chest.getInventory();

        // Source priority: explicit hand override > slot 0 of the chest. Missing/AIR sources abort.
        ItemStack sourceItem;
        if (handOverride != null && handOverride.getType() != Material.AIR) {
            sourceItem = handOverride.clone();
            sourceItem.setAmount(1);
        } else {
            ItemStack slotZero = inventory.getItem(0);
            if (slotZero == null || slotZero.getType() == Material.AIR) {
                return;
            }
            sourceItem = slotZero.clone();
            sourceItem.setAmount(1);
        }

        Location center;
        Block primaryBlock = block;

        if (inventory.getHolder() instanceof DoubleChest) {
            DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
            Chest left = (Chest) doubleChest.getLeftSide();
            Chest right = (Chest) doubleChest.getRightSide();
            
            Location lLoc = left.getLocation();
            Location rLoc = right.getLocation();
            
            center = new Location(block.getWorld(), 
                (lLoc.getX() + rLoc.getX()) / 2.0 + 0.5,
                lLoc.getY() + 1.25,
                (lLoc.getZ() + rLoc.getZ()) / 2.0 + 0.5);
            
            // Resolve primary block deterministically to avoid chunk duplication issues
            if (lLoc.getBlockX() < rLoc.getBlockX() || lLoc.getBlockZ() < rLoc.getBlockZ() || lLoc.getBlockY() < rLoc.getBlockY()) {
                primaryBlock = left.getBlock();
            } else {
                primaryBlock = right.getBlock();
            }
        } else {
            center = block.getLocation().add(0.5, 1.25, 0.5);
        }

        Chunk chunk = primaryBlock.getChunk();

        removeExistingDisplaysAt(chunk, center);

        // Bake the spawn yaw to point the entity north (yaw 180 in Bukkit coords) so the
        // model orientation is independent of the chest's block-data facing direction.
        center.setYaw(180f);
        center.setPitch(0f);

        ItemDisplay display = (ItemDisplay) block.getWorld().spawnEntity(center, EntityType.ITEM_DISPLAY);
        display.setItemStack(sourceItem);
        // FIXED billboard preserves the baked rotation rather than tracking the camera.
        display.setBillboard(Display.Billboard.FIXED);
        display.setRotation(180f, 0f);

        // Adjust scale to match normal item stack size (approx 0.5)
        org.bukkit.util.Transformation transformation = display.getTransformation();
        transformation.getScale().set(0.5f, 0.5f, 0.5f);
        display.setTransformation(transformation);

        storeDisplayInChunk(chunk, display.getUniqueId());
    }
    
    private void removeExistingDisplaysAt(Chunk chunk, Location center) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String data = pdc.get(pdcKey, PersistentDataType.STRING);
        if (data == null) return;
        
        String[] uuids = data.split(",");
        List<String> validUuids = new ArrayList<>();
        boolean changed = false;
        
        for (String uuidStr : uuids) {
            if (uuidStr.isEmpty()) continue;
            try {
                UUID uuid = UUID.fromString(uuidStr);
                org.bukkit.entity.Entity entity = chunk.getWorld().getEntity(uuid);
                
                if (entity instanceof ItemDisplay) {
                    if (entity.getLocation().distanceSquared(center) < 0.1) {
                        entity.remove();
                        changed = true;
                        continue;
                    }
                } else if (entity == null) {
                    changed = true;
                    continue;
                }
                validUuids.add(uuidStr);
            } catch (IllegalArgumentException e) {
                changed = true;
            }
        }
        
        if (changed) {
            if (validUuids.isEmpty()) {
                pdc.remove(pdcKey);
            } else {
                pdc.set(pdcKey, PersistentDataType.STRING, String.join(",", validUuids));
            }
        }
    }

    private void storeDisplayInChunk(Chunk chunk, UUID displayId) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String currentData = pdc.getOrDefault(pdcKey, PersistentDataType.STRING, "");
        if (!currentData.isEmpty()) {
            currentData += ",";
        }
        currentData += displayId.toString();
        pdc.set(pdcKey, PersistentDataType.STRING, currentData);
    }

}