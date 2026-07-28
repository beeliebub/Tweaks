package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.utils.GeometryUtil;
import me.beeliebub.tweaks.utils.InventoryUtil;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for {@link ClaimSubcommand}'s widened refund-on-failure guard — the
 * headline behavior change described in the class Javadoc: ANY non-{@code OK}
 * {@code ProtectionManager.ClaimResult} must refund the just-deducted cost, not just the two named
 * failure values the old switch statement happened to enumerate. Complements {@code ClaimCostTest}
 * (pure pricing/persistence) and {@code UnclaimRefundTest} (the unclaim side of the currency flow).
 */
class ClaimRefundTest {

    private static ServerMock server;
    private static Plugin attachmentOwner;

    @BeforeAll
    static void setUpAll() {
        server = MockBukkit.mock();
        // A lightweight registered plugin, needed only as the "owner" tag for
        // PlayerMock#addAttachment below — deliberately NOT a full MockBukkit.load(Tweaks.class),
        // since granting exactly one permission (PROTECTION_PURCHASEABLE) without also implicitly
        // granting PROTECTION_ADMIN rules out player.setOp(true) (which grants every unregistered
        // permission string, including admin, and would skip the paid path entirely).
        attachmentOwner = MockBukkit.createMockPlugin("tweaks");
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    private RegionCommandContext contextWithMaxChunks(ProtectionManager protection,
                                                       RegionSelectionManager selections,
                                                       int maxChunks) {
        Tweaks plugin = mock(Tweaks.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getInt("max_chunks", 200)).thenReturn(maxChunks);
        when(plugin.getConfig()).thenReturn(config);
        return new RegionCommandContext(plugin, protection, selections);
    }

    @Test
    void execute_overlapsForeignRegion_refundsDeductedCost() {
        PlayerMock claimer = server.addPlayer("Claimer");
        claimer.addAttachment(attachmentOwner, Permissions.PROTECTION_PURCHASEABLE, true);
        World world = claimer.getWorld();

        // A foreign-owned region occupying chunk (0,0) — blocks the claimer's selection below,
        // since the claimer is not its owner.
        UUID foreignOwner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Region foreign = new Region(
                "foreign", foreignOwner, List.of(),
                Map.of(), Map.of(),
                null,
                new Region.RegionBounds(0, 0, 0, 0),
                world.getName(),
                List.of(), Map.of(),
                0, List.of(), List.of()
        );

        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        protection.regions().put("foreign", foreign);

        RegionSelectionManager selections = new RegionSelectionManager(mock(Tweaks.class));
        RegionSelection sel = selections.getOrCreate(claimer, world);
        long chunkKey = GeometryUtil.chunkKey(0, 0);
        sel.setPos1(chunkKey);
        sel.setPos2(chunkKey);

        RegionCommandContext ctx = contextWithMaxChunks(protection, selections, 200);

        // Fund the claimer with exactly the 1-chunk cost (10) before the attempt.
        InventoryUtil.addResourceRupees(claimer, 10);
        assertEquals(10, InventoryUtil.getResourceRupeeBalance(claimer));

        new ClaimSubcommand().execute(ctx, claimer, new String[]{"myclaim"});

        assertEquals(10, InventoryUtil.getResourceRupeeBalance(claimer),
                "a failed claim (foreign overlap) must refund the deducted cost in full");
        assertFalse(protection.regions().containsKey("myclaim"),
                "a rejected claim must not create a region");
    }

    @Test
    void execute_insufficientBalance_deductionNeverHappensNoRefundNeeded() {
        // Not a refund scenario at all — deductResourceRupees fails BEFORE tryClaim runs, so
        // there's nothing to refund. Pins that ordering: cost is never taken from an unfunded
        // player in the first place.
        PlayerMock claimer = server.addPlayer("PoorClaimer");
        claimer.addAttachment(attachmentOwner, Permissions.PROTECTION_PURCHASEABLE, true);
        World world = claimer.getWorld();

        ProtectionManager protection = new ProtectionManager(mock(Tweaks.class));
        RegionSelectionManager selections = new RegionSelectionManager(mock(Tweaks.class));
        RegionSelection sel = selections.getOrCreate(claimer, world);
        long chunkKey = GeometryUtil.chunkKey(5, 5);
        sel.setPos1(chunkKey);
        sel.setPos2(chunkKey);

        RegionCommandContext ctx = contextWithMaxChunks(protection, selections, 200);

        assertEquals(0, InventoryUtil.getResourceRupeeBalance(claimer), "claimer starts broke");

        new ClaimSubcommand().execute(ctx, claimer, new String[]{"cantafford"});

        assertEquals(0, InventoryUtil.getResourceRupeeBalance(claimer),
                "an unaffordable claim must not deduct any Resource Rupees");
        assertFalse(protection.regions().containsKey("cantafford"),
                "an unaffordable claim must not create a region");
    }
}
