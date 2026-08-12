package me.beeliebub.tweaks.skyblock.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.island.IslandCreationService;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Presentation-only island creation picker; mutation remains in the command/service layer. */
@SuppressWarnings("UnstableApiUsage")
public final class IslandGUI {
    private final TypeRegistry types;
    private final IslandCreationService creation;

    public IslandGUI(TypeRegistry types) { this(types, null); }

    public IslandGUI(TypeRegistry types, IslandCreationService creation) {
        this.types = types;
        this.creation = creation;
    }

    public void open(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        for (var difficulty : types.difficulties()) {
            if (!types.typesFor(difficulty.id()).isEmpty()) {
                buttons.add(button(difficulty.displayName(), Messages.SKYBLOCK.islandDifficultyButtonTooltip(),
                        target -> openTypes(target, difficulty.id())));
            }
        }
        show(player, Messages.SKYBLOCK.islandCreateTitle(), Messages.SKYBLOCK.islandDifficultyPrompt(), buttons, null);
    }

    private void openTypes(Player player, String difficultyId) {
        List<ActionButton> buttons = new ArrayList<>();
        for (var type : types.typesFor(difficultyId)) {
            buttons.add(button(type.displayName(), Messages.SKYBLOCK.islandTypeButtonTooltip(),
                    target -> create(target, type.id(), difficultyId)));
        }
        if (buttons.isEmpty()) {
            showNoChoices(player, true);
            return;
        }
        show(player, Messages.SKYBLOCK.islandChooseTypeTitle(), Messages.SKYBLOCK.islandChooseTypePrompt(), buttons,
                this::open);
    }

    private void create(Player target, String typeId, String difficultyId) {
        if (creation == null || !creation.canUse(target)) {
            target.sendMessage(Messages.SKYBLOCK.invalidInput("island creation is unavailable"));
            return;
        }
        var result = creation.begin(target, typeId, difficultyId, completed -> {
            if (!creation.canUse(target)) {
                target.sendMessage(Messages.SKYBLOCK.invalidInput("island creation is unavailable"));
                return;
            }
            if (completed.status() == IslandCreationService.Status.COMPLETED) {
                target.sendMessage(Messages.SKYBLOCK.created(completed.island().displayName()));
            } else if (completed.status() == IslandCreationService.Status.FAILED) {
                target.sendMessage(Messages.SKYBLOCK.invalidInput(completed.reason()));
            }
        });
        if (result.status() == IslandCreationService.Status.FAILED) {
            target.sendMessage(Messages.SKYBLOCK.invalidInput(result.reason()));
        }
    }

    private void showNoChoices(Player player, boolean back) {
        List<ActionButton> buttons = List.of(button(Messages.SKYBLOCK.islandNoChoicesButton(),
                Messages.SKYBLOCK.islandNoChoicesTooltip(), target -> target.sendMessage(
                        Messages.SKYBLOCK.invalidInput(Messages.SKYBLOCK.islandNoChoicesMessage()))));
        show(player, back ? Messages.SKYBLOCK.islandChooseTypeTitle() : Messages.SKYBLOCK.islandCreateTitle(),
                Messages.SKYBLOCK.islandNoChoicesPrompt(), buttons,
                back ? this::open : null);
    }

    private void show(Player player, String title, String body, List<ActionButton> buttons,
                      java.util.function.Consumer<Player> back) {
        if (buttons.isEmpty()) {
            showNoChoices(player, back != null);
            return;
        }
        DialogBase base = DialogBase.builder(Messages.SKYBLOCK.text(title, NamedTextColor.GREEN,
                        TextDecoration.BOLD))
                .body(List.of(DialogBody.plainMessage(Messages.SKYBLOCK.text(
                        body, NamedTextColor.WHITE))))
                .canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE).build();
        ActionButton backButton = back == null ? null : button(Messages.SKYBLOCK.islandBackButton(),
                Messages.SKYBLOCK.islandBackTooltip(), back);
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(buttons, backButton, 2))));
    }

    private static ActionButton button(String label, String tooltip, java.util.function.Consumer<Player> action) {
        return ActionButton.builder(Messages.SKYBLOCK.text(label, NamedTextColor.AQUA))
                .tooltip(Messages.SKYBLOCK.text(tooltip, NamedTextColor.GRAY))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player target) action.accept(target);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build())).build();
    }
}
