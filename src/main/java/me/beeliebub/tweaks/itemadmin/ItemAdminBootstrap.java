package me.beeliebub.tweaks.itemadmin;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

/**
 * Tier:      3 - player-facing infra (register), 5 - leaf features (registerToolProtect)
 * Reads:     resourceHunt (tier 2, CondenseCommand); qualityRegistry (tier 4, straddle)
 * Publishes: itemFilterCommand (read by enchantments' Telekinesis at tier 4)
 * Straddle:  ToolProtectCommand depends on QualityRegistry (enchantments, tier 4) to detect
 *            epic/legendary quality, so it is published through a second entry point
 *            (registerToolProtect) called at tier 5, after enchantments has run, rather than
 *            folding this package's other commands into a later tier just for its sake.
 */
public final class ItemAdminBootstrap {

    private ItemAdminBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        ItemFilterCommand itemFilterCommand = new ItemFilterCommand(plugin);
        services.setItemFilterCommand(itemFilterCommand);
        plugin.getCommand("itemfilter").setExecutor(itemFilterCommand);
        plugin.getCommand("itemfilter").setTabCompleter(itemFilterCommand);
        plugin.getServer().getPluginManager().registerEvents(itemFilterCommand, plugin);

        InvSeeCommand invSeeCommand = new InvSeeCommand(plugin);
        plugin.getCommand("invsee").setExecutor(invSeeCommand);
        plugin.getCommand("invsee").setTabCompleter(invSeeCommand);
        plugin.getServer().getPluginManager().registerEvents(invSeeCommand, plugin);

        // /lore /name /more - admin item-edit commands, dispatched via one class.
        ItemEditCommand itemEditCommand = new ItemEditCommand(plugin);
        for (String label : new String[]{"lore", "name", "more"}) {
            plugin.getCommand(label).setExecutor(itemEditCommand);
            plugin.getCommand(label).setTabCompleter(itemEditCommand);
        }

        GuiCopyCommand guiCopyCommand = new GuiCopyCommand(plugin);
        plugin.getCommand("guicopy").setExecutor(guiCopyCommand);
        plugin.getCommand("guicopy").setTabCompleter(guiCopyCommand);

        DisplayChestSystem displayChestSystem = new DisplayChestSystem(plugin);
        plugin.getCommand("displaychest").setExecutor(displayChestSystem);
        plugin.getCommand("displaychest").setTabCompleter(displayChestSystem);
        plugin.getServer().getPluginManager().registerEvents(displayChestSystem, plugin);

        // /condense depends on ResourceHunt for the resource_hunt_counted PDC plumbing,
        // so it has to be registered after the hunt is constructed.
        CondenseCommand condenseCommand = new CondenseCommand(plugin, services.resourceHunt());
        plugin.getCommand("condense").setExecutor(condenseCommand);
        plugin.getCommand("condense").setTabCompleter(condenseCommand);
    }

    public static void registerToolProtect(Tweaks plugin, Services services) {
        // ToolProtect depends on QualityRegistry to detect epic/legendary quality.
        ToolProtectCommand toolProtectCommand = new ToolProtectCommand(plugin, services.qualityRegistry());
        plugin.getCommand("toolprotect").setExecutor(toolProtectCommand);
        plugin.getCommand("toolprotect").setTabCompleter(toolProtectCommand);
        plugin.getServer().getPluginManager().registerEvents(toolProtectCommand, plugin);
    }
}
