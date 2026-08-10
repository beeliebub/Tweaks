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
import net.kyori.adventure.text.Component;
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
            for (var type : types.typesFor(difficulty.id())) {
                buttons.add(button(type.displayName() + " / " + difficulty.displayName(),
                        "Create this island type", target -> {
                            if (creation == null || !creation.canUse(target)) {
                                target.sendMessage(Messages.SKYBLOCK.invalidInput("island creation is unavailable"));
                                return;
                            }
                            var result = creation.begin(target, type.id(), difficulty.id(), completed -> {
                                if (!creation.canUse(target)) return;
                                if (completed.status() == IslandCreationService.Status.COMPLETED) {
                                    target.sendMessage(Messages.SKYBLOCK.created(completed.island().displayName()));
                                } else if (completed.status() == IslandCreationService.Status.FAILED) {
                                    target.sendMessage(Messages.SKYBLOCK.invalidInput(completed.reason()));
                                }
                            });
                            if (result.status() == IslandCreationService.Status.FAILED) {
                                target.sendMessage(Messages.SKYBLOCK.invalidInput(result.reason()));
                            }
                        }));
            }
        }
        if (buttons.isEmpty()) buttons.add(button("No choices", "No valid island types are configured", ignored -> { }));
        DialogBase base = DialogBase.builder(Messages.SKYBLOCK.text("Create Skyblock Island", NamedTextColor.GREEN,
                        TextDecoration.BOLD))
                .body(List.of(DialogBody.plainMessage(Messages.SKYBLOCK.text(
                        "Choose an island type and difficulty.", NamedTextColor.WHITE))))
                .canCloseWithEscape(true).pause(false).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(buttons, null, 2))));
    }

    private static ActionButton button(String label, String tooltip, java.util.function.Consumer<Player> action) {
        return ActionButton.builder(Messages.SKYBLOCK.text(label, NamedTextColor.AQUA))
                .tooltip(Messages.SKYBLOCK.text(tooltip, NamedTextColor.GRAY))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player target) action.accept(target);
                }, ClickCallback.Options.builder().build())).build();
    }
}
