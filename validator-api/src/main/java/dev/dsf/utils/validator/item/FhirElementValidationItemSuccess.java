package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Represents a successful FHIR validation result.
 * <p>
 * This class extends {@link FhirElementValidationItem} and fixes its
 * {@link ValidationSeverity} to {@code SUCCESS} as well as the {@link ValidationType} to {@code SUCCESS}.
 * </p>
 * <p>
 * References:
 * <ul>
 *   <li>
 *     HL7 FHIR Overview:
 *     <a href="https://hl7.org/fhir/overview.html">https://hl7.org/fhir/overview.html</a>
 *   </li>
 * </ul>
 * </p>
 */
public class FhirElementValidationItemSuccess extends FhirElementValidationItem
{
    /**
     * Constructs a new success validation item for FHIR with the given parameters.
     *
     * @param resourceFile   the FHIR resource file being validated
     * @param fhirReference  a canonical URL or reference identifying the FHIR resource
     * @param description    a short message describing the successful validation result
     */
    public FhirElementValidationItemSuccess(
            File resourceFile,
            String fhirReference,
            String description)
    {
        super(ValidationSeverity.SUCCESS, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, ValidationType.SUCCESS, description);
    }

    /**
     * Constructs a new success validation item for FHIR with the given parameters, including a resource ID.
     *
     * @param resourceFile   the FHIR resource file being validated
     * @param fhirReference  a canonical URL or reference identifying the FHIR resource
     * @param description    a short message describing the successful validation result
     * @param resourceId     the FHIR resource ID, if known
     */
    public FhirElementValidationItemSuccess(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId)
    {
        super(ValidationSeverity.SUCCESS, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, ValidationType.SUCCESS, description, resourceId);
    }

    @Override
    public String toString()
    {
        return String.format(
                "[%s] %s (fhirReference=%s, file=%s, description=%s)",
                ValidationSeverity.SUCCESS,
                this.getClass().getSimpleName(),
                getFhirReference(),
                getResourceFile(),
                getDescription()
        );
    }
}
