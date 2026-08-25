package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.RegionFlag;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class RegionDialogTest {

    private ServerMock server;
    private Tweaks plugin;
    private ProtectionManager protection;
    private Region region;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Tweaks.class);
        protection = new ProtectionManager(plugin);
        region = new Region("dialog-home", server.addPlayer("RegionOwner").getUniqueId(),
                List.of(), EnumSet.noneOf(RegionFlag.class));
        protection.regions().put(region.id(), region);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void buildsAndShowsTheRealMembershipDialog() {
        assertDoesNotThrow(() -> RegionMembershipGui.openMembersMenu(
                server.addPlayer("MemberDialog"), region, protection, mock(PermissionManager.class)));
    }

    @Test
    void buildsAndShowsTheRealSharedInputDialog() {
        assertDoesNotThrow(() -> RegionGuiSupport.openTextInputDialog(
                server.addPlayer("InputDialog"), Component.text("Title"), Component.text("Prompt"),
                "value", Component.text("Value"), 32, Component.text("Apply"),
                (player, value) -> {}, player -> {}));
    }
}
