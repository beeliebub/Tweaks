package me.beeliebub.tweaks.skyblock.economy;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.skyblock.tracking.SkyblockPdc;
import me.beeliebub.tweaks.skyblock.tracking.TrackingScope;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Main-thread shop transaction service shared by commands and Dialog UI. */
public final class ShopService {
    private final ShopCatalog catalog;
    private final SkyblockEconomy economy;
    private final IslandManager islands;
    private final TrackingScope tracking;
    private final SkyblockPdc pdc;
    private final String worldKey;

    public ShopService(ShopCatalog catalog, SkyblockEconomy economy, IslandManager islands,
                       TrackingScope tracking, SkyblockPdc pdc, String worldKey) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.islands = Objects.requireNonNull(islands, "islands");
        this.tracking = tracking;
        this.pdc = pdc;
        this.worldKey = worldKey;
    }

    public BuyResult buy(Player player, Material material, int amount) {
        if (!canUse(player) || amount <= 0) return BuyResult.failed("Skyblock shop is unavailable");
        Island island = islands.forPlayer(player.getUniqueId()).orElse(null);
        ShopCatalog.Entry entry = catalog.entry(material).orElse(null);
        if (island == null) return BuyResult.failed("You do not have a Skyblock island");
        if (entry == null || !entry.buyable()) return BuyResult.failed("That item is not purchasable");
        double price = entry.buyPrice() * amount;
        if (!Double.isFinite(price)) return BuyResult.failed("The price is not representable");
        SkyblockEconomy.Mutation debit = economy.debit(island, price);
        if (debit != SkyblockEconomy.Mutation.APPLIED) return BuyResult.failed(debit == SkyblockEconomy.Mutation.INSUFFICIENT ? "Insufficient island funds" : "Purchase rejected");
        ItemStack item = new ItemStack(material, amount);
        if (pdc != null) pdc.markPurchased(item);
        ItemStack[] inventoryBefore = cloneContents(player.getInventory().getContents());
        try {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack drop : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), drop);
            return new BuyResult(true, "Purchased", amount, price);
        } catch (RuntimeException error) {
            player.getInventory().setContents(cloneContents(inventoryBefore));
            economy.credit(island, price);
            return BuyResult.failed("Purchase rolled back");
        }
    }

    public SellResult sell(Player player, SellMode mode, Material material) {
        if (!canUse(player)) {
            return SellResult.failed("Selling is only available in Skyblock");
        }
        Island island = islands.forPlayer(player.getUniqueId()).orElse(null);
        if (island == null) return SellResult.failed("You do not have a Skyblock island");
        Map<Integer, Integer> amounts = new HashMap<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < Math.min(36, contents.length); slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir() || !allowed(stack)) continue;
            if (mode == SellMode.HELD && slot != player.getInventory().getHeldItemSlot()) continue;
            if (mode == SellMode.ITEM && stack.getType() != material) continue;
            if (mode != SellMode.ALL && mode != SellMode.ITEM && mode != SellMode.HELD) continue;
            ShopCatalog.Entry entry = catalog.entry(stack.getType()).orElse(null);
            if (entry == null || !entry.sellable()) continue;
            amounts.merge(slot, stack.getAmount(), Integer::sum);
        }
        if (amounts.isEmpty()) return SellResult.failed("No sellable items found");
        double total = 0;
        for (Map.Entry<Integer, Integer> sold : amounts.entrySet()) total += catalog.entry(contents[sold.getKey()].getType()).orElseThrow().sellPrice() * sold.getValue();
        SkyblockEconomy.Mutation credit = economy.credit(island, total);
        if (credit != SkyblockEconomy.Mutation.APPLIED) return SellResult.failed("Sale rejected");
        int count = 0;
        for (Map.Entry<Integer, Integer> sold : amounts.entrySet()) {
            ItemStack stack = contents[sold.getKey()];
            count += sold.getValue();
            stack.setAmount(stack.getAmount() - sold.getValue());
            if (stack.getAmount() <= 0) contents[sold.getKey()] = null;
        }
        player.getInventory().setContents(contents);
        return new SellResult(true, "Sold", count, total);
    }

    private boolean inWorld(Player player) { return player != null && worldKey != null && worldKey.equals(player.getWorld().getKey().asString()); }

    public boolean canUse(Player player) {
        return inWorld(player) && player.isOnline() && player.hasPermission(Permissions.SKYBLOCK_USE)
                && islands.forPlayer(player.getUniqueId()).isPresent();
    }

    private boolean allowed(ItemStack stack) {
        if (pdc != null) return pdc.foreignStateAllowed(stack);
        ItemMeta meta = stack.getItemMeta();
        return meta == null || meta.getPersistentDataContainer().getKeys().isEmpty();
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        return copy;
    }

    public enum SellMode { HELD, ITEM, ALL }
    public record BuyResult(boolean success, String message, int amount, double price) {
        static BuyResult failed(String message) { return new BuyResult(false, message, 0, 0); }
    }
    public record SellResult(boolean success, String message, int amount, double price) {
        static SellResult failed(String message) { return new SellResult(false, message, 0, 0); }
    }
}
