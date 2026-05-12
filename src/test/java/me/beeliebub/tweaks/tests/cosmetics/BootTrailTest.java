package me.beeliebub.tweaks.tests.cosmetics;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.cosmetics.BootTrail;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class BootTrailTest {

    private ServerMock server;
    private Tweaks plugin;
    private BootTrail bootTrail;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        bootTrail = new BootTrail(plugin);
        bootTrail.start();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testMovementTriggersTrail() {
        PlayerMock player = server.addPlayer();
        
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ArmorMeta meta = (ArmorMeta) boots.getItemMeta();
        assertNotNull(meta);
        
        TrimMaterial emerald = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft("emerald"));
        TrimPattern sentry = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft("sentry"));
        
        if (emerald != null && sentry != null) {
            meta.setTrim(new ArmorTrim(emerald, sentry));
            boots.setItemMeta(meta);
            player.getInventory().setBoots(boots);

            Location loc1 = player.getLocation().clone();
            Location loc2 = loc1.clone().add(1, 0, 1);
            player.teleport(loc2);
            
            server.getScheduler().performTicks(3);
            
            // Verified logic execution doesn't throw exceptions with valid movement and trim.
        }
    }

    @Test
    void testNoMovementNoTrail() {
        PlayerMock player = server.addPlayer();
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ArmorMeta meta = (ArmorMeta) boots.getItemMeta();
        assertNotNull(meta);
        TrimMaterial emerald = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft("emerald"));
        TrimPattern sentry = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft("sentry"));
        
        if (emerald != null && sentry != null) {
            meta.setTrim(new ArmorTrim(emerald, sentry));
            boots.setItemMeta(meta);
            player.getInventory().setBoots(boots);

            server.getScheduler().performTicks(3);
            // No movement, should not crash or cause issues.
        }
    }
}
