package me.beeliebub.tweaks.tests.tools.durability;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import me.beeliebub.tweaks.tools.durability.DurabilityListener;
import me.beeliebub.tweaks.tools.durability.DurabilityService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DurabilityListenerTest {

    private Tweaks plugin;
    private DurabilityService durability;
    private DurabilityListener listener;
    private Player player;
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        durability = new DurabilityService(plugin);
        listener = new DurabilityListener(plugin, durability);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[]{null, null, null, null});
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void depletedShearsAreBlockedButHealthyShearsAreAllowed() {
        ItemStack spent = spent(Material.SHEARS);
        PlayerShearEntityEvent blocked = mock(PlayerShearEntityEvent.class);
        when(blocked.getItem()).thenReturn(spent);

        listener.onShear(blocked);

        verify(blocked).setCancelled(true);

        PlayerShearEntityEvent healthy = mock(PlayerShearEntityEvent.class);
        when(healthy.getItem()).thenReturn(healthy(Material.SHEARS));
        listener.onShear(healthy);

        verify(healthy, never()).setCancelled(true);
    }

    @Test
    void depletedEntityInteractionIsBlockedOnlyForTheMainHand() {
        ItemStack spent = spent(Material.SHEARS);
        when(inventory.getItemInMainHand()).thenReturn(spent);
        PlayerInteractEntityEvent blocked = mock(PlayerInteractEntityEvent.class);
        when(blocked.getHand()).thenReturn(EquipmentSlot.HAND);
        when(blocked.getPlayer()).thenReturn(player);

        listener.onInteractEntity(blocked);

        verify(blocked).setCancelled(true);

        PlayerInteractEntityEvent offhand = mock(PlayerInteractEntityEvent.class);
        when(offhand.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        when(offhand.getPlayer()).thenReturn(player);
        listener.onInteractEntity(offhand);

        verify(offhand, never()).setCancelled(true);
    }

    @Test
    void depletedProjectileItemIsBlocked() {
        PlayerLaunchProjectileEvent event = mock(PlayerLaunchProjectileEvent.class);
        when(event.getItemStack()).thenReturn(spent(Material.TRIDENT));

        listener.onLaunchProjectile(event);

        verify(event).setCancelled(true);
    }

    @Test
    void depletedShieldCannotAbsorbDamage() {
        when(inventory.getItemInMainHand()).thenReturn(spent(Material.SHIELD));
        when(inventory.getItemInOffHand()).thenReturn(null);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)).thenReturn(false);
        when(event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(true);

        listener.onArmorDamage(event);

        verify(event).setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0.0);
    }

    @Test
    void crossingTheThresholdStopsAnActiveElytraGlideImmediately() {
        ItemStack elytra = healthy(Material.ELYTRA);
        setDamage(elytra, durability.maxDamage(elytra) - 2);
        when(player.isGliding()).thenReturn(true);
        PlayerItemDamageEvent event = mock(PlayerItemDamageEvent.class);
        when(event.getItem()).thenReturn(elytra);
        when(event.getPlayer()).thenReturn(player);
        when(event.getDamage()).thenReturn(1);

        listener.onItemDamage(event);

        verify(event).setDamage(1);
        verify(player).setGliding(false);
    }

    @Test
    void armorDamageStopsAnAlreadySpentElytraGlideImmediately() {
        ItemStack elytra = spent(Material.ELYTRA);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[]{null, null, null, elytra});
        when(player.isGliding()).thenReturn(true);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)).thenReturn(false);
        when(event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(false);

        listener.onArmorDamage(event);

        verify(player).setGliding(false);
    }

    private ItemStack spent(Material material) {
        ItemStack item = healthy(material);
        setDamage(item, durability.maxDamage(item) - 1);
        durability.ensureStamped(item);
        return item;
    }

    private ItemStack healthy(Material material) {
        ItemStack item = new ItemStack(material);
        new AugmentLedger(plugin).write(item, 0, List.of(), true);
        durability.ensureStamped(item);
        return item;
    }

    private static void setDamage(ItemStack item, int damage) {
        item.setData(DataComponentTypes.DAMAGE, damage);
    }
}
