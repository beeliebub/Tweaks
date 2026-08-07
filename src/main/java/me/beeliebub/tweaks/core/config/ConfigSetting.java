package me.beeliebub.tweaks.core.config;

import java.util.List;

/**
 * One admin-editable config.yml value: its path, CLI alias(es), display name, editor type, and
 * optional numeric bounds (used by INT/LONG/DOUBLE/PERCENT; {@code null} on either side means
 * unbounded on that side).
 *
 * <p>{@code path} is the dotted config.yml path for a generic setting. The three pre-existing
 * legacy settings (eggdrop, spawneregg, resourceitems) instead carry a sentinel path
 * ({@link ConfigRegistry#EGGDROP_SENTINEL_PATH} etc.) because their mutation is special-cased in
 * {@link ConfigValueEditor} to preserve exact historical CLI wording - resourceitems in particular
 * has no config.yml path at all, since it delegates to {@code ResourceHuntItems}.
 */
public record ConfigSetting(
        String path,
        List<String> cliAliases,
        String displayName,
        EditorType type,
        Double min,
        Double max
) {
    public ConfigSetting {
        cliAliases = List.copyOf(cliAliases);
    }

    public static ConfigSetting of(String path, String cliAlias, String displayName, EditorType type) {
        return new ConfigSetting(path, List.of(cliAlias), displayName, type, null, null);
    }

    public static ConfigSetting bounded(String path, String cliAlias, String displayName, EditorType type,
                                        double min, double max) {
        return new ConfigSetting(path, List.of(cliAlias), displayName, type, min, max);
    }

    public boolean matchesAlias(String candidate) {
        for (String alias : cliAliases) {
            if (alias.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }
}
