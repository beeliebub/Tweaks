package me.beeliebub.tweaks.skyblock.ui.admin;

import static me.beeliebub.tweaks.skyblock.ui.admin.AdminScreenContext.targetValue;

import io.papermc.paper.registry.data.dialog.ActionButton;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.protection.ui.RegionSelection;
import me.beeliebub.tweaks.protection.ui.RegionWand;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminService;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.island.IslandGrid;
import me.beeliebub.tweaks.skyblock.island.SkyblockSpawn;
import me.beeliebub.tweaks.skyblock.template.BukkitTemplateSupport;
import me.beeliebub.tweaks.skyblock.template.TemplateAdminService;
import me.beeliebub.tweaks.skyblock.template.TemplateCapture;
import me.beeliebub.tweaks.utils.GeometryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public final class WorldScreens {
    private final AdminScreenContext context;
    private final JavaPlugin plugin;
    private final SkyblockBootstrap.Runtime runtime;
    private final SkyblockAdminService admin;
    private final TemplateAdminService templateAdmin;
    private final Set<UUID> templateCaptures;
    private final RegionWand regionWand;
    private final Consumer<Player> hub;

    WorldScreens(AdminScreenContext context, Consumer<Player> hub) {
        this.context = context;
        this.plugin = context.plugin;
        this.runtime = context.runtime;
        this.admin = context.admin;
        this.templateAdmin = context.templateAdmin;
        this.templateCaptures = context.templateCaptures;
        this.regionWand = new RegionWand(plugin);
        this.hub = hub;
    }

    public void openTemplates(Player player) {
        if (!guard(player)) return;
        List<ActionButton> buttons = new ArrayList<>();
        for (String id : runtime.templateStore().ids()) buttons.add(button(id, "Preview template", target -> templateDetail(target, id)));
        buttons.add(button("Get selection wand", "Give the configured region-selection wand",
                target -> { regionWand.give(target); openTemplates(target); }));
        buttons.add(button("Capture", "Capture the current wand selection", this::captureTemplate));
        show(player, Messages.SKYBLOCK.text("Templates", NamedTextColor.AQUA), selectionSummary(player), buttons, hub);
    }

    private void templateDetail(Player player, String id) {
        runtime.templateStore().loadAsync(id).whenComplete((template, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!guard(player)) return;
            if (error != null || template == null) { player.sendMessage(Messages.SKYBLOCK.invalidInput("template")); openTemplates(player); return; }
            long references = runtime.typeRegistry().types().stream()
                    .filter(type -> id.equalsIgnoreCase(type.templateId())).count();
            List<ActionButton> buttons = List.of(button("Delete", "Delete this template", target -> AdminConfirm.open(target, "template " + id, references,
                        "Island types referencing this template prevent deletion.", () -> openTemplates(target), actor -> {
                        templateAdmin.deleteCheckedAsync(id).whenComplete((ignored, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!guard(actor)) return;
                            if (failure == null) {
                                AdminAudit.recorded(plugin.getLogger(), actor, "delete", "template " + id,
                                        "present", "deleted");
                                advanceSetup(actor, true);
                            }
                            actor.sendMessage(failure == null ? Messages.SKYBLOCK.saved("template") : Messages.SKYBLOCK.invalidInput(failure.getMessage()));
                            openTemplates(actor);
                        }));
                    }, this::guard, context::reportGuardFailure)));
            show(player, Messages.SKYBLOCK.text("Template: " + id, NamedTextColor.AQUA),
                    List.of(template.width() + "x" + template.height() + "x" + template.depth(),
                            "Block entities: " + template.blockEntities().size()), buttons, this::openTemplates);
        }));
    }

    private void captureTemplate(Player player) {
        if (!guard(player)) return;
        RegionSelection selection = runtime.regionSelection(player.getUniqueId());
        if (selection == null || !selection.isComplete() || selection.world() != runtime.world()) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput("select a template with the region tool first"));
            openTemplates(player);
            return;
        }
        IslandGrid.ChunkBounds bounds = bounds(selection);
        TemplateCapture.CaptureEstimate estimate;
        try { estimate = templateAdmin.estimate(bounds, runtime.world().getMinHeight(), runtime.world().getMaxHeight()); }
        catch (RuntimeException error) { player.sendMessage(Messages.SKYBLOCK.invalidInput("template selection")); openTemplates(player); return; }
        input(player, "Capture Template", List.of("identifier"), List.of("Estimated volume: " + estimate.width() + "x" + estimate.height()
                + "x" + estimate.depth() + " = " + estimate.estimatedBlocks() + " blocks."), (target, values) -> {
            if (!templateCaptures.add(target.getUniqueId())) {
                target.sendMessage(Messages.SKYBLOCK.invalidInput("template capture already in progress"));
                return;
            }
            try {
                String templateId = targetValue(values, "identifier");
                Island.SpawnOffset offset = new Island.SpawnOffset(8, 0, 8);
                var template = templateAdmin.capture(BukkitTemplateSupport.source(runtime.world()), bounds,
                        runtime.world().getMinHeight(), runtime.world().getMaxHeight(), templateId, offset);
                    templateAdmin.save(templateId, template).whenComplete((ignored, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        templateCaptures.remove(target.getUniqueId());
                        if (!guard(target)) return;
                        if (failure == null) {
                            AdminAudit.recorded(plugin.getLogger(), target, "save", "template " + templateId,
                                    "selection", "saved");
                            advanceSetup(target, true);
                        }
                        target.sendMessage(failure == null ? Messages.SKYBLOCK.saved("template") : Messages.SKYBLOCK.invalidInput("template save failed"));
                        openTemplates(target);
                    }));
            } catch (RuntimeException error) {
                templateCaptures.remove(target.getUniqueId());
                target.sendMessage(Messages.SKYBLOCK.invalidInput(error.getMessage() == null ? "template capture" : error.getMessage()));
                openTemplates(target);
            }
        }, this::openTemplates);
    }

    public void openSpawn(Player player) {
        if (!guard(player)) return;
        SkyblockSpawn.SpawnData data = runtime.spawn().data().orElse(null);
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("Get selection wand", "Give the configured region-selection wand",
                target -> { regionWand.give(target); openSpawn(target); }));
         buttons.add(mutationButton("Record Spawn", "Use the current wand selection and player location", this::recordSpawn));
        if (data != null) buttons.add(button("Clear Spawn", "Use the vanilla fallback after clearing", target -> AdminConfirm.open(target,
                "Skyblock spawn", 0, "The recorded spawn and bounds will be removed.", () -> openSpawn(target), actor -> {
                    SkyblockAdminService.Result result = admin.clearSpawn();
                    context.report(actor, result, "spawn", this::openSpawn);
                }, this::guard, context::reportGuardFailure)));
        List<String> body = new ArrayList<>(selectionSummary(player));
        body.add(data == null ? "Not recorded." : "Recorded bounds: " + data.bounds());
        show(player, Messages.SKYBLOCK.text("Spawn", NamedTextColor.AQUA), body, buttons, hub);
    }

    private void recordSpawn(Player player) {
        RegionSelection selection = runtime.regionSelection(player.getUniqueId());
        if (selection == null || !selection.isComplete() || selection.world() != runtime.world()) {
            player.sendMessage(Messages.SKYBLOCK.invalidInput("select spawn bounds with the region tool first"));
            openSpawn(player);
            return;
        }
        IslandGrid.ChunkBounds bounds = bounds(selection);
        var location = player.getLocation();
        SkyblockAdminService.Result result = admin.recordSpawn(new SkyblockSpawn.SpawnData(runtime.world().getKey().asString(), bounds,
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()));
        context.report(player, result, "spawn", this::openSpawn);
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
    private ActionButton mutationButton(String label, String tooltip, Consumer<Player> action) {
        return context.mutationButton(label, tooltip, action);
    }
    private static IslandGrid.ChunkBounds bounds(RegionSelection selection) {
        int minX = Math.min(GeometryUtil.chunkX(selection.pos1()), GeometryUtil.chunkX(selection.pos2()));
        int maxX = Math.max(GeometryUtil.chunkX(selection.pos1()), GeometryUtil.chunkX(selection.pos2()));
        int minZ = Math.min(GeometryUtil.chunkZ(selection.pos1()), GeometryUtil.chunkZ(selection.pos2()));
        int maxZ = Math.max(GeometryUtil.chunkZ(selection.pos1()), GeometryUtil.chunkZ(selection.pos2()));
        return new IslandGrid.ChunkBounds(minX, minZ, maxX, maxZ);
    }

    private List<String> selectionSummary(Player player) {
        RegionSelection selection = runtime.regionSelection(player.getUniqueId());
        if (selection == null) return List.of("Selection: none", "1. Get the selection wand.",
                "2. Set position one and position two in the configured world.");
        if (selection.world() != runtime.world()) return List.of("Selection: wrong world",
                "Move to " + runtime.world().getKey().asString() + " and select again.");
        if (!selection.hasPos1()) return List.of("Selection: position one only",
                "Set position two with the selection wand.");
        if (!selection.hasPos2()) return List.of("Selection: position one only",
                "Set position two with the selection wand.");
        IslandGrid.ChunkBounds chunkBounds = bounds(selection);
        long width = (long) chunkBounds.maxChunkX() - chunkBounds.minChunkX() + 1;
        long depth = (long) chunkBounds.maxChunkZ() - chunkBounds.minChunkZ() + 1;
        long estimated = width * depth * 256L * (runtime.world().getMaxHeight() - runtime.world().getMinHeight());
        return List.of("Selection: complete", "World: " + runtime.world().getKey().asString(),
                "Chunks: " + width + " x " + depth,
                "Estimated blocks: " + estimated,
                "1. Review the dimensions. 2. Choose Capture when ready.");
    }
}
