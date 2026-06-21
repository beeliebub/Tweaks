package me.beeliebub.tweaks;

import me.beeliebub.tweaks.blocklog.BlockLogSystem;
import me.beeliebub.tweaks.deathinventory.DeathInventoryListener;
import me.beeliebub.tweaks.deathinventory.DeathInventoryManager;
import me.beeliebub.tweaks.combos.*;
import me.beeliebub.tweaks.commands.*;
import me.beeliebub.tweaks.core.HelpSystem;
import me.beeliebub.tweaks.economy.BalanceCommand;
import me.beeliebub.tweaks.economy.EconomyListener;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.ranks.RankCommand;
import me.beeliebub.tweaks.ranks.RankManager;
import me.beeliebub.tweaks.ranks.RankSetCommand;
import me.beeliebub.tweaks.ranks.RankupCommand;
import me.beeliebub.tweaks.itemadmin.DisplayChestSystem;
import me.beeliebub.tweaks.itemadmin.ItemEditCommand;
import me.beeliebub.tweaks.worldmanagement.MoonSystem;
import me.beeliebub.tweaks.worldmanagement.WorldRuleListener;
import me.beeliebub.tweaks.enchantments.*;
import me.beeliebub.tweaks.enchantments.modes.EnchantMode;
import me.beeliebub.tweaks.enchantments.quality.*;
import me.beeliebub.tweaks.listeners.*;
import me.beeliebub.tweaks.managers.*;
import me.beeliebub.tweaks.permissions.PermissionCommand;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.protection.PendingStampsStore;
import me.beeliebub.tweaks.protection.ProtectionCommand;
import me.beeliebub.tweaks.protection.ProtectionKeys;
import me.beeliebub.tweaks.protection.ProtectionListeners;
import me.beeliebub.tweaks.protection.ProtectionManager;
import me.beeliebub.tweaks.protection.RegionLoader;
import me.beeliebub.tweaks.protection.RegionSelectionManager;
import me.beeliebub.tweaks.protection.RegionWriter;
import org.bukkit.Material;
import me.beeliebub.tweaks.playeradmin.PlayerAdminCommand;
import me.beeliebub.tweaks.playeradmin.PlayerAdminManager;
import me.beeliebub.tweaks.tab.TabManager;
import me.beeliebub.tweaks.teleport.TeleportCommandManager;
import me.beeliebub.tweaks.recipes.ResourceRupee;
import me.beeliebub.tweaks.minigames.RewardCommand;
import me.beeliebub.tweaks.minigames.RewardListener;
import me.beeliebub.tweaks.minigames.RewardManager;
import me.beeliebub.tweaks.minigames.andrew.MannequinAI;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackCommand;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import me.beeliebub.tweaks.minigames.andrew.WhackCommand;
import me.beeliebub.tweaks.minigames.andrew.WhackConfig;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.xpbottle.XpBottleListener;
import org.bukkit.plugin.java.JavaPlugin;

// Main plugin class - registers all commands, listeners, enchantments, and minigame systems
public class Tweaks extends JavaPlugin {

    private StorageManager storageManager;
    private EconomyManager economyManager;
    private RankManager rankManager;
    private PermissionManager permissionManager;
    private ProtectionManager protectionManager;
    private PendingStampsStore pendingStampsStore;
    private RegionSelectionManager regionSelectionManager;
    private Material protectionSelectionTool = Material.STONE_AXE;
    private int maxHomes;
    private Telekinesis telekinesis;
    private Replant replant;
    private BlackjackListener blackjackListener;

    public ProtectionManager getProtectionManager() {
        return protectionManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public RegionSelectionManager getRegionSelectionManager() {
        return regionSelectionManager;
    }

    public Material getProtectionSelectionTool() {
        return protectionSelectionTool;
    }

    public Telekinesis getTelekinesis() {
        return telekinesis;
    }

    public Replant getReplant() {
        return replant;
    }

    public BlackjackListener getBlackjackListener() {
        return blackjackListener;
    }

    @Override
    public void onEnable() {

        // Load config.yml and initialize data storage
        saveDefaultConfig();
        maxHomes = getConfig().getInt("max-homes", 3);
        storageManager = new StorageManager(this);
        economyManager = new EconomyManager(this);
        rankManager = new RankManager(this, economyManager);
        getServer().getPluginManager().registerEvents(new EconomyListener(this, economyManager, rankManager), this);
        BalanceCommand balanceCommand = new BalanceCommand(economyManager);
        getCommand("balance").setExecutor(balanceCommand);
        getCommand("balance").setTabCompleter(balanceCommand);
        RankCommand rankCommand = new RankCommand(economyManager, rankManager);
        getCommand("ranks").setExecutor(rankCommand);
        getCommand("ranks").setTabCompleter(rankCommand);
        getCommand("rankup").setExecutor(new RankupCommand(economyManager, rankManager));
        RankSetCommand rankSetCommand = new RankSetCommand(economyManager, rankManager);
        getCommand("rank").setExecutor(rankSetCommand);
        getCommand("rank").setTabCompleter(rankSetCommand);
        permissionManager = new PermissionManager(this);
        protectionManager = new ProtectionManager(this);
        ProtectionKeys.init(this);
        String configuredTool = getConfig().getString("protection.selection-tool", "STONE_AXE");
        Material resolvedTool = Material.matchMaterial(configuredTool);
        if (resolvedTool == null) {
            getLogger().warning("protection.selection-tool '" + configuredTool + "' is not a valid Material; falling back to STONE_AXE");
        } else {
            protectionSelectionTool = resolvedTool;
        }
        java.io.File regionsDir = new java.io.File(getDataFolder(), "regions");
        new RegionLoader(getLogger()).load(regionsDir, protectionManager.regions());
        protectionManager.setWriter(new RegionWriter(this, regionsDir));
        // Per-world refactor migration: regions saved before the world field
        // existed get assigned to the primary loaded world (overworld) so they
        // participate in per-world uniqueness going forward. Idempotent.
        if (!getServer().getWorlds().isEmpty()) {
            int migrated = protectionManager.migrateLegacyRegions(getServer().getWorlds().getFirst().getName());
            if (migrated > 0) {
                getLogger().info("Migrated " + migrated + " legacy region(s) to default world");
            }
        }
        pendingStampsStore = new PendingStampsStore(this, getDataFolder(), protectionManager.pendingStamps());
        pendingStampsStore.load();
        pendingStampsStore.start(20L * 60 * 5); // snapshot every 5 minutes
        regionSelectionManager = new RegionSelectionManager(this);
        regionSelectionManager.start();
        getServer().getPluginManager().registerEvents(regionSelectionManager, this);
        getServer().getPluginManager().registerEvents(
                new ProtectionListeners(this, protectionManager, regionSelectionManager), this);
        ProtectionCommand protectionCommand = new ProtectionCommand(this, protectionManager, regionSelectionManager);
        getCommand("region").setExecutor(protectionCommand);
        getCommand("region").setTabCompleter(protectionCommand);

        // Resource Hunt Items Manager
        ResourceHuntItems resourceHuntItems = new ResourceHuntItems(this);

        // Commands - Combos
        // Player administration: /survival /creative /nv /fly /afk /nick + tab list + boot trail.
        PlayerAdminManager playerAdminManager = new PlayerAdminManager(this);
        PlayerAdminCommand playerAdminCommand = new PlayerAdminCommand(playerAdminManager);
        for (String label : new String[]{"survival", "creative", "nv", "fly", "afk", "nick"}) {
            getCommand(label).setExecutor(playerAdminCommand);
        }
        getServer().getPluginManager().registerEvents(playerAdminManager, this);

        ItemFilterCommand itemFilterCommand = new ItemFilterCommand(this);
        InvSeeCommand invSeeCommand = new InvSeeCommand(this);
        HelpSystem helpSystem = new HelpSystem(getLogger());

        getCommand("itemfilter").setExecutor(itemFilterCommand);
        getCommand("itemfilter").setTabCompleter(itemFilterCommand);
        getCommand("invsee").setExecutor(invSeeCommand);
        getCommand("invsee").setTabCompleter(invSeeCommand);
        getCommand("help").setExecutor(helpSystem);
        getCommand("help").setTabCompleter(helpSystem);

        // Teleportation: /home /sethome /delhome /homes /warp /setwarp /delwarp /warps
        // /spawn /back /tpa /tpahere /tpaccept /tpdeny — all routed through one manager.
        TeleportCommandManager teleportManager = new TeleportCommandManager(this, storageManager, resourceHuntItems, maxHomes);
        for (String label : new String[]{"home", "sethome", "delhome", "homes",
                                         "warp", "setwarp", "delwarp", "warps",
                                         "spawn", "back",
                                         "tpa", "tpahere", "tpaccept", "tpdeny"}) {
            getCommand(label).setExecutor(teleportManager);
            getCommand(label).setTabCompleter(teleportManager);
        }
        getServer().getPluginManager().registerEvents(teleportManager, this);

        // Commands - Misc
        // /lore /name /more — admin item-edit commands, dispatched via one class.
        ItemEditCommand itemEditCommand = new ItemEditCommand();
        for (String label : new String[]{"lore", "name", "more"}) {
            getCommand(label).setExecutor(itemEditCommand);
            getCommand(label).setTabCompleter(itemEditCommand);
        }
        GuiCopyCommand guiCopyCommand = new GuiCopyCommand(this);
        getCommand("guicopy").setExecutor(guiCopyCommand);
        getCommand("guicopy").setTabCompleter(guiCopyCommand);
        ConfigCommand configCommand = new ConfigCommand(this, resourceHuntItems);
        getCommand("tconfig").setExecutor(configCommand);
        getCommand("tconfig").setTabCompleter(configCommand);

        // Commands - Permissions
        PermissionCommand permissionCommand = new PermissionCommand(permissionManager);
        getCommand("tprm").setExecutor(permissionCommand);
        getCommand("tprm").setTabCompleter(permissionCommand);

        // Tab list: unified renderer. Constructed after PlayerAdminManager so it can
        // take a reference to it, and after economyManager/rankManager are ready.
        TabManager tabManager = new TabManager(this, economyManager, rankManager, playerAdminManager);
        getServer().getPluginManager().registerEvents(tabManager, this);
        playerAdminManager.setTabManager(tabManager);
        economyManager.setTabManager(tabManager);
        rankManager.setTabManager(tabManager);

        // Listeners - General
        getServer().getPluginManager().registerEvents(itemFilterCommand, this);
        playerAdminManager.start();
        getServer().getPluginManager().registerEvents(invSeeCommand, this);
        getServer().getPluginManager().registerEvents(helpSystem, this);
        getServer().getPluginManager().registerEvents(permissionManager, this);
        getServer().getPluginManager().registerEvents(new ResourceWorldListener(this, storageManager), this);
        getServer().getPluginManager().registerEvents(new SeparatorListener(this, storageManager), this);
        // World rules: trample, portals, mob-grief, spawner eggs, villager trades.
        getServer().getPluginManager().registerEvents(new WorldRuleListener(this, protectionManager), this);

        // Quality Enchantment System
        QualityRegistry qualityRegistry = new QualityRegistry(this);

        // ToolProtect (depends on QualityRegistry to detect epic/legendary quality)
        ToolProtectCommand toolProtectCommand = new ToolProtectCommand(this, qualityRegistry);
        getCommand("toolprotect").setExecutor(toolProtectCommand);
        getCommand("toolprotect").setTabCompleter(toolProtectCommand);
        getServer().getPluginManager().registerEvents(toolProtectCommand, this);

        // MoonSystem owns the blood moon engine and the /bloodmoon, /fullmoon commands.
        MoonSystem moonSystem = new MoonSystem(this);
        moonSystem.start();
        getServer().getPluginManager().registerEvents(moonSystem, this);
        getCommand("bloodmoon").setExecutor(moonSystem);
        getCommand("fullmoon").setExecutor(moonSystem);

        // Reward + Resource Hunt are constructed early so Tunneller can credit its surrounding
        // (BlockDropItemEvent-bypassing) breaks via ResourceHunt#recordExternalDrops. Listener
        // registration for both still happens further down with the rest of the minigame block.
        RewardManager rewardManager = new RewardManager(this);
        ResourceHunt resourceHunt = new ResourceHunt(this, rewardManager);

        // Enchantments
        telekinesis = new Telekinesis(this, itemFilterCommand);
        Smelter smelter = new Smelter(this, telekinesis, resourceHunt);
        FortuneQualityListener fortuneQuality = new FortuneQualityListener(qualityRegistry);
        SilkTouchQualityListener silkTouchQuality = new SilkTouchQualityListener(qualityRegistry, telekinesis, resourceHunt);
        Lumberjack lumberjack = new Lumberjack(this, telekinesis, qualityRegistry, fortuneQuality);
        GemConnoisseur gemConnoisseur = new GemConnoisseur(this, telekinesis, resourceHunt);
        SpawnerPickup spawnerPickup = new SpawnerPickup(this);
        EggCollector eggCollector = new EggCollector(this, qualityRegistry);
        EnchantMode enchantMode = new EnchantMode(this);
        Tunneller tunneller = new Tunneller(this, telekinesis, smelter, gemConnoisseur, qualityRegistry, fortuneQuality, silkTouchQuality, resourceHunt, enchantMode);
        Efficacy efficacy = new Efficacy(this, qualityRegistry, enchantMode);

        getServer().getPluginManager().registerEvents(telekinesis, this);
        getServer().getPluginManager().registerEvents(smelter, this);
        getServer().getPluginManager().registerEvents(lumberjack, this);
        getServer().getPluginManager().registerEvents(gemConnoisseur, this);
        getServer().getPluginManager().registerEvents(tunneller, this);
        getServer().getPluginManager().registerEvents(spawnerPickup, this);
        getServer().getPluginManager().registerEvents(eggCollector, this);
        getServer().getPluginManager().registerEvents(new AnvilListener(spawnerPickup, eggCollector), this);
        replant = new Replant(this, telekinesis, lumberjack);
        getServer().getPluginManager().registerEvents(replant, this);
        getServer().getPluginManager().registerEvents(efficacy, this);
        enchantMode.wireEnchantments(tunneller, efficacy);
        getServer().getPluginManager().registerEvents(enchantMode, this);

        // Disenchanting Bundle
        getServer().getPluginManager().registerEvents(new DisenchantingBundle(this, qualityRegistry, spawnerPickup, eggCollector), this);

        // Quality Enchantment Listeners
        getServer().getPluginManager().registerEvents(new EnchantTableListener(qualityRegistry, moonSystem), this);
        getServer().getPluginManager().registerEvents(fortuneQuality, this);
        getServer().getPluginManager().registerEvents(silkTouchQuality, this);
        getServer().getPluginManager().registerEvents(new LootingQualityListener(qualityRegistry), this);

        // XP storage bottles (custom brewing-stand recipe + drinkable bottles)
        getServer().getPluginManager().registerEvents(new XpBottleListener(this), this);

        // Dice Converter: throwing a splash potion with this enchantment briefly blocks
        // the thrower from picking up other splash_potion item entities.
        getServer().getPluginManager().registerEvents(new DiceConverterListener(this), this);

        // Resource Rupee currency (renamed emerald + emerald block with crafting grid conversion)
        ResourceRupee resourceRupee = new ResourceRupee();
        getServer().getPluginManager().registerEvents(resourceRupee, this);

        ResourceCommand resourceCommand = new ResourceCommand(resourceHunt, resourceHuntItems);
        getCommand("resource").setExecutor(resourceCommand);
        getCommand("resource").setTabCompleter(resourceCommand);

        RerollCommand rerollCommand = new RerollCommand(resourceHunt);
        getCommand("reroll").setExecutor(rerollCommand);

        // Minigames - Mannequin AI
        MannequinAI mannequinAI = new MannequinAI(this);
        getServer().getPluginManager().registerEvents(mannequinAI, this);
        mannequinAI.resumeAll();

        // Minigames - Rewards (manager already constructed above so Tunneller could see ResourceHunt)
        RewardCommand rewardCommand = new RewardCommand(rewardManager);
        getCommand("reward").setExecutor(rewardCommand);
        getCommand("reward").setTabCompleter(rewardCommand);
        getServer().getPluginManager().registerEvents(new RewardListener(rewardManager, rewardCommand), this);

        // Minigames - Resource Hunt
        getServer().getPluginManager().registerEvents(resourceHunt, this);
        getServer().getPluginManager().registerEvents(
                new me.beeliebub.tweaks.minigames.resource.ResourceCraftListener(this, resourceHunt), this);

        // /condense depends on ResourceHunt for the resource_hunt_counted PDC plumbing,
        // so it has to be registered after the hunt is constructed.
        CondenseCommand condenseCommand = new CondenseCommand(resourceHunt);
        getCommand("condense").setExecutor(condenseCommand);
        getCommand("condense").setTabCompleter(condenseCommand);

        // Minigames - Whack an Andrew
        WhackConfig whackConfig = new WhackConfig(this);
        WhackCommand whackCommand = new WhackCommand(this, whackConfig, rewardManager);
        getCommand("whack").setExecutor(whackCommand);
        getCommand("whack").setTabCompleter(whackCommand);

        // Minigames - Blackjack (in-world ItemDisplay card grid vs the dealer)
        blackjackListener = new BlackjackListener(this, economyManager, rankManager);
        BlackjackCommand blackjackCommand = new BlackjackCommand(economyManager, blackjackListener);
        getCommand("blackjack").setExecutor(blackjackCommand);
        getCommand("blackjack").setTabCompleter(blackjackCommand);
        getServer().getPluginManager().registerEvents(blackjackListener, this);

        // BlockLog - per-chunk PDC chest interaction logging
        BlockLogSystem blockLogSystem = new BlockLogSystem(this);
        getCommand("logs").setExecutor(blockLogSystem);
        getServer().getPluginManager().registerEvents(blockLogSystem, this);

        // Death Inventory - capture and restore player inventories on death
        DeathInventoryManager deathInventoryManager = new DeathInventoryManager(this);
        DeathInventoryListener deathInventoryListener = new DeathInventoryListener(this, deathInventoryManager);
        getServer().getPluginManager().registerEvents(deathInventoryListener, this);
        getCommand("deathinventory").setExecutor(deathInventoryListener);
        getCommand("deathinventory").setTabCompleter(deathInventoryListener);

        // Display Chest
        DisplayChestSystem displayChestSystem = new DisplayChestSystem(this);
        getCommand("displaychest").setExecutor(displayChestSystem);
        getCommand("displaychest").setTabCompleter(displayChestSystem);
        getServer().getPluginManager().registerEvents(displayChestSystem, this);

        getLogger().info("Tweaks has been enabled safely. Async I/O and Teleportation active.");
        }

        @Override
        public void onDisable() {
        if (permissionManager != null) {
            permissionManager.shutdown();
        }
        if (pendingStampsStore != null) {
            pendingStampsStore.stop();
            try {
                pendingStampsStore.writeNow();
            } catch (java.io.IOException e) {
                getLogger().warning("Final pending_stamps flush failed: " + e.getMessage());
            }
        }
        if (regionSelectionManager != null) {
            regionSelectionManager.stop();
        }
        if (blackjackListener != null) {
            // Guarantee no card ItemDisplays survive a server stop.
            blackjackListener.shutdown();
        }
    }

}