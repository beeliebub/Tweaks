package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Legacy CommandExecutor/TabCompleter routing for the HelpSystem façade. */
final class HelpCommandRouter {
    private final HelpSystem helpSystem;

    HelpCommandRouter(HelpSystem helpSystem) {
        this.helpSystem = helpSystem;
    }

    boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                      @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /help.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            helpSystem.openMainMenu(player);
            return true;
        }

        String target = args[0].toLowerCase();
        HelpSystem.HelpCategory category = helpSystem.getCategory(target);
        if (category != null) {
            if (category.hasVisibleArticles(player)) {
                helpSystem.openCategoryMenu(player, category);
            } else {
                player.sendMessage(Component.text("You don't have permission to view this category.", NamedTextColor.RED));
            }
            return true;
        }

        HelpSystem.HelpArticle article = helpSystem.getArticle(target);
        if (article != null) {
            if (article.permission() == null || player.hasPermission(article.permission())) {
                helpSystem.openArticle(player, article);
            } else {
                player.sendMessage(Component.text("You don't have permission to view this article.", NamedTextColor.RED));
            }
            return true;
        }
        player.sendMessage(Component.text("Unknown help section: " + target, NamedTextColor.RED));
        return true;
    }

    List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                               @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return Collections.emptyList();
        String partial = args[0].toLowerCase();
        List<String> options = new ArrayList<>();
        for (HelpSystem.HelpCategory category : helpSystem.getCategories()) {
            if (!category.hasVisibleArticles(player)) continue;
            options.add(category.id());
            for (HelpSystem.HelpArticle article : category.articles()) {
                if (article.permission() == null || player.hasPermission(article.permission())) {
                    options.add(article.id());
                }
            }
        }
        return options.stream().filter(option -> option.startsWith(partial)).toList();
    }
}
