package me.beeliebub.tweaks.tests.skyblock.tracking;

import me.beeliebub.tweaks.skyblock.tracking.TrackCategory;
import me.beeliebub.tweaks.skyblock.tracking.TrackIdentifierDomain;
import me.beeliebub.tweaks.skyblock.tracking.TrackingActionListener;
import me.beeliebub.tweaks.skyblock.tracking.TrackingGatherListener;
import me.beeliebub.tweaks.skyblock.tracking.TrackingInteractionListener;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TrackCategoryCoverageTest {

    @Test
    void everyCategoryHasAnIdentifierDomainAndListener() {
        Set<TrackCategory> recorded = EnumSet.noneOf(TrackCategory.class);
        recorded.addAll(TrackingGatherListener.recordedCategories());
        recorded.addAll(TrackingActionListener.recordedCategories());
        recorded.addAll(TrackingInteractionListener.recordedCategories());

        assertEquals(EnumSet.allOf(TrackCategory.class), recorded);
        for (TrackCategory category : TrackCategory.values()) {
            TrackIdentifierDomain domain = category.identifierDomain();
            assertNotNull(domain, category.name());
        }
    }
}
