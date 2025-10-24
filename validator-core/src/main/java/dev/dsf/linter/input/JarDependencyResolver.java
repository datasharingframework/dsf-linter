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
 * Resolves DSF plugin dependencies for JAR files by generating a minimal stub POM.
 * <p>
 * When validating a standalone JAR file (thin JAR without dependencies), this class
 * creates a minimal Maven project structure with a stub POM containing all required
 * DSF and Camunda dependencies. Maven is then used to download these dependencies,
 * making them available for the ClassLoader.
 * </p>
 *
 * <h3>Strategy:</h3>
 * <ol>
 *   <li>Generate minimal pom.xml with fixed DSF dependencies</li>
 *   <li>Execute: mvn dependency:copy-dependencies</li>
 *   <li>Dependencies are placed in target/dependency</li>
 *   <li>ProjectClassLoaderFactory automatically adds them to classpath</li>
 * </ol>
 *
 * <h3>Fixed Dependencies:</h3>
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
 * @since 3.0.0
 */
public class JarDependencyResolver {

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
                    "    <!-- Camunda BPM engine (required for JavaDelegate validation) -->\n" +
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
                    "    <dependency>\n" +
                    "      <groupId>dev.dsf</groupId>\n" +
                    "      <artifactId>dsf-bpe-process-api-v2</artifactId>\n" +
                    "      <version>2.0.0-M2</version>\n" +
                    "      <scope>provided</scope>\n" +
                    "    </dependency>\n" +
                    "  </dependencies>\n" +
                    "</project>\n";

    private final Logger logger;
    private final MavenBuilder mavenBuilder;

    public JarDependencyResolver(Logger logger) {
        this.logger = logger;
        this.mavenBuilder = new MavenBuilder();
    }

    /**
     * Resolves DSF plugin dependencies for a JAR by creating a stub POM and
     * downloading dependencies via Maven.
     *
     * @param extractionDir the directory containing the extracted JAR
     * @throws IOException if POM creation or file operations fail
     * @throws IllegalStateException if Maven is not available or dependency resolution fails
     * @throws InterruptedException if Maven execution is interrupted
     */
    public void resolveDependencies(Path extractionDir)
            throws IOException, IllegalStateException, InterruptedException {

        logger.info("Resolving DSF plugin dependencies for JAR validation...");

        // Step 1: Locate Maven executable
        String mavenExecutable = MavenUtil.locateMavenExecutable();
        if (mavenExecutable == null) {
            throw new IllegalStateException(
                    "Maven executable not found. Maven is required for JAR validation.\n" +
                            "Please ensure Maven is installed and either:\n" +
                            "  - Available in PATH, or\n" +
                            "  - MAVEN_HOME environment variable is set correctly.\n" +
                            "\n" +
                            "Alternatively, validate the project directly instead of the JAR:\n" +
                            "  dsf-validator -p /path/to/project --mvn --html"
            );
        }

        // Step 2: Create stub pom.xml
        Path pomFile = extractionDir.resolve("pom.xml");
        Files.writeString(pomFile, STUB_POM_TEMPLATE,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.info("Created stub POM with DSF dependencies: " + pomFile.getFileName());

        // Step 3: Execute Maven to download dependencies
        logger.info("Downloading DSF plugin dependencies via Maven...");
        logger.info("This may take a moment on first run (dependencies are cached locally)");

        String[] mavenGoals = {
                "-B",                           // Batch mode (non-interactive)
                "-q",                           // Quiet (minimal output)
                "dependency:copy-dependencies"  // Download dependencies to target/dependency
        };

        boolean success = mavenBuilder.buildProject(
                extractionDir.toFile(),
                mavenExecutable,
                mavenGoals
        );

        if (!success) {
            throw new IllegalStateException(
                    "Failed to resolve DSF dependencies via Maven.\n" +
                            "Possible causes:\n" +
                            "  - No internet connection (dependencies cannot be downloaded)\n" +
                            "  - Maven repository configuration issues\n" +
                            "  - Corrupted local Maven cache (~/.m2/repository)\n" +
                            "\n" +
                            "Try:\n" +
                            "  1. Check your internet connection\n" +
                            "  2. Run 'mvn dependency:purge-local-repository' to clear cache\n" +
                            "  3. Validate the project directly: -p /path/to/project --mvn"
            );
        }

        // Step 4: Verify dependencies were downloaded
        Path dependencyDir = extractionDir.resolve("target").resolve("dependency");
        if (!Files.exists(dependencyDir) || !Files.isDirectory(dependencyDir)) {
            throw new IllegalStateException(
                    "Maven dependency resolution completed but target/dependency directory not found.\n" +
                            "Expected location: " + dependencyDir.toAbsolutePath()
            );
        }

        // Use try-with-resources for Stream
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

        logger.info("Successfully resolved " + jarCount + " dependency JARs");
        logger.debug("Dependencies available at: " + dependencyDir.toAbsolutePath());
    }
}