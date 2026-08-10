package me.beeliebub.tweaks.skyblock.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.economy.ShopService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Category, paginated-item, and quantity dialogs for buying from the Skyblock shop. */
@SuppressWarnings("UnstableApiUsage")
public final class ShopGUI {
    private static final int PAGE_SIZE = 12;
    private final ShopCatalog catalog;
    private final ShopService service;

    public ShopGUI(ShopCatalog catalog, ShopService service) { this.catalog = catalog; this.service = service; }

    public void open(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        for (String category : new TreeSet<>(catalog.entries().stream().filter(ShopCatalog.Entry::buyable)
                .map(ShopCatalog.Entry::category).toList())) {
            buttons.add(button(category, "View purchasable items",
                    target -> { if (service.canUse(target)) openCategory(target, category, 0); }));
        }
        if (buttons.isEmpty()) buttons.add(button("Shop empty", "No buyable items are configured", ignored -> { }));
        show(player, "Skyblock Shop", List.of("Choose a category."), buttons, null);
    }

    private void openCategory(Player player, String category, int page) {
        List<ShopCatalog.Entry> entries = catalog.category(category).stream().filter(ShopCatalog.Entry::buyable).toList();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        int start = current * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());
        List<ActionButton> buttons = new ArrayList<>();
        for (int index = start; index < end; index++) {
            ShopCatalog.Entry entry = entries.get(index);
            buttons.add(button(entry.material().name(), "Buy for " + entry.buyPrice() + " each",
                    target -> { if (service.canUse(target)) openQuantity(target, category, entry); }));
        }
        if (current > 0) buttons.add(button("Previous", "Previous page", target -> openCategory(target, category, current - 1)));
        if (current + 1 < pages) buttons.add(button("Next", "Next page", target -> openCategory(target, category, current + 1)));
        show(player, "Shop: " + category + " (" + (current + 1) + "/" + pages + ")",
                List.of("Select an item."), buttons, target -> open(target));
    }

    private void openQuantity(Player player, String category, ShopCatalog.Entry entry) {
        List<ActionButton> buttons = new ArrayList<>();
        for (int amount : List.of(1, 16, 64)) {
            buttons.add(button("Buy " + amount, "Total " + entry.buyPrice() * amount,
                    target -> {
                        if (!service.canUse(target)) return;
                        ShopService.BuyResult result = service.buy(target, entry.material(), amount);
                        target.sendMessage(result.success() ? Messages.SKYBLOCK.purchased(entry.material().name(), amount, result.price())
                                : Messages.SKYBLOCK.invalidInput(result.message()));
                        openQuantity(target, category, entry);
                    }));
        }
        show(player, entry.material().name(), List.of("Buy quantity"), buttons,
                target -> openCategory(target, category, 0));
    }

    private static ActionButton button(String label, String tooltip, java.util.function.Consumer<Player> action) {
        return ActionButton.builder(Messages.SKYBLOCK.text(label, NamedTextColor.AQUA))
                .tooltip(Messages.SKYBLOCK.text(tooltip, NamedTextColor.GRAY))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player player) action.accept(player);
                }, ClickCallback.Options.builder().build())).build();
    }

    private static void show(Player player, String title, List<String> lines, List<ActionButton> buttons,
                             java.util.function.Consumer<Player> back) {
        ActionButton backButton = back == null ? null : button("Back", "Return", back);
        DialogBase base = DialogBase.builder(Messages.SKYBLOCK.text(title, NamedTextColor.GREEN, TextDecoration.BOLD))
                .body(lines.stream().map(line -> DialogBody.plainMessage(
                        Messages.SKYBLOCK.text(line, NamedTextColor.WHITE))).toList())
                .canCloseWithEscape(true).pause(false).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(buttons, backButton, 2))));
    }
}
