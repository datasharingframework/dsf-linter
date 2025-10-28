package dev.dsf.linter.util.cache;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A thread-safe, generic cache implementation with support for automatic value creation,
 * cleanup callbacks, and cache invalidation.
 *
 * <p>This class provides a reusable caching abstraction that eliminates duplicate cache
 * management logic across the application. It uses {@link ConcurrentHashMap} internally
 * for thread-safe operations without explicit locking.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Thread-safe atomic get-or-create operations via {@link #getOrCreate(Object, Function)}</li>
 *   <li>Optional cleanup callbacks when cache entries are removed</li>
 *   <li>Cache invalidation with automatic resource cleanup</li>
 *   <li>Support for null values through {@link Optional} return types</li>
 * </ul>
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * // Simple cache without cleanup
 * ConcurrentCache<String, ClassLoader> cache = new ConcurrentCache<>();
 * ClassLoader cl = cache.getOrCreate("key", k -> createClassLoader(k));
 *
 * // Cache with cleanup callback
 * ConcurrentCache<Path, File> fileCache = new ConcurrentCache<>(file -> {
 *     try {
 *         Files.deleteIfExists(file.toPath());
 *     } catch (IOException e) {
 *         // Log error
 *     }
 * });
 * }</pre>
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of cached values
 *
 * @since 1.0
 */
public class ConcurrentCache<K, V> {

    private final ConcurrentMap<K, V> cache;
    private final Consumer<V> cleanupCallback;

    /**
     * Creates a new cache without cleanup callbacks.
     */
    public ConcurrentCache() {
        this(null);
    }

    /**
     * Creates a new cache with an optional cleanup callback.
     *
     * <p>The cleanup callback is invoked for each cached value when the cache is cleared
     * or when individual entries are removed. This is useful for releasing resources
     * like file handles, class loaders, or temporary files.</p>
     *
     * @param cleanupCallback optional callback to clean up values when removed from cache,
     *                       may be null if no cleanup is needed
     */
    public ConcurrentCache(Consumer<V> cleanupCallback) {
        this.cache = new ConcurrentHashMap<>();
        this.cleanupCallback = cleanupCallback;
    }

    /**
     * Retrieves the value associated with the key, creating it if absent.
     *
     * <p>This method is thread-safe and guarantees that the creator function is called
     * at most once per key, even under concurrent access. If the creator function throws
     * an exception, it is wrapped in a {@link RuntimeException}.</p>
     *
     * @param key the cache key, must not be null
     * @param creator function to create the value if not present, must not be null
     * @return the cached or newly created value
     * @throws NullPointerException if key or creator is null
     * @throws RuntimeException if the creator function fails
     */
    public V getOrCreate(K key, Function<K, V> creator) {
        Objects.requireNonNull(key, "Cache key must not be null");
        Objects.requireNonNull(creator, "Creator function must not be null");

        return cache.computeIfAbsent(key, k -> {
            try {
                return creator.apply(k);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create cache value for key: " + k, e);
            }
        });
    }

    /**
     * Retrieves the value associated with the key, if present.
     *
     * @param key the cache key
     * @return Optional containing the cached value, or empty if not present
     */
    public Optional<V> get(K key) {
        return Optional.ofNullable(cache.get(key));
    }

    /**
     * Stores a value in the cache, replacing any existing value.
     *
     * @param key the cache key, must not be null
     * @param value the value to cache
     * @return the previous value associated with the key, or null if none existed
     */
    public V put(K key, V value) {
        Objects.requireNonNull(key, "Cache key must not be null");
        V previous = cache.put(key, value);
        if (previous != null && cleanupCallback != null) {
            cleanupCallback.accept(previous);
        }
        return previous;
    }

    /**
     * Removes the entry for the specified key, invoking the cleanup callback if configured.
     *
     * @param key the cache key
     * @return the removed value, or null if the key was not present
     */
    public V remove(K key) {
        V removed = cache.remove(key);
        if (removed != null && cleanupCallback != null) {
            cleanupCallback.accept(removed);
        }
        return removed;
    }

    /**
     * Clears all entries from the cache, invoking cleanup callbacks for all values.
     *
     * <p>If a cleanup callback is configured, it is called for each cached value.
     * Exceptions thrown by the cleanup callback are caught and ignored to ensure
     * all entries are processed.</p>
     */
    public void clear() {
        if (cleanupCallback != null) {
            cache.values().forEach(value -> {
                try {
                    cleanupCallback.accept(value);
                } catch (Exception e) {
                    // Best effort cleanup - log but continue
                }
            });
        }
        cache.clear();
    }

    /**
     * Returns the number of entries currently in the cache.
     *
     * @return the cache size
     */
    public int size() {
        return cache.size();
    }

    /**
     * Checks if the cache contains an entry for the specified key.
     *
     * @param key the cache key
     * @return true if the cache contains the key, false otherwise
     */
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    /**
     * Checks if the cache is empty.
     *
     * @return true if the cache contains no entries, false otherwise
     */
    public boolean isEmpty() {
        return cache.isEmpty();
    }
}