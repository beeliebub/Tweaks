package me.beeliebub.tweaks.listeners;

import me.beeliebub.tweaks.commands.HelpCommand;
import me.beeliebub.tweaks.managers.HelpManager;
import me.beeliebub.tweaks.managers.HelpManager.HelpArticle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

// Sends the welcome /help tip on join. Inventory click/drag routing was removed when
// the /help menu was converted to Paper Dialogs — dialog actions handle their own
// click routing, so no InventoryClickEvent / InventoryDragEvent handlers remain here.
public class HelpListener implements Listener {

    private final HelpManager helpManager;

    @SuppressWarnings("unused") // Retained for constructor compatibility with Tweaks wiring.
    public HelpListener(HelpCommand helpCommand, HelpManager helpManager) {
        this.helpManager = helpManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Component openHelp = Component.text("/help", NamedTextColor.AQUA, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Open the help menu", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand("/help"));

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Need help? Type ", NamedTextColor.YELLOW).append(openHelp));

        HelpArticle tip = helpManager.getRandomArticle(player);
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
