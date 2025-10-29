package dev.dsf.linter.input;

import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.setup.MavenBuilder;
import dev.dsf.linter.util.maven.MavenUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

/**
 * Resolves DSF plugin dependencies by generating stub POMs.
 * <p>
 * This class provides minimal dependency resolution for linter projects,
 * ensuring that all required DSF and Camunda dependencies are available
 * for the ClassLoader.
 * </p>
 *
 * <h3>Strategy:</h3>
 * <ol>
 *   <li>Generate minimal stub pom.xml with fixed DSF dependencies</li>
 *   <li>Execute: mvn dependency:copy-dependencies</li>
 *   <li>Dependencies are placed in target/dependency</li>
 *   <li>ProjectClassLoaderFactory automatically adds them to classpath</li>
 * </ol>
 *
 * <h3>Fixed Stub Dependencies:</h3>
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

    private static final String STUB_POM_TEMPLATE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                    "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" +
                    "                             http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
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
                    "    <!-- Camunda BPM engine (required for JavaDelegate linting) -->\n" +
                    "    <dependency>\n" +
                    "      <groupId>org.camunda.bpm</groupId>\n" +
                    "      <artifactId>camunda-engine</artifactId>\n" +
                    "      <version>7.20.0</version>\n" +
                    "      <scope>provided</scope>\n" +
                    "    </dependency>\n" +
                    "    <!-- DSF BPE API v1 & v2 (for all plugin versions) -->\n" +
                    "    <dependency>\n" +
                    "      <groupId>dev.dsf</groupId>\n" +
                    "      <artifactId>dsf-bpe-process-api-v1</artifactId>\n" +
                    "      <version>1.7.0</version>\n" +
                    "      <scope>provided</scope>\n" +
                    "    </dependency>\n" +
                    "  </dependencies>\n" +
                    "</project>\n";

    private final Logger logger;
    private final MavenBuilder mavenBuilder;

    public DependencyResolver(Logger logger) {
        this.logger = logger;
        this.mavenBuilder = new MavenBuilder();
    }

    /**
     * Resolves stub DSF plugin dependencies by creating a minimal POM and
     * downloading dependencies via Maven.
     * <p>
     * This method should be called for JAR inputs or when --mvn is not specified.
     * </p>
     *
     * @param projectDir the project directory
     * @throws IOException if POM creation or file operations fail
     * @throws IllegalStateException if Maven is not available or dependency resolution fails
     * @throws InterruptedException if Maven execution is interrupted
     */
    public void resolveStubDependencies(Path projectDir)
            throws IOException, IllegalStateException, InterruptedException {

        logger.info("Resolving minimal DSF plugin dependencies (stub POM)...");

        String mavenExecutable = locateMavenOrThrow();

        Path stubPomFile = projectDir.resolve("stub-pom.xml");
        Files.writeString(stubPomFile, STUB_POM_TEMPLATE,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.info("Created stub POM with minimal DSF dependencies: " + stubPomFile.getFileName());

        logger.info("Downloading minimal DSF plugin dependencies via Maven...");
        logger.info("This may take a moment on first run (dependencies are cached locally)");

        String[] mavenGoals = {
                "-B",
                "-q",
                "-f", stubPomFile.getFileName().toString(),
                "dependency:copy-dependencies"
        };

        boolean success = mavenBuilder.buildProject(
                projectDir.toFile(),
                mavenExecutable,
                mavenGoals
        );

        if (!success) {
            throw new IllegalStateException(
                    """
                            Failed to resolve stub DSF dependencies via Maven.
                            Possible causes:
                              - No internet connection (dependencies cannot be downloaded)
                              - Maven repository configuration issues
                              - Corrupted local Maven cache (~/.m2/repository)
                            
                            Try:
                              1. Check your internet connection
                              2. Run 'mvn dependency:purge-local-repository' to clear cache"""
            );
        }

        verifyDependenciesDownloaded(projectDir);
        logger.info("Successfully resolved stub dependencies");
    }

    /**
     * Locates the Maven executable or throws an exception.
     *
     * @return the Maven executable path
     * @throws IllegalStateException if Maven is not found
     */
    private String locateMavenOrThrow() throws IllegalStateException {
        String mavenExecutable = MavenUtil.locateMavenExecutable();
        if (mavenExecutable == null) {
            throw new IllegalStateException(
                    """
                            Maven executable not found. Maven is required for dependency resolution.
                            Please ensure Maven is installed and either:
                              - Available in PATH, or
                              - MAVEN_HOME environment variable is set correctly."""
            );
        }
        return mavenExecutable;
    }

    /**
     * Verifies that dependencies were successfully downloaded.
     *
     * @param projectDir the project directory
     * @throws IllegalStateException if dependency directory is missing or empty
     * @throws IOException if file operations fail
     */
    private void verifyDependenciesDownloaded(Path projectDir) throws IllegalStateException, IOException {
        Path dependencyDir = projectDir.resolve("target").resolve("dependency");
        if (!Files.exists(dependencyDir) || !Files.isDirectory(dependencyDir)) {
            throw new IllegalStateException(
                    "Maven dependency resolution completed but target/dependency directory not found.\n" +
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
                    "No dependency JARs found in target/dependency after Maven execution.\n" +
                            "This indicates a Maven configuration or network issue."
            );
        }

        logger.debug("Verified " + jarCount + " dependency JARs in " + dependencyDir.toAbsolutePath());
    }
}