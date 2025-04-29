package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the date field in an ActivityDefinition does not contain a placeholder.
 * Corresponds to {@link ValidationType#FILE_DATE_NO_PLACEHOLDER}.
 */
public class FhirFileDateNoPlaceholderValidationItem extends FhirElementValidationItem
{
    public FhirFileDateNoPlaceholderValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.WARN, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, ValidationType.FILE_DATE_NO_PLACEHOLDER,
                "ActivityDefinition date does not contain a placeholder");
    }

    public FhirFileDateNoPlaceholderValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.WARN, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, ValidationType.FILE_DATE_NO_PLACEHOLDER, description);
    }
}
