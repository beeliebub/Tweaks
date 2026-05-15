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
public enum RegionFlag {
    BLOCK_BREAK,
    BLOCK_PLACE,
    CONTAINER_ACCESS,
    INTERACT,
    REDSTONE,
    EXPLOSION,
    PVP,
    MOB_GRIEFING,
    ALLOW_BLOCK_BREAK,
    DENY_BLOCK_BREAK,
    ALLOW_BLOCK_PLACE,
    DENY_BLOCK_PLACE;

    public boolean isMaterialFlag() {
        return this == ALLOW_BLOCK_BREAK
                || this == DENY_BLOCK_BREAK
                || this == ALLOW_BLOCK_PLACE
                || this == DENY_BLOCK_PLACE;
    }
}
