package me.beeliebub.tweaks.core.config;

/**
 * The kind of editor a {@link ConfigSetting} needs, both for its CLI grammar and its Dialog GUI
 * leaf screen. MOB_LIST and MATERIAL_LIST are, as of this writing, only ever used by the three
 * pre-existing legacy settings (eggdrop/spawneregg/resourceitems) whose mutation is special-cased
 * in {@link ConfigValueEditor} rather than generic - see that class's class-level Javadoc.
 */
public enum EditorType {
    INT,
    LONG,
    DOUBLE,
    PERCENT,
    BOOLEAN,
    MATERIAL,
    NAMESPACED_KEY,
    WORLD_KEY,
    STRING_LIST,
    WORLD_KEY_LIST,
    MOB_LIST,
    MATERIAL_LIST,
    NUMBER_MAP,
    STRING,
    MATERIAL_GRID
}
