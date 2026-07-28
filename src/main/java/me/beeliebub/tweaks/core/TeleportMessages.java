package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Player-feedback factories for {@code me.beeliebub.tweaks.teleport}.
 * Callers access this registry through {@link Messages#TELEPORT}.
 */
public final class TeleportMessages {

    TeleportMessages() {
    }

    // ---------------------------------------------------------------- Homes

    /** Explains that only a player can teleport to a home. */
    public Component homeTeleportRequiresPlayer() { return red("Only players can teleport."); }

    /** Shows the syntax for teleporting to a home. */
    public Component homeUsage() { return yellow("Usage: /home [name] | /home <player> <name>"); }

    /** Explains that a requested home is absent. */
    public Component homeNotFound() { return red("Home not found!"); }

    /** Announces that home teleportation has started. */
    public Component homeTeleporting(String homeName) { return green("Teleporting to " + homeName + "..."); }

    /** Explains that the destination of a home teleport was unsafe. */
    public Component homeTeleportFailed() { return red("Teleportation failed. Is the destination safe?"); }

    /** Explains that a home's world is not loaded. */
    public Component homeWorldNotLoaded() { return Component.text("The world this home is in is not loaded!", NamedTextColor.DARK_RED); }

    /** Explains that only a player can set a home. */
    public Component setHomeRequiresPlayer() { return red("Only players can set homes."); }

    /** Explains that homes cannot be created in the current world. */
    public Component setHomeWorldDenied() { return red("You cannot set a home in this world."); }

    /** Shows the syntax for creating a home. */
    public Component setHomeUsage() { return yellow("Usage: /sethome <name> OR /sethome <player> <name>"); }

    /** Explains that a player reached the home limit. */
    public Component setHomeMaximumReached(int maxHomes) { return red("You have reached the maximum of " + maxHomes + " homes!"); }

    /** Confirms that a home was saved. */
    public Component setHomeSuccess(String homeName) { return green("Home '" + homeName + "' set successfully!"); }

    /** Explains that only a player can delete a home. */
    public Component delHomeRequiresPlayer() { return red("Only players can delete homes."); }

    /** Shows the syntax for deleting a home. */
    public Component delHomeUsage() { return yellow("Usage: /delhome <name> OR /delhome <player> <name>"); }

    /** Explains that a home to delete is absent. */
    public Component delHomeNotFound(String homeName) { return red("Home '" + homeName + "' does not exist!"); }

    /** Confirms that a home was deleted. */
    public Component delHomeSuccess(String homeName) { return green("Home '" + homeName + "' deleted successfully!"); }

    /** Explains that the console must name a player to list homes. */
    public Component homesConsoleRequiresPlayer() { return red("Console must specify a player: /homes <player>"); }

    /** Shows the syntax for listing homes. */
    public Component homesUsage() { return yellow("Usage: /homes OR /homes <player>"); }

    /** Explains that a player has no saved homes. */
    public Component homesNone(String targetName) { return yellow(targetName + " has no homes set."); }

    /** Lists a player's saved homes. */
    public Component homesList(String targetName, String joinedHomes) { return aqua("Homes for " + targetName + ": " + joinedHomes); }

    // ---------------------------------------------------------------- Warps and spawn

    /** Explains that only a player can use warps. */
    public Component warpRequiresPlayer() { return red("Only players can use warps."); }

    /** Shows the syntax for teleporting to a warp. */
    public Component warpUsage() { return yellow("Usage: /warp <name>"); }

    /** Explains that a requested warp is absent. */
    public Component warpNotFound(String warpName) { return red("Warp '" + warpName + "' does not exist!"); }

    /** Announces that warp teleportation has started. */
    public Component warpingTo(String warpName) { return green("Warping to '" + warpName + "'..."); }

    /** Explains that a warp's world is not loaded. */
    public Component warpWorldNotLoaded() { return red("The world for this warp is not loaded."); }

    /** Explains that only a player can set a warp. */
    public Component setWarpRequiresPlayer() { return red("Only players can set warps."); }

    /** Shows the syntax for setting a warp. */
    public Component setWarpUsage() { return yellow("Usage: /setwarp <name>"); }

    /** Confirms that a warp was saved. */
    public Component setWarpSuccess(String warpName) { return green("Warp '" + warpName + "' set successfully!"); }

    /** Shows the syntax for deleting a warp. */
    public Component delWarpUsage() { return yellow("Usage: /delwarp <name>"); }

    /** Confirms that a warp was deleted. */
    public Component delWarpSuccess(String warpName) { return green("Warp '" + warpName + "' deleted successfully!"); }

    /** Explains that no public warps exist. */
    public Component warpsNone() { return yellow("There are no warps available."); }

    /** Lists available warps for a non-player sender. */
    public Component warpsList(String joinedWarps) { return aqua("Available Warps: " + joinedWarps); }

    /** Renders the title of the clickable warps dialog. */
    public Component warpsDialogTitle() {
        return Component.text("Warps", NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Renders one clickable warp label in the warps dialog. */
    public Component warpsDialogButton(String warpName) {
        return Component.text(warpName, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    /** Renders one warp dialog-button tooltip. */
    public Component warpsDialogTooltip(String warpName) { return gray("Teleport to " + warpName); }

    /** Explains that only a player can teleport to spawn. */
    public Component spawnRequiresPlayer() { return red("Only players can teleport to spawn."); }

    /** Explains that spawn is not configured. */
    public Component spawnNotSet() { return red("Spawn has not been set!"); }

    /** Announces that spawn teleportation has started. */
    public Component spawnTeleporting() { return green("Teleporting to spawn..."); }

    /** Explains that spawn's world is not loaded. */
    public Component spawnWorldNotLoaded() { return red("The world for spawn is not loaded."); }

    // ---------------------------------------------------------------- Back

    /** Explains that only a player can return to a prior location. */
    public Component backRequiresPlayer() { return red("Only players can use /back."); }

    /** Explains that no return location is stored. */
    public Component backNoPreviousLocation() { return red("No previous location found."); }

    /** Explains that stored return-location data could not be read. */
    public Component backLocationCorrupt() { return red("Stored location data is corrupt."); }

    /** Explains that the world of the stored return location is unavailable. */
    public Component backWorldNotLoaded() { return red("The world for your previous location is not loaded."); }

    /** Lists items that prevent returning into a resource world. */
    public Component backDisallowedItems(String itemNames) {
        return Component.text("You cannot return to the resource world with these items: ", NamedTextColor.RED)
                .append(Component.text(itemNames, NamedTextColor.YELLOW));
    }

    /** Confirms that a player returned to their prior location. */
    public Component backSuccess() { return green("Teleported to your previous location!"); }

    /** Explains that a general teleport attempt failed. */
    public Component teleportFailed() { return red("Teleportation failed."); }

    // ---------------------------------------------------------------- TPA

    /** Explains that only a player can use TPA commands. */
    public Component tpaRequiresPlayer() { return red("Only players can use TPA commands."); }

    /** Shows the syntax for a TPA or TPA-here request. */
    public Component tpaUsage(boolean here) {
        return yellow("Usage: " + (here ? "/tpahere" : "/tpa") + " <player>");
    }

    /** Explains that a TPA request cannot target its sender. */
    public Component tpaCannotTargetSelf() { return red("You can't send a TPA request to yourself."); }

    /** Informs a requester that their TPA request expired. */
    public Component tpaRequestExpiredTo(String targetName) { return gray("TPA request to " + targetName + " has expired."); }

    /** Informs a recipient that an incoming TPA request expired. */
    public Component tpaRequestExpiredFrom(String requesterName) { return gray("TPA request from " + requesterName + " has expired."); }

    /** Confirms that a TPA request was sent. */
    public Component tpaRequestSent(String targetName, int timeoutSeconds) {
        return green("TPA request sent to " + targetName + "! They have " + timeoutSeconds + " seconds to respond.");
    }

    /** Describes the requested teleport direction to its recipient. */
    public Component tpaRequestDescription(String requesterName, boolean here) {
        return aqua(here ? requesterName + " wants you to teleport to them."
                : requesterName + " wants to teleport to you.");
    }

    /** Renders the clickable accept/deny controls for an incoming TPA request. */
    public Component tpaRequestControls() {
        Component accept = Component.text("[Accept]", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpaccept"));
        Component deny = Component.text("[Deny]", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpdeny"));
        return accept.append(Component.text("  ")).append(deny);
    }

    /** Explains that a player has no pending TPA request. */
    public Component tpaNoPendingRequest() { return red("You have no pending TPA requests."); }

    /** Explains that the player who requested teleportation went offline. */
    public Component tpaRequesterOffline() { return red("The requesting player is no longer online."); }

    /** Lists items that prevent a TPA recipient from entering the resource world. */
    public Component tpaDisallowedItems(String itemNames) {
        return Component.text("You cannot teleport into the resource world with these items: ", NamedTextColor.RED)
                .append(Component.text(itemNames, NamedTextColor.YELLOW));
    }

    /** Explains to a TPA destination that the teleporting player carried disallowed items. */
    public Component tpaTeleportingPlayerDisallowed(String teleportingName) {
        return red(teleportingName + " has disallowed items and cannot teleport to you.");
    }

    /** Confirms that the teleporting player reached their TPA destination. */
    public Component tpaTeleportSuccess(String destinationName) { return green("Teleported to " + destinationName + "!"); }

    /** Confirms to a destination that a player teleported to them. */
    public Component tpaDestinationNotice(String teleportingName) { return green(teleportingName + " teleported to you."); }

    /** Confirms that the recipient denied a TPA request. */
    public Component tpaDenied() { return yellow("TPA request denied."); }

    /** Informs a requester that their TPA request was denied. */
    public Component tpaDeniedBy(String denierName) { return red(denierName + " denied your TPA request."); }

    private static Component red(String message) { return Component.text(message, NamedTextColor.RED); }
    private static Component green(String message) { return Component.text(message, NamedTextColor.GREEN); }
    private static Component yellow(String message) { return Component.text(message, NamedTextColor.YELLOW); }
    private static Component aqua(String message) { return Component.text(message, NamedTextColor.AQUA); }
    private static Component gray(String message) { return Component.text(message, NamedTextColor.GRAY); }
}
