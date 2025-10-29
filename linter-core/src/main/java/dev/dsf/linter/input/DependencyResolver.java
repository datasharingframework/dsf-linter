package dev.dsf.linter.input;

import dev.dsf.linter.logger.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Resolves DSF plugin dependencies using cached stub dependencies.
 * <p>
 * This class provides minimal dependency resolution for linter projects,
 * ensuring that all required DSF and Camunda dependencies are available
 * for the ClassLoader via a persistent cache system.
 * </p>
 *
 * <h3>Strategy:</h3>
 * <ol>
 *   <li>Check if stub dependencies exist in user's cache (~/.dsf-linter/dependency-cache)</li>
 *   <li>If cached: Copy cached JARs to project's target/dependency</li>
 *   <li>If not cached: Download via Maven once and cache for future use</li>
 *   <li>ProjectClassLoaderFactory automatically adds them to classpath</li>
 * </ol>
 *
 /**
 * <h3>Cached Stub Dependencies:</h3>
 * <ul>
 *   <li>hapi-fhir-base 5.1.0</li>
 *   <li>httpclient 4.5.13</li>
 *   <li>httpcore 4.4.13</li>
 *   <li>camunda-engine 7.20.0 (provided scope)</li>
 *   <li>dsf-bpe-process-api-v1 1.7.0 (provided scope)</li>
 *   <li>dsf-bpe-process-api-v2 2.0.0-M2 (provided scope)</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class DependencyResolver {

    private final Logger logger;
    private final DependencyCache dependencyCache;

    public DependencyResolver(Logger logger) {
        this.logger = logger;
        this.dependencyCache = new DependencyCache(logger);
    }

    /**
     * Resolves stub DSF plugin dependencies using the cache system.
     * <p>
     * This method retrieves stub dependencies from cache (downloading once if needed)
     * and copies them to the project's target/dependency directory for the ClassLoader.
     * </p>
     * <p>
     * This method should be called for JAR inputs or when --mvn is not specified.
     * </p>
     *
     * @param projectDir the project directory
     * @throws IOException if file operations fail
     * @throws IllegalStateException if Maven is not available or dependency resolution fails
     * @throws InterruptedException if Maven execution is interrupted
     */
    public void resolveStubDependencies(Path projectDir)
            throws IOException, IllegalStateException, InterruptedException {

        logger.info("Resolving minimal DSF plugin dependencies...");

        // Get cached stub dependencies (downloads once if needed)
        Path cachedStubDir = dependencyCache.getOrDownloadStubDependencies();

        // Create target/dependency in project directory
        Path projectDependencyDir = projectDir.resolve("target").resolve("dependency");
        Files.createDirectories(projectDependencyDir);

        // Copy cached JARs to project directory
        logger.debug("Copying cached dependencies to project directory...");
        copyCachedDependencies(cachedStubDir, projectDependencyDir);

        verifyDependenciesAvailable(projectDir);
        logger.info("Successfully resolved stub dependencies");
    }

    /**
     * Copies cached dependency JARs to the project's target/dependency directory.
     *
     * @param cacheDir the cache directory containing JARs
     * @param targetDir the target directory in the project
     * @throws IOException if copy operations fail
     */
    private void copyCachedDependencies(Path cacheDir, Path targetDir) throws IOException {
        int copiedCount = 0;

        try (Stream<Path> jars = Files.list(cacheDir)) {
            for (Path jar : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                Path target = targetDir.resolve(jar.getFileName());
                Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
                copiedCount++;
                logger.debug("Copied: " + jar.getFileName());
            }
        }

        logger.debug("Copied " + copiedCount + " dependency JARs to " + targetDir);
    }

    /**
     * Verifies that dependencies are available in the project directory.
     *
     * @param projectDir the project directory
     * @throws IllegalStateException if dependency directory is missing or empty
     * @throws IOException if file operations fail
     */
    private void verifyDependenciesAvailable(Path projectDir) throws IllegalStateException, IOException {
        Path dependencyDir = projectDir.resolve("target").resolve("dependency");
        if (!Files.exists(dependencyDir) || !Files.isDirectory(dependencyDir)) {
            throw new IllegalStateException(
                    "Dependency resolution completed but target/dependency directory not found.\n" +
                            "Expected location: " + dependencyDir.toAbsolutePath()
            );
        }

        long jarCount;
        try (Stream<Path> stream = Files.list(dependencyDir)) {
            jarCount = stream
                    .filter(p -> p.toString().endsWith(".jar"))
                    .count();
        }

        if (jarCount == 0) {
            throw new IllegalStateException(
                    "No dependency JARs found in target/dependency.\n" +
                            "This indicates a cache or file system issue."
            );
        }

        logger.debug("Verified " + jarCount + " dependency JARs in " + dependencyDir.toAbsolutePath());
    }

    /**
     * Clears the dependency cache.
     * This is useful for troubleshooting or forcing a re-download of dependencies.
     *
     * @throws IOException if cache clearing fails
     */
    public void clearCache() throws IOException {
        dependencyCache.clearCache();
    }

    /**
     * Gets the cache directory path.
     *
     * @return the cache directory path
     */
    public static Path getCacheDirectory() {
        return DependencyCache.getCacheDirectory();
    }
}