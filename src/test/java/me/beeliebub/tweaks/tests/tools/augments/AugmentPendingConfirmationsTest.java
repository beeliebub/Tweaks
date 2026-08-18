package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.tools.augments.AugmentConfirmationListener;
import me.beeliebub.tweaks.tools.augments.AugmentPendingConfirmations;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AugmentPendingConfirmationsTest {

    private ServerMock server;
    private Tweaks plugin;
    private PlayerMock player;
    private AugmentPendingConfirmations pending;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        player = server.addPlayer("PendingTester");
        while (player.nextComponentMessage() != null) {}
        pending = new AugmentPendingConfirmations(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void secondCreateReplacesFirstAndCancelsItsTask() {
        ItemStack first = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack second = new ItemStack(Material.DIAMOND_AXE);
        AugmentPendingConfirmations.PendingRequest original = pending.create(player, first, 30);
        AugmentPendingConfirmations.PendingRequest replacement = pending.create(player, second, 40);

        assertSame(replacement, pending.validFor(player, second));
        assertNull(pending.validFor(player, first));
        assertTrue(pending.contains(player.getUniqueId()));
        assertTrue(original != replacement);
    }

    @Test
    void validForRejectsMismatchedSnapshot() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        pending.create(player, item, 30);
        ItemMeta changed = item.getItemMeta();
        changed.displayName(Component.text("Changed"));
        item.setItemMeta(changed);

        assertNull(pending.validFor(player, item));
    }

    @Test
    void validForToleratesDurabilityLoss() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        pending.create(player, item, 30);
        Damageable damage = (Damageable) item.getItemMeta();
        damage.setDamage(4);
        item.setItemMeta(damage);

        assertNotNull(pending.validFor(player, item));
    }

    @Test
    void validForRejectsAfterExpiry() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        pending.create(player, item, 30);
        for (int tick = 0; tick < AugmentPendingConfirmations.EXPIRY_TICKS; tick++) {
            server.getScheduler().performOneTick();
        }

        assertNull(pending.validFor(player, item));
        assertTrue(pending.size() == 0);
    }

    @Test
    void cancelRemovesEntryAndStopsExpiryMessage() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        pending.create(player, item, 30);
        assertTrue(pending.cancel(player.getUniqueId()));
        for (int tick = 0; tick < AugmentPendingConfirmations.EXPIRY_TICKS; tick++) {
            server.getScheduler().performOneTick();
        }

        assertTrue(pending.size() == 0);
        assertNull(player.nextComponentMessage());
    }

    @Test
    void quittingClearsThePendingConfirmation() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        pending.create(player, item, 30);

        new AugmentConfirmationListener(pending).onQuit(new PlayerQuitEvent(player, "quit"));

        assertTrue(pending.size() == 0);
        assertNull(pending.validFor(player, item));
    }
}
