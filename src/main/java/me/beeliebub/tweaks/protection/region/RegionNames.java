package me.beeliebub.tweaks.protection.region;

import java.util.Locale;
import java.util.regex.Pattern;

public final class RegionNames {

    public static final Pattern VALID = Pattern.compile("^[a-z0-9_-]{1,32}$");

    private RegionNames() {}

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String id) {
        return id != null && VALID.matcher(id).matches();
    }

    public static boolean isReserved(String id) {
        return ProtectionManager.GLOBAL_REGION_ID.equals(id)
                || RegionWriter.ARCHIVE_DIR.equals(id)
                || "_legacy".equals(id);
    }
}
