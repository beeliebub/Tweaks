package me.beeliebub.tweaks.core.config;

import java.util.List;
import java.util.Objects;

/** An ordered group of settings shown together as one screen in {@link ConfigRegistry}/{@code ConfigGUI}. */
public record ConfigCategory(String key, String displayName, List<ConfigSetting> settings, Group group) {
    public enum Group {
        MAIN,
        LOGGING
    }

    public ConfigCategory(String key, String displayName, List<ConfigSetting> settings) {
        this(key, displayName, settings, Group.MAIN);
    }

    public ConfigCategory {
        Objects.requireNonNull(group, "group");
        settings = List.copyOf(settings);
    }
}
