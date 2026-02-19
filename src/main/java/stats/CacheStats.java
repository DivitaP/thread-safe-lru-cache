package stats;

import java.util.concurrent.atomic.AtomicLong;

public final class CacheStats {
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);
    private final AtomicLong loadCount = new AtomicLong(0);
    private final AtomicLong loadFailCount = new AtomicLong(0);
    private final AtomicLong expiredCount = new AtomicLong(0);
    private final AtomicLong putCount = new AtomicLong(0);

    public void recordHit() {
        hitCount.incrementAndGet();
    }

    public void recordMiss() {
        missCount.incrementAndGet();
    }

    public void recordEviction() {
        evictionCount.incrementAndGet();
    }

    public void recordLoad() {
        loadCount.incrementAndGet();
    }

    public void recordLoadFail() {
        loadFailCount.incrementAndGet();
    }

    public void recordExpired() {
        expiredCount.incrementAndGet();
    }

    public void recordPut() {
        putCount.incrementAndGet();
    }

    public double hitRate() {
        long total = totalRequestCount();
        return total == 0 ? 0.0 : (double) hitCount.get() / total;
    }

    public double missRate() {
        long total = totalRequestCount();
        return total == 0 ? 0.0 : (double) missCount.get() / total;
    }

    public long totalRequestCount() {
        return hitCount.get() + missCount.get();
    }

        public long getHitCount() {
            return hitCount.get();
        }

        public long getMissCount() {
            return missCount.get();
        }

        public long getEvictionCount() {
            return evictionCount.get();
        }

        public long getLoadCount() {
            return loadCount.get();
        }

        public long getLoadFailCount() {
            return loadFailCount.get();
        }

        public long getExpiredCount() {
            return expiredCount.get();
        }

        public long getPutCount() {
            return putCount.get();
        }

    public void reset() {
        hitCount.set(0);
        missCount.set(0);
        evictionCount.set(0);
        loadCount.set(0);
        loadFailCount.set(0);
        expiredCount.set(0);
        putCount.set(0);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                hitCount.get(),
                missCount.get(),
                evictionCount.get(),
                loadCount.get(),
                loadFailCount.get(),
                expiredCount.get(),
                putCount.get()
        );
    }

    public static final class Snapshot {
        private final long hitCount;
        private final long missCount;
        private final long evictionCount;
        private final long loadCount;
        private final long loadFailCount;
        private final long expiredCount;
        private final long putCount;

        public Snapshot(long hitCount, long missCount, long evictionCount, long loadCount, long loadFailCount, long expiredCount, long putCount) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.evictionCount = evictionCount;
            this.loadCount = loadCount;
            this.loadFailCount = loadFailCount;
            this.expiredCount = expiredCount;
            this.putCount = putCount;
        }

        public long getHitCount() {
            return hitCount;
        }

        public long getMissCount() {
            return missCount;
        }

        public long getEvictionCount() {
            return evictionCount;
        }

        public long getLoadCount() {
            return loadCount;
        }

        public long getLoadFailCount() {
            return loadFailCount;
        }

        public long getExpiredCount() {
            return expiredCount;
        }

        public long getPutCount() {
            return putCount;
        }

        public double hitRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }

        public double missRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) missCount / total;
        }

        public long totalRequestCount() {
            return hitCount + missCount;
        }

        @Override
        public String toString() {
            return String.format(
                    "CacheStats.Snapshot{requests=%d, hitRate=%.2f%%, missRate=%.2f%%, " +
                            "evictions=%d, loads=%d, loadFails=%d, expired=%d, puts=%d}",
                    totalRequestCount(),
                    hitRate()  * 100,
                    missRate() * 100,
                    evictionCount,
                    loadCount,
                    loadFailCount,
                    expiredCount,
                    putCount
            );
        }
    }

    @Override
    public String toString() {
        return snapshot().toString();
    }
}
