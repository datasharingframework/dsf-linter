package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code status} element of a CodeSystem
 * does not have the required value {@code unknown}.
 *
 * <p>This check is required by the DSF CodeSystem base profile to ensure
 * that the <code>status</code> field is not accidentally set to a permanent state
 * before deployment. The actual value will later be filled in by the BPE.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#CODE_SYSTEM_INVALID_STATUS}.</p>
 */
public class FhirCodeSystemInvalidStatusValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item with the default description for invalid status value.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical URL or local reference of the CodeSystem
     * @param actualStatus  the value that was found (e.g. "active", "draft")
     */
    public FhirCodeSystemInvalidStatusValidationItem(File resourceFile,
                                                     String fhirReference,
                                                     String actualStatus)
    {
        this(resourceFile, fhirReference,
                "CodeSystem <status> must be 'unknown' (found '" + actualStatus + "').", false);
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical URL or local reference of the CodeSystem
     * @param description   a custom validation message describing the issue
     * @param custom        whether this is a custom message
     */
    public FhirCodeSystemInvalidStatusValidationItem(File resourceFile,
                                                     String fhirReference,
                                                     String description,
                                                     boolean custom)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.CODE_SYSTEM_INVALID_STATUS,
                description);
    }
}
