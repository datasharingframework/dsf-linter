// src/main/java/dev/dsf/utils/validator/util/ApiVersionHolder.java
package dev.dsf.utils.validator;

/**
 * Holds the detected DSF BPE API version for use throughout the validator.
 */
public class ApiVersionHolder {
    private static String version = "v1";

    public static void setVersion(String ver) {
        version = ver;
    }

    public static String getVersion() {
        return version;
    }
}
