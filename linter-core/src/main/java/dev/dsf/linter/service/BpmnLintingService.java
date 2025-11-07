package dev.dsf.linter.service;

import dev.dsf.linter.bpmn.BpmnLinter;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.linting.LintingOutput;
import dev.dsf.linter.util.resource.ResourceResolutionResult;

import java.io.File;

/**
 * Service for linting BPMN (Business Process Model and Notation) files in DSF process plugins.
 * <p>
 * This service extends {@link AbstractResourceLintingService} to provide BPMN-specific linting
 * functionality while inheriting common resource validation operations. It implements the
 * Template Method pattern, where the base class defines the linting algorithm structure and
 * this class provides BPMN-specific implementations of the abstract methods.
 * </p>
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *   <li>Linting individual BPMN files for syntax, structure, and business rule violations</li>
 *   <li>Validating BPMN file references in plugin definitions</li>
 *   <li>Checking resource location (inside/outside expected root, in dependencies)</li>
 *   <li>Creating appropriate lint items for different error scenarios</li>
 * </ul>
 *
 * <h3>Linting Process:</h3>
 * <p>
 * The service performs the following checks for each BPMN file:
 * </p>
 * <ol>
 *   <li><strong>File Existence:</strong> Verifies that referenced BPMN files exist</li>
 *   <li><strong>Location Validation:</strong> Checks if files are in expected resource root</li>
 *   <li><strong>Content Linting:</strong> Validates BPMN syntax, element names, field injections,
 *       FHIR resource references, and business logic constraints</li>
 *   <li><strong>Dependency Tracking:</strong> Identifies BPMN files found in dependency JARs</li>
 * </ol>
 *
 * <h3>Lint Item Types:</h3>
 * <p>
 * This service creates the following types of lint items:
 * </p>
 * <ul>
 *   <li>{@link PluginDefinitionBpmnFileReferencedButNotFoundLintItem} - When a referenced
 *       BPMN file cannot be found</li>
 *   <li>{@link PluginDefinitionBpmnFileReferencedFoundOutsideExpectedRootLintItem} - When
 *       a BPMN file is found outside the expected resource root</li>
 *   <li>{@link BpmnElementLintItemSuccess} - When a BPMN file is successfully validated</li>
 *   <li>{@link PluginDefinitionLintItemSuccess} - When a BPMN file is successfully parsed
 *       and linted at the plugin level</li>
 *   <li>{@link PluginDefinitionUnparsableBpmnResourceLintItem} - When a BPMN file cannot
 *       be parsed (handled automatically by the base class)</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * Logger logger = new ConsoleLogger();
 * BpmnLintingService service = new BpmnLintingService(logger);
 *
 * List<File> bpmnFiles = Arrays.asList(
 *     new File("bpe/update-allow-list.bpmn"),
 *     new File("bpe/download-allow-list.bpmn")
 * );
 * List<String> missingRefs = Collections.emptyList();
 * Map<String, ResourceResolutionResult> outsideRoot = new HashMap<>();
 * Map<String, ResourceResolutionResult> fromDependencies = new HashMap<>();
 * File pluginResourceRoot = new File("bpe");
 *
 * LintingResult result = service.lint(
 *     "my-plugin",
 *     bpmnFiles,
 *     missingRefs,
 *     outsideRoot,
 *     fromDependencies,
 *     pluginResourceRoot
 * );
 * }</pre>
 *
 * <h3>Integration:</h3>
 * <p>
 * This service is used by {@link dev.dsf.linter.service.PluginLintingOrchestrator} to lint
 * BPMN resources for each discovered DSF process plugin. The actual BPMN content validation
 * is delegated to {@link BpmnLinter}, which performs detailed checks on BPMN elements,
 * events, tasks, and their relationships to FHIR resources.
 * </p>
 *
 * @see AbstractResourceLintingService
 * @see BpmnLinter
 * @see dev.dsf.linter.service.PluginLintingOrchestrator
 * @since 1.1.0
 */
public class BpmnLintingService extends AbstractResourceLintingService {

    private final BpmnLinter bpmnLinter;

    /**
     * Constructs a new BPMN linting service with the specified logger.
     *
     * @param logger the logger instance for linting messages and errors
     */
    public BpmnLintingService(Logger logger) {
        super(logger);
        this.bpmnLinter = new BpmnLinter();
    }

    // IMPLEMENTATION OF ABSTRACT METHODS

    /**
     * Returns the resource type name for logging and error messages.
     *
     * @return the string "BPMN"
     */
    @Override
    protected String getResourceTypeName() {
        return "BPMN";
    }

    /**
     * Lints a single BPMN file and returns linting results.
     * <p>
     * This method delegates to {@link BpmnLinter#lintBpmnFile(java.nio.file.Path)} to perform
     * the actual BPMN validation, which includes checks for:
     * </p>
     * <ul>
     *   <li>BPMN syntax and structure validity</li>
     *   <li>Element naming conventions</li>
     *   <li>Field injection correctness (profile, messageName, instantiatesCanonical)</li>
     *   <li>FHIR resource references (ActivityDefinition, StructureDefinition)</li>
     *   <li>Business logic constraints</li>
     * </ul>
     *
     * @param pluginName the name of the plugin containing this BPMN file
     * @param resourceFile the BPMN file to lint
     * @return a {@link LintingOutput} containing all lint items discovered in the file
     */
    @Override
    protected LintingOutput lintSingleFile(String pluginName, File resourceFile) {
        return bpmnLinter.lintBpmnFile(resourceFile.toPath());
    }

    /**
     * Creates a lint item for a BPMN file that is referenced in the plugin definition
     * but cannot be found.
     *
     * @param pluginName the name of the plugin
     * @param missingRef the missing file reference
     * @return a {@link PluginDefinitionBpmnFileReferencedButNotFoundLintItem} with ERROR severity
     */
    @Override
    protected AbstractLintItem createMissingReferenceLintItem(String pluginName, String missingRef) {
        return new PluginDefinitionBpmnFileReferencedButNotFoundLintItem(
                pluginName,
                LinterSeverity.ERROR,
                new File(missingRef),
                "Referenced BPMN file not found"
        );
    }

    /**
     * Creates a lint item for a BPMN file that is found outside the expected resource root.
     * <p>
     * This typically indicates a misconfiguration where BPMN files are located in an
     * unexpected directory structure.
     * </p>
     *
     * @param pluginName the name of the plugin
     * @param reference the resource reference string
     * @param result the resolution result containing file location information
     * @return a {@link PluginDefinitionBpmnFileReferencedFoundOutsideExpectedRootLintItem}
     */
    @Override
    protected AbstractLintItem createOutsideRootLintItem(
            String pluginName,
            String reference,
            ResourceResolutionResult result) {

        return new PluginDefinitionBpmnFileReferencedFoundOutsideExpectedRootLintItem(
                pluginName,
                result.file().orElseThrow(),
                reference,
                result.expectedRoot(),
                result.actualLocation()
        );
    }

    /**
     * Creates a success lint item for a BPMN file that has been successfully validated.
     * <p>
     * The process ID is extracted from the linting output and used to identify the
     * BPMN process in the success message.
     * </p>
     *
     * @param pluginName the name of the plugin
     * @param resourceFile the BPMN file that was validated
     * @param output the linting output containing the process ID
     * @return a {@link BpmnElementLintItemSuccess} indicating successful validation
     */
    @Override
    protected AbstractLintItem createResourceSuccessItem(
            String pluginName,
            File resourceFile,
            LintingOutput output) {

        String processId = extractProcessId(output);
        return new BpmnElementLintItemSuccess(
                processId,
                resourceFile,
                processId,
                "Referenced BPMN file found and is readable."
        );
    }

    /**
     * Creates a plugin-level success lint item for a BPMN file.
     * <p>
     * This indicates that the BPMN file was successfully parsed and linted at the
     * plugin definition level, separate from individual element-level validations.
     * </p>
     *
     * @param pluginName the name of the plugin
     * @param resourceFile the BPMN file that was processed
     * @return a {@link PluginDefinitionLintItemSuccess} with a descriptive message
     */
    @Override
    protected AbstractLintItem createPluginSuccessItem(String pluginName, File resourceFile) {
        return new PluginDefinitionLintItemSuccess(
                resourceFile,
                pluginName,
                String.format("BPMN file '%s' successfully parsed and linted for plugin '%s'",
                        resourceFile.getName(), pluginName)
        );
    }

    /**
     * Checks if a lint item represents an unparsable BPMN resource.
     * <p>
     * This is used by the base class to identify parsing errors that should be
     * handled specially (e.g., not counted as regular lint errors).
     * </p>
     *
     * @param item the lint item to check
     * @return {@code true} if the item is a {@link PluginDefinitionUnparsableBpmnResourceLintItem}
     */
    @Override
    protected boolean isUnparsableItem(AbstractLintItem item) {
        return item instanceof PluginDefinitionUnparsableBpmnResourceLintItem;
    }

    // BPMN-SPECIFIC HELPER METHODS

    /**
     * Extracts and normalizes the process ID from linting output.
     * <p>
     * If the process ID is blank or missing, returns a placeholder string
     * "[unknown_process]" to ensure a valid identifier is always available.
     * </p>
     *
     * @param output the linting output containing the process ID
     * @return the process ID, or "[unknown_process]" if not available
     */
    private String extractProcessId(LintingOutput output) {
        String processId = output.getProcessId();
        return processId.isBlank() ? "[unknown_process]" : processId;
    }
}