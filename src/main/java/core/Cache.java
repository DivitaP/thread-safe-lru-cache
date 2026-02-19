package core;

import stats.CacheStats;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface Cache<K,V> {

    Optional<V> get(K key);
    void put(K key, V value);
    boolean remove(K key);
    boolean containsKey(K key);
    int size();
    boolean isEmpty();
    void clear();
    void putAll(Map<K,V> entries);
    Set<K> keys();
    CacheStats getStats();
    void shutdown();
}
