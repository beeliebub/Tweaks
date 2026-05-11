package me.beeliebub.tweaks.commands;

import me.beeliebub.tweaks.managers.HelpManager;
import me.beeliebub.tweaks.managers.HelpManager.HelpArticle;
import me.beeliebub.tweaks.managers.HelpManager.HelpCategory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// /help [section] — opens a GUI menu for browsing help categories and sends individual
// articles in chat. The GUI uses HelpHolder for per-slot id mappings so click routing
// is driven by data, not by parallel slot arrays. Inventory and join events live in
// HelpListener (separation of command vs. listener responsibilities).
//
// Layout: both menus are 54 slots. Articles and categories carry their own slot
// indices, so HelpCommand just iterates the data and places items at those slots.
// Category menus reserve slot 45 for a red-glass "Back" tile; the main menu uses
// gray-glass decorative panes around the center icons.
public class HelpCommand implements CommandExecutor, TabCompleter {

    private static final int MENU_SIZE = 54;
    private static final int CATEGORY_BACK_SLOT = 45;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final HelpManager helpManager;

    public HelpCommand(HelpManager helpManager) {
        this.helpManager = helpManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /help.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            openMainMenu(player);
            return true;
        }

        String target = args[0].toLowerCase();
        HelpCategory category = helpManager.getCategory(target);
        if (category != null) {
            if (category.hasVisibleArticles(player)) {
                openCategoryMenu(player, category);
            } else {
                player.sendMessage(Component.text("You don't have permission to view this category.", NamedTextColor.RED));
            }
            return true;
        }

        HelpArticle article = helpManager.getArticle(target);
        if (article != null) {
            if (article.permission() == null || player.hasPermission(article.permission())) {
                sendArticle(player, article);
            } else {
                player.sendMessage(Component.text("You don't have permission to view this article.", NamedTextColor.RED));
            }
            return true;
        }

        player.sendMessage(Component.text("Unknown help section: " + target, NamedTextColor.RED));
        return true;
    }

    public void openMainMenu(Player player) {
        HelpHolder holder = new HelpHolder(null);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<gradient:#FF9A00:#9863E7:#5489FF><b>Help</b></gradient>"));
        holder.attach(inv);

        boolean hasPerms = false;
        HelpCategory permsCat = helpManager.getCategory("permissions");
        if (permsCat != null && permsCat.hasVisibleArticles(player)) {
            hasPerms = true;
        }

        for (HelpCategory category : helpManager.getCategories()) {
            if (!category.hasVisibleArticles(player)) continue;
            int slot = category.slot();

            if (hasPerms) {
                switch (category.id()) {
                    case "features" -> slot = 29;
                    case "minigames" -> slot = 31;
                    case "permissions" -> slot = 33;
                }
            }

            inv.setItem(slot, categoryIcon(category));
            holder.mapCategory(slot, category.id());
        }

        fillBorder(inv);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.0f);
    }

    public void openCategoryMenu(Player player, HelpCategory category) {
        HelpHolder holder = new HelpHolder(category.id());
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE,
                MM.deserialize("<gradient:" + category.gradient() + "><b>" + category.title() + "</b></gradient>"));
        holder.attach(inv);

        for (HelpArticle article : category.articles()) {
            if (article.permission() != null && !player.hasPermission(article.permission())) continue;
            int slot = article.slot();
            inv.setItem(slot, articleIcon(article));
            holder.mapArticle(slot, article.id());
        }

        inv.setItem(CATEGORY_BACK_SLOT, backIcon());
        holder.markBackSlot(CATEGORY_BACK_SLOT);

        fillBorder(inv);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.1f);
    }

    public void sendArticle(Player player, HelpArticle article) {
        Component divider = Component.text("─────────────────", NamedTextColor.DARK_GRAY);
        player.sendMessage(Component.empty());
        player.sendMessage(divider);
        player.sendMessage(MM.deserialize("<gradient:" + article.gradient() + "><b>" + article.title() + "</b></gradient>"));
        player.sendMessage(Component.empty());

        for (Component line : article.content()) {
            player.sendMessage(line);
        }

        List<String> related = article.relatedArticles();
        if (related != null && !related.isEmpty()) {
            Component line = Component.text("See also: ", NamedTextColor.DARK_AQUA);
            boolean first = true;
            for (String refId : related) {
                HelpArticle ref = helpManager.getArticle(refId);
                if (ref == null) continue;
                if (!first) line = line.append(Component.text(", ", NamedTextColor.DARK_GRAY));
                line = line.append(Component.text("[" + ref.title() + "]", NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(Component.text("Open: " + ref.title(), NamedTextColor.GRAY)))
                        .clickEvent(ClickEvent.runCommand("/help " + refId)));
                first = false;
            }
            if (!first) {
                player.sendMessage(Component.empty());
                player.sendMessage(line);
            }
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("[Open Help Menu]", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Back to the help GUI", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand("/help")));
        player.sendMessage(divider);

        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.0f);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();

        String partial = args[0].toLowerCase();
        List<String> options = new ArrayList<>();
        for (HelpCategory c : helpManager.getCategories()) {
            if (c.hasVisibleArticles(player)) {
                options.add(c.id());
                for (HelpArticle a : c.articles()) {
                    if (a.permission() == null || player.hasPermission(a.permission())) {
                        options.add(a.id());
                    }
                }
            }
        }
        return options.stream().filter(o -> o.startsWith(partial)).toList();
    }

    private ItemStack categoryIcon(HelpCategory category) {
        Component name = MM.deserialize("<gradient:" + category.gradient() + "><b>" + category.title() + "</b></gradient>");
        Component lore = Component.text("Click to view ", NamedTextColor.GRAY)
                .append(MM.deserialize("<gradient:" + category.gradient() + "><b>" + category.title().toLowerCase() + "</b></gradient>"))
                .append(Component.text(" articles!", NamedTextColor.GRAY));
        return makeIcon(category.icon(), name, List.of(lore));
    }

    private ItemStack articleIcon(HelpArticle article) {
        Component name = MM.deserialize("<gradient:" + article.gradient() + "><b>" + article.title() + "</b></gradient>");
        Component lore = Component.text("Click to read ", NamedTextColor.GRAY)
                .append(MM.deserialize("<gradient:" + article.gradient() + "><b>" + article.title().toLowerCase() + "</b></gradient>"))
                .append(Component.text(" information!", NamedTextColor.GRAY));
        ItemStack item = makeIcon(article.icon(), name, List.of(lore));

        // Use andrewkm skin for whack-an-andrew article
        if (article.id().equals("whack") && item.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer("andrewkm"));
            item.setItemMeta(skullMeta);
        }

        return item;
    }

    private ItemStack backIcon() {
        return makeIcon(Material.RED_STAINED_GLASS_PANE,
                Component.text("Back", NamedTextColor.RED, TextDecoration.BOLD),
                List.of(Component.text("Return to the main menu", NamedTextColor.GRAY)));
    }

    private ItemStack makeIcon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> cleaned = new ArrayList<>(lore.size());
            for (Component c : lore) cleaned.add(c.decoration(TextDecoration.ITALIC, false));
            meta.lore(cleaned);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorder(Inventory inv) {
        ItemStack filler = makeIcon(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        int size = inv.getSize();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean edge = row == 0 || row == rows - 1 || col == 0 || col == 8;
            if (edge && inv.getItem(slot) == null) inv.setItem(slot, filler);
        }
    }

    // Holder doubles as click router. Stores explicit slot -> id maps so handlers
    // never need to recompute layout from a parallel slot array.
    public static final class HelpHolder implements InventoryHolder {
        private final String parentCategoryId;
        private final Map<Integer, String> slotToCategory = new HashMap<>();
        private final Map<Integer, String> slotToArticle = new HashMap<>();
        private int backSlot = -1;
        private Inventory inventory;

        HelpHolder(@Nullable String parentCategoryId) {
            this.parentCategoryId = parentCategoryId;
        }

        void attach(Inventory inventory) { this.inventory = inventory; }
        void mapCategory(int slot, String id) { slotToCategory.put(slot, id); }
        void mapArticle(int slot, String id) { slotToArticle.put(slot, id); }
        void markBackSlot(int slot) { this.backSlot = slot; }

        public @Nullable String categoryAt(int slot) { return slotToCategory.get(slot); }
        public @Nullable String articleAt(int slot) { return slotToArticle.get(slot); }
        public boolean isBackSlot(int slot) { return slot == backSlot; }
        public boolean isMainMenu() { return parentCategoryId == null; }

        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }
}