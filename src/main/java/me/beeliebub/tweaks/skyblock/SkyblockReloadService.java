package me.beeliebub.tweaks.skyblock;

import me.beeliebub.tweaks.skyblock.challenge.ChallengeRegistry;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.generator.GeneratorRegistry;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;

import java.util.Objects;

/** Atomically reloads the four editable Skyblock registries through their snapshot APIs. */
public final class SkyblockReloadService {
    private final ChallengeRegistry challenges;
    private final TypeRegistry types;
    private final GeneratorRegistry generators;
    private final ShopCatalog shop;

    public SkyblockReloadService(ChallengeRegistry challenges, TypeRegistry types,
                                 GeneratorRegistry generators, ShopCatalog shop) {
        this.challenges = Objects.requireNonNull(challenges, "challenges");
        this.types = Objects.requireNonNull(types, "types");
        this.generators = Objects.requireNonNull(generators, "generators");
        this.shop = Objects.requireNonNull(shop, "shop");
    }

    public void reloadAll() {
        types.load();
        challenges.load();
        types.difficulties().forEach(difficulty -> challenges.setMultiplier(
                difficulty.id(), difficulty.multiplier()));
        generators.load();
        shop.load();
    }
}
