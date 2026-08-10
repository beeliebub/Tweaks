package me.beeliebub.tweaks.skyblock.challenge;

import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.skyblock.island.IslandRegionBridge;
import me.beeliebub.tweaks.skyblock.tracking.TrackKey;
import me.beeliebub.tweaks.skyblock.tracking.SkyblockPdc;
import me.beeliebub.tweaks.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Validates and claims island challenges without depending on any UI implementation. */
public final class ChallengeService {
    private final ChallengeRegistry registry;
    private final IslandManager islands;
    private final IslandRegionBridge regions;
    private final SkyblockPdc pdc;
    private final MoneyReward moneyReward;
    private final Consumer<Island> islandChanged;
    private final ReadyNotification notification;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public ChallengeService(ChallengeRegistry registry, IslandManager islands) {
        this(registry, islands, null, null, (island, amount) -> { }, ignored -> { }, (island, id) -> { });
    }

    public ChallengeService(ChallengeRegistry registry, IslandManager islands, IslandRegionBridge regions,
                            SkyblockPdc pdc, MoneyReward moneyReward, Consumer<Island> islandChanged,
                            ReadyNotification notification) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.islands = Objects.requireNonNull(islands, "islands");
        this.regions = regions;
        this.pdc = pdc;
        this.moneyReward = moneyReward == null ? (island, amount) -> { } : moneyReward;
        this.islandChanged = islandChanged == null ? ignored -> { } : islandChanged;
        this.notification = notification == null ? (island, id) -> { } : notification;
    }

    public Readiness readiness(Island island, String challengeId, Player player) {
        Challenge challenge = registry.challenge(challengeId).orElse(null);
        if (island == null || challenge == null) return Readiness.notReady("Unknown challenge");
        if (island.completedChallenges().contains(challenge.id())) return Readiness.notReady("Already completed");
        if (!registry.availableTo(challenge, island)) return Readiness.notReady("Unavailable for this island type");
        for (String prerequisite : challenge.prerequisites()) {
            if (!island.completedChallenges().contains(prerequisite)) return Readiness.notReady("Requires " + prerequisite);
        }
        for (Challenge.PrerequisiteGroup group : challenge.anyOfGroups()) {
            long completed = group.challengeIds().stream().filter(island.completedChallenges()::contains).count();
            if (completed < group.required()) return Readiness.notReady("Requires " + group.required() + " prerequisite challenges");
        }
        List<MissingRequirement> missing = new ArrayList<>();
        double multiplier = registry.multiplier(island.difficultyId());
        for (ChallengeRequirement requirement : challenge.requirements()) {
            long required = scaled(requirement.amount(), multiplier);
            if (requirement instanceof ChallengeRequirement.Tracked tracked) {
                long current = island.counters().getOrDefault(tracked.key().format(), 0L);
                if (current < required) missing.add(new MissingRequirement(tracked.key().format(), required - current));
            } else if (requirement instanceof ChallengeRequirement.Possession possession) {
                long current = player == null ? 0 : countPossession(player, possession.material(), pdc);
                if (current < required) missing.add(new MissingRequirement(possession.material().name(), required - current));
            }
        }
        return missing.isEmpty() ? Readiness.success() : new Readiness(false, "Requirements incomplete", List.copyOf(missing));
    }

    public boolean canUse(Player player) {
        return player != null && player.isOnline() && player.hasPermission(Permissions.SKYBLOCK_USE)
                && islands.config().worldKey().equalsIgnoreCase(player.getWorld().getKey().asString());
    }

    public boolean canUse(Island island, Player player) {
        return island != null && canUse(player)
                && islands.forPlayer(player.getUniqueId()).map(current -> current.id().equals(island.id())).orElse(false);
    }

    public ClaimResult claim(Island island, String challengeId, Player player) {
        if (island == null || player == null) return ClaimResult.failed("A player and island are required", island);
        Island memberIsland = islands.forPlayer(player.getUniqueId()).orElse(null);
        if (memberIsland == null || !memberIsland.id().equals(island.id()) || !canUse(player)) {
            return ClaimResult.failed("You no longer have access to this island", island);
        }
        Object lock = locks.computeIfAbsent(island.id(), ignored -> new Object());
        synchronized (lock) {
            Island current = islands.byId(island.id()).orElse(island);
            if (!current.isMember(player.getUniqueId())) {
                return ClaimResult.failed("You no longer have access to this island", current);
            }
            Readiness readiness = readiness(current, challengeId, player);
            if (!readiness.ready()) return ClaimResult.failed(readiness.reason(), current);
            Challenge challenge = registry.challenge(challengeId).orElseThrow();
            for (ChallengeReward reward : challenge.rewards()) {
                if (reward instanceof ChallengeReward.SizeUpgrade upgrade
                        && (regions == null || upgrade.size().chunks() <= current.size().chunks())) {
                    return ClaimResult.failed("The island cannot be resized to that size", current);
                }
                if (reward instanceof ChallengeReward.Money money && !Double.isFinite(money.amount())) {
                    return ClaimResult.failed("The reward amount is not representable", current);
                }
            }
            ItemStack[] inventoryBefore = cloneContents(player.getInventory().getContents());
            ItemStack cursorBefore = player.getOpenInventory().getCursor() == null
                    ? null : player.getOpenInventory().getCursor().clone();
            if (!consumePossessions(player, challenge, registry.multiplier(current.difficultyId()), pdc)) {
                return ClaimResult.failed("Possession requirements changed", current);
            }
            Island changed = current;
            try {
                for (ChallengeReward reward : challenge.rewards()) {
                    if (reward instanceof ChallengeReward.Items items) {
                        grant(player, items.items());
                    } else if (reward instanceof ChallengeReward.SizeUpgrade upgrade) {
                        if (regions == null || !regions.resize(changed, player.getWorld(), upgrade.size())) {
                            restoreContents(player, inventoryBefore, cursorBefore);
                            return ClaimResult.failed("The island could not be resized", current);
                        }
                        changed = changed.withSize(upgrade.size());
                    } else if (reward instanceof ChallengeReward.GeneratorUnlock unlock) {
                        changed = changed.withGeneratorTierId(unlock.tierId());
                    } else if (reward instanceof ChallengeReward.Money money) {
                        moneyReward.grant(changed, money.amount());
                    }
                }
            } catch (RuntimeException error) {
                restoreContents(player, inventoryBefore, cursorBefore);
                return ClaimResult.failed("The challenge reward could not be applied", current);
            }
            Set<String> completed = new HashSet<>(changed.completedChallenges());
            completed.add(challenge.id());
            changed = changed.withCompletedChallenges(completed);
            islands.update(changed);
            islandChanged.accept(changed);
            onPrerequisiteClaimed(changed);
            return ClaimResult.success(changed);
        }
    }

    /** Administrative completion path; it bypasses player requirements but still applies rewards atomically. */
    public ClaimResult forceComplete(Island island, String challengeId) {
        return forceComplete(island, challengeId, null);
    }

    public ClaimResult forceComplete(Island island, String challengeId, World world) {
        if (island == null) return ClaimResult.failed("Island not found", null);
        Challenge challenge = registry.challenge(challengeId).orElse(null);
        if (challenge == null) return ClaimResult.failed("Unknown challenge", island);
        Object lock = locks.computeIfAbsent(island.id(), ignored -> new Object());
        synchronized (lock) {
            Island current = islands.byId(island.id()).orElse(island);
            if (current.completedChallenges().contains(challenge.id())) {
                return ClaimResult.failed("Already completed", current);
            }
            if (!registry.availableTo(challenge, current)) {
                return ClaimResult.failed("Unavailable for this island type", current);
            }
            Island changed = current;
            Player owner = Bukkit.getPlayer(current.owner());
            if (challenge.rewards().stream().anyMatch(reward -> reward instanceof ChallengeReward.Items)
                    && owner == null) {
                return ClaimResult.failed("The owner must be online for item rewards", current);
            }
            World resizeWorld = world != null ? world : owner == null ? null : owner.getWorld();
            for (ChallengeReward reward : challenge.rewards()) {
                if (reward instanceof ChallengeReward.SizeUpgrade upgrade
                        && (regions == null || resizeWorld == null || upgrade.size().chunks() <= changed.size().chunks())) {
                    return ClaimResult.failed("The island cannot be resized to that size", current);
                }
            }
            ItemStack[] inventoryBefore = owner == null ? null : cloneContents(owner.getInventory().getContents());
            ItemStack cursorBefore = owner == null ? null : owner.getOpenInventory().getCursor() == null
                    ? null : owner.getOpenInventory().getCursor().clone();
            try {
                for (ChallengeReward reward : challenge.rewards()) {
                    if (reward instanceof ChallengeReward.Items items && owner != null) {
                        grant(owner, items.items());
                    } else if (reward instanceof ChallengeReward.SizeUpgrade upgrade) {
                        if (!regions.resize(changed, resizeWorld, upgrade.size())) {
                            if (owner != null) restoreContents(owner, inventoryBefore, cursorBefore);
                            return ClaimResult.failed("The island could not be resized", current);
                        }
                        changed = changed.withSize(upgrade.size());
                    } else if (reward instanceof ChallengeReward.GeneratorUnlock unlock) {
                        changed = changed.withGeneratorTierId(unlock.tierId());
                    } else if (reward instanceof ChallengeReward.Money money) {
                        moneyReward.grant(changed, money.amount());
                    }
                }
            } catch (RuntimeException error) {
                if (owner != null) restoreContents(owner, inventoryBefore, cursorBefore);
                return ClaimResult.failed("The challenge reward could not be applied", current);
            }
            Set<String> completed = new HashSet<>(changed.completedChallenges());
            completed.add(challenge.id());
            changed = changed.withCompletedChallenges(completed);
            islands.update(changed);
            islandChanged.accept(changed);
            onPrerequisiteClaimed(changed);
            return ClaimResult.success(changed);
        }
    }

    public void onCounterChanged(Island island) {
        notifyReady(island);
    }

    public void onPrerequisiteClaimed(Island island) {
        notifyReady(island);
    }

    public void onMemberLogin(Island island, Player player) {
        notifyReady(island, player);
    }

    private void notifyReady(Island island) {
        notifyReady(island, null);
    }

    private void notifyReady(Island island, Player player) {
        if (island == null) return;
        Set<String> notified = new HashSet<>(island.notifiedChallenges());
        Island changed = island;
        for (Challenge challenge : registry.challenges()) {
            if (notified.contains(challenge.id()) || !registry.availableTo(challenge, island)) continue;
            if (readiness(island, challenge.id(), player).ready()) {
                notified.add(challenge.id());
                notification.ready(island, challenge.id());
            }
        }
        if (!notified.equals(island.notifiedChallenges())) {
            changed = island.withNotifiedChallenges(notified);
            islands.update(changed);
        }
    }

    private static long scaled(long amount, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0) multiplier = 1.0d;
        double result = amount * multiplier;
        if (result >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(1L, (long) Math.ceil(result));
    }

    private static long countPossession(Player player, org.bukkit.Material material, SkyblockPdc pdc) {
        long total = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < Math.min(36, contents.length); index++) {
            ItemStack stack = contents[index];
            if (stack != null && stack.getType() == material && allowed(stack, pdc)) total += stack.getAmount();
        }
        return total;
    }

    private static boolean consumePossessions(Player player, Challenge challenge, double multiplier, SkyblockPdc pdc) {
        Map<org.bukkit.Material, Long> required = new LinkedHashMap<>();
        for (ChallengeRequirement requirement : challenge.requirements()) {
            if (requirement instanceof ChallengeRequirement.Possession possession) {
                required.merge(possession.material(), scaled(possession.amount(), multiplier), Math::addExact);
            }
        }
        for (Map.Entry<org.bukkit.Material, Long> entry : required.entrySet()) {
            Need need = new Need(entry.getKey(), entry.getValue());
            if (countPossession(player, need.material(), pdc) < need.amount()) return false;
            long remaining = need.amount();
            ItemStack[] contents = player.getInventory().getContents();
            for (int index = 0; index < Math.min(36, contents.length) && remaining > 0; index++) {
                ItemStack stack = contents[index];
                if (stack == null || stack.getType() != need.material() || !allowed(stack, pdc)) continue;
                int take = (int) Math.min(remaining, stack.getAmount());
                stack.setAmount(stack.getAmount() - take);
                if (stack.getAmount() <= 0) contents[index] = null;
                remaining -= take;
            }
            player.getInventory().setContents(contents);
        }
        return true;
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        return copy;
    }

    private static void restoreContents(Player player, ItemStack[] contents, ItemStack cursor) {
        player.getInventory().setContents(cloneContents(contents));
        player.getOpenInventory().setCursor(cursor == null ? new ItemStack(org.bukkit.Material.AIR) : cursor.clone());
    }

    private static boolean allowed(ItemStack stack, SkyblockPdc pdc) {
        if (stack == null || stack.getType().isAir()) return false;
        if (pdc != null) return pdc.foreignStateAllowed(stack);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return true;
        if (meta.hasDisplayName() || meta.hasLore() || !meta.getEnchants().isEmpty()
                || meta.hasCustomModelData() || meta instanceof Damageable damageable && damageable.hasDamage()) {
            return false;
        }
        return meta.getPersistentDataContainer().getKeys().isEmpty();
    }

    private static void grant(Player player, List<ItemStack> rewards) {
        for (ItemStack item : rewards) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            Location location = player.getLocation();
            for (ItemStack drop : overflow.values()) player.getWorld().dropItemNaturally(location, drop);
        }
    }

    public record Readiness(boolean ready, String reason, List<MissingRequirement> missing) {
        public Readiness {
            missing = missing == null ? List.of() : List.copyOf(missing);
        }

        public static Readiness success() { return new Readiness(true, "Ready", List.of()); }
        public static Readiness notReady(String reason) { return new Readiness(false, reason, List.of()); }
    }

    public record MissingRequirement(String key, long amount) { }

    public record ClaimResult(boolean claimed, String reason, Island island) {
        public static ClaimResult success(Island island) { return new ClaimResult(true, "Claimed", island); }
        public static ClaimResult failed(String reason, Island island) { return new ClaimResult(false, reason, island); }
    }

    private record Need(org.bukkit.Material material, long amount) { }

    @FunctionalInterface public interface MoneyReward { void grant(Island island, double amount); }
    @FunctionalInterface public interface ReadyNotification { void ready(Island island, String challengeId); }
}
