package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.RewardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

// Resource Hunt minigame.
//
// On every server start, one entry is picked at random from resource_hunt.yml as the active
// target (Material + required amount). The first player to obtain the target via block drops
// in the jass:resource world is granted the "resource" reward via RewardManager and the hunt
// closes for the rest of the session. Drops in any other world are ignored entirely.
//
// Only items "original to" the resource world count: every block placed by a player in
// jass:resource is tagged with the PLACED_KEY PDC marker, and breaking such a block (whether
// by hand, with Tunneller, or via any other route that flows through this listener or
// recordExternalDrops) does not advance progress. If the skipped drops would have advanced
// progress, the breaker gets an action-bar warning so they don't think the hunt is broken.
//
// Listening at EventPriority.LOW for BlockDropItemEvent guarantees we tally the dropped items
// before Telekinesis (default NORMAL priority) clears the event's item list to route them to
// the player's inventory — so telekinesis users and non-telekinesis users are counted exactly
// once each, with no double-counting via the ground-pickup path.
public class ResourceHunt implements Listener {

    public static final String TARGET_WORLD_KEY = "jass:resource";
    public static final String REWARD_NAME = "resource";

    // PDC marker stamped onto every player-placed block in jass:resource. Drops from a block
    // carrying this marker do not count toward the hunt — that's how we keep players from
    // cheesing progress by stockpiling target materials elsewhere, depositing them as blocks
    // in the resource world, and re-mining them on event day.
    private static final NamespacedKey PLACED_KEY = new NamespacedKey("tweaks", "resource_placed");

    // PDC marker stamped onto every player-attributable mob spawn in jass:resource. Drops
    // from a mob carrying this marker don't count, mirroring the placed-block exclusion —
    // spawn-egged zombies, bred animals, snow golems built on-site, mob-spawner output, etc.
    // shouldn't be a viable shortcut for the hunt.
    private static final NamespacedKey SPAWNED_KEY = new NamespacedKey("tweaks", "resource_spawned");

    // Spawn reasons that count as "the player conjured this mob" — i.e. it's not original
    // to the resource world. Everything else (NATURAL, CHUNK_GEN, JOCKEY, VILLAGE_DEFENSE,
    // SLIME_SPLIT, REINFORCEMENTS, INFECTION, TRIAL_SPAWNER, DROWNED, etc.) is treated as
    // legit and counts. SPAWNER is included because there are no naturally-generated
    // spawners in jass:resource, so any mob from a spawner must be from a player-placed one.
    private static final Set<CreatureSpawnEvent.SpawnReason> EXCLUDED_SPAWN_REASONS = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.SPAWNER,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG,
            CreatureSpawnEvent.SpawnReason.DISPENSE_EGG,
            CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM,
            CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN,
            CreatureSpawnEvent.SpawnReason.BUILD_WITHER,
            CreatureSpawnEvent.SpawnReason.BREEDING,
            CreatureSpawnEvent.SpawnReason.BUCKET,
            CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY,
            CreatureSpawnEvent.SpawnReason.CUSTOM,
            CreatureSpawnEvent.SpawnReason.COMMAND
    );

    private final Tweaks plugin;
    private final RewardManager rewardManager;
    private final Material targetMaterial;
    private final int targetAmount;
    private final Map<UUID, Integer> progress = new ConcurrentHashMap<>();
    private volatile UUID winner;
    private volatile String winnerName;

    public ResourceHunt(Tweaks plugin, RewardManager rewardManager) {
        this.plugin = plugin;
        this.rewardManager = rewardManager;

        Map.Entry<Material, Integer> picked = pickRandomTarget();
        if (picked == null) {
            this.targetMaterial = null;
            this.targetAmount = 0;
        } else {
            this.targetMaterial = picked.getKey();
            this.targetAmount = picked.getValue();
            plugin.getLogger().info("Resource Hunt target this session: "
                    + targetAmount + "x " + targetMaterial.getKey());
        }

        // Pre-create the reward shell so admins can populate it via /reward edit resource even
        // before someone wins. Items added after a winner is declared are still picked up at
        // /reward claim time because RewardManager resolves items lazily.
        if (!rewardManager.rewardExists(REWARD_NAME)) {
            rewardManager.createReward(REWARD_NAME);
            plugin.getLogger().info("Created empty '" + REWARD_NAME
                    + "' reward shell — populate with /reward edit " + REWARD_NAME);
        }
    }

    private Map.Entry<Material, Integer> pickRandomTarget() {
        File file = new File(plugin.getDataFolder(), "resource_hunt.yml");
        if (!file.exists()) {
            plugin.saveResource("resource_hunt.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        List<Map.Entry<Material, Integer>> entries = new ArrayList<>();
        for (String key : cfg.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) {
                plugin.getLogger().warning("resource_hunt.yml: unknown material '" + key + "', skipped.");
                continue;
            }
            int amount = cfg.getInt(key, 0);
            if (amount <= 0) {
                plugin.getLogger().warning("resource_hunt.yml: '" + key + "' has non-positive amount, skipped.");
                continue;
            }
            entries.add(Map.entry(mat, amount));
        }

        if (entries.isEmpty()) {
            plugin.getLogger().warning("Resource Hunt has no valid targets in resource_hunt.yml; minigame disabled this session.");
            return null;
        }
        return entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
    }

    public boolean isActive() {
        return targetMaterial != null && winner == null;
    }

    /** True when this block carries the player-placed PDC marker (only ever set in jass:resource). */
    public boolean isPlacedByPlayer(Block block) {
        long[] entries = readChunkEntries(block.getChunk());
        if (entries == null) return false;
        long key = packBlock(block);
        for (long entry : entries) {
            if (entry == key) return true;
        }
        return false;
    }

    // Stamp every player-placed block in the resource world. MONITOR so we only mark blocks
    // that other plugins haven't cancelled. ignoreCancelled is redundant at MONITOR but kept
    // explicit for documentation.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!TARGET_WORLD_KEY.equals(block.getWorld().getKey().asString())) return;
        markPlaced(block);
    }

    // Pack a block position into a single long, unique within its chunk: bits 8+ = y,
    // bits 4-7 = local x (0-15), bits 0-3 = local z (0-15). Used as the entry value
    // inside the chunk-PDC long array under PLACED_KEY.
    private static long packBlock(Block block) {
        int localX = block.getX() & 0xF;
        int localZ = block.getZ() & 0xF;
        long y = block.getY();
        return (y << 8) | ((long) localX << 4) | (long) localZ;
    }

    private static long[] readChunkEntries(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        return pdc.get(PLACED_KEY, PersistentDataType.LONG_ARRAY);
    }

    private static void markPlaced(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        long[] existing = pdc.get(PLACED_KEY, PersistentDataType.LONG_ARRAY);
        long key = packBlock(block);
        if (existing == null) {
            pdc.set(PLACED_KEY, PersistentDataType.LONG_ARRAY, new long[]{key});
            return;
        }
        for (long entry : existing) {
            if (entry == key) return;
        }
        long[] updated = new long[existing.length + 1];
        System.arraycopy(existing, 0, updated, 0, existing.length);
        updated[existing.length] = key;
        pdc.set(PLACED_KEY, PersistentDataType.LONG_ARRAY, updated);
    }

    // Drop the marker for a single block position. Called after we've already classified the
    // break so the chunk PDC doesn't accumulate entries for blocks the player has since mined.
    private static void clearPlaced(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        long[] existing = pdc.get(PLACED_KEY, PersistentDataType.LONG_ARRAY);
        if (existing == null) return;
        long key = packBlock(block);
        int idx = -1;
        for (int i = 0; i < existing.length; i++) {
            if (existing[i] == key) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;
        if (existing.length == 1) {
            pdc.remove(PLACED_KEY);
            return;
        }
        long[] updated = new long[existing.length - 1];
        System.arraycopy(existing, 0, updated, 0, idx);
        System.arraycopy(existing, idx + 1, updated, idx, existing.length - idx - 1);
        pdc.set(PLACED_KEY, PersistentDataType.LONG_ARRAY, updated);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!isActive()) return;
        Block block = event.getBlock();
        if (!TARGET_WORLD_KEY.equals(block.getWorld().getKey().asString())) return;

        int gained = 0;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack.getType() == targetMaterial) {
                gained += stack.getAmount();
            }
        }

        Player player = event.getPlayer();
        if (isPlacedByPlayer(block)) {
            if (gained > 0) sendPlacedWarning(player);
            clearPlaced(block);
            return;
        }
        if (gained <= 0) return;
        recordProgress(player, gained);
    }

    /**
     * Hook for enchant code paths that route drops manually and therefore bypass
     * BlockDropItemEvent (Tunneller's surrounding block breaks, Smelter's setDropItems(false)
     * path, etc.). Call this once per broken block, BEFORE the block is set to AIR, with the
     * drops the player will actually receive (i.e. after smelter/fortune/silk processing).
     *
     * <p>Skips placed blocks, sending the breaker an action-bar warning when the skipped
     * drops would have actually advanced their progress.
     */
    public void recordExternalDrops(Player player, Block block, Collection<ItemStack> drops) {
        if (!isActive()) return;
        if (!TARGET_WORLD_KEY.equals(block.getWorld().getKey().asString())) return;

        int gained = 0;
        for (ItemStack stack : drops) {
            if (stack != null && stack.getType() == targetMaterial) {
                gained += stack.getAmount();
            }
        }

        if (isPlacedByPlayer(block)) {
            if (gained > 0) sendPlacedWarning(player);
            clearPlaced(block);
            return;
        }
        if (gained <= 0) return;
        recordProgress(player, gained);
    }

    private void recordProgress(Player player, int gained) {
        int newTotal = progress.merge(player.getUniqueId(), gained, Integer::sum);
        if (newTotal >= targetAmount) {
            declareWinner(player);
        }
    }

    private void sendPlacedWarning(Player player) {
        player.sendActionBar(Component.text(
                "Hand-placed blocks don't count toward Resource Hunt!", NamedTextColor.RED));
    }

    private void sendSpawnedWarning(Player player) {
        player.sendActionBar(Component.text(
                "Player-spawned mobs don't count toward Resource Hunt!", NamedTextColor.RED));
    }

    // Stamp every player-attributable mob spawn in the resource world. We mark on spawn (rather
    // than checking the spawn reason at death) because the reason is only available on the
    // CreatureSpawnEvent — it isn't preserved on the entity by Bukkit.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!TARGET_WORLD_KEY.equals(event.getLocation().getWorld().getKey().asString())) return;
        if (!EXCLUDED_SPAWN_REASONS.contains(event.getSpawnReason())) return;
        event.getEntity().getPersistentDataContainer()
                .set(SPAWNED_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    // Counts mob-loot drops toward Resource Hunt. HIGH priority so we read drops AFTER quality
    // looting (LOW), Egg Collector (NORMAL), and any other plugin that mutates event.getDrops().
    // Player-killed mobs in jass:resource that aren't carrying the player-spawned marker count;
    // marked mobs skip and warn the killer when the drops would have advanced the target.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isActive()) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;
        if (!TARGET_WORLD_KEY.equals(entity.getWorld().getKey().asString())) return;

        Player killer = entity.getKiller();
        if (killer == null) return;

        int gained = 0;
        for (ItemStack stack : event.getDrops()) {
            if (stack != null && stack.getType() == targetMaterial) {
                gained += stack.getAmount();
            }
        }

        boolean playerSpawned = entity.getPersistentDataContainer()
                .has(SPAWNED_KEY, PersistentDataType.BYTE);
        if (playerSpawned) {
            if (gained > 0) sendSpawnedWarning(killer);
            return;
        }
        if (gained <= 0) return;
        recordProgress(killer, gained);
    }

    private synchronized void declareWinner(Player player) {
        if (winner != null) return;
        winner = player.getUniqueId();
        winnerName = player.getName();

        rewardManager.grantReward(player.getUniqueId(), REWARD_NAME);

        Component announcement = Component.text()
                .append(Component.text("[Resource Hunt] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" was first to gather ", NamedTextColor.YELLOW))
                .append(Component.text(targetAmount + "× " + readableName(targetMaterial), NamedTextColor.WHITE))
                .append(Component.text("! Use ", NamedTextColor.YELLOW))
                .append(Component.text("/reward claim", NamedTextColor.GOLD))
                .append(Component.text(" to collect.", NamedTextColor.YELLOW))
                .build();
        Bukkit.broadcast(announcement);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (targetMaterial == null) return;

        Component msg;
        if (winner == null) {
            msg = Component.text()
                    .append(Component.text("Resource Hunt: ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("first to gather ", NamedTextColor.YELLOW))
                    .append(Component.text(targetAmount + "× " + readableName(targetMaterial), NamedTextColor.WHITE))
                    .append(Component.text(" in the resource world wins!", NamedTextColor.YELLOW))
                    .append(Component.text(" Use /resource to go there now!", NamedTextColor.GREEN))
                    .build();
        } else {
            String name = winnerName != null ? winnerName : "another player";
            msg = Component.text()
                    .append(Component.text("Resource Hunt: ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("already completed by ", NamedTextColor.YELLOW))
                    .append(Component.text(name, NamedTextColor.AQUA))
                    .append(Component.text(" — they gathered ", NamedTextColor.YELLOW))
                    .append(Component.text(targetAmount + "× " + readableName(targetMaterial), NamedTextColor.WHITE))
                    .append(Component.text(".", NamedTextColor.YELLOW))
                    .build();
        }
        event.getPlayer().sendMessage(msg);
    }

    private static String readableName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }
}