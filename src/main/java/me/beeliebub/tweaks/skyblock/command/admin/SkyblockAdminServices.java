package me.beeliebub.tweaks.skyblock.command.admin;

import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeAdminService;
import me.beeliebub.tweaks.skyblock.economy.ShopAdminService;
import me.beeliebub.tweaks.skyblock.generator.GeneratorAdminService;
import me.beeliebub.tweaks.skyblock.template.TemplateAdminService;
import me.beeliebub.tweaks.skyblock.type.TypeAdminService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Immutable collection of the admin mutation services shared by command and Dialog surfaces. */
public final class SkyblockAdminServices {
    private final SkyblockAdminService skyblockAdmin;
    private final TemplateAdminService templateAdmin;
    private final ChallengeAdminService challengeAdmin;
    private final GeneratorAdminService generatorAdmin;
    private final TypeAdminService typeAdmin;
    private final ShopAdminService shopAdmin;

    private SkyblockAdminServices(SkyblockAdminService skyblockAdmin,
                                  TemplateAdminService templateAdmin,
                                  ChallengeAdminService challengeAdmin,
                                  GeneratorAdminService generatorAdmin,
                                  TypeAdminService typeAdmin,
                                  ShopAdminService shopAdmin) {
        this.skyblockAdmin = Objects.requireNonNull(skyblockAdmin, "skyblockAdmin");
        this.templateAdmin = Objects.requireNonNull(templateAdmin, "templateAdmin");
        this.challengeAdmin = Objects.requireNonNull(challengeAdmin, "challengeAdmin");
        this.generatorAdmin = Objects.requireNonNull(generatorAdmin, "generatorAdmin");
        this.typeAdmin = Objects.requireNonNull(typeAdmin, "typeAdmin");
        this.shopAdmin = Objects.requireNonNull(shopAdmin, "shopAdmin");
    }

    public static SkyblockAdminServices create(JavaPlugin plugin, SkyblockBootstrap.Runtime runtime) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(runtime, "runtime");
        return new SkyblockAdminServices(
                new SkyblockAdminService(plugin, runtime),
                new TemplateAdminService(runtime.templateStore(), runtime.typeRegistry(),
                        runtime.config(), plugin),
                new ChallengeAdminService(runtime.challengeRegistry(), runtime.typeRegistry(), plugin),
                new GeneratorAdminService(runtime.generatorRegistry(), runtime.islandManager(), plugin),
                new TypeAdminService(runtime.typeRegistry(), runtime.islandManager(), plugin),
                new ShopAdminService(runtime.shopCatalog(), plugin));
    }

    public SkyblockAdminService skyblockAdmin() {
        return skyblockAdmin;
    }

    public TemplateAdminService templateAdmin() {
        return templateAdmin;
    }

    public ChallengeAdminService challengeAdmin() {
        return challengeAdmin;
    }

    public GeneratorAdminService generatorAdmin() {
        return generatorAdmin;
    }

    public TypeAdminService typeAdmin() {
        return typeAdmin;
    }

    public ShopAdminService shopAdmin() {
        return shopAdmin;
    }
}
