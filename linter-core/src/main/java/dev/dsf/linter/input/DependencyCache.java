package dev.dsf.linter.input;

import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.setup.MavenBuilder;
import dev.dsf.linter.util.maven.MavenUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Manages a persistent local cache for DSF stub dependencies.
 * <p>
 * This cache eliminates the need to download stub dependencies on every linter run.
 * Dependencies are downloaded once via Maven and cached in the user's home directory.
 * Subsequent runs use the cached JARs, significantly improving startup time.
 * </p>
 *
 * <h3>Cache Location:</h3>
 * <ul>
 *   <li>Windows: {@code %USERPROFILE%\.dsf-linter\dependency-cache}</li>
 *   <li>Unix/Mac: {@code ~/.dsf-linter/dependency-cache}</li>
 * </ul>
 *
 /**
 * <h3>Cached Dependencies:</h3>
 * <ul>
 *   <li>camunda-engine-7.20.0.jar</li>
 *   <li>dsf-bpe-process-api-v1-1.7.0.jar</li>
 *   <li>dsf-bpe-process-api-v2-2.0.0-M2.jar</li>  ← NEU!
 *   <li>hapi-fhir-base-5.1.0.jar</li>
 *   <li>httpclient-4.5.13.jar</li>
 *   <li>httpcore-4.4.13.jar</li>
 *   <li>+ transitive dependencies</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.1.0
 */
public class DependencyCache {

    private static final Path CACHE_BASE_DIR = Paths.get(
            System.getProperty("user.home"),
            ".dsf-linter"
    );

    private static final Path CACHE_STUB_DIR = CACHE_BASE_DIR.resolve("dependency-cache");

    private static final String STUB_POM_TEMPLATE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                    "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                    "  <modelVersion>4.0.0</modelVersion>\n" +
                    "  <groupId>dummy</groupId>\n" +
                    "  <artifactId>dummy</artifactId>\n" +
                    "  <version>1.0.0</version>\n" +
                    "  <dependencies>\n" +
                    "    <dependency>\n" +
                    "      <groupId>ca.uhn.hapi.fhir</groupId>\n" +
                    "      <artifactId>hapi-fhir-base</artifactId>\n" +
                    "      <version>5.1.0</version>\n" +
                    "    </dependency>\n" +
                    "    <dependency>\n" +
                    "      <groupId>org.apache.httpcomponents</groupId>\n" +
                    "      <artifactId>httpclient</artifactId>\n" +
                    "      <version>4.5.13</version>\n" +
                    "    </dependency>\n" +
                    "    <dependency>\n" +
                    "      <groupId>org.apache.httpcomponents</groupId>\n" +
                    "      <artifactId>httpcore</artifactId>\n" +
                    "      <version>4.4.13</version>\n" +
                    "    </dependency>\n" +
                    "    <dependency>\n" +
                    "      <groupId>org.camunda.bpm</groupId>\n" +
                    "      <artifactId>camunda-engine</artifactId>\n" +
                    "      <version>7.20.0</version>\n" +
                    "      <scope>provided</scope>\n" +
                    "    </dependency>\n" +
                    "    <dependency>\n" +
                    "      <groupId>dev.dsf</groupId>\n" +
                    "      <artifactId>dsf-bpe-process-api-v1</artifactId>\n" +
                    "      <version>1.7.0</version>\n" +
                    "      <scope>provided</scope>\n" +
                    "    </dependency>\n" +
//                    "    <dependency>\n" +
//                    "      <groupId>dev.dsf</groupId>\n" +
//                    "      <artifactId>dsf-bpe-process-api-v2</artifactId>\n" +
//                    "      <version>2.0.0-M2</version>\n" +
//                    "      <scope>provided</scope>\n" +
//                    "    </dependency>\n" +
                    "  </dependencies>\n" +
                    "</project>\n";

    private static final String[] REQUIRED_JARS = {
            "camunda-engine-7.20.0.jar",
            "dsf-bpe-process-api-v1-1.7.0.jar",
//          "dsf-bpe-process-api-v2-2.0.0-M2.jar",
            "hapi-fhir-base-5.1.0.jar",
            "httpclient-4.5.13.jar",
            "httpcore-4.4.13.jar"
    };

    private final Logger logger;
    private final MavenBuilder mavenBuilder;

    public DependencyCache(Logger logger) {
        this.logger = logger;
        this.mavenBuilder = new MavenBuilder();
    }

    /**
     * Gets the path to cached stub dependencies, downloading them if necessary.
     * <p>
     * This method checks if a valid cache exists. If found, it returns the cache path immediately.
     * If not found or invalid, it downloads dependencies via Maven once and caches them.
     * </p>
     *
     * @return path to directory containing cached dependency JARs
     * @throws IOException if cache operations or Maven download fails
     * @throws IllegalStateException if Maven is not available
     * @throws InterruptedException if Maven execution is interrupted
     */
    public Path getOrDownloadStubDependencies()
            throws IOException, IllegalStateException, InterruptedException {

        if (isCacheValid()) {
            logger.info("Using cached stub dependencies from: " + CACHE_STUB_DIR);
            logger.debug("Cache validation passed - all required JARs present");
            return CACHE_STUB_DIR;
        }

        logger.info("Stub dependency cache not found or invalid");
        logger.info("Downloading stub dependencies (first time only)...");
        logger.info("Cache location: " + CACHE_STUB_DIR);

        downloadAndCacheStubDependencies();

        logger.info("Successfully cached stub dependencies for future use");
        return CACHE_STUB_DIR;
    }

    /**
     * Clears the dependency cache by deleting all cached files.
     *
     * @throws IOException if deletion fails
     */
    public void clearCache() throws IOException {
        if (!Files.exists(CACHE_STUB_DIR)) {
            logger.info("Cache directory does not exist - nothing to clear");
            return;
        }

        logger.info("Clearing dependency cache: " + CACHE_STUB_DIR);

        try (Stream<Path> walk = Files.walk(CACHE_STUB_DIR)) {
            walk.sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            logger.warn("Failed to delete: " + path + " - " + e.getMessage());
                        }
                    });
        }

        logger.info("Dependency cache cleared successfully");
    }

    /**
     * Checks if the cache is valid by verifying that all required JARs exist.
     *
     * @return true if cache is valid, false otherwise
     */
    private boolean isCacheValid() {
        if (!Files.exists(CACHE_STUB_DIR) || !Files.isDirectory(CACHE_STUB_DIR)) {
            logger.debug("Cache directory does not exist: " + CACHE_STUB_DIR);
            return false;
        }

        // Check if all required JARs are present
        for (String requiredJar : REQUIRED_JARS) {
            Path jarPath = CACHE_STUB_DIR.resolve(requiredJar);
            if (!Files.exists(jarPath) || !Files.isRegularFile(jarPath)) {
                logger.debug("Missing required JAR in cache: " + requiredJar);
                return false;
            }
        }

        return true;
    }

    /**
     * Downloads stub dependencies via Maven and caches them.
     *
     * @throws IOException if file operations fail
     * @throws IllegalStateException if Maven is not available or build fails
     * @throws InterruptedException if Maven execution is interrupted
     */
    private void downloadAndCacheStubDependencies()
            throws IOException, IllegalStateException, InterruptedException {

        // Locate Maven executable
        String mavenExecutable = MavenUtil.locateMavenExecutable();
        if (mavenExecutable == null) {
            throw new IllegalStateException(
                    """
                            Maven executable not found. Maven is required for initial dependency download.
                            Please ensure Maven is installed and either:
                              - Available in PATH, or
                              - MAVEN_HOME environment variable is set correctly."""
            );
        }

        // Create temporary directory for Maven build
        Path tempDir = Files.createTempDirectory("dsf-stub-cache-");
        logger.debug("Using temporary directory for Maven build: " + tempDir);

        try {
            // Write stub POM to temp directory
            Path stubPomFile = tempDir.resolve("pom.xml");
            Files.writeString(stubPomFile, STUB_POM_TEMPLATE);

            logger.info("Downloading dependencies via Maven...");
            logger.info("This may take a moment (dependencies are downloaded only once)");

            // Execute Maven
            String[] mavenGoals = {
                    "-B",
                    "-q",
                    "dependency:copy-dependencies"
            };

            boolean success = mavenBuilder.buildProject(
                    tempDir.toFile(),
                    mavenExecutable,
                    mavenGoals
            );

            if (!success) {
                throw new IllegalStateException(
                        """
                                Failed to download stub dependencies via Maven.
                                Possible causes:
                                  - No internet connection
                                  - Maven repository configuration issues
                                  - Corrupted local Maven cache (~/.m2/repository)
                                
                                Try:
                                  1. Check your internet connection
                                  2. Run 'mvn dependency:purge-local-repository' to clear cache"""
                );
            }

            // Move dependencies from temp to cache
            Path tempDependencyDir = tempDir.resolve("target").resolve("dependency");
            if (!Files.exists(tempDependencyDir)) {
                throw new IllegalStateException(
                        "Maven completed but dependency directory not found: " + tempDependencyDir
                );
            }

            // Create cache directory
            Files.createDirectories(CACHE_STUB_DIR);

            // Copy JARs to cache
            try (Stream<Path> jars = Files.list(tempDependencyDir)) {
                jars.filter(p -> p.toString().endsWith(".jar"))
                        .forEach(jar -> {
                            try {
                                Path target = CACHE_STUB_DIR.resolve(jar.getFileName());
                                Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
                                logger.debug("Cached: " + jar.getFileName());
                            } catch (IOException e) {
                                logger.warn("Failed to cache JAR: " + jar.getFileName() + " - " + e.getMessage());
                            }
                        });
            }

            // Verify cache
            if (!isCacheValid()) {
                throw new IllegalStateException(
                        "Cache creation completed but validation failed. Some required JARs may be missing."
                );
            }

        } finally {
            // Cleanup temp directory
            deleteDirectoryRecursively(tempDir);
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param directory the directory to delete
     */
    private void deleteDirectoryRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            logger.debug("Could not delete: " + path + " - " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.warn("Failed to clean up temporary directory " + directory + ": " + e.getMessage());
        }
    }

    /**
     * Gets the cache directory path (for informational purposes).
     *
     * @return the cache directory path
     */
    public static Path getCacheDirectory() {
        return CACHE_STUB_DIR;
    }
}