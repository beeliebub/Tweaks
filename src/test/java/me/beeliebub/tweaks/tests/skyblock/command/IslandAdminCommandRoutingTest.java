package me.beeliebub.tweaks.tests.skyblock.command;

import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.skyblock.command.IslandAdminCommand;
import me.beeliebub.tweaks.skyblock.island.SkyblockSpawn;
import me.beeliebub.tweaks.tests.skyblock.ui.DialogTestHelper;
import me.beeliebub.tweaks.tests.skyblock.ui.SkyblockDialogFixture;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IslandAdminCommandRoutingTest {
    @Test
    void barePlayerCommandOpensHubWithoutConsumingRegionSelection() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            IslandAdminCommand command = new IslandAdminCommand(fixture.plugin, fixture.runtime);

            assertTrue(command.onCommand(fixture.admin, mock(Command.class), "isadmin", new String[0]));
            assertNotNull(DialogTestHelper.openDialog(fixture.admin));
            verify(fixture.selectionManager, never()).get(fixture.admin.getUniqueId());
        }
    }

    @Test
    void bareConsoleCommandUsesTheExistingHelpCatalogue() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            IslandAdminCommand command = new IslandAdminCommand(fixture.plugin, fixture.runtime);
            ConsoleCommandSender console = mock(ConsoleCommandSender.class);

            assertTrue(command.onCommand(console, mock(Command.class), "isadmin", new String[0]));
            verify(console, atLeastOnce()).sendMessage(any(Component.class));
        }
    }

    @Test
    void explicitSpawnStillRecordsThePlayerAsOwner() {
        try (SkyblockDialogFixture fixture = new SkyblockDialogFixture()) {
            var bridge = fixture.runtime.regionBridge();
            when(bridge.spawnRegion(fixture.skyblockWorld)).thenReturn(null);
            when(bridge.claimSpawn(any(), any(), any(), nullable(me.beeliebub.tweaks.protection.region.Region.class), any()))
                    .thenReturn(ProtectionManager.ClaimResult.OK);
            when(fixture.spawn.record(any(SkyblockSpawn.SpawnData.class)))
                    .thenReturn(CompletableFuture.completedFuture(null));
            IslandAdminCommand command = new IslandAdminCommand(fixture.plugin, fixture.runtime);

            assertTrue(command.onCommand(fixture.admin, mock(Command.class), "isadmin", new String[]{"spawn"}));
            verify(fixture.spawn).record(org.mockito.ArgumentMatchers.argThat(data ->
                    fixture.admin.getUniqueId().equals(data.owner())));
        }
    }
}
