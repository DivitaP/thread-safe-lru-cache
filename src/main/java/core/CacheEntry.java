package core;

final class CacheEntry<K,V> {

    final K key;
    V value;
    long createdAt;
    long lastAccessedAt;

    CacheEntry<K, V> prev;
    CacheEntry<K, V> next;

    CacheEntry(K key, V value) {
        this.key = key;
        this.value = value;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.lastAccessedAt = now;
    }

    boolean isExpired(long ttlSeconds) {
        if(ttlSeconds <= 0) {
            return false; // No expiration
        }
        return (System.currentTimeMillis() - createdAt) > ttlSeconds * 1000L;
    }

    void touch() {
        this.lastAccessedAt = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "CacheEntry{" +
                "key=" + key +
                ", value=" + value +
                ", createdAt=" + createdAt +
                ", lastAccessedAt=" + lastAccessedAt +
                '}';
    }
}
