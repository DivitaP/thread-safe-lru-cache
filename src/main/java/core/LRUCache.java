package core;

import config.CacheConfig;
import loader.CacheLoadException;
import loader.CacheLoader;
import stats.CacheStats;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LRUCache<K, V> implements Cache<K, V> {

    private static final Logger LOGGER = Logger.getLogger(LRUCache.class.getName());
    private final ConcurrentHashMap<K, CacheEntry<K, V>> map;
    private final CacheEntry<K, V> head;
    private final CacheEntry<K, V> tail;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    private final CacheConfig<K, V> config;
    private final CacheStats stats;
    private final ScheduledExecutorService cleanupExecutor;

    public LRUCache(CacheConfig<K, V> config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.map = new ConcurrentHashMap<>(Math.min(config.getCapacity() * 2, 1 << 16));
        this.stats = new CacheStats();

        this.head = new CacheEntry<>(null, null);
        this.tail = new CacheEntry<>(null, null);
        head.next = tail;
        tail.prev = head;

        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredEntries,
                config.getCleanupIntervalSeconds(),
                config.getCleanupIntervalSeconds(),
                TimeUnit.SECONDS
        );
    }

    public static <K,V> CacheConfig.Builder<K,V> builder() {
        return CacheConfig.builder();
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "key must not be null");

        readLock.lock();
        try {
            CacheEntry<K,V> entry = map.get(key);
            if(entry != null) {
                if(entry.isExpired(config.getTtlSeconds())) {
                    // fall through to write-lock removal
                } else {
                    // CACHE HIT
                    entry.touch();
                    moveToHead(entry);
                    if(config.isRecordStats()) stats.recordHit();
                    return Optional.of(entry.value);
                }
            }
        } finally {
            readLock.unlock();
        }

        // CACHE MISS or EXPIRED
        if(config.isRecordStats()) stats.recordMiss();

        // remove expired entry under write lock
        writeLock.lock();
        try {
            CacheEntry<K,V> entry = map.get(key);
            if(entry != null && entry.isExpired(config.getTtlSeconds())) {
                removeEntry(entry);
                if(config.isRecordStats()) stats.recordExpired();
            }
        } finally {
            writeLock.unlock();
        }

        // invoke loader if available
        if(config.hasLoader()) {
            return loadAndCache(key);
        }

        return Optional.empty();
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        writeLock.lock();
        try {
            CacheEntry<K,V> existing = map.get(key);
            if(existing != null) {
                // update entry and promote to MRU
                existing.value = value;
                existing.touch();
                moveToHead(existing);
            } else {
                // evict LRU if over capacity
                if(map.size() >= config.getCapacity()) {
                    evictLRU();
                }

                CacheEntry<K,V> newEntry = new CacheEntry<>(key, value);
                map.put(key, newEntry);
                addToHead(newEntry);


            }
            if(config.isRecordStats()) stats.recordPut();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean remove(K key) {
        Objects.requireNonNull(key, "key must not be null");
        writeLock.lock();
        try {
            CacheEntry<K, V> entry = map.remove(key);
            if(entry == null) return false;
            removeEntry(entry);
            return true;
        } finally {
            writeLock.unlock();
        }
    }


    @Override
    public boolean containsKey(K key) {
        Objects.requireNonNull(key, "key must not be null");
        readLock.lock();
        try {
            CacheEntry<K,V> entry = map.get(key);
            return entry != null && !entry.isExpired(config.getTtlSeconds());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            map.clear();
            head.next = tail;
            tail.prev = head;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void putAll(Map<K, V> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        entries.forEach(this::put);
    }

    @Override
    public Set<K> keys() {
        readLock.lock();
        try {
            return Collections.unmodifiableSet(map.keySet());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public CacheStats getStats() {
        return stats;
    }

    @Override
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Optional<V> loadAndCache(K key) {
        CacheLoader<K,V> loader = config.getCacheLoader();
        try {
            V loaded = loader.load(key);
            if(config.isRecordStats()) stats.recordLoad();
            if(loaded != null) {
                put(key, loaded);
                return Optional.of(loaded);
            }
        } catch (CacheLoadException e) {
            if(config.isRecordStats()) stats.recordLoadFail();
            LOGGER.log(Level.WARNING, "CacheLoader failed for key: " + key, e);
        }
        return Optional.empty();
    }

    private void addToHead(CacheEntry<K, V> entry) {
        entry.prev = head;
        entry.next = head.next;
        head.next.prev = entry;
        head.next = entry;
    }

    private void unlinkEntry(CacheEntry<K, V> entry) {
        entry.prev.next = entry.next;
        entry.next.prev = entry.prev;
    }

    private void moveToHead(CacheEntry<K, V> entry) {
        unlinkEntry(entry);
        addToHead(entry);
    }

    private void removeEntry(CacheEntry<K, V> entry) {
        map.remove(entry.key);
        unlinkEntry(entry);
    }

    private void evictLRU() {
        CacheEntry<K, V> lru = tail.prev;
        if (lru == head) return;
        removeEntry(lru);
        if(config.isRecordStats()) stats.recordEviction();
        LOGGER.fine(() -> "Evicted LRU entry with key: " + lru.key);
    }

    private void cleanupExpiredEntries() {
        List<K> expiredKeys = new ArrayList<>();
        readLock.lock();

        try {
            for(CacheEntry<K, V> entry : map.values()) {
                if(entry.isExpired(config.getTtlSeconds())) {
                    expiredKeys.add(entry.key);
                }
            }
        } finally {
            readLock.unlock();
        }

        if(expiredKeys.isEmpty()) return;

        writeLock.lock();
        try {
            for(K key : expiredKeys) {
                CacheEntry<K,V> entry = map.get(key);
                if(entry != null && entry.isExpired(config.getTtlSeconds())) {
                    removeEntry(entry);
                    if(config.isRecordStats()) {
                        stats.recordExpired();
                        stats.recordEviction();
                    }
                }
            }
        } finally {
            writeLock.unlock();
        }

        LOGGER.fine(() -> "Cleaned up " + expiredKeys.size() + " expired cache entries");
    }

    @Override
    public String toString() {
        return "LRUCache{" +
                "size=" + size() +
                "capacity=" + config.getCapacity() +
                "stats=" + stats +
                '}';
    }
}
