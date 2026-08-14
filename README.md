# Tweaks

A Paper plugin that adds custom enchantments, an enchantment quality system, separated world profiles, teleportation utilities, nicknames, flight, world events, cosmetics, and minigames to a multi-world Minecraft server.

**Requires Paper 26.2 and Java 25.**

---

## Table of Contents

- [World Profiles](#world-profiles)
- [Teleportation](#teleportation)
  - [Homes](#homes)
  - [Warps](#warps)
  - [TPA](#tpa)
  - [Back](#back)
  - [Spawn](#spawn)
- [Custom Enchantments](#custom-enchantments)
  - [Telekinesis](#telekinesis)
  - [Smelter](#smelter)
  - [Gem Connoisseur](#gem-connoisseur)
  - [Tunneller](#tunneller)
  - [Lumberjack](#lumberjack)
  - [Replant](#replant)
  - [Efficacy](#efficacy)
  - [Spawner Pickup](#spawner-pickup)
  - [Egg Collector](#egg-collector)
  - [Enchantment Interactions](#enchantment-interactions)
- [Enchantment Quality](#enchantment-quality)
  - [Tiers](#tiers)
  - [Rolling at the Enchanting Table](#rolling-at-the-enchanting-table)
  - [Fortune & Looting Re-Rolls](#fortune--looting-re-rolls)
  - [Luck of the Sea Treasure](#luck-of-the-sea-treasure)
  - [Tunneller & Efficacy Area Scaling](#tunneller--efficacy-area-scaling)
  - [Supported Enchantments](#supported-enchantments)
  - [Silk Touch Quality](#silk-touch-quality)
- [Player Features](#player-features)
  - [Nicknames](#nicknames)
  - [Flight](#flight)
  - [Night Vision](#night-vision)
  - [Item Filter](#item-filter)
  - [Condense](#condense)
  - [Tool Protect](#tool-protect)
  - [AFK](#afk)
  - [Tab List](#tab-list)
  - [Help Menu](#help-menu)
  - [XP Storage Bottles](#xp-storage-bottles)
  - [Disenchanting Bundle](#disenchanting-bundle)
  - [Economy](#economy)
    - [Lottery](#lottery)
  - [Ranks](#ranks)
- [Cosmetics](#cosmetics)
  - [Boot Trails](#boot-trails)
- [World Protections](#world-protections)
  - [Spawn Egg Restrictions](#spawn-egg-restrictions)
- [Admin Tools](#admin-tools)
  - [Block Log](#block-log)
  - [Death Inventory](#death-inventory)
  - [Display Chests](#display-chests)
  - [Item Editing](#item-editing)
  - [Chest GUI Copy](#chest-gui-copy)
  - [Gamemode Shortcuts](#gamemode-shortcuts)
  - [Console Event Logging](#console-event-logging)
- [World Events](#world-events)
  - [Blood Moon](#blood-moon)
- [Minigames](#minigames)
  - [Resource Rupee](#resource-rupee)
  - [Whack an Andrew](#whack-an-andrew)
  - [Blackjack](#blackjack)
  - [Roulette](#roulette)
    - [Discord Betting](#discord-betting)
  - [Resource Hunt](#resource-hunt)
  - [Rewards](#rewards)
- [Land Protection](#land-protection)
  - [Claims & Selection](#claims--selection)
  - [Members & Sub-regions](#members--sub-regions)
  - [Advanced Flags & Targeting](#advanced-flags--targeting)
  - [Material-Specific Flags](#material-specific-flags)
  - [Resolution Priority](#resolution-priority)
  - [Region GUI](#region-gui)
- [Commands Reference](#commands-reference)
- [Permissions Reference](#permissions-reference)
- [Configuration](#configuration)
  - [Discord announcements and stat channels](#discord-announcements-and-stat-channels)
- [Server Administration](#server-administration)
  - [Installation](#installation)
  - [Data Pack Requirement](#data-pack-requirement)
  - [Data Storage](#data-storage)
  - [Building from Source](#building-from-source)

---

## World Profiles

The server is divided into **profiles**. Each profile has its own separate inventory, ender chest, and experience (levels + progress bar). When you travel between worlds that belong to different profiles, your items, ender chest contents, and XP are automatically saved and swapped. You'll see a yellow message confirming the switch:

> *Inventory profile switched to: standard*

By default, the four profiles are:

| Profile | Worlds |
|---|---|
| **Lobby** | The lobby world |
| **Standard** | The overworld, nether, and end (survival worlds) |
| **Archive** | The archive world |
| **Pi** | The pi world |

Admins can add, remove, or modify world-key mappings at runtime — including registering entirely new profiles — via `/tconfig world-profiles <list\|add\|remove\|edit>` or the `/tconfig gui`'s "World Profiles" screen; see [Commands Reference](#commands-reference). A world key's *profile* can only be set when it's first added (renaming an existing entry's profile would orphan that world's already-stored inventory/ender-chest/XP data, so it's config-file-only after creation) — only its tab-list tag and color remain editable afterward.

**What this means for you as a player:**

- Items you collect in survival stay in survival. Walking into the lobby won't wipe your inventory — it will be waiting for you when you return.
- Your ender chest contents are separate per profile. Storing diamonds in your survival ender chest won't make them appear in the lobby ender chest.
- Your experience level and progress are separate per profile. Earning 30 levels in survival won't give you 30 levels in the lobby.
- If you die, your death inventory is correctly handled — the plugin won't accidentally save an empty inventory over your real one.

---

## Teleportation

### Homes

You can save personal locations and teleport back to them at any time.

| Command | What it does |
|---|---|
| `/sethome` | Saves your current location as a home named "default". |
| `/sethome <name>` | Saves your current location with a custom name. |
| `/home` | Teleports you to your "default" home. |
| `/home <name>` | Teleports you to a named home. |
| `/delhome` | Deletes your "default" home. |
| `/delhome <name>` | Deletes a named home. |
| `/homes` | Lists all your saved home names. |

There is a configurable limit on how many homes you can set (default: 15). If you've hit the limit, you can still overwrite an existing home by using the same name. All commands support tab completion for your home names.

### Warps

Warps are shared server-wide locations that any player can teleport to. Only admins can create or delete them.

| Command | What it does |
|---|---|
| `/warp <name>` | Teleports you to a warp. |
| `/warps` | Lists all available warps. |

### TPA

Request to teleport to another player, or request them to come to you.

| Command | What it does |
|---|---|
| `/tpa <player>` | Sends a request to teleport **to** another player. |
| `/tpahere <player>` | Sends a request for another player to teleport **to you**. |
| `/tpaccept` | Accepts an incoming TPA request. |
| `/tpdeny` | Denies an incoming TPA request. |

When you receive a TPA request, you'll see clickable **[Accept]** and **[Deny]** buttons in chat. Requests expire after **30 seconds** if not answered. Only one incoming request can be pending at a time per player — a new request replaces the old one.

**Protection**: Just like `/resource`, if a TPA teleport would take a player from an outside world into a resource world (`jass:resource` or `jass:resource_nether`), the player's inventory is scanned for disallowed items. If any are found, the teleport is blocked.

### Back

| Command | What it does |
|---|---|
| `/back` | Teleports you to your location before your last teleport, or to where you died. |

This works after any teleport (TPA, home, warp, spawn, etc.) and after death — when you die, the death location is captured automatically so you can `/back` to recover your items. If you teleport somewhere after respawning, your back location updates to that pre-teleport spot, as usual. Your back location is saved across logins and server restarts. Traveling **into** a resource world via `/back` is also subject to inventory item restrictions.

---

### Spawn

| Command | What it does |
|---|---|
| `/spawn` | Teleports you to the server spawn. |

The spawn location is set by creating a warp named "spawn" using `/setwarp spawn`. If no spawn warp has been set, the command will tell you.

---

## Custom Enchantments

All custom enchantments are provided by a **server-side data pack** and resolved by the plugin at startup. They appear on tools as normal enchantments and can be obtained however the data pack defines (enchanting table, villager trades, loot tables, etc.).

Tools with the Spawner Pickup or Egg Collector enchantments **cannot be used in anvils** — this is intentional to prevent repair/combination exploits.

### Telekinesis

Sends block drops straight to your inventory instead of dropping them on the ground. If your inventory is full, overflow items drop at the broken block's location as usual.

Also **chain-breaks stackable plants**: breaking one piece of sugar cane, cactus, bamboo, kelp, vines, pointed dripstone, or chorus plant will break the entire connected stack and send all drops to your inventory.

### Smelter

Automatically smelts raw ore drops when you mine blocks:

| Raw Drop | Smelted Into |
|---|---|
| Raw Iron | Iron Ingot |
| Raw Copper | Copper Ingot |
| Raw Gold | Gold Ingot |

Works on the ores you'd normally expect — iron ore, copper ore, gold ore, and their deepslate variants. Combines naturally with Telekinesis to send smelted ingots directly to your inventory.

### Gem Connoisseur

Gives a bonus chance to drop gems and materials when mining **stone**, **deepslate**, or **netherrack**. The enchantment has 3 levels, with higher levels providing better drop rates.

At level 3, mining stone can drop:

| Material | Chance (1 in N) |
|---|---|
| Coal | 1 in 100 |
| Copper Ingot | 1 in 200 |
| Iron Ingot | 1 in 300 |
| Gold Ingot | 1 in 400 |
| Redstone | 1 in 500 |
| Lapis Lazuli | 1 in 600 |
| Diamond | 1 in 700 |
| Emerald | 1 in 800 |

Deepslate has slightly better rates than stone. Netherrack drops quartz, gold, and at level 3, ancient debris. The Fortune enchantment on the same tool can increase the quantity of bonus drops.

Items obtained through Gem Connoisseur count toward the **[Resource Hunt](#resource-hunt)** event when they match the active target.

### Tunneller

Breaks a **3x3 area** of blocks perpendicular to the face you mine. Mine the side of a wall and it carves out a 3x3 tunnel. Mine downward and it clears a 3x3 floor. The center block is the one you break normally; the surrounding blocks are broken by the enchantment.

- Blocks that are air, liquid, or unbreakable (like bedrock) are skipped.
- Your tool takes **durability damage for each extra block** broken by the enchantment. The Unbreaking enchantment reduces this damage normally.
- Works with Smelter, Gem Connoisseur, and Telekinesis — all surrounding blocks benefit from whatever combination of enchantments is on your tool.
- **Containers are safe**: if the tunnelled area collaterally breaks a chest, barrel, hopper, furnace, brewing stand, or other container, its contents are preserved. With Telekinesis they go straight into your inventory (overflow at the chest's location); without Telekinesis they drop on the ground in place. Shulker boxes are unaffected by this — their contents are already preserved inside the dropped shulker item itself.
- The area scales up to **11x11** with quality variants — see [Tunneller & Efficacy Area Scaling](#tunneller--efficacy-area-scaling).

### Lumberjack

Chops down **entire trees** and **large mushrooms** at once. When you break a log or mushroom block, the enchantment finds all connected blocks (up to a configurable cap, default **256**, admin-configurable via `/tconfig enchantments`) and breaks them all in one swing.

- **Trees**: only activates on actual trees — there must be at least one leaf block adjacent to the connected logs. This prevents it from tearing apart log-built structures. Supports Nether trees (Wart blocks/Shroomlight as leaves).
- **Large mushrooms**: works on red and brown giant mushrooms. The connected set must include both stem and cap blocks, so isolated placed mushroom blocks are left alone.
- Your tool takes **durability damage for each extra block** broken. Unbreaking reduces this normally.
- If your tool doesn't have enough durability remaining to chop the entire tree or mushroom, the break is cancelled and you'll see a red warning message.
- Works with Telekinesis to send all drops to your inventory.

### Replant

Automatically replants crops and saplings after harvesting.

**Crops**: When you break a fully-grown crop (wheat, carrots, potatoes, beetroots, nether wart), the enchantment consumes one seed from the drops and replants the crop at age 0. If the crop is **not fully grown**, the break is cancelled entirely — this prevents accidental harvesting of immature crops.

Replant runs through the standard `BlockDropItemEvent` flow so other plugins (e.g. Husbandry's crop traits) can mutate the dropped items first — the seed used for replanting carries any traits applied by those plugins. Plugins can register a `Replant.ReplantHook` (via `Tweaks#getReplant()`) to be notified of replants and persist their own data onto the new block.

**Trees**: When combined with Lumberjack, saplings are automatically planted at the base of felled trees wherever a log was sitting on valid soil (grass, dirt, podzol, moss, mud, etc.).

### Efficacy

Extends shovel, hoe, and axe right-click actions to a **3x3 area**:

| Tool | Action | 3x3 Effect |
|---|---|---|
| Shovel | Right-click grass/dirt | Creates dirt paths in a 3x3 area |
| Hoe | Right-click grass/dirt | Tills farmland in a 3x3 area |
| Axe | Right-click logs/wood | Strips logs/wood in a 3x3 area |

Each surrounding block affected costs **1 durability**. Only affects blocks of the appropriate type — the shovel won't path stone, the hoe won't till stone, etc. Blocks must also have air above them (for shovels and hoes) to be affected.

The area scales up to **11x11** with quality variants — see [Tunneller & Efficacy Area Scaling](#tunneller--efficacy-area-scaling).

### Spawner Pickup

Gives a configurable chance (default: **20%**, admin-configurable via `/tconfig enchantments`) to drop the spawner block when you mine a mob spawner. However, this comes with a cost: the tool tracks how many spawners it has successfully dropped. After a configurable number of successful pickups (default: **5**, also admin-configurable), the tool breaks completely (regardless of remaining durability). The number of uses remaining is shown in the item's lore.

### Egg Collector

Gives a configurable chance (default: **0.5%**) to drop a **spawn egg** when you kill a mob. Like Spawner Pickup, the tool tracks successful egg drops and breaks after a configurable number of uses (default: **5**, admin-configurable via `/tconfig enchantments`). Remaining uses are shown in the item's lore.

If your tool also carries a **quality Looting** enchantment, the egg drop chance benefits from the same **re-roll capabilities** as standard mob loot. A Legendary Looting tool, for example, will re-roll the egg drop chance 5 additional times if the initial roll fails.

Admins can blacklist specific mobs from ever rolling an egg via `/tconfig eggdrop disable <mob>`. Disabled mobs never drop an egg and never consume one of the tool's uses. Use `/tconfig eggdrop enable <mob>` to lift the block. See [Spawn Egg Restrictions](#spawn-egg-restrictions) for the matching spawner-side block.

### Enchantment Interactions

Many enchantments stack together. Here are the notable combinations:

| Combination | Effect |
|---|---|
| Tunneller + Telekinesis | All area drops go straight to your inventory. |
| Tunneller + Smelter | Raw ores from every block in the area are auto-smelted. |
| Tunneller + Gem Connoisseur | Bonus gem drops roll for every block in the area. |
| Tunneller + Smelter + Gem Connoisseur + Telekinesis | The full package: area mining with smelting, gem drops, and inventory routing. |
| Lumberjack + Telekinesis | All log drops from the tree go to your inventory. |
| Lumberjack + Replant | Saplings auto-plant at tree bases after felling. |
| Replant + Telekinesis | Crop drops (minus the replanting seed) go to your inventory. |
| Smelter + Telekinesis | Smelted ingots go directly to your inventory. |
| Gem Connoisseur + Telekinesis | Bonus gem drops go to your inventory. |

---

## Enchantment Quality

In addition to the standard (common) form, many enchantments can roll as **quality variants** with stronger effects. Quality variants are provided by the data pack and applied automatically by the plugin at the enchanting table.

### Tiers

There are four quality tiers above common. Their effects depend on which enchantment they're applied to (see the sections below).

| Tier | Roll Weight | Re-Rolls | Tunneller / Efficacy Area |
|---|---|---|---|
| **Common** *(vanilla)* | 90% (no quality roll) | 0 | 3x3 |
| **Uncommon** | 70% of the 10% quality slice | 1 | 5x5 |
| **Rare** | 20% of the 10% quality slice | 2 | 7x7 |
| **Epic** | 9% of the 10% quality slice | 3 | 9x9 |
| **Legendary** | 1% of the 10% quality slice | 5 | 11x11 |

### Rolling at the Enchanting Table

Whenever you enchant an item at an enchanting table, each rolled enchantment that has a quality variant has a configurable chance (default **10%**, admin-configurable via `/tconfig enchantments`) to be upgraded to a quality tier. If the upgrade triggers, the tier is rolled with the weights above (so legendary is genuinely rare — 1% of the default 10% quality roll, or roughly 1 in 1,000 per applicable enchantment at default settings). The visual name and color of the resulting enchantment is determined by the data pack.

This chance is boosted (default **50%**, also admin-configurable) during a [Blood Moon](#blood-moon) event.

### Fortune & Looting Re-Rolls

A quality Fortune or Looting enchantment grants additional **re-rolls** of the drop count for ores or mob loot. Each tier provides more re-rolls, and the highest result wins. Legendary Fortune, for example, rolls the ore drop formula 6 times (1 base + 5 re-rolls) and keeps the best outcome.

> Re-rolls only apply when the underlying drop is allowed to occur — for example, when Smelter cancels the vanilla drop in favor of an ingot, Fortune re-rolls do not apply to that block.

### Luck of the Sea Treasure

A quality Luck of the Sea rod increases the chance that a fishing catch is replaced with a vanilla treasure roll. The chance is split into 12 evenly-spaced steps (4 tiers × 3 enchant levels), scaling from ~8.3% at Uncommon I up to **100% guaranteed treasure** at Legendary III.

### Tunneller & Efficacy Area Scaling

Quality variants of Tunneller and Efficacy enlarge the affected area:

| Tier | Area |
|---|---|
| Common | 3x3 |
| Uncommon | 5x5 |
| Rare | 7x7 |
| Epic | 9x9 |
| Legendary | 11x11 |

Durability cost still scales with each extra block broken, and Unbreaking continues to mitigate that cost normally.

**Mode cycling**: **Shift + Right-Click** a Tunneller or Efficacy tool to cycle its area down one size. A Legendary tool cycles `11x11 → 9x9 → 7x7 → 5x5 → 3x3 → 11x11`. The current mode is shown as a `Mode: NxN` lore line and is also flashed in the action bar on each cycle. Mode is stored per-item in PDC, so different tools can keep different modes. While sneaking, the tool's usual right-click action (Efficacy path/till/strip) is suppressed so the same input doesn't both cycle the mode and trigger the area effect.

### Supported Enchantments

20 enchantment types have quality variants that the data pack registers and the plugin recognizes:

```
fortune, looting, luck_of_the_sea, frost_walker, knockback, lunge,
lure, multishot, piercing, power, punch, quick_charge, sharpness,
smite, bane_of_arthropods, sweeping_edge, unbreaking, efficacy, tunneller,
silk_touch
```

The plugin implements custom logic for **fortune**, **looting**, **luck_of_the_sea**, **efficacy**, **tunneller**, and **silk_touch**. The remaining quality variants have their behavior defined entirely by the data pack (typically increased base levels or stronger stat effects).

#### Silk Touch Quality

Quality variants of Silk Touch allow players to pick up blocks that are normally impossible to obtain:

| Tier | New Pickups |
|---|---|
| **Uncommon** | Dirt Path |
| **Rare** | Farmland |
| **Epic** | Reinforced Deepslate |
| **Legendary** | Budding Amethyst |

- **Reinforced Deepslate**: Picking this up requires at least an **Epic** Silk Touch tool. This also works with **Tunneller** — an Epic+ Silk Touch Tunneller tool can carve out 3x3 areas of Reinforced Deepslate (which is normally unbreakable).
- Tiers are cumulative: a Legendary tool can pick up everything from the lower tiers.

Quality Silk Touch is **deterministic**: using a quality silk shovel on gravel will reliably drop gravel, suppressing the random flint roll.

---

## Player Features

### Nicknames

Set a custom display name with color support using `&` color codes and hex codes.

| Command | What it does |
|---|---|
| `/nick <nickname>` | Sets your display name. Supports `&` color codes and `&#RRGGBB` hex colors. |
| `/nick off` | Removes your nickname and restores your real name. |

Examples:
- `/nick &cRedName` — sets your name to red.
- `/nick &aGreen &bAqua` — multi-colored name.
- `/nick &#FF5555Custom` — hex color.

Your nickname persists across logins and server restarts.

### Flight

Toggle creative-style flight. Flight access is granted in two ways:

1. **Fly-enabled worlds** — Flight is automatically available in configured worlds (lobby and archive by default).
2. **Advancement** — Earning a specific server advancement grants flight everywhere.

| Command | What it does |
|---|---|
| `/fly` | Toggles flight on/off. |

When you enter a world where you don't have flight access, flight is automatically disabled with a red warning message. Your flight state is remembered across logins — if you had flight enabled and still qualify, it re-enables on join.

### Night Vision

| Command | What it does |
|---|---|
| `/nv` | Toggles permanent night vision on/off. |

Applies an infinite-duration night vision effect with no particles.

### Item Filter

Control which items your character actually picks up. Two modes:

- **Whitelist** — only items on your list are picked up.
- **Blacklist** — every item *except* those on your list are picked up.

The filter is **off by default**. Whether it's enabled, which mode is active, and the contents of each list are all saved on your player profile and persist across logins. The whitelist and blacklist are tracked independently — you can curate one for each mode and swap between them without losing either.

The filter is intelligent and **automatically bypasses crafting and trading**. Items you shift-click or take from a crafting table or villager GUI result slot are never blocked, even if they aren't on your whitelist.

| Command | What it does |
|---|---|
| `/itemfilter` | Show your current state (enabled/disabled, mode, item count). |
| `/itemfilter toggle` | Turn the filter on or off. |
| `/itemfilter mode` | Swap between whitelist and blacklist mode. |
| `/itemfilter add <item>...` | Add one or more items to your active list. |
| `/itemfilter remove <item>...` | Remove one or more items from your active list. |
| `/itemfilter list` | Show the contents of your active list. |
| `/itemfilter clear [mode]` | Clear a specific list (`whitelist`, `blacklist`, `both`), or active list if omitted. |

`/if` is a short alias for `/itemfilter`. Item names use the standard form (e.g. `cobblestone`, `oak_log`, `diamond`); tab completion suggests matches on `add`, your current list contents on `remove`, and lists for `clear`. No permission required — every player can manage their own filter.

### Condense

Compact 9x granular items in your inventory into their block form. Only materials with a vanilla **9:1 forward and 1:9 reverse** recipe are eligible — so the block can always be uncrafted back into the original items.

| Command | What it does |
|---|---|
| `/condense` | Condenses only the type of item you are currently holding. |
| `/condense all` | Condenses every eligible material across your inventory. |

**Eligible materials**: Iron Ingot, Gold Ingot, Diamond, Emerald, Netherite Ingot, Lapis Lazuli, Redstone, Coal, Copper Ingot, Raw Iron, Raw Gold, Raw Copper, Slime Ball, Wheat, Bone Meal, Nether Wart.

Items with a custom display name, lore, enchantments, custom model data, or other persistent data tags are skipped to avoid destroying special items. However, items carrying the **Resource Hunt** tag are eligible for condensation; the resulting block will inherit the tag. Mixed pools (e.g. 5 tagged ingots and 4 untagged ingots) will **not** be merged. Remainders that don't divide evenly into 9 are left in your inventory; produced blocks that don't fit drop at your feet.

### Tool Protect

A safety net that blocks the use of high-tier tools when they're about to break. **On by default for every player** — no opt-in required.

A tool is "protected" only when **all** of these are true:

- It is a **diamond or netherite** sword, pickaxe, axe, shovel, or hoe.
- It carries at least one **Epic** or **Legendary** quality enchantment (see [Enchantment Quality](#enchantment-quality)).
- Its **remaining durability** is below your configured threshold (default: **100**, admin-configurable via `/tconfig itemadmin`).

While a tool is protected, the plugin cancels any action that would damage it — breaking blocks, attacking entities, and right-click actions like tilling, pathing, or stripping. You'll see a red action-bar warning showing the remaining durability and your current threshold:

> *ToolProtect: 87 durability remaining (threshold 100). Repair or /toolprotect off.*

Tools that don't meet the scope above (wood/stone/iron/gold tiers, or top-tier tools without an Epic/Legendary quality enchant) are never blocked — protection only kicks in for the gear that's actually worth saving.

| Command | What it does |
|---|---|
| `/toolprotect` | Show your current state (on/off, threshold). |
| `/toolprotect on` | Turn protection on. |
| `/toolprotect off` | Turn protection off. |
| `/toolprotect durability <n>` | Set your remaining-durability threshold. Values below 1 are rejected — use `/toolprotect off` to disable. |

Both the on/off state and the threshold are saved on your player profile and persist across logins. No permission required.

### AFK

Mark yourself as away-from-keyboard. While you're AFK:

- A red **[AFK]** suffix is appended to your name in the tab list.
- You stop counting toward the sleep percentage, so other players can skip the night without waiting on you.

| Command | What it does |
|---|---|
| `/afk` | Toggles your AFK status. |

You leave AFK automatically the moment you move at least one block from where you toggled it on (including teleports and world changes). Looking around, opening menus, attacking, and clicking do **not** clear AFK — only actual movement does.

You also enter AFK automatically after **10 minutes** of not moving. Only positional movement (walking, jumping, teleporting) resets the idle timer; rotating the camera, attacking, opening menus, chatting, and clicking do **not** count as activity — matching the rule that clicks don't exit AFK either. AFK status is in-memory only and resets on logout. No permission required.

### Tab List

Players in the tab list are automatically sorted by their current world profile and labeled with a colored prefix based on the **specific world** they are in. The display name follows a unified format:

`[WorldPrefix][RankName] Name [$balance] [AFK]`

- **World Prefix**: A colored tag based on the player's current world.
- **Rank Name**: Your current rank in gold brackets (e.g., `&6[I]`, `&b[VIP]`). Rank names support color codes (legacy `&` and `&#rrggbb` hex) and render consistently in the tab list, `/ranks`, `/rankup`, and `/rank set` feedback.
- **Name**: Your nickname (if set) or real name, colored to match the world prefix.
- **$balance**: Your current balance in yellow. This is omitted if you have hidden your balance via `/bal hide`.
- **[AFK]**: A red suffix appended when you are away-from-keyboard.

#### World Tags & Sorting

| World | Tag | Profile (Sorting) |
|---|---|---|
| `jass:lobby` | **[Lobby]** (aqua) | Lobby (1st) |
| `minecraft:overworld` | **[Survival]** (green) | Standard (2nd) |
| `minecraft:the_nether` | **[Nether]** (light purple) | Standard (2nd) |
| `minecraft:the_end` | **[End]** (dark purple) | Standard (2nd) |
| `jass:resource` | **[Resource]** (aqua) | Standard (2nd) |
| `jass:resource_nether` | **[Resource]** (aqua) | Standard (2nd) |
| `jass:archive` | **[Archive]** (gold) | Archive (3rd) |
| `jass:pi` | **[Pi]** (light purple) | Pi (4th) |

Any other world falls back to the **[Survival]** tag. Tags and sorting update automatically when you change worlds or profiles.

### Help Menu

A comprehensive, interactive help system is available to guide you through the server's features. The entire system — including the category menu and individual articles — is rendered via **Paper Dialogs** (clickable GUIs) for a seamless, immersive experience.

- **GUI Access**: Type `/help` to open a categorized menu. Each category and article features a unique, theme-consistent color gradient for easy identification.
- **Articles**: Clicking a category opens its article list, and clicking an article opens its full content (title, body, and related-article jumps) within the GUI. Use the **[Back]** button to return to the category.
- **Dynamic Layout**: The main menu automatically adjusts its layout based on your permissions, ensuring high-priority admin categories (like Permissions) are easily accessible.
- **Direct Access**: Use `/help <section>` (e.g., `/help teleportation` or `/help tunneller`) to jump directly to a specific category or article.
- **Login Tips**: Each time you log in, you'll receive a random gameplay tip to help you discover new features.

---

### Permissions System

A hybrid GUI/CLI permission system with **multi-group membership**, single-parent group inheritance, and per-user overrides.

| Command | Permission | What it does |
|---|---|---|
| `/tprm` | `tweaks.admin.permissions` | Open the Permissions GUI. |
| `/tprm group <name> create` | `tweaks.admin.permissions` | Create a new permission group. |
| `/tprm group <name> delete` | `tweaks.admin.permissions` | Delete a group ('default' is protected). |
| `/tprm group <name> addperm <p>` | `tweaks.admin.permissions` | Grant a permission to a group. |
| `/tprm group <name> delperm <p>` | `tweaks.admin.permissions` | Revoke a permission from a group. |
| `/tprm group <name> inherited-from <parent\|none>` | `tweaks.admin.permissions` | Set group inheritance. |
| `/tprm user <player> addperm <p>` | `tweaks.admin.permissions` | Grant a per-user override. |
| `/tprm user <player> delperm <p>` | `tweaks.admin.permissions` | Revoke a per-user override. |
| `/tprm user <player> setgroup <g\|none>` | `tweaks.admin.permissions` | Replace a user's group memberships with the named group (or clear all with `none`). Use the GUI's **Edit Groups** menu to assemble multi-group memberships interactively. |

**Multi-group model**: Every player always receives the `default` group's permissions, including players without a saved user record; `default` membership cannot be removed. A player may additionally belong to any number of groups, and those permissions supplement the default baseline. Each group may declare a single parent for inheritance, and parent chains are walked recursively per group.

**Effective permissions** for a player are the union of:
1. The player's direct permission overrides.
2. The permissions of every group the player belongs to.
3. Every ancestor group reachable through `inherited-from` chains from those groups.

When two of the player's groups share an ancestor in the inheritance graph, that ancestor's permissions are added exactly once. Cycles in the inheritance graph are skipped safely.

**Permissions GUI**: The visual editor is a tree of Paper Dialogs (multi-action and confirmation) that organizes permissions into logical categories (Admin & Tools, Minigames, etc.) for easier management. Groups and players can be managed with simple click toggles. List screens paginate at 12 entries per page; toggle buttons prefix `✓` for "on" and `✗` for "off".
- **Permission Details**: Hover any permission toggle to see its complete node, a concise description of what it permits, and the pending grant/revoke action.
- **Main Menu**: Entry point with `Groups` and `Players` buttons.
- **Groups Hub**: Manage group permissions (organized by category), member list (toggle-based), and inheritance. The **+ Create Group** button opens a name-entry dialog; **Delete Group** is hidden for the protected `default` group.
- **Users Hub**: Manage player-specific overrides (organized by category) and additional group memberships. The **Edit Groups** panel lists non-default groups with a `✓` marker on the ones the player already belongs to — click any entry to toggle membership. The Players list contains online users only; use **⌕ Search Player** to look up an offline player who has previously joined.

---

### XP Storage Bottles
Store your experience levels for later use or trade by brewing **Experience Potions** in a brewing stand.

**Brewing Recipes** (the emerald value is admin-configurable via `/tconfig xpbottle`, default `1,395`; the emerald block value is always exactly 9x the emerald value; **changing this setting requires a server restart** since it's baked into the recipe at boot):

| Ingredient | Bottle Type | Result | Stored XP |
|---|---|---|---|
| Emerald | Glass Bottle | Experience Potion | 1,395 orbs (default) |
| Emerald Block | Glass Bottle | Experience Potion | 12,555 orbs (default) |

**How it works**:

- **Payment**: Brewing consumes your own experience. The player who places the emerald or emerald block into the brewing stand is the one charged for the brew.
- **Verification**: If you don't have enough experience to cover the cost, the brewing process is cancelled and the ingredients are returned to you (dropped on top of the stand).
- **Stacking**: Unlike regular potions, these custom Experience Potions can be stacked up to **64**.
- **Usage**: Simply drink the bottle to receive the stored experience. The vanilla drinking animation and sound apply, and you'll receive a glass bottle back.

This system ensures that experience can be safely stored and transferred without loss, using a clean, vanilla-friendly brewing mechanic.

### Dice Converter

If the **Dice** data pack is installed, splash potions carrying the `dqc.dice:dice_converter` enchantment will trigger a temporary **2-second pickup block** for the throwing player. This prevents you from accidentally picking up the "dice" item immediately after launching it.

---

### Disenchanting Bundle

Any **bundle with lore** (any custom lore will work) can be used to safely (or mostly safely) extract enchantments from items. **The bundle is consumed and destroyed upon use.**

**How it works**:

1.  **Trigger**: Right-click an enchanted item with a Disenchanting Bundle (or right-click the bundle with the enchanted item on your cursor).
2.  **Extraction**: The bundle will immediately strip **all** enchantments from the item.
3.  **Conversion**: For each enchantment removed, the bundle attempts to convert it into an individual **Enchanted Book**.
4.  **Priority**: Enchantments are processed in order of their [quality tier](#tiers) (Legendary > Epic > Rare > Uncommon > Vanilla).

**Success Probabilities**:

- **First Enchantment**: The highest-tier enchantment is **guaranteed** (100% chance) to be given back as a book. If there are multiple enchantments at the highest tier, one is picked at random to be the guaranteed extraction.
- **Subsequent Enchantments**: After each **successful** book extraction, the chance for the next enchantment to become a book decreases (100% -> 80% -> 60% -> 40% -> 20% -> 0%).
- **Failure**: If an extraction roll fails, the enchantment is still removed from the item, but no book is given (it is lost to the void). After a failure, the success chance for the next enchantment **remains at its current level**.
- **Restrictions**: The Disenchanting Bundle **cannot** be used on tools containing the **Spawner Pickup** or **Egg Collector** enchantments. The bundle will refuse the extraction to prevent players from bypassing the limited uses of these enchants.

This mechanic provides a strategic way to recover powerful enchantments from tools at the cost of the bundle itself and the risk of losing some enchantments on heavily enchanted items.

---

### Economy

The server features a simple, dollar-based economy system. You can earn money through daily login rewards and spend it on ranking up.

#### Daily Login Rewards

Every day you log in, you receive a **Daily Reward**. The amount you receive increases with your **Login Streak**, up to a maximum of 7 days.

- **Base Reward**: $100
- **Streak Multipliers**: Your base reward is multiplied by a factor that grows each day you log in consecutively (1.0x → 2.5x).
- **Rank Bonus**: Higher ranks provide an additional flat bonus to every daily reward.

When you join, you'll see a message like:
> *Daily reward: +$100 (Day 1 streak)*

#### Commands

| Command | What it does |
|---|---|
| `/balance` | View your current balance. Alias: `/bal`. |
| `/balance hide` | Toggles whether your balance is visible to others in the tab list. |
| `/house balance` | View the server-wide casino house account. Admin permission required. |
| `/house add\|remove\|set <amount>` | Adjust the casino house account. Admin permission required. |
| `/house pay <player> <amount>` | Transfer house funds to an online or known offline player. Admin permission required. |

Balances are whole-dollar values. `/balance set|add|remove` reject decimal inputs rather than
rounding them, and a mutation is rejected without changing the balance when its result would leave
the supported `±2^53` range. Legacy persisted fractions are floored on load; non-finite or
out-of-range legacy values are clamped and logged. A rejected casino stake is refused; a rejected
payout, rakeback, or refund is routed to the House account and logged for recovery.

#### Lottery

The server-wide lottery grows from House-account growth since the previous draw. A player enters
after a settled blackjack loss or inactivity forfeiture, or when their roulette round ends net
negative. Entries are one-per-player and offline entrants remain eligible. With `M` as the configured
pot multiplier, `X` as the baseline, `Y` as the House balance, and `Z` as the entrant count, the pot
is `floor(M x (Y - X) x (1 + 0.01 x Z))`, clamped so the House never falls below its live fallback
floor. At least two distinct entrants are required to draw. A one-entrant draw rolls positive House
growth into the fallback and advances the baseline without paying anyone; the ticket is retained.

The pot is paid from the House through a durable payment intent and house-payment journal; an
unpaid draw keeps its entries and baseline, while an in-flight payment resumes from its retained
House debit on the next startup. A successful payment completes the House journal before its
recipient receipt is pruned; the ready-gated join sweep removes only receipts with no matching
full-journal entry. Only a committed draw removes the recorded entrants, so entries added during
payment remain. The live fallback is stored with lottery state, resets to the configured base after
a successful draw, and can be viewed or changed with `/lottery fallback`.

| Command | What it does |
|---|---|
| `/lottery info` | Show the current entrant count, House balance, baseline, and computed pot. |
| `/lottery entries` | List up to 50 current entrants. Public; non-admin requests have a short cooldown. |
| `/lottery draw` | Draw and announce a winner. Admin permission required. |
| `/lottery baseline <amount>` | Set the growth baseline. Admin permission required. |
| `/lottery fallback [<amount>]` | View or set the live fallback floor. Admin permission required. |

When DiscordSRV is installed and `discord.channel-id` is configured, each committed draw also
posts a yellow winner card to that Discord channel. The card uses the winner's name and avatar;
the bot needs **Manage Webhooks** for the channel. The announcement channel is addressed by its
numeric ID and must not be added to DiscordSRV's `Channels:` map, which would make it a two-way
chat bridge.

### Ranks

Progress through the server's rank hierarchy to unlock higher daily reward bonuses and better casino rakeback rates.

| Rank | Cost | Daily Bonus | Rakeback |
|---|---|---|---|
| **I** | $1,000 | +1% | +1% |
| **II** | $2,500 | +2% | +2% |
| **III** | $5,000 | +3% | +3% |
| ... | ... | ... | ... |
| **X** | $350,000 | +10% | +10% |

Costs and bonuses are configurable by admins.

| Command | What it does |
|---|---|
| `/ranks` | Lists all available ranks, their costs, and their benefits. |
| `/rankup` | Purchase the next rank using your current balance. |
| `/ranks edit` | Open the visual rank editor. Admin permission: `tweaks.admin.ranks`. |
| `/rank set <player> <rank_id/name>` | Manually assign a player's rank. Admin permission: `tweaks.admin.rank.set`. |

---

## Cosmetics

Purely visual effects unlocked by ordinary in-game items — no commands, no permissions, no toggles.

### Boot Trails

Apply an **armor trim** to a pair of boots using specific materials to unlock a particle trail that follows your footsteps while you walk, run, or sprint. The effect activates automatically when the boots are equipped and stops when they are removed.

Only horizontal movement triggers the trail; standing still, falling, or rotating the camera produces no particles. The trail samples a few times per second to remain lightweight on the network.

| Trim Material | Particle Effect |
|---|---|
| **Redstone** | Redstone dust (`dust`) |
| **Amethyst** | Portal swirls (`portal`) |
| **Copper** | Waxing effect (`wax_on`) |
| **Diamond** | Glow squid ink (`glow_squid_ink`) |
| **Emerald** | Happy villager sparkles (`happy_villager`) |
| **Gold** | Golden hearts (`goldheart_0` / `heart`) |
| **Iron** | Lava drips (`lava`) |
| **Lapis** | Enchantment table runes (`enchant`) |
| **Netherite** | Combination of smoke, lava, and multi-colored dust |
| **Quartz** | Soul fire flames (`soul_fire_flame`) |
| **Resin** | Honey block slide effect (`landing_honey`) |

Any armor trim pattern can be used to activate these effects, as long as the material matches one of the above.

---

## World Protections

These protections are always active and require no commands or configuration:

| Protection | What it does |
|---|---|
| **Farmland Anti-Trample** | Players and mobs cannot trample farmland by walking or jumping on it. |
| **Creeper Block Protection** | Creeper explosions still deal damage but no longer destroy blocks. |
| **Enderman Grief Protection** | Endermen cannot pick up or place blocks. |
| **End Portal Control** | End portals are disabled in configured worlds (`jass:archive` by default). Players who try receive a red message. |
| **Lore-Tagged Emerald Trade Block** | Emeralds carrying any lore cannot be placed into a regular Villager's trade cost slots. Wandering Traders are exempt and still accept lore-tagged emeralds. |

### Spawn Egg Restrictions

Admins can disable Egg Collector drops or spawn-egg-on-spawner conversion on a per-mob basis. Both lists start empty (every mob allowed) and are managed entirely through commands — no manual config edits required.

| Command | What it does |
|---|---|
| `/tconfig eggdrop disable <mob>` | Stops Egg Collector from ever rolling that mob's spawn egg. |
| `/tconfig eggdrop enable <mob>` | Re-allows Egg Collector to roll that mob's spawn egg. |
| `/tconfig spawneregg disable <mob>` | Blocks players from using that mob's spawn egg on a spawner block. |
| `/tconfig spawneregg enable <mob>` | Re-allows that spawn egg to be used on spawners. |

`<mob>` is the vanilla entity key (e.g. `zombie`, `blaze`, `wither_skeleton`). Tab completion lists every mob with a spawn egg, and the `enable` form prefers currently-disabled mobs.

---

## Admin Tools

### Block Log

A lightweight chest-audit system: every time a player adds or removes items from a **chest**, **trapped chest**, or **barrel**, the change is recorded against that container. Admins can review the history at any time.

| Command | Permission | What it does |
|---|---|---|
| `/logs` | `tweaks.admin.logs` | Toggle inspector mode. With inspector mode on, **left-click (punch)** any chest, trapped chest, or barrel to view its log in chat. |

**What you'll see**: each log entry shows the timestamp (server time), the player's name, an `+amount` (added) or `-amount` (removed) tag, and the item involved. Hover any item name for the full vanilla tooltip (enchants, lore, durability, custom data). Hover a player name to see their UUID. Logs are paginated 10 entries at a time with clickable `[<- Prev]` / `[Next ->]` buttons.

**Storage**: logs are stored entirely in the chunk's persistent data container (PDC) — no extra files, no databases. Each chest's history is one compact byte array under that chunk's data, capped at a configurable number of entries per chest (default **500**, admin-configurable via `/tconfig blocklog`; the oldest are dropped first when the cap is hit).

**Retention**: when a chunk is loaded, any log entry older than a configurable number of days (default **30**, admin-configurable via `/tconfig blocklog`) is pruned automatically. Empty chests (no entries left after pruning) drop their PDC key entirely so the chunk's data stays small.

**Coverage limits**:
- Hoppers, droppers, and other automation are **not** logged — only direct player interaction counts.
- Ender chests and shulker boxes are **not** tracked (per-player or portable; no useful audit value).
- Logs persist as long as the chunk does — destroying a chest does not erase its prior history.

### Death Inventory

Browse and restore player inventories captured at the exact moment of death. All 41 item slots (36 main + 4 armor + 1 off-hand) are saved to a YAML file under `plugins/Tweaks/data/deathinventories/<uuid>/` when a player dies.

| Command | Permission | What it does |
|---|---|---|
| `/deathinventory <player> list` | `tweaks.admin.deathinventory` | List all saved records for the player (newest first), with human-readable dates. |
| `/deathinventory <player> <id>` | `tweaks.admin.deathinventory` | Open a 54-slot GUI showing the captured inventory. Click any item to remove it from the view and receive it yourself. |
| `/deathinventory <player> <id> restore` | `tweaks.admin.deathinventory` | Fully restore all saved slots to the online target player. |

Alias: `/di`.

**Restore behaviour**: slots are set one-by-one; the target's current inventory is overwritten slot-for-slot. Both admin and target player must be online to perform a restore. The target receives an in-chat notification.

**Retention**: records older than a configurable number of days (default **30**, admin-configurable via `/tconfig deathinventory`) are deleted on plugin startup. Empty player directories are also pruned to keep the data folder tidy.

### Display Chests

Render a floating preview of chest contents as a non-solid `ItemDisplay` entity.

| Command | Permission | What it does |
|---|---|---|
| `/displaychest [hand\|side\|hand side\|off]` | `tweaks.admin.displaychest` | Toggle setup/removal mode. While on, **left-click** any chest to spawn or update its display. |

**How it works**:
- **Source Priority**: By default, the plugin clones the item in **Slot 0** (the top-left slot) of the chest.
- **Hand Mode**: Use `/displaychest hand` to enter live-hand mode. In this mode, clicking a chest will use whatever item you are **currently holding** at that moment, rather than the chest's contents.
- **Side Mode**: Use `/displaychest side` (or `/displaychest hand side`) to embed the item flush with the clicked face instead of floating it above the container.
  - **Block Items**: Render embedded inside the block with only the clicked face visible (flush with the surface).
  - **Non-Block Items**: Render flat against the face, similar to an item frame.
- **Centering & Rotation**: Floating displays are automatically centered over the container. The spawned `ItemDisplay` uses a **VERTICAL billboard** rotation, meaning it automatically rotates to face whoever is looking at it from any angle. For double chests, it calculates the midpoint of both halves.
- **Persistence**: Display state (including entity UUIDs) is stored in the chunk's Persistent Data Container (PDC). Old displays at the same location are automatically cleaned up when a new one is placed.
- **Removal**: Use `/displaychest off` to enter removal mode, then click a chest to remove its display. This removes both top-floating and side-embedded entries from any clicked face.

### Item Editing

Edit the display name and lore of the item in your main hand. Both commands support legacy `&`-prefixed color codes (e.g. `&c`, `&l`, `&r`) and `&#rrggbb` hex colors. Spaces in the input are preserved naturally, so quoting is not required. The default vanilla italic styling is suppressed automatically — what you type is what you see.

| Command | Permission | What it does |
|---|---|---|
| `/name <name>` | `tweaks.admin.itemedit` | Set the held item's display name. |
| `/name off` | `tweaks.admin.itemedit` | Clear the held item's custom name. |
| `/lore add <line#> <text>` | `tweaks.admin.itemedit` | Insert a lore line at the 1-indexed position (clamped to end+1). |
| `/lore remove <line#>` | `tweaks.admin.itemedit` | Remove the lore line at the 1-indexed position. |

### Chest GUI Copy

Save the targeted chest's full contents to a YAML file under `plugins/Tweaks/guicopies/`. Look at a single or double chest within a configurable distance (default **8** blocks, admin-configurable via `/tconfig itemadmin`, clamped to 1-64) and run the command. The generated file includes:
- **Human Readable Data**: Each item's properties (material, name, lore, enchants) are stored in Bukkit's standard YAML format for easy inspection or manipulation.
- **Zero-Data-Loss Base64**: Every item is also serialized to a Base64 string via `ItemStack.serializeAsBytes`, ensuring all NBT and PDC data is preserved.
- **Java Code Snippet**: A comprehensive, paste-ready Java block that reconstructs the entire inventory. It includes a `buildItem` helper, handles MiniMessage for readable Adventure components, supports specialized ItemMeta (Potions, Books, Banners, etc.), and preserves all PersistentDataContainer (PDC) tags.

The file also records the world key and block coordinates so the snapshot can be restored or audited later.

| Command | Permission | What it does |
|---|---|---|
| `/guicopy [name]` | `tweaks.admin.guicopy` | Save the targeted chest. With no name, one is auto-generated from the world and coordinates. |

The `name` argument is restricted to letters, numbers, dots, dashes, and underscores so it cannot escape the `guicopies/` directory. Existing files are silently overwritten — the chat confirmation indicates whether the save was a fresh write or an overwrite.

### Gamemode Shortcuts

Convenience commands for switching your own gamemode, both gated by the same admin permission.

| Command | Permission | What it does |
|---|---|---|
| `/survival` | `tweaks.admin.gamemode` | Switch the executing player to Survival. |
| `/creative` | `tweaks.admin.gamemode` | Switch the executing player to Creative. |

Both commands are player-only and only affect the executing player; if you're already in the target gamemode, the command no-ops with a yellow note. Use vanilla `/gamemode` to target other players.

### Console Event Logging

Console event records are opt-in and console-only. Every `logging.*` switch in `config.yml` starts
disabled, so enable only the event families you need. Use `/tconfig logging.<feature>.<event>
<true|false>`, `/tconfig list logging` to print all 20 logging categories, `/tconfig list
logging-core` (or another logging category), or the `/tconfig gui`'s main-menu **Logging** tab.
The GUI shows the 20 logging categories over two pages. A confirmed `/tconfig` save updates the
running logger immediately; editing `config.yml` by hand while the server is running requires a
restart.

The logger covers economy, minigames, lottery, protection administration, permissions, ranks,
teleports, player administration, profiles, item administration, death inventories, block logs,
enchantment outcomes, Resource Hunt, Whack, world management, currency recipes, XP bottles, and
config changes. It does not replace existing `WARNING` or `SEVERE` diagnostics. Protection denials
and other high-frequency records are collapsed into a summary every 30 seconds, with at most 2,000
distinct event keys per window and an explicit overflow marker when admissions are dropped.

---

## World Events

### Blood Moon

A rare server-wide event that turns the night crimson and supercharges enchanting.

**How it triggers**: At the start of every full-moon night in the overworld, the plugin rolls a **50% chance** to begin a Blood Moon. When it activates, the entire server sees a dark-red title, a chat broadcast, and an ominous wither sound. A **red boss bar** also appears at the top of the screen, counting down until the event ends at dawn.

**What it does**: While a Blood Moon is active, the chance for an enchantment rolled at the enchanting table to become a [quality variant](#enchantment-quality) is boosted (default 10% to 50%, both admin-configurable via `/tconfig enchantments`) per applicable enchantment. Tier weights (uncommon/rare/epic/legendary) still apply on top of that.

**How it ends**: The Blood Moon fades automatically at the next dawn. **Sleeping is blocked** once the night's fate is rolled (at dusk), preventing players from skipping the event before it officially begins.

**Checking the moon**: Any player can run `/fullmoon` to see a rough estimate of how many real-world minutes remain until the next full-moon night begins.

**Forcing one (admin)**: Admins with `tweaks.admin.bloodmoon` can run `/bloodmoon` to advance the overworld to the start of the next full-moon night and guarantee activation.

---

## Minigames

### Resource Rupee

A rare currency item that can be found while gathering in resource worlds or earned as rewards. Resource Rupees are special emeralds with a custom name and lore that the plugin recognizes as currency.

- **Conversion**: You can convert between Rupees and Rupee Blocks in any crafting grid.
  - 9 **Resource Rupees** → 1 **Resource Rupee Block**.
  - 1 **Resource Rupee Block** → 9 **Resource Rupees**.
- **Stackable**: Both Rupees and Rupee Blocks stack normally and can be stored in any container.
- **Visuals**: They feature a distinct green name and a `"...the Wanderer's Path..."` lore line to distinguish them from regular emeralds.

### Whack an Andrew

A "Whack-a-Mole" style minigame where armor stands pop up on designated blocks in an arena. Players compete to hit as many as possible, and the top 3 scorers receive rewards.

This is entirely admin-managed — see the [admin commands](#admin-commands) section for setup instructions.

### Blackjack

An in-world **Player-versus-Dealer** casino game played at physical tables using 3D card models.

**Requirement**: Players must have the **`dqc.cards`** resource pack enabled to see the 3D card models.

#### Player vs. Dealer (PvD)

The standard casino experience. Play against an automated dealer at tables with a pre-set bet amount.

- **Interaction**: Right-click the **MIDDLE** button to start a game or clear a finished board.
- **Controls**: **LEFT**=Hit, **MIDDLE**=Start/Clear, **RIGHT**=Stand.
- **Rules**: Standard 52-card deck (reshuffled every game); Dealer stands on all 17s; Blackjack pays 3:2.
- **Dealer Mannequin**: A 'LimeLush' visual mannequin appears on the dealer side at game end. It celebrates on dealer wins and performs a death animation on player wins.
- **Rakeback**: Losing hands grant a small percentage of the bet back, based on your [Rank](#ranks).
- **Balance safety**: A stake whose whole-dollar debit would leave the supported balance range is
  refused; rejected payouts or rakeback are routed to the House account for recovery rather than
  discarded.
- **Free/Practice Tables**: Admins can create tables with **bet 0** (use `free` or `0` as the bet argument). These tables display **"Bet: FREE"** on the hologram, perform no currency transfers, and skip rakeback entirely — ideal for learning the game.

When DiscordSRV is installed and `discord.channel-id` is configured, each settled paid hand is
included in a short grouped `diff` code block showing the player's signed net change. Pushes and
free/practice hands produce no line. Inactivity forfeitures are reported as losses.

#### General Mechanics

- **Inactivity Timeout**: If a game sits idle for **10 minutes** (no hit/stand/deal actions), the session is evicted. Any escrowed bets are forfeited. Setup/betting phases time out after **3 minutes**.
- **Rendering**: Cards lie flat on the table surface. Jacks, Queens, and Kings feature custom player-head portraits.
- **Orientation**: Card spreads automatically use the table's wide axis (X or Z) for all button facings. Upright orientation is deterministic via per-facing yaw: North=180, South=0, East=90, West=270.
- **Face-down Cards**: The dealer's hole card remains face-down until the player stands to prevent information leaks.

#### Table Construction (Admin)

Admins can build and register Blackjack tables in the world.

- **Footprint**: A table must be a solid **2×3** (or 3×2) block area (e.g., stone, wood, etc.). No carpet is required.
- **Controls**: Three wall-mounted buttons (LEFT/MIDDLE/RIGHT) must be placed on one of the 3-long sides.
- **Registration**: Stand near the table and run `/blackjack createtable <bet> [hexColor]`, then right-click the **MIDDLE** button.
- **Bet**: Use any positive integer for a currency-backed table, or `0`/`free` for a no-stakes practice table.
- **Card Backs**: The optional `[hexColor]` argument (e.g., `#FF8800` or `FF8800`) sets a custom tint for card backs at that table.

### Roulette

An in-world roulette wheel — a real physical build the server team constructed, with this plugin overlaying click targets, betting, a spin animation, and payouts on top of it.

- **Bet families**: **Straight-up** on a single pocket (1-36, pays 36:1; pocket **0**/Green pays **50:1**), a **dozen/thirds** (1st/2nd/3rd twelve, pays 3:1), or a **colour** (red/black, pays 2:1). A win credits exactly `stake × odds` — your wagered stake itself is never returned on top of that, even on a win. Green **0** loses every dozen/colour bet — there is no odd/even, high/low, or column betting on this board.
- **Labeled bets**: Every clickable segment shows a floating label with its bet description and odds (e.g. "Straight: 18 (Black)" — 36:1, "Dozen: 1st (1-12)" — 3:1, "Red"/"Black" — 2:1, "Straight: 0 (Green)" — 50:1, shown in green) for as long as the board is active. Labels hide during the spin and reappear once the result is shown. Colors alternate strictly by parity (odd = red, even = black).
- **Color-coded segments**: Each clickable segment is itself a colored block matching its bet — red/black/green wool for straight-up and colour bets, grey/yellow/orange terracotta for the 1st/2nd/3rd dozen — so the betting area is visually readable at a glance. These also hide during the spin and reappear with the labels.
- **Marker geometry**: Each colored `ItemDisplay` marker and its invisible clickable hitbox use the
  same scale and center, so the visual target and interaction footprint line up. Markers use a
  vertical billboard to face viewers; the hitboxes remain axis-aligned, so the display orientation
  does not rotate the clickable box.
- **Sticky stake**: Set your wager once with `/roulette stake <amount>`, then every segment you click wagers that amount. Multiple bets in the same round are allowed and stack — including clicking more than one physical segment of the same dozen, which places two independent bets.
- **Shared round**: The first bet on an idle board opens a **30-second** betting window for everyone nearby — there's no host and no spin button for players. Once the window closes, the wheel spins, a ball orbits and settles into the winning pocket, and the round settles automatically.
- **Bets are final**: There is no un-betting, no refund on quitting mid-round, and winnings are still credited to your balance even if you log off before the wheel stops.
- **Balance safety**: A stake whose whole-dollar debit would leave the supported balance range is
  refused; rejected winnings, rakeback, or shutdown refunds are routed to the House account for
  recovery rather than discarded.
- **Settlement summary**: Your personal result message shows the amount wagered and the amount actually won (winnings only, not your returned stake) — e.g. a $100 stake winning at 36:1 shows wagered $100, won $3,600.
- **Big win announcements**: If your winnings reach 8x your wager, the whole server is notified.
- **House balance**: A hologram over each wheel shows the single server-wide house balance — the same
  account Blackjack's losses and each Roulette player's net losses feed.

When DiscordSRV is installed and `discord.channel-id` is configured, every bettor gets one grouped
`diff` line showing their signed net change, including break-even results. A big-win event is not
duplicated with a second Discord message. Discord settlement lines are emitted for money outcomes
even when the wheel is settling during chunk unload or shutdown.

#### Discord Betting

The Discord commands are native Discord slash commands and do not collide with the in-game
`/roulette` command. An administrator designates the one board used for Discord wagers with
`/roulette setdiscord`; `/roulette cleardiscord` removes that designation. Linked players can then
use `/balance`, `/bet`, `/roulette`, and `/mybets` in the configured `discord.betting-channel-id`.
Account linking is handled by DiscordSRV (`/discord link` in game), and linked players may check
their balance or bet while offline in Minecraft. `/bet` uses the same board min/max and cumulative
exposure rules as physical betting. Each settled round begins its grouped results block with a
header such as `@@ Roulette — 17 Black @@`, and the bettor receives an ephemeral follow-up with
their mention, pocket, colour, and outcome.

#### Board Construction (Admin)

- `/roulette setdiscord` begins the designation flow; right-click the board's spin control to expose that board to the native Discord betting commands.
- `/roulette cleardiscord` clears the Discord betting designation directly. Both commands are refused while a round is in progress.

- Stand near the physical wheel and run `/roulette createboard <min> <max>`, then right-click the button or lever that will act as the board's admin spin control.
- `<min>`/`<max>` set the board's stake range; both must be whole numbers, `min` at least 1 and no greater than `max`.
- `/roulette removeboard` begins the removal flow the same way — right-click the target board's spin control to unregister it. Removal is refused while a round is in progress.
- Once a board is registered, right-clicking its spin control (`tweaks.roulette.forcespin`) force-closes an open betting window and spins immediately — the exact same spin path a window naturally expiring uses, just triggered early. It's a no-op with a message if there's no open round to force, or if the wheel is already spinning.

### Resource Hunt

A gathering minigame that runs in the **`jass:resource`** (Overworld) or **`jass:resource_nether`** (Nether) world. The active world is picked uniformly at random at server startup (each world that has at least one configured target has an equal chance, regardless of how many entries are in each section), and then **each player receives their own unique task** from that world's pool when they join.

**Target Assignment**:
- **Unique Tasks**: When you join, the plugin assigns you a random task from the active world's pool.
- **Fair Distribution**: The system prefers tasks that no other player is currently working on. If all tasks are taken, duplicates are allowed.
- **Persistence**: Your assigned task stays the same for the entire session.
- **Re-rolls**: If you don't like your assigned task, use `/reroll` to get a new one. Your first re-roll each session is **free**; subsequent re-rolls cost **1 Resource Rupee** each.

**Task Categories**:
Tasks are categorized by how you must obtain or interact with the target. Depending on the category, targets are either Minecraft **Materials** (for items/blocks) or **Entity Types** (for mobs).
- **Collect**: Any supported way of obtaining the item (drops, chests, mob loot like ghast tears, etc.).
- **Kill**: Kill the targeted entity.
- **Smelt**: Smelt the target item (e.g., smelting raw iron or iron ore for an Iron Ingot task). Supports the **Smelter** enchantment.
- **Enchant**: Enchant the target item (Overworld only).
- **Shear**: Shear the targeted entity (Overworld only). Supports specific sheep colors (e.g., `red_sheep`).
- **Breed**: Breed the targeted entity; one successful parent pairing counts as one. Turtle, frog, and sniffer targets are credited at pairing time, before eggs are laid (eggs and hatched babies do not add progress).
- **Craft**: Craft the targeted item.
- **Barter**: Receive the target item from a Piglin barter (Nether only).

**Progress & Tiers**:
Each task defines a **base amount** and a **multiplier** that produce three cumulative tier thresholds:

| Tier | Threshold |
|---|---|
| Tier 1 | `amount` |
| Tier 2 | `round(amount × multiplier)` |
| Tier 3 | `round(amount × multiplier²)` |

Crossing each threshold grants the **`resource`** reward **once**. Rewards are claimable via `/reward claim`.

While inside the active resource world, a **green boss bar** shows your progress: **"Category # <Name>"** (e.g., "Kill 20 Sheep"). The bar resets to the next tier once you clear the current one and disappears after Tier 3.

**Protection & Anti-Exploit**:
- **Disallowed Items**: Traveling **into** a resource world via `/resource`, `/back`, or `/tpa` fails if you carry restricted items (managed in `resource_hunt_items.yml`).
- **Anti-recount**: Items counted toward progress (for collect, smelt, enchant, and craft) carry an invisible PDC tag and won't be counted again. This tag is removed when you bring items out of the resource world.
- **Nether Safety**: The plugin generates a **5×5 bedrock platform** at Y=64 if no safe landing is found in the Nether resource world. A ring of **Nether Brick Fence** posts surrounds the platform perimeter at Y=65, acting as a guardrail to prevent players from immediately falling off the edge.

**Configuration** (`plugins/Tweaks/resource_hunt.yml`):
Tasks are grouped by world and category. Each entry is either a bare amount (multiplier defaults to `2.0`) or `"<amount>:<multiplier>"`.

```yaml
overworld:
  collect:
    iron_ore: "7:2.0"
  kill:
    sheep: "20:2.0"
  smelt:
    iron_ingot: "20:1.75"
  enchant:
    diamond_pickaxe: 5
  shear:
    sheep: "20:2.0"
    red_sheep: "2:2.0"
  breed:
    cow: 2
nether:
  barter:
    water_bottle: "10:2.5"
```

**Allowed Items** (`plugins/Tweaks/resource_hunt_items.yml`): a list of materials allowed to be carried into the resource world. Manage at runtime via `/tconfig resourceitems <add|remove> <item>`.

**Admin setup**: the `resource` reward shell is auto-created on first plugin load; populate it with `/reward edit resource`.

**Admin Overrides**: Admins can manually set a player's target using `/resource settarget [player] <target_key>`. The target key corresponds to the identity keys in `resource_hunt.yml` (e.g., `iron_ore`, `sheep`, `water_bottle`). This command supports tab completion for both online players and all available target keys.

**World restrictions**: `jass:resource` and `jass:resource_nether` are single-purpose for gathering:

| Restriction | What it does |
|---|---|
| End portals | Blocked in resource worlds. |
| Nether portals | Blocked in resource worlds. |
| `/sethome` | Refused in resource worlds. |
| Login eject | Players logging in inside a resource world are sent to `/warp newspawn`. |
| Item Whitelist | Restricts items that can be brought in via `/resource`, `/back`, or `/tpa`. |
| Ender Chests | Cannot be opened or used while in a resource world. |

---

### Rewards

A system for creating and distributing item rewards. Rewards are created by admins using a chest GUI and can be awarded to players by the minigame system.

| Command | What it does |
|---|---|
| `/reward claim` | Claims all pending rewards. Items are added to your inventory; overflow drops at your feet. |

---

## Land Protection

A hybrid, PDC-backed land protection system that allows players and admins to claim territory, manage members, and configure fine-grained action flags.

### Claims & Selection

Territory is claimed in **full-chunk increments**.

1.  **Selection**: Use the selection wand (or `/region wand`) to mark two corners of your desired area. The wand material is configurable in `config.yml` via `protection.selection-tool` (defaults to **Stone Axe**).
    - **Left-click** a block to select chunk 1.
    - **Right-click** a block to select chunk 2.
    - **Relaxed Input**: Clicking any block in a chunk anchors that entire chunk (no corner snapping required).
    - A particle outline will show your current selection.
    - Use `/region clear` (alias `/rg clear`) to drop your current selection.
2.  **Claiming**: Run `/region claim <name>` to protect the selected chunks. Names are trimmed,
    lowercased, and must match `^[a-z0-9_-]{1,32}$`; `__global__`, `_deleted`, and `_legacy` are
    reserved. Existing legacy names are loaded unchanged.
    - **Overlap Prevention**: You cannot claim territory that overlaps an existing region in the same world unless you own it.
    - **Per-World Uniqueness**: Region names are unique per world. Two regions can share a name (e.g., "home") if they are in different worlds.
    - **Public Claim Worlds**: An admin can add namespaced world keys (for example, `minecraft:overworld`) to `protection.public-claim-worlds` so any non-admin player can claim there without `tweaks.protection.purchaseable`. Public non-admin claims still incur the normal cost, global per-player chunk limit, and overlap/geometry checks; admins retain their free/unlimited bypass, and removing a world never changes existing claims. Public claim access does not grant `unclaim`, `info`, `flag`, or `member`, so those companion permissions are still required for a fully self-service claim.
3.  **Visuals**: Use `/region info` while standing in a claim to see its boundaries and details.
4.  **Restore Selection**: Use `/region select <name>` to restore the selection wand boundaries to match an existing region you own.

### Roles & Hierarchy

Regions support multiple roles and hierarchical parenting.

- **Roles**:
  - **Owner**: Full control. Can unclaim and manage managers/members.
  - **Manager**: Delegated control. Can edit flags and add/remove members. Cannot edit the manager list or unclaim — those require the owner (or an admin).
  - **Member**: Build access. Can bypass most protection flags (e.g. they can always build/break).
- **Group Membership**: Instead of individual players, you can grant roles to entire permission groups (e.g. `builders`). All members of the group automatically gain the role's privileges.
- **Sub-regions**: You can nest one region inside another using `/region setparent <child> <parent>`.
  - Sub-regions must be at least one full chunk.
  - Sub-region flags **override** their parent's flags.
  - **Containment**: A sub-region must be fully contained within its parent's boundaries.
  - **Sibling Separation**: A sub-region cannot overlap another sub-region belonging to the same parent.
  - **Ownership**: A sub-region must share its parent's owner, and nesting requires owning both regions (or being an admin).
  - **Cascade Unclaim**: Unclaiming a region permanently deletes all of its sub-regions and refunds each region to its own owner.
  - Membership is independent; being a member of a parent does not automatically make you a member of its children.

All named-region commands resolve in the sender's current world. An admin may append a loaded world
name (`/region info home nether`) to address another world; the trailing-world token is consumed only
for admins when the subcommand's minimum arguments remain. Console/RCON callers must provide the world
argument and receive an explicit world-required message otherwise. Same-named regions remain isolated
per world, including pending chunk stamps, orphan cleanup, and `__global__` wilderness flags.

### Advanced Flags & Targeting

Flags control what non-members can do in a region. Rules can target specific groups:

**Syntax**: `/region flag [name] <flag> <value...> [target]`

- **Defaults**: If `[name]` is omitted, it defaults to the region you are currently standing in.
- **Targets**: `owner`, `manager`, `member`, `default` (everyone), or a permission group name (e.g. `admin`).
- **Boolean Flags**: `BLOCK_BREAK`, `BLOCK_PLACE`, `CONTAINER_ACCESS`, `INTERACT`, `REDSTONE`, `EXPLOSION`, `PVP`, `MOB_GRIEFING`, `MOB_SPAWNING`, `INVINCIBILITY`, `ENTRY`.
  - Use `true|false` as the value.
- **EntityType-Specific Flags**: `ALLOW_MOB_SPAWN`, `DENY_MOB_SPAWN`. (No-op pending entity-list storage).
- **Material-Specific Flags**: `ALLOW_BLOCK_BREAK`, `DENY_BLOCK_BREAK`, `ALLOW_BLOCK_PLACE`, `DENY_BLOCK_PLACE`.
  - Use a space-separated list of block materials as the value.
- **Gamerule Overrides**: Region flags take precedence over world gamerules. For example, if `MOB_GRIEFING` is set to `true` in a region, creepers will destroy blocks there even if the world's `mobGriefing` is `false`.
- **ENTRY Flag**: Controls who may physically enter the region.
  - When `ENTRY` is set to `false` for `default`, non-members are blocked at the border and shown an action-bar message.
  - Per-role overrides work the same as all other boolean flags (e.g. `ENTRY true manager` to permit managers).
  - Players who respawn inside an ENTRY-denied region are redirected to the world's default spawn point.

### Administrative Region Tools

Admins with `tweaks.protection.admin` can inspect claims across every world with
`/region list [player|page] [page]`. Results exclude the per-world `__global__` wilderness entries,
are sorted by region name, and show eight regions per page with clickable navigation. Use
`/region tp <name> [world]` to teleport yourself to a region's safe centre; the destination chunk is
loaded asynchronously and the locator searches for solid ground without placing or changing blocks.

`/region togglebypass` is available to admins with `tweaks.protection.bypass`. It enables a
session-only bypass for in-world protection checks, including block actions, container access, and
`ENTRY` teleport/movement denial. The bypass is cleared on join and quit, so an admin must opt in
again after every login. It does not grant region command permission or ownership and does not alter
the region flags themselves.

### Command UX & Tab Completion

- **Usage Info**: If you run a protection command incorrectly, the plugin will display friendly usage information (filtered by your permissions and current-world authorization rules).
- **Tab Completion**: Fully implemented for the legacy command system.
  - **Subcommands**: Filtered based on your permissions and current-world authorization rules.
  - **Region IDs**: Suggestions are ownership-aware. Owners and managers see their regions. Admins with the `tweaks.protection.admin` permission see all regions in their current world (limited to the first 100 results).
  - **Players & Groups**: Online players and existing permission groups (prefixed with `group:`) are suggested for `addmember`/`addmanager`, while current members/managers (including groups) are suggested for `removemember`/`removemanager`.
  - **Flags & Targets**: Region flags, targets, block materials, and entity types are fully tab-completable.

### Region GUI

Type `/region gui` while standing in a region you own (or manage) to open a clickable Paper Dialog dashboard for that claim — no need to remember flag/target syntax. Pass an explicit name (`/region gui <name>`) to manage a region from elsewhere. Standing in overlapping sub-regions opens the innermost (leaf) one. Owners and managers can open their own regions; admins with `tweaks.protection.admin` can open any region.

From the dialog you can:
- Toggle boolean flags and per-role overrides (owner/manager/member/group).
  - **Permission Groups**: The GUI dynamically lists all registered permission groups, allowing you to set specific rules for group members (e.g. `ALLOW_BLOCK_BREAK` for `group:builders`).
- Add or remove members and managers.
- Edit material and entity lists for flags that support them.

### Resolution Priority

When an action occurs, the system checks rules in this order:
1.  **Material Lists**: If the block is in a `DENY` list, the action is blocked. If in an `ALLOW` list, it's permitted.
2.  **Targeted Boolean Rules**: The most specific rule wins: `GROUP` > `OWNER` > `MANAGER` > `MEMBER` > `DEFAULT`.
3.  **Hierarchy**: If no rule is found in the current region, the system walks up to the **parent region** and repeats the check.
4.  **Wilderness Default**: If no rule is found in the entire hierarchy, members are allowed and non-members are blocked.

---

## Commands Reference

### Player Commands

| Command | Description |
|---|---|
| `/home [name]` | Teleport to a saved home (default: "default"). |
| `/sethome [name]` | Save a home at your current location. |
| `/delhome [name]` | Delete a saved home. |
| `/homes` | List all your saved homes. |
| `/warp <name>` | Teleport to a server warp. |
| `/warps` | List all available warps. |
| `/tpa <player>` | Request to teleport to a player. |
| `/tpahere <player>` | Request a player to teleport to you. |
| `/tpaccept` | Accept an incoming TPA request. |
| `/tpdeny` | Deny an incoming TPA request. |
| `/back` | Return to your previous location. |
| `/spawn` | Teleport to the server spawn. |
| `/fly` | Toggle flight mode. |
| `/nv` | Toggle permanent night vision. |
| `/nick <nickname>` | Set your display name with color codes. |
| `/nick off` | Remove your nickname. |
| `/itemfilter [toggle\|mode\|add <item>...\|remove <item>...\|list\|clear [mode]]` | Manage pickup filter. Alias: `/if`. |
| `/condense [all]` | Compact 9x granular items into their block form. |
| `/toolprotect [on\|off]` | Toggle ToolProtect on/off. |
| `/toolprotect durability <n>` | Set remaining-durability threshold for ToolProtect. |
| `/afk` | Toggle AFK status. |
| `/fullmoon` | Show estimate for next full moon. |
| `/displaychest [hand\|off]` | Toggle display chest setup mode. |
| `/tprm [gui\|group\|user]` | Manage server-side permissions. Alias: `/perms`. |
| `/help [section]` | Show comprehensive help menu. |
| `/region <subcommand>` | Land protection management. Alias: `/rg`. |
| `/reward claim` | Claim pending minigame rewards. |
| `/balance` | View your current balance. Alias: `/bal`. |
| `/balance hide` | Toggle whether your balance is visible in the tab list. |
| `/house balance` | View the casino house account (admin). |
| `/lottery info` | View the public lottery status and computed pot. |
| `/lottery entries` | List current lottery entrants (public, with a short non-admin cooldown). |
| `/ranks` | List all available ranks and their benefits. |
| `/rankup` | Purchase the next rank in the hierarchy. |
| `/reroll` | Re-roll your current Resource Hunt target. |
| `/resource` | Teleport to the resource world. |
| `/roulette stake <amount>` | Set your sticky Roulette stake; every segment click wagers it. |

### Admin Commands

| Command | Permission | Description |
|---|---|---|
| `/setwarp <name>` | `tweaks.admin.setwarp` | Create a server warp. |
| `/delwarp <name>` | `tweaks.admin.delwarp` | Delete a server warp. |
| `/home <player> <name>` | `tweaks.admin.home` | Teleport to another player's home. |
| `/sethome <player> <name>` | `tweaks.admin.sethome` | Set a home for another player. |
| `/delhome <player> <name>` | `tweaks.admin.delhome` | Delete another player's home. |
| `/homes <player>` | `tweaks.admin.homes` | List another player's homes. |
| `/nick off <player>` | `tweaks.admin.nick` | Remove another player's nickname. |
| `/tprm` | `tweaks.admin.permissions` | Open the Permissions GUI. |
| `/tprm group <name> <create\|delete\|addperm\|delperm\|inherited-from>` | `tweaks.admin.permissions` | CLI group management. |
| `/tprm user <player> <addperm\|delperm\|setgroup>` | `tweaks.admin.permissions` | CLI user management. |
| `/ranks edit` | `tweaks.admin.ranks` | Open the visual rank editor. |
| `/rank set <player> <rank_id/name>` | `tweaks.admin.rank.set` | Manually assign a player's rank. |
| `/region claim <name>` | `tweaks.protection.purchaseable` (or a listed `protection.public-claim-worlds` world) | Claim territory using wand selection; non-admin public claims still pay and obey the chunk-limit and overlap checks. |
| `/region unclaim <name> [world]` | `tweaks.protection.unclaim` | Remove a region claim and permanently delete its sub-regions, refunding each owner. Alias: `/rg unclaim`. |
| `/region info [name] [world]` | `tweaks.protection.info` | Show region details. Alias: `/rg i`. |
| `/region select <name> [world]` | `tweaks.protection.info` | Restore wand selection to match a region. |
| `/region clear` | — | Drop the current wand selection. |
| `/region wand` | — | Get the selection wand. |
| `/region addmember <r> <p|group:name> [world]` | `tweaks.protection.member` | Add a member (player or group) to a region. Alias: `/rg am`. |
| `/region removemember <r> <p|group:name> [world]` | `tweaks.protection.member` | Remove a member from a region. Alias: `/rg rm`. |
| `/region addmanager <r> <p|group:name> [world]` | `tweaks.protection.member` | Add a manager (player or group) to a region. Alias: `/rg aman`. |
| `/region removemanager <r> <p|group:name> [world]` | `tweaks.protection.member` | Remove a manager from a region. Alias: `/rg rman`. |
| `/region flag [name] <f> <v...> [world]` | `tweaks.protection.flag` | Set a targeted boolean or material flag. |
| `/region unflag <r> <f> [t] [world]` | `tweaks.protection.flag` | Remove a targeted flag rule or material list. |
| `/region setparent <c> <p> [world]` | `tweaks.protection.purchaseable` | Nest a region inside another; you must own both regions unless admin. |
| `/region unsetparent <c> [world]` | `tweaks.protection.purchaseable` | Remove region parenting. |
| `/region gui [name] [world]` | `tweaks.protection.info` | Open the dialog dashboard for a region. |
| `/region list [player\|page] [page]` | `tweaks.protection.admin` | List non-global regions across worlds, eight per page. |
| `/region tp <name> [world]` | `tweaks.protection.admin` | Teleport yourself to a region's asynchronously located safe centre. |
| `/region togglebypass` | `tweaks.protection.bypass` | Toggle the session-only in-world protection bypass. |
| `/blackjack createtable <bet> [hexColor]` | `tweaks.blackjack.createtable` | Create button-linked Blackjack tables. |
| `/blackjack removetable` | `tweaks.blackjack.removetable` | Remove a Blackjack table. |
| `/roulette createboard <min> <max>` | `tweaks.roulette.createboard` | Begin Roulette board setup; right-click a button/lever to finalize. |
| `/roulette removeboard` | `tweaks.roulette.removeboard` | Begin Roulette board removal; right-click the board's spin control. |
| `/roulette setdiscord` | `tweaks.roulette.setdiscord` | Designate the board used by native Discord Roulette betting; right-click its spin control. |
| `/roulette cleardiscord` | `tweaks.roulette.setdiscord` | Clear the current Discord Roulette betting designation. |
| `/house <balance\|add\|remove\|set\|pay>` | `tweaks.admin.house` | View or administer the server-wide casino house account. |
| `/lottery draw` | `tweaks.admin.lottery` | Draw the server-wide lottery and announce the winner. |
| `/lottery baseline <amount>` | `tweaks.admin.lottery` | Set the lottery growth baseline. |
| `/lottery fallback [<amount>]` | `tweaks.admin.lottery` | View or set the live lottery fallback floor. |
| `/tconfig` / `/tconfig gui` | `tweaks.admin.config` | Open the admin config Dialog GUI (players only; console gets plain usage). |
| `/tconfig list [category]` | `tweaks.admin.config` | Print every registered setting and its current value. |
| `/tconfig list logging` | `tweaks.admin.config` | Print the 20 logging categories and their current values. |
| `/tconfig list discord` | `tweaks.admin.config` | Print the Discord announcement, webhook, Roulette betting, grouping, and voice-stat settings. |
| `/tconfig discord.<setting> <value>` | `tweaks.admin.config` | Edit a Discord setting; channel IDs accept a raw numeric ID or an empty value to disable that surface. |
| `/tconfig max_homes <int>` | `tweaks.admin.config` | Set global max homes per player. |
| `/tconfig max_chunks <int>` | `tweaks.admin.config` | Set global max chunk claims per player. |
| `/tconfig egg_collector_drop_chance <0.0-100.0>` | `tweaks.admin.config` | Set the base Egg Collector drop chance. |
| `/tconfig eggdrop <disable\|enable> <mob>` | `tweaks.admin.config` | Disable/enable Egg Collector drops for a mob. |
| `/tconfig spawneregg <disable\|enable> <mob>` | `tweaks.admin.config` | Disable/enable spawn egg usage on spawners. |
| `/tconfig resourceitems <add\|remove> <item>` | `tweaks.admin.config` | Manage resource world item whitelist. |
| `/tconfig world-profiles list` | `tweaks.admin.config` | List every world-key -> profile/tag/color mapping. |
| `/tconfig world-profiles add <world-key> <profile> <label> <color>` | `tweaks.admin.config` | Register a new world-key mapping (profile is only settable here — see [World Profiles](#world-profiles)). |
| `/tconfig world-profiles remove <world-key>` | `tweaks.admin.config` | Remove a world-key mapping (that world falls back to the default profile/tag). |
| `/tconfig world-profiles edit <world-key> <label> <color>` | `tweaks.admin.config` | Change an existing mapping's tab-list tag/color (its profile cannot be changed). |
| `/tconfig <path> <value>` | `tweaks.admin.config` | Generic setter for any other registered setting by its config.yml path, e.g. `protection.selection-tool <material>`, `protection.public-claim-worlds <add\|remove> <world-key>`, `fly-advancement <namespaced-key>`, `fly-worlds <add\|remove> <world>`, `disabled-end-portal-worlds <add\|remove> <world>`, `economy.daily-reward-base <amount>`, `economy.streak-multipliers <day 1-7> <multiplier>`, `lottery.pot-multiplier <0.0-10.0>`, `blocklog.retention-days <days>`, `blocklog.max-entries-per-chest <n>`, `deathinventory.retention-days <days>`, `enchantments.spawner-pickup.drop-chance-percent <0.0-100.0>`, `enchantments.spawner-pickup.uses <n>`, `enchantments.egg-collector.uses <n>`, `enchantments.quality.chance-percent <0.0-100.0>`, `enchantments.quality.blood-moon-chance-percent <0.0-100.0>`, `enchantments.lumberjack.max-logs <n>`, `itemadmin.tool-protect.default-threshold <n>`, `itemadmin.tool-protect.warn-cooldown-ms <ms>`, `itemadmin.gui-copy.max-distance <1-64>`, `xpbottle.orbs-per-emerald <n>` (restart required). |
| `/more` | `tweaks.admin.more` | Maximize the stack size of the held item. |
| `/invsee <player>` | `tweaks.admin.invsee` | View and modify an online player's inventory. |
| `/bloodmoon` | `tweaks.admin.bloodmoon` | Force-activate the Blood Moon event. |
| `/reward create <name>` | `tweaks.admin.reward` | Create a new reward template. |
| `/reward edit <name>` | `tweaks.admin.reward` | Open the reward editor GUI. |
| `/reward give <player> <reward> [count]` | `tweaks.admin.reward` | Queue a reward grant for a player. |
| `/whack arena` | `tweaks.admin.whack` | Start Whack-an-Andrew arena setup. |
| `/whack corner1` | `tweaks.admin.whack` | Set arena corner 1. |
| `/whack corner2` | `tweaks.admin.whack` | Set arena corner 2. |
| `/whack setblocks <material...>` | `tweaks.admin.whack` | Scan arena for spawn point blocks. |
| `/whack start` | `tweaks.admin.whack` | Start a Whack-an-Andrew game. |
| `/whack stop` | `tweaks.admin.whack` | Stop the current game. |
| `/whack setreward <1\|2\|3> <name>` | `tweaks.admin.whack` | Set the reward for 1st/2nd/3rd place. |
| `/logs` | `tweaks.admin.logs` | Toggle chest-log inspector mode. |
| `/deathinventory <player> list` | `tweaks.admin.deathinventory` | List saved death inventories for a player (newest first). Alias: `/di`. |
| `/deathinventory <player> <id>` | `tweaks.admin.deathinventory` | Open a GUI of the saved inventory; click items to claim them. |
| `/deathinventory <player> <id> restore` | `tweaks.admin.deathinventory` | Restore the saved inventory slots to the online target player. |
| `/name <name>\|off\|blank` | `tweaks.admin.itemedit` | Set or clear the held item's display name. |
| `/lore add <line#> <text>` | `tweaks.admin.itemedit` | Insert a lore line at the 1-indexed position. |
| `/lore remove <line#>` | `tweaks.admin.itemedit` | Remove the lore line at the 1-indexed position. |
| `/guicopy [name]` | `tweaks.admin.guicopy` | Save the targeted chest's contents to disk. |
| `/survival` | `tweaks.admin.gamemode` | Switch your gamemode to Survival. |
| `/creative` | `tweaks.admin.gamemode` | Switch your gamemode to Creative. |
| `/resource settarget [p] <t>` | `tweaks.admin.resource.settarget.self/other` | Override a player's Resource Hunt target. |
| `/displaychest [hand\|off]` | `tweaks.admin.displaychest` | Toggle display chest setup/removal mode. |

---

## Permissions Reference

| Permission | What it grants |
|---|---|
| `tweaks.bypass.homes` | Allows bypassing the maximum home count limit. |
| `tweaks.protection.purchaseable` | Gates `setparent`/`unsetparent` everywhere and `claim` unless the current world is listed in `protection.public-claim-worlds`. Non-admin public claims still incur Resource Rupee costs and count against the global chunk limit. |
| `tweaks.protection.unclaim` | Allows unclaiming owned land regions. |
| `tweaks.protection.info` | Allows selecting regions and viewing region information/flags. |
| `tweaks.protection.member` | Allows managing (adding/removing) members and managers of a region. |
| `tweaks.protection.flag` | Allows setting and unsetting region protection flags. |
| `tweaks.protection.admin` | Grants full administrative access over all regions, bypassing limits and ownership checks. |
| `tweaks.protection.bypass` | Allows an admin to opt into the session-only bypass for in-world protection checks. |
| `tweaks.admin.home` | Allows teleporting to any player's home. |
| `tweaks.admin.sethome` | Allows setting a home for any player. |
| `tweaks.admin.delhome` | Allows deleting the home of any player. |
| `tweaks.admin.homes` | Allows listing the homes of any player. |
| `tweaks.admin.setwarp` | Allows setting server warps. |
| `tweaks.admin.delwarp` | Allows deleting server warps. |
| `tweaks.admin.nick` | Allows setting/removing nicknames. |
| `tweaks.admin.config` | Allows modifying plugin configurations via commands and GUI. |
| `tweaks.admin.more` | Allows a player to maximize the stack size of their currently held item. |
| `tweaks.admin.invsee` | Allows viewing and modifying another online player's inventory. |
| `tweaks.admin.bloodmoon` | Allows forcing the next full moon to be a Blood Moon. |
| `tweaks.admin.reward` | Allows managing and claiming minigame rewards. |
| `tweaks.admin.whack` | Allows access to Whack-an-Andrew minigame admin commands. |
| `tweaks.admin.resource.settarget.self` | Allows setting your own Resource Hunt target. |
| `tweaks.admin.resource.settarget.other` | Allows setting another player's Resource Hunt target. |
| `tweaks.admin.logs` | Allows toggling inspector mode to view chest interaction logs. |
| `tweaks.admin.deathinventory` | Allows viewing and restoring player death inventories via `/deathinventory`. |
| `tweaks.admin.itemedit` | Allows editing item properties, such as display name and lore. |
| `tweaks.admin.guicopy` | Allows saving a targeted chest's contents to a YAML file for GUI layouts. |
| `tweaks.admin.gamemode` | Allows switching gamemodes via command. |
| `tweaks.admin.balance` | Allows administrative balance management (set, add, remove). |
| `tweaks.admin.house` | Allows viewing and administering the server-wide casino house account. |
| `tweaks.admin.lottery` | Allows drawing and administering the server-wide lottery; viewing entries is public. |
| `tweaks.admin.ranks` | Allows access to the administrative rank editor via `/ranks edit`. |
| `tweaks.admin.rank.set` | Allows manually assigning a player's rank via `/rank set`. |
| `tweaks.admin.permissions` | Grants full access to the custom permission management system, including groups, users, and GUI editor. |
| `tweaks.blackjack.createtable` | Allows starting the button-linking table creation flow. |
| `tweaks.blackjack.removetable` | Allows starting the table removal flow. |
| `tweaks.admin.roulettescan` | Allows running the read-only Roulette board geometry diagnostic. |
| `tweaks.roulette.createboard` | Allows starting the Roulette board setup flow. |
| `tweaks.roulette.removeboard` | Allows starting the Roulette board removal flow. |
| `tweaks.roulette.setdiscord` | Allows choosing or clearing the Roulette board exposed to Discord betting. |
| `tweaks.roulette.forcespin` | Allows an admin's board-side control to force-close betting and spin immediately. |

---

## Configuration

The plugin generates a `config.yml` on first startup. Custom enchantments require a server-side data pack to register the entries.

On every startup, any key present in the bundled default `config.yml` but missing from your live file is automatically added back with its default value (an admin-modified value is never overwritten, and an explicit empty list you've saved stays empty — only a genuinely absent key is filled in).

A growing set of settings — spanning General, Protection, Player Admin, World Management, Teleport, Minigames, Economy, Block Log, Death Inventory, Enchantments, Item Admin, Xp Bottle, Discord, and Console Event Logging — is also editable at runtime without touching `config.yml` by hand, via `/tconfig`. Six have dedicated CLI forms (see the [Commands Reference](#commands-reference) table above); the rest use the generic `/tconfig <path> <value>` grammar. Run `/tconfig gui` in-game for a Dialog-based editor with the same categories and a main-menu **Logging** tab, or `/tconfig list [category]` to print every registered setting and its current value from the console or in chat. `/tconfig list logging` filters that output to the 20 logging categories; `/tconfig list discord` filters to the Discord settings.

### Discord announcements and stat channels

Discord integration is optional and requires DiscordSRV. Leave every Discord channel ID empty to
disable that surface without log noise or Discord API traffic. The announcement channel is shared
by lottery, Blackjack, and Roulette:

- Lottery draws post a yellow winner card.
- Paid Blackjack hands, inactivity forfeitures, and Roulette settlements are grouped into short
  `diff` code blocks. Blackjack pushes/free hands are omitted; Roulette includes every bettor,
  including a break-even result, and each line shows signed net change. Roulette blocks begin with
  the unprefixed result header `@@ Roulette — N Colour @@`; the remaining lines are kept with that
  header when a round spans multiple Discord messages.
- Roulette result messages are posted as **House**. `discord.webhook-name` and
  `discord.webhook-avatar-url` control casino announcements live; lottery winner cards keep the
  winning player's skin avatar.
- The House balance and lottery pot can be displayed in two separate voice-channel names. Names
  refresh at most once per five minutes and unchanged values are not renamed, respecting Discord's
  channel-rename rate limit. A lottery without a payable pot displays `Lottery Pot: waiting`.

| Setting | Default | Description |
|---|---:|---|
| `discord.channel-id` | `""` | Numeric Discord text-channel ID for lottery/casino messages. |
| `discord.webhook-name` | `House` | Display name used for casino and subjectless webhook messages; blank falls back to House. |
| `discord.webhook-avatar-url` | `""` | Optional public avatar URL for casino and subjectless webhook messages. |
| `discord.betting-channel-id` | `""` | Numeric Discord text-channel ID where the four native Roulette commands are accepted. |
| `discord.betting-enabled` | `true` | Master switch for `/bet`; read-only Roulette commands remain available. |
| `discord.house-channel-id` | `""` | Numeric Discord voice-channel ID for the House balance. |
| `discord.lottery-channel-id` | `""` | Numeric Discord voice-channel ID for the lottery pot. |
| `discord.group-window-seconds` | `2` | Settlement grouping window, bounded to 1–30 seconds. |
| `discord.stat-refresh-seconds` | `300` | Voice-stat refresh interval, bounded to 300–3600 seconds. |

Use quoted raw numeric IDs in `config.yml`; Discord snowflakes are too large for a safe YAML
number. The announcement channel must not be added to DiscordSRV's `Channels:` map, because that
map creates a two-way chat bridge. The bot needs **Manage Webhooks** on the announcement channel
and **Manage Channel** on each configured voice channel. Incorrect IDs or permissions produce one
startup warning after DiscordSRV is ready; empty IDs remain silent. `/tconfig` writes the same
settings immediately, and its generic STRING editor accepts an empty value to disable a channel.

### Console logging settings

The top-level `logging:` section contains one boolean for each supported event. All are `false` by
default. The switches are cached for low-overhead event checks, and a successful CLI or GUI write
updates that cache after the YAML round-trip has been confirmed. Manual edits to `config.yml` are
read on the next restart. Records are written only to the server console; they are not sent to
players or persisted as a separate audit file.

The world-key -> profile/tag/color mapping (`world-profiles`, `world-profile-fallback`, `world-profile-sort-order`) is a separate, fully admin-editable list rather than a single scalar/list setting — see [World Profiles](#world-profiles) and the `/tconfig world-profiles ...` rows in [Commands Reference](#commands-reference) for add/remove/edit.

Recently added via `/tconfig` (converted from previously-hardcoded values):

```yaml
protection:
  claim-cost:
    base: 10.0                   # first-chunk price; not retroactive to already-claimed regions
    decay-rate: 1.1               # per-chunk geometric taper; must be >= 1.0
    minimum-per-chunk: 1
  public-claim-worlds: []         # namespaced world keys where claim permission is waived
playeradmin:
  afk-auto-minutes: 10           # idle time before auto-AFK
  max-nick-length: 24            # new /nick calls only - doesn't retroactively re-check existing nicknames
teleport:
  tpa-timeout-seconds: 30
  sethome-disabled-worlds:       # /sethome refused in these worlds
    - "jass:resource"
    - "jass:resource_nether"
worldmanagement:
  blood-moon-chance-percent: 50.0
disabled-nether-portal-worlds:   # nether portals refused in these worlds
  - "jass:resource"
  - "jass:resource_nether"
minigames:
  blackjack:
    inactivity-timeout-minutes: 10   # mid-hand idle games are force-ended and the bet forfeited
    auto-clear-seconds: 30
  roulette:
    betting-window-seconds: 30
    big-win-multiplier: 8            # payout multiple that triggers the server-wide big-win announcement
    scan-radius: 16                  # baked into a board at registration; doesn't affect existing boards
  resource-hunt:
    default-reward-multiplier: 2.0   # restart required - read once at boot
```

```yaml
blocklog:
  retention-days: 30              # /logs entries older than this are pruned on chunk load
  max-entries-per-chest: 500      # oldest entries dropped once a chest's log exceeds this
deathinventory:
  retention-days: 30              # saved death-inventory snapshots older than this are purged
enchantments:
  spawner-pickup:
    drop-chance-percent: 20.0
    uses: 5                       # tool breaks after this many successful pickups
  egg-collector:
    uses: 5
  quality:
    chance-percent: 10.0          # per-enchant roll chance for a quality variant
    blood-moon-chance-percent: 50.0
  lumberjack:
    max-logs: 256                 # flood-fill area cap per tree/mushroom chopped
itemadmin:
  tool-protect:
    default-threshold: 100        # durability floor below which protected tools can't be used
    warn-cooldown-ms: 2000
  gui-copy:
    max-distance: 8               # /guicopy raycast distance, clamped 1-64
xpbottle:
  orbs-per-emerald: 1395          # restart required - baked into a boot-time brewing recipe
```

```yaml
# Maximum number of homes per player.
max-homes: 15

# Maximum number of chunks per player claim.
max_chunks: 200

# Drop chance percentage for Egg Collector (0.0 - 100.0).
egg-collector-drop-chance: 0.5

# Mobs that will NEVER drop a spawn egg.
egg-collector-disabled-mobs: []

# Mobs whose spawn eggs cannot be used on spawners.
spawner-egg-disabled-mobs: []

# Worlds where the end portal is disabled.
disabled-end-portal-worlds:
  - "jass:archive"

# Worlds where fly is enabled by default.
fly-worlds:
  - "jass:archive"
  - "jass:lobby"

# Namespaced keys of custom enchantments (must match the data pack).
telekinesis: "jass:test1"
smelter: "jass:test2"
lumberjack: "jass:test3"
gem-connoisseur: "jass:test4"
tunneller: "jass:test5"
spawner-pickup: "jass:test6"
egg-collector: "jass:test7"
replant: "jass:test8"
efficacy: "jass:test9"
```

---

## Server Administration

### Installation

1. Place the JAR in `plugins/`.
2. Ensure you are running **Paper 26.2** with **Java 25**.
3. Install the required **data pack** for custom enchantments.
4. Start the server, then update namespaced keys in `config.yml`.
5. Run `/setwarp spawn` to enable `/spawn`.
6. Run `/setwarp newspawn` to enable resource world login ejection.

### Data Pack Requirement

The plugin reads namespaced keys from `config.yml` and looks them up in Paper's enchantment registry. Quality variants are looked up directly under `jass:{tier}_{enchantName}`.

### Data Storage

All data is stored in YAML files under `plugins/Tweaks/`:

- `config.yml`: Plugin settings.
- `homes/<UUID>.yml`: Per-player home locations.
- `warps.yml`: Server warp locations.
- `inventories/<UUID>.yml`: Separated inventories, ender chests, and XP.
- `nick-removals.yml`: Pending nickname removals.
- `whack.yml`: Whack-an-Andrew configuration.
- `rewards/`: Reward templates.

### Building from Source

```bash
./gradlew build
```

Compiled JAR is in `build/libs/`. Run a dev server with:

```bash
./gradlew runServer
```
