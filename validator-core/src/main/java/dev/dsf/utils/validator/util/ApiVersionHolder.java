// src/main/java/dev/dsf/utils/validator/ApiVersionHolder.java
package dev.dsf.utils.validator.util;

import java.util.Objects;


/**
 * Holds the detected DSF BPE API version for use throughout the validator.
 * Provides a single source of truth for the detected version.
 */
public final class ApiVersionHolder {
    private static volatile ApiVersion version = ApiVersion.UNKNOWN;

    private ApiVersionHolder() {}

    /**
     * Sets the globally visible DSF BPE API version.
     */
    public static void setVersion(ApiVersion v) {
        version = Objects.requireNonNull(v, "version must not be null");
    }

    /**
     * Returns the globally visible DSF BPE API version.
     */
    public static ApiVersion getVersion() {
        return version;
    }

    /**
     * Legacy string-based accessor kept for backwards compatibility.
     * @return "v1", "v2" or "unknown"
     */
    @Deprecated
    public static String getVersionString() {
        return switch (version) {
            case V1 -> "v1";
            case V2 -> "v2";
            case UNKNOWN -> "unknown";
        };
    }
}
