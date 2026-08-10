package me.beeliebub.tweaks.tests.skyblock.type;

import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TypeRegistryCodecTest {
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
    void richKitItemSurvivesSaveAndReloadWithForeignPdc() {
        ItemStack original = richItem();
        TypeRegistry registry = new TypeRegistry(plugin);
        registry.load(new YamlConfiguration());
        registry.registerType(new IslandType("rich", "Rich", Set.of("normal"), "", 
                List.of(new IslandType.KitItem(original)), "PLAINS", Set.of()));

        registry.saveAsync().join();
        TypeRegistry reloaded = new TypeRegistry(plugin);
        reloaded.load();

        ItemStack actual = reloaded.type("rich").orElseThrow().kit().get(0).itemStack();
        assertEquals(original.serialize(), actual.serialize());
        assertEquals(41, actual.getItemMeta().getPersistentDataContainer()
                .get(FOREIGN_KEY, PersistentDataType.INTEGER));
    }

    @Test
    void containerKitContentsSurviveSaveAndReload() {
        ItemStack container = bundleWithContents();
        TypeRegistry registry = new TypeRegistry(plugin);
        registry.load(new YamlConfiguration());
        registry.registerType(new IslandType("container", "Container", Set.of("normal"), "",
                List.of(new IslandType.KitItem(container)), "PLAINS", Set.of()));

        registry.saveAsync().join();
        TypeRegistry reloaded = new TypeRegistry(plugin);
        reloaded.load();

        ItemStack actual = reloaded.type("container").orElseThrow().kit().get(0).itemStack();
        BundleMeta meta = (BundleMeta) actual.getItemMeta();
        assertEquals(List.of(new ItemStack(Material.EMERALD, 7)), meta.getItems());
        assertEquals(41, meta.getPersistentDataContainer().get(FOREIGN_KEY, PersistentDataType.INTEGER));
    }

    @Test
    void kitItemDefensivelyCopiesItsStack() {
        ItemStack original = richItem();
        IslandType.KitItem item = new IslandType.KitItem(original);

        original.setAmount(1);
        ItemStack exposed = item.itemStack();
        exposed.setAmount(9);

        assertEquals(2, item.amount());
    }

    @Test
    void legacyKitEntriesRemainScalarAndMalformedEntriesAreSkipped() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("types.legacy.difficulties", List.of("normal"));
        yaml.set("types.legacy.kit", List.of(
                Map.of("material", "not_a_material", "amount", 1),
                Map.of("material", "DIRT", "amount", 1.5d),
                Map.of("material", "OAK_LOG", "amount", "not-a-number"),
                Map.of("material", "STONE", "amount", 80)));

        TypeRegistry registry = new TypeRegistry(Logger.getAnonymousLogger());
        registry.load(yaml);

        IslandType.KitItem item = registry.type("legacy").orElseThrow().kit().get(0);
        assertEquals("stone", item.material());
        assertEquals(80, item.amount());
    }

    @Test
    void plainKitItemsRemainInLegacyScalarFormWhenSaved() {
        TypeRegistry registry = new TypeRegistry(plugin);
        registry.load(new YamlConfiguration());
        registry.registerType(new IslandType("plain", "Plain", Set.of("normal"), "",
                List.of(new IslandType.KitItem("STONE", 12)), "PLAINS", Set.of()));

        registry.saveAsync().join();

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(
                temporaryDirectory.resolve("skyblock/types.yml").toFile());
        Map<?, ?> item = assertInstanceOf(Map.class, saved.getList("types.plain.kit").get(0));
        assertEquals("stone", item.get("material"));
        assertEquals(12, item.get("amount"));
        assertFalse(item.containsKey("=="));
        assertFalse(item.containsKey("meta"));
    }

    @Test
    void kitValidationMatchesAFullChestEditor() {
        assertThrows(IllegalArgumentException.class, () -> new IslandType.KitItem("AIR", 1));
        assertThrows(IllegalArgumentException.class, () -> new IslandType.KitItem("STONE", 0));
        assertThrows(IllegalArgumentException.class, () -> new IslandType.KitItem("not_a_material", 1));

        List<IslandType.KitItem> oversized = new ArrayList<>();
        for (int slot = 0; slot <= IslandType.KIT_CONTAINER_SIZE; slot++) {
            oversized.add(new IslandType.KitItem("STONE", 1));
        }
        assertThrows(IllegalArgumentException.class, () -> new IslandType(
                "oversized", "Oversized", Set.of("normal"), "", oversized, "PLAINS", Set.of()));
    }

    private static ItemStack richItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE, 2);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Foreign pick"));
        meta.lore(List.of(Component.text("Lore survives")));
        meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        meta.setCustomModelData(7102);
        meta.getPersistentDataContainer().set(FOREIGN_KEY, PersistentDataType.INTEGER, 41);
        item.setItemMeta(meta);
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
