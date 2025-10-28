package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains a
 * {@code requester.identifier.value} without the {@code #{organization}} placeholder.
 *
 * <p>This validation issue corresponds to {@link LintingType#Fhir_TASK_REQUESTER_ID_NO_PLACEHOLDER}.</p>
 */
public class FhirTaskRequesterIdNoPlaceholderLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item indicating that the {@code requester.identifier.value}
     * is present but does not include the {@code #{organization}} placeholder.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskRequesterIdNoPlaceholderLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.Fhir_TASK_REQUESTER_ID_NO_PLACEHOLDER,
                description);
    }

    /**
     * Constructs a validation item using the default message for missing {@code #{organization}} placeholder.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskRequesterIdNoPlaceholderLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Task.requester.identifier.value does not contain the '#{organization}' placeholder.");
    }
}
