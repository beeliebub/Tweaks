package me.beeliebub.tweaks.ranks;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.economy.BalanceCommand;
import me.beeliebub.tweaks.utils.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

// /ranks edit GUI hierarchy — fully Paper Dialog-driven.
// Hierarchy:
//   RANK_LIST ── RANK_MENU ── EDIT_PROMPT (per attribute)
//
// Each confirmation dialog's submit callback calls handleSubmit, which validates
// the raw text, calls the appropriate RankManager setter, and reopens the rank menu.
@SuppressWarnings("UnstableApiUsage") // Paper's Dialog API is @ApiStatus.Experimental in 26.1.2.
public final class RankEditGUI {

    private RankEditGUI() {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ---------------------------------------------------------------- Rank list

    /**
     * Opens the top-level list of all ranks (1..maxRank), one button each.
     */
    public static void openRankList(Player player, RankManager rankManager) {
        int max = rankManager.getMaxRank();
        List<ActionButton> buttons = new ArrayList<>();

        for (int rank = 1; rank <= max; rank++) {
            final int r = rank;
            String name = rankManager.getRankDisplayName(r);
            double cost = rankManager.getRankCost(r);
            double multiplier = rankManager.getRankMultiplierBonus(r);
            double rakeback = rankManager.getRankRakeback(r);

            Component label = Component.text("Rank ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(ColorUtil.parse(name))
                    .append(Component.text(" — " + BalanceCommand.formatBalance(cost), NamedTextColor.YELLOW, TextDecoration.BOLD));

            Component tooltip = joinLines(
                    Component.text("Multiplier: " + (int) Math.round(multiplier * 100) + "%", NamedTextColor.GRAY),
                    Component.text("Rakeback: " + (int) Math.round(rakeback * 100) + "%", NamedTextColor.GRAY),
                    Component.text("Click to edit this rank.", NamedTextColor.GREEN));

            buttons.add(dialogButton(label, tooltip, p -> openRankMenu(p, rankManager, r)));
        }

        DialogBase base = DialogBase.builder(MM.deserialize("<!italic><green><bold>Edit Ranks"))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Select a rank to edit its attributes.", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(2)
                        .build()));

        player.showDialog(dialog);
    }

    // --------------------------------------------------------------- Rank menu

    /**
     * Opens the attribute selection sub-menu for one rank.
     */
    public static void openRankMenu(Player player, RankManager rankManager, int rank) {
        String name = rankManager.getRankDisplayName(rank);
        double cost = rankManager.getRankCost(rank);
        double multiplier = rankManager.getRankMultiplierBonus(rank);
        double rakeback = rankManager.getRankRakeback(rank);

        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(dialogButton(
                Component.text("Edit Name", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("Current: ", NamedTextColor.GRAY).append(ColorUtil.parse(name)),
                p -> openEditPrompt(p, rankManager, rank, "name")));

        buttons.add(dialogButton(
                Component.text("Edit Cost", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Current: " + BalanceCommand.formatBalance(cost), NamedTextColor.GRAY),
                p -> openEditPrompt(p, rankManager, rank, "cost")));

        buttons.add(dialogButton(
                Component.text("Edit Multiplier", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Current: " + multiplier + " (e.g. 0.01 = 1%)", NamedTextColor.GRAY),
                p -> openEditPrompt(p, rankManager, rank, "multiplier")));

        buttons.add(dialogButton(
                Component.text("Edit Rakeback", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("Current: " + rakeback + " (e.g. 0.05 = 5%)", NamedTextColor.GRAY),
                p -> openEditPrompt(p, rankManager, rank, "rakeback")));

        ActionButton back = dialogButton(
                Component.text("← Back to Ranks", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the rank list.", NamedTextColor.GRAY),
                p -> openRankList(p, rankManager));

        DialogBase base = DialogBase.builder(
                        Component.text("Rank: ", NamedTextColor.GREEN)
                                .append(ColorUtil.parse(name))
                                .decoration(TextDecoration.ITALIC, false))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Select an attribute to edit.", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(2)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    // -------------------------------------------------------------- Edit prompt

    /**
     * Opens a confirmation dialog with a text-entry field for a single rank attribute.
     *
     * @param attribute one of "name", "cost", "multiplier", "rakeback"
     */
    public static void openEditPrompt(Player player, RankManager rankManager, int rank, String attribute) {
        String rankName = rankManager.getRankDisplayName(rank);
        String currentValue = currentValueString(rankManager, rank, attribute);

        ActionButton submit = ActionButton.builder(
                        Component.text("Save", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .decoration(TextDecoration.ITALIC, false))
                .tooltip(Component.text("Apply the entered value.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                handleSubmit(p, rankManager, rank, attribute, view.getText("value"));
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Component.text("Cancel", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Return to the rank menu.", NamedTextColor.GRAY),
                p -> openRankMenu(p, rankManager, rank));

        String capitalizedAttr = attribute.substring(0, 1).toUpperCase(Locale.ROOT) + attribute.substring(1);

        DialogBase base = DialogBase.builder(
                        Component.text("Edit " + capitalizedAttr + ": Rank ", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .append(ColorUtil.parse(rankName))
                                .decoration(TextDecoration.ITALIC, false))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Current value: " + currentValue + ". Enter a new value below.",
                                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))))
                .inputs(List.of(
                        DialogInput.text("value",
                                        Component.text("Value", NamedTextColor.YELLOW)
                                                .decoration(TextDecoration.ITALIC, false))
                                .maxLength(32)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(submit, cancel)));

        player.showDialog(dialog);
    }

    // --------------------------------------------------------------- Submission

    /**
     * Validates the typed value, calls the appropriate RankManager setter, and sends feedback.
     * On validation failure reopens the edit prompt; on success reopens the rank menu.
     */
    private static void handleSubmit(Player player, RankManager rankManager, int rank, String attribute, String raw) {
        String trimmed = raw == null ? "" : raw.trim();

        if (attribute.equals("name")) {
            if (trimmed.isEmpty()) {
                player.sendMessage(Component.text("Name cannot be empty.", NamedTextColor.RED));
                openEditPrompt(player, rankManager, rank, attribute);
                return;
            }
            rankManager.setRankName(rank, trimmed);
            String displayName = rankManager.getRankDisplayName(rank);
            player.sendMessage(
                    Component.text("Set name of Rank " + rank + " to ", NamedTextColor.GREEN)
                            .append(ColorUtil.parse(displayName))
                            .append(Component.text(".", NamedTextColor.GREEN)));
        } else {
            double value;
            try {
                value = Double.parseDouble(trimmed);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
                openEditPrompt(player, rankManager, rank, attribute);
                return;
            }
            switch (attribute) {
                case "cost" -> rankManager.setRankCost(rank, value);
                case "multiplier" -> rankManager.setRankMultiplier(rank, value);
                case "rakeback" -> rankManager.setRankRakeback(rank, value);
                default -> {
                    player.sendMessage(Component.text("Unknown attribute: " + attribute, NamedTextColor.RED));
                    openRankMenu(player, rankManager, rank);
                    return;
                }
            }
            String capitalizedAttr = attribute.substring(0, 1).toUpperCase(Locale.ROOT) + attribute.substring(1);
            String rankName = rankManager.getRankDisplayName(rank);
            player.sendMessage(
                    Component.text("Set " + capitalizedAttr + " of Rank ", NamedTextColor.GREEN)
                            .append(ColorUtil.parse(rankName))
                            .append(Component.text(" to " + value + ".", NamedTextColor.GREEN)));
        }

        openRankMenu(player, rankManager, rank);
    }

    // -------------------------------------------------------------- Helpers

    private static String currentValueString(RankManager rankManager, int rank, String attribute) {
        return switch (attribute) {
            case "name" -> rankManager.getRankDisplayName(rank);
            case "cost" -> BalanceCommand.formatBalance(rankManager.getRankCost(rank));
            case "multiplier" -> String.valueOf(rankManager.getRankMultiplierBonus(rank));
            case "rakeback" -> String.valueOf(rankManager.getRankRakeback(rank));
            default -> "unknown";
        };
    }

    private static ClickCallback.Options unlimitedClicks() {
        return ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();
    }

    private static ActionButton dialogButton(Component label, Component tooltip, Consumer<Player> action) {
        return ActionButton.builder(label.decoration(TextDecoration.ITALIC, false))
                .tooltip(tooltip.decoration(TextDecoration.ITALIC, false))
                .action(DialogAction.customClick(
                        (_, audience) -> {
                            if (audience instanceof Player p) {
                                action.accept(p);
                            }
                        },
                        unlimitedClicks()))
                .build();
    }

    private static Component joinLines(Component... lines) {
        Component result = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) result = result.append(Component.newline());
            result = result.append(lines[i].decoration(TextDecoration.ITALIC, false));
        }
        return result;
    }
}
