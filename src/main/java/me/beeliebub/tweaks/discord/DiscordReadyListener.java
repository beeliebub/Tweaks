package me.beeliebub.tweaks.discord;

import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordReadyEvent;
import me.beeliebub.tweaks.Tweaks;

/** Pushes the optional Roulette provider after DiscordSRV has populated its guild cache. */
public final class DiscordReadyListener {

    private final Tweaks plugin;
    private final DiscordSrvAnnouncer announcer;

    DiscordReadyListener(Tweaks plugin, DiscordSrvAnnouncer announcer) {
        this.plugin = plugin;
        this.announcer = announcer;
    }

    @Subscribe
    public void onDiscordReady(DiscordReadyEvent event) {
        if (plugin.isEnabled()) announcer.pushSlashCommands();
    }
}
