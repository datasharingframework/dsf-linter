package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a CodeSystem resource does not contain
 * any {@code <concept>} elements.
 *
 * <p>According to the DSF CodeSystem base profile, at least one {@code concept}
 * entry may is defined the set of codes provided by the system.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#CODE_SYSTEM_MISSING_CONCEPT}.</p>
 */
public class FhirCodeSystemMissingConceptValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item with the default message indicating the missing concept list.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical or local reference of the CodeSystem
     */
    public FhirCodeSystemMissingConceptValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "CodeSystem is missing required <concept> elements.", false);
    }

    /**
     * Constructs a new validation item with a custom message.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical or local reference of the CodeSystem
     * @param description   custom error description
     * @param custom        whether the message is custom
     */
    public FhirCodeSystemMissingConceptValidationItem(File resourceFile,
                                                      String fhirReference,
                                                      String description,
                                                      boolean custom)
    {
        super(ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.CODE_SYSTEM_MISSING_CONCEPT,
                description);
    }
}
