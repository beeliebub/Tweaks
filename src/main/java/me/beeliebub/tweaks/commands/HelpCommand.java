package me.beeliebub.tweaks.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.managers.HelpManager;
import me.beeliebub.tweaks.managers.HelpManager.HelpArticle;
import me.beeliebub.tweaks.managers.HelpManager.HelpCategory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// /help [section] — opens a Paper Dialog for browsing help categories and sends
// individual articles in chat. Categories and articles render as ActionButton rows;
// clicks route through DialogAction.customClick, which chains into sub-dialogs or
// closes the dialog and prints the article. PlayerJoin handling lives in HelpListener.
public class HelpCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int BUTTON_WIDTH = 250;

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
        List<ActionButton> buttons = new ArrayList<>();
        for (HelpCategory category : helpManager.getCategories()) {
            if (!category.hasVisibleArticles(player)) continue;
            buttons.add(buildCategoryButton(category));
        }

        Component title = MM.deserialize("<gradient:#FF9A00:#9863E7:#5489FF><b>Help</b></gradient>")
                .decoration(TextDecoration.ITALIC, false);

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .build())
                .type(DialogType.multiAction(buttons, null, 2)));

        player.showDialog(dialog);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.0f);
    }

    public void openCategoryMenu(Player player, HelpCategory category) {
        List<ActionButton> buttons = new ArrayList<>();
        for (HelpArticle article : category.articles()) {
            if (article.permission() != null && !player.hasPermission(article.permission())) continue;
            buttons.add(buildArticleButton(article));
        }

        ActionButton back = ActionButton.builder(
                        Component.text("Back", NamedTextColor.RED, TextDecoration.BOLD)
                                .decoration(TextDecoration.ITALIC, false))
                .tooltip(Component.text("Return to the main menu", NamedTextColor.GRAY))
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) openMainMenu(p);
                        },
                        ClickCallback.Options.builder().build()))
                .build();

        Component title = MM.deserialize("<gradient:" + category.gradient() + "><b>" + category.title() + "</b></gradient>")
                .decoration(TextDecoration.ITALIC, false);

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .build())
                .type(DialogType.multiAction(buttons, back, 1)));

        player.showDialog(dialog);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.1f);
    }

    private ActionButton buildCategoryButton(HelpCategory category) {
        Component label = MM.deserialize("<gradient:" + category.gradient() + "><b>" + category.title() + "</b></gradient>")
                .decoration(TextDecoration.ITALIC, false);
        Component tooltip = Component.text("Click to view " + category.title().toLowerCase() + " articles", NamedTextColor.GRAY);
        return ActionButton.builder(label)
                .tooltip(tooltip)
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) openCategoryMenu(p, category);
                        },
                        ClickCallback.Options.builder().build()))
                .build();
    }

    private ActionButton buildArticleButton(HelpArticle article) {
        Component label = MM.deserialize("<gradient:" + article.gradient() + "><b>" + article.title() + "</b></gradient>")
                .decoration(TextDecoration.ITALIC, false);
        Component tooltip = Component.text("Click to read " + article.title().toLowerCase(), NamedTextColor.GRAY);
        return ActionButton.builder(label)
                .tooltip(tooltip)
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) sendArticle(p, article);
                        },
                        ClickCallback.Options.builder().build()))
                .build();
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
}
