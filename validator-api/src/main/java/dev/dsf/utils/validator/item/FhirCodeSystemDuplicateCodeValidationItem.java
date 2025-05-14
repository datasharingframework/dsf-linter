package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a {@code <concept>} entry in a CodeSystem
 * contains a {@code <code>} value that is not unique.
 *
 * <p>According to the DSF CodeSystem base profile, all codes must be unique
 * within a single CodeSystem resource.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#CODE_SYSTEM_DUPLICATE_CODE}.</p>
 */
public class FhirCodeSystemDuplicateCodeValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item for a duplicated concept code.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical or local reference of the CodeSystem
     * @param duplicateCode the duplicate code that was found
     */
    public FhirCodeSystemDuplicateCodeValidationItem(File resourceFile,
                                                     String fhirReference,
                                                     String duplicateCode)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.CODE_SYSTEM_DUPLICATE_CODE,
                "Duplicate <code> value in CodeSystem <concept>: '" + duplicateCode + "'");
    }
}
