package me.beeliebub.tweaks.tests.permissions;

// All /perms GUI screens are Paper Dialogs. The local MockBukkit v26.2 fork provides the Dialog
// service, so authorized construction is exercised by GUI smoke coverage.
//
// Backend logic that the dialog callbacks invoke (PermissionGroup mutations,
// UserPermissions mutations, PermissionManager.calculateEffectivePermissions,
// inheritance walks) is still covered by PermissionGroupTest,
// UserPermissionsTest, PermissionManagerTest, and PermissionsTest.
final class PermissionGUITest {
    private PermissionGUITest() {}
}
