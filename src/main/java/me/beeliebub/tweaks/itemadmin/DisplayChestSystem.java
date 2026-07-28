package me.beeliebub.tweaks.itemadmin;

import me.beeliebub.tweaks.core.Messages;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// Consolidated DisplayChest subsystem: command (/displaychest), interact listener,
// and per-chunk PDC-backed display manager — previously DisplayChestCommand,
// DisplayChestListener, and DisplayChestManager respectively.
public class DisplayChestSystem implements CommandExecutor, TabCompleter, Listener {

    // ~2 blocks squared. Covers the +1.25 Y top-of-chest displays, the
    // half-embedded side blocks (corner ~0.87 from block center), and the
    // ~0.51-from-face non-block side sprites (~1.01 from block center).
    private static final double REMOVE_RADIUS_SQUARED = 4.0;

    private final Set<UUID> setupModePlayers = new HashSet<>();
    private final Set<UUID> removalModePlayers = new HashSet<>();
    // 'hand' mode: resolve the player's CURRENT main-hand item at click time,
    // rather than snapshotting at /displaychest hand.
    private final Set<UUID> useCurrentHandPlayers = new HashSet<>();
    // 'side' mode: the next setup click should embed the item flush with the
    // clicked face of the chest instead of floating it above the chest.
    private final Set<UUID> embedSidePlayers = new HashSet<>();
    private final NamespacedKey pdcKey;

    public DisplayChestSystem(JavaPlugin plugin) {
        this.pdcKey = new NamespacedKey(plugin, "display_chests");
    }

    // ─── Command ──────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.ITEM_ADMIN.displayChestRequiresPlayer());
            return true;
        }

        // 'off' is exclusive — never combines with hand/side.
        for (String arg : args) {
            if (arg.equalsIgnoreCase("off")) {
                boolean enabled = toggleRemovalMode(player.getUniqueId());
                if (enabled) {
                    player.sendMessage(Messages.ITEM_ADMIN.displayChestRemovalEnabled());
                } else {
                    player.sendMessage(Messages.ITEM_ADMIN.displayChestRemovalDisabled());
                }
                return true;
            }
        }

        boolean handFlag = false;
        boolean sideFlag = false;
        for (String arg : args) {
            String lower = arg.toLowerCase(Locale.ROOT);
            if (lower.equals("hand")) handFlag = true;
            else if (lower.equals("side")) sideFlag = true;
        }

        boolean enabled = toggleSetupMode(player.getUniqueId());
        if (enabled) {
            setUseCurrentHand(player.getUniqueId(), handFlag);
            setEmbedSide(player.getUniqueId(), sideFlag);
            player.sendMessage(Messages.ITEM_ADMIN.displayChestSetupEnabled(handFlag, sideFlag));
        } else {
            player.sendMessage(Messages.ITEM_ADMIN.displayChestSetupDisabled());
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0) return List.of();
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            return filterPrefix(List.of("hand", "side", "off"), partial);
        }

        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            if (first.equals("hand")) options.add("side");
            else if (first.equals("side")) options.add("hand");
            return filterPrefix(options, partial);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        List<String> out = new ArrayList<>(options.size());
        for (String opt : options) {
            if (opt.startsWith(prefix)) out.add(opt);
        }
        return out;
    }

    // ─── Listener ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        boolean isSetup = isSetupMode(player.getUniqueId());
        boolean isRemoval = isRemovalMode(player.getUniqueId());

        if (!isSetup && !isRemoval) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getState() instanceof Chest) {
                event.setCancelled(true);
                if (isSetup) {
                    if (isEmbedSide(player.getUniqueId())) {
                        BlockFace face = event.getBlockFace();
                        if (face == null) face = BlockFace.UP;
                        processChestSide(block, face, player);
                    } else {
                        processChest(block, player);
                    }
                    player.sendMessage(Messages.ITEM_ADMIN.displayChestGeneratedOrUpdated());
                } else {
                    removeDisplay(block);
                    player.sendMessage(Messages.ITEM_ADMIN.displayChestRemoved());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (isSetupMode(id)) toggleSetupMode(id);
        if (isRemovalMode(id)) toggleRemovalMode(id);
    }

    // ─── Mode state ───────────────────────────────────────────────────────────

    public boolean toggleSetupMode(UUID playerId) {
        if (setupModePlayers.contains(playerId)) {
            setupModePlayers.remove(playerId);
            useCurrentHandPlayers.remove(playerId);
            embedSidePlayers.remove(playerId);
            return false;
        } else {
            removalModePlayers.remove(playerId);
            setupModePlayers.add(playerId);
            useCurrentHandPlayers.remove(playerId);
            embedSidePlayers.remove(playerId);
            return true;
        }
    }

    public void setUseCurrentHand(UUID playerId, boolean enabled) {
        if (enabled) useCurrentHandPlayers.add(playerId);
        else useCurrentHandPlayers.remove(playerId);
    }

    public boolean isUseCurrentHand(UUID playerId) {
        return useCurrentHandPlayers.contains(playerId);
    }

    public void setEmbedSide(UUID playerId, boolean enabled) {
        if (enabled) embedSidePlayers.add(playerId);
        else embedSidePlayers.remove(playerId);
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
            setupModePlayers.remove(playerId);
            removalModePlayers.add(playerId);
            return true;
        }
    }

    public boolean isRemovalMode(UUID playerId) {
        return removalModePlayers.contains(playerId);
    }

    // ─── Display rendering ────────────────────────────────────────────────────

    // /displaychest off path. Anchored at the BLOCK center (not the top-of-chest
    // anchor used by processChest) with a wider radius so a single clicked face
    // removes everything we may have spawned for this chest — top-floating
    // displays AND any side-embedded displays attached to any of the block's
    // 6 faces. For double chests, also sweeps the partner block so the user
    // can remove a side display from either half.
    public void removeDisplay(Block block) {
        if (!(block.getState() instanceof Chest chest)) return;
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

    public void processChest(Block block) {
        processChest(block, null, null);
    }

    public void processChest(Block block, Player player) {
        processChest(block, null, player);
    }

    private void processChest(Block block, ItemStack handOverride, Player player) {
        if (!(block.getState() instanceof Chest chest)) return;
        Inventory inventory = chest.getInventory();

        ItemStack sourceItem = resolveSourceItem(player, inventory, handOverride);
        if (sourceItem == null) return;

        Location center;
        Block primaryBlock = block;

        if (inventory.getHolder() instanceof DoubleChest doubleChest) {
            Chest left = (Chest) doubleChest.getLeftSide();
            Chest right = (Chest) doubleChest.getRightSide();

            Location lLoc = left.getLocation();
            Location rLoc = right.getLocation();

            center = new Location(block.getWorld(),
                    (lLoc.getX() + rLoc.getX()) / 2.0 + 0.5,
                    lLoc.getY() + 1.25,
                    (lLoc.getZ() + rLoc.getZ()) / 2.0 + 0.5);

            // Deterministic primary block to avoid chunk duplication issues
            if (lLoc.getBlockX() < rLoc.getBlockX() || lLoc.getBlockZ() < rLoc.getBlockZ() || lLoc.getBlockY() < rLoc.getBlockY()) {
                primaryBlock = left.getBlock();
            } else {
                primaryBlock = right.getBlock();
            }
        } else {
            center = block.getLocation().add(0.5, 1.25, 0.5);
        }

        Chunk chunk = primaryBlock.getChunk();

        removeDisplaysWithinRadius(chunk, center, 0.1);

        center.setYaw(0f);
        center.setPitch(0f);

        ItemDisplay display = (ItemDisplay) block.getWorld().spawnEntity(center, EntityType.ITEM_DISPLAY);
        display.setItemStack(sourceItem);
        display.setBillboard(Display.Billboard.VERTICAL);

        // Bake a 180° Y-axis rotation into the model transform so the item's
        // "front" face renders toward the viewer.
        Quaternionf leftRotation = new Quaternionf().rotateY((float) Math.PI);
        org.bukkit.util.Transformation transformation = display.getTransformation();
        display.setTransformation(new org.bukkit.util.Transformation(
                transformation.getTranslation(),
                leftRotation,
                new Vector3f(0.5f, 0.5f, 0.5f),
                transformation.getRightRotation()));

        storeDisplayInChunk(chunk, display.getUniqueId());
    }

    public void processChestSide(Block block, BlockFace face, Player player) {
        if (!(block.getState() instanceof Chest chest)) return;
        Inventory inventory = chest.getInventory();

        ItemStack sourceItem = resolveSourceItem(player, inventory, null);
        if (sourceItem == null) return;

        Vector faceNormal = new Vector(face.getModX(), face.getModY(), face.getModZ());
        boolean isBlockItem = sourceItem.getType().isBlock();

        // Block items at 0.30, non-block items at 0.51 from chest center along the face normal.
        double faceOffset = isBlockItem ? 0.30 : 0.51;
        Location displayLoc = block.getLocation().clone()
                .add(0.5, 0.5, 0.5)
                .add(faceNormal.clone().multiply(faceOffset));
        if (faceNormal.lengthSquared() > 1.0e-6) {
            displayLoc.setDirection(faceNormal);
        } else {
            displayLoc.setYaw(0f);
            displayLoc.setPitch(0f);
        }

        org.bukkit.util.Transformation transformation = new org.bukkit.util.Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(0.5f, 0.5f, 0.5f),
                new Quaternionf());

        Chunk chunk = block.getChunk();
        ItemDisplay display = (ItemDisplay) block.getWorld()
                .spawnEntity(displayLoc, EntityType.ITEM_DISPLAY);
        display.setItemStack(sourceItem);
        display.setBillboard(Display.Billboard.FIXED);
        if (!isBlockItem) {
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        }
        display.setTransformation(transformation);

        storeDisplayInChunk(chunk, display.getUniqueId());
    }

    // Source priority for the displayed item:
    //   1. /displaychest hand mode + clicking player -> player's CURRENT main hand (live).
    //   2. Explicit handOverride argument (legacy callers).
    //   3. Slot 0 of the chest.
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
