package me.beeliebub.tweaks;

import me.beeliebub.tweaks.blocklog.BlockLogBootstrap;
import me.beeliebub.tweaks.core.CoreBootstrap;
import me.beeliebub.tweaks.deathinventory.DeathInventoryBootstrap;
import me.beeliebub.tweaks.economy.EconomyBootstrap;
import me.beeliebub.tweaks.enchantments.EnchantmentsBootstrap;
import me.beeliebub.tweaks.enchantments.Replant;
import me.beeliebub.tweaks.enchantments.Telekinesis;
import me.beeliebub.tweaks.itemadmin.ItemAdminBootstrap;
import me.beeliebub.tweaks.minigames.MinigamesBootstrap;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import me.beeliebub.tweaks.lottery.LotteryManager;
import me.beeliebub.tweaks.logging.ConsoleEventLog;
import me.beeliebub.tweaks.logging.LoggingBootstrap;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.permissions.PermissionsBootstrap;
import me.beeliebub.tweaks.playeradmin.PlayerAdminBootstrap;
import me.beeliebub.tweaks.profiles.ProfilesBootstrap;
import me.beeliebub.tweaks.protection.ProtectionBootstrap;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.ui.RegionSelectionManager;
import me.beeliebub.tweaks.ranks.RankManager;
import me.beeliebub.tweaks.ranks.RanksBootstrap;
import me.beeliebub.tweaks.recipes.RecipesBootstrap;
import me.beeliebub.tweaks.tab.TabBootstrap;
import me.beeliebub.tweaks.teleport.TeleportBootstrap;
import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.worldmanagement.WorldManagementBootstrap;
import me.beeliebub.tweaks.xpbottle.XpBottleBootstrap;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

// Main plugin class - registers all commands, listeners, enchantments, and minigame systems.
// onEnable() delegates to one XxxBootstrap.register(this, services) call per feature package,
// grouped into ordered tiers (a bootstrap may only read Services fields published by a
// strictly earlier tier). See each package's *Bootstrap class Javadoc for what it reads/
// publishes and why it sits at its tier; see the root CLAUDE.md "Bootstrap" section for the
// standing rules this follows.
public class Tweaks extends JavaPlugin {

    private Services services;

    public ProtectionManager getProtectionManager() {
        return services.protectionManager;
    }

    public EconomyManager getEconomyManager() {
        return services.economyManager;
    }

    public RankManager getRankManager() {
        return services.rankManager;
    }

    public PermissionManager getPermissionManager() {
        return services.permissionManager;
    }

    public RegionSelectionManager getRegionSelectionManager() {
        return services.regionSelectionManager;
    }

    public Material getProtectionSelectionTool() {
        return services.protectionSelectionTool;
    }

    // The one Services field ConfigValueEditor also publishes to directly after a live edit -
    // see Services' class Javadoc on why protectionSelectionTool is the one overwritable field.
    public void setProtectionSelectionTool(Material material) {
        services.setProtectionSelectionTool(material);
    }

    public Telekinesis getTelekinesis() {
        return services.telekinesis;
    }

    public Replant getReplant() {
        return services.replant;
    }

    public BlackjackListener getBlackjackListener() {
        return services.blackjackListener;
    }

    public LotteryManager getLotteryManager() {
        return services.lotteryManager;
    }

    /** Null-tolerant access for event instrumentation during partial boot/teardown. */
    public ConsoleEventLog getConsoleEventLog() {
        return services == null ? null : services.consoleEventLog;
    }

    @Override
    public void onEnable() {
        services = new Services();

        // Paper's plugin loader flips isEnabled() to true before invoking onEnable(), and if
        // onEnable() throws, it only logs one SEVERE line and keeps going — it does NOT disable
        // the plugin. Left alone, a mis-ordered bootstrap tier (Services' write-once setters and
        // strict getters throw IllegalStateException on exactly that) would leave the server
        // running a half-booted Tweaks that still reports isEnabled() == true. Catch and disable
        // explicitly so a bootstrap failure is loud and the plugin cleanly stops, instead of
        // limping on with only whatever tiers ran before the throw.
        try {
            // Load config.yml and initialize data storage
            saveDefaultConfig();
            reconcileConfigDefaults();

            // Tier 0 - console event logging must be published before every feature that can emit
            // an event. LoggingBootstrap reads no Services state; later tiers may use its slot.
            LoggingBootstrap.register(this, services);

            // Tier 1 - foundation state
            ProfilesBootstrap.register(this, services);
            EconomyBootstrap.register(this, services);
            me.beeliebub.tweaks.lottery.LotteryBootstrap.register(this, services);
            EconomyBootstrap.startPaymentReplay(this, services);
            RanksBootstrap.register(this, services);
            EconomyBootstrap.registerRankAwareListener(this, services);
            PermissionsBootstrap.register(this, services);
            ProtectionBootstrap.register(this, services);

            // Tier 2 - shared game state
            MinigamesBootstrap.register(this, services);
            WorldManagementBootstrap.register(this, services);

            // Tier 3 - player-facing infra
            PlayerAdminBootstrap.register(this, services);
            TabBootstrap.register(this, services);
            TeleportBootstrap.register(this, services);
            CoreBootstrap.register(this, services);
            ItemAdminBootstrap.register(this, services);

            // Tier 4 - enchantments
            EnchantmentsBootstrap.register(this, services);

            // Tier 5 - leaf features
            ItemAdminBootstrap.registerToolProtect(this, services);
            RecipesBootstrap.register(this, services);
            XpBottleBootstrap.register(this, services);
            BlockLogBootstrap.register(this, services);
            DeathInventoryBootstrap.register(this, services);

            getLogger().info("Tweaks has been enabled safely. Async I/O and Teleportation active.");
        } catch (RuntimeException e) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "Tweaks failed to enable - a bootstrap tier threw during onEnable(); disabling the plugin rather than running half-booted.", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    // Delegates to one XxxBootstrap.shutdown(this, services) call per feature package with a
    // teardown participant, in the same relative order those packages were registered in
    // onEnable() (permissions -> protection -> economy -> minigames). Each hook null-guards its
    // own Services fields, matching onEnable()'s partial-boot tolerance. See the root CLAUDE.md's
    // "Bootstrap" section.
    //
    // Each step runs inside its own try/catch (added alongside Roulette's shutdown wiring,
    // whose refund-any-BETTING-round guarantee is the first genuinely money-critical link in this
    // chain): an earlier participant throwing must never silently skip a later one's teardown —
    // unlike onEnable()'s single top-level catch (a boot failure disables the whole plugin, so
    // isolating tiers from each other would be pointless), a shutdown failure has nothing left to
    // "abort" into, so the only sound behavior is log-and-continue.
    @Override
    public void onDisable() {
        if (services == null) return;

        // Services' fields are package-private to this package specifically so Tweaks can read them
        // null-tolerantly here — see the root CLAUDE.md's "Bootstrap" section. Each Bootstrap.shutdown
        // takes its instances directly rather than Services itself, since it cannot read those fields
        // from outside this package.
        runShutdownStep("permissions", () -> PermissionsBootstrap.shutdown(this, services.permissionManager));
        runShutdownStep("protection", () -> ProtectionBootstrap.shutdown(this,
                services.protectionManager == null ? null : services.protectionManager.writer(),
                services.pendingStampsStore, services.regionSelectionManager));
        runShutdownStep("economy", () -> EconomyBootstrap.shutdown(this, services.housePaymentService, services.economyManager, services.houseAccount));
        runShutdownStep("lottery", () -> me.beeliebub.tweaks.lottery.LotteryBootstrap.shutdown(this, services.lotteryManager));
        runShutdownStep("minigames", () -> MinigamesBootstrap.shutdown(this, services.blackjackListener, services.rouletteListener));
        runShutdownStep("logging", () -> LoggingBootstrap.shutdown(services.consoleEventLog));
    }

    private void runShutdownStep(String name, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Tweaks: " + name + " shutdown failed — "
                    + "continuing with the remaining shutdown participants rather than aborting.", e);
        }
    }

    // Reconciles a live plugins/Tweaks/config.yml against the bundled config.yml so a key added
    // to a plugin update actually appears on an existing server's file — saveDefaultConfig() only
    // copies the bundled file the very first time the live file doesn't exist at all. Runs after
    // saveDefaultConfig() and before Tier 1, since ProtectionBootstrap/RankManager (tier 1) and
    // PlayerAdminManager/TeleportBootstrap (tier 3) all read config keys this must reconcile first.
    // Package-private (not private) so ConfigDefaultsTest can invoke it directly against a
    // deliberately edited live config — MockBukkit.load() only exercises it once, against an
    // already-bundled-identical fresh copy where nothing differs.
    //
    // A failure here is recoverable (every read site still has its inline fallback) and must never
    // escalate into the tier try/catch in onEnable() — unlike a mis-ordered tier, a reconciliation
    // failure isn't a reason to disable the whole plugin.
    void reconcileConfigDefaults() {
        try (InputStream in = getResource("config.yml")) {
            if (in == null) {
                // Not a transient runtime hiccup - the jar itself is missing a resource it's
                // supposed to bundle, and will reproduce identically on every boot until rebuilt.
                getLogger().severe("Bundled config.yml missing from the jar - skipping default reconciliation.");
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                // YamlConfiguration.loadConfiguration(Reader) swallows IOException/
                // InvalidConfigurationException internally (logs via Bukkit's own static logger
                // and returns an empty config) rather than throwing - calling load(Reader) on our
                // own instance instead lets a malformed bundled config.yml actually reach the
                // catch block below instead of silently installing an empty defaults layer.
                YamlConfiguration bundled = new YamlConfiguration();
                bundled.load(reader);

                // Snapshot before setDefaults() touches anything, so this diff reflects only keys
                // that were truly absent from the live file - not ones already inherited from a
                // previous reconciliation's defaults chain. Leaf paths only (skip intermediate
                // section paths like "protection.claim-cost") so the count reads as "N settings",
                // not inflated by every parent section a new setting happens to introduce.
                Set<String> liveKeysBefore = getConfig().getKeys(true);
                Set<String> added = new TreeSet<>();
                for (String path : bundled.getKeys(true)) {
                    if (!bundled.isConfigurationSection(path) && !liveKeysBefore.contains(path)) {
                        added.add(path);
                    }
                }

                getConfig().setDefaults(bundled);
                getConfig().options().copyDefaults(true);
                // saveConfig() swallows its own IOException and returns void, giving no signal a
                // caller can act on - saving directly here instead lets a real write failure reach
                // the catch block below, so the "added keys" log isn't printed as if it succeeded.
                getConfig().save(new File(getDataFolder(), "config.yml"));

                if (!added.isEmpty()) {
                    getLogger().info("Config reconciliation added " + added.size()
                            + " missing key(s) from bundled defaults: " + added);
                }
            }
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Could not reconcile config.yml against bundled defaults.", e);
        }
    }

}
