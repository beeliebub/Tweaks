package me.beeliebub.tweaks.tests.skyblock.template;

import me.beeliebub.tweaks.skyblock.template.BukkitTemplateSupport;
import me.beeliebub.tweaks.skyblock.template.TemplateCapture;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BukkitTemplateSupportAirTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void coordinateProbeAgreesWithMaterialAir() {
        World world = server.addSimpleWorld("template-air");
        TemplateCapture.BlockSource source = BukkitTemplateSupport.source(world);
        Material[] materials = {Material.STONE, Material.AIR, Material.CAVE_AIR, Material.VOID_AIR};

        for (int index = 0; index < materials.length; index++) {
            Material material = materials[index];
            world.getBlockAt(index, 64, 0).setType(material);
            assertEquals(material.isAir(), source.isAirAt(index, 64, 0), material.name());
        }
    }
}
