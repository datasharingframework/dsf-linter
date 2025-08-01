package dev.dsf.utils.validator.plugin;

import dev.dsf.utils.validator.DsfValidatorImpl;
import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.MissingServiceLoaderRegistrationValidationItem;
import dev.dsf.utils.validator.util.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;


import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.dsf.utils.validator.util.ReportCleaner.prepareCleanReportDirectory;

/**
 * <h2>DSF Validator Maven Plugin – Mojo: verify</h2>
 *
 * <p>
 * This Mojo executes the Digital Sample Framework (DSF) validation during the Maven lifecycle
 * (default phase: {@code verify}). It performs structural and semantic validation of BPMN and FHIR
 * artifacts within a DSF process plugin and reports the results as structured JSON.
 * </p>
 *
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Builds a classloader from {@code target/classes} and compile/runtime dependencies</li>
 *   <li>Checks ServiceLoader registration of {@code dev.dsf.bpe.v2.ProcessPluginDefinition}</li>
 *   <li>Runs DSF BPMN and FHIR validations using {@link dev.dsf.utils.validator.DsfValidatorImpl}</li>
 *   <li>Writes categorized JSON reports to {@code ${project.build.directory}/dsf-validation-reports}</li>
 *   <li>Detects and reports the DSF BPE API version used in the project</li>
 * </ul>
 *
 * <h3>Command-line Usage</h3>
 * <pre>{@code
 * mvn dev.dsf.utils.validator:validator-maven-plugin:verify
 * }</pre>
 *
 * <h3>Configuration Options</h3>
 * <ul>
 *   <li><strong>-Ddsf.skip=true</strong> – Skips validator execution entirely</li>
 *   <li><strong>-Ddsf.failOnMissingService=false</strong> – Do not fail build on missing ServiceLoader registration (warn only)</li>
 * </ul>
 *
 * <h3>Report Output Structure</h3>
 * <pre>
 * ${project.build.directory}/dsf-validation-reports/
 * ├── service-loader.json                      ← Results of ServiceLoader registration checks
 * ├── bpmnReports/
 * │   ├── success/                             ← Individual and aggregated BPMN success items
 * │   ├── other/                               ← BPMN items with warnings/errors
 * │   └── bpmn_issues_aggregated.json
 * ├── fhirReports/
 * │   ├── success/                             ← Individual and aggregated FHIR success items
 * │   ├── other/                               ← FHIR items with warnings/errors
 * │   └── fhir_issues_aggregated.json
 * ├── pluginReports/                           ← Plugin validation directory
 * │   ├── success/                             ← Contains only PluginValidationItemSuccess
 * │   ├── other/                               ← Contains PluginValidationItems excluding success
 * │   └── plugin_issues_aggregated.json        ← All PluginValidationItems (success + others)
 * └── aggregated.json                          ← Combined BPMN + FHIR + Plugin validation results
 * </pre>
 *
 * <h3>Requirements</h3>
 * <ul>
 *   <li>A compiled DSF plugin project (typically {@code mvn compile} is already executed)</li>
 *   <li>A proper ServiceLoader entry in {@code META-INF/services/dev.dsf.bpe.v2.ProcessPluginDefinition}</li>
 * </ul>
 *
 * @author Khalil Malla
 * @version 1.2
 * @since 1.0.0
 *
 * @see dev.dsf.utils.validator.DsfValidatorImpl
 * @see dev.dsf.utils.validator.util.ApiRegistrationValidationSupport
 * @see org.apache.maven.plugin.AbstractMojo
 * @see <a href="https://maven.apache.org/developers/mojo-api.html">Maven Mojo API</a>
 */
@Mojo(
        name = "verify",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        threadSafe = true
)
public class ValidateMojo extends AbstractMojo
{

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File baseDirectory;

    /**
     * The build directory of the project (typically the {@code target} folder).
     * The report folder will be placed inside this directory under "dsf-validation-reports".
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File buildDirectory;

    /**
     * The output directory containing compiled classes (target/classes).
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private String classesDirectory;

    /**
     * Project classpath elements (compile + runtime) for resolving project dependencies.
     */
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> classpathElements;

    /** Fail the build if ServiceLoader registration is missing. */
    @Parameter(property = "dsf.failOnMissingService", defaultValue = "true")
    private boolean failOnMissingService;

    /** Allow skipping the plugin goal entirely. */
    @Parameter(property = "dsf.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Executes the BPMN and FHIR validation process with ServiceLoader checks.
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip)
        {
            System.out.println("DSF validation is skipped (dsf.skip=true).");
            return;
        }

        // 1. Determine project directory
        final File projectDir = (baseDirectory != null) ? baseDirectory : new File(".");

        // 2. Create validator
        DsfValidatorImpl validator = new DsfValidatorImpl();

        // 3. Clear any previous validation state
        ApiRegistrationValidationSupport.clearReportedCache();

        // 4. Pre-build ServiceLoader check (optional, for developer feedback)
        try {
            validator.runServiceLoaderCheck("Pre-build check", projectDir.toPath());
        } catch (IOException | MissingServiceRegistrationException e) {
            throw new RuntimeException(e);
        }

        // 5. (Maven build step is skipped since Mojo runs during build)

        // 6. Post-build ServiceLoader check
        try {
            validator.runServiceLoaderCheck("Post-build check", projectDir.toPath());
        } catch (IOException | MissingServiceRegistrationException e) {
            throw new RuntimeException(e);
        }

        // 7. Detect and store API version using new enum-based approach
        ApiVersionDetector detector = new ApiVersionDetector();
        Optional<DetectedVersion> detectedOpt;
        try {
            detectedOpt = detector.detect(projectDir.toPath());
        } catch (MissingServiceRegistrationException e) {
            throw new RuntimeException(e);
        }
        if (detectedOpt.isPresent()) {
            ApiVersionHolder.setVersion(detectedOpt.get().version());
        } else {
            ApiVersionHolder.setVersion(ApiVersion.UNKNOWN);
        }

        // 8. Prepare/clean report directory
        File reportRoot = new File(buildDirectory, "dsf-validation-reports");
        prepareCleanReportDirectory(reportRoot);
        File pluginRoot = new File(reportRoot, "pluginReports");

        // 9. Collect plugin items and write plugin reports
        List<AbstractValidationItem> pluginItems;
        try {
            pluginItems = validator.collectPluginItems(projectDir.toPath());
        } catch (IOException | MissingServiceRegistrationException e) {
            throw new MojoExecutionException("Failed to collect plugin items", e);
        }

        // Log plugin items found
        System.out.println("\n=== Plugin Validation Results ===");
        if (pluginItems.isEmpty()) {
            System.out.println("No plugin validation items found.");
        } else {
            System.out.println("Found " + pluginItems.size() + " plugin validation item(s):");
            for (AbstractValidationItem item : pluginItems) {
                System.out.println(" - " + item.getSeverity() + ": " + item.getClass().getSimpleName());
            }
        }

        try {
            validator.writePluginReports(pluginItems, pluginRoot);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write plugin validation reports", e);
        }

        // 10. BPMN/FHIR validation (with ClassLoader setup)
        List<AbstractValidationItem> allBpmnItems;
        List<AbstractValidationItem> allFhirItems;

        try (URLClassLoader projectClassLoader = new URLClassLoader(
                buildClasspathUrls(classesDirectory, classpathElements).toArray(URL[]::new),
                getClass().getClassLoader()))
        {
            final Thread current = Thread.currentThread();
            final ClassLoader prev = current.getContextClassLoader();
            current.setContextClassLoader(projectClassLoader);

            try {
                allBpmnItems = validator.validateAllBpmnFilesSplitNewStructure(projectDir, reportRoot);
                allFhirItems = validator.validateAllFhirResourcesSplitNewStructure(projectDir, reportRoot);
            } finally {
                current.setContextClassLoader(prev);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed during BPMN/FHIR validation", e);
        }

        // 11. Write aggregated report (BPMN + FHIR + Plugin)
        List<AbstractValidationItem> combined = new ArrayList<>(allBpmnItems);
        combined.addAll(allFhirItems);
        combined.addAll(pluginItems);
        new ValidationOutput(combined).writeResultsAsJson(new File(reportRoot, "aggregated.json"));

        System.out.println(String.format("%nValidation finished – %d issue(s) found (%d BPMN/FHIR + %d plugin).",
                combined.size(), allBpmnItems.size() + allFhirItems.size(), pluginItems.size()));
        System.out.println("Reports written to: " + reportRoot.getAbsolutePath());
        System.out.println("Plugin reports written to: " + pluginRoot.getAbsolutePath());

        // Print detected API version in red using new enum-based approach
        ApiVersion apiVersion = ApiVersionHolder.getVersion();
        String versionStr = switch (apiVersion) {
            case V1 -> "v1";
            case V2 -> "v2";
            case UNKNOWN -> "unknown";
        };
        System.out.println("\u001B[31mDetected DSF BPE API version: " + versionStr + "\u001B[0m");

        // 12. Fail build if missing ServiceLoader registration and failOnMissingService=true
        boolean hasMissingService = pluginItems.stream()
                .anyMatch(i -> i instanceof MissingServiceLoaderRegistrationValidationItem);

        if (hasMissingService && failOnMissingService)
        {
            throw new MojoFailureException(
                    "Missing ServiceLoader registration for ProcessPluginDefinition. " +
                            "See pluginReports/ or aggregated.json under " + reportRoot.getAbsolutePath());
        }
    }


    /** Build URLs for project classes and compile classpath. */
    private static List<URL> buildClasspathUrls(String classesDir, List<String> elements)
            throws IOException
    {
        List<URL> urls = new ArrayList<>();
        urls.add(new File(classesDir).toURI().toURL());
        for (String e : elements) urls.add(new File(e).toURI().toURL());
        return urls;
    }


}
