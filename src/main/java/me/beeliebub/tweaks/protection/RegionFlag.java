package me.beeliebub.tweaks.protection;

// Per-region toggles. Boolean flags (everything but the four ALLOW_/DENY_
// variants below) follow the targeted-rule resolution in Region#resolveFlag:
// the rule may be permissive or restrictive for the DEFAULT audience, the
// region OWNER, MEMBER, or specific permission groups. A flag with no rule
// at all falls back to the legacy default (members allowed, non-members
// blocked).
//
// Routing of Bukkit events to specific flags happens in ProtectionListener
// (e.g. PlayerInteractEvent splits CONTAINER_ACCESS vs INTERACT vs REDSTONE
// by inspecting the clicked block type).
//
// Material-list flags (the ALLOW_/DENY_ pair for BLOCK_BREAK/BLOCK_PLACE)
// don't carry a boolean — instead they hold a Set<Material> stored in
// Region#materialFlags. They override the corresponding base flag for any
// listed material:
//   * DENY_BLOCK_BREAK contains M -> breaking M is denied (highest priority)
//   * ALLOW_BLOCK_BREAK contains M -> breaking M is allowed
//   * Otherwise the BLOCK_BREAK boolean rule decides
// Identical structure for BLOCK_PLACE. DENY wins over ALLOW if a material is
// listed in both — the safer choice for accidental config conflicts.
//
// MOB_SPAWNING / INVINCIBILITY:
//   * MOB_SPAWNING (boolean) — region admins can set false to suppress
//     vanilla creature spawning in a region.
//   * INVINCIBILITY (boolean) — when true for a player's audience, the
//     protection listener cancels EntityDamageEvent and FoodLevelChangeEvent
//     for that player.
//   * ALLOW_MOB_SPAWN / DENY_MOB_SPAWN — entity-type lists meant to filter
//     CreatureSpawnEvent before falling back to MOB_SPAWNING. Storage for
//     EntityType lists lives in Region (parallel to the Material lists for
//     blocks); the listener consults those lists when present.
public enum RegionFlag {
    BLOCK_BREAK,
    BLOCK_PLACE,
    CONTAINER_ACCESS,
    INTERACT,
    REDSTONE,
    EXPLOSION,
    PVP,
    MOB_GRIEFING,
    MOB_SPAWNING,
    INVINCIBILITY,
    ALLOW_BLOCK_BREAK,
    DENY_BLOCK_BREAK,
    ALLOW_BLOCK_PLACE,
    DENY_BLOCK_PLACE,
    ALLOW_MOB_SPAWN,
    DENY_MOB_SPAWN;

    public boolean isMaterialFlag() {
        return this == ALLOW_BLOCK_BREAK
                || this == DENY_BLOCK_BREAK
                || this == ALLOW_BLOCK_PLACE
                || this == DENY_BLOCK_PLACE;
    }

    public boolean isEntityFlag() {
        return this == ALLOW_MOB_SPAWN || this == DENY_MOB_SPAWN;
    }
}
