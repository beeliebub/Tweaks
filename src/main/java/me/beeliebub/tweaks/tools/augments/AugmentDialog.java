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
        if (AugmentLedger.hasLedger(held)) {
            openHub(player, held);
            return;
        }
        List<ItemStack> legacyGems = augments.computeLegacyGems(held);
        if (legacyGems == null) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return;
        }
        if (!legacyGems.isEmpty()) {
            if (augments.slotCalculator().capacity(held.getType()) < 1) {
                player.sendMessage(Messages.TOOLS.augmentNoSlots());
                return;
            }
            int price = augments.slotCalculator().priceResolution(1).value();
            if (price < 0) {
                player.sendMessage(Messages.TOOLS.augmentConfirmationUnpriced());
                return;
            }
            augments.pendingConfirmations().create(player, held, price);
            player.sendMessage(Messages.TOOLS.augmentMigrationPrompt(price));
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
                button(Messages.TOOLS.augmentListLabel(), Messages.TOOLS.augmentListTooltip(),
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
        buttons.add(button(Messages.TOOLS.augmentListLabel(), Messages.TOOLS.augmentListTooltip(), p -> {
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
        List<ActionButton> buttons = new ArrayList<>();
        for (int option = 0; option < totalEntries; option++) {
            if (option < attached.size()) {
                buttons.add(attachedButton(item, attached.get(option), option));
            } else {
                buttons.add(gemButton(item, compatibleGems.get(option - attached.size())));
            }
        }
        if (buttons.isEmpty()) {
            buttons.add(button(Messages.TOOLS.augmentNoGems(),
                    Messages.TOOLS.augmentNoGemsTooltip(), p -> {}));
        }
        show(player, Messages.TOOLS.augmentListLabel(), Messages.TOOLS.augmentListLabel(), buttons,
                p -> {
                    ItemStack target = currentHeldOrSame(p, item);
                    if (target != null) openHub(p, target);
                });
    }

    public void openGemFirst(Player player, ItemStack gem) {
        AugmentGemItem.GemData data = augments.gemItem().read(gem);
        if (data == null) {
            player.sendMessage(Messages.TOOLS.augmentIncompatible());
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (AugmentService.GemLocation target : inventoryItems(player)) {
            if (target.item() == gem || sameGem(target.item(), gem)) continue;
            if (!augments.compatibleForDisplay(target.item(), data, augments.entries(target.item()))) continue;
            buttons.add(button(Messages.TOOLS.augmentTargetItem(target.item()),
                    toolButtonTooltip(target.item(), data),
                    p -> {
                        if (!augments.enabled()) {
                            p.sendMessage(Messages.TOOLS.featureDisabled("Augments"));
                            return;
                        }
                        ItemStack currentGem = findSameGem(p, gem);
                        ItemStack currentTarget = getSlot(p, target.slot());
                        if (currentGem == null || currentGem.isEmpty() || currentTarget == null
                                || currentTarget.isEmpty() || !currentTarget.isSimilar(target.item())) return;
                        boolean attached = augments.attach(p, currentTarget, currentGem);
                        reopenAfterGemAttach(p, currentTarget, data, attached);
                    }));
        }
        if (buttons.isEmpty()) {
            buttons.add(button(Messages.TOOLS.augmentNoTools(),
                    Messages.TOOLS.augmentNoToolsTooltip(), p -> {}));
        }
        show(player, Messages.TOOLS.augmentListLabel(), gemTooltip(data), buttons, null);
    }

    private ActionButton attachedButton(ItemStack item, AugmentEntry entry, int index) {
        var enchantment = registry().get(entry.enchantmentKey());
        Component name = enchantment == null
                ? Messages.TOOLS.augmentEnchantmentName(entry.enchantmentKey(), entry.level())
                : Messages.TOOLS.augmentEnchantmentName(enchantment, entry.level());
        Component label = Messages.TOOLS.augmentEntry(name, entry.active());
        return button(label, Messages.TOOLS.augmentEntryTooltip(entry.active()), p -> {
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
            openAugments(p, target);
        });
    }

    private ActionButton gemButton(ItemStack item, GemOption option) {
        AugmentService.GemLocation gem = option.location();
        AugmentGemItem.GemData data = option.data();
        Component label = gemLabel(data);
        return button(label, gemTooltip(data).append(Component.newline())
                .append(Messages.TOOLS.augmentGemActionTooltip()), p -> {
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
            openAugments(p, target);
        });
    }

    private Component gemLabel(AugmentGemItem.GemData data) {
        if (augments.isCurse(data.enchantment())) {
            // Curses carry no level in any augment surface, matching the item lore.
            return Messages.TOOLS.augmentInventoryCurseGem(
                    Messages.TOOLS.enchantmentName(data.enchantment()));
        }
        return Messages.TOOLS.augmentInventoryGem(
                Messages.TOOLS.augmentEnchantmentName(data.enchantment(), data.level()));
    }

    /**
     * Tooltip for a candidate tool: what this gem would attach (and any curse riders it carries),
     * followed by the tool's own currently attached augments and bound curses, so a player can
     * distinguish an already-loaded tool from a bare one before committing the attach.
     */
    Component toolButtonTooltip(ItemStack item, AugmentGemItem.GemData data) {
        Component tooltip = gemTooltip(data)
                .append(Component.newline())
                .append(Messages.TOOLS.augmentToolTooltipHeader());
        List<AugmentEntry> entries = augments.entries(item);
        List<AugmentGemItem.CurseRider> curses = augments.ledger().curses(item);
        if (entries.isEmpty() && curses.isEmpty()) {
            return tooltip.append(Component.newline()).append(Messages.TOOLS.augmentToolTooltipNone());
        }
        for (AugmentEntry entry : entries) {
            var enchantment = registry().get(entry.enchantmentKey());
            Component name = enchantment == null
                    ? Messages.TOOLS.augmentEnchantmentName(entry.enchantmentKey(), entry.level())
                    : Messages.TOOLS.augmentEnchantmentName(enchantment, entry.level());
            tooltip = tooltip.append(Component.newline())
                    .append(Messages.TOOLS.augmentToolTooltipEntry(
                            Messages.TOOLS.augmentEntry(name, entry.active())));
        }
        for (AugmentGemItem.CurseRider curse : curses) {
            tooltip = tooltip.append(Component.newline())
                    .append(Messages.TOOLS.augmentToolTooltipEntry(
                            Messages.TOOLS.augmentCurseLore(Messages.TOOLS.enchantmentName(curse.enchantment()))));
        }
        return tooltip;
    }

    private Component gemTooltip(AugmentGemItem.GemData data) {
        Component tooltip = gemLabel(data);
        for (AugmentGemItem.CurseRider rider : data.curses()) {
            tooltip = tooltip.append(Component.newline())
                    .append(Messages.TOOLS.augmentGemRider(
                            Messages.TOOLS.enchantmentName(rider.enchantment())));
        }
        return tooltip;
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

    /**
     * Chooses the follow-up screen after a gem-first attach attempt. A successful attach consumes
     * one gem, so re-opening the gem-first screen is only correct while a usable gem of this type
     * is still in the inventory; reopening it against an emptied stack would read no gem data and
     * wrongly report the gem as incompatible. When the last matching gem was just spent, the item's
     * augment list is shown instead so the new entry is visible.
     */
    void reopenAfterGemAttach(Player player, ItemStack target, AugmentGemItem.GemData gemData,
                              boolean attached) {
        ItemStack remainingGem = findGemMatching(player, gemData);
        if (remainingGem != null) {
            openGemFirst(player, remainingGem);
        } else if (attached) {
            openAugments(player, target);
        }
    }

    /** Compatibility overload for callers that used the former page argument. */
    void reopenAfterGemAttach(Player player, ItemStack target, AugmentGemItem.GemData gemData,
                              boolean attached, int ignoredPage) {
        reopenAfterGemAttach(player, target, gemData, attached);
    }

    private ItemStack findGemMatching(Player player, AugmentGemItem.GemData data) {
        if (data == null) return null;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (gemMatches(stack, data)) return stack;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (gemMatches(offhand, data)) return offhand;
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (gemMatches(stack, data)) return stack;
        }
        return null;
    }

    private boolean gemMatches(ItemStack stack, AugmentGemItem.GemData data) {
        AugmentGemItem.GemData candidate = augments.gemItem().read(stack);
        return candidate != null && candidate.equals(data);
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
        ActionButton exit = back == null ? null : button(Messages.TOOLS.augmentHubTitle(),
                Messages.TOOLS.augmentReturnToHubTooltip(), back);
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
