package me.beeliebub.tweaks.tests.protection;

import me.beeliebub.tweaks.protection.FlagTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlagTargetTest {

    @Test
    void roleConstantsCarryNullGroupName() {
        assertEquals(FlagTarget.Type.DEFAULT, FlagTarget.DEFAULT.type());
        assertNull(FlagTarget.DEFAULT.groupName());
        assertEquals(FlagTarget.Type.OWNER, FlagTarget.OWNER.type());
        assertNull(FlagTarget.OWNER.groupName());
        assertEquals(FlagTarget.Type.MEMBER, FlagTarget.MEMBER.type());
        assertNull(FlagTarget.MEMBER.groupName());
    }

    @Test
    void groupFactoryLowercasesAndStoresName() {
        FlagTarget t = FlagTarget.group("Staff");
        assertEquals(FlagTarget.Type.GROUP, t.type());
        assertEquals("staff", t.groupName());
    }

    @Test
    void groupFactoryRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> FlagTarget.group(""));
        assertThrows(IllegalArgumentException.class, () -> FlagTarget.group("   "));
    }

    @Test
    void roleConstructorRejectsGroupName() {
        assertThrows(IllegalArgumentException.class,
                () -> new FlagTarget(FlagTarget.Type.OWNER, "staff"));
    }

    @Test
    void groupConstructorRequiresGroupName() {
        assertThrows(NullPointerException.class,
                () -> new FlagTarget(FlagTarget.Type.GROUP, null));
    }

    @Test
    void toKeyCanonicalForms() {
        assertEquals("default", FlagTarget.DEFAULT.toKey());
        assertEquals("owner", FlagTarget.OWNER.toKey());
        assertEquals("member", FlagTarget.MEMBER.toKey());
        assertEquals("group:staff", FlagTarget.group("staff").toKey());
    }

    @Test
    void fromKeyRoundTripsCanonicalForms() {
        assertEquals(FlagTarget.DEFAULT, FlagTarget.fromKey("default"));
        assertEquals(FlagTarget.OWNER, FlagTarget.fromKey("owner"));
        assertEquals(FlagTarget.MEMBER, FlagTarget.fromKey("member"));
        assertEquals(FlagTarget.group("staff"), FlagTarget.fromKey("group:staff"));
    }

    @Test
    void fromKeyIsCaseInsensitive() {
        assertEquals(FlagTarget.OWNER, FlagTarget.fromKey("OWNER"));
        assertEquals(FlagTarget.group("staff"), FlagTarget.fromKey("GROUP:Staff"));
    }

    @Test
    void fromKeyReturnsNullForUnknownOrBlankGroup() {
        assertNull(FlagTarget.fromKey("nonsense"));
        assertNull(FlagTarget.fromKey("group:"));
        assertNull(FlagTarget.fromKey(null));
    }

    @Test
    void parseCommandArgDefaultsWhenBlank() {
        assertEquals(FlagTarget.DEFAULT, FlagTarget.parseCommandArg(null));
        assertEquals(FlagTarget.DEFAULT, FlagTarget.parseCommandArg(""));
        assertEquals(FlagTarget.DEFAULT, FlagTarget.parseCommandArg("   "));
    }

    @Test
    void parseCommandArgRecognizesRoles() {
        assertEquals(FlagTarget.OWNER, FlagTarget.parseCommandArg("owner"));
        assertEquals(FlagTarget.OWNER, FlagTarget.parseCommandArg("OWNER"));
        assertEquals(FlagTarget.MEMBER, FlagTarget.parseCommandArg("Member"));
        assertEquals(FlagTarget.DEFAULT, FlagTarget.parseCommandArg("default"));
    }

    @Test
    void parseCommandArgTreatsAnyOtherTokenAsGroup() {
        FlagTarget t = FlagTarget.parseCommandArg("Trusted");
        assertEquals(FlagTarget.Type.GROUP, t.type());
        assertEquals("trusted", t.groupName());
    }

    @Test
    void equalsAndHashCodeMatchRecordSemantics() {
        FlagTarget a = FlagTarget.group("staff");
        FlagTarget b = FlagTarget.group("STAFF");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
