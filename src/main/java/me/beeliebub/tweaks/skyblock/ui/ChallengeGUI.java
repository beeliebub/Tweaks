package me.beeliebub.tweaks.skyblock.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.beeliebub.tweaks.core.Messages;
import me.beeliebub.tweaks.skyblock.challenge.Challenge;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeCategory;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRegistry;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeService;
import me.beeliebub.tweaks.skyblock.island.Island;
import me.beeliebub.tweaks.skyblock.SkyblockDescriptions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Category, paginated-list, and detail dialogs for island challenges. */
@SuppressWarnings("UnstableApiUsage")
public final class ChallengeGUI {
    private static final int PAGE_SIZE = 12;
    private final ChallengeRegistry registry;
    private final ChallengeService service;

    public ChallengeGUI(ChallengeRegistry registry, ChallengeService service) {
        this.registry = registry;
        this.service = service;
    }

    public void open(Player player, Island island) {
        List<ActionButton> buttons = new ArrayList<>();
        for (ChallengeCategory category : registry.categories()) {
            buttons.add(button(category.displayName(), "View challenges in this category",
                    target -> { if (service.canUse(island, target)) openCategory(target, island, category.id(), 0); }));
        }
        if (buttons.isEmpty()) buttons.add(button("No challenges", "The challenge registry is empty", ignored -> { }));
        show(player, "Skyblock Challenges", List.of("Choose a category."), buttons, null);
    }

    private void openCategory(Player player, Island island, String categoryId, int page) {
        List<Challenge> challenges = registry.challengesIn(categoryId);
        int pages = Math.max(1, (challenges.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        int start = current * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, challenges.size());
        List<ActionButton> buttons = new ArrayList<>();
        for (int index = start; index < end; index++) {
            Challenge challenge = challenges.get(index);
            buttons.add(button(challenge.displayName(), challenge.description(),
                    target -> { if (service.canUse(island, target)) openDetail(target, island, challenge); }));
        }
        if (current > 0) buttons.add(button("Previous", "Previous page", target -> openCategory(target, island, categoryId, current - 1)));
        if (current + 1 < pages) buttons.add(button("Next", "Next page", target -> openCategory(target, island, categoryId, current + 1)));
        show(player, "Challenges: " + categoryId + " (" + (current + 1) + "/" + pages + ")",
                List.of("Select a challenge to view progress and claim it when ready."), buttons,
                target -> open(target, island));
    }

    private void openDetail(Player player, Island island, Challenge challenge) {
        ChallengeService.Readiness readiness = service.readiness(island, challenge.id(), player);
        List<String> body = new ArrayList<>();
        body.add(challenge.description());
        body.add(readiness.ready() ? "Ready to claim." : "Locked: " + readiness.reason());
        challenge.requirements().forEach(requirement -> body.add("Requirement: "
                + SkyblockDescriptions.requirement(requirement)));
        challenge.rewards().forEach(reward -> body.add("Reward: " + SkyblockDescriptions.reward(reward)));
        readiness.missing().forEach(missing -> body.add("Missing " + missing.key() + ": " + missing.amount()));
        List<ActionButton> buttons = new ArrayList<>();
        if (readiness.ready()) {
            buttons.add(button("Claim", "Claim this challenge",
                    target -> {
                        if (!service.canUse(island, target)) return;
                        ChallengeService.ClaimResult result = service.claim(island, challenge.id(), target);
                        target.sendMessage(result.claimed() ? Messages.SKYBLOCK.challengeClaimed(challenge.id())
                                : Messages.SKYBLOCK.challengeLocked(result.reason()));
                        openDetail(target, result.island() == null ? island : result.island(), challenge);
                    }));
        }
        show(player, challenge.displayName(), body, buttons, target -> openCategory(target, island, challenge.categoryId(), 0));
    }

    private static ActionButton button(String label, String tooltip, java.util.function.Consumer<Player> action) {
        return ActionButton.builder(Messages.SKYBLOCK.text(label, NamedTextColor.AQUA))
                .tooltip(Messages.SKYBLOCK.text(tooltip == null || tooltip.isBlank()
                                ? "Skyblock challenge" : tooltip, NamedTextColor.GRAY))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player player) action.accept(player);
                }, ClickCallback.Options.builder().build())).build();
    }

    private static void show(Player player, String title, List<String> lines, List<ActionButton> buttons,
                             java.util.function.Consumer<Player> back) {
        ActionButton backButton = back == null ? null : button("Back", "Return", back);
        DialogBase base = DialogBase.builder(Messages.SKYBLOCK.text(title, NamedTextColor.GREEN, TextDecoration.BOLD))
                .body(lines.stream().map(line -> DialogBody.plainMessage(
                        Messages.SKYBLOCK.text(line, NamedTextColor.WHITE))).toList())
                .canCloseWithEscape(true).pause(false).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.multiAction(buttons, backButton, 2))));
    }
}
