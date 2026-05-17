package me.beeliebub.tweaks.managers;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class DisplayChestManager {
    private final JavaPlugin plugin;
    private final Set<UUID> setupModePlayers = new HashSet<>();
    private final Set<UUID> removalModePlayers = new HashSet<>();
    // 'hand' mode: resolve the player's CURRENT main-hand item at click time,
    // rather than snapshotting at /displaychest hand. Set membership means the
    // player toggled the hand variant; the actual item is read from the live
    // Player on each interaction.
    private final Set<UUID> useCurrentHandPlayers = new HashSet<>();
    // 'side' mode: the next setup click should embed the item flush with the
    // clicked face of the chest (via processChestSide) instead of floating it
    // above the chest. Combinable with useCurrentHand for /displaychest hand side.
    private final Set<UUID> embedSidePlayers = new HashSet<>();
    private final NamespacedKey pdcKey;

    public DisplayChestManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, "display_chests");
    }

    public boolean toggleSetupMode(UUID playerId) {
        if (setupModePlayers.contains(playerId)) {
            setupModePlayers.remove(playerId);
            useCurrentHandPlayers.remove(playerId);
            embedSidePlayers.remove(playerId);
            return false;
        } else {
            removalModePlayers.remove(playerId); // Disable removal mode if enabling setup mode
            setupModePlayers.add(playerId);
            useCurrentHandPlayers.remove(playerId);
            embedSidePlayers.remove(playerId);
            return true;
        }
    }

    public void setUseCurrentHand(UUID playerId, boolean enabled) {
        if (enabled) {
            useCurrentHandPlayers.add(playerId);
        } else {
            useCurrentHandPlayers.remove(playerId);
        }
    }

    public boolean isUseCurrentHand(UUID playerId) {
        return useCurrentHandPlayers.contains(playerId);
    }

    public void setEmbedSide(UUID playerId, boolean enabled) {
        if (enabled) {
            embedSidePlayers.add(playerId);
        } else {
            embedSidePlayers.remove(playerId);
        }
    }

    public boolean isEmbedSide(UUID playerId) {
        return embedSidePlayers.contains(playerId);
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

    // /displaychest off path. Anchored at the BLOCK center (not the top-of-chest
    // anchor used by processChest) with a wider radius so a single clicked face
    // removes everything we may have spawned for this chest — top-floating
    // displays AND any side-embedded displays attached to any of the block's
    // 6 faces. For double chests, also sweeps the partner block so the user
    // can remove a side display from either half.
    public void removeDisplay(Block block) {
        if (!(block.getState() instanceof Chest)) return;
        Chest chest = (Chest) block.getState();
        Inventory inventory = chest.getInventory();

        Location anchor = block.getLocation().add(0.5, 0.5, 0.5);
        removeDisplaysWithinRadius(block.getChunk(), anchor, REMOVE_RADIUS_SQUARED);

        if (inventory.getHolder() instanceof DoubleChest doubleChest) {
            Chest left = (Chest) doubleChest.getLeftSide();
            Chest right = (Chest) doubleChest.getRightSide();
            Block partner = left.getBlock().equals(block) ? right.getBlock() : left.getBlock();
            Location partnerAnchor = partner.getLocation().add(0.5, 0.5, 0.5);
            removeDisplaysWithinRadius(partner.getChunk(), partnerAnchor, REMOVE_RADIUS_SQUARED);
        }
    }

    // ~2 blocks squared. Covers the +1.25 Y top-of-chest displays, the
    // half-embedded side blocks (corner ~0.87 from block center), and the
    // ~0.51-from-face non-block side sprites (~1.01 from block center).
    private static final double REMOVE_RADIUS_SQUARED = 4.0;

    public void processChest(Block block) {
        processChest(block, null, null);
    }

    // Listener entry point. The Player is required so we can resolve the
    // current main-hand item when /displaychest hand is active without
    // snapshotting at command time. Orientation is handled client-side by the
    // VERTICAL billboard set on the spawned ItemDisplay (see below), so the
    // Player is not used for any rotation math — every viewer sees the item
    // facing them automatically.
    public void processChest(Block block, Player player) {
        processChest(block, null, player);
    }

    private void processChest(Block block, ItemStack handOverride, Player player) {
        if (!(block.getState() instanceof Chest)) return;
        Chest chest = (Chest) block.getState();
        Inventory inventory = chest.getInventory();

        ItemStack sourceItem = resolveSourceItem(player, inventory, handOverride);
        if (sourceItem == null) return;

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

        // Tight radius — replace only a display previously anchored at this
        // exact top-of-chest spot. /displaychest off uses a wider sweep.
        removeDisplaysWithinRadius(chunk, center, 0.1);

        // Spawn with a neutral yaw/pitch baseline. The VERTICAL billboard set
        // below makes the client rotate the display around its Y axis to face
        // each viewer's camera every frame, so any pre-set yaw would just act
        // as an offset that throws the apparent facing off.
        center.setYaw(0f);
        center.setPitch(0f);

        ItemDisplay display = (ItemDisplay) block.getWorld().spawnEntity(center, EntityType.ITEM_DISPLAY);
        display.setItemStack(sourceItem);
        // VERTICAL billboard: the client billboards the entity around its Y
        // axis to always face the local viewer (per-player, per-frame). No
        // server-side scheduler or rotation math is needed — the engine does
        // the right thing for every camera independently.
        display.setBillboard(Display.Billboard.VERTICAL);

        // Bake a 180° Y-axis rotation into the model transform so the item's
        // "front" face renders toward the viewer. VERTICAL billboard aligns the
        // entity's local +Z with the camera; ItemDisplay's natural facing is
        // its -Z, so without this flip the item appears mirrored backwards.
        // Scale matches a normal held-item size (~0.5).
        Quaternionf leftRotation = new Quaternionf().rotateY((float) Math.PI);
        org.bukkit.util.Transformation transformation = display.getTransformation();
        display.setTransformation(new org.bukkit.util.Transformation(
                transformation.getTranslation(),
                leftRotation,
                new org.joml.Vector3f(0.5f, 0.5f, 0.5f),
                transformation.getRightRotation()));

        storeDisplayInChunk(chunk, display.getUniqueId());
    }

    // Side-mode entry point. The Player is required for the same reasons as
    // processChest (live hand resolution) — orientation is fully determined by
    // the BlockFace the player clicked. Block items are embedded inside the
    // chest block so only the clicked face is visible; non-block items render
    // item-frame style flat against the face.
    public void processChestSide(Block block, BlockFace face, Player player) {
        if (!(block.getState() instanceof Chest)) return;
        Chest chest = (Chest) block.getState();
        Inventory inventory = chest.getInventory();

        ItemStack sourceItem = resolveSourceItem(player, inventory, null);
        if (sourceItem == null) return;

        Vector faceNormal = new Vector(face.getModX(), face.getModY(), face.getModZ());
        boolean isBlockItem = sourceItem.getType().isBlock();

        Location displayLoc;
        org.bukkit.util.Transformation transformation;

        // Face-normal depth offset from the chest's center:
        //   * Block items at 0.30 — the 0.5-thick cube spans 0.05..0.55 along
        //     the normal, so ~10% of the cube protrudes past the chest face
        //     and the rest is embedded inside the chest.
        //   * Non-block items at 0.51 — 2D sprites sit just outside the face
        //     plane to avoid z-fighting with the chest's outer shell.
        double faceOffset = isBlockItem ? 0.30 : 0.51;
        displayLoc = block.getLocation().clone()
                .add(0.5, 0.5, 0.5)
                .add(faceNormal.clone().multiply(faceOffset));
        if (faceNormal.lengthSquared() > 1.0e-6) {
            displayLoc.setDirection(faceNormal);
        } else {
            displayLoc.setYaw(0f);
            displayLoc.setPitch(0f);
        }

        // ItemDisplay block and item models both render with the model origin
        // at the model's CENTER (local vertices roughly in -0.5..0.5), so a
        // 0.5 scale with no translation already centers the model on the
        // entity origin — which is at displayLoc above. Adding a translation
        // here would shift the model off-center into the bottom-left octant
        // of the entity's local axes (and thus the clicked face).
        transformation = new org.bukkit.util.Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(0.5f, 0.5f, 0.5f),
                new Quaternionf());

        Chunk chunk = block.getChunk();
        ItemDisplay display = (ItemDisplay) block.getWorld()
                .spawnEntity(displayLoc, EntityType.ITEM_DISPLAY);
        display.setItemStack(sourceItem);
        // Static, no client billboarding — the whole point of side mode.
        display.setBillboard(Display.Billboard.FIXED);
        if (!isBlockItem) {
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        }
        display.setTransformation(transformation);

        storeDisplayInChunk(chunk, display.getUniqueId());
    }

    // Source priority for the displayed item (shared by processChest and
    // processChestSide):
    //   1. /displaychest hand mode + clicking player -> player's CURRENT main hand (live).
    //   2. Explicit handOverride argument (snapshot path; retained for legacy callers).
    //   3. Slot 0 of the chest.
    // Returns null if no eligible source is found — caller aborts without spawning.
    private ItemStack resolveSourceItem(Player player, Inventory inventory, ItemStack handOverride) {
        if (player != null && useCurrentHandPlayers.contains(player.getUniqueId())) {
            ItemStack live = player.getInventory().getItemInMainHand();
            if (live.getType() != Material.AIR) {
                return cloneSingle(live);
            }
        }
        if (handOverride != null && handOverride.getType() != Material.AIR) {
            return cloneSingle(handOverride);
        }
        ItemStack slotZero = inventory.getItem(0);
        if (slotZero == null || slotZero.getType() == Material.AIR) {
            return null;
        }
        return cloneSingle(slotZero);
    }

    private static ItemStack cloneSingle(ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }

    private void removeDisplaysWithinRadius(Chunk chunk, Location anchor, double maxDistanceSquared) {
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
                    if (entity.getLocation().distanceSquared(anchor) < maxDistanceSquared) {
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