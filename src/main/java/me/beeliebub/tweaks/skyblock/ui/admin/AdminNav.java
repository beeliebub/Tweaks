package me.beeliebub.tweaks.skyblock.ui.admin;

import java.util.Objects;

/** Small navigation value used by administrator screens. */
public record AdminNav(Runnable back) {
    public AdminNav {
        Objects.requireNonNull(back, "back");
    }

    public void open() {
        back.run();
    }
}
