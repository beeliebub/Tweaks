package me.beeliebub.tweaks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

// Parses '&'-prefixed legacy color codes and '&#rrggbb' hex into Adventure components.
// Sets ITALIC=false on the root so vanilla item-name/lore italic styling is suppressed by default.
public final class ColorUtil {

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .build();

    private ColorUtil() {
    }

    public static Component parse(String input) {
        return SERIALIZER.deserialize(input).decoration(TextDecoration.ITALIC, false);
    }

    // Help System Gradients
    public static final String HELP_GRAD_TELEPORTATION = "#FF9A00:#FF9000:#FF8600:#FF7C00:#FF7100:#FF6700:#FF5D00:#006FFF:#0079FF:#0084FF:#008EFF:#0098FF:#00A2FF";
    public static final String HELP_GRAD_CUSTOM_ENCHANTS = "#E43A96:#D2449E:#C04FA5:#AF59AD:#9D64B5:#8B6EBC:#6883CC:#568ED3:#4498DB:#4D92DC:#578CDE:#6086DF:#6980E0:#737BE2:#7C75E3:#856FE4:#8F69E6:#9863E7";
    public static final String HELP_GRAD_QUALITY_ENCHANTS = "#2600B0:#2F06B3:#380CB6:#4112BA:#4A18BD:#531EC0:#5C24C3:#6E30CA:#7736CD:#803CD0:#7635CC:#6C2FC9:#6228C5:#5821C2:#4E1BBE:#4414BB:#3A0DB7:#3007B4:#2600B0";
    public static final String HELP_GRAD_PLAYER_FEATURES = "#00FFCC:#0DFFC3:#19FFBB:#26FFB2:#33FFAA:#40FFA1:#60FF8D:#73FF80:#80FF77:#8CFF6F:#99FF66:#A6FF5E:#B2FF55:#BFFF4D";
    public static final String HELP_GRAD_MINIGAMES = "#FF4B4B:#FF8B2D:#FFD031:#DCFF39:#A5FF3F:#6DFF45:#3BFFC7:#58D0FF:#5489FF";

    public static final String HELP_GRAD_HOMES = "#FF9A00:#FF7C00:#FF5D00:#0084FF:#00A2FF";
    public static final String HELP_GRAD_WARPS = "#FF9A00:#FF7C00:#FF5D00:#0084FF:#00A2FF";
    public static final String HELP_GRAD_SPAWN = "#FF9A00:#FF7C00:#FF5D00:#0084FF:#00A2FF";
    public static final String HELP_GRAD_TPA = "#FF9A00:#FF5D00:#00A2FF";
    public static final String HELP_GRAD_BACK = "#FF9A00:#FF5D00:#0065FF:#00A2FF";

    public static final String HELP_GRAD_TELEKINESIS = "#E43A96:#C44DA4:#A460B2:#8472BF:#6485CD:#4498DB:#558DDD:#6683E0:#7678E2:#876EE5:#9863E7";
    public static final String HELP_GRAD_GEM_CONNOISSEUR = "#E43A96:#CD47A0:#B655AA:#8970BD:#727DC7:#5B8BD1:#4498DB:#5090DD:#5C89DE:#6881E0:#747AE2:#8072E4:#8C6BE5:#9863E7";
    public static final String HELP_GRAD_TUNNELLER = "#E43A96:#BC52A7:#9469B9:#6C81CA:#4498DB:#598BDE:#6E7EE1:#8370E4:#9863E7";
    public static final String HELP_GRAD_LUMBERJACK = "#E43A96:#C44DA4:#A460B2:#8472BF:#6485CD:#4498DB:#598BDE:#6E7EE1:#8370E4:#9863E7";
    public static final String HELP_GRAD_SMELTER = "#E43A96:#AF59AD:#7979C4:#4498DB:#6086DF:#7C75E3:#9863E7";
    public static final String HELP_GRAD_REPLANT = "#E43A96:#AF59AD:#7979C4:#4498DB:#6086DF:#7C75E3:#9863E7";
    public static final String HELP_GRAD_SPAWNER_PICKUP = "#E43A96:#CD47A0:#B655AA:#9F62B4:#8970BD:#727DC7:#5B8BD1:#528FDD:#6086DF:#6E7EE1:#7C75E3:#8A6CE5:#9863E7";
    public static final String HELP_GRAD_EGG_COLLECTOR = "#E43A96:#C94AA2:#AF59AD:#7979C4:#5F88D0:#4498DB:#528FDD:#6086DF:#6E7EE1:#7C75E3:#8A6CE5:#9863E7";
    public static final String HELP_GRAD_DISENCHANTING = "#E43A96:#D4439D:#C44DA4:#B456AB:#A460B2:#9469B9:#8472BF:#747CC6:#6485CD:#548FD4:#4498DB:#4D92DC:#578CDE:#6980E0:#737BE2:#7C75E3:#856FE4:#8F69E6:#9863E7";

    public static final String HELP_GRAD_NICKNAMES = "#00FFCC:#13FFBF:#26FFB2:#4DFF99:#60FF8D:#73FF80:#99FF66:#ACFF5A:#BFFF4D";
    public static final String HELP_GRAD_FLIGHT = "#00FFCC:#26FFB2:#4DFF99:#73FF80:#99FF66:#BFFF4D";
    public static final String HELP_GRAD_ITEM_FILTER = "#00FFCC:#13FFBF:#26FFB2:#3AFFA6:#60FF8D:#73FF80:#86FF73:#99FF66:#ACFF5A:#BFFF4D";
    public static final String HELP_GRAD_AFK = "#26FFB2:#4DFF99:#99FF66";
    public static final String HELP_GRAD_TAB_MENU = "#00FFCC:#26FFB2:#3AFFA6:#73FF80:#86FF73:#99FF66:#BFFF4D";
    public static final String HELP_GRAD_WORLD_PROFILES = "#00FFCC:#0DFFC3:#19FFBB:#26FFB2:#3AFFA6:#5AFF91:#66FF88:#73FF80:#86FF73:#99FF66:#A6FF5E:#B2FF55:#BFFF4D";
    public static final String HELP_GRAD_XP_STORAGE = "#00FFCC:#13FFBF:#3AFFA6:#4DFF99:#73FF80:#86FF73:#99FF66:#ACFF5A:#BFFF4D";
    public static final String HELP_GRAD_TOOL_PROTECT = "#00FFCC:#13FFBF:#26FFB2:#3AFFA6:#5AFF91:#66FF88:#73FF80:#86FF73:#99FF66:#ACFF5A:#BFFF4D";
    public static final String HELP_GRAD_DISPLAY_CHEST = "#00FFCC:#13FFBF:#26FFB2:#33FFAA:#40FFA1:#4DFF99:#60FF8D:#80FF77:#8CFF6F:#99FF66:#ACFF5A:#BFFF4D";
    public static final String HELP_GRAD_ITEM_TOOLS = "#FEBA17:#FEC02D:#FFC63D:#FFCC49:#FFD85F:#FFDE69:#FFE473:#FFEA7C:#FFF085";
    public static final String HELP_GRAD_CONDENSE = "#00FFCC:#13FFBF:#26FFB2:#3AFFA6:#4DFF99:#60FF8D:#73FF80:#86FF73:#99FF66:#ACFF5A:#BFFF4D";
    public static final String HELP_GRAD_BLOCK_LOG = "#FEBA17:#FFC12F:#FFC840:#FFCE4E:#FFD55A:#FFE371:#FFE97B:#FFF085";

    public static final String HELP_GRAD_QUALITY_TIERS = "#2600B0:#350AB5:#4414BB:#531EC0:#6228C5:#7132CB:#803CD0:#6228C5:#531EC0:#4414BB:#350AB5:#2600B0";
    public static final String HELP_GRAD_BLOOD_MOON = "#950502:#850502:#760402:#660403:#560403:#370303";
    public static final String HELP_GRAD_SILK_QUALITY = "#2600B0:#380CB6:#4A18BD:#5C24C3:#803CD0:#6A2DC8:#531EC0:#3D0FB8:#2600B0";

    public static final String HELP_GRAD_RESOURCE_HUNT = "#FF4B4B:#FF6B3C:#FF8B2D:#FFD031:#EEE835:#DCFF39:#A5FF3F:#6DFF45:#3BFFC7:#58D0FF:#56ADFF:#5489FF";
    public static final String HELP_GRAD_REWARDS = "#FF4B4B:#FF8B2D:#FFD031:#DCFF39:#3BFFC7:#58D0FF:#5489FF";
    public static final String HELP_GRAD_WHACK = "#FF4B4B:#FF6B3C:#FF8B2D:#FFAE2F:#FFD031:#EEE835:#DCFF39:#A5FF3F:#6DFF45:#54FF86:#3BFFC7:#4AE8E3:#58D0FF:#56ADFF:#5489FF";

    // Permissions help category + /perms GUI titles. Gold→amber palette is distinct
    // from the other help categories and visually reads as an admin domain.
    public static final String HELP_GRAD_PERMISSIONS = "#FFE066:#FFD24A:#FFC700:#F5A300:#E48400:#A85C00";
    public static final String HELP_GRAD_PERMS_GROUPS = "#FFE066:#FFC700:#E48400:#A85C00";
    public static final String HELP_GRAD_PERMS_USERS = "#FFE066:#FFC700:#E48400:#A85C00";
    public static final String HELP_GRAD_PERMS_TPRM_USAGE = "#FFE066:#FFC700:#E48400:#A85C00";

    // Land protection help category. Stone-grey palette signals territorial /
    // boundary semantics distinct from the warmer feature categories.
    public static final String HELP_GRAD_PROTECTION = "#B0B0B0:#A8AAAE:#A0A4AC:#989EAA:#9098A8:#8892A6:#808CA4:#7886A2:#7080A0:#687A9E:#60749C:#586E9A:#506898";
    public static final String HELP_GRAD_PROTECTION_CLAIM = "#B0B0B0:#9098A8:#7080A0:#506898";
    public static final String HELP_GRAD_PROTECTION_UNCLAIM = "#B0B0B0:#9098A8:#7080A0:#506898";
    public static final String HELP_GRAD_PROTECTION_MEMBERS = "#B0B0B0:#9098A8:#7080A0:#506898";
    public static final String HELP_GRAD_PROTECTION_FLAGS = "#B0B0B0:#9098A8:#7080A0:#506898";
}