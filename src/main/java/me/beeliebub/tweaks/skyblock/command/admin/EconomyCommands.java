package me.beeliebub.tweaks.skyblock.command.admin;

import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.SkyblockDescriptions;
import me.beeliebub.tweaks.skyblock.SkyblockValidation;
import me.beeliebub.tweaks.skyblock.economy.ShopAdminService;
import me.beeliebub.tweaks.skyblock.generator.GeneratorAdminService;
import me.beeliebub.tweaks.skyblock.generator.GeneratorRegistry;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Generator and shop authoring operations for {@code /isadmin}. */
public final class EconomyCommands {
    private final AdminCommandContext context;

    public EconomyCommands(AdminCommandContext context) {
        this.context = context;
    }

    public boolean handleGenerator(CommandSender sender, String[] args) {
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("inspect") && args.length == 3) return inspectGenerator(sender, args[2]);
        if (action.equals("output") && args.length >= 3) {
            String outputAction = args[2].toLowerCase(Locale.ROOT);
            if (outputAction.equals("set") && args.length == 6) {
                GeneratorAdminService.EditResult result = context.generatorAdmin.setOutput(args[3],
                        AdminCommandContext.requiredMaterial(args[4]), AdminArgumentParser.requireDouble(args[5]));
                return context.report(sender, result.success(), "generator output", result.message());
            }
            if (outputAction.equals("remove") && args.length >= 5 && args.length <= 6) {
                Material material = AdminCommandContext.requiredMaterial(args[4]);
                if (!context.requireConfirmation(sender, "generator-output-remove", "generator output "
                        + args[3] + " " + material.name(), 0, "The selected generator output will be removed.",
                        AdminArgumentParser.hasTrailingConfirm(args))) return true;
                GeneratorAdminService.EditResult result = context.generatorAdmin.removeOutput(args[3], material);
                return context.report(sender, result.success(), "generator output", result.message());
            }
        }
        if (action.equals("set") && args.length >= 5 && args[3].equalsIgnoreCase("name")) {
            GeneratorAdminService.EditResult result = context.generatorAdmin.setDisplayName(args[2],
                    AdminCommandContext.join(args, 4));
            return context.report(sender, result.success(), "generator tier", result.message());
        }
        if (action.equals("delete") && args.length >= 3 && args.length <= 4) {
            long references = context.runtime.islandManager().all().stream()
                    .filter(island -> args[2].equalsIgnoreCase(island.generatorTierId())).count();
            if (!context.requireConfirmation(sender, "generator-delete", "generator " + args[2], references,
                    "Islands using this generator tier must be moved before deletion.",
                    AdminArgumentParser.hasTrailingConfirm(args))) return true;
            GeneratorRegistry.DeleteResult result = context.generatorAdmin.delete(args[2]);
            sender.sendMessage(result.deleted() ? Messages.SKYBLOCK.saved("generator tier")
                    : Messages.SKYBLOCK.invalidInput(result.reason() + " (" + result.references() + " reference(s))"));
            return true;
        }
        if (action.equals("create") && args.length >= 3
                && (args.length < 6 || !isMaterial(args[4]) || !isDouble(args[5]))) {
            GeneratorAdminService.EditResult result = context.generatorAdmin.create(args[2],
                    args.length == 3 ? args[2] : AdminCommandContext.join(args, 3));
            return context.report(sender, result.success(), "generator tier", result.message());
        }
        if ((action.equals("create") || action.equals("edit")) && args.length >= 6 && (args.length - 4) % 2 == 0) {
            Map<Material, Double> outputs = new LinkedHashMap<>();
            for (int index = 4; index < args.length; index += 2) {
                outputs.put(AdminCommandContext.requiredMaterial(args[index]),
                        AdminArgumentParser.requireDouble(args[index + 1]));
            }
            GeneratorAdminService.EditResult result = context.generatorAdmin.register(
                    new GeneratorTier(args[2], args[3], outputs));
            return context.report(sender, result.success(), "generator tier", result.message());
        }
        return context.invalidUsage(sender, "generators");
    }

    public boolean handleShop(CommandSender sender, String[] args) {
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("inspect") && args.length == 3) return inspectShop(sender, args[2]);
        if (action.equals("add-held-item")) return context.guiOnly(sender,
                "Shop held-item entries", "shop editor");
        if (action.equals("delete") && args.length >= 3 && args.length <= 4) {
            Material material = AdminCommandContext.requiredMaterial(args[2]);
            if (!context.requireConfirmation(sender, "shop-delete", "shop " + material.name(), 0,
                    "The shop entry will no longer be buyable or sellable.",
                    AdminArgumentParser.hasTrailingConfirm(args))) return true;
            ShopAdminService.DeleteResult result = context.shopAdmin.deleteDetailed(material);
            return context.report(sender, result.success(), "shop entry", result.message());
        }
        if (action.equals("set") && args.length == 6) {
            ShopAdminService.EditResult result = context.shopAdmin.set(AdminCommandContext.requiredMaterial(args[2]),
                    args[3], AdminArgumentParser.requireDouble(args[4]), AdminArgumentParser.requireDouble(args[5]));
            return context.report(sender, result.success(), "shop entry", result.message());
        }
        return context.invalidUsage(sender, "shop");
    }

    private boolean inspectGenerator(CommandSender sender, String id) {
        GeneratorTier tier = context.runtime.generatorRegistry().tier(id).orElse(null);
        if (tier == null) return context.invalid(sender, "unknown generator tier " + id);
        sender.sendMessage(Messages.SKYBLOCK.adminDescription("Generator " + tier.id() + ": "
                + tier.displayName() + ", total weight " + tier.totalWeight()));
        tier.outputs().forEach((material, weight) -> sender.sendMessage(Messages.SKYBLOCK.adminDescription(
                SkyblockDescriptions.generatorOutput(tier, Map.entry(material, weight)))));
        var problems = SkyblockValidation.validateGeneratorTier(tier);
        if (problems.isEmpty()) sender.sendMessage(Messages.SKYBLOCK.adminDescription("Validation: valid"));
        else problems.forEach(problem -> sender.sendMessage(Messages.SKYBLOCK.validationProblem(
                problem.code() + " - " + problem.message())));
        return true;
    }

    private boolean inspectShop(CommandSender sender, String value) {
        Material material = AdminCommandContext.requiredMaterial(value);
        ShopCatalog.Entry entry = context.runtime.shopCatalog().entry(material).orElse(null);
        if (entry == null) return context.invalid(sender, "unknown shop material " + value);
        sender.sendMessage(Messages.SKYBLOCK.adminDescription(SkyblockDescriptions.shopEntry(entry)));
        var problems = SkyblockValidation.validateShopEntry(entry);
        if (problems.isEmpty()) sender.sendMessage(Messages.SKYBLOCK.adminDescription("Validation: valid"));
        else problems.forEach(problem -> sender.sendMessage(Messages.SKYBLOCK.validationProblem(
                problem.code() + " - " + problem.message())));
        return true;
    }

    private static boolean isMaterial(String value) {
        return AdminArgumentParser.parseMaterial(value).isPresent();
    }

    private static boolean isDouble(String value) {
        return AdminArgumentParser.parseDouble(value).isPresent();
    }
}
