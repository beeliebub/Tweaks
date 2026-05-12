package me.beeliebub.tweaks.tests.permissions;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.beeliebub.tweaks.permissions.*;
import me.beeliebub.tweaks.tests.MessageAssert;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.InventoryViewMock;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionListenerTest {

    private ServerMock server;
    private PermissionManager manager;
    private PermissionListener listener;
    private PlayerMock player;
    private org.bukkit.plugin.java.JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        manager = mock(PermissionManager.class);
        when(manager.getPlugin()).thenReturn(plugin);
        listener = new PermissionListener(manager);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onChatCreatesGroup() {
        when(manager.getPrompt(player.getUniqueId())).thenReturn(PermissionManager.PromptType.CREATE_GROUP);
        when(manager.getGroups()).thenReturn(new java.util.HashMap<>());

        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.message()).thenReturn(Component.text("NewGroup"));
        
        listener.onChat(event);

        verify(event).setCancelled(true);
        verify(manager).saveGroups();
        MessageAssert.assertMessageSent(player, "Group 'NewGroup' created.");
    }

    @Test
    void onChatSearchUser() {
        PlayerMock target = server.addPlayer("TargetPlayer");
        when(manager.getPrompt(player.getUniqueId())).thenReturn(PermissionManager.PromptType.SEARCH_USER);

        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.message()).thenReturn(Component.text("TargetPlayer"));
        
        listener.onChat(event);

        verify(event).setCancelled(true);
        verify(manager).setPrompt(player.getUniqueId(), null);
    }

    @Test
    void onInventoryClickMainGroups() {
        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.MAIN, null, null, 0);
        Inventory inv = server.createInventory(holder, 54);
        holder.attach(inv);
        holder.mapAction(20, "open_groups");

        org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
        when(view.getTopInventory()).thenReturn(inv);
        when(view.getPlayer()).thenReturn(player);
        when(view.getInventory(20)).thenReturn(inv);

        InventoryClickEvent event = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER, 20, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );

        listener.onInventoryClick(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void onInventoryClickGroupsListCreate() {
        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.GROUPS_LIST, null, null, 0);
        Inventory inv = server.createInventory(holder, 54);
        holder.attach(inv);
        holder.mapAction(49, "create_group");

        org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
        when(view.getTopInventory()).thenReturn(inv);
        when(view.getPlayer()).thenReturn(player);
        when(view.getInventory(49)).thenReturn(inv);

        InventoryClickEvent event = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER, 49, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );

        listener.onInventoryClick(event);

        assertTrue(event.isCancelled());
        verify(manager).setPrompt(player.getUniqueId(), PermissionManager.PromptType.CREATE_GROUP);
        MessageAssert.assertMessageSent(player, "Type the new group name in chat.");
    }

    @Test
    void onInventoryClickGroupHubDeleteDefaultFails() {
        PermissionHolder holder = new PermissionHolder(PermissionHolder.MenuKind.GROUP_HUB, "default", null, 0);
        Inventory inv = server.createInventory(holder, 54);
        holder.attach(inv);
        holder.mapAction(31, "delete_group");

        org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
        when(view.getTopInventory()).thenReturn(inv);
        when(view.getPlayer()).thenReturn(player);
        when(view.getInventory(31)).thenReturn(inv);

        InventoryClickEvent event = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER, 31, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );

        listener.onInventoryClick(event);

        assertTrue(event.isCancelled());
        MessageAssert.assertMessageSent(player, "Cannot delete default group.");
    }
}
