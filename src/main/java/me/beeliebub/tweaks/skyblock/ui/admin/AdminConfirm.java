package me.beeliebub.tweaks.skyblock.ui.admin;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Confirmation dialog factory for irreversible administrator mutations. */
@SuppressWarnings("UnstableApiUsage")
public final class AdminConfirm {
    private AdminConfirm() {
    }

    public static void open(Player player, String target, long references, String consequence,
                            Runnable cancel, Consumer<Player> confirm) {
        open(player, target, references, consequence, cancel, confirm,
                actor -> actor.isOnline() && actor.hasPermission(Permissions.ADMIN_SKYBLOCK));
    }

    public static void open(Player player, String target, long references, String consequence,
                            Runnable cancel, Consumer<Player> confirm, Predicate<Player> access) {
        Component reference = Messages.SKYBLOCK.text("References: " + references, NamedTextColor.YELLOW);
        Component backup = Messages.SKYBLOCK.text("A registry backup will be written before the change.", NamedTextColor.GRAY);
        DialogBase base = DialogBase.builder(Messages.SKYBLOCK.text("Confirm " + target, NamedTextColor.RED,
                        TextDecoration.BOLD))
                .body(List.of(DialogBody.plainMessage(Messages.SKYBLOCK.text(consequence, NamedTextColor.WHITE)),
                        DialogBody.plainMessage(reference), DialogBody.plainMessage(backup)))
                .canCloseWithEscape(true).pause(false).build();
        ActionButton yes = ActionButton.builder(Messages.SKYBLOCK.text("Confirm", NamedTextColor.RED))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player actor && access != null && access.test(actor)) confirm.accept(actor);
                }, ClickCallback.Options.builder().build())).build();
        ActionButton no = ActionButton.builder(Messages.SKYBLOCK.text("Cancel", NamedTextColor.GRAY))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player actor && access != null && access.test(actor)) cancel.run();
                }, ClickCallback.Options.builder().build())).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(List.of(yes, no), null, 2))));
    }
}
