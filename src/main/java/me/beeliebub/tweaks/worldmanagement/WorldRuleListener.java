package me.beeliebub.tweaks.worldmanagement;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.HotPathEventBuffer;
import me.beeliebub.tweaks.logging.LoggingPaths;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;

import java.util.List;
import java.util.Locale;

// Consolidated world-rule listener. Previously: TrampleListener, PortalListener,
// MobGriefListener, SpawnerEggListener, VillagerTradeListener. Each is a small
// rule-enforcement listener with no shared state — bundling them removes file
// bloat without coupling the rules.
public class WorldRuleListener implements Listener {

    private static final String SPAWNER_EGG_CONFIG_KEY = "spawner-egg-disabled-mobs";
    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";
    private static final int COST_SLOT_A = 0;
    private static final int COST_SLOT_B = 1;

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

    // ─── Villager trade blocking (was VillagerTradeListener) ──────────────────

    // Blocks emeralds carrying lore from being placed in the cost slots of a
    // regular Villager's merchant GUI. Wandering Traders are sibling type, so
    // their trades are unaffected.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory mi)) return;
        if (!isRegularVillagerMerchant(mi.getMerchant())) return;

        InventoryAction action = event.getAction();
        int rawSlot = event.getRawSlot();
        boolean topSlotClicked = rawSlot == COST_SLOT_A || rawSlot == COST_SLOT_B;

        switch (action) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                if (topSlotClicked && hasLoreEmerald(event.getCursor())) {
                    rejectLoreEmerald(event);
                }
            }
            case MOVE_TO_OTHER_INVENTORY -> {
                Inventory clicked = event.getClickedInventory();
                if (clicked != null && clicked != mi && hasLoreEmerald(event.getCurrentItem())) {
                    rejectLoreEmerald(event);
                }
            }
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                if (topSlotClicked) {
                    HumanEntity who = event.getWhoClicked();
                    int hotbar = event.getHotbarButton();
                    if (hotbar >= 0 && who instanceof Player p) {
                        ItemStack hotbarItem = p.getInventory().getItem(hotbar);
                        if (hasLoreEmerald(hotbarItem)) {
                            rejectLoreEmerald(event);
                        }
                    }
                }
            }
            default -> { /* nothing else can place a new item into the cost slots */ }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory mi)) return;
        if (!isRegularVillagerMerchant(mi.getMerchant())) return;
        if (!hasLoreEmerald(event.getOldCursor())) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot == COST_SLOT_A || rawSlot == COST_SLOT_B) {
                event.setCancelled(true);
                notifyRejection(event.getWhoClicked());
                return;
            }
        }
    }

    private boolean isRegularVillagerMerchant(Merchant merchant) {
        return merchant instanceof Villager;
    }

    private boolean hasLoreEmerald(ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().hasLore();
    }

    private void rejectLoreEmerald(InventoryClickEvent event) {
        event.setCancelled(true);
        notifyRejection(event.getWhoClicked());
    }

    private void notifyRejection(HumanEntity who) {
        if (!(who instanceof Player player)) return;
        player.sendMessage(Component.text(
                "This villager won't accept emeralds with lore. Try a wandering trader.",
                NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }
}
