package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains
 * an {@code instantiatesCanonical} reference to an unknown or unsupported
 * canonical URL.
 *
 * <p>According to the DSF {@code task-base} profile, the element {@code instantiatesCanonical}
 * must be present and reference the canonical URL of the associated {@code ActivityDefinition}.</p>
 *
 * <p>This validation issue corresponds to {@link LintingType#TASK_UNKNOWN_INSTANTIATES_CANONICAL}.</p>
 */
public class FhirTaskUnknownInstantiatesCanonicalLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item using a custom message for an unknown
     * {@code instantiatesCanonical} reference.
     *
     * @param resourceFile  the file where the FHIR Task resource was loaded from
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a custom message describing the validation issue
     */
    public FhirTaskUnknownInstantiatesCanonicalLintItem(File resourceFile,
                                                        String fhirReference,
                                                        String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.TASK_UNKNOWN_INSTANTIATES_CANONICAL,
                description);
    }

    /**
     * Constructs a validation item using the default error message for an
     * unknown {@code instantiatesCanonical} reference.
     *
     * @param resourceFile  the file where the FHIR Task resource was loaded from
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskUnknownInstantiatesCanonicalLintItem(File resourceFile,
                                                        String fhirReference) {
        this(resourceFile,
                fhirReference,
                "Task contains an instantiatesCanonical reference to an unknown canonical URL.");
    }
}
