package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import java.io.File;

/**
 * Validation item indicating that a FHIR resource file, which was referenced,
 * could not be found in ressource.
 */
public class FhirFileReferencedButNotFoundValidationItem extends FhirValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for a referenced but missing FHIR file.
     *
     * @param description  A message describing the validation issue.
     * @param severity     The validation severity (usually ERROR).
     * @param resourceFile The path or name of the FHIR file that was not found.
     */
    public FhirFileReferencedButNotFoundValidationItem(String description, ValidationSeverity severity,
                                                       File resourceFile)
    {
        super(severity, resourceFile != null ? resourceFile.getName() : "unknown.xml");
        this.description = description;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    @Override
    public String toString()
    {
        return String.format("[%s] %s (file=%s) : %s", getSeverity(), this.getClass().getSimpleName(),
                getFhirFile(), description);
    }
}