package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.PermissionListener;
import me.beeliebub.tweaks.permissions.PermissionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

// PermissionListener became a no-op after the /perms GUI was migrated to
// Paper Dialogs. Routing now happens via DialogAction.customClick callbacks
// in PermissionGUI; the create-group / search-player chat prompts are
// confirmation dialogs with DialogInput.text fields. This test exists only
// to verify the placeholder listener can still be constructed and registered.
class PermissionListenerTest {

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
    void constructsWithoutThrowing() {
        PermissionManager manager = mock(PermissionManager.class);
        PermissionListener listener = new PermissionListener(manager);
        assertNotNull(listener);
    }
}
