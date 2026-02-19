package loader;

public class CacheLoadException extends RuntimeException {
    private final Object failedKey;

    public CacheLoadException(Object failedKey, String message) {
        super(message);
        this.failedKey = failedKey;
    }

    public CacheLoadException(Object failedKey, String message, Throwable cause) {
        super(message, cause);
        this.failedKey = failedKey;
    }

    public Object getFailedKey() {
        return failedKey;
    }
}
