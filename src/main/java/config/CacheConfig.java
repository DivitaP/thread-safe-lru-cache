package config;

import loader.CacheLoader;

import java.util.concurrent.TimeUnit;

public final class CacheConfig<K,V> {
    public static final int DEFAULT_CAPACITY = 100;
    public static final long DEFAULT_TTL_SECONDS = 300L; // 5 minutes
    public static final long DEFAULT_CLEANUP_INTERVAL = 60L; // 1 minute
    public static final boolean DEFAULT_RECORD_STATS = true;

    private final int capacity;
    private final long ttlSeconds;
    private final long cleanupIntervalSeconds;
    private final boolean recordStats;
    private CacheLoader<K,V> cacheLoader;

    private CacheConfig(Builder<K,V> builder) {
        this.capacity = builder.capacity;
        this.ttlSeconds = builder.ttlSeconds;
        this.cleanupIntervalSeconds = builder.cleanupIntervalSeconds;
        this.recordStats = builder.recordStats;
        this.cacheLoader = builder.cacheLoader;
    }

    public int getCapacity() {
        return capacity;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public long getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }

    public boolean isRecordStats() {
        return recordStats;
    }

    public CacheLoader<K,V> getCacheLoader() {
        return cacheLoader;
    }

    public boolean hasLoader() {
        return cacheLoader != null;
    }

    public static <K,V> Builder<K,V> builder() {
        return new Builder<>();
    }

    public static final class Builder<K,V> {

        private int capacity = DEFAULT_CAPACITY;
        private long ttlSeconds = DEFAULT_TTL_SECONDS;
        private long cleanupIntervalSeconds = DEFAULT_CLEANUP_INTERVAL;
        private boolean recordStats = DEFAULT_RECORD_STATS;
        private CacheLoader<K,V> cacheLoader;

        private Builder() {}

        public Builder<K,V> capacity(int capacity) {
            if(capacity <= 0) {
                throw new IllegalArgumentException("Capacity must be positive");
            }
            this.capacity = capacity;
            return this;
        }

        public Builder<K,V> ttl(long duration, TimeUnit unit) {
            if(duration <= 0) {
                throw new IllegalArgumentException("TTL duration must be positive");
            }

            this.ttlSeconds = unit.toSeconds(duration);
            return this;
        }

        public Builder<K,V> ttlSeconds(long ttlSeconds) {
            return ttl(ttlSeconds, TimeUnit.SECONDS);
        }

        public Builder<K,V> cleanupIntervalSeconds(long cleanupIntervalSeconds) {
            if(cleanupIntervalSeconds <= 0) {
                throw new IllegalArgumentException("Cleanup interval must be positive");
            }
            this.cleanupIntervalSeconds = cleanupIntervalSeconds;
            return this;
        }

        public Builder<K,V> recordStats(boolean recordStats) {
            this.recordStats = recordStats;
            return this;
        }

        public Builder<K,V> loader(CacheLoader<K,V> cacheLoader) {
            this.cacheLoader = cacheLoader;
            return this;
        }

        public CacheConfig<K,V> build() {
            return new CacheConfig<>(this);
        }
    }

    @Override
    public String toString() {
        return "CacheConfig{" +
                "capacity=" + capacity +
                ", ttlSeconds=" + ttlSeconds +
                ", cleanupIntervalSeconds=" + cleanupIntervalSeconds +
                ", recordStats=" + recordStats +
                ", hasLoader=" + hasLoader() +
                '}';
    }
}
