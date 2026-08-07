package me.beeliebub.tweaks.lottery;

import me.beeliebub.tweaks.Services;
import me.beeliebub.tweaks.Tweaks;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Registers the lottery after the Tier 1 economy services are published. */
public final class LotteryBootstrap {

    private LotteryBootstrap() {
    }

    public static void register(Tweaks plugin, Services services) {
        LotteryManager manager = new LotteryManager(plugin, services.houseAccount(),
                services.housePaymentService(), services.economyManager());
        services.setLotteryManager(manager);
        LotteryCommand command = new LotteryCommand(plugin, manager, services.houseAccount(),
                services.housePaymentService());
        plugin.getCommand("lottery").setExecutor(command);
        plugin.getCommand("lottery").setTabCompleter(command);
    }

    public static void shutdown(Tweaks plugin, LotteryManager manager) {
        if (manager == null) return;
        try {
            manager.flush().get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            plugin.getLogger().severe("Lottery flush did not finish before shutdown timeout.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Lottery flush interrupted during shutdown.");
        } catch (java.util.concurrent.ExecutionException e) {
            plugin.getLogger().log(Level.WARNING, "Lottery flush failed during shutdown.", e);
        }
    }
}
