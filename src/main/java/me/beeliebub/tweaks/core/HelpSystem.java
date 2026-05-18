package me.beeliebub.tweaks.core;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.ColorUtil;
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

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int BUTTON_WIDTH = 250;

    private final Map<String, HelpCategory> categories = new LinkedHashMap<>();
    private final Map<String, HelpArticle> allArticles = new LinkedHashMap<>();
    private final Logger logger;

    public HelpSystem() {
        this(Logger.getLogger("Tweaks"));
    }

    public HelpSystem(Logger logger) {
        this.logger = logger;
        addCategory(buildTeleportation());
        addCategory(buildEnchantments());
        addCategory(buildQuality());
        addCategory(buildFeatures());
        addCategory(buildMinigames());
        addCategory(buildPermissions());
        addCategory(buildProtection());
        validateCrossReferences();
    }

    public Collection<HelpCategory> getCategories() {
        return Collections.unmodifiableCollection(categories.values());
    }

    public HelpCategory getCategory(String id) {
        return id == null ? null : categories.get(id);
    }

    public HelpArticle getArticle(String id) {
        return id == null ? null : allArticles.get(id);
    }

    public HelpArticle getRandomArticle(Player player) {
        List<HelpArticle> visible = allArticles.values().stream()
                .filter(a -> a.permission() == null || player.hasPermission(a.permission()))
                .toList();
        if (visible.isEmpty()) return null;
        return visible.get(ThreadLocalRandom.current().nextInt(visible.size()));
    }

    private void addCategory(HelpCategory category) {
        categories.put(category.id(), category);
        for (HelpArticle article : category.articles()) {
            HelpArticle prev = allArticles.put(article.id(), article);
            if (prev != null) {
                logger.warning("Help: duplicate article id '" + article.id() + "' in category '" + category.id() + "'");
            }
        }
    }

    private void validateCrossReferences() {
        for (HelpArticle article : allArticles.values()) {
            for (String refId : article.relatedArticles()) {
                if (!allArticles.containsKey(refId)) {
                    logger.warning("Help: article '" + article.id() + "' references unknown article '" + refId + "'");
                }
            }
        }
    }

    // ============================================================
    // /help dispatch
    // ============================================================

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /help.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            openMainMenu(player);
            return true;
        }

        String target = args[0].toLowerCase();
        HelpCategory category = getCategory(target);
        if (category != null) {
            if (category.hasVisibleArticles(player)) {
                openCategoryMenu(player, category);
            } else {
                player.sendMessage(Component.text("You don't have permission to view this category.", NamedTextColor.RED));
            }
            return true;
        }

        HelpArticle article = getArticle(target);
        if (article != null) {
            if (article.permission() == null || player.hasPermission(article.permission())) {
                openArticle(player, article);
            } else {
                player.sendMessage(Component.text("You don't have permission to view this article.", NamedTextColor.RED));
            }
            return true;
        }

        player.sendMessage(Component.text("Unknown help section: " + target, NamedTextColor.RED));
        return true;
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
        List<ActionButton> buttons = new ArrayList<>();
        for (HelpArticle article : category.articles()) {
            if (article.permission() != null && !player.hasPermission(article.permission())) continue;
            buttons.add(buildArticleButton(article));
        }
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
                .type(DialogType.multiAction(buttons, back, 1)));
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
                        (view, audience) -> { if (audience instanceof Player p) openArticle(p, article); },
                        ClickCallback.Options.builder().build()))
                .build();
    }

    public void openArticle(Player player, HelpArticle article) {
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

    private HelpCategory findCategoryOf(HelpArticle article) {
        for (HelpCategory category : getCategories()) {
            for (HelpArticle a : category.articles()) {
                if (a.id().equals(article.id())) return category;
            }
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();
        String partial = args[0].toLowerCase();
        List<String> options = new ArrayList<>();
        for (HelpCategory c : getCategories()) {
            if (c.hasVisibleArticles(player)) {
                options.add(c.id());
                for (HelpArticle a : c.articles()) {
                    if (a.permission() == null || player.hasPermission(a.permission())) {
                        options.add(a.id());
                    }
                }
            }
        }
        return options.stream().filter(o -> o.startsWith(partial)).toList();
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
                cmd("/warps", "List all warps."),
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
                gray("Extracts enchantments into books via lore-tagged bundles."),
                aqua("Per-extract success chance:"),
                white("1st 100% → 2nd 80% → 3rd 60% → 4th 40% → 5th 20%."),
                green("Highest quality tier is extracted first."),
                yellow("Chance does NOT reset on failure."),
                red("Failures consume the enchant without producing a book."),
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
                white("Legendary: Budding Amethyst.")
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
                cmd("/if clear [mode]", "Clear list(s).")
        ), Material.BARRIER, 14, ColorUtil.HELP_GRAD_ITEM_FILTER, List.of("telekinesis", "profiles")));

        articles.add(new HelpArticle("afk", "AFK", List.of(
                gray("Marks you as away-from-keyboard."),
                cmd("/afk", "Toggle AFK."),
                white("Auto-engages after 10 min idle."),
                white("Auto-clears on physical movement."),
                aqua("AFK players are exempt from sleep requirements.")
        ), Material.TOTEM_OF_UNDYING, 16, ColorUtil.HELP_GRAD_AFK, List.of("tablist", "profiles")));

        articles.add(new HelpArticle("tablist", "Tab List", List.of(
                gray("Tab list groups players by world profile."),
                aqua("World tags:"),
                white("[Lobby], [Survival], [Nether], [End], [Resource]."),
                red("AFK players show an [AFK] suffix."),
                white("Tags update on world change.")
        ), Material.PAPER, 20, ColorUtil.HELP_GRAD_TAB_MENU, List.of("profiles", "afk")));

        articles.add(new HelpArticle("profiles", "World Profiles", List.of(
                gray("Per-profile inventory, ender chest, and XP."),
                aqua("Profiles:"),
                white("Lobby (jass:lobby)."),
                white("Standard (overworld, nether, end)."),
                white("Archive (jass:archive)."),
                white("Pi (jass:pi)."),
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
                gray("Admin tools for editing held items and copying chest GUIs."),
                cmd("/name <name>", "Set held item display name."),
                cmd("/name off", "Clear held item display name."),
                cmd("/lore add <line#> <text>", "Insert lore at 1-indexed line."),
                cmd("/lore remove <line#>", "Remove lore at 1-indexed line."),
                cmd("/guicopy [name]", "Save targeted chest to plugins/Tweaks/guicopies/<name>.yml."),
                white("Color: legacy '&' codes and '&#rrggbb' hex."),
                white("/guicopy ray-traces 8 blocks; supports double chests."),
                white("Output YAML includes a paste-ready Java snippet."),
                red("Permissions: tweaks.admin.itemedit, tweaks.admin.guicopy.")
        ), Material.WRITABLE_BOOK, 37, ColorUtil.HELP_GRAD_ITEM_TOOLS, List.of("blocklog"), Permissions.ADMIN_ITEM_EDIT));

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

        return new HelpCategory("features", "Player Features", articles, Material.WRITABLE_BOOK, 30, ColorUtil.HELP_GRAD_PLAYER_FEATURES);
    }

    private HelpCategory buildMinigames() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("resource_hunt", "Resource Hunt", List.of(
                gray("Individual tiered gather hunt in the resource world."),
                cmd("/resource", "Travel to the resource world."),
                white("Target resets each server restart."),
                white("Progress only counts in resource worlds."),
                aqua("Three cumulative tier thresholds:"),
                white("T1 = amount, T2 = T1×multiplier, T3 = T2×multiplier."),
                gold("Each tier crossed grants one 'resource' reward."),
                red("Ender chests are blocked in resource worlds.")
        ), Material.GRASS_BLOCK, 20, ColorUtil.HELP_GRAD_RESOURCE_HUNT, List.of("rewards", "gem_connoisseur")));

        articles.add(new HelpArticle("rewards", "Rewards", List.of(
                gray("Pending-reward inbox for minigames."),
                cmd("/reward claim", "Claim pending rewards."),
                white("Items go to your inventory; overflow drops at your feet.")
        ), Material.ENDER_CHEST, 24, ColorUtil.HELP_GRAD_REWARDS, List.of("resource_hunt", "whack")));

        articles.add(new HelpArticle("whack", "Whack-an-Andrew", List.of(
                gray("Mole-style minigame: hit armor stands as they pop up."),
                aqua("Play:"),
                white("1. Travel to the Whack arena."),
                white("2. Hit stands while they're up."),
                gold("3. Top 3 scorers receive rewards."),
                red("Admin: /whack arena begins setup.")
        ), Material.PLAYER_HEAD, 31, ColorUtil.HELP_GRAD_WHACK, List.of("rewards", "profiles")));

        return new HelpCategory("minigames", "Minigames", articles, Material.TARGET, 32, ColorUtil.HELP_GRAD_MINIGAMES);
    }

    private HelpCategory buildPermissions() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("permissions_overview", "Permissions Overview", List.of(
                gray("Server-side permission system with groups and per-user overrides."),
                aqua("Model:"),
                white("Users may belong to any number of groups (default if unassigned)."),
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
                white("- Manage Members: Add/remove players via toggle."),
                white("- Inheritance: Choose a group to inherit from."),
                white("- Delete Group: Remove group (protected for 'default')."),
                yellow("Changes propagate to all online members instantly."),
                red("Requires permission: tweaks.admin.permissions.")
        ), Material.WRITABLE_BOOK, 24, ColorUtil.HELP_GRAD_PERMS_GROUPS,
                List.of("permissions_groups", "permissions_overview"),
                Permissions.ADMIN_PERMISSIONS));

        articles.add(new HelpArticle("permissions_players", "Players Management", List.of(
                gray("Audit and manage per-player permission overrides."),
                cmd("/tprm", "Open GUI and click 'Players'."),
                aqua("Features:"),
                white("- Lists online and offline players."),
                white("- Search players by name via a name-entry dialog."),
                white("- Shows each player's current group."),
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
                yellow("Direct overrides take precedence over group perms."),
                red("Requires permission: tweaks.admin.permissions.")
        ), Material.PLAYER_HEAD, 31, ColorUtil.HELP_GRAD_PERMS_USERS,
                List.of("permissions_players", "permissions_overview"),
                Permissions.ADMIN_PERMISSIONS));

        articles.add(new HelpArticle("permissions_gui", "Permissions GUI", List.of(
                gray("Dialog-based visual editor for groups, users, and permissions."),
                cmd("/tprm", "Open the main GUI."),
                aqua("Navigation:"),
                white("Main → Groups → Group Hub → (Perms/Members/Inheritance)."),
                white("Main → Players → User Hub → (Perms/Edit Groups/Reset)."),
                white("Lists paginate at 12 entries; use Prev/Next Page buttons."),
                aqua("Interactions:"),
                white("✓ prefix → currently granted/active (click to revoke)."),
                white("✗ prefix → currently denied/inactive (click to grant)."),
                white("Edit Groups: multi-select toggle of group membership."),
                red("Requires permission: tweaks.admin.permissions.")
        ), Material.COMPASS, 33, ColorUtil.HELP_GRAD_PERMS_TPRM_USAGE,
                List.of("permissions_overview", "permissions_groups", "permissions_players"),
                Permissions.ADMIN_PERMISSIONS));

        return new HelpCategory("permissions", "Permissions", articles, Material.BOOKSHELF, 34, ColorUtil.HELP_GRAD_PERMISSIONS);
    }

    private HelpCategory buildProtection() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("protection_claim", "Claim", List.of(
                gray("Mark a rectangle of chunks as your protected territory."),
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
                yellow("Alias: /rg claim"),
                red("Requires permission: " + Permissions.PROTECTION_CLAIM + ".")
        ), Material.OAK_FENCE, 20, ColorUtil.HELP_GRAD_PROTECTION_CLAIM,
                List.of("protection_info", "protection_members", "protection_flags"),
                Permissions.PROTECTION_CLAIM));

        articles.add(new HelpArticle("protection_info", "Region Info", List.of(
                gray("View details about regions at your location or by name."),
                cmd("/region info [name]", "Show details for a specific region."),
                cmd("/rg i", "Quick info for where you are standing."),
                cmd("/region select <name>", "Restore selection boundaries from a region."),
                aqua("Displays:"),
                white("- Region ID, Owner, Parent (if sub-region)."),
                white("- Members list: hover '(N entries)' for full names."),
                white("- Active flag rules (including targeted rules)."),
                white("- Mob-spawn entity lists (ALLOW/DENY_MOB_SPAWN)."),
                yellow("Aliases: /rg i, /rg am, /rg rm"),
                red("Requires permission: " + Permissions.PROTECTION_INFO + ".")
        ), Material.BOOK, 22, ColorUtil.HELP_GRAD_PROTECTION_CLAIM,
                List.of("protection_claim", "protection_flags"),
                Permissions.PROTECTION_INFO));

        articles.add(new HelpArticle("protection_members", "Members & Hierarchy", List.of(
                gray("Manage who can build and how sub-regions interact."),
                cmd("/region addmember <name> <player>", "Add a member."),
                cmd("/region removemember <name> <player>", "Remove a member."),
                cmd("/region addmanager <name> <player>", "Add a manager."),
                cmd("/region removemanager <name> <player>", "Remove a manager."),
                cmd("/region setparent <child> <parent>", "Create a sub-region relationship."),
                aqua("Roles:"),
                white("- Owner: Full control, can unclaim or transfer."),
                white("- Manager: Can edit flags and members/managers."),
                white("- Member: Build access; lowest priority for flags."),
                aqua("Hierarchy:"),
                white("- Sub-region flags override their parent(s)."),
                white("- Priority: GROUP > OWNER > MANAGER > MEMBER > DEFAULT."),
                white("- Membership is independent at each level."),
                yellow("Aliases: /rg am, /rg rm, /rg aman, /rg rman"),
                red("Requires permission: " + Permissions.PROTECTION_MEMBER + ".")
        ), Material.PLAYER_HEAD, 24, ColorUtil.HELP_GRAD_PROTECTION_MEMBERS,
                List.of("protection_claim", "protection_flags"),
                Permissions.PROTECTION_MEMBER));

        articles.add(new HelpArticle("protection_flags", "Precision Flags", List.of(
                gray("Control actions within regions with targeted rules."),
                cmd("/region flag <name> <flag> <value...> [target]", "Set a flag rule."),
                aqua("Flag Types:"),
                white("- Boolean (BLOCK_BREAK, PVP, MOB_SPAWNING, INVINCIBILITY)."),
                white("- Material (ALLOW_BLOCK_BREAK, etc.): use block list."),
                white("- EntityType (ALLOW_MOB_SPAWN, DENY_MOB_SPAWN): use entity list."),
                aqua("Targeting (Boolean only):"),
                white("- [target] can be: owner, manager, member, or group (e.g. group:admin)."),
                white("- Priority: GROUP > OWNER > MANAGER > MEMBER > DEFAULT."),
                green("- Flags override world gamerules (e.g. mobGriefing)."),
                white("- /region flag [flag] defaults to your current location."),
                red("Requires permission: " + Permissions.PROTECTION_FLAG + ".")
        ), Material.OAK_SIGN, 30, ColorUtil.HELP_GRAD_PROTECTION_FLAGS,
                List.of("protection_claim", "protection_info"),
                Permissions.PROTECTION_FLAG));

        articles.add(new HelpArticle("protection_gui", "Region GUI", List.of(
                gray("Manage a region through a clickable Paper Dialog instead of typing flag CLI."),
                cmd("/region gui", "Open the dialog for the region you're standing in."),
                cmd("/region gui <name>", "Open the dialog for a specific region."),
                aqua("Access:"),
                white("- Owners and managers can open their own regions."),
                white("- Admins with " + Permissions.PROTECTION_ADMIN + " can open any region."),
                white("- Standing in overlapping claims picks the innermost (leaf) sub-region."),
                aqua("In the dialog:"),
                white("- Toggle boolean flags and per-role overrides without remembering syntax."),
                white("- Add/remove members and managers."),
                white("- Edit material and entity lists for flags that support them."),
                yellow("Alias: /rg gui"),
                red("Requires permission: " + Permissions.PROTECTION_INFO + ".")
        ), Material.PAINTING, 32, ColorUtil.HELP_GRAD_PROTECTION_FLAGS,
                List.of("protection_flags", "protection_members", "protection_info"),
                Permissions.PROTECTION_INFO));

        articles.add(new HelpArticle("protection_unclaim", "Unclaim", List.of(
                gray("Logically delete a region and its hierarchy links."),
                cmd("/region unclaim <name>", "Remove a region."),
                aqua("Notes:"),
                white("- If it has children, they become root-level regions."),
                white("- Pointers are purged from chunk PDC as players visit."),
                yellow("Alias: /rg unclaim"),
                red("Requires permission: " + Permissions.PROTECTION_UNCLAIM + ".")
        ), Material.BARRIER, 34, ColorUtil.HELP_GRAD_PROTECTION_UNCLAIM,
                List.of("protection_claim", "protection_info"),
                Permissions.PROTECTION_UNCLAIM));

        return new HelpCategory("protection", "Land Protection", articles, Material.OAK_FENCE, 36, ColorUtil.HELP_GRAD_PROTECTION);
    }
}
