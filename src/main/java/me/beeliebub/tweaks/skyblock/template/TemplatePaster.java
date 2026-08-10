package me.beeliebub.tweaks.skyblock.template;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Main-thread, chunk-budgeted template paste job. */
public final class TemplatePaster {

    private final World world;
    private final IslandTemplate template;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final List<ChunkWork> chunks;
    private final BlockData[] paletteData;
    private final BlockEntityApplier blockEntityApplier;
    private int next;

    public TemplatePaster(World world, IslandTemplate template, int originX, int originY, int originZ) {
        this(world, validateDefaultTemplate(template), originX, originY, originZ, BukkitTemplateSupport.applier());
    }

    public TemplatePaster(World world, IslandTemplate template, int originX, int originY, int originZ,
                          BlockEntityApplier blockEntityApplier) {
        this.world = Objects.requireNonNull(world, "world");
        this.template = Objects.requireNonNull(template, "template");
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.chunks = buildChunks(template.width(), template.depth());
        this.paletteData = template.palette().stream().map(Bukkit::createBlockData).toArray(BlockData[]::new);
        this.blockEntityApplier = Objects.requireNonNull(blockEntityApplier, "blockEntityApplier");
    }

    public int tick(int chunkBudget) {
        if (chunkBudget <= 0) return 0;
        int processed = 0;
        while (processed < chunkBudget && next < chunks.size()) {
            pasteChunk(chunks.get(next++));
            processed++;
        }
        return processed;
    }

    public boolean complete() {
        return next >= chunks.size();
    }

    public int totalChunks() {
        return chunks.size();
    }

    private void pasteChunk(ChunkWork work) {
        for (int y = 0; y < template.height(); y++) {
            for (int z = work.minZ(); z < work.maxZ(); z++) {
                for (int x = work.minX(); x < work.maxX(); x++) {
                    int worldX = originX + x;
                    int worldZ = originZ + z;
                    BlockData data = paletteData[template.indexAt(x, y, z)];
                    org.bukkit.block.Block block = world.getBlockAt(worldX, originY + y, worldZ);
                    if (!block.getBlockData().matches(data)) block.setBlockData(data, false);
                }
            }
        }
        for (var entry : template.blockEntities().entrySet()) {
            IslandTemplate.BlockEntity entity = entry.getKey();
            if (entity.x() < work.minX() || entity.x() >= work.maxX()
                    || entity.z() < work.minZ() || entity.z() >= work.maxZ()) continue;
            if (!blockEntityApplier.apply(world, originX + entity.x(), originY + entity.y(),
                    originZ + entity.z(), entity, entry.getValue())) {
                throw new IllegalStateException("Could not restore template block entity " + entity.type());
            }
        }
    }

    private static List<ChunkWork> buildChunks(int width, int depth) {
        List<ChunkWork> result = new ArrayList<>();
        for (int minX = 0; minX < width; minX += 16) {
            for (int minZ = 0; minZ < depth; minZ += 16) {
                result.add(new ChunkWork(minX, Math.min(width, minX + 16), minZ, Math.min(depth, minZ + 16)));
            }
        }
        return List.copyOf(result);
    }

    private static IslandTemplate validateDefaultTemplate(IslandTemplate template) {
        Objects.requireNonNull(template, "template");
        for (IslandTemplate.BlockEntity entity : template.blockEntities().keySet()) {
            if (!BukkitTemplateSupport.supports(entity.type())) {
                throw new IllegalArgumentException("Unsupported template block entity: " + entity.type());
            }
        }
        return template;
    }

    private record ChunkWork(int minX, int maxX, int minZ, int maxZ) {
    }

    @FunctionalInterface
    public interface BlockEntityApplier {
        BlockEntityApplier EMPTY = (world, x, y, z, entity, payload) -> payload == null || payload.isBlank();

        boolean apply(World world, int x, int y, int z, IslandTemplate.BlockEntity entity, String payload);
    }
}
