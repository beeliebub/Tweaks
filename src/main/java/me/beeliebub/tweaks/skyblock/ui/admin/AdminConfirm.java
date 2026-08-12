package me.beeliebub.tweaks.skyblock.ui.admin;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.core.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Confirmation dialog factory for irreversible administrator mutations. */
@SuppressWarnings("UnstableApiUsage")
public final class AdminConfirm {
    private AdminConfirm() {
    }

    public static void open(Player player, String target, long references, String consequence,
                            Runnable cancel, Consumer<Player> confirm, Predicate<Player> access) {
        open(player, target, references, consequence, cancel, confirm, access,
                actor -> actor.sendMessage(Messages.SKYBLOCK.adminNoPermission()));
    }

    public static void open(Player player, String target, long references, String consequence,
                            Runnable cancel, Consumer<Player> confirm, Predicate<Player> access,
                            Consumer<Player> denied) {
        if (player == null) return;
        Objects.requireNonNull(access, "access");
        Component reference = Messages.SKYBLOCK.text("References: " + references, NamedTextColor.YELLOW);
        Component backup = Messages.SKYBLOCK.text("A registry backup will be written before the change.", NamedTextColor.GRAY);
        DialogBase base = DialogBase.builder(Messages.SKYBLOCK.text("Confirm " + target, NamedTextColor.RED,
                        TextDecoration.BOLD))
                .body(List.of(DialogBody.plainMessage(Messages.SKYBLOCK.text(consequence, NamedTextColor.WHITE)),
                        DialogBody.plainMessage(reference), DialogBody.plainMessage(backup)))
                .canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE).build();
        AtomicBoolean submitted = new AtomicBoolean();
        ActionButton yes = ActionButton.builder(Messages.SKYBLOCK.text("Confirm", NamedTextColor.RED))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player actor) {
                        if (access.test(actor)) {
                            if (!submitted.compareAndSet(false, true)) {
                                actor.sendMessage(Messages.SKYBLOCK.adminAlreadySubmitted());
                                return;
                            }
                            confirm.accept(actor);
                        }
                        else if (denied != null) denied.accept(actor);
                    }
                }, unlimitedClicks())).build();
        ActionButton no = ActionButton.builder(Messages.SKYBLOCK.text("Cancel", NamedTextColor.GRAY))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player actor) {
                        if (access.test(actor)) {
                            if (!submitted.compareAndSet(false, true)) {
                                actor.sendMessage(Messages.SKYBLOCK.adminAlreadySubmitted());
                                return;
                            }
                            cancel.run();
                        }
                        else if (denied != null) denied.accept(actor);
                    }
                }, unlimitedClicks())).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(List.of(yes, no), null, 2))));
    }

    private static ClickCallback.Options unlimitedClicks() {
        return ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();
    }
}
