package me.beeliebub.tweaks.enchantments.quality;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

// TODO: This class is simply not functioning at all, revisit idea in data pack rather than plugin.

// Handles quality luck of the sea. 12 quality steps (4 tiers x 3 levels) evenly spaced
// from ~8.3% to 100% treasure catch rate. When the roll succeeds, the catch is overridden
// with a pull directly from the vanilla treasure loot table.
public class LuckOfSeaQualityListener implements Listener {

    private static final int TOTAL_STEPS = 12;
    private final QualityRegistry registry;
    private final Logger logger;

    public LuckOfSeaQualityListener(QualityRegistry registry, Tweaks plugin) {
        this.registry = registry;
        this.logger = plugin.getLogger();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();
        ItemStack rod = getRod(player, event.getHand());
        if (rod == null) return;

        QualityRegistry.QualityInfo quality = registry.getToolQuality(rod, "luck_of_the_sea");
        if (quality == null) return;

        // Step = tierOrdinal * 3 + enchantLevel (1-indexed: uncommon I = step 1, legendary III = step 12)
        int step = quality.tier().ordinal() * 3 + quality.level();
        double treasureChance = (double) step / TOTAL_STEPS;

        if (ThreadLocalRandom.current().nextDouble() >= treasureChance) return;

        // Generate treasure BEFORE checking caught entity — if generation fails
        // we shouldn't cancel or modify anything
        ItemStack treasure = generateTreasure(player, quality.level());
        if (treasure == null) {
            logger.warning("[QualityLotS] Treasure generation returned null — "
                    + "loot table may be missing or empty");
            return;
        }

        // Try to replace the caught entity's item directly
        Entity caught = event.getCaught();
        if (caught instanceof Item itemEntity) {
            itemEntity.setItemStack(treasure);
        } else {
            // getCaught() returned null or non-Item — cancel the fish catch
            // and give the treasure directly to the player
            logger.info("[QualityLotS] getCaught() was "
                    + (caught == null ? "null" : caught.getType())
                    + " — using direct inventory fallback");
            event.setCancelled(true);
            for (ItemStack leftover : player.getInventory().addItem(treasure).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private ItemStack getRod(Player player, EquipmentSlot hand) {
        // Use event-provided hand when available (correct even with dual-wielded rods)
        if (hand != null) {
            ItemStack rod = player.getInventory().getItem(hand);
            if (rod != null && rod.getType() == Material.FISHING_ROD) return rod;
        }
        // Fallback: search both hands (covers cases where getHand() returns null)
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.getType() == Material.FISHING_ROD) return main;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off.getType() == Material.FISHING_ROD) return off;
        return null;
    }

    private ItemStack generateTreasure(Player player, int luckLevel) {
        LootTable treasureTable = Bukkit.getLootTable(LootTables.FISHING_TREASURE.getKey());
        if (treasureTable == null) {
            logger.warning("[QualityLotS] LootTable for FISHING_TREASURE is null — "
                    + "key was: " + LootTables.FISHING_TREASURE.getKey());
            return null;
        }

        // Luck affects relative treasure weights (e.g. enchanted books vs. name tags).
        // Vanilla Luck of the Sea adds 1 luck per level, so mirror that here.
        LootContext context = new LootContext.Builder(player.getLocation())
                .luck(luckLevel)
                .build();

        Collection<ItemStack> loot = treasureTable.populateLoot(
                ThreadLocalRandom.current(), context);

        if (loot.isEmpty()) {
            logger.warning("[QualityLotS] populateLoot returned empty collection");
        }

        for (ItemStack item : loot) {
            if (item != null && !item.getType().isAir()) return item;
        }
        return null;
    }
}
