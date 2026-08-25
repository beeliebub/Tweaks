package me.beeliebub.tweaks.tests.tools.augments;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.tools.augments.AugmentDialog;
import me.beeliebub.tweaks.tools.augments.AugmentService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AugmentDialogTest {

    private ServerMock server;
    private Tweaks plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void localMockBukkitCanBuildAndShowTheRealAugmentDialog() {
        PlayerMock player = server.addPlayer("DialogTester");
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        AugmentService augments = new AugmentService(plugin, new QualityRegistry(plugin));
        AugmentDialog dialog = new AugmentDialog(augments);

        assertDoesNotThrow(() -> dialog.openHub(player, item));
    }
}
