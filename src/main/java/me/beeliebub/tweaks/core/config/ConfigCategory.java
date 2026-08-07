package me.beeliebub.tweaks.core.config;

import java.util.List;

/** An ordered group of settings shown together as one screen in {@link ConfigRegistry}/{@code ConfigGUI}. */
public record ConfigCategory(String key, String displayName, List<ConfigSetting> settings) {
    public ConfigCategory {
        settings = List.copyOf(settings);
    }
}
