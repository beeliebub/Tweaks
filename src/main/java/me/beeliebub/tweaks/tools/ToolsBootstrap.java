package me.beeliebub.tweaks.tools;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.cash.CashItemListener;
import me.beeliebub.tweaks.tools.cash.CashItemService;
import me.beeliebub.tweaks.tools.durability.DurabilityListener;
import me.beeliebub.tweaks.tools.durability.DurabilityService;
import me.beeliebub.tweaks.tools.durability.RepairKitCommand;
import me.beeliebub.tweaks.tools.durability.RepairKitItem;
import me.beeliebub.tweaks.tools.durability.RepairKitListener;
import me.beeliebub.tweaks.tools.durability.RepairRecipeManager;
import me.beeliebub.tweaks.tools.xp.BlockExpListener;
import me.beeliebub.tweaks.tools.xp.MendingPayoutListener;
import me.beeliebub.tweaks.tools.xp.MobExpListener;
import me.beeliebub.tweaks.tools.xp.TaintPruneListener;
import me.beeliebub.tweaks.tools.xp.XpSettings;
import me.beeliebub.tweaks.utils.BlockTaintStore;
import me.beeliebub.tweaks.utils.ExternalBlockBreakGuard;
import me.beeliebub.tweaks.tools.augments.AugmentCommand;
import me.beeliebub.tweaks.tools.augments.AugmentCraftListener;
import me.beeliebub.tweaks.tools.augments.AugmentConfirmationListener;
import me.beeliebub.tweaks.tools.augments.AugmentDialog;
import me.beeliebub.tweaks.tools.augments.AugmentGemListener;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import me.beeliebub.tweaks.tools.augments.AugmentPendingConfirmations;
import me.beeliebub.tweaks.tools.augments.AugmentTableListener;
import me.beeliebub.tweaks.tools.augments.BookConversionListener;
import me.beeliebub.tweaks.tools.augments.DisenchantingBundle;
import me.beeliebub.tweaks.tools.augments.SlotCalculator;

/** Tier 5 composition root for the live player-tools systems. */
public final class ToolsBootstrap {

    private static AugmentPendingConfirmations pendingConfirmations;

    private ToolsBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        XpSettings xpSettings = new XpSettings(plugin);
        BlockTaintStore taintStore = new BlockTaintStore(plugin, "tools_taint_");
        BlockExpListener blockExp = new BlockExpListener(xpSettings, taintStore);

        plugin.getServer().getPluginManager().registerEvents(new TaintPruneListener(taintStore, xpSettings), plugin);
        plugin.getServer().getPluginManager().registerEvents(new MobExpListener(xpSettings), plugin);
        plugin.getServer().getPluginManager().registerEvents(blockExp, plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new MendingPayoutListener(plugin, services.economyManager(), xpSettings), plugin);
        services.replant().addReplantHook((seed, block) -> blockExp.onReplantedCrop(block));
        services.tunneller().setExternalBlockBreakHook(blockExp);
        services.lumberjack().setExternalBlockBreakHook(blockExp);
        services.efficacy().setExternalBlockBreakHook(blockExp);

        CashItemService cash = new CashItemService(plugin, services.economyManager());
        plugin.getServer().getPluginManager().registerEvents(new CashItemListener(plugin, cash), plugin);

        DurabilityService durability = new DurabilityService(plugin);
        plugin.getServer().getPluginManager().registerEvents(new DurabilityListener(plugin, durability), plugin);
        ExternalBlockBreakGuard protectionGuard = (player, block, material) ->
                services.protectionManager().isBlockActionAllowed(block.getLocation(), player.getUniqueId(),
                        material, me.beeliebub.tweaks.protection.region.RegionFlag.BLOCK_BREAK);
        services.tunneller().setExternalBlockBreakGuard(protectionGuard);
        services.lumberjack().setExternalBlockBreakGuard(protectionGuard);
        services.efficacy().setExternalBlockBreakGuard(protectionGuard);
        services.tunneller().setExternalDurabilityHook(durability);
        services.lumberjack().setExternalDurabilityHook(durability);
        RepairKitItem repairKit = new RepairKitItem(plugin);
        RepairKitListener repairKitListener = new RepairKitListener(repairKit, durability);
        plugin.getServer().getPluginManager().registerEvents(repairKitListener, plugin);
        RepairRecipeManager recipes = new RepairRecipeManager(plugin, repairKit);
        if (!recipes.refresh()) {
            plugin.getLogger().warning("Repair kit recipe is invalid or collides with an existing recipe; it was not registered.");
        }
        RepairKitCommand repairKitCommand = new RepairKitCommand(repairKit, durability);
        plugin.getCommand("repairkit").setExecutor(repairKitCommand);
        plugin.getCommand("repairkit").setTabCompleter(repairKitCommand);

        services.configCommand().setMaterialGridHandler(recipes::applyConfiguredGrid);
        services.configCommand().setSlotCapacityKeyValidator(SlotCalculator::isValidCapacityKey);
        services.configCommand().addConfigChangedListener(path -> {
            if (path.startsWith("xp.")) xpSettings.refresh();
            if (path.startsWith("tools.repair-kit.")) {
                if (!recipes.refresh()) {
                    plugin.getLogger().warning("Live repair kit recipe update was rejected; the previous recipe remains active.");
                }
                durability.warnIfTierStepClamped();
            }
        });

        plugin.getServer().getPluginManager().registerEvents(
                new AnvilLockoutListener(plugin, services.qualityRegistry()), plugin);
        RenameCommand renameCommand = new RenameCommand(plugin);
        plugin.getCommand("rename").setExecutor(renameCommand);
        plugin.getCommand("rename").setTabCompleter(renameCommand);

        pendingConfirmations = new AugmentPendingConfirmations(plugin);
        AugmentService augments = new AugmentService(plugin, services.qualityRegistry(),
                durability::ensureStamped, durability::refreshLoreTail, pendingConfirmations);
        AugmentDialog augmentDialog = new AugmentDialog(augments);
        AugmentCommand augmentCommand = new AugmentCommand(augments, augmentDialog);
        plugin.getCommand("augment").setExecutor(augmentCommand);
        plugin.getCommand("augment").setTabCompleter(augmentCommand);
        plugin.getServer().getPluginManager().registerEvents(new AugmentGemListener(augments.gemItem(), augmentDialog), plugin);
        plugin.getServer().getPluginManager().registerEvents(new AugmentTableListener(plugin, augments), plugin);
        plugin.getServer().getPluginManager().registerEvents(new AugmentCraftListener(plugin, augments), plugin);
        plugin.getServer().getPluginManager().registerEvents(new BookConversionListener(plugin, augments), plugin);
        plugin.getServer().getPluginManager().registerEvents(new DisenchantingBundle(plugin, services.qualityRegistry(), augments), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new AugmentConfirmationListener(pendingConfirmations), plugin);
    }

    public static void shutdown(Tweaks plugin) {
        if (pendingConfirmations != null) {
            pendingConfirmations.cancelAll();
            pendingConfirmations = null;
        }
        new RepairRecipeManager(plugin, new RepairKitItem(plugin)).remove();
    }
}
