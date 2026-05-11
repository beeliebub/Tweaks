package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

// Resource Hunt minigame.
//
// On every server start, one entry is picked at random from resource_hunt.yml as the active
// target (Material + base amount + tier multiplier). Each player builds toward three
// cumulative tier thresholds individually:
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
// Listening at EventPriority.LOW for BlockDropItemEvent guarantees we tally the dropped items
// before Telekinesis (default NORMAL priority) clears the event's item list to route them to
// the player's inventory.
public class ResourceHunt implements Listener {

    public static final String TARGET_WORLD_KEY = "jass:resource";
    public static final String TARGET_WORLD_NETHER_KEY = "jass:resource_nether";
    private static final String REWARD_NAME = "resource";
    private static final int NUM_TIERS = 3;
    private static final double DEFAULT_MULTIPLIER = 2.0;

    private final Tweaks plugin;
    private final RewardManager rewardManager;

    private final Material targetMaterial;
    private final int targetAmount;
    private final double targetMultiplier;
    private final int[] tierThresholds; // length = NUM_TIERS
    private final String activeWorldKey;

    private final Map<UUID, Integer> progress = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> playerBars = new ConcurrentHashMap<>();
    // Highest tier index already granted for each player (0 = none, NUM_TIERS = all done).
    private final Map<UUID, Integer> tiersCompleted = new ConcurrentHashMap<>();

    // Marks an ItemStack that has already contributed to someone's progress. Counted items are
    // skipped on every counting path, which kills the chest-cheese where players stash counted
    // ores in a chest and rebreak the chest to redrop the same items.
    private final NamespacedKey countedKey;

    public ResourceHunt(Tweaks plugin, RewardManager rewardManager) {
        this.plugin = plugin;
        this.rewardManager = rewardManager;
        this.countedKey = new NamespacedKey(plugin, "resource_hunt_counted");

        Target picked = pickRandomTarget();
        if (picked == null) {
            this.targetMaterial = null;
            this.targetAmount = 0;
            this.targetMultiplier = DEFAULT_MULTIPLIER;
            this.tierThresholds = new int[NUM_TIERS];
            this.activeWorldKey = TARGET_WORLD_KEY;
        } else {
            this.targetMaterial = picked.material;
            this.targetAmount = picked.amount;
            this.targetMultiplier = picked.multiplier;
            this.tierThresholds = computeTierThresholds(targetAmount, targetMultiplier);
            this.activeWorldKey = picked.worldKey;
            plugin.getLogger().info("Resource Hunt target this session: "
                    + targetMaterial.getKey() + " in " + activeWorldKey
                    + " (tiers " + tierThresholds[0] + "/" + tierThresholds[1] + "/" + tierThresholds[2]
                    + ", multiplier x" + targetMultiplier + ")");
        }

        // Pre-create the reward shell so admins can populate it via /reward edit resource even
        // before someone wins. Items added later are still picked up at /reward claim time
        // because RewardManager resolves items lazily.
        if (!rewardManager.rewardExists(REWARD_NAME)) {
            rewardManager.createReward(REWARD_NAME);
            plugin.getLogger().info("Created empty '" + REWARD_NAME
                    + "' reward shell for Resource Hunt.");
        }
    }

    private static int[] computeTierThresholds(int base, double multiplier) {
        int[] thresholds = new int[NUM_TIERS];
        double scaled = base;
        for (int i = 0; i < NUM_TIERS; i++) {
            int t = (int) Math.round(scaled);
            // Guarantee strict monotonic growth so a multiplier of 1.0 still presents three tiers.
            if (i > 0 && t <= thresholds[i - 1]) t = thresholds[i - 1] + 1;
            thresholds[i] = t;
            scaled *= multiplier;
        }
        return thresholds;
    }

    private static class Target {
        final Material material;
        final int amount;
        final double multiplier;
        final String worldKey;

        Target(Material material, int amount, double multiplier, String worldKey) {
            this.material = material;
            this.amount = amount;
            this.multiplier = multiplier;
            this.worldKey = worldKey;
        }
    }

    private Target pickRandomTarget() {
        File file = new File(plugin.getDataFolder(), "resource_hunt.yml");
        if (!file.exists()) {
            plugin.saveResource("resource_hunt.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Target> entries = new ArrayList<>();

        loadTargets(config, "overworld", TARGET_WORLD_KEY, entries);
        loadTargets(config, "nether", TARGET_WORLD_NETHER_KEY, entries);

        if (entries.isEmpty()) {
            plugin.getLogger().warning("Resource Hunt has no valid targets in resource_hunt.yml; minigame disabled this session.");
            return null;
        }
        return entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
    }

    // Accepts each entry as either a bare integer (legacy: amount only, multiplier defaults to
    // DEFAULT_MULTIPLIER) or as the string "<amount>:<multiplier>" (tiered form).
    private void loadTargets(YamlConfiguration config, String section, String worldKey, List<Target> entries) {
        if (!config.isConfigurationSection(section)) return;
        Map<String, Object> values = config.getConfigurationSection(section).getValues(false);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Material mat = Material.matchMaterial(entry.getKey());
            if (mat == null) {
                plugin.getLogger().warning("resource_hunt.yml (" + section + "): unknown material '" + entry.getKey() + "', skipped.");
                continue;
            }

            int amount;
            double multiplier;
            Object raw = entry.getValue();
            if (raw instanceof Number num) {
                amount = num.intValue();
                multiplier = DEFAULT_MULTIPLIER;
            } else if (raw instanceof String str) {
                String[] parts = str.split(":", 2);
                try {
                    amount = Integer.parseInt(parts[0].trim());
                    multiplier = parts.length >= 2 ? Double.parseDouble(parts[1].trim()) : DEFAULT_MULTIPLIER;
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("resource_hunt.yml (" + section + "): '" + entry.getKey() + "' has invalid value '" + str + "', skipped.");
                    continue;
                }
            } else {
                plugin.getLogger().warning("resource_hunt.yml (" + section + "): '" + entry.getKey() + "' has invalid amount, skipped.");
                continue;
            }

            if (amount <= 0) {
                plugin.getLogger().warning("resource_hunt.yml (" + section + "): '" + entry.getKey() + "' has non-positive amount, skipped.");
                continue;
            }
            if (multiplier < 1.0) {
                plugin.getLogger().warning("resource_hunt.yml (" + section + "): '" + entry.getKey() + "' has multiplier < 1.0; clamping to 1.0.");
                multiplier = 1.0;
            }

            entries.add(new Target(mat, amount, multiplier, worldKey));
        }
    }

    public boolean isActive() {
        return targetMaterial != null;
    }

    public String getActiveWorldKey() {
        return activeWorldKey;
    }

    private boolean isFullyComplete(UUID uuid) {
        return tiersCompleted.getOrDefault(uuid, 0) >= NUM_TIERS;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Block block = event.getBlock();
        String worldKey = block.getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;

        // Propagate taint from a player-placed block onto its drops, then clear the marker. This
        // closes the place-then-rebreak loop: drops from a tainted block come out pre-tagged and
        // therefore won't be counted by the loop below or by anything downstream.
        boolean wasTainted = consumeBlockTaint(block);
        if (wasTainted) {
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                if (markCounted(stack)) item.setItemStack(stack);
            }
        }

        if (!isActive()) return;
        boolean canCount = worldKey.equals(activeWorldKey) && !isFullyComplete(event.getPlayer().getUniqueId());

        int gained = 0;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack.getType() != targetMaterial) continue;
            if (isCounted(stack)) continue;
            if (canCount) gained += stack.getAmount();
            if (markCounted(stack)) item.setItemStack(stack);
        }

        if (gained <= 0) return;
        recordProgress(event.getPlayer(), gained);
    }

    /**
     * Hook for enchant code paths that route drops manually and therefore bypass
     * BlockDropItemEvent (Tunneller's surrounding block breaks, Smelter's setDropItems(false)
     * path, etc.). Call this once per broken block, BEFORE the block is set to AIR, with the
     * drops the player will actually receive (i.e. after smelter/fortune/silk processing).
     */
    public void recordExternalDrops(Player player, Block block, Collection<ItemStack> drops) {
        String worldKey = block.getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;

        // Same taint-propagation flow as onBlockDropItem, run unconditionally so that even a
        // tunnelled block placed by a player doesn't laundering counted items back into fresh
        // drops. Done before the active/completed guards so the marker is always cleared.
        if (consumeBlockTaint(block)) {
            for (ItemStack stack : drops) markCounted(stack);
        }

        if (!isActive()) return;
        boolean canCount = worldKey.equals(activeWorldKey) && !isFullyComplete(player.getUniqueId());

        int gained = 0;
        for (ItemStack stack : drops) {
            if (stack == null || stack.getType() != targetMaterial) continue;
            if (isCounted(stack)) continue;
            if (canCount) gained += stack.getAmount();
            markCounted(stack);
        }

        if (gained <= 0) return;
        recordProgress(player, gained);
    }

    // Counts mob-loot drops toward Resource Hunt. HIGH priority so we read drops AFTER quality
    // looting (LOW), Egg Collector (NORMAL), and any other plugin that mutates event.getDrops().
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isActive()) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;
        String worldKey = entity.getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;

        Player killer = entity.getKiller();
        if (killer == null) return;
        boolean canCount = worldKey.equals(activeWorldKey) && !isFullyComplete(killer.getUniqueId());

        int gained = 0;
        for (ItemStack stack : event.getDrops()) {
            if (stack == null || stack.getType() != targetMaterial) continue;
            if (isCounted(stack)) continue;
            if (canCount) gained += stack.getAmount();
            markCounted(stack);
        }

        if (gained <= 0) return;
        recordProgress(killer, gained);
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
        if (event.getItemType() != targetMaterial) return;

        int amount = event.getItemAmount();

        // The extracted items aren't in the player inventory yet during the event. Schedule a tag
        // pass next tick so the chest-cheese can't reuse smelted ingots. We tag regardless of
        // completion state — completed players' items still need to be inert for everyone else.
        Bukkit.getScheduler().runTask(plugin,
                () -> tagInventoryStacks(player, targetMaterial, amount));

        if (!worldKey.equals(activeWorldKey) || isFullyComplete(player.getUniqueId())) return;
        recordProgress(player, amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!isActive()) return;
        String worldKey = event.getPlayer().getWorld().getKey().asString();
        if (!isResourceWorld(worldKey)) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item caughtItem)) return;

        ItemStack stack = caughtItem.getItemStack();
        if (stack.getType() != targetMaterial) return;
        if (isCounted(stack)) return;

        if (markCounted(stack)) caughtItem.setItemStack(stack);

        if (!worldKey.equals(activeWorldKey) || isFullyComplete(event.getPlayer().getUniqueId())) return;
        recordProgress(event.getPlayer(), stack.getAmount());
    }

    // When a counted item is placed as a block in the resource world, mark its position on the
    // chunk PDC so that whatever drops out of that block later inherits the counted tag and can't
    // re-credit progress. Skipped for Ageable / amethyst-bud-style blocks because their drops
    // genuinely come from growth, not from the originally-placed material.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!isResourceWorld(block.getWorld().getKey().asString())) return;
        if (!isCounted(event.getItemInHand())) return;
        if (isGrowthExempt(block)) return;
        block.getChunk().getPersistentDataContainer().set(
                blockTaintKey(block), PersistentDataType.BYTE, (byte) 1);
    }

    private boolean isGrowthExempt(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable) return true;
        return switch (block.getType()) {
            case SMALL_AMETHYST_BUD, MEDIUM_AMETHYST_BUD, LARGE_AMETHYST_BUD, AMETHYST_CLUSTER -> true;
            default -> false;
        };
    }

    // Returns true if the block had a taint marker (and clears it as a side effect).
    private boolean consumeBlockTaint(Block block) {
        PersistentDataContainer chunkPdc = block.getChunk().getPersistentDataContainer();
        NamespacedKey key = blockTaintKey(block);
        if (!chunkPdc.has(key, PersistentDataType.BYTE)) return false;
        chunkPdc.remove(key);
        return true;
    }

    // Per-block PDC key on the chunk container. NamespacedKey only allows [a-z0-9/._-], so y is
    // serialized with an 'n'/'p' prefix to encode the sign without breaking validation.
    private NamespacedKey blockTaintKey(Block block) {
        int lx = block.getX() & 0xF;
        int lz = block.getZ() & 0xF;
        int y = block.getY();
        String yEnc = (y < 0) ? "n" + (-y) : "p" + y;
        return new NamespacedKey(plugin, "rh_taint_" + lx + "_" + yEnc + "_" + lz);
    }

    private boolean isCounted(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(countedKey, PersistentDataType.BYTE);
    }

    // Returns true if meta was modified (caller may need to push the stack back onto an Item entity).
    private boolean markCounted(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(countedKey, PersistentDataType.BYTE)) return false;
        pdc.set(countedKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return true;
    }

    // Tags up to `amount` units of `material` in the player's inventory. Used after furnace
    // extraction, where the extracted ItemStack isn't directly accessible during the event.
    private void tagInventoryStacks(Player player, Material material, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (remaining <= 0) break;
            if (stack == null || stack.getType() != material) continue;
            if (isCounted(stack)) continue;
            markCounted(stack);
            remaining -= stack.getAmount();
        }
    }

    private void recordProgress(Player player, int gained) {
        if (isFullyComplete(player.getUniqueId())) return;
        int newTotal = progress.merge(player.getUniqueId(), gained, Integer::sum);
        grantPendingTiers(player, newTotal);
        updateBossBar(player, newTotal);
    }

    // Grants every tier whose cumulative threshold the player has now crossed. A single
    // progress update can grant multiple tiers if the gain was large enough.
    private synchronized void grantPendingTiers(Player player, int newTotal) {
        UUID uuid = player.getUniqueId();
        while (true) {
            int tier = tiersCompleted.getOrDefault(uuid, 0);
            if (tier >= NUM_TIERS) return;
            if (newTotal < tierThresholds[tier]) return;
            tiersCompleted.put(uuid, tier + 1);
            rewardManager.grantReward(uuid, REWARD_NAME);
            announceTierCompletion(player, tier + 1);
        }
    }

    private void announceTierCompletion(Player player, int tier) {
        boolean isFinal = (tier >= NUM_TIERS);

        Component announcement;
        if (isFinal) {
            announcement = Component.text()
                    .append(Component.text("[Resource Hunt] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(player.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" cleared all ", NamedTextColor.YELLOW))
                    .append(Component.text(NUM_TIERS + " tiers", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" of the resource hunt!", NamedTextColor.YELLOW))
                    .build();
        } else {
            int nextThreshold = tierThresholds[tier];
            announcement = Component.text()
                    .append(Component.text("[Resource Hunt] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(player.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" reached Tier " + tier + " (", NamedTextColor.YELLOW))
                    .append(Component.text(tierThresholds[tier - 1] + "x " + readableName(targetMaterial), NamedTextColor.WHITE))
                    .append(Component.text("). Next tier: ", NamedTextColor.YELLOW))
                    .append(Component.text(nextThreshold + "x", NamedTextColor.GOLD))
                    .append(Component.text(".", NamedTextColor.YELLOW))
                    .build();
        }
        Bukkit.broadcast(announcement);

        Component personal = Component.text()
                .append(Component.text(isFinal ? "All tiers complete! " : "Tier " + tier + " complete. ", NamedTextColor.GOLD))
                .append(Component.text("Use ", NamedTextColor.YELLOW))
                .append(Component.text("/reward claim", NamedTextColor.GOLD))
                .append(Component.text(" to collect.", NamedTextColor.YELLOW))
                .build();
        player.sendMessage(personal);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (targetMaterial == null) return;
        Player player = event.getPlayer();
        String worldKey = player.getWorld().getKey().asString();

        if (activeWorldKey.equals(worldKey)) {
            showBossBar(player);
        }

        // Safety: if joining in a non-resource world, ensure no tags remain (e.g. if they logged
        // out in resource but were moved while offline).
        if (!isResourceWorld(worldKey)) {
            stripCountedTags(player);
        }

        Component msg;
        if (isFullyComplete(player.getUniqueId())) {
            msg = Component.text()
                    .append(Component.text("Resource Hunt: ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("you've already completed all tiers this session.", NamedTextColor.YELLOW))
                    .build();
        } else {
            msg = Component.text()
                    .append(Component.text("Resource Hunt: ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("gather ", NamedTextColor.YELLOW))
                    .append(Component.text(readableName(targetMaterial), NamedTextColor.WHITE))
                    .append(Component.text(" in the resource world to clear tiers ", NamedTextColor.YELLOW))
                    .append(Component.text(tierThresholds[0] + "/" + tierThresholds[1] + "/" + tierThresholds[2], NamedTextColor.GOLD))
                    .append(Component.text(". Each tier grants a reward.", NamedTextColor.YELLOW))
                    .append(Component.text(" Use /resource to go there now!", NamedTextColor.GREEN))
                    .build();
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
            stripCountedTags(player);
        }
    }

    private void stripCountedTags(Player player) {
        // Scan main inventory
        for (ItemStack stack : player.getInventory().getContents()) {
            removeCountedTag(stack);
        }
        // Scan ender chest
        for (ItemStack stack : player.getEnderChest().getContents()) {
            removeCountedTag(stack);
        }
    }

    private void removeCountedTag(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(countedKey, PersistentDataType.BYTE)) {
            pdc.remove(countedKey);
            stack.setItemMeta(meta);
        }
    }

    public static boolean isResourceWorld(String worldKey) {
        return TARGET_WORLD_KEY.equals(worldKey) || TARGET_WORLD_NETHER_KEY.equals(worldKey);
    }

    /**
     * Generates a 5x5 bedrock platform at Y=64 in the specified world and returns the center spawn location.
     */
    public static Location createBedrockPlatform(org.bukkit.World world, int cx, int cz) {
        int y = 64; // Middle-ish of the Nether

        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                world.getBlockAt(x, y, z).setType(Material.BEDROCK);
                world.getBlockAt(x, y + 1, z).setType(Material.AIR);
                world.getBlockAt(x, y + 2, z).setType(Material.AIR);
            }
        }
        return new Location(world, cx + 0.5, y + 1, cz + 0.5);
    }

    private void showBossBar(Player player) {
        if (!isActive()) return;
        if (isFullyComplete(player.getUniqueId())) return;

        BossBar bar = playerBars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar b = BossBar.bossBar(
                    Component.text("Resource Hunt", NamedTextColor.GREEN, TextDecoration.BOLD),
                    0.0f,
                    BossBar.Color.GREEN,
                    BossBar.Overlay.PROGRESS);
            updateBossBar(player, progress.getOrDefault(uuid, 0), b);
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

    private void updateBossBar(Player player, int current) {
        BossBar bar = playerBars.get(player.getUniqueId());
        if (bar != null) {
            updateBossBar(player, current, bar);
        }
    }

    // Renders the bar against the next unmet tier's threshold. Once all tiers are cleared the
    // bar is hidden and removed so the player's HUD is clean.
    private void updateBossBar(Player player, int current, BossBar bar) {
        UUID uuid = player.getUniqueId();
        int tierIdx = tiersCompleted.getOrDefault(uuid, 0);
        if (tierIdx >= NUM_TIERS) {
            player.hideBossBar(bar);
            playerBars.remove(uuid);
            return;
        }
        int threshold = tierThresholds[tierIdx];
        float prog = Math.max(0.0f, Math.min(1.0f, (float) current / threshold));
        bar.progress(prog);
        bar.name(Component.text(
                "Resource Hunt Tier " + (tierIdx + 1) + ": " + current + "/" + threshold + " " + readableName(targetMaterial),
                NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    private static String readableName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }
}