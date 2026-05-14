package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// /condense [all] compacts 9x granular -> 1x block for items that have a vanilla
// reverse (block -> 9x granular). Without "all", only the held material is condensed.
//
// Eligibility guard: items with display name, lore, enchants, or custom model data are
// always skipped so admin/custom items are not destroyed. PDC tags are also rejected
// EXCEPT for the resource_hunt_counted tag (the only plugin-managed tag that should
// survive a craft cycle). Tagged source items produce tagged output blocks; the
// ResourceCraftListener round-trips this if a player later uncrafts the block in a
// resource world.
public class CondenseCommand implements CommandExecutor, TabCompleter {

    static final Map<Material, Material> CONDENSE_MAP;
    static {
        Map<Material, Material> m = new EnumMap<>(Material.class);
        m.put(Material.IRON_INGOT, Material.IRON_BLOCK);
        m.put(Material.GOLD_INGOT, Material.GOLD_BLOCK);
        m.put(Material.DIAMOND, Material.DIAMOND_BLOCK);
        m.put(Material.EMERALD, Material.EMERALD_BLOCK);
        m.put(Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK);
        m.put(Material.LAPIS_LAZULI, Material.LAPIS_BLOCK);
        m.put(Material.REDSTONE, Material.REDSTONE_BLOCK);
        m.put(Material.COAL, Material.COAL_BLOCK);
        m.put(Material.COPPER_INGOT, Material.COPPER_BLOCK);
        m.put(Material.RAW_IRON, Material.RAW_IRON_BLOCK);
        m.put(Material.RAW_GOLD, Material.RAW_GOLD_BLOCK);
        m.put(Material.RAW_COPPER, Material.RAW_COPPER_BLOCK);
        m.put(Material.SLIME_BALL, Material.SLIME_BLOCK);
        m.put(Material.WHEAT, Material.HAY_BLOCK);
        m.put(Material.BONE_MEAL, Material.BONE_BLOCK);
        m.put(Material.NETHER_WART, Material.NETHER_WART_BLOCK);
        CONDENSE_MAP = Collections.unmodifiableMap(m);
    }

    private final ResourceHunt resourceHunt;

    public CondenseCommand(ResourceHunt resourceHunt) {
        this.resourceHunt = resourceHunt;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /condense.", NamedTextColor.RED));
            return true;
        }

        boolean all = args.length > 0 && args[0].equalsIgnoreCase("all");

        if (all) {
            int totalBlocks = 0;
            for (Material granular : CONDENSE_MAP.keySet()) {
                totalBlocks += condenseMaterial(player, granular);
            }
            if (totalBlocks == 0) {
                player.sendMessage(Component.text("Nothing to condense.", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("Condensed " + totalBlocks + " block" + (totalBlocks == 1 ? "" : "s") + ".",
                        NamedTextColor.GREEN));
            }
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        Material granular = held.getType();
        if (!CONDENSE_MAP.containsKey(granular)) {
            player.sendMessage(Component.text("Held item is not condensable. Try /condense all.",
                    NamedTextColor.RED));
            return true;
        }

        int blocks = condenseMaterial(player, granular);
        if (blocks == 0) {
            player.sendMessage(Component.text("Not enough " + granular.name().toLowerCase(Locale.ROOT) + " to condense (need 9).",
                    NamedTextColor.YELLOW));
        } else {
            Material block = CONDENSE_MAP.get(granular);
            player.sendMessage(Component.text("Condensed " + blocks + " " + block.name().toLowerCase(Locale.ROOT) + ".",
                    NamedTextColor.GREEN));
        }
        return true;
    }

    private int condenseMaterial(Player player, Material granular) {
        Material block = CONDENSE_MAP.get(granular);
        PlayerInventory inv = player.getInventory();

        // Two pools so the resource_hunt_counted tag round-trips: tagged sources produce
        // tagged blocks, untagged sources produce plain blocks. Mixing pools would either
        // launder tags off counted items or falsely tag clean items.
        int taggedTotal = 0;
        int untaggedTotal = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isCondensable(stack, granular)) continue;
            if (hasResourceHuntTag(stack)) {
                taggedTotal += stack.getAmount();
            } else {
                untaggedTotal += stack.getAmount();
            }
        }
        int taggedBlocks = taggedTotal / 9;
        int untaggedBlocks = untaggedTotal / 9;
        if (taggedBlocks == 0 && untaggedBlocks == 0) return 0;

        int taggedToRemove = taggedBlocks * 9;
        int untaggedToRemove = untaggedBlocks * 9;
        for (int i = 0; i < inv.getSize() && (taggedToRemove > 0 || untaggedToRemove > 0); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isCondensable(stack, granular)) continue;
            boolean tagged = hasResourceHuntTag(stack);
            int budget = tagged ? taggedToRemove : untaggedToRemove;
            if (budget <= 0) continue;
            int amount = stack.getAmount();
            int remove = Math.min(amount, budget);
            if (remove == amount) {
                inv.setItem(i, null);
            } else {
                stack.setAmount(amount - remove);
            }
            if (tagged) taggedToRemove -= remove;
            else untaggedToRemove -= remove;
        }

        if (untaggedBlocks > 0) {
            giveStack(player, new ItemStack(block, untaggedBlocks));
        }
        if (taggedBlocks > 0) {
            ItemStack taggedStack = new ItemStack(block, taggedBlocks);
            resourceHunt.markItemAsCounted(taggedStack);
            giveStack(player, taggedStack);
        }
        return taggedBlocks + untaggedBlocks;
    }

    private void giveStack(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }

    private boolean isCondensable(ItemStack stack, Material expected) {
        if (stack == null || stack.getType() != expected) return false;
        if (!stack.hasItemMeta()) return true;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return true;
        if (meta.hasDisplayName()) return false;
        if (meta.hasLore()) return false;
        if (meta.hasEnchants()) return false;
        if (meta.hasCustomModelData()) return false;
        Set<NamespacedKey> keys = meta.getPersistentDataContainer().getKeys();
        if (keys.isEmpty()) return true;
        // Allow exactly one PDC key, and only if it's the resource hunt counted tag.
        return keys.size() == 1 && keys.contains(resourceHunt.countedTagKey());
    }

    private boolean hasResourceHuntTag(ItemStack stack) {
        return resourceHunt.isItemCounted(stack);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1 && "all".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("all");
        }
        return List.of();
    }
}
