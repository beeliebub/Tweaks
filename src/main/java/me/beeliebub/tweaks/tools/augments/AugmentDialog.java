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
        int capacity = augments.slotCalculator().capacity(item.getType());
        int purchased = augments.ledger().slots(item);
        int used = augments.slotCalculator().used(augments.entries(item));
        List<ActionButton> buttons = new ArrayList<>();
        if (purchased < capacity) {
            int next = purchased + 1;
            buttons.add(button(Messages.TOOLS.augmentBuySlot(next, augments.slotCalculator().price(next)),
                    Messages.TOOLS.augmentSlotsBody(augments.slotCalculator().slotDots(purchased, used, capacity), used, capacity),
                    p -> {
                        augments.purchaseSlot(p, currentHeldOrSame(p, item));
                        openSlots(p, currentHeldOrSame(p, item));
                    }));
        }
        buttons.add(button(Messages.TOOLS.augmentListLabel(), Messages.TOOLS.augmentListLabel(), p -> openAugments(p, currentHeldOrSame(p, item))));
        show(player, Messages.TOOLS.augmentSlotsLabel(),
                Messages.TOOLS.augmentSlotsBody(augments.slotCalculator().slotDots(purchased, used, capacity), used, capacity),
                buttons, p -> openHub(p, currentHeldOrSame(p, item)));
    }

    public void openAugments(Player player, ItemStack item) {
        openAugments(player, item, 0);
    }

    private void openAugments(Player player, ItemStack item, int page) {
        List<ActionButton> buttons = new ArrayList<>();
        List<AugmentEntry> attached = augments.entries(item);
        for (int i = 0; i < attached.size(); i++) {
            int index = i;
            AugmentEntry entry = attached.get(i);
            var enchantment = registry().get(entry.enchantmentKey());
            String name = enchantment == null ? entry.enchantmentKey().toString() : enchantment.getKey().getKey();
            buttons.add(button(Messages.TOOLS.augmentEntry(name, entry.level(), entry.active()),
                    Messages.TOOLS.augmentEntry(name, entry.level(), entry.active()),
                    p -> {
                        augments.toggle(p, currentHeldOrSame(p, item), index);
                        openAugments(p, currentHeldOrSame(p, item), page);
                    }));
        }
        for (AugmentService.GemLocation gem : augments.inventoryGems(player)) {
            AugmentGemItem.GemData data = augments.gemItem().read(gem.item());
            if (data == null || !augments.compatibleForDisplay(item, data.enchantment(), attached)) continue;
            buttons.add(button(Messages.TOOLS.augmentInventoryGem(data.enchantment().getKey().toString(), data.level()),
                    Messages.TOOLS.augmentInventoryGem(data.enchantment().getKey().toString(), data.level()),
                    p -> {
                        ItemStack currentGem = getSlot(p, gem.slot());
                        augments.attach(p, currentHeldOrSame(p, item), currentGem);
                        openAugments(p, currentHeldOrSame(p, item), page);
                    }));
        }
        showPaged(player, Messages.TOOLS.augmentListLabel(), buttons, page,
                Messages.TOOLS.augmentListLabel(),
                p -> openHub(p, currentHeldOrSame(p, item)),
                (p, nextPage) -> openAugments(p, currentHeldOrSame(p, item), nextPage));
    }

    public void openGemFirst(Player player, ItemStack gem) {
        openGemFirst(player, gem, 0);
    }

    private void openGemFirst(Player player, ItemStack gem, int page) {
        AugmentGemItem.GemData data = augments.gemItem().read(gem);
        if (data == null) return;
        List<ActionButton> buttons = new ArrayList<>();
        for (AugmentService.GemLocation target : inventoryItems(player)) {
            if (target.item() == gem || target.item().isSimilar(gem)) continue;
            if (!augments.compatibleForDisplay(target.item(), data.enchantment(), augments.entries(target.item()))) continue;
            buttons.add(button(Messages.TOOLS.augmentEntry(target.item().getType().name(), 1, true),
                    Messages.TOOLS.augmentInventoryGem(data.enchantment().getKey().toString(), data.level()),
                    p -> {
                        ItemStack currentGem = findSameGem(p, gem);
                        augments.attach(p, getSlot(p, target.slot()), currentGem);
                        openGemFirst(p, currentGem, page);
                    }));
        }
        showPaged(player, Messages.TOOLS.augmentListLabel(), buttons, page,
                Messages.TOOLS.augmentInventoryGem(data.enchantment().getKey().toString(), data.level()),
                null,
                (p, nextPage) -> openGemFirst(p, gem, nextPage));
    }

    private void showPaged(Player player, Component title, List<ActionButton> allButtons, int page,
                           Component body, Consumer<Player> back,
                           java.util.function.BiConsumer<Player, Integer> pageOpener) {
        int totalEntries = allButtons.size();
        int totalPages = Math.max(1, (totalEntries + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        List<ActionButton> buttons = new ArrayList<>();
        if (totalEntries == 0) {
            buttons.add(button(Messages.TOOLS.augmentNoGems(), Messages.TOOLS.augmentNoGems(), p -> {}));
        } else {
            int start = currentPage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, totalEntries);
            buttons.addAll(allButtons.subList(start, end));
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
        return current == null || current.isEmpty() ? item : current;
    }

    private static ItemStack getSlot(Player player, int slot) {
        return player.getInventory().getItem(slot);
    }

    private static ItemStack findSameGem(Player player, ItemStack expected) {
        for (ItemStack stack : player.getInventory().getStorageContents()) if (stack != null && stack.isSimilar(expected)) return stack;
        if (player.getInventory().getItemInOffHand().isSimilar(expected)) return player.getInventory().getItemInOffHand();
        for (ItemStack stack : player.getInventory().getArmorContents()) if (stack != null && stack.isSimilar(expected)) return stack;
        return expected;
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
}
