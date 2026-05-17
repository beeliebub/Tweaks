package me.beeliebub.tweaks.permissions;

import org.bukkit.event.Listener;

// Placeholder Bukkit Listener for the /perms GUI. After the migration to Paper
// Dialogs (Tweaks-7fov), all routing happens via DialogAction.customClick
// callbacks inside PermissionGUI; no inventory clicks or chat prompts need to
// be intercepted. The class and constructor are retained so the existing
// registration in Tweaks#onEnable continues to compile.
public class PermissionListener implements Listener {

    public PermissionListener(PermissionManager manager) {
        // No-op: all dispatch is now handled by dialog callbacks.
    }
}
