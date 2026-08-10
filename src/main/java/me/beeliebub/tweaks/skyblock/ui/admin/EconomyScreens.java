package me.beeliebub.tweaks.skyblock.ui.admin;

import static me.beeliebub.tweaks.skyblock.ui.admin.AdminScreenContext.targetValue;

import io.papermc.paper.registry.data.dialog.ActionButton;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.SkyblockDescriptions;
import me.beeliebub.tweaks.skyblock.economy.ShopAdminService;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.generator.GeneratorAdminService;
import me.beeliebub.tweaks.skyblock.generator.GeneratorRegistry;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public final class EconomyScreens {
    private final AdminScreenContext context;
    private final JavaPlugin plugin;
    private final SkyblockBootstrap.Runtime runtime;
    private final GeneratorAdminService generatorAdmin;
    private final ShopAdminService shopAdmin;
    private final AdminItemEditor itemEditor;
    private final Consumer<Player> hub;

    EconomyScreens(AdminScreenContext context, Consumer<Player> hub) {
        this.context = context;
        this.plugin = context.plugin;
        this.runtime = context.runtime;
        this.generatorAdmin = context.generatorAdmin;
        this.shopAdmin = context.shopAdmin;
        this.itemEditor = context.itemEditor;
        this.hub = hub;
    }

    public void openGenerators(Player player) {
        if (!guard(player)) return;
        List<ActionButton> buttons = new ArrayList<>();
        for (GeneratorTier tier : runtime.generatorRegistry().tiers()) buttons.add(button(tier.id(), tier.displayName(),
                target -> generatorDetail(target, tier.id())));
        buttons.add(button("Create", "Create a generator tier", target -> generatorInput(target, false)));
        show(player, Messages.SKYBLOCK.text("Generators", NamedTextColor.AQUA), List.of("Weighted output tiers."), buttons, hub);
    }

    private void generatorDetail(Player player, String id) {
        if (!guard(player)) return;
        GeneratorTier tier = runtime.generatorRegistry().tier(id).orElse(null);
        if (tier == null) { openGenerators(player); return; }
        double total = tier.totalWeight();
        List<ActionButton> buttons = new ArrayList<>();
        for (var output : tier.outputs().entrySet()) {
            buttons.add(button(output.getKey().name(), SkyblockDescriptions.generatorOutput(tier, output),
                    target -> generatorOutputDetail(target, id, output.getKey())));
        }
        buttons.add(button("Display name", tier.displayName(), opener -> input(opener, "Generator Display Name",
                List.of("name"), List.of("Current: " + tier.displayName()), (actor, values) -> {
                    report(actor, generatorAdmin.setDisplayName(id, targetValue(values, "name")), "generator tier");
                    generatorDetail(actor, id);
                }, back -> generatorDetail(back, id))));
        buttons.add(button("Add output", "Choose a material and weight", target -> materialPicker(target, "Generator Material",
                material -> generatorOutputWeight(material, target, id), back -> generatorDetail(back, id), 0, "")));
        long references = runtime.islandManager().all().stream()
                .filter(island -> id.equalsIgnoreCase(island.generatorTierId())).count();
                if (!tier.id().equals("default")) buttons.add(button("Delete tier", "Delete this tier", target -> AdminConfirm.open(target,
                "generator " + id, references, "Islands using this tier cannot use it after deletion.", () -> openGenerators(target), actor -> {
                    GeneratorRegistry.DeleteResult result = generatorAdmin.delete(id);
                    report(actor, result, "generator tier");
                    openGenerators(actor);
                }, this::guard)));
        show(player, Messages.SKYBLOCK.text("Generator: " + id, NamedTextColor.AQUA),
                List.of("Weighted outputs; total weight: " + total), buttons, this::openGenerators);
    }

    private void generatorOutputDetail(Player player, String id, Material material) {
        GeneratorTier tier = runtime.generatorRegistry().tier(id).orElse(null);
        if (tier == null || !tier.outputs().containsKey(material)) { generatorDetail(player, id); return; }
        List<ActionButton> buttons = List.of(
                button("Edit weight", "Change the output weight", target -> generatorOutputWeight(material, target, id)),
                button("Remove", "Remove this material from the output table", target -> AdminConfirm.open(target,
                        "generator output " + material.name(), 0, "The tier must retain at least one output.",
                        () -> generatorDetail(target, id), actor -> {
                            report(actor, generatorAdmin.removeOutput(id, material), "generator output");
                            generatorDetail(actor, id);
                        }, this::guard)));
        show(player, Messages.SKYBLOCK.text("Generator Output: " + material.name(), NamedTextColor.AQUA),
                List.of("Weight: " + tier.outputs().get(material)), buttons, target -> generatorDetail(target, id));
    }

    private void generatorOutputWeight(Material material, Player player, String id) {
        input(player, "Generator Output", List.of("weight"), List.of("Material: " + material.name()), (target, values) -> {
            try {
                report(target, generatorAdmin.setOutput(id, material, Double.parseDouble(targetValue(values, "weight"))),
                        "generator output");
            } catch (RuntimeException error) {
                target.sendMessage(Messages.SKYBLOCK.invalidInput("generator output"));
            }
            generatorDetail(target, id);
        }, target -> generatorDetail(target, id));
    }

    private void generatorInput(Player player, boolean edit) {
        input(player, "Generator Tier", List.of("id", "name"), List.of("The new tier starts empty; add an output before using it."), (target, values) -> {
            try {
                String id = targetValue(values, "id");
                report(target, generatorAdmin.create(id, targetValue(values, "name")), "generator tier");
                generatorDetail(target, id);
            } catch (RuntimeException error) { target.sendMessage(Messages.SKYBLOCK.invalidInput("generator tier")); }
        }, this::openGenerators);
    }

    public void openShop(Player player) {
        openShop(player, 0, "");
    }

    private void openShop(Player player, int pageNumber, String filter) {
        if (!guard(player)) return;
        List<ActionButton> buttons = new ArrayList<>();
        List<ShopCatalog.Entry> entries = new ArrayList<>(runtime.shopCatalog().entries());
        AdminPage.Page<ShopCatalog.Entry> page = AdminPage.create(entries, pageNumber, filter,
                entry -> entry.material().name() + " " + entry.category());
        for (ShopCatalog.Entry entry : page.values()) buttons.add(button(entry.material().name(),
                entry.category() + " buy=" + entry.buyPrice() + " sell=" + entry.sellPrice(),
                target -> shopDetail(target, entry.material())));
        if (page.hasPrevious()) buttons.add(button("Previous", "Previous shop page", target -> openShop(target, page.page() - 1, page.filter())));
        if (page.hasNext()) buttons.add(button("Next", "Next shop page", target -> openShop(target, page.page() + 1, page.filter())));
        buttons.add(button("Filter", "Literal material or category filter", target -> input(target, "Shop Filter", List.of("filter"),
                List.of("Filtering is literal text matching."), (actor, values) -> openShop(actor, 0,
                        targetValue(values, "filter")),
                actor -> openShop(actor, page.page(), page.filter()))));
        buttons.add(button("Add material", "Choose a material for a new catalog entry", target ->
                materialPicker(target, "Shop Material", material -> shopEntryInput(target, material),
                        this::openShop, 0, "")));
        buttons.add(button("Add held item", "Use the held material as a new catalog entry", this::addShopHeld));
        show(player, Messages.SKYBLOCK.text("Shop", NamedTextColor.AQUA),
                List.of("-1 means disabled.", "Filter: " + (page.filter().isBlank() ? "none" : page.filter()) + " (" + page.label() + ")"), buttons, hub);
    }

    private void shopDetail(Player player, Material material) {
        if (!guard(player)) return;
        ShopCatalog.Entry entry = runtime.shopCatalog().entry(material).orElse(null);
        if (entry == null) { openShop(player); return; }
        List<ActionButton> actions = new ArrayList<>();
        actions.add(button("Edit prices", "Change category and buy/sell prices", target -> shopEntryInput(target, material)));
        actions.add(button("Delete", "Remove this shop entry", target -> AdminConfirm.open(target,
                "shop " + material.name(), 0, "The material will no longer be buyable or sellable.",
                () -> shopDetail(target, material), actor -> {
                    ShopAdminService.DeleteResult result = shopAdmin.deleteDetailed(material);
                    report(actor, result.success(), "shop entry", result.message());
                    openShop(actor);
                }, this::guard)));
        show(player, Messages.SKYBLOCK.text("Shop: " + material.name(), NamedTextColor.AQUA),
                List.of(SkyblockDescriptions.shopEntry(entry)), actions, this::openShop);
    }

    private void shopEntryInput(Player player, Material material) {
        if (!guard(player)) return;
        ShopCatalog.Entry current = runtime.shopCatalog().entry(material).orElse(null);
        String category = current == null ? "general" : current.category();
        String buy = current == null ? "0" : Double.toString(current.buyPrice());
        String sell = current == null ? "-1" : Double.toString(current.sellPrice());
        input(player, "Shop: " + material.name(), List.of("category", "buy", "sell"),
                List.of("Material: " + material.name(), "Current values: " + category + ", " + buy + ", " + sell,
                        "Use -1 to disable a direction; at least one direction must remain enabled."),
                (target, values) -> {
                    try {
                        report(target, shopAdmin.set(material, targetValue(values, "category"),
                                Double.parseDouble(targetValue(values, "buy")),
                                Double.parseDouble(targetValue(values, "sell"))), "shop entry");
                    } catch (RuntimeException error) {
                        target.sendMessage(Messages.SKYBLOCK.invalidInput("shop entry"));
                    }
                    openShop(target);
                }, this::openShop);
    }

    private void addShopHeld(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput("hold an item first"));
            openShop(player);
            return;
        }
        Material material = held.getType();
        if (runtime.shopCatalog().entry(material).isPresent()) {
            shopDetail(player, material);
            return;
        }
        shopEntryInput(player, material);
    }

    private void materialPicker(Player player, String title, Consumer<Material> selected, Consumer<Player> back,
                                int pageNumber, String filter) {
        if (!guard(player)) return;
        List<Material> materials = Arrays.stream(Material.values()).filter(material -> !material.isAir()).toList();
        AdminPage.Page<Material> page = AdminPage.create(materials, pageNumber, filter, material -> material.name());
        List<ActionButton> buttons = new ArrayList<>();
        for (Material material : page.values()) {
            buttons.add(button(material.name(), "Choose this material", target -> selected.accept(material)));
        }
        if (page.hasPrevious()) {
            buttons.add(button("Previous", "Previous materials",
                    target -> materialPicker(target, title, selected, back, page.page() - 1, filter)));
        }
        if (page.hasNext()) {
            buttons.add(button("Next", "Next materials",
                    target -> materialPicker(target, title, selected, back, page.page() + 1, filter)));
        }
        buttons.add(button("Filter", "Use a literal material-name filter", target -> input(target, "Material Filter",
                List.of("filter"), List.of("Filtering is literal text matching."), (actor, values) ->
                        materialPicker(actor, title, selected, back, 0,
                                AdminPage.literalFilter(targetValue(values, "filter"))),
                actor -> materialPicker(actor, title, selected, back, page.page(), filter))));
        show(player, Messages.SKYBLOCK.text(title + " " + page.label(), NamedTextColor.AQUA),
                List.of("Filter: " + (filter.isBlank() ? "none" : filter)), buttons, back);
    }

    private boolean guard(Player player) { return context.guard(player); }
    private void input(Player player, String title, List<String> keys, List<String> body,
                       AdminScreenContext.InputAction save, Consumer<Player> cancel) {
        context.input(player, title, keys, body, save, cancel);
    }
    private void show(Player player, Component title, List<String> body, List<ActionButton> buttons,
                      Consumer<Player> back) {
        context.show(player, title, body, buttons, back);
    }
    private ActionButton button(String label, String tooltip, Consumer<Player> action) {
        return context.button(label, tooltip, action);
    }
    private void report(Player player, Object result, String subject) {
        context.report(player, result, subject);
    }

    private void report(Player player, boolean success, String subject, String message) {
        player.sendMessage(success ? Messages.SKYBLOCK.saved(subject) : Messages.SKYBLOCK.invalidInput(message));
        context.advanceSetup(player, success);
    }
}
