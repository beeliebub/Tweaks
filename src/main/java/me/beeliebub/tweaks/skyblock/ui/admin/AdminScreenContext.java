package me.beeliebub.tweaks.skyblock.ui.admin;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.permissions.Permissions;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.SkyblockSetupChecker;
import me.beeliebub.tweaks.skyblock.SkyblockSetupStatus;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeAdminService;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminService;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminServices;
import me.beeliebub.tweaks.skyblock.economy.ShopAdminService;
import me.beeliebub.tweaks.skyblock.generator.GeneratorAdminService;
import me.beeliebub.tweaks.skyblock.generator.GeneratorRegistry;
import me.beeliebub.tweaks.skyblock.type.TypeAdminService;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/** Shared dependencies, dialog primitives, and guarded outcome reporting for admin screens. */
@SuppressWarnings("UnstableApiUsage")
public final class AdminScreenContext {
    final JavaPlugin plugin;
    final SkyblockBootstrap.Runtime runtime;
    final SkyblockAdminService admin;
    final TypeAdminService typeAdmin;
    final ChallengeAdminService challengeAdmin;
    final GeneratorAdminService generatorAdmin;
    final ShopAdminService shopAdmin;
    final me.beeliebub.tweaks.skyblock.template.TemplateAdminService templateAdmin;
    final SkyblockSetupChecker setupChecker;
    final AdminItemEditor itemEditor;
    final Set<UUID> templateCaptures = ConcurrentHashMap.newKeySet();
    final Set<UUID> setupWizards = ConcurrentHashMap.newKeySet();
    final ConcurrentHashMap<UUID, String> setupTargets = new ConcurrentHashMap<>();
    private Consumer<Player> setupContinuation = player -> { };

    public AdminScreenContext(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime,
                              SkyblockAdminService admin, TypeAdminService typeAdmin,
                              ChallengeAdminService challengeAdmin, GeneratorAdminService generatorAdmin,
                              ShopAdminService shopAdmin,
                              me.beeliebub.tweaks.skyblock.template.TemplateAdminService templateAdmin,
                              SkyblockSetupChecker setupChecker, AdminItemEditor itemEditor) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.admin = admin;
        this.typeAdmin = typeAdmin;
        this.challengeAdmin = challengeAdmin;
        this.generatorAdmin = generatorAdmin;
        this.shopAdmin = shopAdmin;
        this.templateAdmin = templateAdmin;
        this.setupChecker = setupChecker;
        this.itemEditor = itemEditor == null ? new AdminItemEditor(plugin) : itemEditor;
    }

    public AdminScreenContext(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime,
                              SkyblockAdminServices services, SkyblockSetupChecker setupChecker,
                              AdminItemEditor itemEditor) {
        this(plugin, runtime, services.skyblockAdmin(), services.typeAdmin(), services.challengeAdmin(),
                services.generatorAdmin(), services.shopAdmin(), services.templateAdmin(), setupChecker, itemEditor);
    }

    public static AdminScreenContext create(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime,
                                            SkyblockAdminService admin) {
        AdminItemEditor editor = runtime.adminItemEditor();
        if (editor == null) {
            editor = new AdminItemEditor(plugin);
            plugin.getServer().getPluginManager().registerEvents(editor, plugin);
        }
        return new AdminScreenContext(plugin, runtime, admin,
                new TypeAdminService(runtime.typeRegistry(), runtime.islandManager(), plugin),
                new ChallengeAdminService(runtime.challengeRegistry(), runtime.typeRegistry(), plugin),
                new GeneratorAdminService(runtime.generatorRegistry(), runtime.islandManager(), plugin),
                new ShopAdminService(runtime.shopCatalog(), plugin),
                new me.beeliebub.tweaks.skyblock.template.TemplateAdminService(runtime.templateStore(),
                        runtime.typeRegistry(), runtime.config(), plugin),
                new SkyblockSetupChecker(plugin, runtime), editor);
    }

    public static AdminScreenContext create(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime,
                                            SkyblockAdminServices services) {
        AdminItemEditor editor = runtime.adminItemEditor();
        if (editor == null) {
            editor = new AdminItemEditor(plugin);
            plugin.getServer().getPluginManager().registerEvents(editor, plugin);
        }
        return new AdminScreenContext(plugin, runtime, services,
                new SkyblockSetupChecker(plugin, runtime), editor);
    }

    public boolean guard(Player player) {
        return plugin != null && plugin.isEnabled() && player != null && player.isOnline()
                && player.hasPermission(Permissions.ADMIN_SKYBLOCK)
                && runtime != null && runtime.world() != null && player.getWorld() == runtime.world();
    }

    public void reportGuardFailure(Player player) {
        if (player == null || plugin == null || !plugin.isEnabled()) return;
        if (!player.isOnline()) {
            player.sendMessage(Messages.SKYBLOCK.adminOffline());
        } else if (!player.hasPermission(Permissions.ADMIN_SKYBLOCK)) {
            player.sendMessage(Messages.SKYBLOCK.adminNoPermission());
        } else if (runtime == null || runtime.world() == null || player.getWorld() != runtime.world()) {
            String worldKey = runtime == null || runtime.world() == null || runtime.world().getKey() == null
                    ? "the configured Skyblock world" : runtime.world().getKey().asString();
            player.sendMessage(Messages.SKYBLOCK.wrongWorld(worldKey));
        }
    }

    public void advanceSetup(Player player, boolean success) {
        if (!success || player == null || !setupWizards.contains(player.getUniqueId())) return;
        String targetId = setupTargets.get(player.getUniqueId());
        if (targetId == null || setupChecker.check().checks().stream()
                .noneMatch(check -> check.id().equals(targetId)
                        && check.state() == SkyblockSetupStatus.State.SATISFIED)) return;
        setupTargets.remove(player.getUniqueId(), targetId);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!guard(player)) {
                setupWizards.remove(player.getUniqueId());
                return;
            }
            setupContinuation.accept(player);
        });
    }

    void setSetupContinuation(Consumer<Player> setupContinuation) {
        this.setupContinuation = setupContinuation == null ? player -> { } : setupContinuation;
    }

    void setSetupTarget(Player player, String targetId) {
        if (player == null || targetId == null || targetId.isBlank()) return;
        setupTargets.put(player.getUniqueId(), targetId);
    }

    void clearSetup(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        setupWizards.remove(id);
        setupTargets.remove(id);
    }

    public void report(Player player, Object rawResult, String subject) {
        report(player, rawResult, subject, null);
    }

    public void report(Player player, Object rawResult, String subject, Consumer<Player> afterSuccess) {
        Outcome outcome = Outcome.from(rawResult);
        outcome.persistence().whenComplete((ignored, error) -> {
            if (plugin == null || !plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!guard(player)) {
                    reportGuardFailure(player);
                    return;
                }
                if (error != null) {
                    player.sendMessage(Messages.SKYBLOCK.invalidInput(persistenceFailure(outcome.message(), error)));
                    return;
                }
                if (outcome.success()) {
                    AdminAudit.recorded(plugin.getLogger(), player, outcome.action(), subject,
                            "current", outcome.message());
                    player.sendMessage(Messages.SKYBLOCK.saved(subject));
                    advanceSetup(player, true);
                    if (afterSuccess != null && !setupWizards.contains(player.getUniqueId())) {
                        afterSuccess.accept(player);
                    }
                } else {
                    player.sendMessage(Messages.SKYBLOCK.invalidInput(outcome.message()));
                }
            });
        });
    }

    public void input(Player player, String title, java.util.List<String> keys, java.util.List<String> body,
                      InputAction save, Consumer<Player> cancel) {
        if (!guard(player)) return;
        var inputs = keys.stream().map(key -> DialogInput.text(key, Messages.SKYBLOCK.text(
                        "identifier".equals(key) ? "id" : key, NamedTextColor.GRAY))
                .maxLength(256).build()).toList();
        AtomicBoolean submitted = new AtomicBoolean();
        ActionButton submit = ActionButton.builder(Messages.SKYBLOCK.text("Save", NamedTextColor.GREEN))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player target && guard(target)) {
                        if (!submitted.compareAndSet(false, true)) {
                            target.sendMessage(Messages.SKYBLOCK.adminAlreadySubmitted());
                            return;
                        }
                        try {
                            save.accept(target, view::getText);
                        } catch (RuntimeException error) {
                            target.sendMessage(Messages.SKYBLOCK.invalidInput(
                                    error.getMessage() == null ? "input" : error.getMessage()));
                        }
                    } else if (audience instanceof Player target) {
                        reportGuardFailure(target);
                    }
                }, unlimitedClicks())).build();
        ActionButton back = ActionButton.builder(Messages.SKYBLOCK.text("Cancel", NamedTextColor.GRAY))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player target && guard(target) && cancel != null) {
                        if (!submitted.compareAndSet(false, true)) {
                            target.sendMessage(Messages.SKYBLOCK.adminAlreadySubmitted());
                            return;
                        }
                        cancel.accept(target);
                    } else if (audience instanceof Player target) {
                        reportGuardFailure(target);
                    }
                }, unlimitedClicks())).build();
        DialogBase base = DialogBase.builder(Messages.SKYBLOCK.text(title, NamedTextColor.AQUA, TextDecoration.BOLD))
                .body(body.stream().map(line -> DialogBody.plainMessage(
                        Messages.SKYBLOCK.text(line, NamedTextColor.WHITE))).toList())
                .inputs(inputs).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(DialogType.confirmation(submit, back))));
    }

    public void show(Player player, Component title, java.util.List<String> body,
                     java.util.List<ActionButton> buttons, Consumer<Player> back) {
        if (!guard(player)) return;
        List<ActionButton> actions = buttons == null ? new ArrayList<>() : new ArrayList<>(buttons);
        ActionButton backButton = back == null ? null : button("Back", "Return to the previous screen", back);
        if (actions.isEmpty() && backButton != null) {
            actions.add(backButton);
            backButton = null;
        }
        if (actions.isEmpty()) {
            actions.add(button("Close", "No administrator actions are available", target ->
                    target.sendMessage(Messages.SKYBLOCK.invalidInput("no available administrator actions"))));
        }
        ActionButton finalBackButton = backButton;
        DialogBase base = DialogBase.builder(title)
                .body(body.stream().map(line -> DialogBody.plainMessage(
                        Messages.SKYBLOCK.text(line, NamedTextColor.WHITE))).toList())
                .canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(actions, finalBackButton, 2))));
    }

    public ActionButton button(String label, String tooltip, Consumer<Player> action) {
        return ActionButton.builder(Messages.SKYBLOCK.text(label, NamedTextColor.AQUA))
                .tooltip(Messages.SKYBLOCK.text(tooltip == null ? "Skyblock administration" : tooltip,
                        NamedTextColor.GRAY))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player target && guard(target)) {
                        action.accept(target);
                    } else if (audience instanceof Player target) {
                        reportGuardFailure(target);
                    }
                }, unlimitedClicks())).build();
    }

    public ActionButton mutationButton(String label, String tooltip, Consumer<Player> action) {
        AtomicBoolean submitted = new AtomicBoolean();
        return ActionButton.builder(Messages.SKYBLOCK.text(label, NamedTextColor.AQUA))
                .tooltip(Messages.SKYBLOCK.text(tooltip == null ? "Skyblock administration" : tooltip,
                        NamedTextColor.GRAY))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player target && guard(target)) {
                        if (!submitted.compareAndSet(false, true)) {
                            target.sendMessage(Messages.SKYBLOCK.adminAlreadySubmitted());
                            return;
                        }
                        try {
                            action.accept(target);
                        } catch (RuntimeException error) {
                            target.sendMessage(Messages.SKYBLOCK.invalidInput(
                                    error.getMessage() == null ? "administrator action" : error.getMessage()));
                        }
                    } else if (audience instanceof Player target) {
                        reportGuardFailure(target);
                    }
                }, unlimitedClicks())).build();
    }

    public void shutdown() {
        itemEditor.shutdown();
    }

    static String targetValue(Function<String, String> values, String key) {
        String value = values.apply(key);
        if (value == null) throw new IllegalArgumentException("missing input: " + key);
        return value.trim();
    }

    private static ClickCallback.Options unlimitedClicks() {
        return ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();
    }

    private static String persistenceFailure(String fallback, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    public record Outcome(boolean success, String message, String action,
                          CompletableFuture<Void> persistence) {
        public Outcome(boolean success, String message, String action) {
            this(success, message, action, CompletableFuture.completedFuture(null));
        }

        public Outcome {
            persistence = persistence == null ? CompletableFuture.completedFuture(null) : persistence;
        }

        static Outcome from(Object rawResult) {
            if (rawResult instanceof Outcome outcome) {
                return outcome;
            }
            if (rawResult instanceof ChallengeAdminService.EditResult result) {
                return new Outcome(result.success(), result.message(), "change", result.persistence());
            }
            if (rawResult instanceof TypeAdminService.EditResult result) {
                return new Outcome(result.success(), result.message(), "change", result.persistence());
            }
            if (rawResult instanceof GeneratorAdminService.EditResult result) {
                return new Outcome(result.success(), result.message(), "change", result.persistence());
            }
            if (rawResult instanceof ShopAdminService.EditResult result) {
                return new Outcome(result.success(), result.message(), "change", result.persistence());
            }
            if (rawResult instanceof ShopAdminService.DeleteResult result) {
                return new Outcome(result.success(), result.message(), "delete", result.persistence());
            }
            if (rawResult instanceof TypeRegistry.DeleteResult result) {
                return new Outcome(result.deleted(), result.reason(), "delete", result.persistence());
            }
            if (rawResult instanceof GeneratorRegistry.DeleteResult result) {
                return new Outcome(result.deleted(), result.reason(), "delete", result.persistence());
            }
            if (rawResult instanceof me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminService.Result result) {
                return new Outcome(result.success(), result.message(), "change");
            }
            if (rawResult instanceof Boolean value) {
                return new Outcome(value, value ? "saved" : "rejected", "change");
            }
            throw new IllegalArgumentException("Unsupported admin outcome: " + rawResult);
        }
    }

    @FunctionalInterface
    public interface InputAction {
        void accept(Player target, java.util.function.Function<String, String> values);
    }
}
