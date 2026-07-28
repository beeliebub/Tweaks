package me.beeliebub.tweaks.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Player-feedback factories for {@code me.beeliebub.tweaks.minigames}.
 * Callers access this registry through {@link Messages#MINIGAMES}.
 */
public final class MinigameMessages {

    MinigameMessages() {
    }

    // ---------------------------------------------------------------- Rewards

    /** Shows the syntax for creating a named reward. */
    public Component rewardCreateUsage() { return red("Usage: /reward create <name>"); }

    /** Explains that a reward name is already defined. */
    public Component rewardAlreadyExists(String name) { return red("A reward named '" + name + "' already exists."); }

    /** Confirms that a reward was created. */
    public Component rewardCreated(String name) {
        return green("Reward '" + name + "' created! Use /reward edit " + name + " to set its items.");
    }

    /** Explains that reward-item editing requires a player sender. */
    public Component rewardEditRequiresPlayer() { return red("Only players can edit reward items (opens a chest GUI)."); }

    /** Shows the syntax for opening a reward-item editor. */
    public Component rewardEditUsage() { return red("Usage: /reward edit <name>"); }

    /** Explains that a named reward does not exist. */
    public Component rewardDoesNotExist(String name) { return red("No reward named '" + name + "' exists."); }

    /** Renders the inventory title used to edit a reward. */
    public Component rewardEditorTitle(String name) { return Component.text("Reward: " + name, NamedTextColor.GOLD); }

    /** Shows the syntax for granting a queued reward. */
    public Component rewardGiveUsage() { return red("Usage: /reward give <player> <reward> [count]"); }

    /** Explains that a reward count must be an integer. */
    public Component rewardCountMustBeInteger() { return red("Count must be an integer."); }

    /** Explains that a reward count must be positive. */
    public Component rewardCountMustBePositive() { return red("Count must be at least 1."); }

    /** Explains that a requested offline reward recipient has never joined. */
    public Component rewardRecipientHasNeverPlayed(String name) { return red("Player '" + name + "' has never played before."); }

    /** Confirms that rewards were queued for a player. */
    public Component rewardGranted(int count, String rewardName, String targetName) {
        return green("Granted " + count + "x '" + rewardName + "' to " + targetName + ".");
    }

    /** Informs a player that rewards were queued for them. */
    public Component rewardReceived(int count, String rewardName) {
        return Component.text()
                .append(Component.text("You received " + count + "x ", NamedTextColor.YELLOW))
                .append(Component.text("'" + rewardName + "'", NamedTextColor.GOLD))
                .append(Component.text(". Use ", NamedTextColor.YELLOW))
                .append(Component.text("/reward claim", NamedTextColor.GOLD))
                .append(Component.text(" to collect.", NamedTextColor.YELLOW))
                .build();
    }

    /** Explains that claiming queued rewards requires a player sender. */
    public Component rewardClaimRequiresPlayer() { return red("Only players can claim rewards."); }

    /** Explains that a player has no queued rewards. */
    public Component rewardNoneToClaim() { return yellow("You have no rewards to claim."); }

    /** Confirms that queued rewards were delivered to a player. */
    public Component rewardClaimedWithOverflowWarning() {
        return green("Rewards claimed! Items that didn't fit were dropped at your feet.");
    }

    /** Explains that claimed reward definitions contained no items. */
    public Component rewardClaimedNoItems() { return yellow("Rewards claimed, but they contained no items."); }

    /** Renders the full reward command help listing for the sender's permission level. */
    public List<Component> rewardUsage(String label, boolean includeAdminCommands) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("=== Rewards ===", NamedTextColor.GOLD));
        if (includeAdminCommands) {
            lines.add(usageRow(label, "create <name>", "Create a new reward"));
            lines.add(usageRow(label, "edit <name>", "Edit reward items"));
            lines.add(usageRow(label, "give <player> <reward> [count]", "Queue a reward for a player"));
        }
        lines.add(usageRow(label, "claim", "Claim pending rewards"));
        return List.copyOf(lines);
    }

    /** Creates the intentional blank line before a reward-claim reminder. */
    public Component rewardClaimReminderSpacer() { return Component.empty(); }

    /** Reminds a player of pending rewards at login. */
    public Component rewardClaimReminder(int pendingCount) {
        return Component.text("You have " + pendingCount + " unclaimed reward(s)! Type ", NamedTextColor.GOLD)
                .append(Component.text("/reward claim", NamedTextColor.YELLOW))
                .append(Component.text(" to collect them.", NamedTextColor.GOLD));
    }

    /** Confirms that a reward editor's contents were saved. */
    public Component rewardUpdated(String name) { return green("Reward '" + name + "' updated!"); }

    // ---------------------------------------------------------------- Whack an Andrew

    /** Announces the start of a Whack an Andrew game. */
    public Component whackGameStarted() { return Component.text("Whack an Andrew has started! Hit the mannequins to score!", NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD); }

    /** Announces that a Whack an Andrew game was paused. */
    public Component whackGamePaused() { return yellow("Game paused."); }

    /** Shows the scoring result of one Whack an Andrew hit. */
    public Component whackHitScore(int points, int score) {
        return Component.text((points > 0 ? "+" + points + "!" : points + "!") + " ",
                        points > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)
                .append(Component.text("Score: " + score, NamedTextColor.GOLD));
    }

    /** Shows the current Whack an Andrew score and remaining time. */
    public Component whackScoreAndTime(int score, String time) {
        return Component.text("Score: " + score + " ", NamedTextColor.GOLD)
                .append(Component.text("| Time: " + time, NamedTextColor.AQUA));
    }

    /** Heads the Whack an Andrew results listing. */
    public Component whackResultsHeader() { return Component.text("=== Whack an Andrew - Results ===", NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD); }

    /** Explains that no Whack an Andrew scores were recorded. */
    public Component whackNoScores() { return gray("No scores recorded."); }

    /** Renders one ranked Whack an Andrew score row. */
    public Component whackResultEntry(int rank, String name, int points, NamedTextColor color) {
        return Component.text("#" + rank + " " + name + " - " + points + " points", color);
    }

    /** Informs a Whack an Andrew player that they earned a queued reward. */
    public Component whackRewardEarned() { return green("You earned a reward! Use /reward claim to collect it."); }

    /** Explains that Whack an Andrew administration requires a player sender. */
    public Component whackRequiresPlayer() { return red("Only players can use this command."); }
    /** Announces the beginning of Whack an Andrew arena setup. */
    public Component whackArenaSetupStarted() { return green("Arena setup started."); }
    /** Instructs an administrator to set one Whack an Andrew arena corner. */
    public Component whackCornerInstruction(boolean first, String label) {
        String prefix = first ? "Look at the bottom corner and run: " : "Then look at the opposite top corner and run: ";
        return Component.text(prefix, NamedTextColor.YELLOW)
                .append(Component.text("/" + label + " corner" + (first ? "1" : "2"), NamedTextColor.GOLD));
    }
    /** Explains that a block target is needed to define a Whack an Andrew corner. */
    public Component whackCornerTargetRequired(int corner) { return red("Look at a block to set as corner " + corner + "."); }
    /** Confirms the first Whack an Andrew arena corner. */
    public Component whackCornerOneSet(String location) { return green("Corner 1 set at " + location + "."); }
    /** Instructs an administrator to choose the opposite Whack an Andrew corner. */
    public Component whackCornerTwoInstruction() { return yellow("Now look at the opposite corner and run /whack corner2."); }
    /** Explains that the first Whack an Andrew corner must be set first. */
    public Component whackCornerOneRequired() { return red("You must set corner 1 first! Run /whack arena."); }
    /** Explains that Whack an Andrew arena corners must be in one world. */
    public Component whackCornersSameWorldRequired() { return red("Both corners must be in the same world."); }
    /** Confirms creation of a Whack an Andrew arena. */
    public Component whackArenaCreated(String firstLocation, String secondLocation) { return green("Arena created from " + firstLocation + " to " + secondLocation + "!"); }
    /** Instructs an administrator to select Whack an Andrew spawn blocks. */
    public Component whackSetBlocksInstruction() { return yellow("Now set spawn blocks with /whack setblocks <material>"); }
    /** Explains that a Whack an Andrew arena must exist first. */
    public Component whackArenaRequired() { return red("Create an arena first with /whack arena."); }
    /** Shows the syntax for selecting Whack an Andrew spawn blocks. */
    public Component whackSetBlocksUsage() { return red("Usage: /whack setblocks <material> [material...]"); }
    /** Shows an example Whack an Andrew spawn-block command. */
    public Component whackSetBlocksExample() { return gray("Example: /whack setblocks hay_block gold_block"); }
    /** Explains that a Whack an Andrew spawn-block material is invalid. */
    public Component whackUnknownBlock(String material) { return red("Unknown block material: " + material); }
    /** Reports Whack an Andrew spawn locations discovered in the arena. */
    public Component whackSpawnLocationsFound(int count) { return green("Found " + count + " spawn locations in the arena."); }
    /** Explains that no configured Whack an Andrew spawn blocks were found. */
    public Component whackNoSpawnBlocksFound() { return yellow("No matching blocks were found in the arena bounds."); }
    /** Explains that spawn blocks must be selected before starting the game. */
    public Component whackSpawnBlocksRequired() { return red("Set spawn blocks first with /whack setblocks <material>."); }
    /** Explains that no Whack an Andrew game is currently running. */
    public Component whackNoGameRunning() { return red("No game is currently running."); }
    /** Explains that no Whack an Andrew game is currently active. */
    public Component whackNoGameActive() { return red("No game is currently active."); }
    /** Shows the syntax for assigning a Whack an Andrew place reward. */
    public Component whackSetRewardUsage() { return red("Usage: /whack setreward <1|2|3> <reward_name>"); }
    /** Explains that a Whack an Andrew reward place must be from one through three. */
    public Component whackPlaceInvalid() { return red("Place must be 1, 2, or 3."); }
    /** Confirms a Whack an Andrew place reward assignment. */
    public Component whackPlaceRewardSet(String ordinal, String rewardName) { return green(ordinal + " place reward set to '" + rewardName + "'."); }
    /** Confirms that Whack an Andrew configuration was reloaded. */
    public Component whackConfigReloaded() { return green("whack.yml reloaded."); }
    /** Renders the full Whack an Andrew administration command help listing. */
    public List<Component> whackUsage(String label) {
        return List.of(
                Component.text("=== Whack an Andrew ===", NamedTextColor.GOLD),
                usageRow(label, "arena", "Begin arena setup"),
                usageRow(label, "corner1", "Set first corner (look at block)"),
                usageRow(label, "corner2", "Set second corner (look at block)"),
                usageRow(label, "setblocks <material...>", "Scan arena for spawn blocks"),
                usageRow(label, "start", "Start the game"),
                usageRow(label, "pause", "Pause the game"),
                usageRow(label, "stop", "Stop the game"),
                usageRow(label, "setreward <1|2|3> <name>", "Set place reward"),
                usageRow(label, "reload", "Reload whack.yml config"));
    }

    // ---------------------------------------------------------------- Resource Hunt

    /** Broadcasts completion of every Resource Hunt tier. */
    public Component resourceHuntAllTiersComplete(String playerName, int tierCount) {
        return Component.text()
                .append(Component.text("[Resource Hunt] ", NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text(playerName, NamedTextColor.AQUA))
                .append(Component.text(" cleared all ", NamedTextColor.YELLOW))
                .append(Component.text(tierCount + " tiers", NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text(" of the resource hunt!", NamedTextColor.YELLOW))
                .build();
    }

    /** Broadcasts completion of one non-final Resource Hunt tier. */
    public Component resourceHuntTierComplete(String playerName, int tier, String verb, int completedThreshold,
                                              String targetName, int nextThreshold) {
        return Component.text()
                .append(Component.text("[Resource Hunt] ", NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text(playerName, NamedTextColor.AQUA))
                .append(Component.text(" reached Tier " + tier + " (", NamedTextColor.YELLOW))
                .append(Component.text(verb + " " + completedThreshold + "x " + targetName, NamedTextColor.WHITE))
                .append(Component.text("). Next tier: ", NamedTextColor.YELLOW))
                .append(Component.text(nextThreshold + "x", NamedTextColor.GOLD))
                .append(Component.text(".", NamedTextColor.YELLOW))
                .build();
    }

    /** Informs a player that they completed a Resource Hunt tier. */
    public Component resourceHuntPersonalCompletion(boolean finalTier, int tier) {
        return Component.text()
                .append(Component.text(finalTier ? "All tiers complete! " : "Tier " + tier + " complete. ", NamedTextColor.GOLD))
                .append(Component.text("Use ", NamedTextColor.YELLOW))
                .append(Component.text("/reward claim", NamedTextColor.GOLD))
                .append(Component.text(" to collect.", NamedTextColor.YELLOW))
                .build();
    }

    /** Informs a player that all Resource Hunt tiers are already complete. */
    public Component resourceHuntAlreadyComplete() {
        return Component.text()
                .append(Component.text("Resource Hunt: ", NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text("you've already completed all tiers this session.", NamedTextColor.YELLOW))
                .build();
    }

    /** Describes a player's current Resource Hunt task at login. */
    public Component resourceHuntTask(String verb, String targetName, int firstThreshold, int secondThreshold,
                                      int thirdThreshold) {
        return Component.text()
                .append(Component.text("Resource Hunt: ", NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text(verb + " ", NamedTextColor.YELLOW))
                .append(Component.text(targetName, NamedTextColor.WHITE))
                .append(Component.text(" in the resource world to clear tiers ", NamedTextColor.YELLOW))
                .append(Component.text(firstThreshold + "/" + secondThreshold + "/" + thirdThreshold, NamedTextColor.GOLD))
                .append(Component.text(". Each tier grants a reward.", NamedTextColor.YELLOW))
                .append(Component.text(" Use /resource to go there now!", NamedTextColor.GREEN))
                .build();
    }

    /** Renders the default Resource Hunt boss-bar title. */
    public Component resourceHuntBossBarTitle() {
        return Component.text("Resource Hunt", NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.BOLD);
    }

    /** Renders an active Resource Hunt boss-bar title. */
    public Component resourceHuntBossBarProgress(int tier, String verb, int current, int threshold, String targetName) {
        return Component.text("Resource Hunt Tier " + tier + " - " + verb + " " + current + "/" + threshold + " " + targetName,
                NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.BOLD);
    }

    // ---------------------------------------------------------------- Blackjack tables

    /** Explains that Blackjack table management requires a player sender. */
    public Component blackjackManageRequiresPlayer() { return Messages.MM.deserialize("<red>Only players can manage Blackjack tables.</red>"); }
    /** Shows Blackjack table-creation syntax. */
    public Component blackjackCreateUsage(String label) { return Messages.MM.deserialize("<gray>Usage:</gray> <yellow>/" + label + " createtable <bet> [hexColor]</yellow>"); }
    /** Explains accepted Blackjack table-bet values. */
    public Component blackjackBetInvalid() { return Messages.MM.deserialize("<red>Bet must be a positive number, 0, or 'free'.</red>"); }
    /** Explains that a Blackjack table bet cannot be negative. */
    public Component blackjackBetNegative() { return Messages.MM.deserialize("<red>Bet cannot be negative.</red>"); }
    /** Explains accepted Blackjack card-back color input. */
    public Component blackjackColorInvalid() { return Messages.MM.deserialize("<red>Invalid color. Use a hex value like <yellow>FF8800</yellow> or <yellow>#FF8800</yellow>.</red>"); }
    /** Confirms that Blackjack table placement is awaiting a middle-button click. */
    public Component blackjackTableSetupStarted(String betDisplay, Integer backColor) {
        String colorSuffix = backColor == null ? ""
                : " <gray>| Back color:</gray> <yellow>#" + String.format("%06X", backColor) + "</yellow>";
        return Messages.MM.deserialize("<green>Table setup started!</green> <gray>Right-click the</gray> <yellow>middle control button</yellow> <gray>to finalize the table (bet:</gray> <yellow>" + betDisplay + "</yellow><gray>).</gray>" + colorSuffix);
    }
    /** Confirms that Blackjack table removal is awaiting a middle-button click. */
    public Component blackjackTableRemovalStarted() {
        return Messages.MM.deserialize("<green>Table removal started!</green> <gray>Right-click the</gray> <yellow>middle button</yellow> <gray>of the table you want to remove.</gray>");
    }
    /** Shows the Blackjack table command help syntax. */
    public Component blackjackUsage(String label) {
        return Messages.MM.deserialize("<gray>Usage:</gray> <yellow>/" + label + " <createtable <bet> [hexColor]|removetable></yellow>");
    }

    /** Instructs an administrator to select a Blackjack table's middle button. */
    public Component blackjackSetupPrompt(int bet) {
        return Messages.MM.deserialize("<yellow>Right-click the <white>MIDDLE</white> control button of the table to register it. "
                + "Bet: <white>$" + bet + "</white></yellow>");
    }

    /** Instructs an administrator to select a Blackjack table for removal. */
    public Component blackjackRemovalPrompt() {
        return Messages.MM.deserialize("<yellow>Right-click the <white>MIDDLE</white> button of the server table you want to remove.</yellow>");
    }

    /** Explains that a Blackjack setup click was not on a wall button. */
    public Component blackjackWallButtonRequired() {
        return Messages.MM.deserialize("<red>That doesn't look like a wall button. Please right-click a wall-mounted button.</red>");
    }

    /** Explains that a Blackjack setup button cannot be floor or ceiling mounted. */
    public Component blackjackWallMountRequired() {
        return Messages.MM.deserialize("<red>Please use a wall-mounted button (not floor or ceiling).</red>");
    }

    /** Explains that no valid surface exists beneath a proposed Blackjack table. */
    public Component blackjackTableSurfaceRequired() {
        return Messages.MM.deserialize("<red>No valid 2x3 block area found for this button. "
                + "Ensure a solid 2-wide by 3-deep block area sits beneath the three control buttons.</red>");
    }

    /** Confirms registration of a Blackjack table. */
    public Component blackjackTableRegistered(String coordinates, int bet) {
        return Messages.MM.deserialize("<green>Blackjack table registered!</green> "
                + "<gray>Center:</gray> <yellow>" + coordinates + "</yellow> <gray>| Bet:</gray> <yellow>$" + bet + "</yellow>");
    }

    /** Confirms removal of a Blackjack table. */
    public Component blackjackTableRemoved() { return Messages.MM.deserialize("<green>Blackjack table removed.</green>"); }

    /** Renders a physical Blackjack table's hologram. */
    public Component blackjackTableHologram(String betLabel) {
        return Messages.MM.deserialize("<gold><bold>Blackjack Table</bold></gold>\n"
                + "<gray>Press MIDDLE to Play</gray>\n"
                + "<yellow>Bet: " + betLabel + "</yellow>");
    }

    /** Explains that a player must finish their current Blackjack game first. */
    public Component blackjackGameInProgress() {
        return Messages.MM.deserialize("<red>Your game is still in progress. Use the LEFT or RIGHT buttons.</red>");
    }

    /** Explains that a player's balance cannot cover a Blackjack table bet. */
    public Component blackjackCannotAfford(int bet, long balance) {
        return Messages.MM.deserialize("<red>You cannot afford a bet of $" + bet + ". Your balance is $" + balance + ".</red>");
    }

    /** Explains that a Blackjack game could not start and whether its bet was refunded. */
    public Component blackjackStartFailed(boolean refunded) {
        return Messages.MM.deserialize("<red>Could not start a Blackjack game here."
                + (refunded ? " Your bet was refunded." : "") + "</red>");
    }

    /** Announces an in-progress Blackjack hand after its initial deal. */
    public Component blackjackHandStarted(int playerValue, String dealerShowing) {
        return Messages.MM.deserialize("<gold>Blackjack!</gold> <gray>Your hand:</gray> <yellow>" + playerValue
                + "</yellow> <gray>| Dealer shows:</gray> <yellow>" + dealerShowing + "</yellow>");
    }

    /** Explains the physical Blackjack table controls. */
    public Component blackjackControls() {
        return Messages.MM.deserialize("<gray>LEFT button = Hit  |  RIGHT button = Stand  |  MIDDLE button = Clear</gray>");
    }

    /** Confirms a Blackjack hit and shows the current hand value. */
    public Component blackjackHit(int playerValue) {
        return Messages.MM.deserialize("<green>Hit!</green> <gray>Hand:</gray> <yellow>" + playerValue + "</yellow>");
    }

    /** Confirms that a Blackjack player stood. */
    public Component blackjackStand() { return Messages.MM.deserialize("<red>Stand.</red>"); }

    /** Shows the final Blackjack player and dealer values. */
    public Component blackjackFinalValues(int playerValue, int dealerValue) {
        return Messages.MM.deserialize("<gray>You:</gray> <yellow>" + playerValue
                + "</yellow> <gray>vs Dealer:</gray> <yellow>" + dealerValue + "</yellow>");
    }

    /** Instructs a player to clear a finished Blackjack board. */
    public Component blackjackClearBoard() {
        return Messages.MM.deserialize("<gray>Press MIDDLE to clear the board.</gray>");
    }

    /** Renders a settled Blackjack outcome without changing settlement arithmetic. */
    public Component blackjackSettlementSummary(String resultName, int bet, int payoutAmount, int rakebackAmount) {
        boolean freeTurn = bet == 0;
        String rakebackSuffix = rakebackAmount > 0 ? " <gray>(Rakeback: +$" + rakebackAmount + ")</gray>" : "";
        String practiceNote = freeTurn ? " <gray>(Practice table â€” no stakes)</gray>" : "";
        String summary = switch (resultName) {
            case "PLAYER_BLACKJACK" -> "<gold><bold>BLACKJACK!</bold></gold>" + (freeTurn
                    ? " <green>You win!</green>" + practiceNote
                    : " <green>You won $" + (payoutAmount - bet) + "!</green>");
            case "PLAYER_WIN" -> "<green>You win!</green>" + (freeTurn
                    ? practiceNote
                    : " <green>Payout: $" + payoutAmount + " (net +$" + bet + ")</green>");
            case "PUSH" -> "<yellow>Push.</yellow>" + (freeTurn
                    ? " <yellow>Tie game.</yellow>" + practiceNote
                    : " <yellow>Your bet of $" + bet + " is returned.</yellow>");
            case "DEALER_WIN" -> "<red>Dealer wins.</red>" + (freeTurn
                    ? practiceNote
                    : " <red>You lost $" + bet + ".</red>" + rakebackSuffix);
            default -> throw new IllegalArgumentException("Unknown Blackjack result: " + resultName);
        };
        return Messages.MM.deserialize(summary);
    }

    /** Explains that an inactive Blackjack game was automatically ended. */
    public Component blackjackInactiveGameEnded(int bet) {
        String betClause = bet == 0
                ? "(Practice table â€” no stakes.)"
                : "Your bet of $" + bet + " was forfeited.";
        return Messages.MM.deserialize("<red>Your Blackjack game was ended due to 10 minutes of inactivity. "
                + betClause + "</red>");
    }

    /** Explains that logging into a resource world returns the player to survival. */
    public Component resourceWorldLoginEjected() {
        return Component.text("For your safety, returning you to the main survival world!", NamedTextColor.YELLOW);
    }

    /** Explains that a resource-Nether roof teleport was redirected to a safe platform. */
    public Component resourceWorldNetherRoofRedirected() {
        return Component.text("The Nether roof is off-limits; redirecting you to a safe platform.", NamedTextColor.GOLD);
    }

    /** Explains that ender chests are unavailable in resource worlds. */
    public Component resourceWorldEnderChestDisabled() {
        return Component.text("Ender chests are disabled in resource worlds!", NamedTextColor.RED);
    }

    // ---------------------------------------------------------------- Roulette (scan diagnostic)

    /** Diagnostic passthrough: renders the /roulettescan summary verbatim, one Component line per input line. */
    public Component rouletteScanResult(String summary) {
        String[] lines = summary.split("\n", -1);
        var builder = Component.text();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) builder.append(Component.newline());
            builder.append(Component.text(lines[i], i == 0 ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        }
        return builder.build();
    }

    /** Diagnostic passthrough: explains why a /roulettescan run could not complete. */
    public Component rouletteScanFailed(String reason) { return red("Roulette scan failed: " + reason); }

    // ---------------------------------------------------------------- Roulette boards

    /** Explains that Roulette board management requires a player sender. */
    public Component rouletteManageRequiresPlayer() {
        return Messages.MM.deserialize("<red>Only players can manage Roulette boards.</red>");
    }

    /** Shows the Roulette command help syntax. */
    public Component rouletteUsage(String label) {
        return Messages.MM.deserialize("<gray>Usage:</gray> <yellow>/" + label
                + " <createboard <min> <max>|removeboard|stake <amount>></yellow>");
    }

    /** Shows Roulette board-creation syntax. */
    public Component rouletteCreateUsage(String label) {
        return Messages.MM.deserialize("<gray>Usage:</gray> <yellow>/" + label + " createboard <min> <max></yellow>");
    }

    /** Explains that Roulette stake bounds must satisfy min &gt;= 1 and min &lt;= max. */
    public Component rouletteInvalidBounds() {
        return Messages.MM.deserialize("<red>Invalid bounds. Both min and max must be whole numbers, "
                + "min must be at least 1, and min cannot exceed max.</red>");
    }

    /** Confirms that Roulette board placement is awaiting a control-block click. */
    public Component rouletteCreateStarted(int minBet, int maxBet) {
        return Messages.MM.deserialize("<green>Board setup started!</green> <gray>Right-click the</gray> "
                + "<yellow>spin control button/lever</yellow> <gray>to finalize the board (stakes:</gray> "
                + "<yellow>$" + minBet + "-$" + maxBet + "</yellow><gray>).</gray>");
    }

    /** Instructs an administrator to select a Roulette board's spin control. */
    public Component rouletteControlSetupPrompt(int minBet, int maxBet) {
        return Messages.MM.deserialize("<yellow>Right-click the button or lever that will act as this "
                + "board's admin spin control. Stakes: <white>$" + minBet + "-$" + maxBet + "</white></yellow>");
    }

    /** Confirms that Roulette board removal is awaiting a control-block click. */
    public Component rouletteRemovalStarted() {
        return Messages.MM.deserialize("<green>Board removal started!</green> <gray>Right-click the</gray> "
                + "<yellow>spin control</yellow> <gray>of the board you want to remove.</gray>");
    }

    /** Instructs an administrator to select a Roulette board for removal. */
    public Component rouletteRemovalPrompt() {
        return Messages.MM.deserialize("<yellow>Right-click the spin control of the board you want to remove.</yellow>");
    }

    /** Explains that a Roulette setup click was not on a wall-mountable button or lever. */
    public Component rouletteWallButtonRequired() {
        return Messages.MM.deserialize("<red>That doesn't look like a button or lever. Please right-click a button or lever.</red>");
    }

    /** Explains that a Roulette setup control cannot be floor or ceiling mounted. */
    public Component rouletteWallMountRequired() {
        return Messages.MM.deserialize("<red>Please use a wall-mounted button or lever (not floor or ceiling).</red>");
    }

    /** Explains that a scanned Roulette board is missing required segments. */
    public Component rouletteBoardIncomplete(List<String> problems) {
        return Messages.MM.deserialize("<red>Board scan incomplete — nothing was registered:</red> <gray>"
                + String.join("; ", problems) + "</gray>");
    }

    /** Explains that this exact Roulette board is already registered. */
    public Component rouletteBoardAlreadyRegistered() {
        return Messages.MM.deserialize("<red>This board is already registered.</red>");
    }

    /** Explains that a scanned Roulette board could not be brought online (hitbox spawn failure). */
    public Component rouletteBoardActivationFailed() {
        return Messages.MM.deserialize("<red>Board activation failed — nothing was registered. "
                + "See the server console for details, then try again.</red>");
    }

    /** Confirms registration of a Roulette board. */
    public Component rouletteBoardRegistered(String coordinates, int minBet, int maxBet) {
        return Messages.MM.deserialize("<green>Roulette board registered!</green> "
                + "<gray>Center:</gray> <yellow>" + coordinates + "</yellow> <gray>| Stakes:</gray> "
                + "<yellow>$" + minBet + "-$" + maxBet + "</yellow>");
    }

    /** Confirms removal of a Roulette board. */
    public Component rouletteBoardRemoved() {
        return Messages.MM.deserialize("<green>Roulette board removed.</green>");
    }

    /** Explains that a Roulette board removal hit an error and may only be partially cleaned up. */
    public Component rouletteBoardRemovalFailed() {
        return Messages.MM.deserialize("<red>Board removal hit an error and may be only partially "
                + "cleaned up — check the server console.</red>");
    }

    // ---------------------------------------------------------------- Roulette betting

    /** Shows the syntax for setting a sticky Roulette stake. */
    public Component rouletteStakeUsage(String label) {
        return Messages.MM.deserialize("<gray>Usage:</gray> <yellow>/" + label + " stake <amount></yellow>");
    }

    /** Explains that a typed Roulette stake was not a whole number. */
    public Component rouletteStakeNotWholeNumber() {
        return Messages.MM.deserialize("<red>Stake must be a whole number, e.g.</red> <yellow>/roulette stake 25</yellow><red>.</red>");
    }

    /** Explains that a typed Roulette stake was zero or negative. */
    public Component rouletteStakeBelowMinimum() {
        return Messages.MM.deserialize("<red>Stake must be at least $1.</red>");
    }

    /** Explains that a typed Roulette stake exceeds the largest wager any board could ever accept. */
    public Component rouletteStakeAboveCeiling(int maxStake) {
        return Messages.MM.deserialize("<red>Stake is too large — the maximum any board can ever accept is</red> "
                + "<yellow>$" + maxStake + "</yellow><red>.</red>");
    }

    /** Confirms a sticky Roulette stake was set. */
    public Component rouletteStakeSet(int amount) {
        return Messages.MM.deserialize("<green>Stake set to</green> <yellow>$" + amount
                + "</yellow><green>. Click a segment to bet.</green>");
    }

    /** Explains that a segment click had no sticky stake to wager. */
    public Component rouletteNoStakeSet(int minBet, int maxBet) {
        return Messages.MM.deserialize("<red>Set a stake first with</red> <yellow>/roulette stake <amount></yellow> "
                + "<gray>(this board's range:</gray> <yellow>$" + minBet + "-$" + maxBet + "</yellow><gray>).</gray>");
    }

    /** Explains that a sticky stake falls outside the clicked board's current min/max. */
    public Component rouletteStakeOutsideBoardRange(int stake, int minBet, int maxBet) {
        return Messages.MM.deserialize("<red>Your stake of</red> <yellow>$" + stake + "</yellow> "
                + "<red>is outside this board's range:</red> <yellow>$" + minBet + "-$" + maxBet + "</yellow><red>.</red>");
    }

    /** Explains that a segment click landed while betting was already closed. */
    public Component rouletteBettingClosed() {
        return Messages.MM.deserialize("<red>Betting is closed for this round — wait for the wheel to settle.</red>");
    }

    /** Explains that a player's balance cannot cover a Roulette stake at click time. */
    public Component rouletteInsufficientFunds(int stake, long balance) {
        return Messages.MM.deserialize("<red>You cannot afford a stake of $" + stake
                + ". Your balance is $" + balance + ".</red>");
    }

    /** Explains that a bet was rejected by this round's cumulative exposure guard. */
    public Component rouletteExposureLimitReached() {
        return Messages.MM.deserialize("<red>You've reached this round's maximum total wager. Wait for the next round.</red>");
    }

    /**
     * Confirms a placed Roulette bet. {@code betType}/{@code betTarget} are neutral tokens (the
     * enum name and a selector/color token), never pre-formatted display text — the wording lives
     * entirely in this factory, matching {@code blackjackSettlementSummary}'s precedent of not
     * importing a minigame type into {@code core}.
     */
    public Component rouletteBetPlaced(String betType, String betTarget, int amount, int payoutMultiplier) {
        String label = switch (betType) {
            case "STRAIGHT" -> "pocket " + betTarget;
            case "DOZEN" -> "dozen " + betTarget;
            case "COLOR" -> betTarget.equals("RED") ? "red" : "black";
            default -> betTarget;
        };
        return Messages.MM.deserialize("<green>Bet placed:</green> <yellow>$" + amount + "</yellow> <gray>on</gray> "
                + "<yellow>" + label + "</yellow> <gray>(pays " + payoutMultiplier + ":1).</gray>");
    }

    /** Generic guard-rail message for a malformed-bet or should-never-happen rejection branch. */
    public Component rouletteBetRejected() {
        return Messages.MM.deserialize("<red>That bet could not be placed. See the server console if this repeats.</red>");
    }

    /** Broadcasts to nearby players that a Roulette round's betting window just opened. */
    public Component rouletteWindowOpened(int seconds) {
        return Messages.MM.deserialize("<gold>Roulette:</gold> <yellow>betting is open for " + seconds + " seconds!</yellow>");
    }

    /** Explains that a Roulette board cannot be removed while a round is in flight. */
    public Component rouletteBoardBusy() {
        return Messages.MM.deserialize("<red>This board has an active round — wait for it to finish before removing it.</red>");
    }

    /** Explains that a new Roulette board's finalization was deferred because another wheel is mid-spin. */
    public Component rouletteSpinInProgressElsewhere() {
        return Messages.MM.deserialize("<red>A Roulette wheel is spinning right now — wait for it to finish, then click "
                + "the control again to finalize this board.</red>");
    }

    /** Action-bar sticky-stake indicator, shown near a board when a stake is set. */
    public Component rouletteStakeIndicator(int stake, int minBet, int maxBet) {
        return Messages.MM.deserialize("<gray>Roulette stake:</gray> <yellow>$" + stake + "</yellow> "
                + "<gray>(board range $" + minBet + "-$" + maxBet + ")</gray>");
    }

    /** Action-bar sticky-stake indicator, shown near a board when no stake is set. */
    public Component rouletteStakeIndicatorUnset(int minBet, int maxBet) {
        return Messages.MM.deserialize("<gray>No Roulette stake set — </gray><yellow>/roulette stake <amount></yellow> "
                + "<gray>(board range $" + minBet + "-$" + maxBet + ")</gray>");
    }

    // ---------------------------------------------------------------- Roulette spin & settlement

    /** Nearby broadcast the instant a Roulette betting window closes and the wheel begins spinning. */
    public Component rouletteSpinStarted() {
        return Messages.MM.deserialize("<gold>Roulette:</gold> <yellow>betting is closed — the wheel is spinning!</yellow>");
    }

    /** Status hologram text for an idle Roulette board. */
    public Component rouletteIdleBoardStatus(int minBet, int maxBet) {
        return Messages.MM.deserialize("<gray>Click a segment to bet</gray>\n<yellow>$" + minBet + "-$" + maxBet + "</yellow>");
    }

    /** Status hologram text while a Roulette board's betting window is open. */
    public Component rouletteSpinCountdown(int secondsRemaining) {
        return Messages.MM.deserialize("<gold><bold>Betting open</bold></gold>\n<yellow>" + secondsRemaining + "s remaining</yellow>");
    }

    /** Status hologram text while a Roulette wheel is spinning. */
    public Component rouletteSpinningStatus() {
        return Messages.MM.deserialize("<gold><bold>Spinning...</bold></gold>");
    }

    /**
     * Status hologram text once a Roulette round has settled, held for the result linger period.
     * @param pocketColorName {@code RouletteWheel.PocketColor#name()} — "RED"/"BLACK"/"GREEN"
     */
    public Component rouletteResultStatus(int pocket, String pocketColorName) {
        return Messages.MM.deserialize("<gold><bold>Winner: " + pocket + "</bold></gold>\n" + colorTag(pocketColorName));
    }

    /** Nearby broadcast of a settled Roulette round's drawn pocket. {@code dozen} is 0 for pocket 0. */
    public Component rouletteSpinResult(int pocket, String pocketColorName, int dozen) {
        String dozenSuffix = dozen == 0 ? "" : " <gray>(dozen " + dozen + ")</gray>";
        return Messages.MM.deserialize("<gold>Roulette:</gold> <yellow>" + pocket + "</yellow> " + colorTag(pocketColorName) + dozenSuffix);
    }

    /**
     * Per-bettor settlement summary, sent only to bettors who are still online. Shows the gross
     * amount wagered and the amount actually won — {@code payout} is stake-inclusive (the
     * settlement math returns the stake plus winnings together, since the stake was already
     * debited at bet-placement time), so this factory subtracts {@code wagered} back out before
     * display: a $100 stake at 36:1 nets $3,600 in winnings, not the $3,700 {@code payout} itself
     * carries. Floored at {@code 0} rather than shown negative on a loss — {@code wagered} alone
     * already conveys what was lost.
     */
    public Component rouletteRoundOutcome(int pocket, String pocketColorName, long wagered, long payout, long rakeback) {
        long net = payout - wagered;
        long winnings = Math.max(0L, net);
        String wonColor = net >= 0 ? "green" : "red";
        String rakebackSuffix = rakeback > 0 ? " <gray>(Rakeback: +$" + rakeback + ")</gray>" : "";
        return Messages.MM.deserialize("<gold>Roulette result:</gold> <yellow>" + pocket + "</yellow> "
                + colorTag(pocketColorName) + " <gray>| Wagered:</gray> <yellow>$" + wagered + "</yellow> "
                + "<gray>| Won:</gray> <" + wonColor + ">$" + winnings + "</" + wonColor + ">" + rakebackSuffix);
    }

    /**
     * Server-wide broadcast for a round settlement whose net winnings reached the "big win"
     * threshold ({@code winnings >= 8x wagered} — see {@code RouletteSessionManager.isBigWin}).
     * {@code winnings} is net (stake already excluded), matching {@code rouletteRoundOutcome}'s
     * "Won" figure. {@code playerName} is a pre-resolved display name (the winner may be offline
     * by the time this fires) so this class stays decoupled from {@code OfflinePlayer} lookups.
     */
    public Component rouletteBigWinAnnouncement(String playerName, long winnings, int pocket, String pocketColorName) {
        return Messages.MM.deserialize("<gold><bold>Big Win!</bold></gold> <yellow>" + playerName + "</yellow> "
                + "<gray>just won</gray> <green>$" + winnings + "</green> <gray>on Roulette, pocket</gray> "
                + "<yellow>" + pocket + "</yellow> " + colorTag(pocketColorName) + "<gray>!</gray>");
    }

    /** House-balance hologram — the one global house number on the server. */
    public Component rouletteHouseBalanceHologram(long balance) {
        return Messages.MM.deserialize("<gold><bold>House Balance</bold></gold>\n<yellow>$" + balance + "</yellow>");
    }

    /** House-balance hologram text before {@code HouseAccount#isLoaded()} becomes true. */
    public Component rouletteHouseBalanceUnavailable() {
        return Messages.MM.deserialize("<gold><bold>House Balance</bold></gold>\n<gray>Loading...</gray>");
    }

    // ---------------------------------------------------------------- Roulette segment labels

    /**
     * Floating label above a betting hitbox, shown for as long as the board is active (idle and
     * betting alike — a clickable hitbox is labeled regardless of round state). {@code
     * descriptionLine} and {@code odds} (e.g. {@code "36:1"}) are pre-built plain strings — only
     * the roulette package knows bet-type/selector/pocket-color mapping. {@code accentColorName}
     * is one of {@code "RED"}/{@code "BLACK"}/{@code "GREEN"}/{@code null}, reused from this
     * class's existing pocket-color vocabulary, to tint the description line; {@code null} (dozen
     * bets) renders the description in a neutral color.
     */
    public Component rouletteSegmentLabel(String descriptionLine, String odds, String accentColorName) {
        String accent = switch (accentColorName == null ? "" : accentColorName) {
            case "RED" -> "red";
            case "BLACK" -> "dark_gray";
            case "GREEN" -> "green";
            default -> "white";
        };
        return Messages.MM.deserialize("<" + accent + ">" + descriptionLine + "</" + accent + ">"
                + "\n<gray>" + odds + "</gray>");
    }

    // ---------------------------------------------------------------- Roulette admin force-spin

    /** Force-spin pressed on an idle board, or one still lingering on its last result. */
    public Component rouletteForceSpinNothingToSpin() {
        return Messages.MM.deserialize("<red>There is no open betting round on this board to force-close.</red>");
    }

    /** Force-spin pressed while the wheel is already spinning. */
    public Component rouletteForceSpinAlreadySpinning() {
        return Messages.MM.deserialize("<red>This board is already spinning.</red>");
    }

    /** Force-spin pressed on a board whose hitboxes were deactivated (e.g. a misaligned wheel). */
    public Component rouletteForceSpinBoardInactive() {
        return Messages.MM.deserialize("<red>This board is inactive and cannot be force-spun right now.</red>");
    }

    /** Confirms an admin's force-spin closed betting and started the wheel. */
    public Component rouletteForceSpinTriggered() {
        return Messages.MM.deserialize("<gold>Roulette:</gold> <yellow>betting force-closed — spinning now.</yellow>");
    }

    /** Generic guard-rail message for a force-spin that failed unexpectedly. */
    public Component rouletteForceSpinFailed() {
        return Messages.MM.deserialize("<red>The force-spin failed. See the server console if this repeats.</red>");
    }

    private static String colorTag(String pocketColorName) {
        return switch (pocketColorName) {
            case "RED" -> "<red>(Red)</red>";
            case "BLACK" -> "<dark_gray>(Black)</dark_gray>";
            default -> "<green>(Green)</green>";
        };
    }

    private static Component red(String message) { return Component.text(message, NamedTextColor.RED); }
    private static Component green(String message) { return Component.text(message, NamedTextColor.GREEN); }
    private static Component yellow(String message) { return Component.text(message, NamedTextColor.YELLOW); }
    private static Component gray(String message) { return Component.text(message, NamedTextColor.GRAY); }
    private static Component usageRow(String label, String syntax, String description) {
        return Component.text("/" + label + " " + syntax, NamedTextColor.YELLOW)
                .append(Component.text(" - " + description, NamedTextColor.GRAY));
    }
}
