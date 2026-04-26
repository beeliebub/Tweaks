# Tweaks

A Paper plugin that adds custom enchantments, separated world profiles, teleportation utilities, nicknames, flight, and minigames to a multi-world Minecraft server.

**Requires Paper 26.1.1 and Java 25.**

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
- [Player Features](#player-features)
  - [Nicknames](#nicknames)
  - [Flight](#flight)
  - [Night Vision](#night-vision)
  - [Tab List](#tab-list)
- [World Protections](#world-protections)
- [Minigames](#minigames)
  - [Whack an Andrew](#whack-an-andrew)
  - [Rewards](#rewards)
- [Commands Reference](#commands-reference)
- [Permissions Reference](#permissions-reference)
- [Configuration](#configuration)
- [Server Administration](#server-administration)
  - [Installation](#installation)
  - [Data Pack Requirement](#data-pack-requirement)
  - [Data Storage](#data-storage)
  - [Building from Source](#building-from-source)

---

## World Profiles

The server is divided into four **profiles**. Each profile has its own separate inventory, ender chest, and experience (levels + progress bar). When you travel between worlds that belong to different profiles, your items, ender chest contents, and XP are automatically saved and swapped. You'll see a yellow message confirming the switch:

> *Inventory profile switched to: standard*

The four profiles are:

| Profile | Worlds |
|---|---|
| **Lobby** | The lobby world |
| **Standard** | The overworld, nether, and end (survival worlds) |
| **Archive** | The archive world |
| **Pi** | The pi world |

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

### Back

| Command | What it does |
|---|---|
| `/back` | Teleports you to your location before your last teleport. |

This works after any teleport (TPA, home, warp, spawn, etc.). Your back location is saved across logins and server restarts.

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

### Tunneller

Breaks a **3x3 area** of blocks perpendicular to the face you mine. Mine the side of a wall and it carves out a 3x3 tunnel. Mine downward and it clears a 3x3 floor. The center block is the one you break normally; the surrounding 8 blocks are broken by the enchantment.

- Blocks that are air, liquid, or unbreakable (like bedrock) are skipped.
- Your tool takes **durability damage for each extra block** broken by the enchantment. The Unbreaking enchantment reduces this damage normally.
- Works with Smelter, Gem Connoisseur, and Telekinesis — all 8 surrounding blocks benefit from whatever combination of enchantments is on your tool.

### Lumberjack

Chops down **entire trees** and **large mushrooms** at once. When you break a log or mushroom block, the enchantment finds all connected blocks (up to 256) and breaks them all in one swing.

- **Trees**: only activates on actual trees — there must be at least one leaf block adjacent to the connected logs. This prevents it from tearing apart log-built structures.
- **Large mushrooms**: works on red and brown giant mushrooms. The connected set must include both stem and cap blocks, so isolated placed mushroom blocks are left alone.
- Your tool takes **durability damage for each extra block** broken. Unbreaking reduces this normally.
- If your tool doesn't have enough durability remaining to chop the entire tree or mushroom, the break is cancelled and you'll see a red warning message.
- Works with Telekinesis to send all drops to your inventory.

### Replant

Automatically replants crops and saplings after harvesting.

**Crops**: When you break a fully-grown crop (wheat, carrots, potatoes, beetroots, nether wart), the enchantment consumes one seed from the drops and replants the crop at age 0. If the crop is **not fully grown**, the break is cancelled entirely — this prevents accidental harvesting of immature crops.

**Trees**: When combined with Lumberjack, saplings are automatically planted at the base of felled trees wherever a log was sitting on valid soil (grass, dirt, podzol, moss, mud, etc.).

### Efficacy

Extends shovel, hoe, and axe right-click actions to a **3x3 area**:

| Tool | Action | 3x3 Effect |
|---|---|---|
| Shovel | Right-click grass/dirt | Creates dirt paths in a 3x3 area |
| Hoe | Right-click grass/dirt | Tills farmland in a 3x3 area |
| Axe | Right-click logs/wood | Strips logs/wood in a 3x3 area |

Each surrounding block affected costs **1 durability**. Only affects blocks of the appropriate type — the shovel won't path stone, the hoe won't till stone, etc. Blocks must also have air above them (for shovels and hoes) to be affected.

### Spawner Pickup

Gives a **20% chance** to drop the spawner block when you mine a mob spawner. However, this comes with a cost: the tool tracks how many spawners it has successfully dropped. After **5 successful spawner pickups**, the tool breaks completely (regardless of remaining durability). The number of uses remaining is shown in the item's lore.

### Egg Collector

Gives a configurable chance (default: **0.5%**) to drop a **spawn egg** when you kill a mob. Like Spawner Pickup, the tool tracks successful egg drops and **breaks after 5**. Remaining uses are shown in the item's lore.

### Enchantment Interactions

Many enchantments stack together. Here are the notable combinations:

| Combination | Effect |
|---|---|
| Tunneller + Telekinesis | All 3x3 drops go straight to your inventory. |
| Tunneller + Smelter | Raw ores from all 9 blocks are auto-smelted. |
| Tunneller + Gem Connoisseur | Bonus gem drops roll for all 9 blocks. |
| Tunneller + Smelter + Gem Connoisseur + Telekinesis | The full package: 3x3 mining with smelting, gem drops, and inventory routing. |
| Lumberjack + Telekinesis | All log drops from the tree go to your inventory. |
| Lumberjack + Replant | Saplings auto-plant at tree bases after felling. |
| Replant + Telekinesis | Crop drops (minus the replanting seed) go to your inventory. |
| Smelter + Telekinesis | Smelted ingots go directly to your inventory. |
| Gem Connoisseur + Telekinesis | Bonus gem drops go to your inventory. |

---

## Player Features

### Nicknames

Set a custom display name with color support using `&` color codes and hex colors.

| Command | What it does |
|---|---|
| `/nick <nickname>` | Sets your display name. Supports `&` color codes and `&#RRGGBB` hex colors. |
| `/nick off` | Removes your nickname and restores your real name. |

Examples:
- `/nick &cRedName` — sets your name to red.
- `/nick &aGreen &bAqua` — multi-colored name.
- `/nick &#FF5555Custom` — hex color.

Your nickname persists across logins and server restarts. Requires the `tweaks.nick` permission.

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

### Tab List

Players in the tab list are automatically sorted and labeled by their current world profile. Each profile has a colored prefix:

| Profile | Tag |
|---|---|
| Lobby | **[Lobby]** (aqua) |
| Standard | **[Survival]** (green) |
| Archive | **[Archive]** (gold) |
| Pi | **[Pi]** (light purple) |

Players in the lobby appear at the top of the tab list, followed by survival, archive, and pi. Tags update automatically when you change worlds.

---

## World Protections

These protections are always active and require no commands or configuration:

| Protection | What it does |
|---|---|
| **Farmland Anti-Trample** | Players and mobs cannot trample farmland by walking or jumping on it. |
| **Creeper Block Protection** | Creeper explosions still deal damage but no longer destroy blocks. |
| **Enderman Grief Protection** | Endermen cannot pick up or place blocks. |
| **End Portal Control** | End portals are disabled in configured worlds (archive by default). Players who try receive a red message. |

---

## Minigames

### Whack an Andrew

A "Whack-a-Mole" style minigame where armor stands pop up on designated blocks in an arena. Players compete to hit as many as possible, and the top 3 scorers receive rewards.

This is entirely admin-managed — see the [admin commands](#admin-commands) section for setup instructions.

### Rewards

A system for creating and distributing item rewards. Rewards are created by admins using a chest GUI and can be awarded to players by the minigame system.

| Command | What it does |
|---|---|
| `/reward claim` | Claims all pending rewards. Items are added to your inventory; overflow drops at your feet. |

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
| `/nv` | Toggle night vision. |
| `/nick <nickname>` | Set your display name with color codes. |
| `/nick off` | Remove your nickname. |
| `/reward claim` | Claim pending minigame rewards. |

### Admin Commands

| Command | Permission | Description |
|---|---|---|
| `/setwarp <name>` | `tweaks.admin.setwarp` | Create a server warp. |
| `/delwarp <name>` | `tweaks.admin.delwarp` | Delete a server warp. |
| `/home <player> <name>` | `tweaks.admin.home` | Teleport to another player's home. |
| `/sethome <player> <name>` | `tweaks.admin.sethome` | Set a home for another player. |
| `/delhome <player> <name>` | `tweaks.admin.delhome` | Delete another player's home. |
| `/homes <player>` | `tweaks.admin.homes` | List another player's homes. |
| `/nick off <player>` | `tweaks.admin.nick` | Remove another player's nickname (works on offline players). |
| `/config <key> <value>` | `tweaks.admin.config` | Update a config value at runtime. |
| `/reward create <name>` | `tweaks.admin.reward` | Create a new reward template. |
| `/reward edit <name>` | `tweaks.admin.reward` | Open the reward editor GUI. |
| `/whack arena` | `tweaks.admin.whack` | Start Whack-an-Andrew arena setup. |
| `/whack corner1` | `tweaks.admin.whack` | Set arena corner 1 (look at target block). |
| `/whack corner2` | `tweaks.admin.whack` | Set arena corner 2 (look at target block). |
| `/whack setblocks <material...>` | `tweaks.admin.whack` | Scan arena for spawn point blocks. |
| `/whack start` | `tweaks.admin.whack` | Start a Whack-an-Andrew game. |
| `/whack pause` | `tweaks.admin.whack` | Pause the current game. |
| `/whack stop` | `tweaks.admin.whack` | Stop the current game. |
| `/whack setreward <1\|2\|3> <name>` | `tweaks.admin.whack` | Set the reward for 1st/2nd/3rd place. |
| `/whack reload` | `tweaks.admin.whack` | Reload the whack.yml config. |

### Runtime Config Keys

These keys can be changed at runtime with `/config <key> <value>`:

| Key | Type | Description |
|---|---|---|
| `max_homes` | Integer | Maximum number of homes per player. |
| `egg_collector_drop_chance` | Decimal (0.0 - 100.0) | Egg Collector drop chance percentage. |

---

## Permissions Reference

| Permission | What it grants |
|---|---|
| `tweaks.nick` | Use `/nick` to set your own nickname. |
| `tweaks.bypass.homes` | Bypass the max homes limit. |
| `tweaks.admin.home` | Teleport to other players' homes. |
| `tweaks.admin.sethome` | Set homes for other players. |
| `tweaks.admin.delhome` | Delete other players' homes. |
| `tweaks.admin.homes` | View other players' home lists. |
| `tweaks.admin.setwarp` | Create server warps. |
| `tweaks.admin.delwarp` | Delete server warps. |
| `tweaks.admin.nick` | Remove other players' nicknames. |
| `tweaks.admin.config` | Use the `/config` command. |
| `tweaks.admin.reward` | Create and edit minigame rewards. |
| `tweaks.admin.whack` | Full access to Whack-an-Andrew admin commands. |

---

## Configuration

The plugin generates a `config.yml` on first startup. All enchantments require a server-side data pack to register the actual enchantment entries — the config simply maps each enchantment to its namespaced key in the data pack.

```yaml
# The maximum number of homes a player can set.
max-homes: 15

# Drop chance percentage for Egg Collector enchantment (0.0 - 100.0).
egg-collector-drop-chance: 0.5

# Worlds where the end portal is disabled.
disabled-end-portal-worlds:
  - "jass:archive"

# Worlds where fly is enabled by default.
fly-worlds:
  - "jass:archive"
  - "jass:lobby"

# Flight permission advancement (namespaced key).
fly-advancement: "jass:test"

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

# Drop rates for Gem Connoisseur (1-in-N chance per block mined, by enchant level and block type).
gem-connoisseur-rates:
  1:
    stone:
      emerald: 1250
      diamond: 1094
      # ... etc
  2:
    # Better rates at level 2 ...
  3:
    # Best rates at level 3 ...
```

---

## Server Administration

### Installation

1. Place the compiled JAR in your server's `plugins/` folder.
2. Ensure your server is running **Paper 26.1.1** with **Java 25**.
3. Install the accompanying **data pack** that registers the custom enchantment entries. Without the data pack, all custom enchantments will gracefully disable themselves on startup (you'll see warnings in the console).
4. Start the server. The plugin will generate its `config.yml` and data directories.
5. Update the enchantment namespaced keys in `config.yml` to match your data pack's enchantment keys.
6. Set up a spawn point with `/setwarp spawn` so the `/spawn` command works.

### Data Pack Requirement

The plugin does **not** register custom enchantments itself. It reads namespaced keys from `config.yml` and looks them up in Paper's enchantment registry. You need a server-side data pack that defines the enchantment entries. If a key is missing, blank, or points to a non-existent enchantment, that specific enchantment feature will disable itself without affecting the rest of the plugin.

### Data Storage

All data is stored in flat YAML files under the plugin's data folder:

| Path | Contents |
|---|---|
| `plugins/Tweaks/config.yml` | Plugin configuration. |
| `plugins/Tweaks/homes/<UUID>.yml` | Per-player home locations. |
| `plugins/Tweaks/warps.yml` | Server warp locations. |
| `plugins/Tweaks/inventories/<UUID>.yml` | Per-player separated inventories, ender chests, and experience per profile. |
| `plugins/Tweaks/nick-removals.yml` | Pending offline nickname removals. |
| `plugins/Tweaks/whack.yml` | Whack-an-Andrew arena configuration. |
| `plugins/Tweaks/rewards/` | Reward template data. |

All file I/O is performed asynchronously to prevent lag. Home and warp data is loaded into memory on startup. Inventory data is loaded per-player on join and unloaded on quit.

### Building from Source

```bash
./gradlew build
```

The compiled JAR is output to `build/libs/`. To run a development server with the plugin:

```bash
./gradlew runServer
```

This starts a Paper 26.1.1 server with 2 GB of heap and the plugin automatically loaded.