package me.beeliebub.tweaks.skyblock.template;

import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/** Bukkit adapters for the lossless block data carried by an island template. */
public final class BukkitTemplateSupport {
    private static final String VERSION = "v1";

    private BukkitTemplateSupport() {
    }

    public static TemplateCapture.BlockSource source(World world) {
        Objects.requireNonNull(world, "world");
        return new TemplateCapture.BlockSource() {
            @Override
            public String blockData(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getBlockData().getAsString();
            }

            @Override
            public boolean isAirAt(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType().isAir();
            }

            @Override
            public java.util.Map<IslandTemplate.BlockEntity, String> blockEntities(
                    int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                java.util.Map<IslandTemplate.BlockEntity, String> entities = new java.util.LinkedHashMap<>();
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            BlockState state = world.getBlockAt(x, y, z).getState();
                            EntityPayload payload = capture(state);
                            if (payload != null) {
                                entities.put(new IslandTemplate.BlockEntity(x - minX, y - minY, z - minZ,
                                        payload.type()), payload.payload());
                            }
                        }
                    }
                }
                return entities;
            }
        };
    }

    public static TemplatePaster.BlockEntityApplier applier() {
        return (world, x, y, z, entity, payload) -> apply(world, x, y, z, entity, payload);
    }

    public static boolean supports(String type) {
        if (type == null) return false;
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "container", "sign", "jukebox" -> true;
            default -> false;
        };
    }

    private static EntityPayload capture(BlockState state) {
        if (state instanceof Container container) {
            return new EntityPayload("container", encodeInventory(container.getInventory()));
        }
        if (state instanceof Sign sign) {
            return new EntityPayload("sign", encodeSign(sign));
        }
        if (state instanceof Jukebox jukebox) {
            return new EntityPayload("jukebox", VERSION + "|"
                    + (jukebox.getRecord() == null ? "" : encodeItem(jukebox.getRecord())));
        }
        if (state instanceof TileState) {
            // Preserve the fact that a block entity exists so the default paster can reject
            // the template before changing a destination world instead of silently losing data.
            return new EntityPayload("unsupported", state.getType().name());
        }
        return null;
    }

    private static boolean apply(World world, int x, int y, int z,
                                 IslandTemplate.BlockEntity entity, String payload) {
        if (entity == null || payload == null) return false;
        BlockState state = world.getBlockAt(x, y, z).getState();
        try {
            return switch (entity.type().toLowerCase(java.util.Locale.ROOT)) {
                case "container" -> applyInventory(state, payload);
                case "sign" -> applySign(state, payload);
                case "jukebox" -> applyJukebox(state, payload);
                default -> false;
            };
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean applyInventory(BlockState state, String payload) {
        if (!(state instanceof Container container)) return false;
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 3 || !VERSION.equals(fields[0])) return false;
        int size = Integer.parseInt(fields[1]);
        if (size != container.getInventory().getSize() || fields[2].isBlank()) return false;
        String[] encoded = fields[2].split(",", -1);
        if (encoded.length != size) return false;
        ItemStack[] contents = new ItemStack[size];
        for (int index = 0; index < size; index++) {
            contents[index] = encoded[index].isBlank() ? null : decodeItem(encoded[index]);
        }
        container.getInventory().setContents(contents);
        return container.update(true, false);
    }

    private static boolean applySign(BlockState state, String payload) {
        if (!(state instanceof Sign sign)) return false;
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 3 || !VERSION.equals(fields[0])) return false;
        applySignSide(sign.getSide(Side.FRONT), fields[1]);
        applySignSide(sign.getSide(Side.BACK), fields[2]);
        return sign.update(true, false);
    }

    private static void applySignSide(SignSide side, String encoded) {
        String[] lines = encoded.split(",", -1);
        if (lines.length != 4) throw new IllegalArgumentException("Sign must have four lines");
        for (int index = 0; index < lines.length; index++) {
            side.setLine(index, decodeText(lines[index]));
        }
    }

    private static boolean applyJukebox(BlockState state, String payload) {
        if (!(state instanceof Jukebox jukebox)) return false;
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 2 || !VERSION.equals(fields[0])) return false;
        jukebox.setRecord(fields[1].isBlank() ? null : decodeItem(fields[1]));
        return jukebox.update(true, false);
    }

    private static String encodeInventory(Inventory inventory) {
        StringJoiner values = new StringJoiner(",");
        for (ItemStack item : inventory.getContents()) values.add(item == null ? "" : encodeItem(item));
        return VERSION + "|" + inventory.getSize() + "|" + values;
    }

    private static String encodeSign(Sign sign) {
        return VERSION + "|" + encodeSide(sign.getSide(Side.FRONT)) + "|" + encodeSide(sign.getSide(Side.BACK));
    }

    private static String encodeSide(SignSide side) {
        StringJoiner values = new StringJoiner(",");
        for (String line : side.getLines()) values.add(encodeText(line));
        return values.toString();
    }

    private static String encodeItem(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static ItemStack decodeItem(String value) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
    }

    private static String encodeText(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        return new String(Base64.getDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
    }

    private record EntityPayload(String type, String payload) {
    }
}
