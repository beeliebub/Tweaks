package me.beeliebub.tweaks.tools.durability;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.tools.augments.AugmentLedger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Opens the repair target dialog and performs one-kit transactional repairs. */
@SuppressWarnings("UnstableApiUsage")
public final class RepairKitListener implements Listener {

    private static final int PAGE_SIZE = 12;
    private final RepairKitItem kitItem;
    private final DurabilityService durability;

    public RepairKitListener(RepairKitItem kitItem, DurabilityService durability) {
        this.kitItem = kitItem;
        this.durability = durability;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        // An air interaction reports itself as cancelled, so ignoreCancelled would skip it.
        if (event.useItemInHand() == Event.Result.DENY) return;
        ItemStack kit = event.getItem();
        if (!kitItem.isKit(kit)) return;
        event.setCancelled(true);
        openDialog(event.getPlayer(), 0);
    }

    public List<Target> targets(Player player) {
        List<Target> targets = new ArrayList<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) add(targets, i, storage[i]);
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) add(targets, 36 + i, armor[i]);
        add(targets, 40, player.getInventory().getItemInOffHand());
        return targets;
    }

    public boolean apply(Player player, int slot) {
        return apply(player, slot, null);
    }

    public boolean apply(Player player, int slot, ItemStack expectedTarget) {
        ItemStack kit = player.getInventory().getItemInMainHand();
        if (!kitItem.isKit(kit)) {
            player.sendMessage(Messages.TOOLS.repairKitNoTarget());
            return false;
        }
        ItemStack target = player.getInventory().getItem(slot);
        if (!AugmentLedger.hasLedger(target)) {
            player.sendMessage(Messages.TOOLS.repairKitRequiresAugmented());
            return false;
        }
        if (!durability.ensureStamped(target) || !durability.isDamageable(target)) {
            player.sendMessage(Messages.TOOLS.repairKitNoDamageableItem());
            return false;
        }
        if (durability.damage(target) <= 0) {
            player.sendMessage(Messages.TOOLS.repairKitAlreadyFull());
            return false;
        }
        if (expectedTarget != null && (target == null || !target.isSimilar(expectedTarget))) {
            player.sendMessage(Messages.TOOLS.repairKitTargetChanged());
            return false;
        }
        if (durability.tier(target) >= maxTier()) {
            player.sendMessage(Messages.TOOLS.repairKitMaxTier());
            return false;
        }
        if (!durability.repair(target)) {
            player.sendMessage(Messages.TOOLS.repairKitRepairFailed());
            return false;
        }
        kit.setAmount(kit.getAmount() - 1);
        player.sendMessage(Messages.TOOLS.repairKitApplied(durability.tier(target), durability.maxTier()));
        return true;
    }

    private void openDialog(Player player, int page) {
        List<Target> targets = targets(player);
        if (targets.isEmpty()) {
            player.sendMessage(Messages.TOOLS.repairKitNoTarget());
            return;
        }
        int totalPages = Math.max(1, (targets.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, targets.size());
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Target target = targets.get(i);
            buttons.add(button(Messages.TOOLS.repairKitTargetName(target.item()),
                    Messages.TOOLS.repairKitTargetTooltip(target.item(), durability.tier(target.item()), maxTier()),
                    p -> apply(p, target.slot(), target.item())));
        }
        if (currentPage > 0) {
            buttons.add(button(Messages.TOOLS.repairKitPreviousPage(),
                    Messages.TOOLS.repairKitPageTooltip(currentPage, totalPages),
                    p -> openDialog(p, currentPage - 1)));
        }
        if (currentPage + 1 < totalPages) {
            buttons.add(button(Messages.TOOLS.repairKitNextPage(),
                    Messages.TOOLS.repairKitPageTooltip(currentPage + 2, totalPages),
                    p -> openDialog(p, currentPage + 1)));
        }
        DialogBase base = DialogBase.builder(Messages.TOOLS.repairKitName())
                .body(List.of(DialogBody.plainMessage(Messages.TOOLS.repairKitDialogBody())))
                .build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(buttons).columns(2).build())));
    }

    private void add(List<Target> targets, int slot, ItemStack item) {
        if (item != null && !item.isEmpty() && AugmentLedger.hasLedger(item)
                && durability.isDamageable(item)) {
            durability.ensureStamped(item);
            if (durability.damage(item) <= 0) return;
            targets.add(new Target(slot, item.clone()));
        }
    }

    private int maxTier() {
        return durability.maxTier();
    }

    private static ActionButton button(Component label, Component tooltip,
                                       java.util.function.Consumer<Player> action) {
        return ActionButton.builder(label).tooltip(tooltip)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player player) action.accept(player);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                .build();
    }

    public record Target(int slot, ItemStack item) {}
}
