package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;
import java.io.File;

/**
 * <p>
 * Validation item that represents an issue where a required placeholder
 * (such as {@code #{organization}}) is missing in a critical FHIR resource field.
 * </p>
 *
 * <p>
 * In particular, this validation applies to fields like
 * {@code requester.identifier.value} in Task resources, but it can also be
 * used for any field where dynamic placeholder enforcement is expected.
 * </p>
 *
 * <p>
 * This item corresponds to the {@link ValidationType#FHIR_TASK_REQUESTER_ORGANIZATION_NO_PLACEHOLDER}
 * type and is reported with {@link ValidationSeverity#WARN} severity.
 * </p>
 *
 * <p>
 * Ensuring placeholders are present helps to keep resource files dynamic and
 * adaptable across different environments (e.g., during deployment or testing).
 * </p>
 *
 * <h3>Typical Use Case:</h3>
 * <ul>
 *   <li>Validating that {@code requester.identifier.value} in a FHIR Task resource contains the {@code #{organization}} placeholder.</li>
 *   <li>Flagging fields that mistakenly use hardcoded static values instead of placeholders.</li>
 * </ul>
 *
 * <h3>Example Issue:</h3>
 * <pre>
 * {
 *   "severity": "WARN",
 *   "file": "task-example.xml",
 *   "reference": "Task/example",
 *   "type": "FHIR_TASK_REQUESTER_ORGANIZATION_NO_PLACEHOLDER",
 *   "description": "requester.identifier.value must contain '#{organization}'."
 * }
 * </pre>
 */
public class FhirTaskRequesterOrganizationNoPlaceholderValidationItem extends FhirElementValidationItem
{
    /**
     * Creates a new {@code FhirFileVersionNoPlaceholderValidationItem} with a default description.
     *
     * @param resourceFile the FHIR resource file in which the issue was found (can be {@code null})
     * @param fhirReference the FHIR reference (e.g., {@code Task/example})
     */
    public FhirTaskRequesterOrganizationNoPlaceholderValidationItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_TASK_REQUESTER_ORGANIZATION_NO_PLACEHOLDER,
                "Required field does not contain a dynamic placeholder"
        );
    }

    /**
     * Creates a new {@code FhirFileVersionNoPlaceholderValidationItem} with a custom description.
     *
     * @param resourceFile the FHIR resource file in which the issue was found (can be {@code null})
     * @param fhirReference the FHIR reference (e.g., {@code Task/example})
     * @param description a custom description to provide additional context for the validation issue
     */
    public FhirTaskRequesterOrganizationNoPlaceholderValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_TASK_REQUESTER_ORGANIZATION_NO_PLACEHOLDER,
                description
        );
    }
}
