package me.beeliebub.tweaks.tests.skyblock.tracking;

import io.papermc.paper.event.player.PlayerTradeEvent;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandManager;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import me.beeliebub.tweaks.skyblock.tracking.IslandTracker;
import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import me.beeliebub.tweaks.skyblock.tracking.TrackIdentifierDomain;
import me.beeliebub.tweaks.skyblock.tracking.TrackKey;
import me.beeliebub.tweaks.skyblock.tracking.TrackingInteractionListener;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingInteractionListenerTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final Clock TEST_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    private IslandManager islands;
    private IslandTracker tracker;
    private Island island;
    private Location location;
    private TrackingInteractionListener listener;

    @BeforeEach
    void setUp() {
        islands = mock(IslandManager.class);
        tracker = mock(IslandTracker.class);
        island = Island.create(OWNER, 0, IslandSize.SMALL, TEST_CLOCK);
        location = mock(Location.class);
        when(islands.islandAt(any(Location.class))).thenReturn(Optional.of(island));
        listener = new TrackingInteractionListener(islands, tracker);
    }

    @Test
    void declaresExactlyTheSixInteractionCategories() {
        assertEquals(EnumSet.of(
                TrackCategory.SHEAR,
                TrackCategory.BREED,
                TrackCategory.TAME,
                TrackCategory.BARTER,
                TrackCategory.BREW,
                TrackCategory.TRADE), TrackingInteractionListener.recordedCategories());
    }

    @Test
    void interactionCategoriesDeclareTheirIdentifierDomains() {
        Map<TrackCategory, TrackIdentifierDomain> expected = Map.of(
                TrackCategory.SHEAR, TrackIdentifierDomain.ENTITY_TYPE,
                TrackCategory.BREED, TrackIdentifierDomain.ENTITY_TYPE,
                TrackCategory.TAME, TrackIdentifierDomain.ENTITY_TYPE,
                TrackCategory.BARTER, TrackIdentifierDomain.MATERIAL,
                TrackCategory.BREW, TrackIdentifierDomain.MATERIAL,
                TrackCategory.TRADE, TrackIdentifierDomain.MATERIAL);

        expected.forEach((category, domain) -> assertEquals(domain, category.identifierDomain()));
    }

    @Test
    void onShearRecordsTheEntityTypeFromAConstructedPaperEvent() {
        Player player = playerAtIsland();
        Entity entity = mock(Entity.class);
        when(entity.getType()).thenReturn(EntityType.SHEEP);

        PlayerShearEntityEvent event = new PlayerShearEntityEvent(
                player, entity, stack(Material.SHEARS, 1), EquipmentSlot.HAND, List.of());

        listener.onShear(event);

        verifyRecord(TrackCategory.SHEAR, "SHEEP", 1L);
    }

    @Test
    void onBreedRecordsTheChildEntityTypeFromAConstructedPaperEvent() {
        Player breeder = playerAtIsland();
        LivingEntity child = mock(LivingEntity.class);
        LivingEntity mother = mock(LivingEntity.class);
        LivingEntity father = mock(LivingEntity.class);
        when(child.getType()).thenReturn(EntityType.COW);

        EntityBreedEvent event = new EntityBreedEvent(
                child, mother, father, breeder, stack(Material.WHEAT, 1), 0);

        listener.onBreed(event);

        verifyRecord(TrackCategory.BREED, "COW", 1L);
    }

    @Test
    void onTameRecordsTheEntityTypeFromAConstructedPaperEvent() {
        Player owner = playerAtIsland();
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getType()).thenReturn(EntityType.WOLF);

        EntityTameEvent event = new EntityTameEvent(entity, owner);

        listener.onTame(event);

        verifyRecord(TrackCategory.TAME, "WOLF", 1L);
    }

    @Test
    void onBarterRecordsEachOutcomeMaterialAndAmountFromAConstructedPaperEvent() {
        Piglin piglin = mock(Piglin.class);
        when(piglin.getLocation()).thenReturn(location);
        List<ItemStack> outcomes = List.of(
                stack(Material.QUARTZ, 2),
                stack(Material.OBSIDIAN, 3));
        PiglinBarterEvent event = new PiglinBarterEvent(
                piglin, stack(Material.GOLD_INGOT, 1), outcomes);

        listener.onBarter(event);

        verifyRecord(TrackCategory.BARTER, "QUARTZ", 2L);
        verifyRecord(TrackCategory.BARTER, "OBSIDIAN", 3L);
    }

    @Test
    void onBrewRecordsEachResultMaterialAndAmountFromAConstructedPaperEvent() {
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(location);
        BrewerInventory inventory = mock(BrewerInventory.class);
        List<ItemStack> results = List.of(
                stack(Material.POTION, 1),
                stack(Material.SPLASH_POTION, 2));
        BrewEvent event = new BrewEvent(block, inventory, results, 20);

        listener.onBrew(event);

        verifyRecord(TrackCategory.BREW, "POTION", 1L);
        verifyRecord(TrackCategory.BREW, "SPLASH_POTION", 2L);
    }

    @Test
    void onTradeRecordsTheRecipeResultMaterialAndAmountFromAConstructedPaperEvent() {
        Player player = playerAtIsland();
        AbstractVillager merchant = mock(AbstractVillager.class);
        MerchantRecipe recipe = mock(MerchantRecipe.class);
        ItemStack result = stack(Material.EMERALD, 4);
        when(recipe.getResult()).thenReturn(result);
        PlayerTradeEvent event = new PlayerTradeEvent(player, merchant, recipe, true, true);

        listener.onTrade(event);

        verifyRecord(TrackCategory.TRADE, "EMERALD", 4L);
    }

    private Player playerAtIsland() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(location);
        return player;
    }

    private static ItemStack stack(Material material, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }

    private void verifyRecord(TrackCategory category, String identifier, long amount) {
        verify(tracker).record(same(island), eq(new TrackKey(category, identifier)), eq(amount));
    }
}
