package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.minigames.RewardManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

// Resource Hunt minigame.
//
// On every server start, one entry is picked at random from resource_hunt.yml as the *initial*
// target. The world that target belongs to (overworld or nether section) becomes the active
// resource world for the whole session; every other player target is also drawn from that same
// world's pool.
//
// Targets are grouped under categories (collect, kill, smelt, enchant, shear, breed, craft,
// barter). Each category dictates how progress is earned and whether the target identifier names
// a Material or an EntityType. World restrictions: enchant/shear are overworld-only, barter is
// nether-only; entries placed in the wrong world section are skipped with a warning.
//
// Targets are per-player: the first player to join receives the initial target. Each subsequent
// join is assigned a random target from the active world's pool, preferring identifiers no other
// player already has. If every identifier in the pool is taken, duplicates are allowed
// (best-effort uniqueness, not strict).
//
// Each player builds toward three cumulative tier thresholds individually, computed from their
// own assigned amount and multiplier:
//   Tier 1 = base
//   Tier 2 = round(base * multiplier)
//   Tier 3 = round(base * multiplier^2)
// Crossing each threshold grants the "resource" reward exactly once via RewardManager; a
// single update may cross multiple tiers if the gain is large. Drops/extracts in any world
// other than the active resource world are ignored entirely for progress purposes.
//
// To prevent cheesing, players are restricted from bringing disallowed items into the
// resource world (enforced by /resource and /back).
//
// Listening at EventPriority.NORMAL for BlockDropItemEvent ensures we tally the final drops after
// quality Fortune's LOW-priority re-rolls, while still running before Replant (HIGH) and
// Telekinesis (HIGHEST), which clears the event's item list to route drops into the inventory.
public class ResourceHunt implements Listener {

    public static final String TARGET_WORLD_KEY = "jass:resource";
    public static final String TARGET_WORLD_NETHER_KEY = "jass:resource_nether";
    private static final String REWARD_NAME = "resource";
    private static final int NUM_TIERS = 3;
    private static final Set<EntityType> EGG_LAYING_BREED_TYPES =
            Set.of(EntityType.TURTLE, EntityType.FROG, EntityType.SNIFFER);

    public enum Category {
        COLLECT, KILL, SMELT, ENCHANT, SHEAR, BREED, CRAFT, BARTER
    }

    private final Tweaks plugin;
    private final RewardManager rewardManager;

    private final String activeWorldKey;
    private final List<ResourceHuntTarget.Definition> worldTargets;
    private final ResourceHuntTarget.Definition initialTarget;

    private final Map<UUID, ResourceHuntTarget.Player> playerTargets = new ConcurrentHashMap<>();
    // Identity keys (Material or EntityType) already assigned, guarded by `this`.
    private final Set<Object> assignedKeys = new HashSet<>();
    // Players who have consumed their one free reroll this session.
    private final Set<UUID> usedFreeReroll = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Integer> progress = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> playerBars = new ConcurrentHashMap<>();
    // Highest tier index already granted for each player (0 = none, NUM_TIERS = all done).
    private final Map<UUID, Integer> tiersCompleted = new ConcurrentHashMap<>();

    // Marks an ItemStack that has already contributed to someone's progress. Counted items are
    // skipped on every counting path, which kills the chest-cheese where players stash counted
    // ores in a chest and rebreak the chest to redrop the same items.
    private final ResourceHuntPdc pdc;

    public ResourceHunt(Tweaks plugin, RewardManager rewardManager) {
        this.plugin = plugin;
        this.rewardManager = rewardManager;
        this.pdc = new ResourceHuntPdc(plugin);

        Map<String, List<ResourceHuntTarget.Definition>> entriesByWorld =
                new ResourceHuntConfigLoader(plugin).loadAllEntries();
        if (entriesByWorld.isEmpty()) {
            plugin.getLogger().warning("Resource Hunt has no valid targets in resource_hunt.yml; minigame disabled this session.");
            this.initialTarget = null;
            this.activeWorldKey = TARGET_WORLD_KEY;
            this.worldTargets = List.of();
        } else {
            List<String> worldKeys = new ArrayList<>(entriesByWorld.keySet());
            String pickedKey = worldKeys.get(ThreadLocalRandom.current().nextInt(worldKeys.size()));
            this.activeWorldKey = pickedKey;
            this.worldTargets = List.copyOf(entriesByWorld.get(pickedKey));
            ResourceHuntTarget.Definition picked = worldTargets.get(ThreadLocalRandom.current().nextInt(worldTargets.size()));
            this.initialTarget = picked;
            ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
            if (eventLog != null) eventLog.log(LoggingPaths.RESOURCEHUNT_ASSIGNED,
                    () -> "[ResourceHunt] (console) Resource Hunt active world this session: " + activeWorldKey
                            + " (chosen from " + entriesByWorld.size() + " populated world(s); "
                            + "initial target " + picked.category + ":" + targetIdName(picked)
                            + ", " + worldTargets.size() + " candidate target(s) in pool)");
        }

        // Pre-create the reward shell so admins can populate it via /reward edit resource even
        // before someone wins. Items added later are still picked up at /reward claim time
        // because RewardManager resolves items lazily.
        if (!rewardManager.rewardExists(REWARD_NAME)) {
            rewardManager.createReward(REWARD_NAME);
            ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
            if (eventLog != null) eventLog.log(LoggingPaths.RESOURCEHUNT_ASSIGNED,
                    () -> "[ResourceHunt] (console) Created empty '" + REWARD_NAME
                            + "' reward shell for Resource Hunt.");
        }
    }

    public boolean isActive() {
        return !worldTargets.isEmpty();
    }

    public String getActiveWorldKey() {
        return activeWorldKey;
    }

    boolean isFullyComplete(UUID uuid) {
        return tiersCompleted.getOrDefault(uuid, 0) >= NUM_TIERS;
    }

    static boolean isEggLayingBreedType(EntityType entityType) {
        return EGG_LAYING_BREED_TYPES.contains(entityType);
    }

    // Atomically resolves the player's session target, assigning one on first call. First caller
    // ever receives `initialTarget`; later callers prefer identifiers nobody else has been assigned
    // yet, falling back to a random draw from the full world pool if all identifiers are taken.
    private synchronized ResourceHuntTarget.Player assignTargetIfAbsent(UUID uuid) {
        ResourceHuntTarget.Player existing = playerTargets.get(uuid);
        if (existing != null) return existing;
        if (worldTargets.isEmpty()) return null;

        ResourceHuntTarget.Definition pick;
        if (assignedKeys.isEmpty()) {
            pick = initialTarget;
        } else {
            List<ResourceHuntTarget.Definition> available = new ArrayList<>();
            for (ResourceHuntTarget.Definition t : worldTargets) {
                if (!assignedKeys.contains(t.identityKey())) available.add(t);
            }
            List<ResourceHuntTarget.Definition> pool = available.isEmpty() ? worldTargets : available;
            pick = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }

        ResourceHuntTarget.Player pt = new ResourceHuntTarget.Player(pick, NUM_TIERS);
        playerTargets.put(uuid, pt);
        assignedKeys.add(pick.identityKey());
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) eventLog.log(LoggingPaths.RESOURCEHUNT_ASSIGNED,
                () -> "[ResourceHunt] " + ConsoleEventLog.actorLabel(null, uuid)
                        + " assigned " + pick.category + ":" + targetIdName(pick)
                        + " (tiers " + pt.tierThresholds[0] + "/" + pt.tierThresholds[1] + "/" + pt.tierThresholds[2]
                        + ", x" + pick.multiplier + ")");
        return pt;
    }

    ResourceHuntTarget.Player targetFor(UUID uuid) {
        return playerTargets.get(uuid);
    }

    int getProgress(java.util.UUID uuid) {
        return progress.getOrDefault(uuid, 0);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Block block = event.getBlock();
        String worldKey = block.getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;

        // Propagate taint from a player-placed block onto its drops, then clear the marker. This
        // closes the place-then-rebreak loop: drops from a tainted block come out pre-tagged and
        // therefore won't be counted by the loop below or by anything downstream.
        boolean wasTainted = pdc.consumeBlockTaint(block);
        if (wasTainted) {
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                if (pdc.markCounted(stack)) item.setItemStack(stack);
            }
        }

        if (!isActive()) return;
        UUID uuid = event.getPlayer().getUniqueId();
        ResourceHuntTarget.Player target = targetFor(uuid);
        if (target == null) return;
        if (target.material == null) return;
        // Block drops only credit COLLECT targets. Mining is one way of obtaining; smelt/enchant/
        // craft/etc. have their own dedicated paths.
        if (target.category != Category.COLLECT) return;
        boolean canCount = worldKey.contains(activeWorldKey) && !isFullyComplete(uuid);

        int gained = 0;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack.getType() != target.material) continue;
            if (pdc.isCounted(stack)) continue;
            if (canCount) gained += stack.getAmount();
            if (pdc.markCounted(stack)) item.setItemStack(stack);
        }

        if (gained <= 0) return;
        recordProgress(event.getPlayer(), target, gained);
    }

    // COLLECT acts as a wildcard: any supported obtain method credits a COLLECT target. Other
    // categories only credit when the action matches exactly.
    private static boolean categoryAllowsAction(Category target, Category action) {
        return target == Category.COLLECT || target == action;
    }

    /**
     * Backward-compatible entry point. Treats the drops as a COLLECT action (mining-style obtain).
     * Callers that perform a more specific action (e.g. Smelter) should use the overload that
     * takes a {@link Category} so SMELT/etc. targets can be credited too.
     */
    public void recordExternalDrops(Player player, Block block, Collection<ItemStack> drops) {
        recordExternalDrops(player, block, drops, Category.COLLECT);
    }

    /**
     * Hook for enchant code paths that route drops manually and therefore bypass
     * BlockDropItemEvent (Tunneller's surrounding block breaks, Smelter's setDropItems(false)
     * path, etc.). Call this once per broken block, BEFORE the block is set to AIR, with the
     * drops the player will actually receive (i.e. after smelter/fortune/silk processing).
     *
     * @param action the action category that produced these drops. Smelter passes {@link
     *               Category#SMELT}; vanilla-style block breaks pass {@link Category#COLLECT}.
     */
    public void recordExternalDrops(Player player, Block block, Collection<ItemStack> drops, Category action) {
        String worldKey = block.getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;

        // Same taint-propagation flow as onBlockDropItem, run unconditionally so that even a
        // tunnelled block placed by a player doesn't laundering counted items back into fresh
        // drops. Done before the active/completed guards so the marker is always cleared.
        if (pdc.consumeBlockTaint(block)) {
            for (ItemStack stack : drops) pdc.markCounted(stack);
        }

        if (!isActive()) return;
        UUID uuid = player.getUniqueId();
        ResourceHuntTarget.Player target = targetFor(uuid);
        if (target == null) return;
        if (target.material == null) return;
        if (!categoryAllowsAction(target.category, action)) return;
        boolean canCount = worldKey.contains(activeWorldKey) && !isFullyComplete(uuid);

        int gained = 0;
        for (ItemStack stack : drops) {
            if (stack == null || stack.getType() != target.material) continue;
            if (pdc.isCounted(stack)) continue;
            if (canCount) gained += stack.getAmount();
            pdc.markCounted(stack);
        }

        if (gained <= 0) return;
        recordProgress(player, target, gained);
    }

    // Credits one tally per matching mob kill (KILL category) and credits matching item drops from
    // mob deaths toward COLLECT targets. The two paths are independent: a KILL target never
    // reaches the drop-counting block, and a COLLECT target never triggers the kill-count block.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isActive()) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;
        String worldKey = entity.getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;

        Player killer = entity.getKiller();
        if (killer == null) return;
        UUID uuid = killer.getUniqueId();
        ResourceHuntTarget.Player target = targetFor(uuid);
        if (target == null) return;

        if (target.category == Category.KILL) {
            if (target.entityType == null || entity.getType() != target.entityType) return;
            if (!worldKey.equals(activeWorldKey) || isFullyComplete(uuid)) return;
            recordProgress(killer, target, 1);
            return;
        }

        // COLLECT path: tally any drops that match the player's target material, then mark each
        // counted drop so a later pickup doesn't double-credit. O(drops) — no entity-type gate,
        // so future config additions (any mob -> any material) work automatically.
        if (target.category == Category.COLLECT && target.material != null) {
            if (!worldKey.equals(activeWorldKey) || isFullyComplete(uuid)) return;
            int gained = 0;
            for (ItemStack drop : event.getDrops()) {
                if (drop == null || drop.getType() != target.material) continue;
                if (pdc.isCounted(drop)) continue;
                gained += drop.getAmount();
                pdc.markCounted(drop);
            }
            if (gained > 0) recordProgress(killer, target, gained);
        }
    }

    // SHEAR target: each shear action of the matching entity type credits one tally.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        if (!isActive()) return;
        Player player = event.getPlayer();
        String worldKey = event.getEntity().getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;
        ResourceHuntTarget.Player target = targetFor(player.getUniqueId());
        if (target == null) return;
        if (target.category != Category.SHEAR) return;
        if (target.entityType == null || event.getEntity().getType() != target.entityType) return;
        if (target.sheepColor != null) {
            if (!(event.getEntity() instanceof org.bukkit.entity.Sheep sheep)) return;
            if (sheep.getColor() != target.sheepColor) return;
        }
        if (!worldKey.equals(activeWorldKey) || isFullyComplete(player.getUniqueId())) return;
        recordProgress(player, target, 1);
    }

    // BREED target: each successful breed event whose breeder is the player credits one tally.
    // Natural breeding (e.g. villager-villager with no player feeder) has a null breeder and is
    // skipped.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (!isActive()) return;
        if (isEggLayingBreedType(event.getEntity().getType())) return;
        if (!(event.getBreeder() instanceof Player player)) return;
        String worldKey = event.getEntity().getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;
        ResourceHuntTarget.Player target = targetFor(player.getUniqueId());
        if (target == null) return;
        if (target.category != Category.BREED) return;
        if (target.entityType == null || event.getEntity().getType() != target.entityType) return;
        if (!worldKey.equals(activeWorldKey) || isFullyComplete(player.getUniqueId())) return;
        recordProgress(player, target, 1);
    }

    // BARTER target: piglin barter outcome items count toward the player who triggered the trade.
    // PiglinBarterEvent doesn't expose the bartering player, so we attribute to the nearest
    // player in the active resource world within an 8-block radius — the same range piglins use
    // to consider gold-throwers as trade partners.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiglinBarter(PiglinBarterEvent event) {
        if (!isActive()) return;
        LivingEntity piglin = event.getEntity();
        String worldKey = piglin.getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;

        Player player = nearestPlayer(piglin, 8 * 8);
        if (player == null) return;

        ResourceHuntTarget.Player target = targetFor(player.getUniqueId());
        if (target == null) return;
        if (target.category != Category.BARTER) return;
        if (target.material == null) return;
        if (!worldKey.equals(activeWorldKey) || isFullyComplete(player.getUniqueId())) return;

        int gained = 0;
        for (ItemStack stack : event.getOutcome()) {
            if (stack == null || stack.getType() != target.material) continue;
            gained += stack.getAmount();
        }
        if (gained > 0) recordProgress(player, target, gained);
    }

    private Player nearestPlayer(LivingEntity origin, double maxDistanceSquared) {
        Player best = null;
        double bestDist2 = maxDistanceSquared;
        Location loc = origin.getLocation();
        for (Player p : origin.getWorld().getPlayers()) {
            double d2 = p.getLocation().distanceSquared(loc);
            if (d2 < bestDist2) {
                bestDist2 = d2;
                best = p;
            }
        }
        return best;
    }

    // Counts items pulled from a furnace, blast furnace, or smoker — FurnaceExtractEvent fires
    // for all three Furnace block-entity subtypes when the player removes the cooked item from
    // the result slot. The event reports the *resolved* item type and amount the player took, so
    // this naturally credits e.g. iron ingot from raw iron without us having to introspect the
    // recipe or input stack.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (!isActive()) return;
        Player player = event.getPlayer();
        String worldKey = event.getBlock().getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;
        ResourceHuntTarget.Player target = targetFor(player.getUniqueId());
        if (target == null) return;
        if (target.material == null) return;
        if (event.getItemType() != target.material) return;
        // Furnace extraction credits both COLLECT (any obtain method) and SMELT (action-specific).
        if (!categoryAllowsAction(target.category, Category.SMELT)) return;

        int amount = event.getItemAmount();

        // The extracted items aren't in the player inventory yet during the event. Schedule a tag
        // pass next tick so the chest-cheese can't reuse smelted ingots. We tag regardless of
        // completion state — completed players' items still need to be inert for everyone else.
        Bukkit.getScheduler().runTask(plugin,
                () -> pdc.tagInventoryStacks(player, target.material, amount));

        if (!worldKey.equals(activeWorldKey) || isFullyComplete(player.getUniqueId())) return;
        recordProgress(player, target, amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!isActive()) return;
        String worldKey = event.getPlayer().getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item caughtItem)) return;

        ResourceHuntTarget.Player target = targetFor(event.getPlayer().getUniqueId());
        if (target == null) return;
        if (target.material == null) return;
        // Fishing is a COLLECT-only obtain path.
        if (target.category != Category.COLLECT) return;

        ItemStack stack = caughtItem.getItemStack();
        if (stack.getType() != target.material) return;
        if (pdc.isCounted(stack)) return;

        if (pdc.markCounted(stack)) caughtItem.setItemStack(stack);

        if (!worldKey.equals(activeWorldKey) || isFullyComplete(event.getPlayer().getUniqueId())) return;
        recordProgress(event.getPlayer(), target, stack.getAmount());
    }

    // Credits an ENCHANT target when the player enchants a matching item in the active resource
    // world. The item itself is tagged so it can't be funneled back through other counting paths.
    // Enchanted books are not handled here because the input material at event time is BOOK rather
    // than ENCHANTED_BOOK; configuring ENCHANT enchanted_book would never match by design.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        String worldKey = event.getEnchantBlock().getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;

        ItemStack stack = event.getItem();
        Material material = stack.getType();
        pdc.markCounted(stack);

        if (!isActive()) return;
        ResourceHuntTarget.Player target = targetFor(player.getUniqueId());
        if (target == null) return;
        if (target.material == null) return;
        if (target.material != material) return;
        if (!categoryAllowsAction(target.category, Category.ENCHANT)) return;
        if (!worldKey.equals(activeWorldKey) || isFullyComplete(player.getUniqueId())) return;
        recordProgress(player, target, 1);
    }

    /**
     * Records a CRAFT action toward the player's target. Called by {@link ResourceCraftListener}
     * from a deferred (next-tick) runnable so it can observe items that landed in the inventory
     * after the originating CraftItemEvent.
     */
    public void recordCraftProgress(Player player, Material material, int amount) {
        if (!isActive()) return;
        if (amount <= 0) return;
        UUID uuid = player.getUniqueId();
        ResourceHuntTarget.Player target = targetFor(uuid);
        if (target == null) return;
        if (target.material == null) return;
        if (target.material != material) return;
        if (!categoryAllowsAction(target.category, Category.CRAFT)) return;
        String worldKey = player.getWorld().getKey().asString();
        if (!worldKey.equals(activeWorldKey) || isFullyComplete(uuid)) return;
        recordProgress(player, target, amount);
    }

    /**
     * Records a successful egg-laying breed pairing for the player's matching target.
     */
    public void recordBreedProgress(Player player, EntityType type, int pairings) {
        if (!isActive()) return;
        if (pairings <= 0) return;
        UUID uuid = player.getUniqueId();
        ResourceHuntTarget.Player target = targetFor(uuid);
        if (target == null) return;
        if (target.category != Category.BREED) return;
        if (target.entityType != type) return;
        String worldKey = player.getWorld().getKey().asString();
        if (!worldKey.equals(activeWorldKey) || isFullyComplete(uuid)) return;
        recordProgress(player, target, pairings);
    }

    // When a counted item is placed as a block in the resource world, mark its position on the
    // chunk PDC so that whatever drops out of that block later inherits the counted tag and can't
    // re-credit progress. Skipped for Ageable / amethyst-bud-style blocks because their drops
    // genuinely come from growth, not from the originally-placed material.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!isResourceWorld(block.getWorld().getKey().asString())) return;
        pdc.taintPlacedBlock(block, event.getItemInHand());
    }

    // Public accessor for siblings in this package (e.g. ResourceCraftListener) that need
    // to apply the counted tag without re-implementing the PDC plumbing.
    public boolean markItemAsCounted(ItemStack stack) {
        return pdc.markCounted(stack);
    }

    public boolean isItemCounted(ItemStack stack) {
        return pdc.isCounted(stack);
    }

    public NamespacedKey countedTagKey() {
        return pdc.countedKey();
    }

    private void recordProgress(Player player, ResourceHuntTarget.Player target, int gained) {
        if (isFullyComplete(player.getUniqueId())) return;
        int newTotal = progress.merge(player.getUniqueId(), gained, Integer::sum);
        grantPendingTiers(player, target, newTotal);
        updateBossBar(player, target, newTotal);
    }

    // Grants every tier whose cumulative threshold the player has now crossed. A single
    // progress update can grant multiple tiers if the gain was large enough.
    private synchronized void grantPendingTiers(Player player, ResourceHuntTarget.Player target, int newTotal) {
        UUID uuid = player.getUniqueId();
        while (true) {
            int tier = tiersCompleted.getOrDefault(uuid, 0);
            if (tier >= NUM_TIERS) return;
            if (newTotal < target.tierThresholds[tier]) return;
            tiersCompleted.put(uuid, tier + 1);
            rewardManager.grantReward(uuid, REWARD_NAME);
            announceTierCompletion(player, target, tier + 1);
        }
    }

    private void announceTierCompletion(Player player, ResourceHuntTarget.Player target, int tier) {
        boolean isFinal = (tier >= NUM_TIERS);
        String verb = categoryVerb(target.category);
        String name = readableName(target);

        Component announcement;
        if (isFinal) {
            announcement = Messages.MINIGAMES.resourceHuntAllTiersComplete(player.getName(), NUM_TIERS);
        } else {
            int nextThreshold = target.tierThresholds[tier];
            announcement = Messages.MINIGAMES.resourceHuntTierComplete(
                    player.getName(), tier, verb, target.tierThresholds[tier - 1], name, nextThreshold);
        }
        Bukkit.broadcast(announcement);

        Component personal = Messages.MINIGAMES.resourceHuntPersonalCompletion(isFinal, tier);
        player.sendMessage(personal);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String worldKey = player.getWorld().getKey().asString();

        // Safety: if joining in a non-resource world, ensure no tags remain (e.g. if they logged
        // out in resource but were moved while offline). Done before the early-out so the strip
        // still runs even when the hunt is disabled this session.
        if (!isResourceWorld(worldKey)) {
            pdc.stripCountedTags(player);
        }

        if (!isActive()) return;
        ResourceHuntTarget.Player target = assignTargetIfAbsent(player.getUniqueId());
        if (target == null) return;

        if (activeWorldKey.equals(worldKey)) {
            showBossBar(player);
        }

        Component msg;
        if (isFullyComplete(player.getUniqueId())) {
            msg = Messages.MINIGAMES.resourceHuntAlreadyComplete();
        } else {
            String verb = categoryVerb(target.category);
            msg = Messages.MINIGAMES.resourceHuntTask(verb, readableName(target),
                    target.tierThresholds[0], target.tierThresholds[1], target.tierThresholds[2]);
        }
        player.sendMessage(msg);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        playerBars.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String fromWorldKey = event.getFrom().getKey().asString();
        String toWorldKey = player.getWorld().getKey().asString();

        // 1. Boss bar management for the active world
        if (activeWorldKey.equals(toWorldKey)) {
            showBossBar(player);
        } else {
            hideBossBar(player);
        }

        // 2. PDC removal when leaving ANY resource world. We strip tags from both inventory and
        // ender chest so that items brought out of the resource world (whether in pockets or
        // in a chest) can stack with survival items.
        if (isResourceWorld(fromWorldKey) && !isResourceWorld(toWorldKey)) {
            pdc.stripCountedTags(player);
        }
    }

    /**
     * Returns the set of target identity key strings available in the current session's world pool.
     * Keys match the YAML format: lowercase material names (e.g. "iron_ore"), entity type keys
     * (e.g. "minecraft:creeper"), or color-prefixed sheep keys (e.g. "red_sheep").
     * Returns an empty set when the hunt is inactive.
     */
    public Set<String> getAvailableTargetKeys() {
        Set<String> keys = new java.util.LinkedHashSet<>();
        for (ResourceHuntTarget.Definition t : worldTargets) {
            keys.add(targetIdName(t));
        }
        return java.util.Collections.unmodifiableSet(keys);
    }

    /**
     * Overrides the given player's current Resource Hunt target with the target identified by
     * {@code targetKey}. The key must match one of those returned by {@link #getAvailableTargetKeys()}.
     * Resets the player's progress and tiers to zero so they start fresh on the new target.
     *
     * @param playerUuid the UUID of the player whose target should be overridden
     * @param targetKey  the identity key string (as returned by {@link #getAvailableTargetKeys()})
     * @return {@code true} if the target was found and applied; {@code false} if the key is unknown
     */
    public synchronized boolean forceTarget(UUID playerUuid, String targetKey) {
        ResourceHuntTarget.Definition match = null;
        for (ResourceHuntTarget.Definition t : worldTargets) {
            if (targetIdName(t).equalsIgnoreCase(targetKey)) {
                match = t;
                break;
            }
        }
        if (match == null) return false;

        ResourceHuntTarget.Definition forcedTarget = match;
        ResourceHuntTarget.Player pt = new ResourceHuntTarget.Player(forcedTarget, NUM_TIERS);
        playerTargets.put(playerUuid, pt);
        progress.remove(playerUuid);
        tiersCompleted.remove(playerUuid);

        // Update the boss bar if the player is currently online and in the resource world.
        Player online = Bukkit.getPlayer(playerUuid);
        if (online != null) {
            hideBossBar(online);
            if (activeWorldKey.equals(online.getWorld().getKey().asString())) {
                showBossBar(online);
            }
        }

        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) eventLog.log(LoggingPaths.RESOURCEHUNT_FORCED,
                () -> "[ResourceHunt] (console) admin forced target " + forcedTarget.category + ":" + targetIdName(forcedTarget)
                        + " (tiers " + pt.tierThresholds[0] + "/" + pt.tierThresholds[1] + "/" + pt.tierThresholds[2]
                        + ") onto " + playerUuid);
        return true;
    }

    /**
     * Returns true if the player has already consumed their free reroll this session.
     */
    public boolean hasUsedFreeReroll(UUID uuid) {
        return usedFreeReroll.contains(uuid);
    }

    /**
     * Returns true if the player has crossed at least the first tier threshold of their current
     * target. Used by /reroll to lock players into a target once they've made meaningful progress.
     */
    public boolean hasCompletedFirstTier(UUID uuid) {
        return tiersCompleted.getOrDefault(uuid, 0) >= 1;
    }

    /**
     * Marks that the player has used their free reroll this session.
     */
    public void markFreeRerollUsed(UUID uuid) {
        usedFreeReroll.add(uuid);
    }

    /**
     * Assigns a new random target to the player, preferring one distinct from their current
     * target (if possible). Resets progress and tiers. Updates the boss bar if the player is
     * online in the resource world. Returns {@code true} on success, {@code false} if the hunt
     * is not active.
     */
    public synchronized boolean rerollTarget(Player player) {
        if (!isActive()) return false;
        UUID uuid = player.getUniqueId();

        // Collect the pool excluding the player's current target key (best-effort uniqueness).
        Object currentKey = null;
        ResourceHuntTarget.Player current = playerTargets.get(uuid);
        if (current != null) {
            // Reconstruct the identity key from the current PlayerTarget.
            if (current.sheepColor != null) {
                currentKey = java.util.Objects.hash(current.entityType, current.sheepColor);
            } else if (current.material != null) {
                currentKey = current.material;
            } else {
                currentKey = current.entityType;
            }
        }

        List<ResourceHuntTarget.Definition> available = new ArrayList<>();
        for (ResourceHuntTarget.Definition t : worldTargets) {
            if (!t.identityKey().equals(currentKey)) available.add(t);
        }
        List<ResourceHuntTarget.Definition> pool = available.isEmpty() ? worldTargets : available;
        ResourceHuntTarget.Definition pick = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));

        // Remove old assignment key before adding the new one.
        if (currentKey != null) assignedKeys.remove(currentKey);
        assignedKeys.add(pick.identityKey());

        ResourceHuntTarget.Player pt = new ResourceHuntTarget.Player(pick, NUM_TIERS);
        playerTargets.put(uuid, pt);
        progress.remove(uuid);
        tiersCompleted.remove(uuid);

        // Refresh the boss bar.
        hideBossBar(player);
        if (activeWorldKey.equals(player.getWorld().getKey().asString())) {
            showBossBar(player);
        }

        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog != null) eventLog.log(LoggingPaths.RESOURCEHUNT_REROLLED,
                () -> "[ResourceHunt] " + ConsoleEventLog.actorLabel(player.getName(), uuid)
                        + " rerolled target -> " + pick.category + ":" + targetIdName(pick)
                        + " (tiers " + pt.tierThresholds[0] + "/" + pt.tierThresholds[1]
                        + "/" + pt.tierThresholds[2] + ")");
        return true;
    }

    public static boolean isResourceWorld(String worldKey) {
        return ResourceHuntPlatform.isResourceWorld(worldKey);
    }

    /**
     * Generates a 5x5 bedrock platform at Y=64 in the specified world and returns the center spawn location.
     */
    public static Location createBedrockPlatform(org.bukkit.World world, int cx, int cz) {
        return ResourceHuntPlatform.createBedrockPlatform(world, cx, cz);
    }

    private void showBossBar(Player player) {
        if (!isActive()) return;
        if (isFullyComplete(player.getUniqueId())) return;
        ResourceHuntTarget.Player target = targetFor(player.getUniqueId());
        if (target == null) return;

        BossBar bar = playerBars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar b = BossBar.bossBar(
                    Messages.MINIGAMES.resourceHuntBossBarTitle(),
                    0.0f,
                    BossBar.Color.GREEN,
                    BossBar.Overlay.PROGRESS);
            updateBossBar(player, target, progress.getOrDefault(uuid, 0), b);
            return b;
        });

        player.showBossBar(bar);
    }

    private void hideBossBar(Player player) {
        BossBar bar = playerBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private void updateBossBar(Player player, ResourceHuntTarget.Player target, int current) {
        BossBar bar = playerBars.get(player.getUniqueId());
        if (bar != null) {
            updateBossBar(player, target, current, bar);
        }
    }

    // Renders the bar against the next unmet tier's threshold. Once all tiers are cleared the
    // bar is hidden and removed so the player's HUD is clean.
    private void updateBossBar(Player player, ResourceHuntTarget.Player target, int current, BossBar bar) {
        UUID uuid = player.getUniqueId();
        int tierIdx = tiersCompleted.getOrDefault(uuid, 0);
        if (tierIdx >= NUM_TIERS) {
            player.hideBossBar(bar);
            playerBars.remove(uuid);
            return;
        }
        int threshold = target.tierThresholds[tierIdx];
        float prog = Math.max(0.0f, Math.min(1.0f, (float) current / threshold));
        bar.progress(prog);
        bar.name(Messages.MINIGAMES.resourceHuntBossBarProgress(
                tierIdx + 1, categoryVerb(target.category), current, threshold, readableName(target)));
    }

    private static String categoryVerb(Category category) {
        return switch (category) {
            case COLLECT -> "Collect";
            case KILL -> "Kill";
            case SMELT -> "Smelt";
            case ENCHANT -> "Enchant";
            case SHEAR -> "Shear";
            case BREED -> "Breed";
            case CRAFT -> "Craft";
            case BARTER -> "Barter";
        };
    }

    private static String readableName(ResourceHuntTarget.Player target) {
        if (target.material != null) return prettify(target.material.name());
        if (target.entityType != null) {
            if (target.sheepColor != null)
                return prettify(target.sheepColor.name()) + " " + prettify(target.entityType.name());
            return prettify(target.entityType.name());
        }
        return "?";
    }

    private static String targetIdName(ResourceHuntTarget.Definition target) {
        if (target.material != null) return target.material.getKey().toString();
        if (target.entityType != null) {
            if (target.sheepColor != null)
                return target.sheepColor.name().toLowerCase() + "_" + target.entityType.getKey().getKey();
            return target.entityType.getKey().toString();
        }
        return "?";
    }

    private static String prettify(String enumName) {
        String spaced = enumName.toLowerCase().replace('_', ' ');
        StringBuilder out = new StringBuilder(spaced.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            if (c == ' ') {
                capitalizeNext = true;
                out.append(c);
            } else if (capitalizeNext) {
                out.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
