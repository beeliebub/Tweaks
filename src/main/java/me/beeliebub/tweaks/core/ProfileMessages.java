package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Player-feedback factories for world-profile inventory separation. */
public final class ProfileMessages {

    ProfileMessages() {
    }

    /** Explains that a destination profile could not be decoded, so the swap was aborted safely. */
    public Component profileSwapDataCorrupt() {
        return Component.text("Profile swap aborted: destination data is corrupt. Please report this.",
                NamedTextColor.RED);
    }

    /** Confirms the profile loaded after a cross-profile world change. */
    public Component profileSwitched(String profile) {
        return Component.text("Inventory profile switched to: " + profile, NamedTextColor.YELLOW);
    }
}
