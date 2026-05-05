package me.beeliebub.tweaks.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

// Static content provider for the /help system. Categories are defined in
// per-topic builder methods so the constructor stays readable. Cross-references
// (relatedArticles) are validated once at construction; missing IDs log a warning.
public class HelpManager {

    public record HelpArticle(String id, String title, List<Component> content, Material icon, List<String> relatedArticles) {
        public HelpArticle {
            content = List.copyOf(content);
            relatedArticles = relatedArticles == null ? List.of() : List.copyOf(relatedArticles);
        }
    }

    public record HelpCategory(String id, String title, List<HelpArticle> articles, Material icon) {
        public HelpCategory {
            articles = List.copyOf(articles);
        }
    }

    private final Map<String, HelpCategory> categories = new LinkedHashMap<>();
    private final Map<String, HelpArticle> allArticles = new LinkedHashMap<>();
    private final Logger logger;

    public HelpManager() {
        this(Logger.getLogger("Tweaks"));
    }

    public HelpManager(Logger logger) {
        this.logger = logger;
        addCategory(buildTeleportation());
        addCategory(buildEnchantments());
        addCategory(buildQuality());
        addCategory(buildFeatures());
        addCategory(buildMinigames());
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

    public HelpArticle getRandomArticle() {
        if (allArticles.isEmpty()) return null;
        List<HelpArticle> articles = new ArrayList<>(allArticles.values());
        return articles.get(ThreadLocalRandom.current().nextInt(articles.size()));
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

    private static Component cmd(String command, String description) {
        return Component.text(command, NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text(description, NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.suggestCommand(command));
    }

    private static Component gray(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private static Component white(String text) {
        return Component.text(text, NamedTextColor.WHITE);
    }

    private static Component aqua(String text) {
        return Component.text(text, NamedTextColor.AQUA);
    }

    private static Component yellow(String text) {
        return Component.text(text, NamedTextColor.YELLOW);
    }

    private static Component red(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    private static Component green(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private static Component gold(String text) {
        return Component.text(text, NamedTextColor.GOLD);
    }

    private HelpCategory buildTeleportation() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("homes", "Homes", List.of(
                gray("Save personal locations and return to them anytime."),
                Component.empty(),
                cmd("/sethome [name]", "Save your current location as a home (default: 'default')."),
                cmd("/home [name]", "Teleport to one of your saved homes."),
                cmd("/delhome <name>", "Delete a saved home."),
                cmd("/homes", "List all your saved home names."),
                Component.empty(),
                aqua("Limit: 15 homes per player."),
                yellow("Tip: You can overwrite existing homes by reusing the name.")
        ), Material.CYAN_BED, List.of("warps", "back")));

        articles.add(new HelpArticle("warps", "Warps", List.of(
                gray("Shared server-wide locations created by admins."),
                Component.empty(),
                cmd("/warp <name>", "Teleport to a server-wide warp."),
                cmd("/warps", "List all available warps."),
                Component.empty(),
                aqua("Common warps: spawn, newspawn, crates.")
        ), Material.RECOVERY_COMPASS, List.of("homes", "spawn")));

        articles.add(new HelpArticle("tpa", "TPA", List.of(
                gray("Request to teleport to or from other players."),
                Component.empty(),
                cmd("/tpa <player>", "Request to teleport TO a player."),
                cmd("/tpahere <player>", "Request a player to teleport TO YOU."),
                cmd("/tpaccept", "Accept a pending teleport request."),
                cmd("/tpdeny", "Deny a pending teleport request."),
                Component.empty(),
                aqua("Mechanics:"),
                white("- Requests expire after 30 seconds."),
                white("- Clickable [Accept] / [Deny] buttons in chat."),
                red("- Inventory scan when entering resource worlds!")
        ), Material.ENDER_PEARL, List.of("homes", "resource_hunt")));

        articles.add(new HelpArticle("back", "Back", List.of(
                gray("Return to your last location or death spot."),
                Component.empty(),
                cmd("/back", "Teleport back to your previous location."),
                Component.empty(),
                aqua("Features:"),
                white("- Automatically captures death locations."),
                white("- Works after any teleport (home, warp, spawn)."),
                white("- State is saved across server restarts.")
        ), Material.CLOCK, List.of("homes", "tpa")));

        articles.add(new HelpArticle("spawn", "Spawn", List.of(
                gray("Return to the server's central hub."),
                Component.empty(),
                cmd("/spawn", "Teleport to the server spawn point."),
                Component.empty(),
                aqua("Always a safe place to meet other players!")
        ), Material.BEACON, List.of("warps", "profiles")));

        return new HelpCategory("teleportation", "Teleportation", articles, Material.COMPASS);
    }

    private HelpCategory buildEnchantments() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("telekinesis", "Telekinesis", List.of(
                gray("Sends block drops straight to your inventory."),
                Component.empty(),
                aqua("Chain-Breaking:"),
                gray("Breaking one piece of these will break the whole stack:"),
                white("- Sugar Cane, Cactus, Bamboo, Kelp, Vines,"),
                white("  Pointed Dripstone, Chorus Plant."),
                Component.empty(),
                yellow("Tip: If your inventory is full, items drop at your feet.")
        ), Material.HOPPER, List.of("smelter", "tunneller")));

        articles.add(new HelpArticle("smelter", "Smelter", List.of(
                gray("Auto-smelts raw iron, copper, and gold drops."),
                Component.empty(),
                aqua("Conversions:"),
                white("- Raw Iron -> Iron Ingot"),
                white("- Raw Copper -> Copper Ingot"),
                white("- Raw Gold -> Gold Ingot"),
                Component.empty(),
                green("Combines with Telekinesis for ingots straight to pockets!")
        ), Material.BLAST_FURNACE, List.of("telekinesis", "gem_connoisseur")));

        articles.add(new HelpArticle("gem_connoisseur", "Gem Connoisseur", List.of(
                gray("Bonus chance to drop gems from Stone/Deepslate/Netherrack."),
                Component.empty(),
                aqua("Drops (at Level 3):"),
                white("- Coal (1/100), Copper (1/200), Iron (1/300)"),
                white("- Gold (1/400), Redstone (1/500), Lapis (1/600)"),
                white("- Diamond (1/700), Emerald (1/800)"),
                Component.empty(),
                yellow("Tip: Fortune increases the quantity of these drops!")
        ), Material.AMETHYST_CLUSTER, List.of("resource_hunt", "smelter")));

        articles.add(new HelpArticle("tunneller", "Tunneller", List.of(
                gray("Breaks an area of blocks centered on your target."),
                Component.empty(),
                aqua("Mechanics:"),
                white("- Common: 3x3 area"),
                gold("- Legendary: 11x11 area!"),
                red("- Costs durability for EVERY extra block broken."),
                white("- Skips air, liquids, and unbreakable blocks."),
                Component.empty(),
                green("Integrates with Smelter and Gem Connoisseur.")
        ), Material.NETHERITE_PICKAXE, List.of("tiers")));

        articles.add(new HelpArticle("lumberjack", "Lumberjack", List.of(
                gray("Chops entire trees or large mushrooms at once."),
                Component.empty(),
                aqua("Tree Detection:"),
                white("- Must have at least one adjacent leaf block."),
                white("- Supports Nether trees (Wart blocks/Shroomlight)."),
                white("- Maximum 256 blocks per chop."),
                Component.empty(),
                red("Caution: Tool breaks if durability is too low!")
        ), Material.NETHERITE_AXE, List.of("replant", "telekinesis")));

        articles.add(new HelpArticle("replant", "Replant", List.of(
                gray("Auto-replants crops and saplings."),
                Component.empty(),
                aqua("Crops:"),
                white("- Wheat, Carrots, Potatoes, Beetroots, Nether Wart."),
                yellow("- Must be FULLY GROWN to replant."),
                Component.empty(),
                aqua("Saplings:"),
                white("- Automatically plants saplings at base of felled trees.")
        ), Material.FLOWERING_AZALEA, List.of("lumberjack", "telekinesis")));

        articles.add(new HelpArticle("disenchanting", "Disenchanting Bundle", List.of(
                gray("Extract enchantments into books using bundles with lore."),
                Component.empty(),
                aqua("Success Chances:"),
                white("1st: 100% | 2nd: 80% | 3rd: 60% | 4th: 40% | 5th: 20%"),
                Component.empty(),
                green("- Highest quality tier is always extracted first."),
                red("- Failures remove the enchant but give no book."),
                yellow("- Chance does NOT reset on failure."),
                red("- The bundle is consumed upon use.")
        ), Material.BUNDLE, List.of("tiers")));

        articles.add(new HelpArticle("spawner_pickup", "Spawner Pickup", List.of(
                gray("Gives a 20% chance to drop the spawner when mined."),
                Component.empty(),
                white("- Tool tracks uses in lore."),
                red("- Tool breaks after 5 successful pickups!"),
                red("- CANNOT be repaired in anvils.")
        ), Material.SPAWNER, List.of("egg_collector")));

        articles.add(new HelpArticle("egg_collector", "Egg Collector", List.of(
                gray("Chance to drop a spawn egg when you kill a mob."),
                Component.empty(),
                white("- Base chance: 0.5% per kill."),
                red("- Tool breaks after 5 successful drops."),
                red("- CANNOT be repaired in anvils."),
                Component.empty(),
                yellow("Tip: Quality Looting grants re-rolls for this chance!")
        ), Material.SNIFFER_EGG, List.of("spawner_pickup", "tiers")));

        return new HelpCategory("enchantments", "Custom Enchantments", articles, Material.KNOWLEDGE_BOOK);
    }

    private HelpCategory buildQuality() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("tiers", "Quality Tiers", List.of(
                gray("Enchantments can roll higher quality variants."),
                Component.empty(),
                aqua("Tiers:"),
                white("Uncommon < Rare < Epic < Legendary"),
                Component.empty(),
                aqua("Bonuses:"),
                white("- Fortune/Looting: up to 5 extra re-rolls."),
                white("- Luck of the Sea: up to 100% treasure rate."),
                white("- Area Enchantments: up to 11x11 coverage."),
                white("- Silk Touch: can pick up Paths, Farmland, etc.")
        ), Material.NETHER_STAR, List.of("blood_moon", "tunneller", "silk_quality")));

        articles.add(new HelpArticle("silk_quality", "Silk Touch Quality", List.of(
                gray("Quality Silk Touch variants allow picking up special blocks:"),
                Component.empty(),
                white("- Uncommon: Dirt Path"),
                white("- Rare: Farmland"),
                white("- Epic: Reinforced Deepslate"),
                white("- Legendary: Budding Amethyst")
        ), Material.GLOW_ITEM_FRAME, List.of("tiers", "tunneller")));

        articles.add(new HelpArticle("blood_moon", "Blood Moon", List.of(
                gray("Rare server event during full moons (50% chance)."),
                Component.empty(),
                aqua("Effects:"),
                red("- Quality enchant chance: 10% -> 50%!"),
                red("- Sleeping is blocked until dawn."),
                red("- Red boss bar tracks the night's progress."),
                Component.empty(),
                cmd("/fullmoon", "Check when the next full moon occurs.")
        ), Material.CRIMSON_NYLIUM, List.of("tiers")));

        return new HelpCategory("quality", "Enchantment Quality", articles, Material.NETHER_STAR);
    }

    private HelpCategory buildFeatures() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("nicknames", "Nicknames", List.of(
                gray("Set a custom colored display name."),
                Component.empty(),
                cmd("/nick <name>", "Set your nickname with colors (&c, &l, etc)."),
                cmd("/nick off", "Reset your name to default."),
                Component.empty(),
                aqua("Examples:"),
                white("&cRedName, &aGreen&bAqua, &#FF5555HexColor.")
        ), Material.NAME_TAG, List.of("profiles", "tablist")));

        articles.add(new HelpArticle("flight", "Flight", List.of(
                gray("Toggle creative-style flight."),
                Component.empty(),
                cmd("/fly", "Toggle flight on or off."),
                Component.empty(),
                aqua("Access:"),
                white("1. Fly-enabled worlds (Lobby, Archive)."),
                white("2. Earning a specific server advancement.")
        ), Material.FEATHER, List.of("profiles", "teleportation")));

        articles.add(new HelpArticle("itemfilter", "Item Filter", List.of(
                gray("Control which items you pick up."),
                Component.empty(),
                cmd("/if toggle", "Enable or disable the filter."),
                cmd("/if mode", "Switch between Whitelist and Blacklist."),
                cmd("/if add <item>", "Add an item to the current list."),
                cmd("/if remove <item>", "Remove an item from the list."),
                cmd("/if list", "View your active filter list."),
                Component.empty(),
                aqua("Filter state is saved on your profile!")
        ), Material.REPEATER, List.of("telekinesis", "profiles")));

        articles.add(new HelpArticle("toolprotect", "Tool Protect", List.of(
                gray("Blocks using high-tier tools when nearly broken."),
                Component.empty(),
                aqua("Eligibility:"),
                white("- Diamond or Netherite tools."),
                white("- Must carry Epic or Legendary quality enchants."),
                white("- Durability below threshold (Default: 100)."),
                Component.empty(),
                cmd("/toolprotect durability <n>", "Set your custom durability threshold.")
        ), Material.SHIELD, List.of("tiers")));

        articles.add(new HelpArticle("xp_bottles", "XP Storage Bottles", List.of(
                gray("Brew Emeralds into drinkable Experience Potions."),
                Component.empty(),
                aqua("Recipes:"),
                white("- Emerald + Glass Bottle -> 1,395 orbs"),
                white("- Emerald Block + Glass Bottle -> 12,555 orbs"),
                Component.empty(),
                yellow("- Brewing consumes your OWN experience."),
                green("- Potions stack to 64!")
        ), Material.EXPERIENCE_BOTTLE, List.of("profiles", "disenchanting")));

        articles.add(new HelpArticle("profiles", "World Profiles", List.of(
                gray("Separate inventories/XP per world group."),
                Component.empty(),
                aqua("Profile Groups:"),
                white("- Lobby: The lobby world."),
                white("- Standard: Survival, Nether, and End."),
                white("- Archive: The archive world."),
                Component.empty(),
                yellow("Switching worlds automatically swaps items and XP!")
        ), Material.PAINTING, List.of("spawn", "tablist")));

        articles.add(new HelpArticle("tablist", "Tab List", List.of(
                gray("Players in tab are sorted by world profile."),
                Component.empty(),
                aqua("World Tags:"),
                white("[Lobby], [Survival], [Nether], [End], [Resource]"),
                Component.empty(),
                red("- AFK players show an [AFK] suffix."),
                white("- Tags update instantly on world change.")
        ), Material.PAPER, List.of("profiles", "afk")));

        articles.add(new HelpArticle("afk", "AFK", List.of(
                gray("Mark yourself as away-from-keyboard."),
                Component.empty(),
                cmd("/afk", "Toggle AFK status."),
                Component.empty(),
                white("- Automatically active after 10 mins idle."),
                white("- Clears automatically on physical movement."),
                aqua("- Exempts you from sleep requirements!")
        ), Material.TOTEM_OF_UNDYING, List.of("tablist", "profiles")));

        return new HelpCategory("features", "Player Features", articles, Material.WRITABLE_BOOK);
    }

    private HelpCategory buildMinigames() {
        List<HelpArticle> articles = new ArrayList<>();

        articles.add(new HelpArticle("resource_hunt", "Resource Hunt", List.of(
                gray("Server-wide race to gather a target resource."),
                Component.empty(),
                aqua("Rules:"),
                white("- Target resets every server restart."),
                white("- Progress only counts in Resource Worlds."),
                gold("- First winner gets a TRIPLE reward!"),
                red("- Ender Chests are BLOCKED in these worlds."),
                Component.empty(),
                cmd("/resource", "Go to the resource gathering world.")
        ), Material.RAW_GOLD_BLOCK, List.of("rewards", "gem_connoisseur")));

        articles.add(new HelpArticle("whack", "Whack-an-Andrew", List.of(
                gray("Mole-style minigame hitting armor stands."),
                Component.empty(),
                aqua("How to play:"),
                white("1. Go to the Whack arena."),
                white("2. Hit armor stands as they pop up."),
                gold("3. Top 3 scorers win rewards at the end!"),
                Component.empty(),
                red("Admin Setup:"),
                gray("- Use /whack arena to start setup.")
        ), Material.ARMOR_STAND, List.of("rewards", "profiles")));

        articles.add(new HelpArticle("rewards", "Rewards", List.of(
                gray("System for creating and distributing items."),
                Component.empty(),
                cmd("/reward claim", "Receive your pending minigame rewards."),
                Component.empty(),
                white("Items are added to inventory (overflow drops).")
        ), Material.ENDER_CHEST, List.of("resource_hunt", "whack")));

        return new HelpCategory("minigames", "Minigames", articles, Material.TARGET);
    }
}