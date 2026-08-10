package me.beeliebub.tweaks.skyblock.listener;

import me.beeliebub.tweaks.skyblock.challenge.ChallengeService;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/** Login/logout triggers for island activity and possession-only challenge readiness. */
public final class ChallengeProgressListener implements Listener {
    private final IslandManager islands;
    private final ChallengeService challenges;

    public ChallengeProgressListener(IslandManager islands, ChallengeService challenges) {
        this.islands = Objects.requireNonNull(islands, "islands");
        this.challenges = Objects.requireNonNull(challenges, "challenges");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Island island = islands.forPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (island == null) return;
        islands.markActive(island);
        challenges.onMemberLogin(island, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        islands.forPlayer(event.getPlayer().getUniqueId()).ifPresent(islands::markActive);
    }
}
