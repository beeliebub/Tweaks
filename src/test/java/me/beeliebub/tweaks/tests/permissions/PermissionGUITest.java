package me.beeliebub.tweaks.tests.permissions;

// All /perms GUI screens are now Paper Dialogs (Tweaks-7fov). MockBukkit does
// not provide a DialogInstancesProvider service, so ActionButton.builder(...)
// and related Dialog construction throw NoSuchElementException under the
// test runtime. Coverage of the dialog open path lives in real-server smoke
// tests rather than unit tests.
//
// Backend logic that the dialog callbacks invoke (PermissionGroup mutations,
// UserPermissions mutations, PermissionManager.calculateEffectivePermissions,
// inheritance walks) is still covered by PermissionGroupTest,
// UserPermissionsTest, PermissionManagerTest, and PermissionsTest.
final class PermissionGUITest {
    private PermissionGUITest() {}
}
