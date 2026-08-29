package me.beeliebub.tweaks.core;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.utils.ColorUtil;
import me.beeliebub.tweaks.permissions.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

// Unified help system: data records, the static article content, the /help executor
// (Paper Dialog rendering), and the join-tip listener — all in one cohesive module.
public class HelpSystem implements CommandExecutor, TabCompleter, Listener {

    // ============================================================
    // Data records
    // ============================================================

    public record HelpArticle(
            String id,
            String title,
            List<Component> content,
            Material icon,
            int slot,
            String gradient,
            List<String> relatedArticles,
            String permission
    ) {
        public HelpArticle {
            content = List.copyOf(content);
            relatedArticles = relatedArticles == null ? List.of() : List.copyOf(relatedArticles);
        }

        public HelpArticle(String id, String title, List<Component> content, Material icon, int slot,
                           String gradient, List<String> relatedArticles) {
            this(id, title, content, icon, slot, gradient, relatedArticles, null);
        }
    }

    public record HelpCategory(
            String id,
            String title,
            List<HelpArticle> articles,
            Material icon,
            int slot,
            String gradient
    ) {
        public HelpCategory {
            articles = List.copyOf(articles);
        }

        public boolean hasVisibleArticles(Player player) {
            for (HelpArticle article : articles) {
                if (article.permission() == null || player.hasPermission(article.permission())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final MiniMessage MM = Messages.MM;
    private static final int BUTTON_WIDTH = 250;

    private final HelpCatalog catalog;
    private final HelpCommandRouter commandRouter;

    public HelpSystem() {
        this(Logger.getLogger("Tweaks"));
    }

    public HelpSystem(Logger logger) {
        this.catalog = new HelpCatalog(logger);
        this.commandRouter = new HelpCommandRouter(this);
        catalog.add(buildTeleportation());
        catalog.add(buildEnchantments());
        catalog.add(buildQuality());
        catalog.add(buildFeatures());
        catalog.add(buildMinigames());
        catalog.add(buildPermissions());
        catalog.add(buildProtection());
        catalog.add(buildEconomy());
        catalog.validateCrossReferences();
    }

    public Collection<HelpCategory> getCategories() {
        return catalog.categories();
    }

    public HelpCategory getCategory(String id) {
        return catalog.category(id);
    }

    public HelpArticle getArticle(String id) {
        return catalog.article(id);
    }

    public HelpArticle getRandomArticle(Player player) {
        return catalog.randomVisibleArticle(player);
    }

    // ============================================================
    // /help dispatch
    // ============================================================

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        return commandRouter.onCommand(sender, command, label, args);
    }

    public void openMainMenu(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        for (HelpCategory category : getCategories()) {
            if (!category.hasVisibleArticles(player)) continue;
            buttons.add(buildCategoryButton(category));
        }
        Component title = MM.deserialize("<gradient:#FF9A00:#9863E7:#5489FF><b>Help</b></gradient>")
                .decoration(TextDecoration.ITALIC, false);
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title).canCloseWithEscape(true).pause(false).build())
                .type(DialogType.multiAction(buttons, null, 2)));
        player.showDialog(dialog);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.0f);
    }

    public void openCategoryMenu(Player player, HelpCategory category) {
        openCategoryMenu(player, category, 0);
    }

    private void openCategoryMenu(Player player, HelpCategory category, int page) {
        List<HelpArticle> visible = category.articles().stream()
                .filter(article -> article.permission() == null || player.hasPermission(article.permission()))
                .toList();
        int totalPages = Math.max(1, (visible.size() + 11) / 12);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * 12;
        int end = Math.min(start + 12, visible.size());
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = start; i < end; i++) {
            HelpArticle article = visible.get(i);
            if (article.permission() != null && !player.hasPermission(article.permission())) continue;
            buttons.add(buildArticleButton(article));
        }
        if (currentPage > 0) buttons.add(pageButton("◀ Previous Page", p -> openCategoryMenu(p, category, currentPage - 1)));
        if (currentPage + 1 < totalPages) buttons.add(pageButton("Next Page ▶", p -> openCategoryMenu(p, category, currentPage + 1)));
        ActionButton back = ActionButton.builder(
                        Component.text("Back", NamedTextColor.RED, TextDecoration.BOLD)
                                .decoration(TextDecoration.ITALIC, false))
                .tooltip(Component.text("Return to the main menu", NamedTextColor.GRAY))
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (view, audience) -> { if (audience instanceof Player p) openMainMenu(p); },
                        ClickCallback.Options.builder().build()))
                .build();
        Component title = MM.deserialize("<gradient:" + category.gradient() + "><b>" + category.title() + "</b></gradient>")
                .decoration(TextDecoration.ITALIC, false);
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title).canCloseWithEscape(true).pause(false).build())
                .type(DialogType.multiAction(buttons, back, 2)));
        player.showDialog(dialog);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.1f);
    }

    private ActionButton buildCategoryButton(HelpCategory category) {
        Component label = MM.deserialize("<gradient:" + category.gradient() + "><b>" + category.title() + "</b></gradient>")
                .decoration(TextDecoration.ITALIC, false);
        Component tooltip = Component.text("Click to view " + category.title().toLowerCase() + " articles", NamedTextColor.GRAY);
        return ActionButton.builder(label).tooltip(tooltip).width(BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (view, audience) -> { if (audience instanceof Player p) openCategoryMenu(p, category); },
                        ClickCallback.Options.builder().build()))
                .build();
    }

    private ActionButton buildArticleButton(HelpArticle article) {
        Component label = MM.deserialize("<gradient:" + article.gradient() + "><b>" + article.title() + "</b></gradient>")
                .decoration(TextDecoration.ITALIC, false);
        Component tooltip = Component.text("Click to read " + article.title().toLowerCase(), NamedTextColor.GRAY);
        return ActionButton.builder(label).tooltip(tooltip).width(BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player p) {
                                if (article.permission() != null && !p.hasPermission(article.permission())) {
                                    p.sendMessage(Messages.noPermission());
                                    return;
                                }
                                openArticle(p, article);
                            }
                        },
                        ClickCallback.Options.builder().build()))
                .build();
    }

    public void openArticle(Player player, HelpArticle article) {
        if (article == null || (article.permission() != null && !player.hasPermission(article.permission()))) {
            player.sendMessage(Messages.noPermission());
            openMainMenu(player);
            return;
        }
        HelpCategory owningCategory = findCategoryOf(article);
        Component title = MM.deserialize("<gradient:" + article.gradient() + "><b>" + article.title() + "</b></gradient>")
                .decoration(TextDecoration.ITALIC, false);

        List<DialogBody> body = new ArrayList<>();
        for (Component line : article.content()) {
            body.add(DialogBody.plainMessage(line, BUTTON_WIDTH));
        }

        List<ActionButton> jumpButtons = new ArrayList<>();
        for (String refId : article.relatedArticles()) {
            HelpArticle ref = getArticle(refId);
            if (ref == null) continue;
            if (ref.permission() != null && !player.hasPermission(ref.permission())) continue;
            jumpButtons.add(ActionButton.builder(
                            MM.deserialize("<gradient:" + ref.gradient() + ">See also: " + ref.title() + "</gradient>")
                                    .decoration(TextDecoration.ITALIC, false))
                    .tooltip(Component.text("Open " + ref.title(), NamedTextColor.GRAY))
                    .width(BUTTON_WIDTH)
                    .action(DialogAction.customClick(
                            (view, audience) -> { if (audience instanceof Player p) openArticle(p, ref); },
                            ClickCallback.Options.builder().build()))
                    .build());
        }

        ActionButton back = ActionButton.builder(
                        Component.text("Back", NamedTextColor.RED, TextDecoration.BOLD)
                                .decoration(TextDecoration.ITALIC, false))
                .tooltip(Component.text(owningCategory != null
                                ? "Return to " + owningCategory.title()
                                : "Return to the help menu",
                        NamedTextColor.GRAY))
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (!(audience instanceof Player p)) return;
                            if (owningCategory != null) openCategoryMenu(p, owningCategory);
                            else openMainMenu(p);
                        },
                        ClickCallback.Options.builder().build()))
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true).pause(false).body(body).build())
                .type(DialogType.multiAction(jumpButtons, back, 1)));
        player.showDialog(dialog);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.0f);
    }

    private static ActionButton pageButton(String label, java.util.function.Consumer<Player> action) {
        return ActionButton.builder(Component.text(label, NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false))
                .tooltip(Component.text("Change help page", NamedTextColor.GRAY))
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) action.accept(p);
                }, ClickCallback.Options.builder().build()))
                .build();
    }

    private HelpCategory findCategoryOf(HelpArticle article) {
        return catalog.categoryOf(article);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        return commandRouter.onTabComplete(sender, command, label, args);
    }

    // ============================================================
    // Join-tip listener
    // ============================================================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Component openHelp = Component.text("/help", NamedTextColor.AQUA, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Open the help menu", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand("/help"));

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Need help? Type ", NamedTextColor.YELLOW).append(openHelp));

        HelpArticle tip = getRandomArticle(player);
        if (tip != null) {
            Component readMore = Component.text(" [Read]", NamedTextColor.YELLOW)
                    .hoverEvent(HoverEvent.showText(Component.text("Open: " + tip.title(), NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand("/help " + tip.id()));
            player.sendMessage(Component.text("Tip: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text(tip.title(), NamedTextColor.WHITE))
                    .append(readMore));
        }
        player.sendMessage(Component.empty());
    }

    // ============================================================
    // Helpers for building article content
    // ============================================================

    private static Component cmd(String command, String description) {
        return Component.text(command, NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text(description, NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.suggestCommand(command));
    }

    private static Component gray(String text)   { return Component.text(text, NamedTextColor.GRAY); }
    private static Component white(String text)  { return Component.text(text, NamedTextColor.WHITE); }
    private static Component aqua(String text)   { return Component.text(text, NamedTextColor.AQUA); }
    private static Component yellow(String text) { return Component.text(text, NamedTextColor.YELLOW); }
    private static Component red(String text)    { return Component.text(text, NamedTextColor.RED); }
    private static Component green(String text)  { return Component.text(text, NamedTextColor.GREEN); }
    private static Component gold(String text)   { return Component.text(text, NamedTextColor.GOLD); }

    // ============================================================
    // Article content
    // ============================================================

    private HelpCategory buildTeleportation() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("homes", "Homes", List.of(
                gray("Personal teleport bookmarks. Default name: 'default'."),
                cmd("/sethome [name]", "Save current location as a home."),
                cmd("/home [name]", "Teleport to a saved home."),
                cmd("/delhome <name>", "Delete a saved home."),
                cmd("/homes", "List your saved homes."),
                aqua("Cap: 15 per player. Reusing a name overwrites."),
                red("Disabled in jass:resource and jass:resource_nether.")
        ), Material.RED_BED, 20, ColorUtil.HELP_GRAD_HOMES, List.of("warps", "back")));

        articles.add(new HelpArticle("warps", "Warps", List.of(
                gray("Server-wide locations. Admin-managed."),
                cmd("/warp <name>", "Teleport to a warp."),
                cmd("/warps", "Open a clickable warps menu."),
                aqua("Common: spawn, newspawn, crates.")
        ), Material.COMPASS, 22, ColorUtil.HELP_GRAD_WARPS, List.of("homes", "spawn")));

        articles.add(new HelpArticle("spawn", "Spawn", List.of(
                gray("Teleport to the server's central hub."),
                cmd("/spawn", "Teleport to spawn.")
        ), Material.BEACON, 24, ColorUtil.HELP_GRAD_SPAWN, List.of("warps", "profiles")));

        articles.add(new HelpArticle("tpa", "TPA", List.of(
                gray("Player-to-player teleport requests."),
                cmd("/tpa <player>", "Request to teleport to a player."),
                cmd("/tpahere <player>", "Request a player to teleport to you."),
                cmd("/tpaccept", "Accept a pending request."),
                cmd("/tpdeny", "Deny a pending request."),
                aqua("Expires: 30s. Inline [Accept]/[Deny] buttons."),
                red("Inventory scan when entering resource worlds.")
        ), Material.ENDER_PEARL, 30, ColorUtil.HELP_GRAD_TPA, List.of("homes", "resource_hunt")));

        articles.add(new HelpArticle("back", "Back", List.of(
                gray("Returns you to your prior location or death point."),
                cmd("/back", "Teleport to previous location."),
                aqua("Captures: deaths, /home, /warp, /spawn, /tpa."),
                white("Persists across server restarts.")
        ), Material.CLOCK, 32, ColorUtil.HELP_GRAD_BACK, List.of("homes", "tpa")));

        return new HelpCategory("teleportation", "Teleportation", articles, Material.RECOVERY_COMPASS, 20, ColorUtil.HELP_GRAD_TELEPORTATION);
    }

    private HelpCategory buildEnchantments() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("telekinesis", "Telekinesis", List.of(
                gray("Sends block drops to your inventory; overflow falls to your feet."),
                aqua("Chain-break (one click breaks the stack):"),
                white("Sugar Cane, Cactus, Bamboo, Kelp, Vines,"),
                white("Pointed Dripstone, Chorus Plant.")
        ), Material.HOPPER, 10, ColorUtil.HELP_GRAD_TELEKINESIS, List.of("smelter", "tunneller")));

        articles.add(new HelpArticle("gem_connoisseur", "Gem Connoisseur", List.of(
                gray("Bonus gem drops from Stone, Deepslate, and Netherrack."),
                aqua("Per-block rate at level 3:"),
                white("Coal 1/100, Copper 1/200, Iron 1/300,"),
                white("Gold 1/400, Redstone 1/500, Lapis 1/600,"),
                white("Diamond 1/700, Emerald 1/800."),
                yellow("Fortune scales drop quantity.")
        ), Material.AMETHYST_CLUSTER, 12, ColorUtil.HELP_GRAD_GEM_CONNOISSEUR, List.of("resource_hunt", "smelter")));

        articles.add(new HelpArticle("tunneller", "Tunneller", List.of(
                gray("Breaks an N×N area centered on the targeted block."),
                aqua("Area scaling by tier:"),
                white("Common 3×3, Uncommon 5×5, Rare 7×7,"),
                white("Epic 9×9, Legendary 11×11."),
                white("Skips air, liquids, and unbreakable blocks."),
                red("Costs durability per extra block broken."),
                green("Stacks with Smelter and Gem Connoisseur."),
                aqua("Mode cycling:"),
                white("Sneak + right-click to cycle area down (wraps to max).")
        ), Material.DIAMOND_PICKAXE, 14, ColorUtil.HELP_GRAD_TUNNELLER, List.of("tiers")));

        articles.add(new HelpArticle("lumberjack", "Lumberjack", List.of(
                gray("Fells whole trees and large mushrooms in one chop."),
                aqua("Detection:"),
                white("Requires at least one adjacent leaf block."),
                white("Supports nether trees (warts/shroomlight)."),
                white("Hard cap: 256 blocks per chop."),
                red("Stops if remaining tool durability is too low.")
        ), Material.DIAMOND_AXE, 16, ColorUtil.HELP_GRAD_LUMBERJACK, List.of("replant", "telekinesis")));

        articles.add(new HelpArticle("smelter", "Smelter", List.of(
                gray("Smelts raw ore drops on pickup."),
                aqua("Conversions:"),
                white("Raw Iron → Iron Ingot."),
                white("Raw Copper → Copper Ingot."),
                white("Raw Gold → Gold Ingot."),
                green("Stacks with Telekinesis.")
        ), Material.BLAST_FURNACE, 20, ColorUtil.HELP_GRAD_SMELTER, List.of("telekinesis", "gem_connoisseur")));

        articles.add(new HelpArticle("replant", "Replant", List.of(
                gray("Auto-replants harvested crops and felled saplings."),
                aqua("Crops (must be fully grown):"),
                white("Wheat, Carrots, Potatoes, Beetroots, Nether Wart."),
                aqua("Saplings:"),
                white("Planted at the base of trees felled by Lumberjack.")
        ), Material.FLOWERING_AZALEA, 22, ColorUtil.HELP_GRAD_REPLANT, List.of("lumberjack", "telekinesis")));

        articles.add(new HelpArticle("spawner_pickup", "Spawner Pickup", List.of(
                gray("20% chance to drop the spawner when mined."),
                white("Tracks successful uses in the tool's lore."),
                red("Tool breaks after 5 successful pickups."),
                red("Cannot be repaired in anvils.")
        ), Material.SPAWNER, 24, ColorUtil.HELP_GRAD_SPAWNER_PICKUP, List.of("egg_collector")));

        articles.add(new HelpArticle("egg_collector", "Egg Collector", List.of(
                gray("Chance to drop a spawn egg on mob kill."),
                white("Base chance: 0.5% per kill."),
                red("Tool breaks after 5 successful drops."),
                red("Cannot be repaired in anvils."),
                yellow("Quality Looting grants extra rolls.")
        ), Material.ALLAY_SPAWN_EGG, 30, ColorUtil.HELP_GRAD_EGG_COLLECTOR, List.of("spawner_pickup", "tiers")));

        articles.add(new HelpArticle("dice_converter", "Dice Converter", List.of(
                gray("Special enchantment for splash potions (from Dice data pack)."),
                aqua("Effect:"),
                white("When thrown, the player is temporarily blocked"),
                white("from picking up splash potions for 2 seconds."),
                yellow("Prevents accidental self-pickup of the 'dice' potion.")
        ), Material.SPLASH_POTION, 31, ColorUtil.HELP_GRAD_CUSTOM_ENCHANTS, List.of("telekinesis")));

        articles.add(new HelpArticle("disenchanting", "Disenchanting Bundle", List.of(
                gray("Recovers attached augments as Augment Gems via any lore-tagged bundle."),
                aqua("Recovery order and chance:"),
                white("Highest quality first; successful recovery steps use 100% → 80% → 60% → 40% → 20% → 0%."),
                yellow("Failed rolls return no gem and do not advance the chance ladder."),
                yellow("Legacy enchantments are migrated before the source item is destroyed."),
                red("Bound curses are destroyed with the source item and never return as riders."),
                red("A legacy item containing only curses is refused before anything is consumed."),
                red("The bundle is consumed on use."),
                red("Cannot extract from tools with Spawner Pickup or Egg Collector.")
        ), Material.BUNDLE, 32, ColorUtil.HELP_GRAD_DISENCHANTING, List.of("tiers")));

        return new HelpCategory("enchantments", "Custom Enchantments", articles, Material.ENCHANTED_BOOK, 22, ColorUtil.HELP_GRAD_CUSTOM_ENCHANTS);
    }

    private HelpCategory buildQuality() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("tiers", "Quality Tiers", List.of(
                gray("Enchantments roll quality variants when applied."),
                aqua("Tiers: Uncommon < Rare < Epic < Legendary."),
                aqua("Per-tier bonuses:"),
                white("Fortune/Looting: up to 5 bonus rolls."),
                white("Luck of the Sea: up to 100% treasure rate."),
                white("Tunneller/Efficacy: up to 11×11 area."),
                white("Silk Touch: extends to special blocks (see /help silk_quality)."),
                aqua("Mode cycling:"),
                white("Sneak + right-click Tunneller/Efficacy to cycle area size down.")
        ), Material.NETHER_STAR, 20, ColorUtil.HELP_GRAD_QUALITY_TIERS, List.of("blood_moon", "tunneller", "silk_quality")));

        articles.add(new HelpArticle("blood_moon", "Blood Moon", List.of(
                gray("Server event with a 50% chance during full-moon nights."),
                aqua("Effects:"),
                red("Quality enchant chance: 10% → 50%."),
                red("Sleep is blocked once the night's fate is rolled."),
                red("Red boss bar tracks night progress."),
                cmd("/fullmoon", "Show next full moon estimate.")
        ), Material.NETHER_WART_BLOCK, 24, ColorUtil.HELP_GRAD_BLOOD_MOON, List.of("tiers")));

        articles.add(new HelpArticle("silk_quality", "Silk Touch Quality", List.of(
                gray("Silk Touch tiers extend to normally non-silk-able blocks:"),
                white("Uncommon: Dirt Path."),
                white("Rare: Farmland."),
                white("Epic: Reinforced Deepslate."),
                white("Legendary: Budding Amethyst."),
                green("Deterministic: quality silk shovels always drop gravel (no flint).")
        ), Material.DIAMOND_ORE, 31, ColorUtil.HELP_GRAD_SILK_QUALITY, List.of("tiers", "tunneller")));

        return new HelpCategory("quality", "Enchantment Quality", articles, Material.NETHER_STAR, 24, ColorUtil.HELP_GRAD_QUALITY_ENCHANTS);
    }

    private HelpCategory buildFeatures() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("nicknames", "Nicknames", List.of(
                gray("Custom display name with color and hex support."),
                cmd("/nick <name>", "Set nickname (& and &#rrggbb supported)."),
                cmd("/nick off", "Clear nickname."),
                aqua("Examples:"),
                white("&cRedName, &aGreen&bAqua, &#FF5555HexColor.")
        ), Material.NAME_TAG, 10, ColorUtil.HELP_GRAD_NICKNAMES, List.of("profiles", "tablist")));

        articles.add(new HelpArticle("flight", "Flight", List.of(
                gray("Toggles creative-style flight."),
                cmd("/fly", "Toggle flight."),
                aqua("Eligibility:"),
                white("Fly-enabled worlds (Lobby, Archive)."),
                white("Earned via a server advancement.")
        ), Material.ELYTRA, 12, ColorUtil.HELP_GRAD_FLIGHT, List.of("profiles", "homes")));

        articles.add(new HelpArticle("itemfilter", "Item Filter", List.of(
                gray("Filters which items you pick up. State is per-profile."),
                cmd("/if toggle", "Enable/disable the filter."),
                cmd("/if mode", "Switch Whitelist/Blacklist."),
                cmd("/if add <item>...", "Add item(s) to the active list."),
                cmd("/if remove <item>...", "Remove item(s)."),
                cmd("/if list", "Show the active list."),
                cmd("/if clear [mode]", "Clear list(s)."),
                green("Bypasses crafting and trading result slots automatically.")
        ), Material.BARRIER, 14, ColorUtil.HELP_GRAD_ITEM_FILTER, List.of("telekinesis", "profiles")));

        articles.add(new HelpArticle("afk", "AFK", List.of(
                gray("Marks you as away-from-keyboard."),
                cmd("/afk", "Toggle AFK."),
                white("Auto-engages after 10 min idle."),
                white("Auto-clears on physical movement."),
                aqua("AFK players are exempt from sleep requirements.")
        ), Material.TOTEM_OF_UNDYING, 16, ColorUtil.HELP_GRAD_AFK, List.of("tablist", "profiles")));

        articles.add(new HelpArticle("tablist", "Tab List", List.of(
                gray("The tab list uses a unified display format:"),
                yellow("[World][Rank] Name [$Balance] [AFK]"),
                aqua("World Tags:"),
                white("A bracketed tag per world (default: [Lobby], [Survival], [Nether], [End], [Resource], [Archive], [Pi]) - admin-configurable, see /tconfig world-profiles list."),
                white("Rank names show your current rank in gold brackets."),
                white("Balance hides if you've toggled it off via /bal hide."),
                red("AFK players show a red [AFK] suffix."),
                white("Sorting is based on your world profile.")
        ), Material.PAPER, 20, ColorUtil.HELP_GRAD_TAB_MENU, List.of("profiles", "afk", "balance")));

        articles.add(new HelpArticle("profiles", "World Profiles", List.of(
                gray("Per-profile inventory, ender chest, and XP."),
                aqua("Default profiles:"),
                white("Lobby, Standard (overworld/nether/end), Archive, Pi."),
                gray("Admins can add/remove/edit world-key mappings live - see /tconfig world-profiles list for the current set."),
                yellow("Items and XP swap automatically on world change.")
        ), Material.PAINTING, 22, ColorUtil.HELP_GRAD_WORLD_PROFILES, List.of("spawn", "tablist")));

        articles.add(new HelpArticle("xp_bottles", "XP Storage Bottles", List.of(
                gray("Brewing-stand recipe converting emeralds to drinkable XP potions."),
                aqua("Yields:"),
                white("Emerald + Glass Bottle → 1,395 orbs."),
                white("Emerald Block + Glass Bottle → 12,555 orbs."),
                yellow("Brewing consumes the brewer's XP."),
                red("Brewing is cancelled if the brewer cannot afford the cost."),
                green("Potions stack to 64.")
        ), Material.EXPERIENCE_BOTTLE, 24, ColorUtil.HELP_GRAD_XP_STORAGE, List.of("profiles", "disenchanting")));

        articles.add(new HelpArticle("tool_xp", "Tool XP and Mending", List.of(
                gray("Custom block XP rolls and Mending payouts are live-configurable."),
                white("Mob XP removal includes the Ender Dragon; player death and non-mob XP sources stay intact."),
                white("Crops, leaves, leaf decay, and configured stone blocks each use their own percentage."),
                white("Placed leaves and stone are tainted and do not award custom XP."),
                white("A Mending item in either hand or armor converts collected XP to economy credit."),
                yellow("When Mending payout is enabled, the repair step is cancelled so the full orb value can be paid out."),
                cmd("/tconfig xp", "Edit XP toggles, chances, materials, taint TTL, and the Mending rate.")
        ), Material.EXPERIENCE_BOTTLE, 26, ColorUtil.HELP_GRAD_XP_STORAGE, List.of("xp_bottles", "profiles")));

        articles.add(new HelpArticle("cash_items", "Cash Items", List.of(
                gray("Tagged datapack cash converts when it enters your inventory."),
                white("Pickup, trade results, clicks, drags, and plugin inventory moves convert the whole stack."),
                yellow("Failed economy writes, malformed values, zero values, and overflow leave the item unchanged."),
                cmd("/tconfig tools.cash-item", "Enable the bridge or change the configured cash key."),
                white("Datapacks must write the value under PublicBukkitValues with the Tweaks cash key.")
        ), Material.GOLD_INGOT, 28, ColorUtil.HELP_GRAD_ECONOMY, List.of("balance", "tool_xp")));

        articles.add(new HelpArticle("durability_tools", "Durability and Repair Kits", List.of(
                gray("Only augmented or already-participating damageable items receive a custom durability pool; plain items use vanilla breaking."),
                white("Crafted and smithing damageables are initialized as augmented items, including an empty ledger when no enchantments are present."),
                white("Repair Kits restore one damaged augmented item, advance its tier, and consume one kit; full-durability items are hidden and refused."),
                white("The kit's target menu shows each item by its /rename name, or its ordinary item name when unnamed, so identical materials stay distinguishable."),
                cmd("/repairkit give [player] [amount]", "Admin command for test kits."),
                cmd("/repairkit debug", "Admin command showing participation, ledger/stamp state, tier, damage, threshold, depleted state, and effective tier step."),
                cmd("/rename [name]", "Free plain-text or color-formatted rename; omit the name to reset."),
                yellow("With never-break enabled, participating items remain at one durability point and become inert without being destroyed; below the repair limit they show 'Out of durability — use a Repair Kit'."),
                red("At the repair limit, the item shows 'Broken — cannot be repaired further' and remains permanently inert."),
                white("The projected pool has a minimum maximum of 8 and caps the effective tier step so the top tier retains a real pool."),
                white("Blocked depleted uses include mining, right-clicks, melee damage beyond one, bows/crossbows, fishing, shearing, entity right-click, trident launch, gliding, and armor/shield absorption."),
                red("Anvils and grindstones are locked unless you have tweaks.tools.anvilbypass; staff with that permission will not see the grindstone lockout.")
        ), Material.DIAMOND_PICKAXE, 30, ColorUtil.HELP_GRAD_TOOL_PROTECT, List.of("toolprotect", "item_admin")));

        articles.add(new HelpArticle("augments", "Augments", List.of(
                gray("Augment Gems hold real registry enchantments in the vanilla stored-enchantment component while the item ledger owns slot state."),
                cmd("/augment", "Open the two-screen slot and augment menu for the held item."),
                cmd("/augment confirm", "Confirm the quoted XP charge and migrate a legacy-enchanted held item."),
                cmd("/augment cancel", "Cancel a pending legacy-migration confirmation."),
                white("Enchanting tables charge and complete normally, then convert the result into gems one tick later."),
                yellow("The complete gem batch is preflighted before the table result is accepted; a refusal or full inventory cancels the enchantment and keeps the vanilla result."),
                white("Enchanted books produce gems; a gem right-click on air or a block opens the same attach flow."),
                white("The gem-first menu shows each compatible tool by its /rename name, and its hover tooltip lists that tool's current augments and bound curses so identical materials stay distinguishable."),
                white("Gems use readable enchantment names; tool lore, the dialog buttons, and the attach/detach chat all name augments the same way — a Roman numeral only for multi-level enchantments, none for single-level ones, and no level for a curse; table and book curses ride another gem, while an all-curse roll produces one curse-only gem."),
                yellow("Augment Gems cannot be used as ingredients in crafting recipes; ordinary amethyst shards remain craftable."),
                white("A curse primary uses Bind and rider curses appear under that gem's action; attaching a curse is permanent, free, and occupies no slot."),
                white("Slots use ◌ for unpurchased, ○ for purchased, and ● for occupied slots."),
                white("Configured capacities remain exact for purchases; indicators above 64 slots use …; missing prices use the highest configured price."),
                yellow("An empty price map leaves slots unpriced, so purchases refuse without changing XP or the ledger."),
                white("Turning an augment off removes the active enchantment but keeps its slot occupied."),
                yellow("A Disenchanting Bundle validates every ledger record before mutation, randomizes highest-tier ties, and keeps the current chance after a failure."),
                white("Unenchanted crafted, smithed, and villager-traded damageable results receive one free slot; enchanted results receive none. A netherite upgrade keeps its existing ledger and grants no free slot, but its slot ceiling rises to the netherite maximum right away — buy the extra slots one at a time."),
                white("A ledger-less enchanted item gets a 30-second quoted confirmation; confirm charges that quote before migrating enchantments into gems, while cancel, expiry, or an item change leaves it untouched."),
                white("Legacy curses stay real on the item and are recorded in the ledger's permanent curse list; curse lore uses the enchantment name directly without a redundant Curse: prefix. Tool lore is non-italic: active quality entries keep their tier color and rarity icon, active plain enchantments are gray, inactive entries are dark gray, symbols are white, dots are bounded, unaccounted real enchantments show in red, and the repurposed Mending enchantment always reads as Numismatic."),
                white("Plain items gain no augment PDC until a slot is purchased or a gem is attached."),
                white("Book conversion rechecks exact post-event stack deltas; crafted results initialize their own ledger; foreign lookalike lore is preserved by exact component fingerprints."),
                cmd("/augment give <player> <enchantment-key> [level] [curse-key[:level] ...]", "Admin test-gem command."),
                red("Augment state is stored in PDC and survives renamed items and foreign metadata.")
        ), Material.AMETHYST_SHARD, 32, ColorUtil.HELP_GRAD_GEM_CONNOISSEUR, List.of("tiers", "durability_tools")));

        articles.add(new HelpArticle("rename_tools", "Rename and Lockouts", List.of(
                gray("Player-facing item renaming is free and does not require an anvil."),
                cmd("/rename <name>", "Apply a legacy-color name to the main-hand item."),
                cmd("/rename", "Reset the main-hand item to its default name."),
                white("Names are limited by the live rename max-length setting."),
                red("Anvil and grindstone openings are cancelled unless you have tweaks.tools.anvilbypass; staff with it will not see the grindstone lockout.")
        ), Material.NAME_TAG, 34, ColorUtil.HELP_GRAD_NICKNAMES, List.of("durability_tools", "augments")));

        articles.add(new HelpArticle("toolprotect", "Tool Protect", List.of(
                gray("Blocks usage of high-tier tools when nearly broken."),
                aqua("Eligibility (all required):"),
                white("Diamond or Netherite material."),
                white("Carries an Epic or Legendary quality enchant."),
                white("Durability below threshold (default 100)."),
                cmd("/toolprotect durability <n>", "Set your threshold.")
        ), Material.SHIELD, 30, ColorUtil.HELP_GRAD_TOOL_PROTECT, List.of("tiers")));

        articles.add(new HelpArticle("display_chest", "Display Chest", List.of(
                gray("Renders a floating preview of chest contents as an ItemDisplay."),
                cmd("/displaychest [hand|side|hand side|off]", "Toggle setup/removal mode; click chests."),
                white("Auto-centers over single and double chests."),
                white("Defaults to slot 0; use 'hand' to use your held item."),
                white("Use 'side' to embed flush with the clicked face."),
                white("- Block items: embedded (clicked face visible)."),
                white("- Non-block: flat against face (item frame style)."),
                white("Billboard: items rotate to always face the viewer."),
                white("Removal: 'off' mode removes both top and side displays."),
                white("State stored in the chunk PDC."),
                green("Setup mode persists for batch placement.")
        ), Material.CHEST, 32, ColorUtil.HELP_GRAD_DISPLAY_CHEST, List.of("itemfilter", "blocklog")));

        articles.add(new HelpArticle("condense", "Condense", List.of(
                gray("Compacts 9x granular items into their block form."),
                cmd("/condense", "Condense the held item type only."),
                cmd("/condense all", "Condense every eligible material in your inventory."),
                aqua("Eligible (vanilla 9:1, both crafting directions):"),
                white("Iron, Gold, Diamond, Emerald, Netherite, Lapis,"),
                white("Redstone, Coal, Copper, Raw Iron/Gold/Copper,"),
                white("Slime, Wheat, Bone Meal, Nether Wart."),
                green("Resource Hunt items are condensable; the block inherits the tag."),
                yellow("Items with custom name, lore, enchants, or other PDC tags are skipped."),
                red("Mixed pools (e.g. 5 tagged + 4 untagged) do NOT merge.")
        ), Material.IRON_BLOCK, 34, ColorUtil.HELP_GRAD_CONDENSE, List.of("itemfilter", "resource_hunt")));

        articles.add(new HelpArticle("gamemode", "Gamemode Shortcuts", List.of(
                gray("Quick self-only gamemode switches."),
                cmd("/survival", "Switch to Survival mode."),
                cmd("/creative", "Switch to Creative mode."),
                white("Affects only the executing player."),
                yellow("No-op if already in the target mode."),
                red("Requires permission: tweaks.admin.gamemode.")
        ), Material.COMMAND_BLOCK, 39, ColorUtil.HELP_GRAD_ITEM_TOOLS,
                List.of("item_admin", "blocklog"), Permissions.ADMIN_GAMEMODE));

        articles.add(new HelpArticle("item_admin", "Item Tools (Admin)", List.of(
                gray("Admin tools for editing held items, viewing inventories, and copying chest GUIs."),
                cmd("/name <name>", "Set held item display name."),
                cmd("/lore add <line#> <text>", "Insert lore at 1-indexed line."),
                cmd("/more", "Maximize held item stack size."),
                cmd("/invsee <player>", "View/modify online player's inventory."),
                cmd("/guicopy [name]", "Save targeted chest to guicopies/<name>.yml."),
                white("Color: legacy '&' codes and '&#rrggbb' hex."),
                white("/guicopy ray-traces 8 blocks; supports double chests."),
                white("Output YAML includes a paste-ready Java snippet."),
                red("Permissions: itemedit, invsee, more, guicopy.")
        ), Material.WRITABLE_BOOK, 37, ColorUtil.HELP_GRAD_ITEM_TOOLS, List.of("blocklog"), Permissions.ADMIN_ITEM_EDIT));

        articles.add(new HelpArticle("config_admin", "Config Tools (Admin)", List.of(
                gray("Manage plugin configuration at runtime."),
                cmd("/tconfig gui", "Open the admin config GUI (players only)."),
                cmd("/tconfig list [category]", "Print every setting and its current value."),
                cmd("/tconfig list logging", "Print the 20 logging categories and their current values."),
                cmd("/tconfig max_homes <int>", "Set global max homes per player."),
                cmd("/tconfig max_chunks <int>", "Set global max chunk claims per player."),
                cmd("/tconfig egg_collector_drop_chance <%>", "Set base egg drop chance."),
                cmd("/tconfig eggdrop <disable|enable> <mob>", "Toggle drops for specific mobs."),
                cmd("/tconfig spawneregg <disable|enable> <mob>", "Toggle spawner-egg usage for mobs."),
                cmd("/tconfig resourceitems <add|remove> <item>", "Manage resource world allowed items."),
                cmd("/tconfig world-profiles <list|add|remove|edit>", "Manage world-key -> profile/tag/color mappings (see /help profiles)."),
                cmd("/tconfig logging.<feature>.<event> <true|false>", "Toggle one console-only event record."),
                white("The GUI main menu has a Logging tab with 20 categories over two pages."),
                white("Logging records are disabled by default; a confirmed CLI/GUI save applies immediately, while hand edits require a restart."),
                cmd("/tconfig <path> <value>", "Generic setter for any other registered setting (Protection, Player Admin, World Management, Teleport, Minigames, Economy, Block Log, Death Inventory, Enchantments, Item Admin, Xp Bottle categories)."),
                white("Ranks: /ranks edit  ·  Permissions: /tprm  ·  Whack: /whack"),
                red("Permission: tweaks.admin.config.")
        ), Material.COMMAND_BLOCK, 41, ColorUtil.HELP_GRAD_ITEM_TOOLS,
                List.of("blocklog", "item_admin"), Permissions.ADMIN_CONFIG));

        articles.add(new HelpArticle("blocklog", "Block Log (Admin)", List.of(
                gray("Per-chunk audit log for chest, trapped chest, and barrel changes."),
                cmd("/logs", "Toggle inspector mode; punch a chest to view."),
                aqua("Display:"),
                white("Time, player, delta, item per entry."),
                white("Hover item for tooltip; hover player for UUID."),
                white("10 entries per page; [Prev]/[Next] paginates."),
                aqua("Storage:"),
                white("Stored in chunk PDC; no extra files."),
                white("Pruned: entries older than 30 days on chunk load."),
                white("Cap: 500 entries per chest (oldest dropped)."),
                red("Permission: tweaks.admin.logs.")
        ), Material.WRITABLE_BOOK, 43, ColorUtil.HELP_GRAD_BLOCK_LOG, List.of("toolprotect"), Permissions.ADMIN_LOGS));

        articles.add(new HelpArticle("death_inventory", "Death Inventory (Admin)", List.of(
                gray("Browse and restore player inventories captured at the moment of death."),
                cmd("/deathinventory <player> list", "List all saved death records (newest first)."),
                cmd("/deathinventory <player> <id>", "Open a 54-slot GUI of the saved inventory."),
                cmd("/deathinventory <player> <id> restore", "Fully replace the player's current inventory."),
                aqua("GUI:"),
                white("Clicking an item removes it from the view and gives it to you."),
                aqua("Restore:"),
                white("Clears the target player's inventory, then fills all 41 slots"),
                white("(main, armor, offhand) exactly as they were at time of death."),
                white("Both admin and target player must be online to restore."),
                aqua("Retention:"),
                white("Records are kept for 30 days; older files are purged on startup."),
                yellow("Alias: /di"),
                red("Permission: tweaks.admin.deathinventory.")
        ), Material.CHEST, 45, ColorUtil.HELP_GRAD_BLOCK_LOG, List.of("item_admin", "blocklog"),
                Permissions.ADMIN_DEATH_INVENTORY));

        return new HelpCategory("features", "Player Features", articles, Material.WRITABLE_BOOK, 30, ColorUtil.HELP_GRAD_PLAYER_FEATURES);
    }

    private HelpCategory buildMinigames() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("resource_hunt", "Resource Hunt", List.of(
                gray("Individual tiered gathering minigame in the resource worlds."),
                cmd("/resource", "Travel to the resource world."),
                white("The world (overworld/nether) is picked at startup,"),
                white("but each player gets their own random target material."),
                white("Targets are unique per-player where possible."),
                green("Collect category includes mob drops (e.g. ghast tears)."),
                green("Shear targets support specific colors (e.g. red_sheep)."),
                green("Turtle, frog, and sniffer targets count one successful pairing before eggs are laid."),
                aqua("Three cumulative tier thresholds:"),
                white("T1 = amount, T2 = round(T1 * multiplier), T3 = round(T1 * multiplier²)."),
                gold("Each tier crossed grants one 'resource' reward."),
                red("Ender chests are blocked in resource worlds."),
                gold("Admin:"),
                cmd("/resource settarget [p] <t>", "Override a player's target."),
                aqua("Self: tweaks.admin.resource.settarget.self"),
                aqua("Other: tweaks.admin.resource.settarget.other")
        ), Material.GRASS_BLOCK, 20, ColorUtil.HELP_GRAD_RESOURCE_HUNT, List.of("reroll", "rewards")));

        articles.add(new HelpArticle("reroll", "Target Re-roll", List.of(
                gray("Get a new Resource Hunt target."),
                cmd("/reroll", "Re-roll your current target."),
                white("First re-roll per session is FREE."),
                white("Subsequent re-rolls cost 1 Resource Rupee each."),
                yellow("Wait until you are in the resource world to see your bar update.")
        ), Material.EMERALD, 22, ColorUtil.HELP_GRAD_RESOURCE_HUNT, List.of("resource_hunt", "resource_rupee")));

        articles.add(new HelpArticle("resource_rupee", "Resource Rupee", List.of(
                gray("A currency item found while gathering in the resource worlds or earned as a reward."),
                white("Resource Rupees are special emeralds with a green name and a"),
                white("\"...the Wanderer's Path...\" lore line; the plugin recognizes them"),
                white("as currency even when they come from datapacks or /give."),
                aqua("Conversion (any crafting grid):"),
                white("9 Resource Rupees -> 1 Resource Rupee Block."),
                white("1 Resource Rupee Block -> 9 Resource Rupees."),
                aqua("Spent on:"),
                white("- Re-rolling your Resource Hunt target after the free re-roll."),
                white("- Purchasing land claims with /claim."),
                red("Carrying any lore-marked emerald blocks regular villager trades; stash it first. Wandering Traders still work."),
                green("Both forms stack normally and can be stored in any container.")
        ), Material.EMERALD, 26, ColorUtil.HELP_GRAD_RESOURCE_HUNT, List.of("reroll", "protection_purchaseable")));

        articles.add(new HelpArticle("rewards", "Rewards", List.of(
                gray("Pending-reward inbox for minigames."),
                cmd("/reward claim", "Claim pending rewards."),
                white("Items go to your inventory; overflow drops at your feet."),
                red("Admin:"),
                cmd("/reward create <name>", "Create reward template."),
                cmd("/reward edit <name>", "Open reward editor GUI."),
                cmd("/reward give <p> <r> [c]", "Queue reward grant.")
        ), Material.ENDER_CHEST, 24, ColorUtil.HELP_GRAD_REWARDS, List.of("resource_hunt", "whack")));

        articles.add(new HelpArticle("whack", "Whack-an-Andrew", List.of(
                gray("Mole-style minigame: hit armor stands as they pop up."),
                aqua("Play:"),
                white("1. Travel to the Whack arena."),
                white("2. Hit stands while they're up."),
                gold("3. Top 3 scorers receive rewards."),
                red("Admin: /whack arena begins setup.")
        ), Material.PLAYER_HEAD, 31, ColorUtil.HELP_GRAD_WHACK, List.of("rewards", "profiles")));

        articles.add(new HelpArticle("blackjack", "Blackjack (PvD)", List.of(
                gray("Play against the dealer at admin-placed physical tables."),
                white("Middle button starts the game or clears a finished one."),
                aqua("Rules:"),
                white("- Dealer stands on all 17s; Blackjack pays 3:2."),
                white("- Hole Card: dealer's 2nd card is hidden until you stand."),
                white("- Face cards (J/Q/K) feature custom player portraits."),
                aqua("Controls:"),
                white("- Left: Hit, Middle: Start/Clear, Right: Stand."),
                green("Dealer Mannequin: celebrates or mourns at game end."),
                green("Practice tables (bet: FREE) available — no currency required."),
                white("When Discord is configured, settled wins, losses, and forfeitures appear in grouped lines."),
                red("Requirement: 'dqc.cards' resource pack.")
        ), Material.GREEN_WOOL, 33, ColorUtil.HELP_GRAD_BLACKJACK, List.of("blackjack_mechanics")));

        articles.add(new HelpArticle("blackjack_mechanics", "Blackjack Mechanics", List.of(
                gray("Technical details for all Blackjack tables."),
                aqua("General:"),
                red("Inactivity: 10 min idle evicts and forfeits bets."),
                white("Rendering: cards lie flat on the table surface."),
                white("Deck: standard 52-card, reshuffled every game."),
                gold("Admin Setup:"),
                white("- Footprint: solid 2×3 area (no carpet required)."),
                white("- Registration: /blackjack createtable <bet> [hexColor]"),
                white("- Free/practice table: use '0' or 'free' as the bet."),
                white("- Free tables display 'Bet: FREE' and skip all economy."),
                white("- Card Backs: optional [hexColor] for custom tints.")
        ), Material.KNOWLEDGE_BOOK, 35, ColorUtil.HELP_GRAD_BLACKJACK, List.of("blackjack")));

        articles.add(new HelpArticle("roulette", "Roulette", List.of(
                gray("Bet on the in-world roulette wheel with nearby players."),
                cmd("/roulette stake <amount>", "Set your sticky stake; every segment click wagers it."),
                aqua("Bet families:"),
                white("- Straight-up: a single pocket (1-36), pays 36:1; pocket 0 (Green) pays 50:1."),
                white("- Dozen (Thirds): 1st/2nd/3rd twelve, pays 3:1."),
                white("- Colour: red or black, pays 2:1."),
                yellow("Green 0 loses every Dozen/Colour bet — no odd/even, high/low, or columns."),
                yellow("A win pays exactly stake x odds — your wagered stake is never returned on top."),
                white("Segments are color-coded blocks (red/black/green wool, dozen terracotta) with a floating odds label."),
                white("Each colored marker and clickable hitbox share one scale and center; markers billboard vertically while hitboxes stay axis-aligned."),
                aqua("How a round works:"),
                white("The first bet on an idle wheel opens a 30-second window for everyone nearby."),
                white("When it closes, the wheel spins, the ball settles, and the round pays out."),
                red("Bets are final: no un-betting, no refund on quit."),
                green("Winnings are credited to your balance even if you log off first."),
                white("Your result message shows the amount wagered and the amount actually won."),
                white("When Discord is configured, each settled bettor appears in the grouped settlement record."),
                white("Discord betting uses native /balance, /bet, /roulette, and /mybets commands in the configured betting channel; linked accounts may bet while offline."),
                gold("Winning 8x your wager or more announces it to the whole server!"),
                gold("A hologram over the wheel shows the one server-wide house balance."),
                red("Admin:"),
                cmd("/roulette createboard <min> <max>", "Begin board setup; right-click the spin control to finalize."),
                cmd("/roulette removeboard", "Begin board removal; right-click the target board's spin control."),
                cmd("/roulette setdiscord", "Designate the board used by Discord betting; right-click its spin control."),
                cmd("/roulette cleardiscord", "Clear the Discord betting designation directly."),
                white("Right-click a registered board's spin control to force-close betting and spin now.")
        ), Material.RED_CONCRETE, 37, ColorUtil.HELP_GRAD_BLACKJACK, List.of("blackjack", "house")));

        return new HelpCategory("minigames", "Minigames", articles, Material.TARGET, 32, ColorUtil.HELP_GRAD_MINIGAMES);
    }

    private HelpCategory buildPermissions() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("permissions_overview", "Permissions Overview", List.of(
                gray("Server-side permission system with groups and per-user overrides."),
                aqua("Model:"),
                white("Every player always receives default, plus any additional groups."),
                white("Each group may declare a single parent group, forming an inheritance chain."),
                white("Effective perms = user direct + union of all assigned groups + ancestors."),
                white("Shared ancestors in the DAG contribute once; cycles are skipped safely."),
                yellow("Permission keys are defined in Permissions.java."),
                cmd("/tprm", "Open the visual editor."),
                red("Requires permission: tweaks.admin.permissions.")
        ), Material.BOOKSHELF, 20, ColorUtil.HELP_GRAD_PERMISSIONS,
                List.of("permissions_groups", "permissions_players", "permissions_gui"),
                Permissions.ADMIN_PERMISSIONS));

        articles.add(new HelpArticle("permissions_groups", "Groups Management", List.of(
                gray("Manage permission groups and their members."),
                cmd("/tprm", "Open GUI and click 'Groups'."),
                aqua("Features:"),
                white("- Dynamic listing of all server groups."),
                white("- Create new groups via a name-entry dialog."),
                white("- Pagination support for many groups."),
                white("- Click any group to open the Group Hub."),
                red("Requires permission: tweaks.admin.permissions.")
        ), Material.CHEST, 22, ColorUtil.HELP_GRAD_PERMS_GROUPS,
                List.of("permissions_overview", "permissions_group_edit", "permissions_gui"),
                Permissions.ADMIN_PERMISSIONS));

        articles.add(new HelpArticle("permissions_group_edit", "Group Editing", List.of(
                gray("Detailed configuration for a specific group."),
                aqua("Operations:"),
                white("- Edit Permissions: Toggle individual perms."),
                white("- Manage Members: Add/remove players from non-default groups."),
                white("- Inheritance: Choose a group to inherit from."),
                white("- Delete Group: Remove group (protected for 'default')."),
                yellow("Changes refresh every online player's effective permissions instantly."),
                red("Requires permission: tweaks.admin.permissions.")
        ), Material.WRITABLE_BOOK, 24, ColorUtil.HELP_GRAD_PERMS_GROUPS,
                List.of("permissions_groups", "permissions_overview"),
                Permissions.ADMIN_PERMISSIONS));

        articles.add(new HelpArticle("permissions_players", "Players Management", List.of(
                gray("Audit and manage per-player permission overrides."),
                cmd("/tprm", "Open GUI and click 'Players'."),
                aqua("Features:"),
                white("- Lists online players only."),
                white("- Search previously joined offline players by name."),
                white("- Shows default plus each player's additional groups."),
                white("- Click any player to open the User Hub."),
                red("Requires permission: tweaks.admin.permissions.")
        ), Material.PLAYER_HEAD, 29, ColorUtil.HELP_GRAD_PERMS_USERS,
                List.of("permissions_overview", "permissions_player_edit", "permissions_gui"),
                Permissions.ADMIN_PERMISSIONS));

        articles.add(new HelpArticle("permissions_player_edit", "Player Editing", List.of(
                gray("Manage specific settings for an individual player."),
                aqua("Operations:"),
                white("- Edit Permissions: Toggle direct overrides."),
                white("- Edit Groups: Toggle group memberships (multi-select)."),
                white("- Reset User: Clear all overrides and groups."),
                yellow("Effective permissions combine default, groups, ancestors, and direct overrides."),
                red("Requires permission: tweaks.admin.permissions.")
        ), Material.PLAYER_HEAD, 31, ColorUtil.HELP_GRAD_PERMS_USERS,
                List.of("permissions_players", "permissions_overview"),
                Permissions.ADMIN_PERMISSIONS));

        articles.add(new HelpArticle("permissions_gui", "Permissions GUI", List.of(
                gray("Dialog-based visual editor for groups, users, and permissions."),
                cmd("/tprm", "Open the main GUI."),
                aqua("Navigation:"),
                white("Main → Groups → Group Hub → Categories → perms."),
                white("Main → Players → User Hub → Categories → perms."),
                white("Lists paginate at 12 entries; use Prev/Next Page buttons."),
                aqua("Interactions:"),
                white("Hover a permission toggle for its full node and description."),
                white("✓ prefix → currently granted/active (click to revoke)."),
                white("✗ prefix → currently denied/inactive (click to grant)."),
                white("Categories: perms are organized by system (Protection, Minigames, etc.)."),
                white("Edit Groups: multi-select toggle of group membership."),
                red("Requires permission: tweaks.admin.permissions.")
        ), Material.COMPASS, 33, ColorUtil.HELP_GRAD_PERMS_TPRM_USAGE,
                List.of("permissions_overview", "permissions_groups", "permissions_players"),
                Permissions.ADMIN_PERMISSIONS));

        return new HelpCategory("permissions", "Permissions", articles, Material.BOOKSHELF, 34, ColorUtil.HELP_GRAD_PERMISSIONS);
    }

    private HelpCategory buildProtection() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("protection_purchaseable", "Claim", List.of(
                gray("Mark a rectangle of chunks as your protected territory."),
                white("Region names are 1-32 lowercase letters, digits, '_' or '-'; input is normalized."),
                cmd("/region claim <name>", "Claim the current wand selection."),
                cmd("/region wand", "Get the selection tool (Stone Axe)."),
                aqua("Workflow:"),
                white("- Use the selection wand (Stone Axe) to select chunks."),
                white("- Left-click to set chunk 1, Right-click to set chunk 2."),
                white("- Clicking any block in a chunk anchors that entire chunk."),
                white("- Particle outlines show the current selection."),
                white("- Use /region clear to drop the current selection."),
                white("- Coverage is chunk-granular: entire chunks are claimed."),
                white("- Claims <= 5 chunks stamp immediately; others stamp as chunks load."),
                white("- Names are unique per world; 'home' can exist in multiple worlds."),
                white("- Admins may list worlds in protection.public-claim-worlds so anyone can claim there."),
                white("- Public non-admin claims still cost Resource Rupees and count toward max_chunks; admins bypass both."),
                white("- Unclaim/info/flag/member permissions are separate; removing a world is not retroactive."),
                yellow("Alias: /rg claim"),
                red("Requires permission: " + Permissions.PROTECTION_PURCHASEABLE
                        + ", unless the current world is publicly claimable.")
        ), Material.OAK_FENCE, 20, ColorUtil.HELP_GRAD_PROTECTION_PURCHASEABLE,
                List.of("protection_info", "protection_members", "protection_flags"),
                null));

        articles.add(new HelpArticle("protection_info", "Region Info", List.of(
                gray("View details about regions at your location or by name."),
                cmd("/region info [name] [world]", "Show details for a specific region."),
                cmd("/rg i", "Quick info for where you are standing."),
                cmd("/region select <name>", "Restore selection boundaries from a region."),
                cmd("/region list [player|page] [page]", "Admin-only paginated list of non-global regions across worlds."),
                cmd("/region tp <name> [world]", "Admin-only teleport to a region's safe centre."),
                cmd("/region togglebypass", "Opt into the session-only admin bypass for in-world protection."),
                aqua("Displays:"),
                white("- Region ID, Owner, Parent (if sub-region)."),
                white("- Members list: hover '(N entries)' for full names."),
                white("- Active flag rules (including targeted rules)."),
                white("- Mob-spawn entity lists (ALLOW/DENY_MOB_SPAWN)."),
                yellow("Aliases: /rg i, /rg am, /rg rm"),
                red("Requires permission: " + Permissions.PROTECTION_INFO + ".")
        ), Material.BOOK, 22, ColorUtil.HELP_GRAD_PROTECTION_PURCHASEABLE,
                List.of("protection_purchaseable", "protection_flags"),
                Permissions.PROTECTION_INFO));

        articles.add(new HelpArticle("protection_members", "Members & Hierarchy", List.of(
                gray("Manage who can build and how sub-regions interact."),
                cmd("/region addmember <name> <player|group:name> [world]", "Add a member (player or group)."),
                cmd("/region removemember <name> <player|group:name> [world]", "Remove a member."),
                cmd("/region addmanager <name> <player|group:name> [world]", "Add a manager."),
                cmd("/region removemanager <name> <player|group:name> [world]", "Remove a manager."),
                cmd("/region setparent <child> <parent> [world]", "Create a sub-region relationship."),
                cmd("/region unsetparent <child> [world]", "Remove a sub-region relationship."),
                aqua("Roles:"),
                white("- Owner: Full control, can unclaim and manage the hierarchy."),
                white("- Manager: Can edit flags and members. Cannot edit the manager list."),
                white("- Member: Build access; lowest priority for flags."),
                aqua("Hierarchy:"),
                white("- Sub-region flags override their parent(s)."),
                white("- Priority: GROUP > OWNER > MANAGER > MEMBER > DEFAULT."),
                white("- Nesting requires owning both regions; non-admin child and parent owners must match."),
                white("- Membership is independent at each level."),
                yellow("Aliases: /rg am, /rg rm, /rg aman, /rg rman"),
                red("Requires permission: " + Permissions.PROTECTION_MEMBER + ".")
        ), Material.PLAYER_HEAD, 24, ColorUtil.HELP_GRAD_PROTECTION_MEMBERS,
                List.of("protection_purchaseable", "protection_flags"),
                Permissions.PROTECTION_MEMBER));

        articles.add(new HelpArticle("protection_flags", "Precision Flags", List.of(
                gray("Control actions within regions with targeted rules."),
                cmd("/region flag <name> <flag> <value...> [target] [world]", "Set a flag rule."),
                aqua("Flag Types:"),
                white("- Boolean (BLOCK_BREAK, PVP, MOB_SPAWNING, INVINCIBILITY, ENTRY)."),
                white("- Material (ALLOW_BLOCK_BREAK, etc.): use block list."),
                white("- EntityType (ALLOW_MOB_SPAWN, DENY_MOB_SPAWN): use entity list."),
                aqua("ENTRY flag:"),
                white("- Controls who may enter the region at all."),
                white("- ENTRY false default blocks all non-exempt players at the border."),
                white("- Players already inside cannot move; must /home or /spawn to leave."),
                white("- Respawn inside a denied region redirects to world spawn."),
                white("- Admins with " + Permissions.PROTECTION_BYPASS + " can toggle a session-only bypass; it is cleared on join and quit."),
                aqua("Targeting (Boolean only):"),
                white("- [target] can be: owner, manager, member, or group (e.g. group:admin)."),
                white("- Priority: GROUP > OWNER > MANAGER > MEMBER > DEFAULT."),
                green("- Flags override world gamerules (e.g. mobGriefing)."),
                white("- /region flag [flag] defaults to your current location."),
                red("Requires permission: " + Permissions.PROTECTION_FLAG + ".")
        ), Material.OAK_SIGN, 30, ColorUtil.HELP_GRAD_PROTECTION_FLAGS,
                List.of("protection_purchaseable", "protection_info"),
                Permissions.PROTECTION_FLAG));

        articles.add(new HelpArticle("protection_gui", "Region GUI", List.of(
                gray("Manage a region through a clickable Paper Dialog instead of typing flag CLI."),
                cmd("/region gui", "Open the dialog for the region you're standing in."),
                cmd("/region gui <name> [world]", "Open the dialog for a specific region."),
                aqua("Access:"),
                white("- Owners and managers can open their own regions."),
                white("- Admins with " + Permissions.PROTECTION_ADMIN + " can open any region."),
                white("- Standing in overlapping claims picks the innermost (leaf) sub-region."),
                aqua("In the dialog:"),
                white("- Toggle boolean flags and per-role overrides without remembering syntax."),
                white("- Add/remove members (owners and managers); add/remove managers (owner only)."),
                white("- Edit material and entity lists for flags that support them."),
                yellow("Alias: /rg gui"),
                red("Requires permission: " + Permissions.PROTECTION_INFO + ".")
        ), Material.PAINTING, 32, ColorUtil.HELP_GRAD_PROTECTION_FLAGS,
                List.of("protection_flags", "protection_members", "protection_info"),
                Permissions.PROTECTION_INFO));

        articles.add(new HelpArticle("protection_unclaim", "Unclaim", List.of(
                gray("Permanently remove a region and all of its sub-regions."),
                cmd("/region unclaim <name> [world]", "Remove a region and cascade through its sub-regions."),
                aqua("Notes:"),
                white("- Each destroyed region is refunded to its own owner when online."),
                white("- Deleted region files are archived and stale chunk pointers are cleaned lazily."),
                yellow("Alias: /rg unclaim"),
                red("Requires permission: " + Permissions.PROTECTION_UNCLAIM + ".")
        ), Material.BARRIER, 34, ColorUtil.HELP_GRAD_PROTECTION_UNCLAIM,
                List.of("protection_purchaseable", "protection_info"),
                Permissions.PROTECTION_UNCLAIM));

        return new HelpCategory("protection", "Land Protection", articles, Material.OAK_FENCE, 36, ColorUtil.HELP_GRAD_PROTECTION);
    }

    private HelpCategory buildEconomy() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("daily_rewards", "Daily Rewards", List.of(
                gray("Earn money just for logging in!"),
                aqua("Login Streak:"),
                white("The more days in a row you log in, the higher your reward."),
                white("Maximum streak: 7 days."),
                aqua("Multipliers:"),
                white("Day 1: 1.0x (Base $100)"),
                white("Day 7: 2.5x (Up to $250 base)"),
                yellow("Rank bonuses apply on top of the streak reward."),
                green("Verified: Daily reward message uses '+$100' formatting.")
        ), Material.GOLD_INGOT, 20, ColorUtil.HELP_GRAD_ECONOMY, List.of("balance", "ranks")));

        articles.add(new HelpArticle("balance", "Economy & Balance", List.of(
                gray("Server-wide dollar-based economy."),
                cmd("/balance", "View your current funds. Alias: /bal."),
                cmd("/balance hide", "Toggle balance visibility in the tab list."),
                red("Admin:"),
                cmd("/balance set <player> <amount>", "Set a player's balance."),
                cmd("/balance add <player> <amount>", "Grant funds."),
                cmd("/balance remove <player> <amount>", "Deduct funds."),
                white("Balances are whole-dollar values; decimal admin inputs and mutations outside the supported range are rejected without changing the balance."),
                red("Permission: tweaks.admin.balance.")
        ), Material.EMERALD, 22, ColorUtil.HELP_GRAD_ECONOMY, List.of("daily_rewards", "ranks", "lottery")));

        articles.add(new HelpArticle("lottery", "Server Lottery", List.of(
                gray("Players who lose to the casino can enter the server-wide lottery."),
                white("The pot is floor(M x House growth since the baseline x (1 + 1% per entrant)), clamped so the House stays above its live fallback."),
                white("One entrant rolls positive House growth into the fallback and advances the baseline; the fallback resets to its configured base after a successful payout."),
                white("At least two players must have entries before a draw can pay out."),
                cmd("/lottery info", "View entrants, the baseline, fallback floor, and current pot."),
                cmd("/lottery", "Shows the public lottery information."),
                cmd("/lottery entries", "List the current entrant pool. Public, with a short non-admin cooldown."),
                red("Admin:"),
                cmd("/lottery draw", "Draw one eligible winner and pay the current pot."),
                cmd("/lottery baseline <amount>", "Set the growth baseline."),
                cmd("/lottery fallback [<amount>]", "View or set the live fallback floor."),
                white("If payment is abandoned, entries and baseline stay unchanged. A retained payment resumes on the next startup; a stuck payment needs House reconciliation."),
                white("When configured, the lottery voice channel displays the live payable pot or 'waiting'."),
                red("Admin permission: " + Permissions.LOTTERY_ADMIN + ".")
        ), Material.CHEST, 21, ColorUtil.HELP_GRAD_ECONOMY, List.of("house", "balance")));

        articles.add(new HelpArticle("house", "Casino House Account", List.of(
                gray("Administrative account receiving casino losses and inactive-hand forfeitures."),
                red("Admin:"),
                cmd("/house balance", "View the server-wide house balance."),
                cmd("/house add|remove|set <amount>", "Adjust the account; only set may make it negative."),
                cmd("/house pay <player> <amount>", "Transfer house funds to an online or known offline player."),
                white("When configured, a Discord voice channel displays the live House balance."),
                red("Permission: " + Permissions.HOUSE_ADMIN + ".")
        ), Material.GOLD_BLOCK, 23, ColorUtil.HELP_GRAD_ECONOMY, List.of("balance", "ranks", "roulette", "lottery"), Permissions.HOUSE_ADMIN));

        articles.add(new HelpArticle("ranks", "Ranks & Progression", List.of(
                gray("Purchase ranks to unlock better rewards and bonuses."),
                cmd("/ranks", "List all ranks, costs, and benefits."),
                cmd("/rankup", "Purchase the next rank in the hierarchy."),
                aqua("Benefits:"),
                white("- Daily Bonus: Increases your daily login rewards."),
                white("- Rakeback: Grants a percentage of losses back in the casino."),
                red("Admin:"),
                cmd("/ranks edit", "Open the visual rank editor (Paper Dialog)."),
                red("Permission: " + Permissions.ADMIN_RANKS + "."),
                cmd("/rank set <player> <rank_id/name>", "Manually assign a player's rank."),
                red("Permission: " + Permissions.ADMIN_RANK_SET + ".")
        ), Material.GOLD_BLOCK, 24, ColorUtil.HELP_GRAD_RANKS, List.of("balance", "daily_rewards")));

        return new HelpCategory("economy", "Economy & Ranks", articles, Material.GOLD_INGOT, 38, ColorUtil.HELP_GRAD_ECONOMY);
    }
}
