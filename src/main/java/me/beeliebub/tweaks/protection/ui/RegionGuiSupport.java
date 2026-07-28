package me.beeliebub.tweaks.protection.ui;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.core.ProtectionMessages.Text;
import me.beeliebub.tweaks.protection.region.Region;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Shared Paper Dialog plumbing and region-context lookup for RegionGUI screens. */
@SuppressWarnings("UnstableApiUsage")
final class RegionGuiSupport {

    private RegionGuiSupport() {}

    static ClickCallback.Options unlimitedClicks() {
        return ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();
    }

    static ActionButton dialogButton(Component label, Component tooltip, Consumer<Player> action) {
        return ActionButton.builder(label.decoration(TextDecoration.ITALIC, false))
                .tooltip(tooltip.decoration(TextDecoration.ITALIC, false))
                .action(DialogAction.customClick(
                        (_, audience) -> {
                            if (audience instanceof Player player) {
                                action.accept(player);
                            }
                        },
                        unlimitedClicks()))
                .build();
    }

    static void addPageNavButtons(List<ActionButton> buttons, int currentPage, int totalPages,
                                  Consumer<Player> prevAction, Consumer<Player> nextAction) {
        if (currentPage > 0) {
            buttons.add(dialogButton(
                    Messages.PROTECTION.text(Text.GUI_PREV_PAGE),
                    Messages.PROTECTION.text(Text.GUI_PAGE, currentPage, totalPages),
                    prevAction));
        }
        if (currentPage + 1 < totalPages) {
            buttons.add(dialogButton(
                    Messages.PROTECTION.text(Text.GUI_NEXT_PAGE),
                    Messages.PROTECTION.text(Text.GUI_PAGE, currentPage + 2, totalPages),
                    nextAction));
        }
    }

    static Component pageSummary(int total, Text singular, Text plural, int currentPage, int totalPages) {
        return Messages.PROTECTION.text(Text.GUI_PAGE_SUMMARY, total,
                Messages.PROTECTION.value(total == 1 ? singular : plural),
                currentPage + 1, totalPages).decoration(TextDecoration.ITALIC, false);
    }

    static void openTextInputDialog(Player player, Component title, Component prompt, String inputKey,
                                    Component inputLabel, int maxLength, Component submitTooltip,
                                    BiConsumer<Player, String> onSubmit, Consumer<Player> onCancel) {
        ActionButton submit = ActionButton.builder(
                        Messages.PROTECTION.text(Text.GUI_ADD)
                                .decoration(TextDecoration.ITALIC, false))
                .tooltip(submitTooltip
                        .decoration(TextDecoration.ITALIC, false))
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player actor) {
                                onSubmit.accept(actor, view.getText(inputKey));
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Messages.PROTECTION.text(Text.GUI_CANCEL),
                Messages.PROTECTION.text(Text.GUI_RETURN_NO_CHANGES),
                onCancel);

        DialogBase base = DialogBase.builder(title.decoration(TextDecoration.ITALIC, false))
                .body(List.of(DialogBody.plainMessage(
                        prompt.decoration(TextDecoration.ITALIC, false))))
                .inputs(List.of(
                        DialogInput.text(inputKey,
                                        inputLabel.decoration(TextDecoration.ITALIC, false))
                                .maxLength(maxLength)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.confirmation(submit, cancel)));
        player.showDialog(dialog);
    }

    static Region refreshRegion(ProtectionManager protectionManager, Region stale) {
        if (stale == null) return null;
        if (stale.worldName() != null) {
            World world = Bukkit.getWorld(stale.worldName());
            if (world != null) {
                if (ProtectionManager.GLOBAL_REGION_ID.equals(stale.id())) {
                    return protectionManager.globalRegion(world);
                }
                Region fresh = protectionManager.byName(world, stale.id());
                if (fresh != null) return fresh;
            }
        }
        return protectionManager.byNameAnyWorld(stale.id());
    }

    static World worldOf(Region region) {
        if (region == null || region.worldName() == null) return null;
        return Bukkit.getWorld(region.worldName());
    }

    @SuppressWarnings("deprecation") // Bukkit#getOfflinePlayer(UUID) is the supported sync path.
    static String lookupName(UUID uuid) {
        if (uuid == null) return Messages.PROTECTION.value(Text.UNKNOWN_NAME);
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return (name == null || name.isEmpty()) ? uuid.toString() : name;
    }
}
