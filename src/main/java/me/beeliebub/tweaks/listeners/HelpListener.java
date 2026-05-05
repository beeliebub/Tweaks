package me.beeliebub.tweaks.listeners;

import me.beeliebub.tweaks.commands.HelpCommand;
import me.beeliebub.tweaks.commands.HelpCommand.HelpHolder;
import me.beeliebub.tweaks.managers.HelpManager;
import me.beeliebub.tweaks.managers.HelpManager.HelpArticle;
import me.beeliebub.tweaks.managers.HelpManager.HelpCategory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

// Routes clicks/drags inside the help GUI and shows a welcome tip on join.
// Kept separate from HelpCommand so the command class stays a pure command.
public class HelpListener implements Listener {

    private final HelpCommand helpCommand;
    private final HelpManager helpManager;

    public HelpListener(HelpCommand helpCommand, HelpManager helpManager) {
        this.helpCommand = helpCommand;
        this.helpManager = helpManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HelpHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        if (holder.isBackSlot(slot)) {
            helpCommand.openMainMenu(player);
            return;
        }

        String categoryId = holder.categoryAt(slot);
        if (categoryId != null) {
            HelpCategory category = helpManager.getCategory(categoryId);
            if (category != null) helpCommand.openCategoryMenu(player, category);
            return;
        }

        String articleId = holder.articleAt(slot);
        if (articleId != null) {
            HelpArticle article = helpManager.getArticle(articleId);
            if (article != null) {
                player.closeInventory();
                helpCommand.sendArticle(player, article);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof HelpHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Component openHelp = Component.text("/help", NamedTextColor.AQUA, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Open the help menu", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand("/help"));

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Need help? Type ", NamedTextColor.YELLOW).append(openHelp));

        HelpArticle tip = helpManager.getRandomArticle();
        if (tip != null) {
            Component readMore = Component.text(" [Read]", NamedTextColor.YELLOW)
                    .hoverEvent(HoverEvent.showText(Component.text("Open: " + tip.title(), NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand("/help " + tip.id()));
            player.sendMessage(Component.text("Tip: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text(tip.title(), NamedTextColor.WHITE))
                    .append(readMore));
        }
        player.sendMessage(Component.empty());
    }
}