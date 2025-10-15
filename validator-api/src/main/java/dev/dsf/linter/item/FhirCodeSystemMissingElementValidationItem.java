package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a required element in a CodeSystem resource is missing.
 *
 * <p>This applies to mandatory elements such as <code>url</code>, <code>name</code>,
 * <code>title</code>, <code>publisher</code>, <code>content</code>, or <code>caseSensitive</code>
 * as required by the DSF CodeSystem base profile.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#CODE_SYSTEM_MISSING_ELEMENT}.</p>
 */
public class FhirCodeSystemMissingElementValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item with a default message indicating the missing element name.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical URL or local reference of the CodeSystem
     * @param elementName   the name of the missing element (e.g. "url", "name")
     */
    public FhirCodeSystemMissingElementValidationItem(File resourceFile,
                                                      String fhirReference,
                                                      String elementName) {
        this(resourceFile, fhirReference,
                "CodeSystem is missing required element <" + elementName + ">.", false);
    }

    /**
     * Constructs a new validation item witch a custom message.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical URL or local reference of the CodeSystem
     * @param description   custom message describing the validation issue
     */
    public FhirCodeSystemMissingElementValidationItem(File resourceFile,
                                                      String fhirReference,
                                                      String description,
                                                      boolean custom) {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.CODE_SYSTEM_MISSING_ELEMENT,
                description);
    }
}
