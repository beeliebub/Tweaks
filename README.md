# Tweaks

A lightweight Minecraft Spigot/Paper plugin providing essential utilities like homes, warps, and world-specific tweaks.

## Features

- **Home System**: Players can set multiple personal homes and teleport to them at any time.
- **Warp System**: Global warps that can be created and accessed by all players.
- **Spawn Command**: Quick teleportation to the world's default spawn point.
- **Night Vision**: A command to toggle night vision.
- **Async Data Storage**: All home and warp data is saved asynchronously to prevent server lag.
- **Custom Tweaks**:
    - **Farmland Protection**: Prevents players and mobs from trampling farmland.
    - **End Portal Control**: Disable end portal usage in specific worlds.
    - **Inventory Separation**: Separates player inventories between different worlds or groups of worlds.
- **Configuration**: In-game command to update configuration values.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/home [name]` | Teleport to a saved home. | `tweaks.home` |
| `/home <player> <name>` | Teleport to another player's home. | `tweaks.admin.home` |
| `/sethome [name]` | Set a home at your current location. | `tweaks.sethome` |
| `/sethome <player> <name>` | Set a home for another player. | `tweaks.admin.sethome` |
| `/delhome [name]` | Delete a saved home. | `tweaks.delhome` |
| `/delhome <player> <name>` | Delete another player's home. | `tweaks.admin.delhome` |
| `/homes` | List all your saved homes. | `tweaks.homes` |
| `/homes <player>` | List another player's homes. | `tweaks.admin.homes` |
| `/warp <name>` | Teleport to a global warp. | `tweaks.warp` |
| `/setwarp <name>` | Set a global warp. | `tweaks.admin.setwarp` |
| `/delwarp <name>` | Delete a global warp. | `tweaks.admin.delwarp` |
| `/warps` | List all available warps. | `tweaks.warps` |
| `/spawn` | Teleport to the spawn. | `tweaks.spawn` |
| `/nv` | Toggle night vision. | `tweaks.nv` |
| `/config <key> <value>` | Update a configuration value. | `tweaks.admin.config` |

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

## Requirements

- **Minecraft Version**: 1.19.4 (Paper API).
- **Java**: 17 or newer.

## Building

This project uses Gradle. To build the plugin, run:

```bash
./gradlew build
```

The compiled JAR will be located in `build/libs/`.
