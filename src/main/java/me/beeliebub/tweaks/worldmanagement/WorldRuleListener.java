package me.beeliebub.tweaks.worldmanagement;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.HotPathEventBuffer;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

// Consolidated world-rule listener. Previously: TrampleListener, PortalListener,
// MobGriefListener, SpawnerEggListener, VillagerTradeListener. Each is a small
// rule-enforcement listener with no shared state — bundling them removes file
// bloat without coupling the rules.
public class WorldRuleListener implements Listener {

    private static final String SPAWNER_EGG_CONFIG_KEY = "spawner-egg-disabled-mobs";
    private static final String TRADE_XP_DISABLED_KEY = "worldmanagement.villager-trade-xp-disabled";
    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";

    private final Tweaks plugin;
    private final ProtectionManager protection;

    public WorldRuleListener(Tweaks plugin, ProtectionManager protection) {
        this.plugin = plugin;
        this.protection = protection;
    }

    // ─── Trample (was TrampleListener) ────────────────────────────────────────

    @EventHandler
    public void onPlayerTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.FARMLAND) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityTrample(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (block != null && block.getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }

    // ─── Portal blocking (was PortalListener) ─────────────────────────────────

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        String worldKey = event.getFrom().getWorld().getKey().asString().toLowerCase();
        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL && isEndPortalDisabled(worldKey)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("The End is disabled in this world!", NamedTextColor.RED));
            logPortalBlocked(event.getPlayer(), cause, worldKey);
        } else if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && isNetherPortalDisabled(worldKey)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Nether portals do not work in this world.", NamedTextColor.RED));
            logPortalBlocked(event.getPlayer(), cause, worldKey);
        }
    }

    private void logPortalBlocked(Player player, PlayerTeleportEvent.TeleportCause cause, String worldKey) {
        ConsoleEventLog eventLog = ConsoleEventLog.forPlugin(plugin);
        if (eventLog == null) return;
        String actorName = player.getName();
        java.util.UUID actorId = player.getUniqueId();
        eventLog.logHot(LoggingPaths.WORLDMANAGEMENT_PORTAL,
                new HotPathEventBuffer.HotKey(actorId,
                        worldKey + ":" + cause.name().toLowerCase(Locale.ROOT), null), actorName);
    }

    // Live read (no constructor-time caching) so a /tconfig edit to disabled-end-portal-worlds
    // takes effect immediately - matches the spawner-egg list's pattern below.
    private boolean isEndPortalDisabled(String worldKey) {
        for (String entry : plugin.getConfig().getStringList("disabled-end-portal-worlds")) {
            if (entry.equalsIgnoreCase(worldKey)) return true;
        }
        return false;
    }

    // Live read, same pattern as isEndPortalDisabled - replaces the previously hardcoded
    // RESOURCE_WORLD_KEY/RESOURCE_NETHER_WORLD_KEY pair (which already covered both keys here;
    // the drift this config-migration fixes was in TeleportCommandManager's separate /sethome
    // check, not this one - see teleport/CLAUDE.md).
    private boolean isNetherPortalDisabled(String worldKey) {
        for (String entry : plugin.getConfig().getStringList("disabled-nether-portal-worlds")) {
            if (entry.equalsIgnoreCase(worldKey)) return true;
        }
        return false;
    }

    // ─── Mob griefing (was MobGriefListener) ──────────────────────────────────

    // Default-deny on creeper block destruction and enderman block manipulation,
    // with per-region opt-in via the MOB_GRIEFING protection flag. Priority HIGH
    // puts us after ProtectionManager's LOWEST listeners which already filtered
    // EXPLOSION-protected blocks out of the creeper blockList.
    @EventHandler(priority = EventPriority.HIGH)
    public void onCreeperExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Creeper)) return;
        event.blockList().removeIf(b ->
                !protection.isExplicitlyAllowed(b.getLocation(), null, RegionFlag.MOB_GRIEFING));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEndermanGrief(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Enderman)) return;
        if (protection.isExplicitlyAllowed(
                event.getBlock().getLocation(), null, RegionFlag.MOB_GRIEFING)) {
            return;
        }
        event.setCancelled(true);
    }

    // ─── Spawner egg blocking (was SpawnerEggListener) ────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        String matKey = item.getType().getKey().getKey();
        if (!matKey.endsWith(SPAWN_EGG_SUFFIX)) return;
        String mob = matKey.substring(0, matKey.length() - SPAWN_EGG_SUFFIX.length());

        List<String> disabled = plugin.getConfig().getStringList(SPAWNER_EGG_CONFIG_KEY);
        for (String entry : disabled) {
            if (entry.equalsIgnoreCase(mob)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text(
                        "That spawn egg cannot be used on spawners on this server."
                ).color(NamedTextColor.RED));
                return;
            }
        }
    }

    // ─── Villager trade access (was VillagerTradeListener) ────────────────────

    // A regular Villager will not open its trade menu for a player carrying any
    // lore-bearing emerald or emerald block. That covers the plugin's Resource
    // Rupee currency (a renamed, lore-tagged emerald / emerald block) as well as
    // any custom emerald an admin or datapack has given lore, without needing to
    // match specific names. Wandering Traders are a separate entity type and keep
    // working normally. A player must stash the currency (ender chest, a container,
    // drop it) before a regular villager will trade.
    //
    // The block is on the interaction, not the inventory-open event: cancelling
    // the open after the villager has already engaged the player leaves it stuck
    // in a "busy" state with no API to clear it. Stopping the right-click here
    // means the villager is never engaged in the first place. A name tag renames
    // the villager rather than opening a trade, so that interaction is left alone.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;

        Player player = event.getPlayer();
        ItemStack inHand = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (inHand.getType() == Material.NAME_TAG) return;

        if (!carriesLoreEmerald(player)) return;

        event.setCancelled(true);
        // The event fires once per hand for a single physical click; greet only the
        // main hand so the player gets one message and one sound.
        if (event.getHand() == EquipmentSlot.HAND) {
            player.sendMessage(Component.text(
                    "You can't trade with a villager while carrying Resource Rupees or other lore-marked emeralds. Wandering traders still work.",
                    NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    // Scans the 36 main inventory slots plus the off-hand slot (armor slots cannot
    // hold emeralds). A lore-bearing emerald or emerald block is treated as currency
    // regardless of its name, since the lore is what separates Resource Rupees (and
    // admin / datapack custom emeralds) from the plain item a villager expects as
    // payment.
    private boolean carriesLoreEmerald(Player player) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isLoreEmerald(stack)) return true;
        }
        return isLoreEmerald(player.getInventory().getItemInOffHand());
    }

    private boolean isLoreEmerald(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        if (type != Material.EMERALD && type != Material.EMERALD_BLOCK) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().hasLore();
    }

    // ─── Trade XP suppression ────────────────────────────────────────────────

    // Suppresses the experience-orb reward a player gets for completing a trade
    // with a villager or wandering trader. The merchant still accrues its own
    // trade experience and levels up as normal - only the player-facing orb drop
    // is removed. Config key is read live so a /tconfig toggle applies with no
    // restart, matching the other rules in this class.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerTrade(PlayerTradeEvent event) {
        if (plugin.getConfig().getBoolean(TRADE_XP_DISABLED_KEY, true)) {
            event.setRewardExp(false);
        }
    }
}
