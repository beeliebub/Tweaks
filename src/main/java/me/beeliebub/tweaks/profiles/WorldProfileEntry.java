package me.beeliebub.tweaks.profiles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * One row of the {@code world-profiles} list (or the {@code world-profile-fallback} object, which
 * shares this shape with an empty {@code worldKey}): a world key mapped to its inventory/XP
 * profile and tab-list display. See {@link WorldProfileTable} for read/write ownership.
 */
public record WorldProfileEntry(String worldKey, String profile, String tagText, NamedTextColor color) {

    public Component tagComponent() {
        return Component.text("[" + tagText + "] ", color);
    }
}
