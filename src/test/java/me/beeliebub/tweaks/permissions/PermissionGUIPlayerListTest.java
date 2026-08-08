package me.beeliebub.tweaks.permissions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PermissionGUIPlayerListTest {

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
    void playersMenuSourceContainsOnlyOnlinePlayers() {
        PlayerMock online = server.addPlayer("Online");
        UUID offlineUser = UUID.randomUUID();

        List<UUID> playerIds = PermissionGUI.onlinePlayerIds();

        assertEquals(List.of(online.getUniqueId()), playerIds);
        assertFalse(playerIds.contains(offlineUser));
    }
}
