package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a {@code <concept>} element in a CodeSystem resource
 * is missing the required {@code <display>} child element.
 *
 * <p>According to the DSF CodeSystem base profile, each {@code concept} must provide
 * a human-readable display string using the {@code <display>} element.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#CODE_SYSTEM_CONCEPT_MISSING_DISPLAY}.</p>
 */
public class FhirCodeSystemConceptMissingDisplayValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item using the default message for a missing <display> element.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical or local reference of the CodeSystem
     */
    public FhirCodeSystemConceptMissingDisplayValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "CodeSystem <concept> element is missing required <display>.", false);
    }

    /**
     * Constructs a validation item with a custom message.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical or local reference of the CodeSystem
     * @param description   a custom error message
     * @param custom        whether the message is custom
     */
    public FhirCodeSystemConceptMissingDisplayValidationItem(File resourceFile,
                                                             String fhirReference,
                                                             String description,
                                                             boolean custom)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.CODE_SYSTEM_CONCEPT_MISSING_DISPLAY,
                description);
    }
}
