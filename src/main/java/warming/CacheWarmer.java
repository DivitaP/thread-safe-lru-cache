package warming;

import core.Cache;
import loader.CacheLoadException;
import loader.CacheLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CacheWarmer<K, V> {
    private static final Logger LOGGER = Logger.getLogger(CacheWarmer.class.getName());
    private static final int DEFAULT_CONCURRENCY = 4;

    private final CacheLoader<K, V> loader;
    private final int concurrency;

    private CacheWarmer(Builder<K, V> builder) {
        this.loader = builder.loader;
        this.concurrency = builder.concurrency;
    }

    public WarmingResult warm(Cache<K, V> cache, Collection<K> keys) {
        Objects.requireNonNull(cache, "cache must not be null");
        Objects.requireNonNull(keys, "keys must not be null");

        if (keys.isEmpty()) {
            return WarmingResult.empty();
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        List<Future<Boolean>> futures = new ArrayList<>();
        long startMs = System.currentTimeMillis();

        for (K key : keys) {
            futures.add(executor.submit(() -> {
                try {
                    V value = loader.load(key);
                    if (value != null) {
                        cache.put(key, value);
                        return true;
                    }
                } catch (CacheLoadException e) {
                    LOGGER.log(Level.WARNING, "Warming failed for key: " + key, e);
                }
                return false;
            }));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Cache warming interrupted");
        }

        long successCount = 0;
        long failCount = 0;

        for (Future<Boolean> future : futures) {
            try {
                if (Boolean.TRUE.equals(future.get()) && future.isDone()) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (ExecutionException | InterruptedException e) {
                failCount++;
            }
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        LOGGER.info(String.format("Cache warming complete: %d loaded, %d failed, %d ms",
                successCount, failCount, elapsedMs));

        return new WarmingResult(successCount, failCount, elapsedMs);
    }

    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    public static final class Builder<K, V> {
        private int concurrency = DEFAULT_CONCURRENCY;
        private CacheLoader<K, V> loader;

        private Builder() {
        }

        public Builder<K, V> concurrency(int concurrency) {
            if (concurrency <= 0) {
                throw new IllegalArgumentException("Concurrency must be positive");
            }
            this.concurrency = concurrency;
            return this;
        }

        public Builder<K, V> loader(CacheLoader<K, V> loader) {
            this.loader = Objects.requireNonNull(loader);
            return this;
        }

        public CacheWarmer<K, V> build() {
            Objects.requireNonNull(loader, "Loader must be set");
            return new CacheWarmer<>(this);
        }
    }

    public static final class WarmingResult {
        private final long successCount;
        private final long failCount;
        private final long elapsedMs;

        public WarmingResult(long successCount, long failCount, long elapsedMs) {
            this.successCount = successCount;
            this.failCount = failCount;
            this.elapsedMs = elapsedMs;
        }

        public static WarmingResult empty() {
            return new WarmingResult(0, 0, 0);
        }

        public long getSuccessCount() {
            return successCount;
        }

        public long getFailCount() {
            return failCount;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }

        public long getTotalCount() {
            return successCount + failCount;
        }

        @Override
        public String toString() {
            return "WarmingResult{" +
                    "successCount=" + successCount +
                    ", failCount=" + failCount +
                    ", elapsedMs=" + elapsedMs +
                    '}';
        }
    }
}
