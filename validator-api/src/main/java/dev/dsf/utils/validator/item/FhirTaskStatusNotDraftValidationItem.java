package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation error indicating that the <code>status</code> element in a FHIR Task
 * resource does not have the required value <code>draft</code>.
 * <p>
 * In DSF Task resources under development, the <code>status</code> must always be set to <code>draft</code>
 * to indicate that the resource is not yet active or in production.
 * </p>
 */
public class FhirTaskStatusNotDraftValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new error indicating that the <code>status</code> element does not have the value <code>draft</code>.
     *
     * @param resourceFile   the FHIR Task file where the issue was detected
     * @param fhirReference  a canonical or logical reference to the resource
     */
    public FhirTaskStatusNotDraftValidationItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.TASK_STATUS_NOT_DRAFT,
                "The <status> element must be set to 'draft'."
        );
    }

    /**
     * Constructs a new error with a custom message for an invalid <code>status</code> value.
     *
     * @param resourceFile   the FHIR Task file where the issue was detected
     * @param fhirReference  a canonical or logical reference to the resource
     * @param description    explanation of the issue
     */
    public FhirTaskStatusNotDraftValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.TASK_STATUS_NOT_DRAFT,
                description
        );
    }
}
