package me.beeliebub.tweaks.skyblock.tracking;

/** The stable challenge-counter categories. */
public enum TrackCategory {
    COLLECT(TrackIdentifierDomain.MATERIAL),
    KILL(TrackIdentifierDomain.ENTITY_TYPE),
    SMELT(TrackIdentifierDomain.MATERIAL),
    ENCHANT(TrackIdentifierDomain.MATERIAL),
    SHEAR(TrackIdentifierDomain.ENTITY_TYPE),
    BREED(TrackIdentifierDomain.ENTITY_TYPE),
    CRAFT(TrackIdentifierDomain.MATERIAL),
    BARTER(TrackIdentifierDomain.MATERIAL),
    PLACE(TrackIdentifierDomain.MATERIAL),
    FISH(TrackIdentifierDomain.MATERIAL),
    BREW(TrackIdentifierDomain.MATERIAL),
    TRADE(TrackIdentifierDomain.MATERIAL),
    TAME(TrackIdentifierDomain.ENTITY_TYPE);

    private final TrackIdentifierDomain identifierDomain;

    TrackCategory(TrackIdentifierDomain identifierDomain) {
        this.identifierDomain = identifierDomain;
    }

    public TrackIdentifierDomain identifierDomain() {
        return identifierDomain;
    }
}
