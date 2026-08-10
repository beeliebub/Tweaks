package me.beeliebub.tweaks.skyblock.challenge;

import me.beeliebub.tweaks.skyblock.island.IslandSize;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Rewards are value objects; mutation is performed by ChallengeService. */
public sealed interface ChallengeReward permits ChallengeReward.Items,
        ChallengeReward.SizeUpgrade, ChallengeReward.GeneratorUnlock, ChallengeReward.Money {
    record Items(List<ItemStack> items) implements ChallengeReward {
        public Items {
            Objects.requireNonNull(items, "items");
            if (items.isEmpty()) throw new IllegalArgumentException("Item reward cannot be empty");
            List<ItemStack> copies = new ArrayList<>(items.size());
            for (int index = 0; index < items.size(); index++) {
                ItemStack item = Objects.requireNonNull(items.get(index), "item").clone();
                if (isAir(item.getType())) {
                    throw new IllegalArgumentException("Reward item " + index + " cannot be air");
                }
                if (item.getAmount() <= 0) {
                    throw new IllegalArgumentException("Reward item " + index + " amount must be positive");
                }
                try {
                    item.serialize();
                } catch (RuntimeException error) {
                    throw new IllegalArgumentException("Reward item " + index + " meta cannot be serialized", error);
                }
                copies.add(item);
            }
            items = List.copyOf(copies);
        }

        @Override
        public List<ItemStack> items() {
            return items.stream().map(ItemStack::clone).toList();
        }

        private static boolean isAir(Material material) {
            return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
        }
    }

    record SizeUpgrade(IslandSize size) implements ChallengeReward {
        public SizeUpgrade {
            Objects.requireNonNull(size, "size");
        }
    }

    record GeneratorUnlock(String tierId) implements ChallengeReward {
        public GeneratorUnlock {
            Objects.requireNonNull(tierId, "tierId");
            tierId = tierId.trim().toLowerCase(Locale.ROOT);
            if (tierId.isBlank()) throw new IllegalArgumentException("Generator tier cannot be blank");
        }
    }

    record Money(double amount) implements ChallengeReward {
        public Money {
            if (!Double.isFinite(amount) || amount <= 0) {
                throw new IllegalArgumentException("Money reward must be finite and positive");
            }
        }
    }
}
