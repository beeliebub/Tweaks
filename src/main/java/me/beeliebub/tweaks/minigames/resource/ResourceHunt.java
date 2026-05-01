package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.RewardManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

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
// mob kills, or fishing in the jass:resource world is granted the "resource" reward via
// RewardManager. The first player to complete is granted the reward 3 times; everyone else
// who completes afterward gets it once. The hunt remains open for the rest of the session so
// that anyone can still complete it; only the completing player's boss bar is removed when
// they finish. Drops in any other world are ignored entirely.
//
// To prevent cheesing, players are restricted from bringing disallowed items into the
// resource world (enforced by /resource and /back).
//
// Listening at EventPriority.LOW for BlockDropItemEvent guarantees we tally the dropped items
// before Telekinesis (default NORMAL priority) clears the event's item list to route them to
// the player's inventory
public class ResourceHunt implements Listener {

    public static final String TARGET_WORLD_KEY = "jass:resource";
    private static final String REWARD_NAME = "resource";
    private static final int FIRST_WINNER_REWARD_COUNT = 3;

    private final Tweaks plugin;
    private final RewardManager rewardManager;

    private final Material targetMaterial;
    private final int targetAmount;
    private final Map<UUID, Integer> progress = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> playerBars = new ConcurrentHashMap<>();
    private final Set<UUID> completed = ConcurrentHashMap.newKeySet();

    private UUID firstWinner = null;
    private String firstWinnerName = null;

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
                    + "' reward shell for Resource Hunt.");
        }
    }

    private Map.Entry<Material, Integer> pickRandomTarget() {
        File file = new File(plugin.getDataFolder(), "resource_hunt.yml");
        if (!file.exists()) {
            plugin.saveResource("resource_hunt.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map.Entry<Material, Integer>> entries = new ArrayList<>();

        for (String key : config.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) {
                plugin.getLogger().warning("resource_hunt.yml: unknown material '" + key + "', skipped.");
                continue;
            }
            int amount = config.getInt(key);
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
        return targetMaterial != null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!isActive()) return;
        if (completed.contains(event.getPlayer().getUniqueId())) return;
        Block block = event.getBlock();
        if (!TARGET_WORLD_KEY.equals(block.getWorld().getKey().asString())) return;

        int gained = 0;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack.getType() == targetMaterial) {
                gained += stack.getAmount();
            }
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
        if (!isActive()) return;
        if (completed.contains(player.getUniqueId())) return;
        if (!TARGET_WORLD_KEY.equals(block.getWorld().getKey().asString())) return;

        int gained = 0;
        for (ItemStack stack : drops) {
            if (stack != null && stack.getType() == targetMaterial) {
                gained += stack.getAmount();
            }
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
        if (!TARGET_WORLD_KEY.equals(entity.getWorld().getKey().asString())) return;

        Player killer = entity.getKiller();
        if (killer == null) return;
        if (completed.contains(killer.getUniqueId())) return;

        int gained = 0;
        for (ItemStack stack : event.getDrops()) {
            if (stack != null && stack.getType() == targetMaterial) {
                gained += stack.getAmount();
            }
        }

        if (gained <= 0) return;
        recordProgress(killer, gained);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!isActive()) return;
        if (completed.contains(event.getPlayer().getUniqueId())) return;
        if (!TARGET_WORLD_KEY.equals(event.getPlayer().getWorld().getKey().asString())) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item caughtItem)) return;

        ItemStack stack = caughtItem.getItemStack();
        if (stack.getType() == targetMaterial) {
            recordProgress(event.getPlayer(), stack.getAmount());
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

        if (TARGET_WORLD_KEY.equals(player.getWorld().getKey().asString())) {
            showBossBar(player);
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
        if (TARGET_WORLD_KEY.equals(player.getWorld().getKey().asString())) {
            showBossBar(player);
        } else {
            hideBossBar(player);
        }
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