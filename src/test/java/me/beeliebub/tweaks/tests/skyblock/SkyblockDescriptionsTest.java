package me.beeliebub.tweaks.tests.skyblock;

import me.beeliebub.tweaks.skyblock.SkyblockDescriptions;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRequirement;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeReward;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import me.beeliebub.tweaks.skyblock.tracking.TrackKey;
import me.beeliebub.tweaks.skyblock.type.IslandDifficulty;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkyblockDescriptionsTest {

    @Test
    void describesEachRequirementVariant() {
        assertEquals("Track collect:OAK_LOG x12",
                SkyblockDescriptions.requirement(new ChallengeRequirement.Tracked(
                        new TrackKey(TrackCategory.COLLECT, "oak_log"), 12)));
        assertEquals("Have 3 Diamond Block",
                SkyblockDescriptions.requirement(new ChallengeRequirement.Possession(
                        Material.DIAMOND_BLOCK, 3)));
    }

    @Test
    void describesEachRewardVariant() {
        assertEquals("Items: 2 Diamond Sword, 5 Emerald",
                SkyblockDescriptions.reward(new ChallengeReward.Items(List.of(
                        item(Material.DIAMOND_SWORD, 2),
                        item(Material.EMERALD, 5)))));
        assertEquals("Upgrade island size to LARGE",
                SkyblockDescriptions.reward(new ChallengeReward.SizeUpgrade(IslandSize.LARGE)));
        assertEquals("Unlock generator tier tier-2",
                SkyblockDescriptions.reward(new ChallengeReward.GeneratorUnlock("  TIER-2 ")));
        assertEquals("Receive 10 Skybucks",
                SkyblockDescriptions.reward(new ChallengeReward.Money(10.0d)));
        assertEquals("Receive 12.50 Skybucks",
                SkyblockDescriptions.reward(new ChallengeReward.Money(12.5d)));
    }

    @Test
    void describesGeneratorOutputsWithDeterministicWeightsAndShares() {
        Map<Material, Double> outputs = new LinkedHashMap<>();
        outputs.put(Material.COBBLESTONE, 3.0d);
        outputs.put(Material.IRON_ORE, 1.5d);
        GeneratorTier tier = new GeneratorTier("ore", "Ore", outputs);

        assertEquals("Cobblestone - 3 weight (66.7%)",
                SkyblockDescriptions.generatorOutput(tier,
                        tier.outputs().entrySet().stream().toList().get(0)));
        assertEquals("Iron Ore - 1.50 weight (33.3%)",
                SkyblockDescriptions.generatorOutput(tier,
                        tier.outputs().entrySet().stream().toList().get(1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shopEntries")
    void describesEveryShopDirectionState(String name, ShopCatalog.Entry entry, String expected) {
        assertEquals(expected, SkyblockDescriptions.shopEntry(entry));
    }

    @Test
    void describesIslandTypesWithAndWithoutTemplates() {
        IslandType kitType = new IslandType("starter", "Starter", Set.of("normal", "hard"), "",
                List.of(new IslandType.KitItem(item(Material.DIAMOND_PICKAXE, 2))));
        IslandType templateType = new IslandType("rich", "Rich", Set.of("normal"), "starter-template");

        assertEquals("Starter (starter) - no template, 2 difficulty option(s), 1 kit item(s)",
                SkyblockDescriptions.islandType(kitType));
        assertEquals("Rich (rich) - template starter-template, 1 difficulty option(s), 0 kit item(s)",
                SkyblockDescriptions.islandType(templateType));
    }

    @Test
    void describesDifficultiesWithIntegerAndDecimalMultipliers() {
        assertEquals("Normal (normal) x1, order 0",
                SkyblockDescriptions.difficulty(new IslandDifficulty("normal", "Normal", 0)));
        assertEquals("Hard (hard) x1.25, order 2",
                SkyblockDescriptions.difficulty(new IslandDifficulty("hard", "Hard", 2, 1.25d)));
    }

    @Test
    void describesMaterialsEntitiesAndRawGeneratorOutput() {
        assertEquals("Diamond Block", SkyblockDescriptions.material(Material.DIAMOND_BLOCK));
        assertEquals("Elder Guardian", SkyblockDescriptions.entity("elder_guardian"));
        assertEquals("Iron Ore - 2 weight (0.0%)",
                SkyblockDescriptions.generatorOutput(Material.IRON_ORE, 2.0d, 0.0d));
    }

    @Test
    void descriptionHelpersRejectNullModels() {
        GeneratorTier tier = new GeneratorTier("default", "Default", Map.of(Material.STONE, 1.0d));

        assertThrows(NullPointerException.class, () -> SkyblockDescriptions.requirement(null));
        assertThrows(NullPointerException.class, () -> SkyblockDescriptions.reward(null));
        assertThrows(NullPointerException.class,
                () -> SkyblockDescriptions.generatorOutput(null, Map.entry(Material.STONE, 1.0d)));
        assertThrows(NullPointerException.class, () -> SkyblockDescriptions.generatorOutput(tier, null));
        assertThrows(NullPointerException.class,
                () -> SkyblockDescriptions.generatorOutput(null, 1.0d, 1.0d));
        assertThrows(NullPointerException.class, () -> SkyblockDescriptions.shopEntry(null));
        assertThrows(NullPointerException.class, () -> SkyblockDescriptions.islandType(null));
        assertThrows(NullPointerException.class, () -> SkyblockDescriptions.difficulty(null));
        assertThrows(NullPointerException.class, () -> SkyblockDescriptions.material(null));
        assertThrows(NullPointerException.class, () -> SkyblockDescriptions.entity(null));
    }

    private static Stream<Arguments> shopEntries() {
        return Stream.of(
                Arguments.of("buy and sell", new ShopCatalog.Entry(
                        Material.IRON_INGOT, "Ores", 12.5d, 7.0d),
                        "Iron Ingot - ores, buy 12.50, sell 7"),
                Arguments.of("buy disabled", new ShopCatalog.Entry(
                        Material.IRON_INGOT, "Ores", -1.0d, 7.0d),
                        "Iron Ingot - ores, buy disabled, sell 7"),
                Arguments.of("sell disabled", new ShopCatalog.Entry(
                        Material.IRON_INGOT, "Ores", 12.5d, -1.0d),
                        "Iron Ingot - ores, buy 12.50, sell disabled"),
                Arguments.of("buy and sell disabled", new ShopCatalog.Entry(
                        Material.IRON_INGOT, "Ores", -1.0d, -1.0d),
                        "Iron Ingot - ores, buy disabled, sell disabled"));
    }

    private static ItemStack item(Material material, int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.clone()).thenReturn(item);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(amount);
        when(item.serialize()).thenReturn(Map.of("type", material.name(), "amount", amount));
        return item;
    }
}
