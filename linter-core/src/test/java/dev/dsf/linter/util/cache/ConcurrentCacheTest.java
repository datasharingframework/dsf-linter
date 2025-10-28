package dev.dsf.linter.util.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentCacheTest {

    private ConcurrentCache<String, String> cache;

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void testBasicGetOrCreate() {
        cache = new ConcurrentCache<>();

        String value = cache.getOrCreate("key1", k -> "value1");
        assertEquals("value1", value);
        assertEquals(1, cache.size());

        String cachedValue = cache.getOrCreate("key1", k -> "different");
        assertEquals("value1", cachedValue);
        assertEquals(1, cache.size());
    }

    @Test
    void testGetOrCreateWithException() {
        cache = new ConcurrentCache<>();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            cache.getOrCreate("key1", k -> {
                throw new IllegalStateException("Creator failed");
            });
        });

        assertTrue(exception.getMessage().contains("Failed to create cache value"));
        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    @Test
    void testCleanupCallback() {
        List<String> cleanedUp = new ArrayList<>();
        cache = new ConcurrentCache<>(cleanedUp::add);

        cache.getOrCreate("key1", k -> "value1");
        cache.getOrCreate("key2", k -> "value2");

        cache.clear();

        assertEquals(2, cleanedUp.size());
        assertTrue(cleanedUp.contains("value1"));
        assertTrue(cleanedUp.contains("value2"));
        assertTrue(cache.isEmpty());
    }

    @Test
    void testRemoveWithCleanup() {
        List<String> cleanedUp = new ArrayList<>();
        cache = new ConcurrentCache<>(cleanedUp::add);

        cache.getOrCreate("key1", k -> "value1");
        String removed = cache.remove("key1");

        assertEquals("value1", removed);
        assertEquals(1, cleanedUp.size());
        assertTrue(cleanedUp.contains("value1"));
    }

    @Test
    void testPutReplacesAndCleansUp() {
        List<String> cleanedUp = new ArrayList<>();
        cache = new ConcurrentCache<>(cleanedUp::add);

        cache.put("key1", "value1");
        String previous = cache.put("key1", "value2");

        assertEquals("value1", previous);
        assertEquals(1, cleanedUp.size());
        assertTrue(cleanedUp.contains("value1"));
        assertEquals("value2", cache.get("key1").orElse(null));
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        cache = new ConcurrentCache<>();
        int threadCount = 10;
        AtomicInteger creationCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    cache.getOrCreate("sharedKey", k -> {
                        creationCount.incrementAndGet();
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "sharedValue";
                    });
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, creationCount.get(),
                "Creator should be called exactly once despite concurrent access");
        assertEquals(1, cache.size());
    }

    @Test
    void testGetReturnsEmpty() {
        cache = new ConcurrentCache<>();

        Optional<String> result = cache.get("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void testContainsKey() {
        cache = new ConcurrentCache<>();

        assertFalse(cache.containsKey("key1"));
        cache.getOrCreate("key1", k -> "value1");
        assertTrue(cache.containsKey("key1"));
    }

    @Test
    void testNullKeyThrowsException() {
        cache = new ConcurrentCache<>();

        assertThrows(NullPointerException.class, () ->
                cache.getOrCreate(null, k -> "value"));

        assertThrows(NullPointerException.class, () ->
                cache.put(null, "value"));
    }

    @Test
    void testNullCreatorThrowsException() {
        cache = new ConcurrentCache<>();

        assertThrows(NullPointerException.class, () ->
                cache.getOrCreate("key1", null));
    }

    @Test
    void testCleanupCallbackException() {
        cache = new ConcurrentCache<>(value -> {
            throw new RuntimeException("Cleanup failed");
        });

        cache.getOrCreate("key1", k -> "value1");

        assertDoesNotThrow(() -> cache.clear(),
                "Cleanup exceptions should be caught and ignored");
    }
}