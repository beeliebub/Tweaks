package me.beeliebub.tweaks.skyblock.command.admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable syntax catalogue for the console-safe {@code /isadmin} command.
 *
 * <p>The entries are deliberately plain data.  A command router can use the same tree for help
 * text, literal tab completion, and the syntax included in invalid-input feedback without
 * depending on a Dialog or a registry implementation.</p>
 */
public final class AdminUsage {
    private static final String COMMAND = "isadmin";
    private static final List<Entry> TOP_LEVEL = buildTopLevel();

    private AdminUsage() {
    }

    /** Returns the ordered, immutable top-level catalogue. */
    public static List<Entry> entries() {
        return TOP_LEVEL;
    }

    /** Alias that makes the root of the catalogue explicit to command routers. */
    public static List<Entry> topLevelEntries() {
        return TOP_LEVEL;
    }

    /** Finds an entry by a whitespace-separated command path, with or without {@code /isadmin}. */
    public static Optional<Entry> find(String topic) {
        if (topic == null || topic.isBlank()) return Optional.empty();
        return findTokens(pathTokens(topic));
    }

    /** Finds an entry by command-path tokens. */
    public static Optional<Entry> find(String... path) {
        return findTokens(pathTokens(path));
    }

    /** Returns all help lines, in declaration order. */
    public static List<HelpLine> helpEntries() {
        return helpEntries("");
    }

    /**
     * Returns help for a topic, or an empty list when the topic is not in the catalogue.  A blank
     * topic returns the complete catalogue.
     */
    public static List<HelpLine> helpEntries(String topic) {
        if (topic == null || topic.isBlank()) {
            List<HelpLine> lines = new ArrayList<>();
            TOP_LEVEL.forEach(entry -> flatten(entry, lines));
            return List.copyOf(lines);
        }
        Optional<Entry> entry = find(topic);
        if (entry.isEmpty()) return List.of();
        List<HelpLine> lines = new ArrayList<>();
        flatten(entry.get(), lines);
        return List.copyOf(lines);
    }

    /** Returns formatted help lines suitable for passing to {@code Messages.SKYBLOCK}. */
    public static List<String> help() {
        return help("");
    }

    /** Returns formatted help lines for one topic. */
    public static List<String> help(String topic) {
        return helpEntries(topic).stream().map(HelpLine::formatted).toList();
    }

    /**
     * Returns the syntax for one topic.  The command prefix is intentionally omitted so the
     * router can decide how to render it in an error message.
     */
    public static Optional<String> syntax(String topic) {
        return find(topic).map(Entry::syntax);
    }

    public static String syntaxSummary(String topic) {
        List<HelpLine> lines = helpEntries(topic);
        if (lines.isEmpty()) return "";
        int start = lines.size() > 1 ? 1 : 0;
        return String.join(" | ", lines.subList(start, lines.size()).stream()
                .map(HelpLine::syntax).toList());
    }

    /** Returns literal completion candidates for the final, possibly partial, argument. */
    public static List<String> suggestions(String[] args) {
        if (args == null || args.length == 0) return literalSuggestions(TOP_LEVEL, "");

        String partial = safe(args[args.length - 1]);
        List<String> path = pathTokens(args, args.length - 1);
        if (path.isEmpty()) return literalSuggestions(TOP_LEVEL, partial);

        // "help" takes a topic rather than a declared child node.  The top-level names are the
        // useful static candidates here; dynamic registry ids are supplied by the router.
        if (path.size() == 1 && path.get(0).equals("help")) {
            return TOP_LEVEL.stream()
                    .map(Entry::token)
                    .filter(token -> !token.equals("help"))
                    .filter(token -> startsWith(token, partial))
                    .toList();
        }

        Optional<Entry> parent = findTokens(path);
        return parent.map(entry -> literalSuggestions(entry.children(), partial)).orElseGet(List::of);
    }

    /** List-based completion overload for routers that already hold command arguments in a list. */
    public static List<String> suggestions(List<String> args) {
        if (args == null) return suggestions((String[]) null);
        return suggestions(args.toArray(String[]::new));
    }

    /** Alias matching Bukkit's terminology. */
    public static List<String> complete(String[] args) {
        return suggestions(args);
    }

    /** Returns literal candidates for a known node without treating any value as a command token. */
    public static List<String> childSuggestions(String topic, String prefix) {
        Optional<Entry> entry = find(topic);
        return entry.map(value -> literalSuggestions(value.children(), prefix)).orElseGet(List::of);
    }

    private static List<Entry> buildTopLevel() {
        return List.of(
                entry("help", "help [topic]", "Show /isadmin syntax and descriptions."),
                entry("status", "status", "Show the Skyblock authoring readiness checklist."),
                entry("spawn", "spawn", "Record the Skyblock spawn from the current protection selection."),
                entry("clearspawn", "clearspawn [confirm]", "Clear the recorded spawn and use the world fallback after confirmation."),
                entry("reload", "reload", "Reload the Skyblock registries."),
                entry("gui", "gui", "Open the complete in-game Skyblock authoring surface."),
                islandUsage(),
                templatesUsage(),
                typesUsage(),
                difficultiesUsage(),
                challengesUsage(),
                generatorsUsage(),
                shopUsage()
        );
    }

    private static Entry islandUsage() {
        return entry("island", "island", "Inspect and administer live Skyblock islands.",
                entry("list", "island list", "List live islands."),
                entry("inspect", "island inspect <owner|id>", "Inspect one live island."),
                entry("teleport", "island teleport <owner|id>", "Teleport an in-game administrator to an island."),
                entry("size", "island size <owner|id> <SMALL|MEDIUM|LARGE>", "Increase an island's size."),
                entry("force-complete", "island force-complete <owner|id> <challenge>",
                        "Force-complete a challenge through the normal reward path."),
                entry("force-delete", "island force-delete <owner|id> [confirm]",
                        "Delete an island after explicit confirmation.")
        );
    }

    private static Entry templatesUsage() {
        return entry("templates", "templates", "Capture, inspect, and delete island templates.",
                entry("list", "templates list", "List template identifiers."),
                entry("save", "templates save <id>", "Save a template from the current protection selection."),
                entry("preview", "templates preview <id>", "Show template dimensions and block-entity count."),
                entry("delete", "templates delete <id> [confirm]", "Delete an unreferenced template after confirmation.")
        );
    }

    private static Entry typesUsage() {
        return entry("types", "types", "Create and edit island types.",
                entry("list", "types list [filter]", "List island types, optionally filtered by id or display name."),
                entry("create", "types create <id> [display...]", "Create an island type with a multiword display name."),
                entry("set", "types set <id> <field> <value...>", "Set one island-type field without replacing the others.",
                        entry("name", "types set <id> name <value...>", "Set the display name."),
                        entry("difficulties", "types set <id> difficulties <difficulty...>", "Set the difficulty ids."),
                        entry("template", "types set <id> template <value...>", "Set the template id."),
                        entry("biome", "types set <id> biome <value...>", "Set the biome."),
                        entry("allowed", "types set <id> allowed <challenge...>", "Set allowed challenge ids.")),
                entry("inspect", "types inspect <id>", "Inspect one island type and its validation problems."),
                entry("kit", "types kit <id>", "Edit the physical kit in the /isadmin gui item editor."),
                entry("allowed", "types allowed <id> <challenge...>", "Legacy alias for setting allowed challenge ids."),
                entry("edit", "types edit <id> <name> <difficulty[,difficulty]> [template] [biome]",
                        "Legacy positional type-edit alias."),
                entry("delete", "types delete <id> [confirm]", "Delete an unreferenced island type after confirmation.")
        );
    }

    private static Entry difficultiesUsage() {
        return entry("difficulties", "difficulties", "Create and edit island difficulties.",
                entry("list", "difficulties list [filter]", "List difficulties, optionally filtered by id or display name."),
                entry("create", "difficulties create <id> [display...]", "Create a difficulty with a multiword display name."),
                entry("set", "difficulties set <id> <field> <value...>", "Set one difficulty field.",
                        entry("name", "difficulties set <id> name <value...>", "Set the display name."),
                        entry("multiplier", "difficulties set <id> multiplier <value>", "Set the progression multiplier."),
                        entry("order", "difficulties set <id> order <value>", "Set the display order.")),
                entry("inspect", "difficulties inspect <id>", "Inspect one difficulty."),
                entry("edit", "difficulties edit <id> <name> <multiplier> [order]",
                        "Legacy positional difficulty-edit alias."),
                entry("delete", "difficulties delete <id> [confirm]", "Delete an unreferenced difficulty after confirmation.")
        );
    }

    private static Entry generatorsUsage() {
        return entry("generators", "generators", "Create and edit weighted cobblestone generator tiers.",
                entry("list", "generators list [filter]", "List generator tiers, optionally filtered."),
                entry("create", "generators create <id> [display...]", "Create a generator tier with a multiword display name."),
                entry("set", "generators set <id> <field> <value...>", "Set one generator-tier field.",
                        entry("name", "generators set <id> name <value...>", "Set the display name.")),
                entry("output", "generators output <set|remove> ...", "Edit one output without replacing the whole table.",
                        entry("set", "generators output set <id> <material> <weight>", "Add or update one output."),
                        entry("remove", "generators output remove <id> <material> [confirm]",
                                "Remove one output after explicit confirmation.")),
                entry("inspect", "generators inspect <id>", "Inspect a tier, output weights, and validation problems."),
                entry("edit", "generators edit <id> <name> <material> <weight> ...",
                        "Legacy whole-table generator-edit alias."),
                entry("delete", "generators delete <id> [confirm]", "Delete an unreferenced generator tier after confirmation.")
        );
    }

    private static Entry shopUsage() {
        return entry("shop", "shop", "Create and edit Skyblock shop entries.",
                entry("list", "shop list [filter]", "List shop entries, optionally filtered."),
                entry("set", "shop set <material> <category> <buy> <sell>", "Set both shop directions for a material."),
                entry("inspect", "shop inspect <material>", "Inspect one shop entry."),
                entry("add-held-item", "shop add-held-item", "Add a held item through the /isadmin gui shop screen."),
                entry("delete", "shop delete <material> [confirm]", "Delete a shop entry after confirmation.")
        );
    }

    private static Entry challengesUsage() {
        return entry("challenges", "challenges", "Create and edit challenge definitions and categories.",
                entry("list", "challenges list [filter]", "List challenges, optionally filtered by id or display name."),
                entry("create", "challenges create <id> [display...]", "Create a challenge with a multiword display name."),
                entry("set", "challenges set <id> <field> <value...>", "Set one challenge field.",
                        entry("name", "challenges set <id> name <value...>", "Set the display name."),
                        entry("description", "challenges set <id> description <value...>", "Set the description."),
                        entry("category", "challenges set <id> category <category>", "Set the challenge category.")),
                entry("inspect", "challenges inspect <id>", "Inspect a challenge, requirements, rewards, and validation problems."),
                entry("validate", "challenges validate [id]", "Validate one challenge or every challenge."),
                entry("requirement", "challenges requirement <add|edit|remove|move> ...",
                        "Edit ordered challenge requirements.",
                        entry("add", "challenges requirement add <id> <tracked|possession> <identifier> <amount>",
                                "Append a tracked or possession requirement."),
                        entry("edit", "challenges requirement edit <id> <index> <tracked|possession> <identifier> <amount>",
                                "Replace one requirement in place."),
                        entry("remove", "challenges requirement remove <id> <index>", "Remove one requirement."),
                        entry("move", "challenges requirement move <id> <from> <to>", "Move one requirement while preserving the list.")),
                entry("reward", "challenges reward <add|edit|remove|move> ...",
                        "Edit ordered challenge rewards.",
                        entry("add", "challenges reward add <id> <money|size|generator|item> <value...>",
                                "Append a non-container reward."),
                        entry("edit", "challenges reward edit <id> <index> <money|size|generator|item> <value...>",
                                "Replace one reward in place."),
                        entry("remove", "challenges reward remove <id> <index>", "Remove one reward."),
                        entry("move", "challenges reward move <id> <from> <to>", "Move one reward while preserving the list.")),
                entry("prerequisites", "challenges prerequisites <id> <challenge...>", "Set ordered prerequisites."),
                entry("any-of", "challenges any-of <id> <required> <challenge...>", "Set an any-of prerequisite group."),
                entry("types", "challenges types <id> <type...>", "Set island-type gating."),
                entry("category", "challenges category <create|set|delete> ...", "Manage challenge categories.",
                        entry("create", "challenges category create <id> [display...]", "Create a category."),
                        entry("set", "challenges category set <id> <name|order> <value...>", "Set a category field."),
                        entry("delete", "challenges category delete <id> [confirm]", "Delete an unreferenced category after confirmation.")),
                entry("edit", "challenges edit <id> <name> <description...>", "Legacy positional challenge-text alias."),
                entry("category-create", "challenges category-create <id> [display...]", "Legacy category-create alias."),
                entry("category-delete", "challenges category-delete <id> [confirm]", "Legacy category-delete alias."),
                entry("category-order", "challenges category-order <id> <order>", "Legacy category-order alias."),
                entry("category-set", "challenges category <id> <category>", "Legacy category-assignment alias."),
                entry("requirement-remove", "challenges requirement-remove <id> <index>", "Legacy requirement-removal alias."),
                entry("requirement-move", "challenges requirement-move <id> <from> <to>", "Legacy requirement-move alias."),
                entry("reward-remove", "challenges reward-remove <id> <index>", "Legacy reward-removal alias."),
                entry("reward-move", "challenges reward-move <id> <from> <to>", "Legacy reward-move alias."),
                entry("delete", "challenges delete <id> [confirm]", "Delete a challenge after confirmation.")
        );
    }

    private static Entry entry(String token, String syntax, String description, Entry... children) {
        return new Entry(token, syntax, description, List.of(children));
    }

    private static Optional<Entry> findTokens(Collection<String> path) {
        if (path == null || path.isEmpty()) return Optional.empty();
        Entry current = null;
        List<Entry> candidates = TOP_LEVEL;
        for (String token : path) {
            if (token == null || token.isBlank()) continue;
            String normalized = token.toLowerCase(Locale.ROOT);
            Optional<Entry> match = candidates.stream()
                    .filter(entry -> entry.token().equals(normalized))
                    .findFirst();
            if (match.isEmpty()) return Optional.empty();
            current = match.get();
            candidates = current.children();
        }
        return Optional.ofNullable(current);
    }

    private static List<String> pathTokens(String topic) {
        if (topic == null || topic.isBlank()) return List.of();
        return pathTokens(topic.trim().split("\\s+"));
    }

    private static List<String> pathTokens(String[] values) {
        return pathTokens(values, values == null ? 0 : values.length);
    }

    private static List<String> pathTokens(String[] values, int limit) {
        if (values == null || limit <= 0) return List.of();
        List<String> tokens = new ArrayList<>();
        int end = Math.min(limit, values.length);
        for (int index = 0; index < end; index++) {
            String value = safe(values[index]);
            if (value.isBlank()) continue;
            for (String token : value.split("\\s+")) {
                if (token.equalsIgnoreCase("/" + COMMAND) || token.equalsIgnoreCase(COMMAND)) continue;
                tokens.add(token.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(tokens);
    }

    private static void flatten(Entry entry, List<HelpLine> lines) {
        lines.add(new HelpLine(entry.syntax(), entry.description()));
        entry.children().forEach(child -> flatten(child, lines));
    }

    private static List<String> literalSuggestions(List<Entry> entries, String prefix) {
        String normalized = safe(prefix).toLowerCase(Locale.ROOT);
        return entries.stream()
                .map(Entry::token)
                .filter(token -> startsWith(token, normalized))
                .toList();
    }

    private static boolean startsWith(String value, String prefix) {
        return value.toLowerCase(Locale.ROOT).startsWith(prefix);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /** One immutable syntax node in the command tree. */
    public record Entry(String token, String syntax, String description, List<Entry> children) {
        public Entry {
            token = Objects.requireNonNull(token, "token").trim().toLowerCase(Locale.ROOT);
            syntax = Objects.requireNonNull(syntax, "syntax").trim();
            description = Objects.requireNonNull(description, "description").trim();
            if (token.isBlank()) throw new IllegalArgumentException("Usage token cannot be blank");
            if (syntax.isBlank()) throw new IllegalArgumentException("Usage syntax cannot be blank");
            if (description.isBlank()) throw new IllegalArgumentException("Usage description cannot be blank");
            children = List.copyOf(children == null ? List.of() : children);
        }

        public boolean hasChildren() {
            return !children.isEmpty();
        }

        public List<String> childTokens() {
            return children.stream().map(Entry::token).toList();
        }

        public Optional<Entry> child(String childToken) {
            if (childToken == null) return Optional.empty();
            String normalized = childToken.trim().toLowerCase(Locale.ROOT);
            return children.stream().filter(child -> child.token().equals(normalized)).findFirst();
        }
    }

    /** Plain help data; the router can turn it into a project-owned Adventure component. */
    public record HelpLine(String syntax, String description) {
        public HelpLine {
            syntax = Objects.requireNonNull(syntax, "syntax").trim();
            description = Objects.requireNonNull(description, "description").trim();
        }

        public String formatted() {
            return "/" + COMMAND + " " + syntax + " - " + description;
        }
    }
}
