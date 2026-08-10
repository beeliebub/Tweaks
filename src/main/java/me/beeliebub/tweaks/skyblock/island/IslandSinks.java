package me.beeliebub.tweaks.skyblock.island;

import me.beeliebub.tweaks.skyblock.SkyblockConfig;
import me.beeliebub.tweaks.skyblock.economy.SkyblockEconomy;
import org.bukkit.block.Biome;

import java.util.Objects;

/** Wallet-backed cosmetic sinks shared by commands and future Dialog controls. */
public final class IslandSinks {
    private final IslandHomes homes;
    private final SkyblockEconomy economy;
    private final SkyblockConfig config;

    public IslandSinks(IslandHomes homes, SkyblockEconomy economy, SkyblockConfig config) {
        this.homes = Objects.requireNonNull(homes, "homes");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.config = Objects.requireNonNull(config, "config");
    }

    public Result purchaseHomeSlot(Island island) {
        return homes.purchaseSlot(island, economy, config.homeSlotPrice())
                ? Result.applied("home slot purchased") : Result.rejected("home slot purchase rejected");
    }

    /** Validates a biome choice before a chunk-budgeted biome job debits the configured sink. */
    public Result validateBiomeChange(Island island, Biome biome) {
        if (island == null || biome == null) return Result.rejected("island or biome not found");
        double price = config.biomeChangePrice();
        if (!Double.isFinite(price) || price < 0) return Result.rejected("biome price is invalid");
        return Result.applied("biome change may debit " + price);
    }

    public record Result(boolean applied, String reason) {
        static Result applied(String reason) { return new Result(true, reason); }
        static Result rejected(String reason) { return new Result(false, reason); }
    }
}
