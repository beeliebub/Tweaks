package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Player-facing feedback for the Skyblock commands, listeners, and dialogs. */
public final class SkyblockMessages {

    SkyblockMessages() {
        // Owned by Messages.SKYBLOCK.
    }

    public Component text(String message, NamedTextColor color) {
        return Component.text(message, color);
    }

    public Component text(String message, NamedTextColor color, TextDecoration decoration) {
        return Component.text(message, color).decorate(decoration);
    }

    public Component adminText(String message, NamedTextColor color) {
        return Component.text(message, color);
    }

    public Component noPermission() {
        return Component.text("You do not have permission to use Skyblock.", NamedTextColor.RED);
    }

    public Component adminNoPermission() {
        return Component.text("You do not have permission to administer Skyblock.", NamedTextColor.RED);
    }

    public Component unavailable(String worldKey, String profileCommand) {
        return Component.text("Skyblock is unavailable: world '" + worldKey
                + "' needs its own world profile. Configure it with " + profileCommand + ".",
                NamedTextColor.RED);
    }

    public Component wrongWorld() {
        return Component.text("This command can only be used in the Skyblock world.", NamedTextColor.RED);
    }

    public Component wrongWorld(String worldKey) {
        return Component.text("Skyblock administration requires standing in the configured world '"
                + worldKey + "'.", NamedTextColor.RED);
    }

    public Component useSkyblock() {
        return Component.text("Use /sb to travel to Skyblock first.", NamedTextColor.YELLOW);
    }

    public Component noIsland() {
        return Component.text("You are not a member of a Skyblock island.", NamedTextColor.RED);
    }

    public Component islandRequired() {
        return Component.text("You need a Skyblock island before using this action.", NamedTextColor.RED);
    }

    public Component containment() {
        return Component.text("You cannot leave the area you are visiting.", NamedTextColor.RED);
    }

    public Component atSpawn() {
        return Component.text("You have been returned to Skyblock spawn.", NamedTextColor.YELLOW);
    }

    public Component created(String displayName) {
        return Component.text("Created your Skyblock island", NamedTextColor.GREEN)
                .append(Component.text(" " + displayName + ".", NamedTextColor.YELLOW));
    }

    public Component createBlocked() {
        return Component.text("You already own an island or have an accepted island membership.",
                NamedTextColor.RED);
    }

    public Component islandInfo(String id, String owner, int members) {
        return Component.text("Island " + id + " owned by " + owner + " (" + members + " members).",
                NamedTextColor.GRAY);
    }

    public Component islandNotFound(String value) {
        return Component.text("No Skyblock island matches '" + value + "'.", NamedTextColor.RED);
    }

    public Component inviteSent(String player) {
        return Component.text("Invitation sent to " + player + ".", NamedTextColor.GREEN);
    }

    public Component inviteReceived(String owner) {
        return Component.text(owner + " invited you to their Skyblock island. Use /island accept.",
                NamedTextColor.YELLOW);
    }

    public Component inviteExpired() {
        return Component.text("That Skyblock invitation has expired.", NamedTextColor.RED);
    }

    public Component memberChanged(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    public Component deleteConfirmation() {
        return Component.text("This destroys the island, its wallet, and every member's Skyblock inventory and ender chest. Run /island delete confirm to continue.", NamedTextColor.RED);
    }

    public Component deleteStarted() {
        return Component.text("Island deletion started. Everyone has been moved to spawn and the island is closed while terrain is cleared.", NamedTextColor.YELLOW);
    }

    public Component wipeComplete() {
        return Component.text("Your Skyblock inventory and ender chest were cleared because you lost island access.", NamedTextColor.YELLOW);
    }

    public Component invalidInput(String input) {
        return Component.text("Invalid Skyblock value: " + input, NamedTextColor.RED);
    }

    public Component adminDescription(String description) {
        return Component.text(description, NamedTextColor.GRAY);
    }

    public Component validationProblem(String problem) {
        return Component.text("Problem: " + problem, NamedTextColor.RED);
    }

    public Component guiOnly(String operation, String screen) {
        return Component.text(operation + " is available in the /isadmin gui "
                + screen + " screen.", NamedTextColor.YELLOW);
    }

    public Component invalidConfiguredDefaults(String typeId, String difficultyId) {
        return Component.text("Configured Skyblock defaults are invalid: type '" + typeId
                + "' and difficulty '" + difficultyId + "'. Update skyblock.default-type and "
                + "skyblock.default-difficulty before creating a bare island.", NamedTextColor.RED);
    }

    public Component adminConfirmationRequired(String subject, long references, String backupPath) {
        return Component.text("No changes made. Repeat the same command with trailing 'confirm' within 60 seconds "
                + "to change " + subject + ". References: " + references + ". Backup: " + backupPath,
                NamedTextColor.YELLOW);
    }

    public Component adminConfirmationExpired(String subject) {
        return Component.text("The confirmation for " + subject + " expired or does not match this actor. "
                + "Repeat the original command to request a new confirmation.", NamedTextColor.RED);
    }

    public Component adminHelp(String line) {
        return Component.text(line, NamedTextColor.GRAY);
    }

    public Component adminStatus(String id, String state, String reason) {
        return Component.text(id + ": " + state + " - " + reason, NamedTextColor.GRAY);
    }

    public Component adminStatus(String id, String state, String reason, String target) {
        String suffix = target == null || target.isBlank() ? "" : " [fix: " + target + "]";
        return Component.text(id + ": " + state + " - " + reason + suffix, NamedTextColor.GRAY);
    }

    public Component saved(String subject) {
        return Component.text("Saved Skyblock " + subject + ".", NamedTextColor.GREEN);
    }

    public Component savedWithNote(String subject, String note) {
        return saved(subject).append(Component.text(" " + note, NamedTextColor.YELLOW));
    }

    public Component templatePreview(String id, int width, int height, int depth, int blockEntities) {
        return Component.text("Template " + id + ": " + width + "x" + height + "x" + depth
                + ", " + blockEntities + " block entities.", NamedTextColor.GRAY);
    }

    public Component adminList(String subject, String value) {
        return Component.text("Skyblock " + subject + ": " + value, NamedTextColor.GRAY);
    }

    public Component adminRegistryCounts(int types, int challenges, int generators, int shopEntries) {
        return Component.text("Skyblock registries — types: " + types + ", challenges: " + challenges
                + ", generators: " + generators + ", shop entries: " + shopEntries + ".", NamedTextColor.GRAY);
    }

    public Component notSellable() {
        return Component.text("That item cannot be sold in the Skyblock shop.", NamedTextColor.RED);
    }

    public Component sold(int amount, String material, double value) {
        return Component.text("Sold " + amount + " " + material + " for " + value + " Skybucks.",
                NamedTextColor.GREEN);
    }

    public Component purchased(String material, int amount, double value) {
        return Component.text("Bought " + amount + " " + material + " for " + value + " Skybucks.",
                NamedTextColor.GREEN);
    }

    public Component insufficientFunds() {
        return Component.text("Your island wallet cannot afford that purchase.", NamedTextColor.RED);
    }

    public Component noBalance() {
        return Component.text("Your island has no Skyblock wallet.", NamedTextColor.RED);
    }

    public Component transferComplete(String player, double amount) {
        return Component.text("Transferred " + amount + " Skybucks to " + player + "'s island.",
                NamedTextColor.GREEN);
    }

    public Component sameIsland() {
        return Component.text("You cannot pay another member of the same island.", NamedTextColor.RED);
    }

    public Component challengeReady(String id) {
        return Component.text("Challenge ready to claim: " + id, NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD);
    }

    public Component challengeClaimed(String id) {
        return Component.text("Claimed Skyblock challenge " + id + ".", NamedTextColor.GREEN);
    }

    public Component challengeLocked(String reason) {
        return Component.text("Challenge locked: " + reason, NamedTextColor.RED);
    }
}
