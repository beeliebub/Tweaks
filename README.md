# Tweaks

A lightweight Minecraft Paper plugin providing essential utilities like homes, warps, TPA, nicknames, and world-specific tweaks.

## Features

- **Home System**: Players can set multiple personal homes and teleport to them at any time.
- **Warp System**: Global warps that can be created and accessed by all players.
- **TPA System**: Request to teleport to other players or request them to teleport to you, with a 30-second timeout.
- **Back Command**: Return to your previous location before your last teleport.
- **Spawn Command**: Quick teleportation to the world's default spawn point.
- **Night Vision**: A command to toggle night vision.
- **Nickname System**: Set custom display names with color code and hex color support, persisted across logins.
- **Tab Manager**: Organizes players into color-coded scoreboard teams based on their current world (Lobby, Survival, Archive).
- **Async Data Storage**: All home, warp, and inventory data is saved asynchronously to prevent server lag.
- **Custom Tweaks**:
    - **Farmland Protection**: Prevents players and mobs from trampling farmland.
    - **End Portal Control**: Disable end portal usage in specific worlds.
    - **Inventory Separation**: Separates player inventories between different worlds or groups of worlds (Lobby, Archive, Standard).
- **Configuration**: In-game command to update configuration values.

## Commands

| Command | Description | Permission             |
|---|---|------------------------|
| `/home [name]` | Teleport to a saved home. | None                   |
| `/home <player> <name>` | Teleport to another player's home. | `tweaks.admin.home`    |
| `/sethome [name]` | Set a home at your current location. | None                   |
| `/sethome <player> <name>` | Set a home for another player. | `tweaks.admin.sethome` |
| `/delhome [name]` | Delete a saved home. | None                   |
| `/delhome <player> <name>` | Delete another player's home. | `tweaks.admin.delhome` |
| `/homes` | List all your saved homes. | None                   |
| `/homes <player>` | List another player's homes. | `tweaks.admin.homes`   |
| `/warp <name>` | Teleport to a global warp. | None                   |
| `/setwarp <name>` | Set a global warp. | `tweaks.admin.setwarp` |
| `/delwarp <name>` | Delete a global warp. | `tweaks.admin.delwarp` |
| `/warps` | List all available warps. | None                   |
| `/tpa <player>` | Request to teleport to a player. | None                   |
| `/tpahere <player>` | Request a player to teleport to you. | None                   |
| `/tpaccept` | Accept an incoming TPA request. | None                   |
| `/tpdeny` | Deny an incoming TPA request. | None                   |
| `/back` | Return to your previous location. | None                   |
| `/spawn` | Teleport to the spawn. | None                   |
| `/nv` | Toggle night vision. | None                   |
| `/nick <nickname>` | Set a custom display name with color support. | None                   |
| `/nick off` | Remove your nickname. | None                   |
| `/nick off <player>` | Remove another player's nickname. | `tweaks.admin.nick`    |
| `/config <key> <value>` | Update a configuration value. | `tweaks.admin.config`  |

## Configuration

The `config.yml` allows you to customize the plugin behavior:

```yaml
# The maximum number of homes a player can set.
max-homes: 15

# Worlds where the end portal is disabled.
disabled-end-portal-worlds:
  - jass:archive
```

## Storage

- **Homes**: Saved per-player in `plugins/Tweaks/homes/<UUID>.yml`.
- **Warps**: Saved globally in `plugins/Tweaks/warps.yml`.
- **Inventories**: Saved per-player in `plugins/Tweaks/inventories/<UUID>.yml`.
- **Nicknames**: Stored per-player via PersistentDataContainer. Pending removals for offline players are saved in `plugins/Tweaks/nick-removals.yml`.
- **Back Locations**: Stored per-player via PersistentDataContainer.

## Requirements

- **Minecraft Version**: 26.1.1 (Paper API 26.1.1).
- **Java**: 25 or newer.

## Building

This project uses Gradle. To build the plugin, run:

```bash
./gradlew build
```

The compiled JAR will be located in `build/libs/`.
