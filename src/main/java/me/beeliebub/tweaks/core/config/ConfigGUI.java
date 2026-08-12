package me.beeliebub.tweaks.core.config;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.core.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code /tconfig}'s Paper Dialog hierarchy: MAIN (category picker) -&gt; CATEGORY (setting list)
 * -&gt; per-{@link EditorType} leaf. Ported from {@code permissions/PermissionGUI}'s idiom - see
 * that class for the pattern this mirrors. Every mutation delegates to {@link ConfigValueEditor};
 * this class holds no parse/validate/write logic of its own.
 *
 * <p>Untested: MockBukkit cannot construct {@code Dialog}/{@code ActionButton} (no
 * {@code DialogInstancesProvider} service), so this dialog-open path is a documented MockBukkit
 * boundary, same as {@code PermissionGUI}/{@code RegionGUI}. {@link ConfigValueEditor} is the
 * unit-testable core this class calls into.
 */
@SuppressWarnings("UnstableApiUsage") // Paper's Dialog API is @ApiStatus.Experimental in 26.2.
public final class ConfigGUI {

    private ConfigGUI() {}

    private static final int DIALOG_PAGE_SIZE = 12;
    private static final int DIALOG_COLUMNS = 2;

    // ------------------------------------------------------------------ Main

    public static void openMainMenu(Player player, ConfigValueEditor valueEditor) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(dialogButton(
                Messages.CONFIG.worldProfileCategoryLabel(),
                Messages.CONFIG.worldProfileCategoryTooltip(),
                p -> openWorldProfileList(p, valueEditor, 0)));
        for (ConfigCategory category : ConfigRegistry.mainMenuCategories()) {
            buttons.add(dialogButton(
                    Messages.CONFIG.categoryLabel(category.displayName()),
                    Messages.CONFIG.categoryTooltip(category.settings().size()),
                    p -> openCategory(p, valueEditor, category.key(), 0)));
        }
        buttons.add(dialogButton(
                Messages.CONFIG.loggingCategoryLabel(),
                Messages.CONFIG.loggingCategoryTooltip(),
                p -> openLoggingMenu(p, valueEditor, 0)));

        DialogBase base = DialogBase.builder(Messages.CONFIG.mainTitle())
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.mainBody())))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(DIALOG_COLUMNS).build()));

        player.showDialog(dialog);
    }

    // --------------------------------------------------------------- Category

    public static void openCategory(Player player, ConfigValueEditor valueEditor, String categoryKey, int page) {
        ConfigCategory category = ConfigRegistry.category(categoryKey).orElse(null);
        if (category == null) {
            player.sendMessage(Messages.CONFIG.unknownCategory(categoryKey));
            openMainMenu(player, valueEditor);
            return;
        }

        List<ConfigSetting> settings = category.settings();
        int totalPages = Math.max(1, (settings.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, settings.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            ConfigSetting setting = settings.get(i);
            String currentValue = valueEditor.currentValueDisplay(setting);
            buttons.add(dialogButton(
                    Messages.CONFIG.settingLabel(setting.displayName()),
                    Messages.CONFIG.settingTooltip(currentValue),
                    p -> openSetting(p, valueEditor, categoryKey, setting.path(), 0)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openCategory(target, valueEditor, categoryKey, currentPage - 1),
                target -> openCategory(target, valueEditor, categoryKey, currentPage + 1));

        ActionButton back = dialogButton(
                Messages.CONFIG.backLabel(),
                category.group() == ConfigCategory.Group.LOGGING
                        ? Messages.CONFIG.backToLoggingTooltip()
                        : Messages.CONFIG.backToMainTooltip(),
                p -> openCategoryParent(p, valueEditor, category));

        DialogBase base = DialogBase.builder(Messages.CONFIG.categoryTitle(category.displayName()))
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.categoryBody())))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    // --------------------------------------------------------------- Logging

    public static void openLoggingMenu(Player player, ConfigValueEditor valueEditor, int page) {
        List<ConfigCategory> categories = ConfigRegistry.loggingCategories();
        int totalPages = Math.max(1, (categories.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, categories.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            ConfigCategory category = categories.get(i);
            buttons.add(dialogButton(
                    Messages.CONFIG.categoryLabel(category.displayName()),
                    Messages.CONFIG.categoryTooltip(category.settings().size()),
                    p -> openCategory(p, valueEditor, category.key(), 0)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openLoggingMenu(target, valueEditor, currentPage - 1),
                target -> openLoggingMenu(target, valueEditor, currentPage + 1));

        ActionButton back = dialogButton(
                Messages.CONFIG.backLabel(),
                Messages.CONFIG.backToMainTooltip(),
                p -> openMainMenu(p, valueEditor));

        DialogBase base = DialogBase.builder(Messages.CONFIG.loggingTitle())
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.loggingBody())))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void openCategoryParent(Player player, ConfigValueEditor valueEditor,
                                           ConfigCategory category) {
        if (category.group() == ConfigCategory.Group.LOGGING) {
            openLoggingMenu(player, valueEditor, 0);
        } else {
            openMainMenu(player, valueEditor);
        }
    }

    // ---------------------------------------------------------------- Setting

    private static void openSetting(Player player, ConfigValueEditor valueEditor, String categoryKey, String path, int page) {
        ConfigSetting setting = ConfigRegistry.byPath(path).orElse(null);
        if (setting == null) {
            player.sendMessage(Messages.CONFIG.unknownSetting(path));
            openCategory(player, valueEditor, categoryKey, 0);
            return;
        }

        switch (setting.type()) {
            case BOOLEAN -> openBooleanEditor(player, valueEditor, categoryKey, setting);
            case STRING_LIST, WORLD_KEY_LIST, MOB_LIST, MATERIAL_LIST ->
                    openListEditor(player, valueEditor, categoryKey, setting, page);
            case NUMBER_MAP -> openMapEditor(player, valueEditor, categoryKey, setting);
            default -> openScalarEditor(player, valueEditor, categoryKey, setting);
        }
    }

    private static void openScalarEditor(Player player, ConfigValueEditor valueEditor, String categoryKey, ConfigSetting setting) {
        String currentValue = valueEditor.currentValueDisplay(setting);

        ActionButton save = ActionButton.builder(
                        Messages.CONFIG.saveLabel().decoration(TextDecoration.ITALIC, false))
                .tooltip(Messages.CONFIG.saveTooltip())
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                EditResult result = valueEditor.applyScalar(setting, view.getText("value"));
                                p.sendMessage(result.message());
                                openCategory(p, valueEditor, categoryKey, 0);
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Messages.CONFIG.cancelLabel(),
                Messages.CONFIG.cancelTooltip(),
                p -> openCategory(p, valueEditor, categoryKey, 0));

        DialogBase base = DialogBase.builder(Messages.CONFIG.editTitle(setting.displayName()))
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.editBody(currentValue))))
                .inputs(List.of(
                        DialogInput.text("value", Messages.CONFIG.valueInputLabel())
                                .maxLength(64)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(save, cancel)));

        player.showDialog(dialog);
    }

    private static void openBooleanEditor(Player player, ConfigValueEditor valueEditor, String categoryKey, ConfigSetting setting) {
        ActionButton trueButton = dialogButton(
                Messages.CONFIG.settingLabel("True"), Messages.CONFIG.settingTooltip("true"),
                p -> {
                    p.sendMessage(valueEditor.applyScalar(setting, "true").message());
                    openCategory(p, valueEditor, categoryKey, 0);
                });
        ActionButton falseButton = dialogButton(
                Messages.CONFIG.settingLabel("False"), Messages.CONFIG.settingTooltip("false"),
                p -> {
                    p.sendMessage(valueEditor.applyScalar(setting, "false").message());
                    openCategory(p, valueEditor, categoryKey, 0);
                });
        ActionButton back = dialogButton(
                Messages.CONFIG.backToCategoryLabel(),
                Messages.CONFIG.backToMainTooltip(),
                p -> openCategory(p, valueEditor, categoryKey, 0));

        DialogBase base = DialogBase.builder(Messages.CONFIG.editTitle(setting.displayName()))
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.editBody(valueEditor.currentValueDisplay(setting)))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(trueButton, falseButton))
                        .columns(2)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void openListEditor(Player player, ConfigValueEditor valueEditor, String categoryKey, ConfigSetting setting, int page) {
        List<String> values = valueEditor.currentListValues(setting);

        int totalPages = Math.max(1, (values.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, values.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String value = values.get(i);
            buttons.add(dialogButton(
                    Messages.CONFIG.listEntryLabel(value),
                    Messages.CONFIG.listEntryTooltip(),
                    p -> {
                        p.sendMessage(valueEditor.listRemove(setting, value).message());
                        openListEditor(p, valueEditor, categoryKey, setting, currentPage);
                    }));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openListEditor(target, valueEditor, categoryKey, setting, currentPage - 1),
                target -> openListEditor(target, valueEditor, categoryKey, setting, currentPage + 1));

        buttons.add(dialogButton(
                Messages.CONFIG.addEntryLabel(),
                Messages.CONFIG.addEntryTooltip(),
                p -> openAddEntryDialog(p, valueEditor, categoryKey, setting)));

        ActionButton back = dialogButton(
                Messages.CONFIG.backToCategoryLabel(),
                Messages.CONFIG.backToMainTooltip(),
                p -> openCategory(p, valueEditor, categoryKey, 0));

        DialogBase base = DialogBase.builder(Messages.CONFIG.editTitle(setting.displayName()))
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.categoryBody())))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void openAddEntryDialog(Player player, ConfigValueEditor valueEditor, String categoryKey, ConfigSetting setting) {
        ActionButton add = ActionButton.builder(
                        Messages.CONFIG.saveLabel().decoration(TextDecoration.ITALIC, false))
                .tooltip(Messages.CONFIG.saveTooltip())
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                EditResult result = valueEditor.listAdd(setting, view.getText("value"));
                                p.sendMessage(result.message());
                                openListEditor(p, valueEditor, categoryKey, setting, 0);
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Messages.CONFIG.cancelLabel(),
                Messages.CONFIG.cancelTooltip(),
                p -> openListEditor(p, valueEditor, categoryKey, setting, 0));

        DialogBase base = DialogBase.builder(Messages.CONFIG.addEntryTitle(setting.displayName()))
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.addEntryBody())))
                .inputs(List.of(
                        DialogInput.text("value", Messages.CONFIG.addEntryInputLabel())
                                .maxLength(64)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(add, cancel)));

        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------ NUMBER_MAP

    private static void openMapEditor(Player player, ConfigValueEditor valueEditor, String categoryKey, ConfigSetting setting) {
        List<String> keys = valueEditor.currentMapKeys(setting);
        List<String> labels = valueEditor.currentMapEntryLabels(setting);

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            buttons.add(dialogButton(
                    Messages.CONFIG.mapEntryLabel(labels.get(i)),
                    Messages.CONFIG.mapEntryTooltip(),
                    p -> openMapEntryEditor(p, valueEditor, categoryKey, setting, key)));
        }

        buttons.add(dialogButton(
                Messages.CONFIG.addMapEntryLabel(),
                Messages.CONFIG.addMapEntryTooltip(),
                p -> openAddMapEntryDialog(p, valueEditor, categoryKey, setting)));

        ActionButton back = dialogButton(
                Messages.CONFIG.backToCategoryLabel(),
                Messages.CONFIG.backToMainTooltip(),
                p -> openCategory(p, valueEditor, categoryKey, 0));

        DialogBase base = DialogBase.builder(Messages.CONFIG.editTitle(setting.displayName()))
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.categoryBody())))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void openMapEntryEditor(Player player, ConfigValueEditor valueEditor, String categoryKey, ConfigSetting setting, String key) {
        ActionButton save = ActionButton.builder(
                        Messages.CONFIG.saveLabel().decoration(TextDecoration.ITALIC, false))
                .tooltip(Messages.CONFIG.saveTooltip())
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                EditResult result = valueEditor.mapPut(setting, key, view.getText("value"));
                                p.sendMessage(result.message());
                                openMapEditor(p, valueEditor, categoryKey, setting);
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Messages.CONFIG.cancelLabel(),
                Messages.CONFIG.cancelTooltip(),
                p -> openMapEditor(p, valueEditor, categoryKey, setting));

        DialogBase base = DialogBase.builder(Messages.CONFIG.editMapEntryTitle(setting.displayName(), key))
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.editBody(valueEditor.currentMapEntryValue(setting, key)))))
                .inputs(List.of(
                        DialogInput.text("value", Messages.CONFIG.valueInputLabel())
                                .maxLength(64)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(save, cancel)));

        player.showDialog(dialog);
    }

    private static void openAddMapEntryDialog(Player player, ConfigValueEditor valueEditor, String categoryKey, ConfigSetting setting) {
        ActionButton add = ActionButton.builder(
                        Messages.CONFIG.saveLabel().decoration(TextDecoration.ITALIC, false))
                .tooltip(Messages.CONFIG.saveTooltip())
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                EditResult result = valueEditor.mapPut(setting, view.getText("key"), view.getText("value"));
                                p.sendMessage(result.message());
                                openMapEditor(p, valueEditor, categoryKey, setting);
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Messages.CONFIG.cancelLabel(),
                Messages.CONFIG.cancelTooltip(),
                p -> openMapEditor(p, valueEditor, categoryKey, setting));

        DialogBase base = DialogBase.builder(Messages.CONFIG.addMapEntryTitle(setting.displayName()))
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.addMapEntryBody())))
                .inputs(List.of(
                        DialogInput.text("key", Messages.CONFIG.mapKeyInputLabel())
                                .maxLength(32)
                                .build(),
                        DialogInput.text("value", Messages.CONFIG.addEntryInputLabel())
                                .maxLength(64)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(add, cancel)));

        player.showDialog(dialog);
    }

    // ------------------------------------------------------------------ World Profiles
    // Bespoke leaf, not a ConfigRegistry category - see core/config/CLAUDE.md's "world-profiles"
    // section for why this is special-cased like eggdrop/spawneregg/resourceitems rather than a
    // generic EditorType.

    private static void openWorldProfileList(Player player, ConfigValueEditor valueEditor, int page) {
        List<String> keys = valueEditor.worldProfileKeys();

        int totalPages = Math.max(1, (keys.size() + DIALOG_PAGE_SIZE - 1) / DIALOG_PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * DIALOG_PAGE_SIZE;
        int end = Math.min(start + DIALOG_PAGE_SIZE, keys.size());

        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String key = keys.get(i);
            buttons.add(dialogButton(
                    Messages.CONFIG.worldProfileEntryLabel(valueEditor.worldProfileEntryDisplay(key)),
                    Messages.CONFIG.worldProfileEntryTooltip(),
                    p -> openWorldProfileEntry(p, valueEditor, key)));
        }

        addPageNavButtons(buttons, currentPage, totalPages,
                target -> openWorldProfileList(target, valueEditor, currentPage - 1),
                target -> openWorldProfileList(target, valueEditor, currentPage + 1));

        buttons.add(dialogButton(
                Messages.CONFIG.addEntryLabel(),
                Messages.CONFIG.worldProfileAddTooltip(),
                p -> openAddWorldProfileDialog(p, valueEditor)));

        ActionButton back = dialogButton(
                Messages.CONFIG.backLabel(),
                Messages.CONFIG.backToMainTooltip(),
                p -> openMainMenu(p, valueEditor));

        DialogBase base = DialogBase.builder(Messages.CONFIG.worldProfileListTitle())
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.categoryBody())))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons)
                        .columns(DIALOG_COLUMNS)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    private static void openWorldProfileEntry(Player player, ConfigValueEditor valueEditor, String worldKey) {
        ActionButton edit = dialogButton(
                Messages.CONFIG.worldProfileEditButtonLabel(),
                Messages.CONFIG.worldProfileEditButtonTooltip(),
                p -> openEditWorldProfileDialog(p, valueEditor, worldKey));

        // Immediate on click, no confirmation - mirrors PermissionGUI's Delete Group and this
        // package's own list-entry removal. Removing the last entry is legal (see
        // ConfigValueEditor#worldProfileRemove / WorldProfileTable's class Javadoc).
        ActionButton remove = dialogButton(
                Messages.CONFIG.worldProfileRemoveButtonLabel(),
                Messages.CONFIG.worldProfileRemoveButtonTooltip(),
                p -> {
                    p.sendMessage(valueEditor.worldProfileRemove(worldKey).message());
                    openWorldProfileList(p, valueEditor, 0);
                });

        ActionButton back = dialogButton(
                Messages.CONFIG.backLabel(),
                Messages.CONFIG.backToMainTooltip(),
                p -> openWorldProfileList(p, valueEditor, 0));

        DialogBase base = DialogBase.builder(Messages.CONFIG.worldProfileEntryTitle(worldKey))
                .body(List.of(DialogBody.plainMessage(
                        Messages.CONFIG.worldProfileEntryBody(valueEditor.worldProfileEntryDisplay(worldKey)))))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(edit, remove))
                        .columns(2)
                        .exitAction(back)
                        .build()));

        player.showDialog(dialog);
    }

    /** Edits only tag/color - the entry's profile is shown as read-only body text, never an input field. */
    private static void openEditWorldProfileDialog(Player player, ConfigValueEditor valueEditor, String worldKey) {
        ActionButton save = ActionButton.builder(
                        Messages.CONFIG.saveLabel().decoration(TextDecoration.ITALIC, false))
                .tooltip(Messages.CONFIG.saveTooltip())
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                EditResult result = valueEditor.worldProfileEdit(
                                        worldKey, view.getText("label"), view.getText("color"));
                                p.sendMessage(result.message());
                                openWorldProfileEntry(p, valueEditor, worldKey);
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Messages.CONFIG.cancelLabel(),
                Messages.CONFIG.cancelTooltip(),
                p -> openWorldProfileEntry(p, valueEditor, worldKey));

        DialogBase base = DialogBase.builder(Messages.CONFIG.worldProfileEditTitle(worldKey))
                .body(List.of(DialogBody.plainMessage(
                        Messages.CONFIG.worldProfileEntryBody(valueEditor.worldProfileEntryDisplay(worldKey)))))
                .inputs(List.of(
                        DialogInput.text("label", Messages.CONFIG.worldProfileLabelInputLabel())
                                .maxLength(32)
                                .build(),
                        DialogInput.text("color", Messages.CONFIG.worldProfileColorInputLabel())
                                .maxLength(32)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(save, cancel)));

        player.showDialog(dialog);
    }

    /** Add requires profile up front - there's no existing data to orphan for a world key that never existed. */
    private static void openAddWorldProfileDialog(Player player, ConfigValueEditor valueEditor) {
        ActionButton add = ActionButton.builder(
                        Messages.CONFIG.saveLabel().decoration(TextDecoration.ITALIC, false))
                .tooltip(Messages.CONFIG.saveTooltip())
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                EditResult result = valueEditor.worldProfileAdd(
                                        view.getText("world_key"), view.getText("profile"),
                                        view.getText("label"), view.getText("color"));
                                p.sendMessage(result.message());
                                openWorldProfileList(p, valueEditor, 0);
                            }
                        },
                        unlimitedClicks()))
                .build();

        ActionButton cancel = dialogButton(
                Messages.CONFIG.cancelLabel(),
                Messages.CONFIG.cancelTooltip(),
                p -> openWorldProfileList(p, valueEditor, 0));

        DialogBase base = DialogBase.builder(Messages.CONFIG.worldProfileAddTitle())
                .body(List.of(DialogBody.plainMessage(Messages.CONFIG.worldProfileAddBody())))
                .inputs(List.of(
                        DialogInput.text("world_key", Messages.CONFIG.worldProfileWorldKeyInputLabel())
                                .maxLength(64)
                                .build(),
                        DialogInput.text("profile", Messages.CONFIG.worldProfileProfileInputLabel())
                                .maxLength(32)
                                .build(),
                        DialogInput.text("label", Messages.CONFIG.worldProfileLabelInputLabel())
                                .maxLength(32)
                                .build(),
                        DialogInput.text("color", Messages.CONFIG.worldProfileColorInputLabel())
                                .maxLength(32)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(add, cancel)));

        player.showDialog(dialog);
    }

    // -------------------------------------------------------------- Helpers

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

    private static void addPageNavButtons(List<ActionButton> buttons, int currentPage, int totalPages,
                                          Consumer<Player> prevAction, Consumer<Player> nextAction) {
        if (currentPage > 0) {
            buttons.add(dialogButton(
                    Messages.CONFIG.previousPageLabel(),
                    Messages.CONFIG.pageTooltip(currentPage, totalPages),
                    prevAction));
        }
        if (currentPage + 1 < totalPages) {
            buttons.add(dialogButton(
                    Messages.CONFIG.nextPageLabel(),
                    Messages.CONFIG.pageTooltip(currentPage + 2, totalPages),
                    nextAction));
        }
    }
}
