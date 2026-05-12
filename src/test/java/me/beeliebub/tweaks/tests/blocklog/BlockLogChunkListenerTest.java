package me.beeliebub.tweaks.tests.blocklog;

import me.beeliebub.tweaks.blocklog.BlockLogChunkListener;
import me.beeliebub.tweaks.blocklog.ChestLogManager;
import org.bukkit.Chunk;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockLogChunkListenerTest {

    @Test
    void retentionWindowIsThirtyDays() throws Exception {
        // Locked-in invariant: the 30-day retention is documented and other listeners assume it.
        Field field = BlockLogChunkListener.class.getDeclaredField("RETENTION_MILLIS");
        field.setAccessible(true);
        long retention = (long) field.get(null);
        assertEquals(TimeUnit.DAYS.toMillis(30), retention);
    }

    @Test
    void chunkLoadEventDelegatesToManagerWithCorrectCutoff() {
        ChestLogManager manager = mock(ChestLogManager.class);
        BlockLogChunkListener listener = new BlockLogChunkListener(manager);

        Chunk chunk = mock(Chunk.class);
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getChunk()).thenReturn(chunk);

        long before = System.currentTimeMillis();
        listener.onChunkLoad(event);
        long after = System.currentTimeMillis();

        ArgumentCaptor<Long> cutoff = ArgumentCaptor.forClass(Long.class);
        verify(manager).pruneChunk(eq(chunk), cutoff.capture());

        long retentionMs = TimeUnit.DAYS.toMillis(30);
        long captured = cutoff.getValue();
        assertTrue(captured >= before - retentionMs,
                "cutoff should be roughly now - 30 days; was " + captured);
        assertTrue(captured <= after - retentionMs,
                "cutoff should not be in the future relative to call time");
    }

    @Test
    void multipleEventsAlwaysHitTheManager() {
        ChestLogManager manager = mock(ChestLogManager.class);
        BlockLogChunkListener listener = new BlockLogChunkListener(manager);

        Chunk a = mock(Chunk.class);
        Chunk b = mock(Chunk.class);
        ChunkLoadEvent eventA = mock(ChunkLoadEvent.class);
        ChunkLoadEvent eventB = mock(ChunkLoadEvent.class);
        when(eventA.getChunk()).thenReturn(a);
        when(eventB.getChunk()).thenReturn(b);

        listener.onChunkLoad(eventA);
        listener.onChunkLoad(eventB);

        verify(manager).pruneChunk(eq(a), anyLong());
        verify(manager).pruneChunk(eq(b), anyLong());
    }
}
