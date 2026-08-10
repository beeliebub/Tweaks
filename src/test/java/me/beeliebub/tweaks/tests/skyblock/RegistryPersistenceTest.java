package me.beeliebub.tweaks.tests.skyblock;

import me.beeliebub.tweaks.Tweaks;
import me.beeliebub.tweaks.skyblock.SkyblockBootstrap;
import me.beeliebub.tweaks.skyblock.challenge.ChallengeRegistry;
import me.beeliebub.tweaks.skyblock.economy.ShopCatalog;
import me.beeliebub.tweaks.skyblock.generator.GeneratorRegistry;
import me.beeliebub.tweaks.skyblock.island.IslandStore;
import me.beeliebub.tweaks.skyblock.island.PendingWipeStore;
import me.beeliebub.tweaks.skyblock.type.TypeRegistry;
import me.beeliebub.tweaks.utils.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistryPersistenceTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(RegistryKind.class)
    void rapidSavesRemainOrderedAndLatestTailSurvivesEarlierFailure(RegistryKind kind,
                                                                    @TempDir Path directory) throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        QueuedRegistry registry = registry(kind, plugin);
        ManualExecutor executor = new ManualExecutor();
        ControlledFuture<Void> initialTail = new ControlledFuture<>(executor);
        replaceSaveTail(registry.owner(), initialTail);
        AtomicInteger writes = new AtomicInteger();

        try (MockedStatic<YamlStore> yamlStore = mockStatic(YamlStore.class)) {
            yamlStore.when(() -> YamlStore.saveNow(any(JavaPlugin.class), any(File.class), anyString(),
                            org.mockito.ArgumentMatchers.<Consumer<YamlConfiguration>>any()))
                    .thenAnswer(invocation -> writes.incrementAndGet() != 1);

            CompletableFuture<Void> first = registry.save().get();
            CompletableFuture<Void> second = registry.save().get();
            CompletableFuture<Void> retained = registry.flush().get();

            assertEquals(0, executor.size());
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            initialTail.complete(null);
            assertEquals(1, executor.size());

            executor.runNext();
            assertTrue(first.isCompletedExceptionally());
            assertEquals(1, executor.size());
            assertFalse(second.isDone());

            executor.runNext();
            retained.get(2, TimeUnit.SECONDS);

            assertNotSame(second, retained);
            assertTrue(second.isDone());
            assertTrue(retained.isDone());
            assertEquals(2, writes.get());
        }
    }

    @ParameterizedTest(name = "{0} detached observers")
    @EnumSource(RegistryKind.class)
    void completingReturnedFuturesCannotReleasePrivateSaveQueue(RegistryKind kind,
                                                                 @TempDir Path directory) throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        QueuedRegistry registry = registry(kind, plugin);
        ManualExecutor executor = new ManualExecutor();
        ControlledFuture<Void> initialTail = new ControlledFuture<>(executor);
        replaceSaveTail(registry.owner(), initialTail);

        try (MockedStatic<YamlStore> yamlStore = mockStatic(YamlStore.class)) {
            yamlStore.when(() -> YamlStore.saveNow(any(JavaPlugin.class), any(File.class), anyString(),
                            org.mockito.ArgumentMatchers.<Consumer<YamlConfiguration>>any()))
                    .thenReturn(true);

            CompletableFuture<Void> saveObserver = registry.save().get();
            CompletableFuture<Void> flushObserver = registry.flush().get();
            CompletableFuture<Void> laterSave = registry.save().get();

            saveObserver.complete(null);
            flushObserver.complete(null);
            assertEquals(0, executor.size());
            assertFalse(laterSave.isDone());

            initialTail.complete(null);
            executor.runNext();
            executor.runNext();
            laterSave.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void shutdownAwaitsAllRegistryQueuesUnderItsPersistenceDeadline() throws Exception {
        Tweaks plugin = mock(Tweaks.class);
        SkyblockBootstrap.Runtime runtime = mock(SkyblockBootstrap.Runtime.class);
        IslandStore islandStore = mock(IslandStore.class);
        PendingWipeStore pendingWipeStore = mock(PendingWipeStore.class);
        TypeRegistry types = mock(TypeRegistry.class);
        ChallengeRegistry challenges = mock(ChallengeRegistry.class);
        GeneratorRegistry generators = mock(GeneratorRegistry.class);
        ShopCatalog shop = mock(ShopCatalog.class);
        CompletableFuture<Void> completed = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> shopSave = new CompletableFuture<>();
        CountDownLatch shopFlushCalled = new CountDownLatch(1);
        AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();

        when(runtime.islandStore()).thenReturn(islandStore);
        when(runtime.pendingWipeStore()).thenReturn(pendingWipeStore);
        when(runtime.typeRegistry()).thenReturn(types);
        when(runtime.challengeRegistry()).thenReturn(challenges);
        when(runtime.generatorRegistry()).thenReturn(generators);
        when(runtime.shopCatalog()).thenReturn(shop);
        when(islandStore.shutdown()).thenReturn(completed);
        when(pendingWipeStore.shutdown()).thenReturn(completed);
        when(types.flush()).thenReturn(completed);
        when(challenges.flush()).thenReturn(completed);
        when(generators.flush()).thenReturn(completed);
        when(shop.flush()).thenAnswer(invocation -> {
            shopFlushCalled.countDown();
            return shopSave;
        });

        Thread shutdown = Thread.ofPlatform().name("skyblock-shutdown-test").start(() -> {
            try {
                SkyblockBootstrap.shutdown(plugin, runtime);
            } catch (Throwable error) {
                shutdownFailure.set(error);
            }
        });

        boolean reachedShopFlush = shopFlushCalled.await(2, TimeUnit.SECONDS);
        assertTrue(reachedShopFlush, () -> "Shutdown failed before the shop flush: " + shutdownFailure.get());
        assertTrue(shutdown.isAlive());
        shopSave.complete(null);
        shutdown.join(2_000);

        assertFalse(shutdown.isAlive());
        assertNull(shutdownFailure.get());
        verify(types).flush();
        verify(challenges).flush();
        verify(generators).flush();
    }

    private static QueuedRegistry registry(RegistryKind kind, JavaPlugin plugin) {
        return switch (kind) {
            case CHALLENGE -> {
                ChallengeRegistry registry = new ChallengeRegistry(plugin);
                yield new QueuedRegistry(registry, registry::saveAsync, registry::flush);
            }
            case TYPE -> {
                TypeRegistry registry = new TypeRegistry(plugin);
                yield new QueuedRegistry(registry, registry::saveAsync, registry::flush);
            }
            case GENERATOR -> {
                GeneratorRegistry registry = new GeneratorRegistry(plugin);
                yield new QueuedRegistry(registry, registry::saveAsync, registry::flush);
            }
            case SHOP -> {
                ShopCatalog registry = new ShopCatalog(plugin);
                yield new QueuedRegistry(registry, registry::saveAsync, registry::flush);
            }
        };
    }

    private static void replaceSaveTail(Object registry, CompletableFuture<Void> tail) throws Exception {
        Field field = registry.getClass().getDeclaredField("saveTail");
        field.setAccessible(true);
        field.set(registry, tail);
    }

    private enum RegistryKind {
        CHALLENGE,
        TYPE,
        GENERATOR,
        SHOP
    }

    private record QueuedRegistry(Object owner, Supplier<CompletableFuture<Void>> save,
                                  Supplier<CompletableFuture<Void>> flush) {
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        int size() {
            return tasks.size();
        }

        void runNext() {
            tasks.remove().run();
        }
    }

    private static final class ControlledFuture<T> extends CompletableFuture<T> {
        private final Executor executor;

        private ControlledFuture(Executor executor) {
            this.executor = executor;
        }

        @Override
        public <U> CompletableFuture<U> newIncompleteFuture() {
            return new ControlledFuture<>(executor);
        }

        @Override
        public Executor defaultExecutor() {
            return executor;
        }
    }
}
