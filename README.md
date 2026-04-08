# Tweaks

A lightweight Minecraft Spigot/Paper plugin providing essential utilities like homes, warps, and world-specific tweaks.

## Features

- **Home System**: Players can set multiple personal homes and teleport to them at any time.
- **Warp System**: Global warps that can be created and accessed by all players.
- **Spawn Command**: Quick teleportation to the world's default spawn point.
- **Async Data Storage**: All home and warp data is saved asynchronously to prevent server lag.
- **Custom Tweaks**: Includes specialized logic like preventing end portal usage in specific worlds (e.g., "Archive").

## Commands

| Command | Description |
|---------|-------------|
| `/home [name]` | Teleport to a saved home. |
| `/sethome <name>`| Set a home at your current location. |
| `/delhome <name>`| Delete a saved home. |
| `/homes` | List all your saved homes. |
| `/warp [name]` | Teleport to a global warp. |
| `/setwarp <name>`| Set a global warp. |
| `/delwarp <name>`| Delete a global warp. |
| `/warps` | List all available warps. |
| `/spawn` | Teleport to the world spawn. |

## Configuration

The `config.yml` allows you to customize the plugin behavior:

```yaml
# The maximum number of homes a player can set.
max-homes: 15
```

## Storage

- **Homes**: Saved per-player in `plugins/Tweaks/homes/<UUID>.yml`.
- **Warps**: Saved globally in `plugins/Tweaks/warps.yml`.

## Requirements

- **Minecraft Version**: 1.20.x or newer (Supports Paper/Spigot API).
- **Java**: 21 or newer.

## Building

This project uses Gradle. To build the plugin, run:

```bash
./gradlew build
```

The compiled JAR will be located in `build/libs/`.
