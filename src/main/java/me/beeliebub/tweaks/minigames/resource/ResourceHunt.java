package me.beeliebub.tweaks.minigames.resource;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.minigames.RewardManager;
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
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

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
// target (Material + required amount). The first player to obtain the target via block drops,
// mob kills, or fishing in the jass:resource world is granted the "resource" reward via 
// RewardManager and the hunt closes for the rest of the session. Drops in any other world 
// are ignored entirely.
//
// To prevent cheesing, players are restricted from bringing disallowed items into the 
// resource world (enforced by /resource and /back).
//
// Listening at EventPriority.LOW for BlockDropItemEvent guarantees we tally the dropped items
// before Telekinesis (default NORMAL priority) clears the event's item list to route them to
// the player's inventory â€” so telekinesis users and non-telekinesis users are counted exactly
// once each, with no double-counting via the ground-pickup path.
public class ResourceHunt implements Listener {

    public static final String TARGET_WORLD_KEY = "jass:resource";
    public static final String REWARD_NAME = "resource";

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
                    + "' reward shell â€” populate with /reward edit " + REWARD_NAME);
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
        if (newTotal >= targetAmount) {
            declareWinner(player);
        }
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
                .append(Component.text(targetAmount + "Ã— " + readableName(targetMaterial), NamedTextColor.WHITE))
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
                    .append(Component.text(targetAmount + "Ã— " + readableName(targetMaterial), NamedTextColor.WHITE))
                    .append(Component.text(" in the resource world wins!", NamedTextColor.YELLOW))
                    .append(Component.text(" Use /resource to go there now!", NamedTextColor.GREEN))
                    .build();
        } else {
            String name = winnerName != null ? winnerName : "another player";
            msg = Component.text()
                    .append(Component.text("Resource Hunt: ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("already completed by ", NamedTextColor.YELLOW))
                    .append(Component.text(name, NamedTextColor.AQUA))
                    .append(Component.text(" â€” they gathered ", NamedTextColor.YELLOW))
                    .append(Component.text(targetAmount + "Ã— " + readableName(targetMaterial), NamedTextColor.WHITE))
                    .append(Component.text(".", NamedTextColor.YELLOW))
                    .build();
        }
        event.getPlayer().sendMessage(msg);
    }

    private static String readableName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }
}
