package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation warning indicating that the {@code instantiatesCanonical} element in a FHIR Task
 * resource is missing expected placeholder values (e.g., {@code #{version}}).
 * <p>
 * During development, Task resources should contain placeholder values that will be replaced
 * during the build process. This warning helps identify cases where placeholders are missing.
 * </p>
 */
public class FhirTaskInstantiatesCanonicalPlaceholderValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new warning indicating that the {@code instantiatesCanonical} element is missing placeholders.
     *
     * @param resourceFile   the FHIR Task file where the issue was detected
     * @param fhirReference  a canonical or logical reference to the resource
     */
    public FhirTaskInstantiatesCanonicalPlaceholderValidationItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.TASK_INSTANTIATES_CANONICAL_PLACEHOLDER,
                "The <instantiatesCanonical> element is missing expected placeholder values (e.g., #{version})."
        );
    }

    /**
     * Constructs a new warning with a custom message for missing placeholder detection.
     *
     * @param resourceFile   the FHIR Task file where the issue was detected
     * @param fhirReference  a canonical or logical reference to the resource
     * @param description    explanation of the issue
     */
    public FhirTaskInstantiatesCanonicalPlaceholderValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.TASK_INSTANTIATES_CANONICAL_PLACEHOLDER,
                description
        );
    }
}
