package me.beeliebub.tweaks.skyblock;

import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRequirement;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.generator.GeneratorTier;
import me.beeliebub.tweaks.skyblock.island.SkyblockSpawn;
import me.beeliebub.tweaks.skyblock.type.IslandType;
import me.beeliebub.tweaks.skyblock.type.IslandDifficulty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Computes the deterministic setup checklist without opening a Paper Dialog. */
public final class SkyblockSetupChecker {
    private final SkyblockBootstrap.Runtime runtime;

    public SkyblockSetupChecker(SkyblockBootstrap.Runtime runtime) {
        this.runtime = runtime;
    }

    /** Compatibility constructor retained for callers that previously passed the plugin. */
    public SkyblockSetupChecker(org.bukkit.plugin.java.JavaPlugin ignored,
                                SkyblockBootstrap.Runtime runtime) {
        this(runtime);
    }

    public SkyblockSetupStatus check() {
        List<SkyblockSetupStatus.Check> checks = new ArrayList<>();
        if (runtime == null) {
            checks.add(check("world", "Skyblock world", SkyblockSetupStatus.State.BLOCKED,
                    "Skyblock runtime is unavailable.", null));
            return new SkyblockSetupStatus(checks);
        }

        checks.add(check("world", "World loaded and profile exclusive", SkyblockSetupStatus.State.SATISFIED,
                runtime.world() == null ? "world unavailable" : runtime.world().getKey().asString()
                        + " / profile " + runtime.profile(), null));
        checks.add(advisory("nether-portals", "Nether portals disabled", "disabled-nether-portal-worlds"));
        checks.add(advisory("end-portals", "End portals disabled", "disabled-end-portal-worlds"));
        checks.add(advisory("sethome", "/sethome disabled", "teleport.sethome-disabled-worlds"));

        SkyblockSpawn spawn = runtime.spawn();
        boolean hasSpawn = spawn != null && spawn.isRecorded();
        checks.add(check("spawn", "Spawn recorded", hasSpawn ? SkyblockSetupStatus.State.SATISFIED
                        : SkyblockSetupStatus.State.INCOMPLETE,
                hasSpawn ? spawn.data().map(value -> value.bounds().toString()).orElse("recorded")
                        : "Record a spawn with the region wand.", "spawn"));

        List<String> templateIds = templateIds();
        checks.add(check("templates", "At least one template", templateIds.isEmpty()
                        ? SkyblockSetupStatus.State.INCOMPLETE : SkyblockSetupStatus.State.SATISFIED,
                templateIds.isEmpty() ? "Capture a template with the region wand." : templateIds.size() + " template(s).",
                "templates"));

        boolean hasDifficulty = runtime.typeRegistry() != null && !safe(runtime.typeRegistry().difficulties()).isEmpty();
        checks.add(check("difficulties", "At least one difficulty", hasDifficulty
                        ? SkyblockSetupStatus.State.SATISFIED : SkyblockSetupStatus.State.BLOCKED,
                hasDifficulty ? "Configured." : "Create a difficulty before creating an island.", "difficulties"));

        boolean hasType = runtime.typeRegistry() != null && !safe(runtime.typeRegistry().types()).isEmpty();
        checks.add(check("types", "At least one island type", hasType
                        ? SkyblockSetupStatus.State.SATISFIED : SkyblockSetupStatus.State.BLOCKED,
                hasType ? "Configured." : "Create an island type before creating an island.", "types"));

        boolean typesValid = hasType && safe(runtime.typeRegistry().types()).stream()
                .allMatch(type -> validType(type, runtime));
        checks.add(check("type-references", "Island type references resolve", typesValid
                        ? SkyblockSetupStatus.State.SATISFIED : SkyblockSetupStatus.State.BLOCKED,
                typesValid ? "All type references resolve." : "A type references a missing template or difficulty.",
                "types"));

        boolean hasGenerator = runtime.generatorRegistry() != null
                && safe(runtime.generatorRegistry().tiers()).stream().anyMatch(tier -> !tier.id().equals("default"));
        checks.add(check("generators", "Generator tier beyond default", hasGenerator
                        ? SkyblockSetupStatus.State.SATISFIED : SkyblockSetupStatus.State.INCOMPLETE,
                hasGenerator ? "Configured." : "Add a generator tier beyond the seeded default.", "generators"));

        boolean hasShop = runtime.shopCatalog() != null && !safe(runtime.shopCatalog().entries()).isEmpty();
        checks.add(check("shop", "At least one shop entry", hasShop
                        ? SkyblockSetupStatus.State.SATISFIED : SkyblockSetupStatus.State.INCOMPLETE,
                hasShop ? "Configured." : "Add a shop entry.", "shop"));

        boolean hasChallenges = runtime.challengeRegistry() != null
                && !safe(runtime.challengeRegistry().challenges()).isEmpty();
        boolean challengesValid = runtime.challengeRegistry() != null
                && !runtime.challengeRegistry().hasInvalidDefinitions();
        SkyblockSetupStatus.State challengeState = !challengesValid
                ? SkyblockSetupStatus.State.BLOCKED
                : !hasChallenges ? SkyblockSetupStatus.State.INCOMPLETE : SkyblockSetupStatus.State.SATISFIED;
        String challengeReason = !challengesValid
                ? "A challenge is disabled by an invalid reference or cycle."
                : !hasChallenges ? "Create at least one challenge." : "Configured.";
        checks.add(check("challenges", "Challenges are configured and valid", challengeState,
                challengeReason, "challenges"));

        checks.add(defaultSelectionCheck());
        checks.add(playableTypesCheck(templateIds));
        checks.add(reachableChallengesCheck());
        checks.add(challengeIntegrityCheck());
        checks.add(shopQualityCheck());
        checks.add(generatorQualityCheck());
        return new SkyblockSetupStatus(checks);
    }

    private SkyblockSetupStatus.Check defaultSelectionCheck() {
        SkyblockConfig config = runtime.config();
        if (config == null || runtime.typeRegistry() == null) {
            return check("default-type", "Configured default type and difficulty",
                    SkyblockSetupStatus.State.BLOCKED, "Skyblock defaults are unavailable.", "types");
        }
        String typeId = config.defaultType();
        String difficultyId = config.defaultDifficulty();
        IslandType type = runtime.typeRegistry().type(typeId).orElse(null);
        boolean valid = type != null && runtime.typeRegistry().difficulty(difficultyId).isPresent()
                && type.allowsDifficulty(difficultyId);
        return check("default-type", "Configured default type and difficulty",
                valid ? SkyblockSetupStatus.State.SATISFIED : SkyblockSetupStatus.State.BLOCKED,
                valid ? typeId + " / " + difficultyId
                        : "Configured default is invalid: " + typeId + " / " + difficultyId,
                "types");
    }

    private SkyblockSetupStatus.Check playableTypesCheck(List<String> templateIds) {
        if (runtime.typeRegistry() == null) {
            return check("playable-types", "At least one playable island type", SkyblockSetupStatus.State.BLOCKED,
                    "Island type registry is unavailable.", "types");
        }
        List<String> unplayable = new ArrayList<>();
        for (IslandType type : safe(runtime.typeRegistry().types())) {
            if (!SkyblockValidation.validateIslandType(type, runtime.typeRegistry().difficulties(),
                    new java.util.LinkedHashSet<>(templateIds)).isEmpty()) unplayable.add(type.id());
        }
        List<IslandType> types = safe(runtime.typeRegistry().types());
        boolean playable = !types.isEmpty() && unplayable.isEmpty();
        return check("playable-types", "At least one playable island type",
                playable ? SkyblockSetupStatus.State.SATISFIED : SkyblockSetupStatus.State.BLOCKED,
                playable ? "Every configured type has a template or kit and a difficulty."
                        : "Unplayable type(s) produce empty or invalid islands: "
                        + (unplayable.isEmpty() ? "(none)" : String.join(", ", unplayable)), "types");
    }

    private SkyblockSetupStatus.Check reachableChallengesCheck() {
        if (runtime.typeRegistry() == null || runtime.challengeRegistry() == null) {
            return check("reachable-challenges", "A fresh default island can claim a challenge",
                    SkyblockSetupStatus.State.BLOCKED, "Challenge or type registry is unavailable.", "challenges");
        }
        String typeId = runtime.config() == null ? SkyblockConfig.DEFAULT_TYPE : runtime.config().defaultType();
        IslandType type = runtime.typeRegistry().type(typeId).orElse(null);
        IslandDifficulty difficulty = type == null || runtime.config() == null ? null
                : runtime.typeRegistry().difficulty(runtime.config().defaultDifficulty()).orElse(null);
        boolean claimable = type != null && safe(runtime.challengeRegistry().challenges()).stream()
                .filter(challenge -> SkyblockValidation.validateChallenge(challenge,
                        runtime.challengeRegistry().challenges(), runtime.typeRegistry().types(),
                        runtime.generatorRegistry() == null ? List.of() : runtime.generatorRegistry().tiers()).isEmpty())
                .anyMatch(challenge -> freshClaimable(challenge, type,
                        difficulty == null ? 1.0d : difficulty.multiplier()));
        return check("reachable-challenges", "A fresh default island can claim a challenge",
                claimable ? SkyblockSetupStatus.State.SATISFIED : SkyblockSetupStatus.State.INCOMPLETE,
                claimable ? "A challenge is claimable from the configured kit."
                        : "No challenge is claimable before island progression begins.", "challenges");
    }

    private SkyblockSetupStatus.Check challengeIntegrityCheck() {
        if (runtime.challengeRegistry() == null) {
            return check("challenge-integrity", "Challenge references and cycles are valid",
                    SkyblockSetupStatus.State.BLOCKED, "Challenge registry is unavailable.", "challenges");
        }
        List<String> invalid = new ArrayList<>();
        for (Challenge challenge : safe(runtime.challengeRegistry().challenges())) {
            if (!SkyblockValidation.validateChallenge(challenge, runtime.challengeRegistry().challenges(),
                    runtime.typeRegistry() == null ? List.of() : runtime.typeRegistry().types(),
                    runtime.generatorRegistry() == null ? List.of() : runtime.generatorRegistry().tiers()).isEmpty()) {
                invalid.add(challenge.id());
            }
        }
        return check("challenge-integrity", "Challenge references and cycles are valid",
                invalid.isEmpty() ? SkyblockSetupStatus.State.SATISFIED : SkyblockSetupStatus.State.BLOCKED,
                invalid.isEmpty() ? "All challenge definitions validate."
                        : "Invalid challenge(s): " + first(invalid, 3), "challenges");
    }

    private SkyblockSetupStatus.Check shopQualityCheck() {
        if (runtime.shopCatalog() == null) {
            return check("shop-prices", "Shop entries have an enabled direction",
                    SkyblockSetupStatus.State.BLOCKED, "Shop catalog is unavailable.", "shop");
        }
        List<ShopCatalog.Entry> entries = safe(runtime.shopCatalog().entries());
        List<String> invalid = entries.stream().filter(entry -> !SkyblockValidation.validateShopEntry(entry).isEmpty())
                .map(entry -> entry.material().name()).toList();
        return check("shop-prices", "Shop entries have an enabled direction",
                entries.isEmpty() ? SkyblockSetupStatus.State.INCOMPLETE
                        : invalid.isEmpty() ? SkyblockSetupStatus.State.SATISFIED : SkyblockSetupStatus.State.BLOCKED,
                entries.isEmpty() ? "Add at least one usable shop entry."
                        : invalid.isEmpty() ? "All shop entries can be bought or sold."
                        : "Disabled in both directions: " + first(invalid, 3), "shop");
    }

    private SkyblockSetupStatus.Check generatorQualityCheck() {
        if (runtime.generatorRegistry() == null) {
            return check("generator-weights", "Generator weights have a positive finite total",
                    SkyblockSetupStatus.State.BLOCKED, "Generator registry is unavailable.", "generators");
        }
        List<GeneratorTier> tiers = safe(runtime.generatorRegistry().tiers());
        List<String> invalid = tiers.stream().filter(tier -> !SkyblockValidation.validateGeneratorTier(tier).isEmpty())
                .map(GeneratorTier::id).toList();
        return check("generator-weights", "Generator weights have a positive finite total",
                tiers.isEmpty() ? SkyblockSetupStatus.State.INCOMPLETE
                        : invalid.isEmpty() ? SkyblockSetupStatus.State.SATISFIED : SkyblockSetupStatus.State.BLOCKED,
                tiers.isEmpty() ? "Add a generator tier."
                        : invalid.isEmpty() ? "All generator tiers have usable weights."
                        : "Invalid generator tier(s): " + first(invalid, 3), "generators");
    }

    private List<String> templateIds() {
        return runtime.templateStore() == null ? List.of() : safe(runtime.templateStore().ids());
    }

    private SkyblockSetupStatus.Check advisory(String id, String label, String key) {
        return check(id, label, SkyblockSetupStatus.State.ADVISORY,
                "Informational only; change " + key + " with /tconfig.", null);
    }

    private static boolean validType(IslandType type, SkyblockBootstrap.Runtime runtime) {
        boolean template = type.templateId().isBlank()
                || runtime.templateStore() != null && runtime.templateStore().ids().contains(type.templateId());
        boolean difficulty = !type.difficultyIds().isEmpty()
                && runtime.typeRegistry() != null
                && type.difficultyIds().stream().anyMatch(id -> runtime.typeRegistry().difficulty(id).isPresent());
        return template && difficulty;
    }

    private static boolean freshClaimable(Challenge challenge, IslandType type, double multiplier) {
        if (!challenge.prerequisites().isEmpty() || !challenge.anyOfGroups().isEmpty()) return false;
        if (!challenge.typeIds().isEmpty() && !challenge.typeIds().contains(type.id())) return false;
        if (!type.allowedChallengeIds().isEmpty() && !type.allowedChallengeIds().contains(challenge.id())) return false;
        Map<String, Integer> kit = new HashMap<>();
        type.kit().forEach(item -> kit.merge(item.material().toUpperCase(Locale.ROOT), item.amount(), Integer::sum));
        for (ChallengeRequirement requirement : challenge.requirements()) {
            if (!(requirement instanceof ChallengeRequirement.Possession possession)
                    || kit.getOrDefault(possession.material().name(), 0)
                    < scaled(possession.amount(), multiplier)) return false;
        }
        return true;
    }

    private static long scaled(long amount, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0) multiplier = 1.0d;
        double required = amount * multiplier;
        if (required >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(1L, (long) Math.ceil(required));
    }

    private static String first(Collection<String> values, int count) {
        if (values == null || values.isEmpty()) return "(none)";
        return values.stream().limit(count).reduce((left, right) -> left + ", " + right).orElse("(none)");
    }

    private static <T> List<T> safe(Collection<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static SkyblockSetupStatus.Check check(String id, String label, SkyblockSetupStatus.State state,
                                                    String reason, String target) {
        return new SkyblockSetupStatus.Check(id, label, state, reason, target);
    }
}
