// src/main/java/dev/dsf/utils/validator/util/ApiVersionDetector.java
package dev.dsf.utils.validator.util;

import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;

import java.io.IOException;
import java.nio.file.*;

/**
 * Detects whether the exploded / source tree uses DSF BPE API v1 or v2.
 *
 * <p><strong>Primary rule:</strong> We require a ServiceLoader registration for
 * {@code ProcessPluginDefinition}. If none is found in any recognized
 * {@code META-INF/services} location, {@link MissingServiceRegistrationException}
 * is thrown so callers can turn this into a validation item.</p>
 *
 * <p><strong>Helpers:</strong> For best-effort detection you can use
 * {@link #detectFromServiceLoader(Path)} (non-throwing) or {@link #detectByFallback(Path)}
 * to infer the API version from class names when you want to continue validation.</p>
 */
public final class ApiVersionDetector
{
    /**
     * Detects the API version by locating a ServiceLoader provider-configuration file.
     * Throws if none is found so the caller can surface a validation error.
     *
     * @param root project root to scan
     * @return "v1" or "v2"
     * @throws MissingServiceRegistrationException if no ServiceLoader registration is found
     */
    public String detectOrThrow(Path root) throws MissingServiceRegistrationException
    {
        String version = detectFromServiceLoader(root);
        if (version == null)
        {
            throw new MissingServiceRegistrationException(
                    "No ProcessPluginDefinition ServiceLoader registration found. " +
                            "Please add a provider-configuration file " +
                            "\"META-INF/services/dev.dsf.bpe.v2.ProcessPluginDefinition\" " +
                            "(or the v1 equivalent) to your plugin JAR.");
        }
        return version;
    }

    /**
     * Attempts to detect the API version by checking common ServiceLoader locations.
     * Returns {@code null} if no registration is found.
     *
     * @param root project root to scan
     * @return "v1", "v2", or {@code null} if not found
     */
    public String detectFromServiceLoader(Path root)
    {
        // Typical Maven output
        String v = findInServicesDir(root.resolve("target/classes/META-INF/services"));
        if (v != null) return v;

        // Typical Gradle outputs (resources & classes)
        v = findInServicesDir(root.resolve("build/resources/main/META-INF/services"));
        if (v != null) return v;
        v = findInServicesDir(root.resolve("build/classes/java/main/META-INF/services"));
        if (v != null) return v;

        // Source-tree check (useful during development)
        v = findInServicesDir(root.resolve("src/main/resources/META-INF/services"));
        if (v != null) return v;

        // Flat/exploded layout
        v = findInServicesDir(root.resolve("META-INF/services"));
        if (v != null) return v;

        return null;
    }

    /**
     * Best-effort fallback by scanning for class names that hint at the DSF BPE API version.
     * This never throws.
     *
     * @param root project root to scan
     * @return "v1", "v2", or {@code null} if inconclusive
     */
    public String detectByFallback(Path root)
    {
        // Prefer compiled outputs; otherwise walk from root
        Path start = Files.isDirectory(root.resolve("target/classes"))
                ? root.resolve("target/classes")
                : (Files.isDirectory(root.resolve("build/classes/java/main"))
                ? root.resolve("build/classes/java/main")
                : root);

        try
        {
            try (var stream = Files.walk(start))
            {
                boolean hasV2 = stream.anyMatch(p ->
                        p.getFileName() != null &&
                                p.getFileName().toString().startsWith("dev.dsf.bpe.v2"));
                if (hasV2) return "v2";
            }

            try (var stream = Files.walk(start))
            {
                boolean hasV1 = stream.anyMatch(p ->
                        p.getFileName() != null &&
                                p.getFileName().toString().startsWith("dev.dsf.bpe.v1"));
                if (hasV1) return "v1";
            }
        }
        catch (IOException ignored)
        {

        }

        return null;
    }

    //  helpers

    private String findInServicesDir(Path servicesDir)
    {
        if (!Files.isDirectory(servicesDir)) return null;

        // Prefer v2 if both exist
        if (Files.isRegularFile(servicesDir.resolve("dev.dsf.bpe.v2.ProcessPluginDefinition")))
            return "v2";
        if (Files.isRegularFile(servicesDir.resolve("dev.dsf.bpe.v1.ProcessPluginDefinition")))
            return "v1";
        return null;
    }
}
