package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the business key check was skipped for a FHIR Task
 * because the status does not require specific business key validation rules.
 *
 * <p>This informational item is reported when the Task status is neither in the set of
 * statuses that require a business key nor is it 'draft' (which prohibits business key).
 * In such cases, the business key validation is skipped as it's not applicable.</p>
 *
 * <p>This validation item corresponds to an INFO severity level and helps provide
 * transparency about which validation checks are being performed or skipped.</p>
 */
public class FhirTaskBusinessKeyCheckIsSkippedValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating that business key validation was skipped.
     *
     * @param resourceFile  the file where the Task resource was found
     * @param fhirReference the canonical URL or local reference to the resource
     * @param description   a human-readable message describing why the check was skipped
     */
    public FhirTaskBusinessKeyCheckIsSkippedValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_TASK_BUSINESS_KEY_CHECK_SKIPPED,
                description);
    }

    /**
     * Constructs a validation item using the default message for skipped business key check.
     *
     * @param resourceFile  the file where the Task resource was found
     * @param fhirReference the canonical URL or local reference to the resource
     */
    public FhirTaskBusinessKeyCheckIsSkippedValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Business key validation check was skipped for this Task status.");
    }
}

