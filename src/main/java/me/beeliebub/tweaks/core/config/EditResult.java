package me.beeliebub.tweaks.core.config;

import net.kyori.adventure.text.Component;

/** Outcome of one {@link ConfigValueEditor} mutation attempt - a message to show the sender either way. */
public sealed interface EditResult {
    Component message();

    record Ok(Component message) implements EditResult {}
    record Invalid(Component message) implements EditResult {}
}
