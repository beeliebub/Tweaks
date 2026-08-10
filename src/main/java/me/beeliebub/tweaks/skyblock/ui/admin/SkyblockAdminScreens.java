package me.beeliebub.tweaks.skyblock.ui.admin;

import io.papermc.paper.registry.data.dialog.ActionButton;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.SkyblockSetupChecker;
import me.beeliebub.tweaks.skyblock.SkyblockSetupStatus;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminService;
import me.beeliebub.tweaks.skyblock.command.admin.SkyblockAdminServices;
import me.beeliebub.tweaks.skyblock.ui.IslandAdminGUI;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Entry point and hub for the Skyblock administrator Dialog surface. */
@SuppressWarnings("UnstableApiUsage")
public final class SkyblockAdminScreens {
    private final AdminScreenContext context;
    private final TypeScreens types;
    private final ChallengeScreens challenges;
    private final EconomyScreens economy;
    private final WorldScreens worlds;
    private final IslandScreens islands;

    public SkyblockAdminScreens(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime,
                                SkyblockAdminService admin) {
        this(AdminScreenContext.create(plugin, runtime, admin));
    }

    public SkyblockAdminScreens(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime,
                                SkyblockAdminServices services) {
        this(AdminScreenContext.create(plugin, runtime, services));
    }

    public SkyblockAdminScreens(AdminScreenContext context) {
        this.context = context;
        this.types = new TypeScreens(context, this::openHub);
        this.challenges = new ChallengeScreens(context, this::openHub);
        this.economy = new EconomyScreens(context, this::openHub);
        this.worlds = new WorldScreens(context, this::openHub);
        this.islands = new IslandScreens(context, this::openHub);
        context.setSetupContinuation(this::continueSetup);
    }

    public void open(Player player) {
        if (!context.guard(player)) return;
        openHub(player);
    }

    public AdminItemEditor itemEditor() {
        return context.itemEditor;
    }

    public void shutdown() {
        context.shutdown();
    }

    private void openHub(Player player) {
        if (!context.guard(player)) return;
        SkyblockSetupStatus status = context.setupChecker.check();
        List<ActionButton> buttons = new ArrayList<>();
        for (SkyblockSetupStatus.Check check : status.checks()) {
            if (check.actionable()) {
                buttons.add(context.button(check.label(), check.reason(),
                        target -> openScreen(target, check.targetScreen())));
            }
        }
        buttons.add(context.button("Islands", "Browse and administer live islands", islands::open));
        buttons.add(context.button("Types", "Author island types and kits", types::open));
        buttons.add(context.button("Difficulties", "Author island difficulties", types::openDifficulties));
        buttons.add(context.button("Templates", "Capture and inspect templates", worlds::openTemplates));
        buttons.add(context.button("Challenges", "Author challenge progression", challenges::open));
        buttons.add(context.button("Generators", "Author generator tiers", economy::openGenerators));
        buttons.add(context.button("Shop", "Author shop entries", economy::openShop));
        buttons.add(context.button("Spawn", "Record or clear Skyblock spawn", worlds::openSpawn));
        buttons.add(context.button("Settings", "Read-only Skyblock settings; use /tconfig to edit",
                this::openSettings));
        buttons.add(context.button("Reload", "Reload all four registries", target -> {
            try {
                SkyblockAdminService.Result result = context.admin.reload();
                target.sendMessage(result.success() ? Messages.SKYBLOCK.saved("registries")
                        : Messages.SKYBLOCK.invalidInput(result.message()));
            } catch (RuntimeException error) {
                target.sendMessage(Messages.SKYBLOCK.invalidInput("registry reload failed"));
            }
            openHub(target);
        }));
        buttons.add(context.button("Run Setup", "Walk through unmet checklist entries", this::runSetup));

        List<String> body = new ArrayList<>();
        body.add("Islands: " + context.admin.listIslands().size());
        body.add("Types: " + context.runtime.typeRegistry().types().size()
                + ", difficulties: " + context.runtime.typeRegistry().difficulties().size());
        body.add("Challenges: " + context.runtime.challengeRegistry().challenges().size()
                + ", generators: " + context.runtime.generatorRegistry().tiers().size()
                + ", shop entries: " + context.runtime.shopCatalog().entries().size());
        body.addAll(status.checks().stream().map(check -> check.label() + ": " + check.state()
                + " — " + check.reason()).toList());
        context.show(player, Messages.SKYBLOCK.text("Skyblock Administration", NamedTextColor.AQUA),
                body, buttons, null);
    }

    private void runSetup(Player player) {
        if (!context.guard(player)) return;
        context.setupWizards.add(player.getUniqueId());
        continueSetup(player);
    }

    private void continueSetup(Player player) {
        if (!context.guard(player)) {
            context.clearSetup(player);
            return;
        }
        for (SkyblockSetupStatus.Check check : context.setupChecker.check().checks()) {
            if (check.actionable()) {
                context.setSetupTarget(player, check.id());
                openScreen(player, check.targetScreen());
                return;
            }
        }
        context.clearSetup(player);
        player.sendMessage(Messages.SKYBLOCK.adminText("Skyblock setup is complete.", NamedTextColor.GREEN));
        openHub(player);
    }

    private void openScreen(Player player, String screen) {
        if (screen == null) return;
        switch (screen) {
            case "spawn" -> worlds.openSpawn(player);
            case "templates" -> worlds.openTemplates(player);
            case "difficulties" -> types.openDifficulties(player);
            case "types" -> types.open(player);
            case "generators" -> economy.openGenerators(player);
            case "shop" -> economy.openShop(player);
            case "challenges" -> challenges.open(player);
            case "islands" -> islands.open(player);
            default -> openHub(player);
        }
    }

    private void openSettings(Player player) {
        if (!context.guard(player)) return;
        List<String> paths = List.of("world", "grid-pitch-chunks", "island-y", "max-members", "max-homes",
                "max-homes-purchasable", "invite-timeout-seconds", "save-interval-seconds",
                "deletion-chunks-per-tick", "containment-message-cooldown-ms", "template-max-height",
                "template-max-blocks", "max-slot-index", "price.biome-change", "price.home-slot",
                "default-type", "default-difficulty");
        List<String> body = new ArrayList<>();
        body.add("Read-only. Change a value with the exact /tconfig command shown below.");
        for (String path : paths) {
            String full = "skyblock." + path;
            boolean restart = path.equals("world") || path.equals("grid-pitch-chunks");
            body.add(full + (restart ? " (restart required)" : "") + " = "
                    + String.valueOf(context.plugin.getConfig().get(full))
                    + " — /tconfig " + full + " <value>");
        }
        context.show(player, Messages.SKYBLOCK.text("Skyblock Settings", NamedTextColor.AQUA),
                body, List.of(), this::openHub);
    }
}
