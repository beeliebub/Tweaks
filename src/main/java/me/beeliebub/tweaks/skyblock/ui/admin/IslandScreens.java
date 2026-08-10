package me.beeliebub.tweaks.skyblock.ui.admin;

import static me.beeliebub.tweaks.skyblock.ui.admin.AdminScreenContext.targetValue;

import io.papermc.paper.registry.data.dialog.ActionButton;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminService;
import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandSize;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public final class IslandScreens {
    private final AdminScreenContext context;
    private final JavaPlugin plugin;
    private final SkyblockBootstrap.Runtime runtime;
    private final SkyblockAdminService admin;
    private final Consumer<Player> hub;

    IslandScreens(AdminScreenContext context, Consumer<Player> hub) {
        this.context = context;
        this.plugin = context.plugin;
        this.runtime = context.runtime;
        this.admin = context.admin;
        this.hub = hub;
    }

    public void open(Player player) {
        openIslands(player, 0, "");
    }

    private void openIslands(Player player) {
        openIslands(player, 0, "");
    }

    private void openIslands(Player player, int pageNumber, String filter) {
        if (!guard(player)) return;
        List<Island> islands = admin.listIslands();
        String normalizedFilter = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        List<Island> filtered = islands.stream().filter(island -> {
            String owner = String.valueOf(Bukkit.getOfflinePlayer(island.owner()).getName());
            return (island.id() + " " + owner).toLowerCase(Locale.ROOT).contains(normalizedFilter);
        }).toList();
        AdminPage.Page<Island> page = AdminPage.create(filtered, pageNumber, "", island -> island.id());
        List<ActionButton> buttons = new ArrayList<>();
        for (Island island : page.values()) buttons.add(button(island.id(),
                "Owner: " + String.valueOf(Bukkit.getOfflinePlayer(island.owner()).getName()) + ", members: " + island.memberCount(),
                target -> islandDetail(target, island.id())));
        if (page.hasPrevious()) buttons.add(button("Previous", "Previous island page", target -> openIslands(target, page.page() - 1, normalizedFilter)));
        if (page.hasNext()) buttons.add(button("Next", "Next island page", target -> openIslands(target, page.page() + 1, normalizedFilter)));
        buttons.add(button("Filter", "Literal island id or owner filter", target -> input(target, "Island Filter", List.of("filter"),
                List.of("Filtering is literal text matching."), (actor, values) -> openIslands(actor, 0, targetValue(values, "filter")),
                actor -> openIslands(actor, page.page(), normalizedFilter))));
        show(player, Messages.SKYBLOCK.text("Islands (" + islands.size() + ")", NamedTextColor.AQUA),
                List.of("Live island administration is limited to inspect, teleport, resize, completion, and deletion.",
                        "Page: " + page.label()), buttons,
                hub);
    }

    private void islandDetail(Player player, String id) {
        Island island = runtime.islandManager().byId(id).orElse(null);
        if (island == null || runtime.deletion() != null && runtime.deletion().isDeleting(id)) { openIslands(player); return; }
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("Teleport", "Teleport to island spawn", target -> {
            if (!guard(target)) return;
            Island current = runtime.islandManager().byId(id).orElse(null);
            if (current == null || runtime.deletion() != null && runtime.deletion().isDeleting(id)) {
                openIslands(target);
                return;
            }
            target.teleport(runtime.islandManager().spawnLocation(current, runtime.world()));
        }));
        buttons.add(button("Resize", "Increase island size", target -> resizeIsland(target, id)));
        buttons.add(button("Complete challenge", "Force-complete a challenge", target -> completeChallenge(target, id)));
        buttons.add(button("Force delete", "Delete this island", target -> AdminConfirm.open(target, "island " + id, island.memberCount(),
                "The island, wallet, and member inventories will be removed.", () -> openIslands(target), actor -> {
                    SkyblockAdminService.Result result = admin.forceDelete(runtime.islandManager().byId(id).orElse(null));
                    report(actor, result, "island deletion");
                    openIslands(actor);
                }, this::guard)));
        show(player, Messages.SKYBLOCK.text("Island: " + id, NamedTextColor.AQUA),
                List.of("Owner: " + Bukkit.getOfflinePlayer(island.owner()).getName(), "Members: " + island.memberCount(),
                        "Type: " + island.typeId(), "Size: " + island.size()), buttons, this::openIslands);
    }

    private void resizeIsland(Player player, String id) {
        List<ActionButton> buttons = new ArrayList<>();
        Island island = runtime.islandManager().byId(id).orElse(null);
        if (island == null) { openIslands(player); return; }
        for (IslandSize size : IslandSize.values()) if (size.chunks() > island.size().chunks()) buttons.add(button(size.name(), "Increase to " + size.name(), target -> {
            SkyblockAdminService.Result result = admin.setSize(runtime.islandManager().byId(id).orElse(null), size);
            report(target, result, "island size");
            islandDetail(target, id);
        }));
        show(player, Messages.SKYBLOCK.text("Resize Island", NamedTextColor.AQUA), List.of("Only larger sizes are available."), buttons,
                target -> islandDetail(target, id));
    }

    private void completeChallenge(Player player, String id) {
        Island island = runtime.islandManager().byId(id).orElse(null);
        if (island == null) { openIslands(player); return; }
        List<ActionButton> buttons = runtime.challengeRegistry().challenges().stream()
                .filter(challenge -> runtime.challengeRegistry().availableTo(challenge, island))
                .map(challenge -> button(challenge.id(), challenge.displayName(), target -> {
                    SkyblockAdminService.Result result = admin.forceComplete(
                            runtime.islandManager().byId(id).orElse(null), challenge.id(), target);
                    if (result.success()) {
                        AdminAudit.recorded(plugin.getLogger(), target, "change", "challenge " + challenge.id(),
                                "incomplete", "completed");
                        advanceSetup(target, true);
                    }
                    target.sendMessage(result.success() ? Messages.SKYBLOCK.challengeClaimed(challenge.id())
                            : Messages.SKYBLOCK.invalidInput(result.message()));
                    islandDetail(target, id);
                })).toList();
        show(player, Messages.SKYBLOCK.text("Complete Challenge", NamedTextColor.AQUA),
                List.of("Choose an available challenge for this island type."), buttons,
                target -> islandDetail(target, id));
    }

    private boolean guard(Player player) { return context.guard(player); }
    private void advanceSetup(Player player, boolean success) { context.advanceSetup(player, success); }
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
}
