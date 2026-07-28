package me.beeliebub.tweaks.economy;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Tier:      1 - foundation state
 * Publishes: economyManager, houseAccount, and housePaymentService (read by minigames' blackjack
 *            and by shutdown())
 * Straddle:  EconomyListener needs both EconomyManager and RankManager, so it is published
 *            through a second entry point (registerRankAwareListener), called immediately
 *            after ranks.RanksBootstrap.register in the same tier, rather than folding
 *            ranks' command wiring into this package.
 * Ordering:  housePaymentService's startup replay is chained onto houseAccount's own async load
 *            (whenLoaded()) rather than awaited here, so register() stays non-blocking — replay
 *            only runs, and housePaymentService only becomes ready, if the house account itself
 *            loaded successfully.
 */
public final class EconomyBootstrap {

    private EconomyBootstrap() {}

    public static void register(Tweaks plugin, Services services) {
        EconomyManager economyManager = new EconomyManager(plugin);
        services.setEconomyManager(economyManager);

        HouseAccount houseAccount = new HouseAccount(plugin);
        services.setHouseAccount(houseAccount);

        HousePaymentService housePaymentService = new HousePaymentService(plugin, houseAccount, economyManager);
        services.setHousePaymentService(housePaymentService);
        houseAccount.whenLoaded().thenRun(() -> {
            if (houseAccount.isLoaded()) {
                housePaymentService.replayPendingPayments();
            } else {
                plugin.getLogger().severe("House account failed to load; house payments will remain unavailable until restart");
            }
        }).exceptionally(error -> {
            // Nothing downstream awaits this chain, so an uncaught exception here (e.g. a future
            // change to pendingPayments() that throws synchronously) would otherwise vanish with
            // housePaymentService.isReady() silently stuck false and no log explaining why.
            plugin.getLogger().log(Level.SEVERE, "House payment replay failed to start", error);
            return null;
        });

        BalanceCommand balanceCommand = new BalanceCommand(economyManager);
        plugin.getCommand("balance").setExecutor(balanceCommand);
        plugin.getCommand("balance").setTabCompleter(balanceCommand);

        HouseCommand houseCommand = new HouseCommand(plugin, houseAccount, housePaymentService);
        plugin.getCommand("house").setExecutor(houseCommand);
        plugin.getCommand("house").setTabCompleter(houseCommand);
    }

    public static void registerRankAwareListener(Tweaks plugin, Services services) {
        plugin.getServer().getPluginManager().registerEvents(
                new EconomyListener(plugin, services.economyManager(), services.rankManager()), plugin);
    }

    /**
     * Bounded-wait shutdown participant, in this order: reject new house payments, wait (5s) for
     * any in flight, flush every cached player balance (5s), then flush the house-account snapshot
     * (5s). Payment sequencing must be given a chance to reach a terminal or safely-replayable state
     * before the generic flushes run, since those flushes know nothing about payment semantics.
     *
     * <p>Takes the three instances directly, rather than {@code Services}, because {@code Services}'
     * fields are package-private to {@code me.beeliebub.tweaks} for {@code Tweaks}' own null-tolerant
     * partial-boot reads — {@code Tweaks#onDisable()} passes them through here already resolved.
     *
     * <p>An interrupt caught in one phase is <b>not</b> re-asserted until every phase has had its
     * own chance to run its full bounded wait — {@code Future#get(timeout, unit)} checks the
     * interrupted flag at entry and throws immediately if it is already set, so re-interrupting
     * eagerly would silently collapse every later phase's 5-second budget to zero.
     */
    public static void shutdown(Tweaks plugin, HousePaymentService housePaymentService,
                                EconomyManager economyManager, HouseAccount houseAccount) {
        boolean interrupted = false;

        if (housePaymentService != null) {
            housePaymentService.beginShutdown();
            try {
                housePaymentService.awaitInFlight().get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                plugin.getLogger().severe("In-flight house payments did not finish before shutdown timeout; "
                        + "surviving journal entries will replay on next startup.");
            } catch (InterruptedException e) {
                interrupted = true;
                plugin.getLogger().warning("House payment shutdown wait interrupted.");
            } catch (java.util.concurrent.ExecutionException e) {
                plugin.getLogger().log(Level.WARNING, "An in-flight house payment failed during shutdown", e);
            }
        }
        if (economyManager != null) {
            try {
                economyManager.saveAll().get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                // Outstanding writes are not cancelled and may still be running on the common
                // pool after the JVM exits, risking a truncated file — SEVERE, not a routine warning.
                plugin.getLogger().severe("Economy flush did not finish before shutdown timeout; "
                        + "some player data may not have been saved.");
            } catch (InterruptedException e) {
                interrupted = true;
                plugin.getLogger().warning("Economy flush interrupted during shutdown.");
            } catch (java.util.concurrent.ExecutionException e) {
                plugin.getLogger().log(Level.WARNING, "Economy flush failed during shutdown", e);
            }
        }
        if (houseAccount != null) {
            try {
                houseAccount.flush().get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                plugin.getLogger().severe("House-account flush did not finish before shutdown timeout.");
            } catch (InterruptedException e) {
                interrupted = true;
                plugin.getLogger().warning("House-account flush interrupted during shutdown.");
            } catch (java.util.concurrent.ExecutionException e) {
                plugin.getLogger().log(Level.WARNING, "House-account flush failed during shutdown", e);
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
