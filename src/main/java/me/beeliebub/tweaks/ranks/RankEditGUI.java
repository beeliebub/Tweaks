package me.beeliebub.tweaks.ranks;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.economy.BalanceCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextDecoration;
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
@SuppressWarnings("UnstableApiUsage") // Paper's Dialog API is @ApiStatus.Experimental in 26.2.
public final class RankEditGUI {

    private RankEditGUI() {}

    // ---------------------------------------------------------------- Rank list

    /**
     * Opens the top-level list of all ranks (1..maxRank), one button each.
     */
    public static void openRankList(Player player, RankManager rankManager) {
        int max = rankManager.getMaxRank();
        List<ActionButton> buttons = new ArrayList<>();

        for (int rank = 1; rank <= max; rank++) {
            final int r = rank;
            double cost = rankManager.getRankCost(r);
            double multiplier = rankManager.getRankMultiplierBonus(r);
            double rakeback = rankManager.getRankRakeback(r);

            Component label = Messages.rankEditListButtonLabel(rankManager.getRankDisplayComponent(r),
                    BalanceCommand.formatBalance(cost));

            Component tooltip = joinLines(
                    Messages.rankEditListMultiplierTooltip((int) Math.round(multiplier * 100)),
                    Messages.rankEditListRakebackTooltip((int) Math.round(rakeback * 100)),
                    Messages.rankEditListInstructionTooltip());

            buttons.add(dialogButton(label, tooltip, p -> openRankMenu(p, rankManager, r)));
        }

        DialogBase base = DialogBase.builder(Messages.rankEditListTitle())
                .body(List.of(DialogBody.plainMessage(
                        Messages.rankEditListBody())))
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
        double cost = rankManager.getRankCost(rank);
        double multiplier = rankManager.getRankMultiplierBonus(rank);
        double rakeback = rankManager.getRankRakeback(rank);

        List<ActionButton> buttons = new ArrayList<>();

        buttons.add(dialogButton(
                Messages.rankEditNameButtonLabel(),
                Messages.rankEditNameTooltip(rankManager.getRankDisplayComponent(rank)),
                p -> openEditPrompt(p, rankManager, rank, "name")));

        buttons.add(dialogButton(
                Messages.rankEditCostButtonLabel(),
                Messages.rankEditCostTooltip(BalanceCommand.formatBalance(cost)),
                p -> openEditPrompt(p, rankManager, rank, "cost")));

        buttons.add(dialogButton(
                Messages.rankEditMultiplierButtonLabel(),
                Messages.rankEditMultiplierTooltip(multiplier),
                p -> openEditPrompt(p, rankManager, rank, "multiplier")));

        buttons.add(dialogButton(
                Messages.rankEditRakebackButtonLabel(),
                Messages.rankEditRakebackTooltip(rakeback),
                p -> openEditPrompt(p, rankManager, rank, "rakeback")));

        ActionButton back = dialogButton(
                Messages.rankEditBackButtonLabel(),
                Messages.rankEditBackTooltip(),
                p -> openRankList(p, rankManager));

        DialogBase base = DialogBase.builder(Messages.rankEditMenuTitle(rankManager.getRankDisplayComponent(rank)))
                .body(List.of(DialogBody.plainMessage(
                        Messages.rankEditMenuBody())))
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
        String currentValue = currentValueString(rankManager, rank, attribute);

        ActionButton submit = ActionButton.builder(
                        Messages.rankEditSaveButtonLabel())
                .tooltip(Messages.rankEditSaveTooltip())
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                handleSubmit(p, rankManager, rank, attribute, view.getText("value"));
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Messages.rankEditCancelButtonLabel(),
                Messages.rankEditCancelTooltip(),
                p -> openRankMenu(p, rankManager, rank));

        String capitalizedAttr = attribute.substring(0, 1).toUpperCase(Locale.ROOT) + attribute.substring(1);

        DialogBase base = DialogBase.builder(Messages.rankEditPromptTitle(
                capitalizedAttr, rankManager.getRankDisplayComponent(rank)))
                .body(List.of(DialogBody.plainMessage(
                        Messages.rankEditPromptBody(currentValue))))
                .inputs(List.of(
                        DialogInput.text("value",
                                        Messages.rankEditValueInputLabel())
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
                player.sendMessage(Messages.rankEditNameCannotBeEmpty());
                openEditPrompt(player, rankManager, rank, attribute);
                return;
            }
            rankManager.setRankName(rank, trimmed);
            player.sendMessage(Messages.rankEditNameUpdated(rank, rankManager.getRankDisplayComponent(rank)));
        } else {
            double value;
            try {
                value = Double.parseDouble(trimmed);
            } catch (NumberFormatException e) {
                player.sendMessage(Messages.invalidDecimal());
                openEditPrompt(player, rankManager, rank, attribute);
                return;
            }
            switch (attribute) {
                case "cost" -> rankManager.setRankCost(rank, value);
                case "multiplier" -> rankManager.setRankMultiplier(rank, value);
                case "rakeback" -> rankManager.setRankRakeback(rank, value);
                default -> {
                    player.sendMessage(Messages.rankEditUnknownAttribute(attribute));
                    openRankMenu(player, rankManager, rank);
                    return;
                }
            }
            String capitalizedAttr = attribute.substring(0, 1).toUpperCase(Locale.ROOT) + attribute.substring(1);
            player.sendMessage(Messages.rankEditAttributeUpdated(capitalizedAttr,
                    rankManager.getRankDisplayComponent(rank), value));
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
