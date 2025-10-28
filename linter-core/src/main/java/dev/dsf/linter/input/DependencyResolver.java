package dev.dsf.linter.input;

import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.setup.MavenBuilder;
import dev.dsf.linter.util.maven.MavenUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Resolves DSF plugin dependencies by generating stub POMs and/or using project POMs.
 * <p>
 * This class provides dependency resolution for linter projects, ensuring that
 * all required DSF and Camunda dependencies are available for the ClassLoader.
 * It supports two modes:
 * <ul>
 *   <li><b>Stub dependencies:</b> Always executed - provides minimal DSF API dependencies</li>
 *   <li><b>Project dependencies:</b> Conditionally executed - loads project-specific dependencies from pom.xml</li>
 * </ul>
 * </p>
 *
 * <h3>Strategy (when --mvn is NOT specified):</h3>
 * <ol>
 *   <li>Always: Generate minimal stub pom.xml with fixed DSF dependencies</li>
 *   <li>Additionally (if pom.xml exists): Load dependencies from project pom.xml</li>
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

    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
            "<dependency>.*?</dependency>",
            Pattern.DOTALL
    );

    private static final Pattern MAVEN_COMPILER_SOURCE_PATTERN = Pattern.compile(
            "<maven\\.compiler\\.source>(.*?)</maven\\.compiler\\.source>"
    );

    private static final Pattern MAVEN_COMPILER_TARGET_PATTERN = Pattern.compile(
            "<maven\\.compiler\\.target>(.*?)</maven\\.compiler\\.target>"
    );

    private static final Pattern MAVEN_COMPILER_RELEASE_PATTERN = Pattern.compile(
            "<maven\\.compiler\\.release>(.*?)</maven\\.compiler\\.release>"
    );

    private final Logger logger;
    private final MavenBuilder mavenBuilder;

    public DependencyResolver(Logger logger) {
        this.logger = logger;
        this.mavenBuilder = new MavenBuilder();
    }

    /**
     * Resolves merged dependencies by combining stub dependencies with project dependencies.
     * <p>
     * This method creates a merged POM containing both stub dependencies (DSF, Camunda, etc.)
     * and project-specific dependencies extracted from the project's effective POM.
     * It then compiles sources and copies all dependencies to target/dependency.
     * </p>
     *
     * @param projectDir the project directory containing pom.xml
     * @throws IOException if POM creation, file operations, or Maven execution fails
     * @throws IllegalStateException if Maven is not available or dependency resolution fails
     * @throws InterruptedException if Maven execution is interrupted
     */
    public void resolveMergedDependencies(Path projectDir)
            throws IOException, IllegalStateException, InterruptedException {

        logger.info("Resolving merged dependencies (stub + project dependencies)...");

        Path pomFile = projectDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            throw new IllegalStateException(
                    "Cannot resolve merged dependencies: pom.xml not found at " + pomFile
            );
        }

        String mavenExecutable = locateMavenOrThrow();

        logger.info("Extracting project configuration from effective POM...");
        String effectivePomContent = generateEffectivePom(projectDir, mavenExecutable);

        Set<String> projectDependencies = parseDependenciesFromPom(effectivePomContent);
        logger.info("Found " + projectDependencies.size() + " project dependencies");

        String javaVersion = extractJavaVersion(effectivePomContent);
        if (javaVersion != null) {
            logger.info("Detected Java version: " + javaVersion);
        } else {
            javaVersion = "17";
            logger.warn("Could not detect Java version from POM, using fallback: Java " + javaVersion);
        }

        String mergedPom = createMergedPom(projectDependencies, javaVersion);

        Path mergedPomFile = projectDir.resolve("merged-stub-pom.xml");
        Files.writeString(mergedPomFile, mergedPom,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.info("Created merged POM: " + mergedPomFile.getFileName());
        logger.debug("Merged POM content:\n" + mergedPom);

        logger.info("Compiling sources and downloading all dependencies...");
        logger.info("This may take a moment on first run (dependencies are cached locally)");

        String[] mavenGoals = {
                "-B",
                "-q",
                "-DskipTests",
                "-f", mergedPomFile.getFileName().toString(),
                "compile",
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
                            Failed to resolve merged dependencies via Maven.
                            Possible causes:
                              - The project is not set up correctly as a DSF Process Plugin project
                              - You try to lint a multi-module project, but currently, the linter does not fully support that
                              - Invalid dependencies in project POM
                            
                            Try:
                              1. Running the linter again with the --mvn option (for more information, see the README.md file)
                              2. Build a JAR for the project you want to lint and pass that JAR to the linter
                              3. Verify project POM is valid"""
            );
        }

        verifyDependenciesDownloaded(projectDir);
        logger.info("Successfully resolved merged dependencies");
    }

    /**
     * Generates the effective POM for the project.
     * <p>
     * Uses Maven's help:effective-pom goal to generate the fully resolved POM
     * including inherited dependencies and properties from parent POMs.
     * </p>
     *
     * @param projectDir the project directory
     * @param mavenExecutable the Maven executable path
     * @return the effective POM XML content
     * @throws IOException if Maven execution fails
     * @throws InterruptedException if Maven execution is interrupted
     */
    private String generateEffectivePom(Path projectDir, String mavenExecutable)
            throws IOException, InterruptedException {

        Path tempEffectivePom = projectDir.resolve("effective-pom.xml");

        String[] effectivePomGoals = {
                "-B",
                "-q",
                "help:effective-pom",
                "-Doutput=" + tempEffectivePom.toAbsolutePath()
        };

        boolean success = mavenBuilder.buildProject(
                projectDir.toFile(),
                mavenExecutable,
                effectivePomGoals
        );

        if (!success) {
            throw new IOException("Failed to generate effective POM for project");
        }

        if (!Files.exists(tempEffectivePom)) {
            throw new IOException("Effective POM was not created at: " + tempEffectivePom);
        }

        String effectivePomContent = Files.readString(tempEffectivePom, StandardCharsets.UTF_8);

        Files.deleteIfExists(tempEffectivePom);

        return effectivePomContent;
    }

    /**
     * Parses dependency blocks from a POM XML string.
     * Extracts dependencies from the effective POM, excluding test and provided scope dependencies
     * that are not already in the stub dependencies.
     *
     * @param pomContent the POM XML content
     * @return set of dependency XML blocks
     */
    private Set<String> parseDependenciesFromPom(String pomContent) {
        Set<String> dependencies = new HashSet<>();

        int dependenciesStart = pomContent.indexOf("<dependencies>");
        int dependenciesEnd = pomContent.indexOf("</dependencies>");

        if (dependenciesStart == -1 || dependenciesEnd == -1) {
            logger.debug("No <dependencies> section found in effective POM");
            return dependencies;
        }

        String dependenciesSection = pomContent.substring(dependenciesStart, dependenciesEnd + "</dependencies>".length());

        Matcher matcher = DEPENDENCY_PATTERN.matcher(dependenciesSection);

        while (matcher.find()) {
            String dependency = matcher.group().trim();

            if (shouldIncludeDependency(dependency)) {
                dependencies.add(dependency);
            }
        }

        return dependencies;
    }

    /**
     * Determines if a dependency should be included in the merged POM.
     *
     * @param dependency the dependency XML block
     * @return true if the dependency should be included
     */
    private boolean shouldIncludeDependency(String dependency) {
        if (dependency.contains("<scope>test</scope>")) {
            return false;
        }

        return !isDuplicateOfStubDependency(dependency);
    }

    /**
     * Extracts Java version from the effective POM.
     * Checks multiple locations in the following order:
     * 1. maven.compiler.release property
     * 2. maven.compiler.source property
     * 3. maven.compiler.target property
     * 4. maven-compiler-plugin configuration (release)
     * 5. maven-compiler-plugin configuration (source)
     *
     * @param pomContent the effective POM XML content
     * @return the Java version or null if not found
     */
    private String extractJavaVersion(String pomContent) {
        // Check properties first
        Matcher releaseMatcher = MAVEN_COMPILER_RELEASE_PATTERN.matcher(pomContent);
        if (releaseMatcher.find()) {
            return releaseMatcher.group(1).trim();
        }

        Matcher sourceMatcher = MAVEN_COMPILER_SOURCE_PATTERN.matcher(pomContent);
        if (sourceMatcher.find()) {
            return sourceMatcher.group(1).trim();
        }

        Matcher targetMatcher = MAVEN_COMPILER_TARGET_PATTERN.matcher(pomContent);
        if (targetMatcher.find()) {
            return targetMatcher.group(1).trim();
        }

        // Check maven-compiler-plugin configuration
        return extractFromMavenCompilerPlugin(pomContent);
    }

    /**
     * Extracts Java version from maven-compiler-plugin configuration block.
     *
     * @param pomContent the effective POM XML content
     * @return the Java version or null if not found
     */
    private String extractFromMavenCompilerPlugin(String pomContent) {
        Pattern compilerPluginPattern = Pattern.compile(
                "<plugin>.*?<artifactId>maven-compiler-plugin</artifactId>.*?<configuration>(.*?)</configuration>.*?</plugin>",
                Pattern.DOTALL
        );

        Matcher pluginMatcher = compilerPluginPattern.matcher(pomContent);
        if (pluginMatcher.find()) {
            String configSection = pluginMatcher.group(1);

            Pattern releasePattern = Pattern.compile("<release>(.*?)</release>");
            Matcher releaseMatcher = releasePattern.matcher(configSection);
            if (releaseMatcher.find()) {
                return releaseMatcher.group(1).trim();
            }

            Pattern sourcePattern = Pattern.compile("<source>(.*?)</source>");
            Matcher sourceMatcher = sourcePattern.matcher(configSection);
            if (sourceMatcher.find()) {
                return sourceMatcher.group(1).trim();
            }
        }

        return null;
    }

    /**
     * Checks if a dependency is already present in stub dependencies.
     *
     * @param dependency the dependency XML block
     * @return true if dependency is already in stub POM
     */
    private boolean isDuplicateOfStubDependency(String dependency) {
        return STUB_POM_TEMPLATE.contains(extractArtifactId(dependency));
    }

    /**
     * Extracts artifactId from a dependency XML block.
     *
     * @param dependency the dependency XML
     * @return the artifactId or empty string if not found
     */
    private String extractArtifactId(String dependency) {
        Pattern artifactIdPattern = Pattern.compile("<artifactId>(.*?)</artifactId>");
        Matcher matcher = artifactIdPattern.matcher(dependency);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Creates a merged POM combining stub dependencies and project dependencies.
     * Optionally includes Java version configuration from the original project.
     *
     * @param projectDependencies set of project dependency XML blocks
     * @param javaVersion the Java version to use (can be null)
     * @return the merged POM XML content
     */
    private String createMergedPom(Set<String> projectDependencies, String javaVersion) {
        StringBuilder mergedPom = new StringBuilder();

        mergedPom.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        mergedPom.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        mergedPom.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        mergedPom.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n");
        mergedPom.append("                             http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        mergedPom.append("  <modelVersion>4.0.0</modelVersion>\n");
        mergedPom.append("  <groupId>dummy</groupId>\n");
        mergedPom.append("  <artifactId>dummy</artifactId>\n");
        mergedPom.append("  <version>1.0.0</version>\n\n");

        if (javaVersion != null) {
            mergedPom.append("  <properties>\n");
            mergedPom.append("    <maven.compiler.source>").append(javaVersion).append("</maven.compiler.source>\n");
            mergedPom.append("    <maven.compiler.target>").append(javaVersion).append("</maven.compiler.target>\n");
            mergedPom.append("    <maven.compiler.release>").append(javaVersion).append("</maven.compiler.release>\n");
            mergedPom.append("  </properties>\n\n");
        }

        mergedPom.append("  <dependencies>\n");

        mergedPom.append("    <!-- Stub dependencies (DSF API, Camunda, HTTP clients) -->\n");
        mergedPom.append("    <dependency>\n");
        mergedPom.append("      <groupId>ca.uhn.hapi.fhir</groupId>\n");
        mergedPom.append("      <artifactId>hapi-fhir-base</artifactId>\n");
        mergedPom.append("      <version>5.1.0</version>\n");
        mergedPom.append("    </dependency>\n");
        mergedPom.append("    <dependency>\n");
        mergedPom.append("      <groupId>org.apache.httpcomponents</groupId>\n");
        mergedPom.append("      <artifactId>httpclient</artifactId>\n");
        mergedPom.append("      <version>4.5.13</version>\n");
        mergedPom.append("    </dependency>\n");
        mergedPom.append("    <dependency>\n");
        mergedPom.append("      <groupId>org.apache.httpcomponents</groupId>\n");
        mergedPom.append("      <artifactId>httpcore</artifactId>\n");
        mergedPom.append("      <version>4.4.13</version>\n");
        mergedPom.append("    </dependency>\n");
        mergedPom.append("    <!-- Camunda BPM engine (required for JavaDelegate linting) -->\n");
        mergedPom.append("    <dependency>\n");
        mergedPom.append("      <groupId>org.camunda.bpm</groupId>\n");
        mergedPom.append("      <artifactId>camunda-engine</artifactId>\n");
        mergedPom.append("      <version>7.20.0</version>\n");
        mergedPom.append("      <scope>provided</scope>\n");
        mergedPom.append("    </dependency>\n");
        mergedPom.append("    <!-- DSF BPE API v1 & v2 (for all plugin versions) -->\n");
        mergedPom.append("    <dependency>\n");
        mergedPom.append("      <groupId>dev.dsf</groupId>\n");
        mergedPom.append("      <artifactId>dsf-bpe-process-api-v1</artifactId>\n");
        mergedPom.append("      <version>1.7.0</version>\n");
        mergedPom.append("      <scope>provided</scope>\n");
        mergedPom.append("    </dependency>\n");
        mergedPom.append("    <dependency>\n");
        mergedPom.append("      <groupId>dev.dsf</groupId>\n");
        mergedPom.append("      <artifactId>dsf-bpe-process-api-v2</artifactId>\n");
        mergedPom.append("      <version>2.0.0-M2</version>\n");
        mergedPom.append("      <scope>provided</scope>\n");
        mergedPom.append("    </dependency>\n");

        if (!projectDependencies.isEmpty()) {
            mergedPom.append("    <!-- Project dependencies from effective POM -->\n");
            for (String dependency : projectDependencies) {
                mergedPom.append("    ").append(dependency).append("\n");
            }
        }

        mergedPom.append("  </dependencies>\n");
        mergedPom.append("</project>\n");

        return mergedPom.toString();
    }

    /**
     * Resolves stub DSF plugin dependencies by creating a minimal POM and
     * downloading dependencies via Maven.
     * <p>
     * This method should ALWAYS be called when --mvn is not specified,
     * regardless of input type (directory, git, jar).
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