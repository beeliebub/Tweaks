package me.beeliebub.tweaks.listeners;

import me.beeliebub.tweaks.Tweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

// Cancels right-click of disabled spawn eggs on spawner blocks so the spawner's spawned type cannot
// be set. Disabled mobs are managed at runtime via /tconfig spawneregg <disable|enable> <mob>.
public class SpawnerEggListener implements Listener {

    private static final String CONFIG_KEY = "spawner-egg-disabled-mobs";
    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";

    private final Tweaks plugin;

    public SpawnerEggListener(Tweaks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        String matKey = item.getType().getKey().getKey();
        if (!matKey.endsWith(SPAWN_EGG_SUFFIX)) return;
        String mob = matKey.substring(0, matKey.length() - SPAWN_EGG_SUFFIX.length());

        List<String> disabled = plugin.getConfig().getStringList(CONFIG_KEY);
        for (String entry : disabled) {
            if (entry.equalsIgnoreCase(mob)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text(
                        "That spawn egg cannot be used on spawners on this server."
                ).color(NamedTextColor.RED));
                return;
            }
        }
    }
}