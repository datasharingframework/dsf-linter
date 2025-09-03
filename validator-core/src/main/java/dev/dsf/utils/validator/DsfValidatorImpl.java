package dev.dsf.utils.validator;

import dev.dsf.utils.validator.analysis.LeftoverResourceDetector;
import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.report.ValidationReportGenerator;
import dev.dsf.utils.validator.service.BpmnValidationService;
import dev.dsf.utils.validator.service.FhirValidationService;
import dev.dsf.utils.validator.service.PluginValidationService;
import dev.dsf.utils.validator.service.ResourceDiscoveryService;
import dev.dsf.utils.validator.setup.ProjectSetupHandler;
import dev.dsf.utils.validator.util.api.ApiVersion;
import dev.dsf.utils.validator.util.api.ApiVersionHolder;
import dev.dsf.utils.validator.util.validation.DsfValidator;
import dev.dsf.utils.validator.util.validation.ValidationOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * <h1>DSF Validator Implementation (Refactored)</h1>
 *
 * <p>
 * This refactored implementation of the {@link DsfValidator} interface provides a clean,
 * modular architecture for validating DSF (Data Sharing Framework) compliant projects.
 * The validator now orchestrates specialized services, each responsible for a specific
 * aspect of the validation process.
 * </p>
 *
 * <h2>Architecture Overview</h2>
 * <p>
 * The refactored validator follows a service-oriented architecture with clear separation
 * of concerns:
 * </p>
 * <ul>
 * <li><strong>Orchestration Layer:</strong> This class coordinates the overall workflow</li>
 * <li><strong>Setup Services:</strong> {@link ProjectSetupHandler} manages project configuration</li>
 * <li><strong>Discovery Services:</strong> {@link ResourceDiscoveryService} handles resource discovery</li>
 * <li><strong>Validation Services:</strong> Specialized validators for BPMN, FHIR, and plugin aspects</li>
 * <li><strong>Analysis Services:</strong> {@link LeftoverResourceDetector} finds unreferenced resources</li>
 * <li><strong>Reporting Services:</strong> {@link ValidationReportGenerator} creates all reports</li>
 * </ul>
 *
 * <h2>Validation Workflow</h2>
 * <p>
 * The validation process follows a clear, sequential workflow:
 * </p>
 * <ol>
 * <li><strong>Setup Phase:</strong> Project environment configuration and classpath setup</li>
 * <li><strong>Discovery Phase:</strong> Plugin definition discovery and resource collection</li>
 * <li><strong>Validation Phase:</strong> Parallel validation of BPMN, FHIR, and plugin resources</li>
 * <li><strong>Analysis Phase:</strong> Detection of unreferenced resources</li>
 * <li><strong>Reporting Phase:</strong> Generation of comprehensive validation reports</li>
 * </ol>
 *
 * <h2>Benefits of Refactored Design</h2>
 * <ul>
 * <li>Each service has a single, well-defined responsibility</li>
 * <li>Services can be tested independently with mocked dependencies</li>
 * <li>New validation types can be added without modifying existing code</li>
 * <li>The main validation flow is clear and self-documenting</li>
 * <li>Services can be reused in different contexts</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Logger logger = new ConsoleLogger();
 * DsfValidator validator = new DsfValidatorImpl(logger);
 * Path projectPath = Paths.get("/path/to/dsf-project");
 *
 * try {
 *     ValidationOutput result = validator.validate(projectPath);
 *     if (result.hasErrors()) {
 *         System.err.println("Validation failed with errors");
 *         System.exit(1);
 *     }
 * } catch (ResourceValidationException e) {
 *     System.err.println("Critical validation error: " + e.getMessage());
 *     System.exit(1);
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This implementation is <strong>not thread-safe</strong>. Each validation operation
 * should use a separate instance or be externally synchronized.
 * </p>
 *
 * @author DSF Development Team
 * @version 2.0.0
 * @since 1.0.0
 *
 * @see DsfValidator
 * @see ValidationOutput
 * @see ProjectSetupHandler
 * @see ResourceDiscoveryService
 * @see BpmnValidationService
 * @see FhirValidationService
 * @see PluginValidationService
 * @see LeftoverResourceDetector
 * @see ValidationReportGenerator
 */
public class DsfValidatorImpl implements DsfValidator {

    private final Logger logger;
    private final ProjectSetupHandler setupHandler;
    private final ResourceDiscoveryService discoveryService;
    private final BpmnValidationService bpmnValidator;
    private final FhirValidationService fhirValidator;
    private final PluginValidationService pluginValidator;
    private final LeftoverResourceDetector leftoverDetector;
    private final ValidationReportGenerator reportGenerator;

    /**
     * Constructs a new DsfValidatorImpl with all required services.
     *
     * @param logger the logger for output messages
     */
    public DsfValidatorImpl(Logger logger) {
        this.logger = logger;
        this.setupHandler = new ProjectSetupHandler(logger);
        this.discoveryService = new ResourceDiscoveryService(logger);
        this.bpmnValidator = new BpmnValidationService(logger);
        this.fhirValidator = new FhirValidationService(logger);
        this.pluginValidator = new PluginValidationService(logger);
        this.leftoverDetector = new LeftoverResourceDetector(logger);
        this.reportGenerator = new ValidationReportGenerator(logger);
    }

    /**
     * Orchestrates the complete validation workflow for DSF-compliant process plugin projects.
     *
     * <p>This method coordinates all validation services to provide a comprehensive
     * validation of the project. The workflow is divided into clear phases, each
     * handled by a specialized service.</p>
     *
     * <h3>Validation Phases</h3>
     * <ol>
     *   <li><strong>Setup Phase:</strong> Configures the project environment,
     *       builds Maven projects if necessary, and creates the appropriate ClassLoader.</li>
     *   <li><strong>Discovery Phase:</strong> Discovers the ProcessPluginDefinition,
     *       determines resource roots, and collects all referenced resources.</li>
     *   <li><strong>Validation Phase:</strong> Validates BPMN files, FHIR resources,
     *       and plugin configuration in parallel.</li>
     *   <li><strong>Analysis Phase:</strong> Detects unreferenced resources that
     *       exist on disk but are not referenced in the plugin definition.</li>
     *   <li><strong>Reporting Phase:</strong> Generates comprehensive reports
     *       including individual file reports and aggregated summaries.</li>
     * </ol>
     *
     * <h3>Error Handling</h3>
     * <p>The method implements fail-fast error handling for critical issues:</p>
     * <ul>
     *   <li>Invalid project directory</li>
     *   <li>Maven build failures</li>
     *   <li>Missing or multiple ProcessPluginDefinitions</li>
     *   <li>Resource parsing errors</li>
     * </ul>
     *
     * <h3>ClassLoader Management</h3>
     * <p>The method properly manages the thread context ClassLoader, ensuring it is
     * restored to its original state even if validation fails.</p>
     *
     * @param path absolute path to the project root directory to validate
     * @return a {@link ValidationOutput} containing all validation results and statistics
     *
     * @throws ResourceValidationException if any referenced resource file fails parsing
     * @throws IllegalStateException if project setup or discovery fails
     * @throws IOException if file system operations fail
     * @throws InterruptedException if Maven build is interrupted
     * @throws MissingServiceRegistrationException if ServiceLoader registration fails
     *
     * @see ValidationOutput
     */
    @Override
    public ValidationOutput validate(Path path)
            throws ResourceValidationException, IllegalStateException, IOException,
            InterruptedException, MissingServiceRegistrationException {

        // Validate input
        if (!Files.isDirectory(path)) {
            logger.error("ERROR: This validation workflow only supports project directories.");
            return ValidationOutput.empty();
        }

        logger.info("=== Starting DSF Validation ===");
        logger.info("Project path: " + path.toAbsolutePath());

        // Store the previous ClassLoader to restore later
        final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            // Phase 1: Project Setup
            logger.info("\n--- Phase 1: Project Setup ---");
            ProjectSetupHandler.ProjectContext context = setupHandler.setupValidationEnvironment(path);

            // Phase 2: Resource Discovery
            logger.info("\n--- Phase 2: Resource Discovery ---");
            ResourceDiscoveryService.DiscoveryResult discovery = discoveryService.discoverResources(context);

            // Phase 3: Resource Validation
            logger.info("\n--- Phase 3: Resource Validation ---");

            // Validate BPMN resources
            logger.info("Validating BPMN resources...");
            BpmnValidationService.ValidationResult bpmnResults = bpmnValidator.validate(
                    discovery.bpmnFiles(),
                    discovery.missingBpmnRefs()
            );

            // Validate FHIR resources
            logger.info("Validating FHIR resources...");
            FhirValidationService.ValidationResult fhirResults = fhirValidator.validate(
                    discovery.fhirFiles(),
                    discovery.missingFhirRefs()
            );

            // Validate plugin configuration
            logger.info("Validating plugin configuration...");
            PluginValidationService.ValidationResult pluginResults = pluginValidator.validate(
                    context.projectPath()
            );

            // Phase 4: Leftover Analysis
            logger.info("\n--- Phase 4: Leftover Analysis ---");
            LeftoverResourceDetector.AnalysisResult leftoverResults = leftoverDetector.analyze(
                    context.projectDir(),
                    discovery.resourcesDir(),
                    discovery.referencedPaths().stream()
                            .filter(ref -> ref.endsWith(".bpmn"))
                            .collect(java.util.stream.Collectors.toSet()),
                    discovery.referencedPaths().stream()
                            .filter(ref -> !ref.endsWith(".bpmn"))
                            .collect(java.util.stream.Collectors.toSet())
            );

            // Phase 5: Report Generation
            logger.info("\n--- Phase 5: Report Generation ---");
            ValidationOutput output = reportGenerator.generateReports(
                    bpmnResults,
                    fhirResults,
                    pluginResults,
                    leftoverResults
            );

            // Final Summary
            printFinalSummary(output);
            printDetectedApiVersion();

            return output;

        } finally {
            // Always restore the original ClassLoader
            setupHandler.restoreClassLoader(previousClassLoader);
            logger.debug("ClassLoader context restored.");
        }
    }

    /**
     * Prints the final validation summary.
     *
     * @param output the validation output
     */
    private void printFinalSummary(ValidationOutput output) {
        int totalIssues = output.validationItems().size();
        int errors = output.getErrorCount();
        int warnings = output.getWarningCount();

        logger.info("\n=== Validation Complete ===");
        logger.info(String.format(
                "Total issues: %d (Errors: %d, Warnings: %d)",
                totalIssues, errors, warnings
        ));

        if (errors > 0) {
            logger.error("Validation completed with errors.");
        } else if (warnings > 0) {
            logger.warn("Validation completed with warnings.");
        } else {
            logger.info("Validation completed successfully.");
        }
    }

    /**
     * Prints the detected DSF BPE API version.
     */
    private void printDetectedApiVersion() {
        ApiVersion apiVersion = ApiVersionHolder.getVersion();
        String versionStr = switch (apiVersion) {
            case V1 -> "v1";
            case V2 -> "v2";
            case UNKNOWN -> "unknown";
        };
        logger.info("\u001B[31mDetected DSF BPE API version: " + versionStr + "\u001B[0m");
    }
}