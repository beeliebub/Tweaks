package me.beeliebub.tweaks.skyblock.island;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Named homes owned by an island member and persisted as part of island state. */
public final class IslandHomes {

    private final IslandManager islands;
    private final int defaultSlots;
    private final int maximumSlots;

    public IslandHomes(IslandManager islands, int defaultSlots, int maximumSlots) {
        this.islands = islands;
        this.defaultSlots = Math.max(1, defaultSlots);
        this.maximumSlots = Math.max(this.defaultSlots, maximumSlots);
    }

    public boolean set(UUID player, String name, Location location) {
        Island island = islands.forPlayer(player).orElse(null);
        if (island == null || location == null || name == null || name.isBlank()) return false;
        Map<UUID, Map<String, Island.Home>> homes = copy(island.homes());
        Map<String, Island.Home> playerHomes = new LinkedHashMap<>(homes.getOrDefault(player, Map.of()));
        if (!playerHomes.containsKey(name.toLowerCase())
                && playerHomes.size() >= island.homeLimit(defaultSlots)) return false;
        playerHomes.put(name.toLowerCase(), fromLocation(location));
        homes.put(player, playerHomes);
        islands.update(island.withHomes(homes));
        return true;
    }

    public Optional<Location> get(UUID player, String name, WorldResolver resolver) {
        Island island = islands.forPlayer(player).orElse(null);
        if (island == null) return Optional.empty();
        Island.Home home = island.homes().getOrDefault(player, Map.of()).get(name.toLowerCase());
        if (home == null) return Optional.empty();
        org.bukkit.World world = resolver.resolve(home.world());
        return world == null ? Optional.empty() : Optional.of(new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch()));
    }

    public boolean delete(UUID player, String name) {
        Island island = islands.forPlayer(player).orElse(null);
        if (island == null) return false;
        Map<UUID, Map<String, Island.Home>> homes = copy(island.homes());
        Map<String, Island.Home> playerHomes = new LinkedHashMap<>(homes.getOrDefault(player, Map.of()));
        boolean removed = playerHomes.remove(name.toLowerCase()) != null;
        if (playerHomes.isEmpty()) homes.remove(player); else homes.put(player, playerHomes);
        if (removed) islands.update(island.withHomes(homes));
        return removed;
    }

    /** Removes every saved home belonging to a member who is losing island access. */
    public boolean clear(UUID player) {
        Island island = islands.forPlayer(player).orElse(null);
        if (island == null || !island.homes().containsKey(player)) return false;
        Map<UUID, Map<String, Island.Home>> homes = copy(island.homes());
        homes.remove(player);
        islands.update(island.withHomes(homes));
        return true;
    }

    public int maximumSlots() {
        return maximumSlots;
    }

    public boolean purchaseSlot(Island island) {
        if (island == null || defaultSlots + island.purchasedHomeSlots() >= maximumSlots) return false;
        islands.update(island.withPurchasedHomeSlots(island.purchasedHomeSlots() + 1));
        return true;
    }

    public boolean purchaseSlot(Island island, me.beeliebub.tweaks.skyblock.economy.SkyblockEconomy economy,
                                double price) {
        if (economy == null || !Double.isFinite(price) || price < 0) return false;
        if (price == 0) return purchaseSlot(island);
        if (economy.debit(island, price) != me.beeliebub.tweaks.skyblock.economy.SkyblockEconomy.Mutation.APPLIED) return false;
        try {
            if (purchaseSlot(island)) return true;
        } catch (RuntimeException ignored) {
            // The refund below restores the wallet when the island aggregate rejects the update.
        }
        economy.credit(island, price);
        return false;
    }

    private static Island.Home fromLocation(Location location) {
        return new Island.Home(location.getWorld().getKey().asString(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch());
    }

    private static Map<UUID, Map<String, Island.Home>> copy(Map<UUID, Map<String, Island.Home>> original) {
        Map<UUID, Map<String, Island.Home>> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, Island.Home>> entry : original.entrySet()) {
            result.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return result;
    }

    /** Narrow world resolution seam so this class stays straightforward to test. */
    public interface WorldResolver {
        org.bukkit.World resolve(String key);
    }
}
