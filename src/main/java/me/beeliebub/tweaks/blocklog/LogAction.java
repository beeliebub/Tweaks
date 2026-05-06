package me.beeliebub.tweaks.blocklog;

// Action recorded in a chest log entry. Persisted as a single byte (ordinal()).
public enum LogAction {
    ADD,
    REMOVE;

    public static LogAction fromByte(byte b) {
        return b == 1 ? REMOVE : ADD;
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}