package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.RewardManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
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
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

// Resource Hunt minigame.
//
// On every server start, one entry is picked at random from resource_hunt.yml as the active
// target (Material + required amount). Each player who reaches the threshold via block drops,
// mob kills, fishing, or smelting (furnace, blast furnace, smoker) in the jass:resource or
// jass:resource_nether world is granted the "resource" reward via RewardManager. The first
// player to complete is granted the reward 3 times; everyone else who completes afterward gets
// it once. The hunt remains open for the rest of the session so that anyone can still complete
// it; only the completing player's boss bar is removed when they finish. Drops/extracts in any
// other world are ignored entirely.
//
// To prevent cheesing, players are restricted from bringing disallowed items into the
// resource world (enforced by /resource and /back).
//
// Listening at EventPriority.LOW for BlockDropItemEvent guarantees we tally the dropped items
// before Telekinesis (default NORMAL priority) clears the event's item list to route them to
// the player's inventory
public class ResourceHunt implements Listener {

    public static final String TARGET_WORLD_KEY = "jass:resource";
    public static final String TARGET_WORLD_NETHER_KEY = "jass:resource_nether";
    private static final String REWARD_NAME = "resource";
    private static final int FIRST_WINNER_REWARD_COUNT = 3;

    private final Tweaks plugin;
    private final RewardManager rewardManager;

    private final Material targetMaterial;
    private final int targetAmount;
    private final String activeWorldKey;

    private final Map<UUID, Integer> progress = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> playerBars = new ConcurrentHashMap<>();
    private final Set<UUID> completed = ConcurrentHashMap.newKeySet();

    // Marks an ItemStack that has already contributed to someone's progress. Counted items are
    // skipped on every counting path, which kills the chest-cheese where players stash counted
    // ores in a chest and rebreak the chest to redrop the same items.
    private final NamespacedKey countedKey;

    private UUID firstWinner = null;
    private String firstWinnerName = null;

    public ResourceHunt(Tweaks plugin, RewardManager rewardManager) {
        this.plugin = plugin;
        this.rewardManager = rewardManager;
        this.countedKey = new NamespacedKey(plugin, "resource_hunt_counted");

        Target picked = pickRandomTarget();
        if (picked == null) {
            this.targetMaterial = null;
            this.targetAmount = 0;
            this.activeWorldKey = TARGET_WORLD_KEY;
        } else {
            this.targetMaterial = picked.material;
            this.targetAmount = picked.amount;
            this.activeWorldKey = picked.worldKey;
            plugin.getLogger().info("Resource Hunt target this session: "
                    + targetAmount + "x " + targetMaterial.getKey() + " in " + activeWorldKey);
        }

        // Pre-create the reward shell so admins can populate it via /reward edit resource even
        // before someone wins. Items added after a winner is declared are still picked up at
        // /reward claim time because RewardManager resolves items lazily.
        if (!rewardManager.rewardExists(REWARD_NAME)) {
            rewardManager.createReward(REWARD_NAME);
            plugin.getLogger().info("Created empty '" + REWARD_NAME
                    + "' reward shell for Resource Hunt.");
        }
    }

    private static class Target {
        Material material;
        int amount;
        String worldKey;

        Target(Material material, int amount, String worldKey) {
            this.material = material;
            this.amount = amount;
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

    private void loadTargets(YamlConfiguration config, String section, String worldKey, List<Target> entries) {
        if (!config.isConfigurationSection(section)) return;
        Map<String, Object> values = config.getConfigurationSection(section).getValues(false);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Material mat = Material.matchMaterial(entry.getKey());
            if (mat == null) {
                plugin.getLogger().warning("resource_hunt.yml (" + section + "): unknown material '" + entry.getKey() + "', skipped.");
                continue;
            }
            if (!(entry.getValue() instanceof Number num)) {
                plugin.getLogger().warning("resource_hunt.yml (" + section + "): '" + entry.getKey() + "' has invalid amount, skipped.");
                continue;
            }
            int amount = num.intValue();
            if (amount <= 0) {
                plugin.getLogger().warning("resource_hunt.yml (" + section + "): '" + entry.getKey() + "' has non-positive amount, skipped.");
                continue;
            }
            entries.add(new Target(mat, amount, worldKey));
        }
    }

    public boolean isActive() {
        return targetMaterial != null;
    }

    public String getActiveWorldKey() {
        return activeWorldKey;
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
        boolean canCount = worldKey.equals(activeWorldKey) && !completed.contains(event.getPlayer().getUniqueId());

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
        boolean canCount = worldKey.equals(activeWorldKey) && !completed.contains(player.getUniqueId());

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
        boolean canCount = worldKey.equals(activeWorldKey) && !completed.contains(killer.getUniqueId());

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

        if (!worldKey.equals(activeWorldKey) || completed.contains(player.getUniqueId())) return;
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

        if (!worldKey.equals(activeWorldKey) || completed.contains(event.getPlayer().getUniqueId())) return;
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
        int newTotal = progress.merge(player.getUniqueId(), gained, Integer::sum);
        updateBossBar(player, newTotal);
        if (newTotal >= targetAmount) {
            declareCompletion(player);
        }
    }

    private synchronized void declareCompletion(Player player) {
        if (!completed.add(player.getUniqueId())) return;

        boolean isFirst = (firstWinner == null);
        int rewardCount;
        if (isFirst) {
            firstWinner = player.getUniqueId();
            firstWinnerName = player.getName();
            rewardCount = FIRST_WINNER_REWARD_COUNT;
        } else {
            rewardCount = 1;
        }

        for (int i = 0; i < rewardCount; i++) {
            rewardManager.grantReward(player.getUniqueId(), REWARD_NAME);
        }

        // Only hide this player's boss bar; the hunt stays open for everyone else.
        BossBar bar = playerBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }

        Component announcement;
        if (isFirst) {
            announcement = Component.text()
                    .append(Component.text("[Resource Hunt] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(player.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" was first to gather ", NamedTextColor.YELLOW))
                    .append(Component.text(targetAmount + "x " + readableName(targetMaterial), NamedTextColor.WHITE))
                    .append(Component.text(" and earned a ", NamedTextColor.YELLOW))
                    .append(Component.text("triple reward", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("! Others can still complete the hunt for a single reward.", NamedTextColor.YELLOW))
                    .build();
        } else {
            announcement = Component.text()
                    .append(Component.text("[Resource Hunt] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(player.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" also completed the hunt!", NamedTextColor.YELLOW))
                    .build();
        }
        Bukkit.broadcast(announcement);

        Component personal = Component.text()
                .append(Component.text("Use ", NamedTextColor.YELLOW))
                .append(Component.text("/reward claim", NamedTextColor.GOLD))
                .append(Component.text(rewardCount > 1
                                ? " to collect your " + rewardCount + " rewards."
                                : " to collect your reward.",
                        NamedTextColor.YELLOW))
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

        boolean playerDone = completed.contains(player.getUniqueId());

        Component msg;
        if (firstWinner == null) {
            msg = Component.text()
                    .append(Component.text("Resource Hunt: ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("first to gather ", NamedTextColor.YELLOW))
                    .append(Component.text(targetAmount + "x " + readableName(targetMaterial), NamedTextColor.WHITE))
                    .append(Component.text(" in the resource world wins a triple reward!", NamedTextColor.YELLOW))
                    .append(Component.text(" Use /resource to go there now!", NamedTextColor.GREEN))
                    .build();
        } else if (playerDone) {
            msg = Component.text()
                    .append(Component.text("Resource Hunt: ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("you've already completed this session's hunt.", NamedTextColor.YELLOW))
                    .build();
        } else {
            String name = firstWinnerName != null ? firstWinnerName : "another player";
            msg = Component.text()
                    .append(Component.text("Resource Hunt: ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(name, NamedTextColor.AQUA))
                    .append(Component.text(" finished first — complete the hunt yourself for a single reward!", NamedTextColor.YELLOW))
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

    private void showBossBar(Player player) {
        if (!isActive()) return;
        if (completed.contains(player.getUniqueId())) return;

        BossBar bar = playerBars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar b = BossBar.bossBar(
                    Component.text("Resource Hunt: Collect " + targetAmount + "x " + readableName(targetMaterial), NamedTextColor.GREEN, TextDecoration.BOLD),
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

    private void updateBossBar(Player player, int current, BossBar bar) {
        float prog = Math.max(0.0f, Math.min(1.0f, (float) current / targetAmount));
        bar.progress(prog);
        bar.name(Component.text("Resource Hunt: " + current + "/" + targetAmount + " " + readableName(targetMaterial), NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    private static String readableName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }
}