package me.beeliebub.tweaks.tools.augments;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.core.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Shared two-screen augment hub used by the command and gem-first entry point. */
@SuppressWarnings("UnstableApiUsage")
public final class AugmentDialog {

    private static final int PAGE_SIZE = 12;
    private final AugmentService augments;

    public AugmentDialog(AugmentService augments) {
        this.augments = augments;
    }

    public void openHeld(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.isEmpty()) {
            player.sendMessage(Messages.TOOLS.augmentRequiresItem());
            return;
        }
        if (!augments.enabled()) {
            player.sendMessage(Messages.TOOLS.featureDisabled("Augments"));
            return;
        }
        if (!augments.ledgerStateValid(held)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return;
        }
        if (AugmentLedger.hasLedger(held) && !augments.ledger().migrated(held)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return;
        }
        if (augments.gemItem().read(held) != null) {
            openGemFirst(player, held);
            return;
        }
        List<ItemStack> migrated = augments.migrateToGems(player, held);
        if (migrated == null) {
            player.sendMessage(Messages.TOOLS.inventoryFull());
            return;
        }
        openHub(player, held);
    }

    public void openHub(Player player, ItemStack item) {
        if (item == null || item.isEmpty()) {
            player.sendMessage(Messages.TOOLS.augmentRequiresItem());
            return;
        }
        if (!augments.ledgerStateValid(item)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return;
        }
        List<ActionButton> buttons = List.of(
                button(Messages.TOOLS.augmentSlotsLabel(), Messages.TOOLS.augmentSlotsBody(
                                augments.slotCalculator().slotDots(augments.ledger().slots(item),
                                        augments.slotCalculator().used(augments.entries(item)),
                                        augments.slotCalculator().capacity(item.getType())),
                                augments.slotCalculator().used(augments.entries(item)),
                                augments.slotCalculator().capacity(item.getType())),
                        p -> openSlots(p, item)),
                button(Messages.TOOLS.augmentListLabel(), Messages.TOOLS.augmentListLabel(),
                        p -> openAugments(p, item)));
        show(player, Messages.TOOLS.augmentHubTitle(), Messages.TOOLS.augmentHubTitle(), buttons, null);
    }

    public void openSlots(Player player, ItemStack item) {
        if (item == null || item.isEmpty()) {
            player.sendMessage(Messages.TOOLS.augmentRequiresItem());
            return;
        }
        if (!augments.ledgerStateValid(item)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return;
        }
        int capacity = augments.slotCalculator().capacity(item.getType());
        int purchased = augments.ledger().slots(item);
        int used = augments.slotCalculator().used(augments.entries(item));
        List<ActionButton> buttons = new ArrayList<>();
        if (purchased < capacity) {
            int next = purchased + 1;
            int price = augments.slotCalculator().price(next);
            if (price >= 0) {
                buttons.add(button(Messages.TOOLS.augmentBuySlot(next, price),
                        Messages.TOOLS.augmentSlotsBody(augments.slotCalculator().slotDots(purchased, used, capacity), used, capacity),
                        p -> {
                            if (!augments.enabled()) {
                                p.sendMessage(Messages.TOOLS.featureDisabled("Augments"));
                                return;
                            }
                            ItemStack target = currentHeldOrSame(p, item);
                            if (target == null) {
                                p.sendMessage(Messages.TOOLS.augmentRequiresItem());
                                return;
                            }
                            augments.purchaseSlot(p, target);
                            openSlots(p, target);
                        }));
            }
        }
        buttons.add(button(Messages.TOOLS.augmentListLabel(), Messages.TOOLS.augmentListLabel(), p -> {
            ItemStack target = currentHeldOrSame(p, item);
            if (target == null) {
                p.sendMessage(Messages.TOOLS.augmentRequiresItem());
                return;
            }
            openAugments(p, target);
        }));
        show(player, Messages.TOOLS.augmentSlotsLabel(),
                Messages.TOOLS.augmentSlotsBody(augments.slotCalculator().slotDots(purchased, used, capacity), used, capacity),
                buttons, p -> openHub(p, currentHeldOrSame(p, item)));
    }

    public void openAugments(Player player, ItemStack item) {
        openAugments(player, item, 0);
    }

    private void openAugments(Player player, ItemStack item, int page) {
        if (item == null || item.isEmpty()) {
            player.sendMessage(Messages.TOOLS.augmentRequiresItem());
            return;
        }
        if (!augments.ledgerStateValid(item)) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return;
        }
        List<AugmentEntry> attached = augments.entries(item);
        List<GemOption> compatibleGems = new ArrayList<>();
        for (AugmentService.GemLocation gem : augments.inventoryGems(player)) {
            AugmentGemItem.GemData data = augments.gemItem().read(gem.item());
            if (data != null && augments.compatibleForDisplay(item, data, attached)) {
                compatibleGems.add(new GemOption(gem, data));
            }
        }
        int totalEntries = attached.size() + compatibleGems.size();
        int totalPages = Math.max(1, (totalEntries + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, totalEntries);
        List<ActionButton> visible = new ArrayList<>();
        for (int option = start; option < end; option++) {
            if (option < attached.size()) {
                visible.add(attachedButton(item, attached.get(option), option, currentPage));
            } else {
                visible.add(gemButton(item, compatibleGems.get(option - attached.size()), currentPage));
            }
        }
        showPaged(player, Messages.TOOLS.augmentListLabel(), visible, totalEntries, page,
                Messages.TOOLS.augmentListLabel(),
                p -> {
                    ItemStack target = currentHeldOrSame(p, item);
                    if (target != null) openHub(p, target);
                },
                (p, nextPage) -> {
                    ItemStack target = currentHeldOrSame(p, item);
                    if (target != null) openAugments(p, target, nextPage);
                });
    }

    public void openGemFirst(Player player, ItemStack gem) {
        openGemFirst(player, gem, 0);
    }

    private void openGemFirst(Player player, ItemStack gem, int page) {
        AugmentGemItem.GemData data = augments.gemItem().read(gem);
        if (data == null) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (AugmentService.GemLocation target : inventoryItems(player)) {
            if (target.item() == gem || sameGem(target.item(), gem)) continue;
            if (!augments.compatibleForDisplay(target.item(), data, augments.entries(target.item()))) continue;
            buttons.add(button(Messages.TOOLS.augmentTargetItem(target.item().getType().name()),
                    gemTooltip(data),
                    p -> {
                        if (!augments.enabled()) {
                            p.sendMessage(Messages.TOOLS.featureDisabled("Augments"));
                            return;
                        }
                        ItemStack currentGem = findSameGem(p, gem);
                        ItemStack currentTarget = getSlot(p, target.slot());
                        if (currentGem == null || currentGem.isEmpty() || currentTarget == null
                                || currentTarget.isEmpty() || !currentTarget.isSimilar(target.item())) return;
                        augments.attach(p, currentTarget, currentGem);
                        openGemFirst(p, currentGem, page);
                    }));
        }
        showPaged(player, Messages.TOOLS.augmentListLabel(), buttons, buttons.size(), page,
                gemTooltip(data),
                null,
                (p, nextPage) -> openGemFirst(p, gem, nextPage));
    }

    private ActionButton attachedButton(ItemStack item, AugmentEntry entry, int index, int page) {
        var enchantment = registry().get(entry.enchantmentKey());
        Component name = enchantment == null
                ? Messages.TOOLS.enchantmentName(entry.enchantmentKey(), entry.level())
                : Messages.TOOLS.enchantmentName(enchantment, entry.level());
        Component label = Messages.TOOLS.augmentEntry(name, entry.active());
        return button(label, label, p -> {
            if (!augments.enabled()) {
                p.sendMessage(Messages.TOOLS.featureDisabled("Augments"));
                return;
            }
            ItemStack target = currentHeldOrSame(p, item);
            if (target == null) {
                p.sendMessage(Messages.TOOLS.augmentRequiresItem());
                return;
            }
            augments.toggle(p, target, index);
            openAugments(p, target, page);
        });
    }

    private ActionButton gemButton(ItemStack item, GemOption option, int page) {
        AugmentService.GemLocation gem = option.location();
        AugmentGemItem.GemData data = option.data();
        Component label = gemLabel(data);
        return button(label, gemTooltip(data), p -> {
            if (!augments.enabled()) {
                p.sendMessage(Messages.TOOLS.featureDisabled("Augments"));
                return;
            }
            ItemStack currentGem = getSlot(p, gem.slot());
            ItemStack target = currentHeldOrSame(p, item);
            if (currentGem == null || currentGem.isEmpty() || !sameGem(currentGem, gem.item()) || target == null) {
                return;
            }
            augments.attach(p, target, currentGem);
            openAugments(p, target, page);
        });
    }

    private Component gemLabel(AugmentGemItem.GemData data) {
        Component name = Messages.TOOLS.enchantmentName(data.enchantment(), data.level());
        return augments.isCurse(data.enchantment())
                ? Messages.TOOLS.augmentInventoryCurseGem(name)
                : Messages.TOOLS.augmentInventoryGem(name);
    }

    private Component gemTooltip(AugmentGemItem.GemData data) {
        Component tooltip = gemLabel(data);
        for (AugmentGemItem.CurseRider rider : data.curses()) {
            tooltip = tooltip.append(Component.newline())
                    .append(Messages.TOOLS.augmentGemRider(
                            Messages.TOOLS.enchantmentName(rider.enchantment(), rider.level())));
        }
        return tooltip;
    }

    private void showPaged(Player player, Component title, List<ActionButton> visibleButtons, int totalEntries, int page,
                           Component body, Consumer<Player> back,
                           java.util.function.BiConsumer<Player, Integer> pageOpener) {
        int totalPages = Math.max(1, (totalEntries + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        List<ActionButton> buttons = new ArrayList<>();
        if (totalEntries == 0) {
            buttons.add(button(Messages.TOOLS.augmentNoGems(), Messages.TOOLS.augmentNoGems(), p -> {}));
        } else {
            buttons.addAll(visibleButtons);
        }
        if (currentPage > 0) {
            buttons.add(button(Messages.TOOLS.augmentPreviousPage(),
                    Messages.TOOLS.augmentPageSummary(currentPage, totalPages, totalEntries),
                    p -> pageOpener.accept(p, currentPage - 1)));
        }
        if (currentPage + 1 < totalPages) {
            buttons.add(button(Messages.TOOLS.augmentNextPage(),
                    Messages.TOOLS.augmentPageSummary(currentPage + 2, totalPages, totalEntries),
                    p -> pageOpener.accept(p, currentPage + 1)));
        }
        Component pageBody = totalPages == 1 ? body
                : body.append(Component.text("\n")
                .append(Messages.TOOLS.augmentPageSummary(currentPage + 1, totalPages, totalEntries)));
        show(player, title, pageBody, buttons, back);
    }

    private static ItemStack currentHeldOrSame(Player player, ItemStack item) {
        ItemStack current = player.getInventory().getItemInMainHand();
        return current == null || current.isEmpty() || item == null || item.isEmpty()
                || !current.isSimilar(item) ? null : current;
    }

    private static ItemStack getSlot(Player player, int slot) {
        return player.getInventory().getItem(slot);
    }

    private ItemStack findSameGem(Player player, ItemStack expected) {
        if (expected == null || expected.isEmpty()) return null;
        for (ItemStack stack : player.getInventory().getStorageContents()) if (sameGem(stack, expected)) return stack;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (sameGem(offhand, expected)) return offhand;
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (sameGem(stack, expected)) return stack;
        }
        return null;
    }

    private boolean sameGem(ItemStack first, ItemStack second) {
        AugmentGemItem.GemData left = augments.gemItem().read(first);
        AugmentGemItem.GemData right = augments.gemItem().read(second);
        return left != null && right != null && left.equals(right);
    }

    private static List<AugmentService.GemLocation> inventoryItems(Player player) {
        List<AugmentService.GemLocation> result = new ArrayList<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) if (storage[i] != null && !storage[i].isEmpty()) result.add(new AugmentService.GemLocation(i, storage[i]));
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) if (armor[i] != null && !armor[i].isEmpty()) result.add(new AugmentService.GemLocation(36 + i, armor[i]));
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && !off.isEmpty()) result.add(new AugmentService.GemLocation(40, off));
        return result;
    }

    private void show(Player player, Component title, Component body, List<ActionButton> buttons,
                      Consumer<Player> back) {
        ActionButton exit = back == null ? null : button(Messages.TOOLS.augmentHubTitle(), Messages.TOOLS.augmentHubTitle(), back);
        DialogBase base = DialogBase.builder(title).body(List.of(DialogBody.plainMessage(body))).build();
        var type = DialogType.multiAction(buttons).columns(2);
        if (exit != null) type.exitAction(exit);
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type.build())));
    }

    private static ActionButton button(Component label, Component tooltip, Consumer<Player> action) {
        return ActionButton.builder(label).tooltip(tooltip)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player player) action.accept(player);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build())).build();
    }

    private org.bukkit.Registry<org.bukkit.enchantments.Enchantment> registry() {
        return io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT);
    }

    private record GemOption(AugmentService.GemLocation location, AugmentGemItem.GemData data) {}
}
