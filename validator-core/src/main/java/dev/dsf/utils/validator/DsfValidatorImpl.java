package dev.dsf.utils.validator;

import dev.dsf.utils.validator.build.MavenBuilder;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.plugin.PluginDefinitionDiscovery;
import dev.dsf.utils.validator.plugin.PluginDefinitionDiscovery.PluginAdapter;
import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.fhir.FhirResourceValidator;
import dev.dsf.utils.validator.util.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.stream.Collectors;

import static dev.dsf.utils.validator.classloading.ProjectClassLoaderFactory.createProjectClassLoader;

/**
 * <h1>DSF Validator Implementation</h1>
 *
 * <p>
 * This class provides the core implementation of the {@link DsfValidator} interface, delivering a comprehensive,
 * definition-driven validation framework for DSF (Data Sharing Framework) compliant projects. The validator
 * employs a sophisticated approach that uses the project's {@code ProcessPluginDefinition} as the authoritative
 * source for resource references, ensuring targeted validation of only relevant resources while maintaining
 * comprehensive coverage and early error detection.
 * </p>
 *
 * <h2>Architecture Overview</h2>
 * <p>
 * The validator implements a multi-phase validation architecture designed around the following core principles:
 * </p>
 * <ul>
 * <li><strong>Definition-Driven Validation:</strong> Uses ProcessPluginDefinition as the single source of truth
 *     for resource references, eliminating unnecessary file scanning and ensuring validation precision.</li>
 * <li><strong>Layout Adaptability:</strong> Automatically detects and adapts to different project layouts,
 *     supporting both standard Maven projects and exploded plugin deployments (CI environments).</li>
 * <li><strong>Fail-Fast Error Handling:</strong> Implements strict error handling that terminates validation
 *     early for critical failures while continuing for recoverable issues.</li>
 * <li><strong>Comprehensive Reporting:</strong> Generates structured, hierarchical JSON reports with detailed
 *     categorization and aggregation for all validation results.</li>
 * </ul>
 *
 * <h2>Validation Workflow</h2>
 * <p>
 * The validation process follows a sequential, multi-phase workflow designed for maximum reliability and
 * comprehensive feedback:
 * </p>
 * <ol>
 * <li><strong>Project Layout Detection:</strong>
 *     <ul>
 *     <li>Detects standard Maven layout (presence of {@code pom.xml} file)</li>
 *     <li>Identifies exploded plugin layout (CI environment deployment)</li>
 *     <li>Adapts resource resolution strategy based on detected layout</li>
 *     </ul>
 * </li>
 * <li><strong>Build and Classpath Preparation:</strong>
 *     <ul>
 *     <li>Standard layout: Executes Maven build with {@code clean package dependency:copy-dependencies}</li>
 *     <li>Exploded layout: Creates custom ClassLoader from project directory</li>
 *     <li>Ensures all runtime dependencies are available for reflection operations</li>
 *     </ul>
 * </li>
 * <li><strong>Plugin Definition Discovery:</strong>
 *     <ul>
 *     <li>Discovers unique {@code ProcessPluginDefinition} implementation using {@link PluginDefinitionDiscovery}</li>
 *     <li>Validates that exactly one definition exists (fails if zero or multiple found)</li>
 *     <li>Extracts resource references for targeted validation</li>
 *     </ul>
 * </li>
 * <li><strong>Resource Root Detection:</strong>
 *     <ul>
 *     <li>Determines the effective resource root directory based on plugin's code source</li>
 *     <li>Handles multi-module Maven projects and Gradle builds correctly</li>
 *     <li>Falls back to appropriate defaults when code source is unavailable</li>
 *     </ul>
 * </li>
 * <li><strong>API Version Detection:</strong>
 *     <ul>
 *     <li>Automatically detects DSF BPE API version (v1/v2) using {@link ApiVersionDetector}</li>
 *     <li>Enables version-specific validation rules and constraints</li>
 *     <li>Reports detected version for transparency</li>
 *     </ul>
 * </li>
 * <li><strong>FHIR Authorization Cache Population:</strong>
 *     <ul>
 *     <li>Populates FHIR authorization cache from project resources and classpath dependencies</li>
 *     <li>Enables comprehensive FHIR resource validation with proper context</li>
 *     </ul>
 * </li>
 * <li><strong>Targeted Resource Validation:</strong>
 *     <ul>
 *     <li>BPMN validation using {@link BPMNValidator} for process definition compliance</li>
 *     <li>FHIR validation using {@link FhirResourceValidator} for resource compliance</li>
 *     <li>Validates <em>only</em> resources explicitly referenced in the plugin definition</li>
 *     <li>Parsing failures immediately abort validation with detailed error reporting</li>
 *     </ul>
 * </li>
 * <li><strong>Unreferenced Resource Detection:</strong>
 *     <ul>
 *     <li>Scans project resource directories ({@code bpe/}, {@code fhir/} under resources root)</li>
 *     <li>Identifies files that exist on disk but are not referenced in the plugin definition</li>
 *     <li>Reports unreferenced files as "leftover" warnings for maintenance purposes</li>
 *     </ul>
 * </li>
 * <li><strong>ServiceLoader Registration Verification:</strong>
 *     <ul>
 *     <li>Confirms proper registration of {@code ProcessPluginDefinition} in {@code META-INF/services}</li>
 *     <li>Validates Java ServiceLoader mechanism compliance</li>
 *     <li>Reports registration issues with specific resolution guidance</li>
 *     </ul>
 * </li>
 * <li><strong>Comprehensive Report Generation:</strong>
 *     <ul>
 *     <li>Aggregates all validation results (successes, warnings, errors)</li>
 *     <li>Generates structured JSON reports in hierarchical directory structure</li>
 *     <li>Provides detailed categorization and aggregation of results</li>
 *     </ul>
 * </li>
 * </ol>
 *
 * <h2>Validation Categories</h2>
 * <p>
 * The validator produces comprehensive validation results across three primary categories:
 * </p>
 * <ul>
 * <li><strong>BPMN Validation:</strong>
 *     <ul>
 *     <li>Syntax validation for process definition correctness</li>
 *     <li>Process model analysis and structural validation</li>
 *     <li>Reference verification and consistency checking</li>
 *     <li>DSF-specific compliance validation</li>
 *     </ul>
 * </li>
 * <li><strong>FHIR Validation:</strong>
 *     <ul>
 *     <li>Resource syntax validation against FHIR specifications</li>
 *     <li>Profile compliance checking for DSF-specific constraints</li>
 *     <li>Reference resolution and consistency validation</li>
 *     <li>Authorization context validation</li>
 *     </ul>
 * </li>
 * <li><strong>Plugin Validation:</strong>
 *     <ul>
 *     <li>ServiceLoader registration verification</li>
 *     <li>Unreferenced file detection and reporting</li>
 *     <li>Plugin definition consistency checking</li>
 *     <li>Project structure validation</li>
 *     </ul>
 * </li>
 * </ul>
 *
 * <h2>Report Structure</h2>
 * <p>
 * The validator generates a comprehensive, hierarchical report structure organized by validation type
 * and result category. All reports are written to the {@code report/} directory (customizable via
 * system property {@code dsf.report.dir}):
 * </p>
 * <pre>
 * report/
 * ├── aggregated.json                          (Master report combining all validation results)
 * ├── bpmnReports/
 * │   ├── success/                             (Successful BPMN validations)
 * │   │   ├── bpmn_issues_{parentFolder}_{processId}.json (Individual process reports)
 * │   │   └── bpmn_success_aggregated.json     (All successful BPMN validations)
 * │   ├── other/                               (BPMN errors and warnings)
 * │   │   ├── bpmn_issues_{parentFolder}_{processId}.json (Individual process reports)
 * │   │   └── bpmn_other_aggregated.json       (All BPMN issues)
 * │   └── bpmn_issues_aggregated.json          (Combined BPMN report)
 * ├── fhirReports/
 * │   ├── success/                             (Successful FHIR validations)
 * │   │   ├── fhir_issues_{parentFolder}_{baseName}.json (Individual resource reports)
 * │   │   └── fhir_success_aggregated.json     (All successful FHIR validations)
 * │   ├── other/                               (FHIR errors and warnings)
 * │   │   ├── fhir_issues_{parentFolder}_{baseName}.json (Individual resource reports)
 * │   │   └── fhir_other_aggregated.json       (All FHIR issues)
 * │   └── fhir_issues_aggregated.json          (Combined FHIR report)
 * └── pluginReports/
 *     ├── success/                             (Plugin validation successes)
 *     │   ├── plugin_issue_success_XXX.json    (Individual success reports)
 *     │   └── plugin_success_aggregated.json   (All plugin successes)
 *     ├── other/                               (Plugin errors and warnings)
 *     │   ├── plugin_issue_other_XXX.json      (Individual issue reports)
 *     │   └── plugin_other_aggregated.json     (All plugin issues)
 *     └── plugin_issues_aggregated.json        (Combined plugin report)
 * </pre>
 *
 * <h2>Error Handling and Termination Conditions</h2>
 * <p>
 * The validator implements strict error handling policies with well-defined termination conditions
 * to ensure reliable operation and comprehensive feedback:
 * </p>
 *
 * <h3>Critical Failures (Immediate Termination)</h3>
 * <ul>
 * <li><strong>Invalid Project Directory:</strong> If the provided path is not a directory,
 *     validation terminates immediately with an empty result.</li>
 * <li><strong>Maven Build Failure:</strong> In standard layouts, failure of Maven build process
 *     (excluding test execution) terminates validation immediately.</li>
 * <li><strong>ClassLoader Creation Failure:</strong> Inability to create the project classpath
 *     for either Maven or exploded layouts terminates validation.</li>
 * <li><strong>Plugin Definition Issues:</strong> Validation terminates if no {@code ProcessPluginDefinition}
 *     is found or if multiple definitions are discovered.</li>
 * <li><strong>Resource Parsing Errors:</strong> Any referenced BPMN or FHIR resource file containing
 *     syntax errors that prevent parsing will abort the entire validation process.</li>
 * <li><strong>File System Errors:</strong> Critical file system operations failures during resource
 *     discovery or report generation will terminate validation.</li>
 * </ul>
 *
 * <h3>Recoverable Issues (Continued Validation)</h3>
 * <ul>
 * <li><strong>Missing Referenced Files:</strong> Files referenced in the plugin definition but not
 *     found on disk are reported as errors, but validation continues.</li>
 * <li><strong>Unreferenced Resources:</strong> Files found on disk but not referenced in the plugin
 *     definition are reported as warnings without terminating validation.</li>
 * <li><strong>ServiceLoader Registration Issues:</strong> Missing or incorrect ServiceLoader registration
 *     is reported as an error with specific guidance, but validation continues.</li>
 * </ul>
 *
 * <h2>Integration Components</h2>
 * <p>
 * The validator integrates with multiple specialized components and utilities to provide
 * comprehensive validation capabilities:
 * </p>
 * <ul>
 * <li>{@link MavenBuilder} - Project compilation and dependency resolution for standard layouts</li>
 * <li>{@link PluginDefinitionDiscovery} - ProcessPluginDefinition discovery and validation</li>
 * <li>{@link ApiVersionDetector} - Automatic DSF BPE API version detection</li>
 * <li>{@link FhirAuthorizationCache} - DSF CodeSystem caching for FHIR validation context</li>
 * <li>{@link BPMNValidator} - BPMN process definition validation and analysis</li>
 * <li>{@link FhirResourceValidator} - FHIR resource validation with DSF-specific profiles</li>
 * <li>{@link ApiRegistrationValidationSupport} - ServiceLoader registration verification</li>
 * <li>{@link ReportCleaner} - Report directory management and cleanup</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Validation</h3>
 * <pre>{@code
 * Logger logger = new ConsoleLogger();
 * DsfValidator validator = new DsfValidatorImpl(logger);
 * Path projectPath = Paths.get("/path/to/dsf-project");
 *
 * try {
 *     ValidationOutput result = validator.validate(projectPath);
 *
 *     if (result.hasErrors()) {
 *         System.err.println("Validation failed with " + result.getErrorCount() + " errors");
 *         System.exit(1);
 *     } else {
 *         System.out.println("Validation completed successfully with " +
 *                           result.validationItems().size() + " total findings");
 *     }
 * } catch (ResourceValidationException e) {
 *     System.err.println("Critical validation error: " + e.getMessage());
 *     System.exit(1);
 * }
 * }</pre>
 *
 * <h3>Custom Report Directory</h3>
 * <pre>{@code
 * // Set custom report directory before validation
 * System.setProperty("dsf.report.dir", "/custom/report/path");
 *
 * ValidationOutput result = validator.validate(projectPath);
 * System.out.println("Reports written to: " + System.getProperty("dsf.report.dir"));
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This implementation is <strong>not thread-safe</strong>. Each validation operation should use
 * a separate instance of {@code DsfValidatorImpl}, or external synchronization should be employed
 * when sharing instances across multiple threads.
 * </p>
 *
 * <h2>System Requirements</h2>
 * <ul>
 * <li>Java 11 or higher</li>
 * <li>Maven executable available in system PATH (for standard project layouts)</li>
 * <li>Sufficient file system permissions for report directory creation and writing</li>
 * <li>Access to project dependencies and classpath resources</li>
 * </ul>
 *
 * @author DSF Development Team
 * @version 1.0.0
 * @since 1.0.0
 *
 * @see DsfValidator
 * @see ValidationOutput
 * @see PluginDefinitionDiscovery
 * @see BPMNValidator
 * @see FhirResourceValidator
 * @see ApiVersionDetector
 */
public class DsfValidatorImpl implements DsfValidator
{
    private final FhirResourceValidator fhirResourceValidator;
    private final Logger logger;
    /**
     * Constructs a default {@code DsfValidatorImpl} that includes a
     * {@link FhirResourceValidator} for validating FHIR resources.
     */
    public DsfValidatorImpl(Logger logger)
    {
        this.logger = logger;
        this.fhirResourceValidator = new FhirResourceValidator();
    }

    /**
     * Orchestrates the complete validation workflow for DSF-compliant process plugin projects.
     *
     * <p>This method implements a definition-driven validation process that adapts to different
     * project layouts (standard Maven, Gradle, or exploded plugin in CI pipelines). Instead of
     * performing a naive directory scan, it validates resources based on the explicit references
     * declared in the discovered {@code ProcessPluginDefinition}.</p>
     *
     * <h3>Validation Workflow</h3>
     * <ol>
     *   <li><strong>Project Layout Detection:</strong>
     *       Detects whether the input path is a Maven project (presence of {@code pom.xml})
     *       or an exploded plugin layout (e.g., CI artifacts without sources).</li>
     *   <li><strong>Build Phase & ClassLoader Setup:</strong>
     *       <ul>
     *         <li>For Maven projects: Executes {@code clean package dependency:copy-dependencies}
     *             to ensure that compiled classes and dependencies are available. A custom
     *             {@link java.net.URLClassLoader} is created from the project's {@code target/classes}
     *             and dependency JARs.</li>
     *         <li>For exploded layouts: Builds a {@link java.net.URLClassLoader} directly from
     *             the given project directory.</li>
     *       </ul>
     *       In both cases the thread context class loader (TCCL) is temporarily switched and
     *       later restored.</li>
     *   <li><strong>Plugin Discovery:</strong>
     *       Discovers the unique {@code ProcessPluginDefinition} implementation using
     *       {@link PluginDefinitionDiscovery#discoverSingle(File)}. The plugin’s code source
     *       location (directory or JAR) is logged for diagnostics.</li>
     *   <li><strong>Resource Root Detection:</strong>
     *       Determines the effective resource root directory based on the plugin’s code source.
     *       This ensures correct handling of multi-module Maven projects (e.g.,
     *       {@code <module>/target/classes}) and Gradle builds (e.g.,
     *       {@code build/resources/main}).</li>
     *   <li><strong>API Version Detection:</strong>
     *       Automatically detects and sets the DSF BPE API version (v1/v2) via
     *       {@link ApiVersionDetector}.</li>
     *   <li><strong>Authorization Cache Initialization:</strong>
     *       Seeds the {@link FhirAuthorizationCache} with DSF CodeSystem information from
     *       project and classpath.</li>
     *   <li><strong>Resource Collection:</strong>
     *       Collects all BPMN and FHIR resource references from the discovered plugin definition.</li>
     *   <li><strong>Targeted Validation:</strong>
     *       Validates only the referenced BPMN and FHIR resources. Missing references are reported
     *       as warnings, and parsing errors cause immediate validation exceptions.</li>
     *   <li><strong>Leftover Detection:</strong>
     *       Identifies unreferenced BPMN or FHIR files in the resource root and reports them
     *       as potential issues.</li>
     *   <li><strong>ServiceLoader Verification:</strong>
     *       Checks that the {@code ProcessPluginDefinition} is correctly registered in
     *       {@code META-INF/services}.</li>
     *   <li><strong>Report Generation:</strong>
     *       Writes structured JSON reports for BPMN, FHIR, and plugin checks, as well as an
     *       aggregated summary.</li>
     * </ol>
     *
     * <h3>Error Handling Strategy</h3>
     * <ul>
     *   <li>Throws immediately if the Maven build fails in a standard project.</li>
     *   <li>Throws if no unique {@code ProcessPluginDefinition} can be discovered.</li>
     *   <li>Throws {@link ResourceValidationException} if a referenced resource contains parsing errors.</li>
     *   <li>Continues with warnings for missing resources, leftovers, or ServiceLoader anomalies.</li>
     * </ul>
     *
     * <h3>Report Structure</h3>
     * <p>All reports are written to a clean {@code report/} directory (overridable by system
     * property {@code dsf.report.dir}):</p>
     *
     * <pre>
     * report/
     * ├── aggregated.json                 (Combined results summary)
     * ├── bpmnReports/
     * │   ├── success/                    (Successful BPMN validations)
     * │   ├── other/                      (BPMN errors and warnings)
     * │   └── bpmn_issues_aggregated.json
     * ├── fhirReports/
     * │   ├── success/                    (Successful FHIR validations)
     * │   ├── other/                      (FHIR errors and warnings)
     * │   └── fhir_issues_aggregated.json
     * └── pluginReports/
     *     ├── success/                    (Plugin validation successes)
     *     ├── other/                      (Plugin errors and warnings)
     *     └── plugin_issues_aggregated.json
     * </pre>
     *
     * @param path absolute path to the project root directory to validate
     * @return a {@link ValidationOutput} containing all validation results and statistics
     *
     * @throws ResourceValidationException if any referenced BPMN or FHIR resource file fails parsing
     * @throws IllegalStateException if the project layout is invalid, Maven is missing, or plugin discovery fails
     * @throws IOException if file system operations fail during validation or report generation
     * @throws InterruptedException if the Maven build process is interrupted
     * @throws RuntimeException if the Maven build goal fails
     * @throws MissingServiceRegistrationException if ServiceLoader registration checks fail
     *
     * @see PluginDefinitionDiscovery#discoverSingle(File)
     * @see ValidationOutput
     * @see ApiVersionDetector
     * @see FhirAuthorizationCache
     */
    @Override
    public ValidationOutput validate(Path path) throws ResourceValidationException, IllegalStateException, IOException, InterruptedException, MissingServiceRegistrationException
    {
        if (!Files.isDirectory(path))
        {
            logger.error("ERROR: This validation workflow only supports project directories.");
            return ValidationOutput.empty();
        }

        final File projectDir = path.toFile();

        // 0) Detect layout: Maven source checkout (pom.xml) vs. exploded plugin layout (e.g., CI artifacts)
        final boolean isMavenProject = Files.isRegularFile(path.resolve("pom.xml"));

        // We will restore the previous TCCL at the end to avoid leaking classpaths to callers.
        final ClassLoader previousTCCL = Thread.currentThread().getContextClassLoader();
        File resourcesDir = null;

        try
        {
            // 1) Build classpath and set TCCL
            if (isMavenProject)
            {
                logger.info("Detected Maven project ('pom.xml' exists), executing build...");

                final String mavenExecutable = MavenUtil.locateMavenExecutable();
                if (mavenExecutable == null)
                    throw new IllegalStateException("Maven executable not found in PATH.");

                final MavenBuilder builder = new MavenBuilder();
                final boolean buildOk = builder.buildProject(projectDir, mavenExecutable,
                        "-B", "-DskipTests", "-Dformatter.skip=true", "-Dexec.skip=true",
                        "clean", "package", "dependency:copy-dependencies");
                if (!buildOk)
                    throw new RuntimeException("Maven 'package' phase failed.");

                try
                {
                    ClassLoader projectCl = createProjectClassLoader(projectDir);
                    Thread.currentThread().setContextClassLoader(projectCl);
                    logger.info("Context ClassLoader set for Maven project validation.");
                }
                catch (Exception e)
                {
                    throw new IllegalStateException("Failed to create project classpath for Maven project at " + projectDir, e);
                }
            }
            else
            {
                logger.info("No 'pom.xml' found. Assuming exploded plugin layout – skipping Maven build.");
                logger.info("Building runtime classpath from: " + projectDir.getAbsolutePath());
                try
                {
                    ClassLoader projectCl = createProjectClassLoader(projectDir);
                    Thread.currentThread().setContextClassLoader(projectCl);
                    logger.info("Context ClassLoader set for exploded plugin validation.");
                }
                catch (Exception e)
                {
                    throw new IllegalStateException("Failed to create project classpath for exploded plugin at " + projectDir, e);
                }
            }

            // 2) Discover the (single) ProcessPluginDefinition on the constructed classpath
            final PluginAdapter pluginAdapter = PluginDefinitionDiscovery.discoverSingle(projectDir);
            logger.info("Discovered ProcessPluginDefinition: " + pluginAdapter.sourceClass().getName());

            // Extra diagnostics: show the CodeSource location (directory or JAR)
            try
            {
                var cs = pluginAdapter.sourceClass().getProtectionDomain().getCodeSource();
                if (cs != null && cs.getLocation() != null)
                    logger.debug("Plugin class CodeSource: " + cs.getLocation());
                else
                    logger.debug("Plugin class CodeSource: <null> (class may come from a special loader/JAR)");
            }
            catch (Exception ignore) { /* debug only */ }

            // 2a) Determine the proper resources root (handles multi-module & Gradle layouts)
            resourcesDir = determineResourcesRoot(projectDir, pluginAdapter,
                    isMavenProject
                            ? (new File(projectDir, "src/main/resources").isDirectory()
                            ? new File(projectDir, "src/main/resources")
                            : new File(projectDir, "target/classes"))
                            : projectDir);

            logger.info("Resources root in use: " + resourcesDir.getAbsolutePath());
            if (isMavenProject)
                logger.info("Using standard Maven resource directory: " + resourcesDir.getAbsolutePath());
            else
                logger.info("Using project root as resource directory for exploded plugin: " + resourcesDir.getAbsolutePath());

            // 3) Detect DSF BPE API version (best-effort)
            ApiVersionDetector detector = new ApiVersionDetector();
            detector.detect(path).ifPresent(v -> ApiVersionHolder.setVersion(v.version()));

            // 4) Seed DSF CodeSystem cache from project and classpath
            FhirAuthorizationCache.seedFromProjectAndClasspath(projectDir);

            // 5) Collect referenced resources from the plugin definition
            logger.info("Gathering referenced resources from " + pluginAdapter.sourceClass().getSimpleName() + "...");
            final Set<String> referencedBpmnPaths = pluginAdapter.getProcessModels().stream()
                    .map(ResourceResolver::normalizeRef)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            final Set<String> referencedFhirPaths = pluginAdapter.getFhirResourcesByProcessId().values().stream()
                    .flatMap(Collection::stream)
                    .map(ResourceResolver::normalizeRef)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // 6) Resolve referenced files against the selected resources root
            final File finalResourcesDir = resourcesDir;

            final List<File> bpmnFilesToValidate = referencedBpmnPaths.stream()
                    .map(this::cleanRef)
                    .map(ref -> ResourceResolver.resolveToFile(ref, finalResourcesDir))
                    .flatMap(Optional::stream)
                    .distinct()
                    .collect(Collectors.toList());

            final List<String> missingBpmnRefs = referencedBpmnPaths.stream()
                    .map(this::cleanRef)
                    .filter(ref -> ResourceResolver.resolveToFile(ref, finalResourcesDir).isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            final List<File> fhirFilesToValidate = referencedFhirPaths.stream()
                    .map(this::cleanRef)
                    .map(ref -> ResourceResolver.resolveToFile(ref, finalResourcesDir))
                    .flatMap(Optional::stream)
                    .distinct()
                    .collect(Collectors.toList());

            final List<String> missingFhirRefs = referencedFhirPaths.stream()
                    .map(this::cleanRef)
                    .filter(ref -> ResourceResolver.resolveToFile(ref, finalResourcesDir).isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            logger.info("Referenced BPMN: " + referencedBpmnPaths.size()
                    + " -> resolved: " + bpmnFilesToValidate.size()
                    + ", missing: " + missingBpmnRefs.size());
            logger.info("Referenced FHIR: " + referencedFhirPaths.size()
                    + " -> resolved: " + fhirFilesToValidate.size()
                    + ", missing: " + missingFhirRefs.size());

            if (!missingBpmnRefs.isEmpty())
                logger.warn("Sample missing BPMN ref: " + missingBpmnRefs.getFirst());
            if (!missingFhirRefs.isEmpty())
                logger.warn("Sample missing FHIR ref: " + missingFhirRefs.getFirst());

            // Sanity diagnostics: if we have references but resolved none, likely wrong base or JAR classpath case
            if (!referencedBpmnPaths.isEmpty() && bpmnFilesToValidate.isEmpty())
                logger.warn("Sanity check: No BPMN files resolved although BPMN references exist. " +
                        "If the plugin was loaded from a JAR, ensure ResourceResolver can read via ClassLoader.");
            if (!referencedFhirPaths.isEmpty() && fhirFilesToValidate.isEmpty())
                logger.warn("Sanity check: No FHIR files resolved although FHIR references exist. " +
                        "If the plugin was loaded from a JAR, ensure ResourceResolver can read via ClassLoader.");

            logger.info("Resources root in use: " + resourcesDir.getAbsolutePath());

            // 7) Prepare report directory
            final File reportRoot = new File(System.getProperty("dsf.report.dir", "report"));
            ReportCleaner.prepareCleanReportDirectory(reportRoot);

            // 8) Validate referenced files and generate reports (BPMN + FHIR)
            final List<AbstractValidationItem> bpmnItems =
                    validateAllBpmnFilesSplitNewStructure(bpmnFilesToValidate, missingBpmnRefs, reportRoot);

            final List<AbstractValidationItem> fhirItems =
                    validateAllFhirResourcesSplitNewStructure(fhirFilesToValidate, missingFhirRefs, reportRoot);

            // 9) Find and report leftovers (unreferenced files) under resourcesDir
            final List<AbstractValidationItem> leftoverItems =
                    findAndReportLeftovers(projectDir, resourcesDir, referencedBpmnPaths, referencedFhirPaths);

            // 10) Plugin-level checks (e.g., ServiceLoader registrations)
            final List<AbstractValidationItem> serviceLoaderItems = collectPluginItems(path);

            // 11) Aggregate and write plugin reports
            final List<AbstractValidationItem> allPluginItems = new ArrayList<>();
            allPluginItems.addAll(leftoverItems);
            allPluginItems.addAll(serviceLoaderItems);
            writePluginReports(allPluginItems, new File(reportRoot, "pluginReports"));

            // 12) Aggregate everything
            final List<AbstractValidationItem> combined = new ArrayList<>();
            combined.addAll(bpmnItems);
            combined.addAll(fhirItems);
            combined.addAll(allPluginItems);

            final ValidationOutput finalOutput = new ValidationOutput(combined);
            finalOutput.writeResultsAsJson(new File(reportRoot, "aggregated.json"));

            System.out.printf("%nValidation finished – %d issue(s) found (%d BPMN/FHIR + %d plugin).%n",
                    combined.size(), bpmnItems.size() + fhirItems.size(), allPluginItems.size());
            logger.info("Reports written to: " + reportRoot.getAbsolutePath());

            printDetectedApiVersion();
            return finalOutput;
        }
        finally
        {
            // Always restore the previous TCCL
            Thread.currentThread().setContextClassLoader(previousTCCL);
        }
    }


    // BPMN (Split with sub-aggregators)

    /**
     * Validates a list of BPMN files and generates structured reports.
     *
     * <p>This method validates each BPMN file in the provided list, categorizes results into
     * 'success' and 'other' (errors/warnings), and writes individual and aggregated JSON reports
     * to the specified directory structure.</p>
     *
     * <p>For each missing reference that cannot be resolved to a file, an error validation item
     * is created. Files that exist are validated using {@link BPMNValidator}, and parsing failures
     * result in a {@link ResourceValidationException} that aborts the entire validation process.</p>
     *
     * <p>Report structure generated:</p>
     * <pre>
     * reportRoot/bpmnReports/
     * ├── success/
     * │   ├── bpmn_issues_{parentFolder}_{processId}.json (per file)
     * │   └── bpmn_success_aggregated.json
     * ├── other/
     * │   ├── bpmn_issues_{parentFolder}_{processId}.json (per file)
     * │   └── bpmn_other_aggregated.json
     * └── bpmn_issues_aggregated.json (combined)
     * </pre>
     *
     * @param bpmnFiles  The list of BPMN files to validate
     * @param missingBpmnRefs The list of BPMN file references that could not be resolved to actual files
     * @param reportRoot The root directory for report output (e.g., "report/")
     * @return A list of all validation items generated during BPMN validation
     * @throws ResourceValidationException if any BPMN file contains syntax errors that prevent parsing
     */
    public List<AbstractValidationItem> validateAllBpmnFilesSplitNewStructure(List<File> bpmnFiles, List<String> missingBpmnRefs, File reportRoot) throws ResourceValidationException
    {
        // 1) Setup subfolders for BPMN reports
        File bpmnFolder = new File(reportRoot, "bpmnReports");
        if (!bpmnFolder.mkdirs() && !bpmnFolder.exists()) {
            throw new RuntimeException("Failed to create BPMN reports directory: " + bpmnFolder.getAbsolutePath());
        }
        File successFolder = new File(bpmnFolder, "success");
        File otherFolder = new File(bpmnFolder, "other");
        if (!successFolder.mkdirs() && !successFolder.exists()) {
            throw new RuntimeException("Failed to create BPMN success directory: " + successFolder.getAbsolutePath());
        }
        if (!otherFolder.mkdirs() && !otherFolder.exists()) {
            throw new RuntimeException("Failed to create BPMN other directory: " + otherFolder.getAbsolutePath());
        }

        // Initialize aggregators for all results
        List<AbstractValidationItem> allBpmnItems = new ArrayList<>();
        List<AbstractValidationItem> successAggregator = new ArrayList<>();
        List<AbstractValidationItem> otherAggregator = new ArrayList<>();

        // Before validating, emit NOT FOUND items for unresolved refs
        for (String missing : missingBpmnRefs) {
            BpmnFileReferencedButNotFoundValidationItem notFoundItem = new BpmnFileReferencedButNotFoundValidationItem(
                    ValidationSeverity.ERROR,
                    new File(missing), // only for context
                    "Referenced BPMN file not found (disk or classpath): " + missing);
            allBpmnItems.add(notFoundItem);
            otherAggregator.add(notFoundItem);
        }

        if (bpmnFiles.isEmpty() && missingBpmnRefs.isEmpty())
        {
            logger.info("No referenced .bpmn files to validate.");
            return allBpmnItems;
        }

        BPMNValidator validator = new BPMNValidator();

        // 3) Validate each BPMN file from the provided list
        for (File bpmnFile : bpmnFiles)
        {

            logger.info("Validating BPMN file: " + bpmnFile.getName());
            ValidationOutput out;
            List<AbstractValidationItem> itemsForThisFile;
            // This call will throw ResourceValidationException on parsing failure
            out = validator.validateBpmnFile(bpmnFile.toPath());
            itemsForThisFile = new ArrayList<>(out.validationItems());

            // Add a success item indicating the file was found and is readable
            String processId = out.getProcessId();
            if (processId.isBlank()) {
                processId = "[unknown_process]";
            }

            itemsForThisFile.add(new BpmnElementValidationItemSuccess(processId, bpmnFile, processId, "Referenced BPMN file found and is readable."));

            out.printResults();

            allBpmnItems.addAll(itemsForThisFile);

            List<AbstractValidationItem> successItems = itemsForThisFile.stream()
                    .filter(this::isSuccessItem)
                    .toList();
            List<AbstractValidationItem> otherItems = itemsForThisFile.stream()
                    .filter(x -> !isSuccessItem(x))
                    .toList();

            successAggregator.addAll(successItems);
            otherAggregator.addAll(otherItems);

            // Write individual file reports based on the results
            String pid = out.getProcessId();
            if (pid.isBlank())
            {
                pid = bpmnFile.getName().replace(".bpmn", "");
            }
            String parentFolderName = bpmnFile.getParentFile().getName();

            if (!successItems.isEmpty())
            {
                new ValidationOutput(successItems).writeResultsAsJson(new File(successFolder,
                        "bpmn_issues_" + parentFolderName + "_" + pid + ".json"));
            }

            if (!otherItems.isEmpty())
            {
                new ValidationOutput(otherItems).writeResultsAsJson(new File(otherFolder,
                        "bpmn_issues_" + parentFolderName + "_" + pid + ".json"));
            }
        }

        // 4) Write unified sub-reports for all BPMN files
        writeSubReports("bpmn", successAggregator, otherAggregator, bpmnFolder);

        return allBpmnItems;
    }


    /**
     * Validates a list of FHIR resource files and generates structured reports.
     *
     * <p>This method validates each FHIR file in the provided list using {@link FhirResourceValidator},
     * categorizes results into 'success' and 'other' (errors/warnings), and writes individual and
     * aggregated JSON reports to the specified directory structure.</p>
     *
     * <p>For each file that cannot be found on disk, an error validation item is created.
     * Files that exist are validated, and parsing failures result in a {@link ResourceValidationException}
     * that aborts the entire validation process.</p>
     *
     * <p>Report structure generated:</p>
     * <pre>
     * reportRoot/fhirReports/
     * ├── success/
     * │   ├── fhir_issues_{parentFolder}_{baseName}.json (per file)
     * │   └── fhir_success_aggregated.json
     * ├── other/
     * │   ├── fhir_issues_{parentFolder}_{baseName}.json (per file)
     * │   └── fhir_other_aggregated.json
     * └── fhir_issues_aggregated.json (combined)
     * </pre>
     *
     * @param fhirFiles  The list of FHIR resource files to validate
     * @param missingFhirRefs The list of FHIR file references that could not be resolved to actual files
     * @param reportRoot The root directory for report output (e.g., "report/")
     * @return A list of all validation items generated during FHIR validation
     * @throws ResourceValidationException if any FHIR file contains syntax errors that prevent parsing
     */
    public List<AbstractValidationItem> validateAllFhirResourcesSplitNewStructure(List<File> fhirFiles, List<String> missingFhirRefs, File reportRoot) throws ResourceValidationException
    {
        // 1) Setup subfolders for FHIR reports
        File fhirFolder = new File(reportRoot, "fhirReports");
        if (!fhirFolder.mkdirs() && !fhirFolder.exists()) {
            throw new RuntimeException("Failed to create FHIR reports directory: " + fhirFolder.getAbsolutePath());
        }
        File successFolder = new File(fhirFolder, "success");
        File otherFolder = new File(fhirFolder, "other");
        if (!successFolder.mkdirs() && !successFolder.exists()) {
            throw new RuntimeException("Failed to create FHIR success directory: " + successFolder.getAbsolutePath());
        }
        if (!otherFolder.mkdirs() && !otherFolder.exists()) {
            throw new RuntimeException("Failed to create FHIR other directory: " + otherFolder.getAbsolutePath());
        }

        // Initialize aggregators for all results
        List<AbstractValidationItem> allFhirItems = new ArrayList<>();
        List<AbstractValidationItem> successAggregator = new ArrayList<>();
        List<AbstractValidationItem> otherAggregator = new ArrayList<>();

        // Before validating, emit NOT FOUND items for unresolved refs
        for (String missing : missingFhirRefs) {
            dev.dsf.utils.validator.item.FhirFileReferencedButNotFoundValidationItem notFoundItem =
                    new dev.dsf.utils.validator.item.FhirFileReferencedButNotFoundValidationItem(
                            "Referenced FHIR file not found : " + missing,
                            dev.dsf.utils.validator.ValidationSeverity.ERROR,
                            new java.io.File(missing)); // only for context
            allFhirItems.add(notFoundItem);
            otherAggregator.add(notFoundItem);
        }

        if (fhirFiles.isEmpty() && missingFhirRefs.isEmpty())
        {
            logger.info("No referenced FHIR files to validate.");
            return allFhirItems;
        }

        // 3) Validate each FHIR file from the provided list
        for (File fhirFile : fhirFiles)
        {

            logger.info("Validating FHIR file: " + fhirFile.getName());
            ValidationOutput output;
            List<AbstractValidationItem> itemsForThisFile;
            // This call will throw ResourceValidationException on parsing failure
            output = fhirResourceValidator.validateSingleFile(fhirFile.toPath());
            itemsForThisFile = new ArrayList<>(output.validationItems());

            // Add a success item indicating the file was found and is readable
            String fhirReference = itemsForThisFile.stream()
                    .filter(item -> item instanceof FhirElementValidationItem)
                    .map(item -> ((FhirElementValidationItem) item).getFhirReference())
                    .filter(ref -> ref != null && !ref.isBlank())
                    .findFirst()
                    .orElse("unknown_reference");

            itemsForThisFile.add(new FhirElementValidationItemSuccess(fhirFile, fhirReference, "Referenced FHIR file found and is readable."));

            output.printResults();

            allFhirItems.addAll(itemsForThisFile);

            List<AbstractValidationItem> successItems = itemsForThisFile.stream()
                    .filter(this::isSuccessItem)
                    .toList();
            List<AbstractValidationItem> otherItems = itemsForThisFile.stream()
                    .filter(x -> !isSuccessItem(x))
                    .toList();

            successAggregator.addAll(successItems);
            otherAggregator.addAll(otherItems);

            // Write individual file reports based on the results
            String baseName = fhirFile.getName();
            int idx = baseName.lastIndexOf('.');
            if (idx > 0)
            {
                baseName = baseName.substring(0, idx);
            }
            String parentFolderName = fhirFile.getParentFile().getName();

            if (!successItems.isEmpty())
            {
                new ValidationOutput(successItems).writeResultsAsJson(new File(successFolder,
                        "fhir_issues_" + parentFolderName + "_" + baseName + ".json"));
            }

            if (!otherItems.isEmpty())
            {
                new ValidationOutput(otherItems).writeResultsAsJson(new File(otherFolder,
                        "fhir_issues_" + parentFolderName + "_" + baseName + ".json"));
            }
        }

        // 4) Write unified sub-reports for all FHIR files
        writeSubReports("fhir", successAggregator, otherAggregator, fhirFolder);

        return allFhirItems;
    }


    // Common Utility

    /**
     * Determines if the validation item is "SUCCESS." Adjust if your success logic differs.
     *
     * @param item a validation item
     * @return {@code true} if severity == {@link ValidationSeverity#SUCCESS}, else false
     */
    private boolean isSuccessItem(AbstractValidationItem item)
    {
        return item.getSeverity() == ValidationSeverity.SUCCESS;
    }

    /**
     * Unified file finder using NIO PathMatcher for flexible file filtering.
     * Uses try-with-resources for automatic stream management.
     *
     * @param root the root directory to search
     * @param glob the glob pattern
     * @return list of matching files
     */
    private List<File> findFiles(Path root, String glob)
    {
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }

        PathMatcher matcher = root.getFileSystem().getPathMatcher(glob);
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error walking directory " + root + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * <p>
     * Recursively finds all FHIR files under the given {@code rootPath} using the centralized
     * file type detection from {@link FhirFileUtils}.
     * </p>
     *
     * @param rootPath  The root path to traverse
     * @return a list of {@link File} objects that are valid FHIR files (XML or JSON)
     */
    private List<File> findFhirFilesRecursively(Path rootPath)
    {
        return findFiles(rootPath, "glob:**/*.{xml,json}").stream()
                .filter(f -> FhirFileUtils.isFhirFile(f.toPath()))
                .collect(Collectors.toList());
    }

    /**
     * Recursively finds all BPMN files (ending in .bpmn) under the given {@code rootPath}.
     *
     * @param rootPath The root path to traverse.
     * @return A list of {@link File} objects that are BPMN files.
     */
    private List<File> findBpmnFilesRecursively(Path rootPath)
    {
        // sole purpose is to find BPMN files.
        return findFiles(rootPath, "glob:**/*.bpmn");
    }

    /**
     * Common reporter to write validation items split by success/other with aggregated reports.
     * Creates the necessary directory structure and writes individual and aggregated JSON files.
     *
     * @param prefix the file prefix (e.g., "bpmn", "fhir", "plugin")
     * @param success list of successful validation items
     * @param other list of non-successful validation items
     * @param targetDir the target directory for reports
     */
    private void writeSubReports(String prefix,
                                 List<AbstractValidationItem> success,
                                 List<AbstractValidationItem> other,
                                 File targetDir)
    {
        File successDir = new File(targetDir, "success");
        File otherDir = new File(targetDir, "other");
        if (!successDir.exists() && !successDir.mkdirs()) {
            throw new RuntimeException("Could not create directory: " + successDir.getAbsolutePath());
        }
        if (!otherDir.exists() && !otherDir.mkdirs()) {
            throw new RuntimeException("Could not create directory: " + otherDir.getAbsolutePath());
        }

        // Write success reports
        if (!success.isEmpty()) {
            new ValidationOutput(success).writeResultsAsJson(
                    new File(successDir, prefix + "_success_aggregated.json"));
        }

        // Write other reports
        if (!other.isEmpty()) {
            new ValidationOutput(other).writeResultsAsJson(
                    new File(otherDir, prefix + "_other_aggregated.json"));
        }

        // Write combined aggregated report
        List<AbstractValidationItem> all = new ArrayList<>(success.size() + other.size());
        all.addAll(success);
        all.addAll(other);
        if (!all.isEmpty()) {
            new ValidationOutput(all).writeResultsAsJson(
                    new File(targetDir, prefix + "_issues_aggregated.json"));
        }
    }


    /**
     * Collects plugin validation items (ServiceLoader registration checks) for the given project root.
     *
     * @param root the project root
     * @return a list of plugin validation items as {@link PluginValidationItem}
     */
    public List<AbstractValidationItem> collectPluginItems(Path root) throws  MissingServiceRegistrationException {
        List<AbstractValidationItem> raw = new ArrayList<>();
        ApiRegistrationValidationSupport support = new ApiRegistrationValidationSupport();
        support.run("Plugin collection", root, raw);

        // Convert raw items to proper PluginValidationItem types
        List<AbstractValidationItem> pluginItems = new ArrayList<>();

        // Use only the directory name for the "file" property
        String projectName = root.toFile().getName();

        if (raw.isEmpty()) {
            // No registration found - create missing service item
            pluginItems.add(new MissingServiceLoaderRegistrationValidationItem(
                    new File(projectName),
                    "META-INF/services",
                    "No ProcessPluginDefinition service registration found"
            ));
        } else {
            // Use the items directly from the support class
            pluginItems.addAll(raw);
        }

        return pluginItems;
    }

    /**
     * Writes plugin validation items under:
     * <pre>
     * report/pluginReports/
     *   ├── plugin_issues_aggregated.json
     *   ├── success/
     *   │     ├── plugin_success_aggregated.json
     *   │     └── plugin_issue_success_XXX.json
     *   └── other/
     *         ├── plugin_other_aggregated.json
     *         └── plugin_issue_other_XXX.json
     * </pre>
     *
     * @param items      plugin items to write
     * @param pluginRoot the plugin report root directory (e.g., {@code new File("report", "pluginReports")})
     * @throws IOException if writing fails
     */
    public void writePluginReports(List<AbstractValidationItem> items, File pluginRoot) throws IOException
    {
        if (!pluginRoot.isDirectory() && !pluginRoot.mkdirs())
            throw new IOException("Could not create directory: " + pluginRoot.getAbsolutePath());

        // Split by severity
        List<AbstractValidationItem> success = new ArrayList<>();
        List<AbstractValidationItem> other   = new ArrayList<>();
        for (AbstractValidationItem it : items)
        {
            if (it instanceof PluginValidationItemSuccess)
                success.add(it);
            else
                other.add(it);
        }

        // Root-level aggregated
        new ValidationOutput(items)
                .writeResultsAsJson(new File(pluginRoot, "plugin_issues_aggregated.json"));

        // Success folder (individual + aggregated)
        File successDir = new File(pluginRoot, "success");
        if (!successDir.isDirectory() && !successDir.mkdirs())
            throw new IOException("Could not create directory: " + successDir.getAbsolutePath());
        writePerItem(success, successDir, "plugin_issue_success_%03d.json");
        new ValidationOutput(success)
                .writeResultsAsJson(new File(successDir, "plugin_success_aggregated.json"));

        // Other folder (individual + aggregated)
        File otherDir = new File(pluginRoot, "other");
        if (!otherDir.isDirectory() && !otherDir.mkdirs())
            throw new IOException("Could not create directory: " + otherDir.getAbsolutePath());
        writePerItem(other, otherDir, "plugin_issue_other_%03d.json");
        new ValidationOutput(other)
                .writeResultsAsJson(new File(otherDir, "plugin_other_aggregated.json"));
    }

    /**
     * Prints the detected DSF BPE API version to the console with red formatting.
     * This method should be called after validation is complete to display the version at the end.
     */
    public void printDetectedApiVersion() {
        ApiVersion apiVersion = ApiVersionHolder.getVersion();
        String versionStr = switch (apiVersion) {
            case V1 -> "v1";
            case V2 -> "v2";
            case UNKNOWN -> "unknown";
        };
        logger.info("\u001B[31mDetected DSF BPE API version: " + versionStr + "\u001B[0m");
    }

    /**
     * Finds all BPMN and FHIR files on disk and compares them to the sets of referenced files.
     * Generates validation items for any unreferenced files ("leftovers").
     *
     * @param projectDir          The root directory of the project.
     * @param resourcesDir        The 'src/main/resources' directory, used as the base for relative paths.
     * @param referencedBpmnPaths A set of relative paths to BPMN files from the plugin definition.
     * @param referencedFhirPaths A set of relative paths to FHIR files from the plugin definition.
     * @return A list of validation items for leftover checks.
     */
    private List<AbstractValidationItem> findAndReportLeftovers(File projectDir, File resourcesDir, Set<String> referencedBpmnPaths, Set<String> referencedFhirPaths)
    {
        // Find all actual files on disk
        File bpeRoot = new File(resourcesDir, "bpe");
        List<File> actualBpmnFiles = (bpeRoot.exists()) ? findBpmnFilesRecursively(bpeRoot.toPath()) : Collections.emptyList();

        File fhirRoot = new File(resourcesDir, "fhir");
        List<File> actualFhirFiles = (fhirRoot.exists()) ? findFhirFilesRecursively(fhirRoot.toPath()) : Collections.emptyList();

        // Convert the paths of actual files to be relative to the resources directory
        Set<String> actualBpmnPaths = actualBpmnFiles.stream()
                .map(f -> getRelativePath(f, resourcesDir)) // Use resourcesDir as the base
                .collect(Collectors.toSet());
        Set<String> actualFhirPaths = actualFhirFiles.stream()
                .map(f -> getRelativePath(f, resourcesDir)) // Use resourcesDir as the base
                .collect(Collectors.toSet());

        // Find the difference: files on disk that are not in the definition
        actualBpmnPaths.removeAll(referencedBpmnPaths);
        actualFhirPaths.removeAll(referencedFhirPaths);

        List<AbstractValidationItem> items = new ArrayList<>();
        if (actualBpmnPaths.isEmpty() && actualFhirPaths.isEmpty())
        {
            // No leftovers found -> success item
            items.add(new PluginValidationItemSuccess(projectDir, projectDir.getName(),
                    "All BPMN and FHIR resources found in the project are correctly referenced in ProcessPluginDefinition."));
        }
        else
        {
            // Report all leftovers as warnings
            actualBpmnPaths.forEach(path ->
                items.add(new ProcessPluginRessourceNotLoadedValidationItem(new File(resourcesDir, path), path, null)));
            actualFhirPaths.forEach(path ->
                items.add(new ProcessPluginRessourceNotLoadedValidationItem(new File(resourcesDir, path), path, null)));
        }

        return items;
    }

    /**
     * Wandelt einen absoluten Dateipfad in einen relativen Pfad um (mit '/' als Trennzeichen).
     */
    private String getRelativePath(File file, File baseDir)
    {
        String relative = baseDir.toPath().relativize(file.toPath()).toString();
        return relative.replace(File.separator, "/");
    }

    /**
     * Writes one JSON file per item into the given folder using the given filename pattern.
     *
     * @param items    items to serialize
     * @param dir      target folder
     * @param pattern  file name pattern (e.g., {@code "plugin_issue_success_%03d.json"})
     */
    private void writePerItem(List<AbstractValidationItem> items, File dir, String pattern)
    {
        for (int i = 0; i < items.size(); i++)
        {
            AbstractValidationItem it = items.get(i);
            String fileName = String.format(pattern, i + 1);
            new ValidationOutput(new ArrayList<>(List.of(it)))
                    .writeResultsAsJson(new File(dir, fileName));
        }
    }

    /** Normalizes a resource reference for filesystem/classpath resolution. */
    private String cleanRef(String ref) {
        if (ref == null) return "";
        String r = ref.trim();
        // drop classpath: prefix
        if (r.startsWith("classpath:")) r = r.substring("classpath:".length());
        // unify separators
        r = r.replace('\\', '/');
        // remove all leading slashes so File(base, r) doesn't ignore base on Windows/Unix
        while (r.startsWith("/")) r = r.substring(1);
        return r;
    }

    /**
     * Determines the effective resources root directory for the discovered plugin.
     * <p>
     * Strategy:
     * <ol>
     *   <li>Use the plugin class' {@link java.security.ProtectionDomain} → {@link java.security.CodeSource} location.
     *       If it is a directory (e.g., {@code <module>/target/classes}), return it.
     *       For Gradle layouts, prefer {@code build/resources/main} when the code source points at
     *       {@code build/classes/java/main}.</li>
     *   <li>If the code source is a JAR (or unavailable), fall back to a sensible local directory:
     *       {@code src/main/resources}, then {@code target/classes}, then {@code build/resources/main}.</li>
     *   <li>Finally, return the provided {@code fallback} (or {@code projectDir} if {@code fallback} is null).</li>
     * </ol>
     * <b>Note:</b> If the code source is a JAR, you typically cannot access resources as {@code File}s.
     * In that case, resolve resources via the {@link ClassLoader} (e.g., {@code getResourceAsStream}).
     *
     * @param projectDir the project root directory (source checkout)
     * @param pluginAdapter provides access to the discovered plugin class
     * @param fallback a precomputed default (e.g., {@code src/main/resources} or {@code target/classes})
     * @return the best-effort resources root directory as a {@link File}
     */
    private static File determineResourcesRoot(File projectDir, PluginAdapter pluginAdapter, File fallback)
    {
        try
        {
            Class<?> pluginClass = pluginAdapter.sourceClass();
            if (pluginClass != null)
            {
                java.security.ProtectionDomain pd = pluginClass.getProtectionDomain();
                if (pd != null)
                {
                    java.security.CodeSource cs = pd.getCodeSource();
                    if (cs != null && cs.getLocation() != null)
                    {
                        java.net.URI uri = cs.getLocation().toURI();
                        java.nio.file.Path loc = java.nio.file.Paths.get(uri);

                        // If the code source is a directory (typical during local Maven/Gradle builds)
                        if (java.nio.file.Files.isDirectory(loc))
                        {
                            String norm = loc.toString().replace('\\', '/');

                            // Maven: <module>/target/classes → use directly
                            if (norm.endsWith("/target/classes"))
                            {
                                return loc.toFile();
                            }

                            // Gradle: prefer <module>/build/resources/main if classes root is returned
                            if (norm.endsWith("/build/classes/java/main"))
                            {
                                java.nio.file.Path gradleRes = loc.getParent() // java
                                        .getParent() // classes
                                        .resolve("resources")
                                        .resolve("main");
                                if (java.nio.file.Files.isDirectory(gradleRes))
                                    return gradleRes.toFile();

                                // Fall back to classes dir if resources dir is missing
                                return loc.toFile();
                            }

                            // Unknown layout but still a directory on the classpath — use it
                            return loc.toFile();
                        }

                        // Code source points to a JAR or non-directory → use fallback (read via ClassLoader later)
                        return (fallback != null) ? fallback : projectDir;
                    }
                }
            }
        }
        catch (Exception ignore)
        {

        }

        // Local source checkout fallbacks (Maven/Gradle)
        File mavenResources = new File(projectDir, "src/main/resources");
        if (mavenResources.isDirectory()) return mavenResources;

        File mavenClasses = new File(projectDir, "target/classes");
        if (mavenClasses.isDirectory()) return mavenClasses;

        File gradleResources = new File(projectDir, "build/resources/main");
        if (gradleResources.isDirectory()) return gradleResources;

        return (fallback != null) ? fallback : projectDir;
    }

}
