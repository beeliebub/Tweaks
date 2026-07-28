package me.beeliebub.tweaks.core;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** Ordered HelpSystem category/article index and cross-reference validator. */
final class HelpCatalog {
    private final Map<String, HelpSystem.HelpCategory> categories = new LinkedHashMap<>();
    private final Map<String, HelpSystem.HelpArticle> articles = new LinkedHashMap<>();
    private final Logger logger;

    HelpCatalog(Logger logger) {
        this.logger = logger;
    }

    void add(HelpSystem.HelpCategory category) {
        categories.put(category.id(), category);
        for (HelpSystem.HelpArticle article : category.articles()) {
            HelpSystem.HelpArticle previous = articles.put(article.id(), article);
            if (previous != null) {
                logger.warning("Help: duplicate article id '" + article.id() + "' in category '" + category.id() + "'");
            }
        }
    }

    void validateCrossReferences() {
        for (HelpSystem.HelpArticle article : articles.values()) {
            for (String referenceId : article.relatedArticles()) {
                if (!articles.containsKey(referenceId)) {
                    logger.warning("Help: article '" + article.id() + "' references unknown article '" + referenceId + "'");
                }
            }
        }
    }

    Collection<HelpSystem.HelpCategory> categories() {
        return Collections.unmodifiableCollection(categories.values());
    }

    HelpSystem.HelpCategory category(String id) {
        return id == null ? null : categories.get(id);
    }

    HelpSystem.HelpArticle article(String id) {
        return id == null ? null : articles.get(id);
    }

    HelpSystem.HelpArticle randomVisibleArticle(Player player) {
        List<HelpSystem.HelpArticle> visible = articles.values().stream()
                .filter(article -> article.permission() == null || player.hasPermission(article.permission()))
                .toList();
        return visible.isEmpty() ? null : visible.get(ThreadLocalRandom.current().nextInt(visible.size()));
    }

    HelpSystem.HelpCategory categoryOf(HelpSystem.HelpArticle article) {
        for (HelpSystem.HelpCategory category : categories.values()) {
            for (HelpSystem.HelpArticle candidate : category.articles()) {
                if (candidate.id().equals(article.id())) return category;
            }
        }
        return null;
    }
}
