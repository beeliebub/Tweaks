package me.beeliebub.tweaks.tools.durability;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

/**
 * Re-registers the repair kit recipe after the server finishes (re)loading its data packs.
 * A datapack reload — whether from {@code /reload}, {@code /minecraft:reload}, or the initial
 * startup pass that completes after plugins enable — rebuilds the recipe manager from datapack
 * contents only and drops every plugin-added recipe, which then surfaces as
 * "Tried to load unrecognized recipe" spam when players' recipe books re-sync.
 */
public final class RepairRecipeReloadListener implements Listener {

    private final Tweaks plugin;
    private final RepairRecipeManager recipes;

    public RepairRecipeReloadListener(Tweaks plugin, RepairRecipeManager recipes) {
        this.plugin = plugin;
        this.recipes = recipes;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (!recipes.refresh()) {
            plugin.getLogger().warning("Repair kit recipe could not be re-registered after a data pack "
                    + event.getType().name().toLowerCase() + "; it is invalid or collides with an existing recipe.");
        }
    }
}
