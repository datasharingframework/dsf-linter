package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation error indicating that a ValueSet is missing one or more
 * <code>&lt;compose&gt;&lt;include&gt;</code> elements.
 * <p>
 * In DSF, each ValueSet must specify at least one include section within the compose block
 * to define which CodeSystems and concepts are covered.
 * </p>
 */
public class FhirValueSetMissingComposeIncludeValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error item for a ValueSet missing <code>&lt;compose&gt;&lt;include&gt;</code> elements.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     */
    public FhirValueSetMissingComposeIncludeValidationItem(
            File resourceFile,
            String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_COMPOSE_INCLUDE,
                "ValueSet is missing <compose><include> definition(s)"
        );
    }

    /**
     * Constructs a new validation error item for a ValueSet missing <code>&lt;compose&gt;&lt;include&gt;</code> elements,
     * explicitly referencing the resource ID if available.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetMissingComposeIncludeValidationItem(
            File resourceFile,
            String fhirReference,
            String resourceId)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_COMPOSE_INCLUDE,
                "ValueSet is missing <compose><include> definition(s)",
                resourceId
        );
    }

    @Override
    public String toString()
    {
        return String.format(
                "[%s] %s (fhirReference=%s, file=%s, description=%s)",
                getSeverity(),
                this.getClass().getSimpleName(),
                getFhirReference(),
                getResourceFile(),
                getDescription()
        );
    }
}
