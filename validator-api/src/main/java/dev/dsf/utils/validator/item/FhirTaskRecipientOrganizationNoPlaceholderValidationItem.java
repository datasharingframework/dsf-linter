package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * <p>
 * Validation item that represents an issue where the required placeholder
 * {@code #{organization}} is missing from the recipient section of a FHIR Task resource.
 * </p>
 *
 * <p>
 * Specifically, this validation checks whether
 * {@code restriction.recipient.identifier.value} contains a dynamic
 * organization placeholder required in draft-stage Task resources.
 * </p>
 *
 * <p>
 * This item corresponds to the {@link ValidationType#FHIR_TASK_RECIPIENT_ORGANIZATION_NO_PLACEHOLDER}
 * type and is reported with {@link ValidationSeverity#WARN} severity.
 * </p>
 *
 * <p>
 * Placeholder validation ensures resource templates remain flexible and
 * environment-independent during development and deployment.
 * </p>
 *
 * <h3>Typical Use Case:</h3>
 * <ul>
 *   <li>Validating that {@code restriction.recipient.identifier.value} contains the {@code #{organization}} placeholder.</li>
 *   <li>Preventing static or hardcoded recipient organization values during development.</li>
 * </ul>
 *
 * <h3>Example Issue:</h3>
 * <pre>
 * {
 *   "severity": "WARN",
 *   "file": "task-example.xml",
 *   "reference": "Task/example",
 *   "type": "FHIR_TASK_RECIPIENT_ORGANIZATION_NO_PLACEHOLDER",
 *   "description": "restriction.recipient.identifier.value must contain '#{organization}'."
 * }
 * </pre>
 */
public class FhirTaskRecipientOrganizationNoPlaceholderValidationItem extends FhirElementValidationItem
{
    /**
     * Creates a new {@code FhirTaskRecipientOrganizationNoPlaceholderValidationItem} with a default description.
     *
     * @param resourceFile the FHIR resource file in which the issue was found (can be {@code null})
     * @param fhirReference the FHIR reference (e.g., {@code Task/example})
     */
    public FhirTaskRecipientOrganizationNoPlaceholderValidationItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_TASK_RECIPIENT_ORGANIZATION_NO_PLACEHOLDER,
                "Required field does not contain a dynamic placeholder"
        );
    }

    /**
     * Creates a new {@code FhirTaskRecipientOrganizationNoPlaceholderValidationItem} with a custom description.
     *
     * @param resourceFile the FHIR resource file in which the issue was found (can be {@code null})
     * @param fhirReference the FHIR reference (e.g., {@code Task/example})
     * @param description a custom description to provide additional context for the validation issue
     */
    public FhirTaskRecipientOrganizationNoPlaceholderValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_TASK_RECIPIENT_ORGANIZATION_NO_PLACEHOLDER,
                description
        );
    }
}
