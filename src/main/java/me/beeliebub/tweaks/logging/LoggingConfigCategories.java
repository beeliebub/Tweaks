package me.beeliebub.tweaks.logging;

import me.beeliebub.tweaks.core.config.ConfigCategory;
import me.beeliebub.tweaks.core.config.ConfigSetting;
import me.beeliebub.tweaks.core.config.EditorType;

import java.util.List;

/** Builds the logging-owned config categories for the shared core registry. */
public final class LoggingConfigCategories {

    private LoggingConfigCategories() {}

    public static List<ConfigCategory> categories() {
        return LoggingPaths.categories().stream()
                .map(category -> new ConfigCategory(category.key(), category.displayName(),
                        category.events().stream()
                                .map(event -> ConfigSetting.of(event.path(), event.path(),
                                        event.displayName(), EditorType.BOOLEAN))
                                .toList(), ConfigCategory.Group.LOGGING))
                .toList();
    }
}
