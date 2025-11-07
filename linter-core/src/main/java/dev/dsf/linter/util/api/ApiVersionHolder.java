package dev.dsf.linter.util.api;

import java.util.Objects;

/**
 * Holds the detected DSF BPE API version for use throughout the linter.
 * Provides a single source of truth for the detected version on a per-thread basis.
 * This class is thread-safe.
 */
public final class ApiVersionHolder {

    private static final ThreadLocal<ApiVersion> version =
            ThreadLocal.withInitial(() -> ApiVersion.UNKNOWN);

    private ApiVersionHolder() {
    }

    /**
     * Sets the DSF BPE API version for the current thread.
     *
     * @param v the API version to set; must not be null.
     */
    public static void setVersion(ApiVersion v) {
        version.set(Objects.requireNonNull(v, "version must not be null"));
    }

    /**
     * Returns the DSF BPE API version for the current thread.
     *
     * @return the current thread's API version, defaults to {@link ApiVersion#UNKNOWN}.
     */
    public static ApiVersion getVersion() {
        return version.get();
    }

    /**
     * Clears the API version for the current thread, resetting it to the initial value (UNKNOWN).
     * This should be called after a linting run to prevent memory leaks in thread pools.
     */
    public static void clear() {
        version.remove();
    }

}