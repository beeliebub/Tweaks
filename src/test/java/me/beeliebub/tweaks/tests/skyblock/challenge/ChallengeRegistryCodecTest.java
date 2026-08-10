package me.beeliebub.tweaks.tests.skyblock.challenge;

import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRegistry;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeReward;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChallengeRegistryCodecTest {
    private static final NamespacedKey FOREIGN_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("foreign-plugin:kept"));

    @TempDir
    Path temporaryDirectory;

    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void richItemRewardSurvivesSaveAndReloadWithForeignPdc() {
        ItemStack original = richItem();
        ChallengeRegistry registry = new ChallengeRegistry(plugin);
        registry.register(new Challenge("rich", "general", "Rich", "", List.of(), List.of(), List.of(),
                List.of(new ChallengeReward.Items(List.of(original))), Set.of()));

        registry.saveAsync().join();
        ChallengeRegistry reloaded = new ChallengeRegistry(plugin);
        reloaded.load();

        ChallengeReward.Items reward = (ChallengeReward.Items) reloaded.challenge("rich").orElseThrow()
                .rewards().get(0);
        ItemStack actual = reward.items().get(0);
        assertEquals(original.serialize(), actual.serialize());
        assertEquals(41, actual.getItemMeta().getPersistentDataContainer()
                .get(FOREIGN_KEY, PersistentDataType.INTEGER));
    }

    @Test
    void containerRewardContentsSurviveSaveAndReload() {
        ItemStack container = bundleWithContents();
        ChallengeRegistry registry = new ChallengeRegistry(plugin);
        registry.register(new Challenge("container", "general", "Container", "", List.of(), List.of(),
                List.of(), List.of(new ChallengeReward.Items(List.of(container))), Set.of()));

        registry.saveAsync().join();
        ChallengeRegistry reloaded = new ChallengeRegistry(plugin);
        reloaded.load();

        ChallengeReward.Items reward = (ChallengeReward.Items) reloaded.challenge("container").orElseThrow()
                .rewards().get(0);
        BundleMeta meta = (BundleMeta) reward.items().get(0).getItemMeta();
        assertEquals(List.of(new ItemStack(Material.EMERALD, 7)), meta.getItems());
        assertEquals(41, meta.getPersistentDataContainer().get(FOREIGN_KEY, PersistentDataType.INTEGER));
    }

    @Test
    void itemRewardDefensivelyCopiesItsStacks() {
        ItemStack original = richItem();
        ChallengeReward.Items reward = new ChallengeReward.Items(List.of(original));

        original.setAmount(1);
        ItemStack exposed = reward.items().get(0);
        exposed.setAmount(9);

        assertEquals(2, reward.items().get(0).getAmount());
    }

    @Test
    void legacyAndMalformedItemEntriesAreHandledIndependently() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("challenges.items.rewards", List.of(Map.of(
                "type", "items",
                "items", List.of(
                        Map.of("material", "not_a_material", "amount", 1),
                        Map.of("material", "DIRT", "amount", 1.5d),
                        Map.of("material", "OAK_LOG", "amount", "not-a-number"),
                        Map.of("material", "DIAMOND", "amount", 3)))));

        ChallengeRegistry registry = new ChallengeRegistry();
        registry.load(yaml);

        ChallengeReward.Items reward = (ChallengeReward.Items) registry.challenge("items").orElseThrow()
                .rewards().get(0);
        assertEquals(1, reward.items().size());
        assertEquals(new ItemStack(Material.DIAMOND, 3), reward.items().get(0));
    }

    @Test
    void malformedRewardDoesNotAbortFollowingRewards() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("challenges.rewards.rewards", List.of(
                Map.of("type", "money", "amount", "not-a-number"),
                Map.of("type", "money", "amount", 12.5d)));

        ChallengeRegistry registry = new ChallengeRegistry();
        registry.load(yaml);

        assertEquals(List.of(new ChallengeReward.Money(12.5d)),
                registry.challenge("rewards").orElseThrow().rewards());
    }

    private static ItemStack richItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD, 2);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Foreign blade"));
        meta.lore(List.of(Component.text("Lore survives")));
        meta.addEnchant(Enchantment.SHARPNESS, 4, true);
        meta.setCustomModelData(7102);
        meta.getPersistentDataContainer().set(FOREIGN_KEY, PersistentDataType.INTEGER, 41);
        item.setItemMeta(meta);
        assertTrue(item.hasItemMeta());
        return item;
    }

    private static ItemStack bundleWithContents() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        meta.setItems(List.of(new ItemStack(Material.EMERALD, 7)));
        meta.getPersistentDataContainer().set(FOREIGN_KEY, PersistentDataType.INTEGER, 41);
        bundle.setItemMeta(meta);
        return bundle;
    }
}
