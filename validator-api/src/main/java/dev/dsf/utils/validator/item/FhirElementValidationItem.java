package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import java.io.File;

/**
 * <p>
 * FHIR element validation item. Extends {@link FhirValidationItem} by adding
 * optional fields describing a particular FHIR resource reference, such as a
 * canonical URL or a pointer to the resource file.
 * </p>
 *
 * <p>
 * Subclasses often represent specific validation issues around certain FHIR elements
 * (e.g., StructureDefinition, ActivityDefinition, or other resources).
 * </p>
 */
public abstract class FhirElementValidationItem extends FhirValidationItem
{
    /**
     * The canonical or URL reference pointing to the FHIR resource (e.g. profile URL, activityDefinition URL).
     */
    protected final String fhirReference;

    /**
     * The (optional) actual file that was being parsed or checked, if available.
     */
    protected final File fhirResourceFile;

    /**
     * Constructs a new FHIR element validation item with a specific severity, resource reference, and optional file.
     *
     * @param severity       the validation severity
     * @param fhirReference  a canonical or other identifying string reference to the FHIR resource
     * @param fhirFile       optional file pointer (may be null if unknown)
     */
    public FhirElementValidationItem(ValidationSeverity severity, String fhirReference, File fhirFile)
    {
        super(severity);
        this.fhirReference = fhirReference;
        this.fhirResourceFile = fhirFile;
    }

    public String getFhirReference()
    {
        return fhirReference;
    }

    public File getFhirResourceFile()
    {
        return fhirResourceFile;
    }


    @Override
    public String getDescription()
    {
        return "FHIR Element Validation Item for resource: " + (fhirReference != null ? fhirReference : "N/A");
    }
}
