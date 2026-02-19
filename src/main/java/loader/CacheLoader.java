package loader;

@FunctionalInterface
public interface CacheLoader<K,V> {
    V load(K key) throws CacheLoadException;
}
