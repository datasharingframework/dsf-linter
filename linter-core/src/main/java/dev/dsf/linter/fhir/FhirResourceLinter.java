package dev.dsf.linter.fhir;

import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.util.linting.AbstractFhirInstanceLinter;
import dev.dsf.linter.util.linting.LintingOutput;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates FHIR resources (XML/JSON) against conformance rules using pluggable resource-specific linters.
 * 
 * <p>
 * This class serves as the central facade for comprehensive FHIR (Fast Healthcare Interoperability Resources)
 * validation. It employs a plugin-based architecture using Java's {@link ServiceLoader} mechanism to
 * dynamically discover specialized linter implementations at runtime, enabling extensibility without
 * modifying the core infrastructure.
 * </p>
 *
 * <h2>Architecture</h2>
 * <p>
 * The validation process delegates to:
 * </p>
 * <ul>
 *   <li>{@link FhirFileLinter} - Handles file I/O, parsing, and dispatches validation</li>
 *   <li>{@link AbstractFhirInstanceLinter} implementations - Resource-specific validators
 *       (ActivityDefinition, Task, ValueSet, CodeSystem, Questionnaire, StructureDefinition)</li>
 * </ul>
 *
 * <h2>Linter Discovery</h2>
 * <p>
 * Linters are discovered in two phases:
 * </p>
 * <ol>
 *   <li><strong>ServiceLoader</strong>: Scans {@code META-INF/services/dev.dsf.linter.util.linting.AbstractFhirInstanceLinter}
 *       for registered implementations (production mode)</li>
 *   <li><strong>Fallback</strong>: Manually instantiates default linters if ServiceLoader finds none
 *       (typically in test environments)</li>
 * </ol>
 *
 * <h2>Validation Categories</h2>
 * <ul>
 *   <li><strong>Structural</strong>: FHIR base specifications (required elements, cardinality, data types)</li>
 *   <li><strong>Semantic</strong>: Logical consistency and business rules (status transitions, bindings, references)</li>
 *   <li><strong>Conformance</strong>: Adherence to profiles, extensions, and implementation guides</li>
 *   <li><strong>Best Practices</strong>: Recommendations for improvement</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <p>
 * All errors (parse failures, file I/O, unknown resource types) are captured and returned as
 * {@link LintingOutput} items rather than thrown as exceptions, ensuring graceful degradation.
 * </p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Logger logger = new ConsoleLogger();
 * FhirResourceLinter linter = new FhirResourceLinter(logger);
 * 
 * Path file = Paths.get("fhir/ActivityDefinition-example.xml");
 * LintingOutput output = linter.lintSingleFile(file);
 * 
 * if (output.hasErrors()) {
 *     output.getItems().forEach(item -> 
 *         System.out.println("[" + item.getSeverity() + "] " + item.getMessage()));
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe after construction. The internal linter list is effectively immutable,
 * allowing concurrent calls to {@link #lintSingleFile(Path)} from multiple threads.
 * </p>
 *
 * <h2>Extensibility</h2>
 * <p>
 * To add support for new FHIR resource types:
 * </p>
 * <ol>
 *   <li>Extend {@link AbstractFhirInstanceLinter} with validation logic</li>
 *   <li>Register in {@code META-INF/services/dev.dsf.linter.util.linting.AbstractFhirInstanceLinter}</li>
 *   <li>The linter will be auto-discovered and used</li>
 * </ol>
 *
 * @see AbstractFhirInstanceLinter
 * @see FhirFileLinter
 * @see LintingOutput
 * @see <a href="https://www.hl7.org/fhir/">FHIR Specification</a>
 * @since 1.0
 */
public final class FhirResourceLinter {
    /**
     * Cached list of all discovered resource-specific linters.
     * Populated via ServiceLoader or fallback instantiation; effectively immutable after construction.
     */
    private final List<AbstractFhirInstanceLinter> linters;
    
    /**
     * Handles file I/O, parsing, and delegates validation to resource-specific linters.
     */
    private final FhirFileLinter fileLinter;

    /**
     * Constructs a FHIR resource linter with the specified logger.
     * 
     * <p>
     * Initializes the linting infrastructure by discovering resource-specific linters via
     * {@link ServiceLoader}. If no linters are found (e.g., in test environments), falls back
     * to instantiating default linters for ActivityDefinition, Task, ValueSet, CodeSystem,
     * Questionnaire, and StructureDefinition resources.
     * </p>
     * 
     * @param logger the logger for recording validation operations and results; must not be {@code null}
     */
    public FhirResourceLinter(Logger logger) {
        // Discover linters via ServiceLoader OR fallback to manual list
        ServiceLoader<AbstractFhirInstanceLinter> loader =
                ServiceLoader.load(AbstractFhirInstanceLinter.class);
        linters = loader.stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toCollection(ArrayList::new));

        // Fallback in case ServiceLoader finds nothing (e.g., during tests)
        if (linters.isEmpty()) {
            linters.add(new FhirActivityDefinitionLinter());
            linters.add(new FhirTaskLinter());
            linters.add(new FhirValueSetLinter());
            linters.add(new FhirCodeSystemLinter());
            linters.add(new FhirQuestionnaireLinter());
            linters.add(new FhirStructureDefinitionLinter());
        }
        
        // Create the file linter with the discovered linters
        this.fileLinter = new FhirFileLinter(linters, logger);
    }

    /**
     * Validates a single FHIR resource file (XML or JSON format).
     * 
     * <p>
     * Reads the file, detects the format, parses the resource, selects the appropriate linter
     * based on resource type, and performs validation. All errors (file I/O, parsing, validation)
     * are captured and returned as {@link LintingOutput} items; no exceptions are thrown.
     * </p>
     *
     * <p>
     * Validation results include issues categorized as ERROR (spec violations), WARNING (best practice
     * deviations), or INFO (improvement suggestions).
     * </p>
     *
     * @param fhirFile the FHIR resource file to validate (XML/JSON); must not be {@code null}
     * @return a {@link LintingOutput} containing all validation issues; never {@code null}
     * @see FhirFileLinter#lintSingleFile(Path)
     * @see LintingOutput
     */
    public LintingOutput lintSingleFile(Path fhirFile) {
        return fileLinter.lintSingleFile(fhirFile);
    }
}