package dev.dsf.linter.util.api;

import dev.dsf.linter.exception.MissingServiceRegistrationException;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;

import static dev.dsf.linter.constants.DsfApiConstants.V1_SERVICE_FILE;
import static dev.dsf.linter.constants.DsfApiConstants.V2_SERVICE_FILE;

/**
 * <h2>DSF API Version Detection Utility</h2>
 *
 * <p>
 * This utility class detects whether a given plugin project uses the
 * <strong>Data Sharing Framework (DSF)</strong> BPE API version {@code v1} or {@code v2}.
 * It works by inspecting known ServiceLoader registration directories or,
 * if necessary, by scanning class paths as a fallback.
 * </p>
 *
 * <h3>Detection Strategy</h3>
 * <ol>
 *   <li><strong>Primary:</strong> Search for ServiceLoader configuration files
 *       ({@code META-INF/services/dev.dsf.bpe.v1.ProcessPluginDefinition} or
 *       {@code ...v2...}) in common Maven/Gradle layouts.</li>
 *   <li><strong>Fallback:</strong> If ServiceLoader config files are missing,
 *       perform a directory walk to identify class files or resources with known DSF API package names.</li>
 * </ol>
 *
 * <h3>Usage Recommendations</h3>
 * <ul>
 *   <li>Use {@link #detect(Path)} for a non-throwing best-effort version detection.</li>
 *   <li>Use {@link #detectOrThrow(Path)} to enforce registration presence (e.g., in validation workflows).</li>
 * </ul>
 *
 * <p>
 * This class is stateless and safe for concurrent use.
 * </p>
 *
 * @see DetectedVersion
 * @see ApiVersion
 */
public final class ApiVersionDetector
{
    /**
     * List of known locations (relative to the project root) where ServiceLoader configuration files might reside.
     * These cover common layouts for Maven and Gradle builds.
     */
    private static final List<String> SERVICE_DIRS = List.of(
            "META-INF/services",                            // generic
            "src/main/resources/META-INF/services",         // source tree
            "target/classes/META-INF/services",             // Maven
            "build/resources/main/META-INF/services",       // Gradle resources
            "build/classes/java/main/META-INF/services"     // Gradle classes
    );

    private static final String V2_FILE = V2_SERVICE_FILE;
    private static final String V1_FILE = V1_SERVICE_FILE;

    /**
     * Attempts to detect the DSF BPE API version by scanning known ServiceLoader paths
     * for provider configuration files. If none are found, a fallback scan is performed.
     * This method does not throw and returns an empty {@link Optional} if the version
     * cannot be determined.
     *
     * @param root the project root directory to scan
     * @return an {@link Optional} containing the detected version and file path, or empty if none found
     */
    public Optional<DetectedVersion> detect(Path root) throws MissingServiceRegistrationException
    {
        // Step 1: ServiceLoader-based detection
        for (String rel : SERVICE_DIRS)
        {
            Path dir = root.resolve(rel);
            if (Files.isRegularFile(dir.resolve(V2_FILE)))
                return Optional.of(new DetectedVersion(ApiVersion.V2, dir.resolve(V2_FILE), DetectionSource.SERVICE_FILE));
            if (Files.isRegularFile(dir.resolve(V1_FILE)))
                return Optional.of(new DetectedVersion(ApiVersion.V1, dir.resolve(V1_FILE), DetectionSource.SERVICE_FILE));
        }

        // Step 2: Fallback scan for class/package names
        return detectByFallbackOptional(root);
    }

    /**
     * Detects the DSF API version and throws a {@link MissingServiceRegistrationException}
     * if no ServiceLoader registration file is found in any known location.
     *
     * <p>This method should be used when ServiceLoader registration is considered mandatory.</p>
     *
     * @param root the project root directory to scan
     * @return the detected version with its origin
     * @throws MissingServiceRegistrationException if no valid ServiceLoader file is found
     */
    public DetectedVersion detectOrThrow(Path root) throws MissingServiceRegistrationException
    {
        return detect(root).orElseThrow(() ->
                new MissingServiceRegistrationException("No ServiceLoader registration found under META-INF/services"));
    }

    /**
     * Performs a fallback scan by walking the file tree below common compiled output directories
     * (e.g., {@code target/classes} or {@code build/classes/java/main}) to detect files
     * that reference DSF BPE API package names.
     *
     * <p>This method prefers API version {@code v2} over {@code v1} if both are detected.</p>
     *
     * @param root the project root directory to scan
     * @return an {@link Optional} containing the detected version and path if found; otherwise empty
     */
    private Optional<DetectedVersion> detectByFallbackOptional(Path root) throws MissingServiceRegistrationException
    {
        // Prefer compiled outputs; otherwise walk from project root
        Path start = Files.isDirectory(root.resolve("target/classes"))
                ? root.resolve("target/classes")
                : (Files.isDirectory(root.resolve("build/classes/java/main"))
                ? root.resolve("build/classes/java/main")
                : root);

        try (var stream = Files.walk(start))
        {
            final boolean[] seen = new boolean[2]; // [0]=v1, [1]=v2
            final Path[] firstSeen = new Path[2];

            stream.forEach(p ->
            {
                String name = p.getFileName() != null ? p.getFileName().toString() : "";
                if (!seen[1] && name.startsWith("dev.dsf.bpe.v2"))
                {
                    seen[1] = true;
                    firstSeen[1] = p;
                }
                else if (!seen[0] && name.startsWith("dev.dsf.bpe.v1"))
                {
                    seen[0] = true;
                    firstSeen[0] = p;
                }
            });

            if (seen[1])
                return Optional.of(new DetectedVersion(ApiVersion.V2, firstSeen[1], DetectionSource.FALLBACK_SCAN));
            if (seen[0])
                return Optional.of(new DetectedVersion(ApiVersion.V1, firstSeen[0], DetectionSource.FALLBACK_SCAN));

            return Optional.empty();
        }
        catch (IOException e)
        {
            detectOrThrow(root);
            return Optional.empty();
        }
    }
}
