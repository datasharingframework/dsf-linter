package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a required element in a CodeSystem resource is missing.
 *
 * <p>This applies to mandatory elements such as <code>url</code>, <code>name</code>,
 * <code>title</code>, <code>publisher</code>, <code>content</code>, or <code>caseSensitive</code>
 * as required by the DSF CodeSystem base profile.</p>
 *
 * <p>This issue corresponds to {@link LintingType#CODE_SYSTEM_MISSING_ELEMENT}.</p>
 */
public class FhirCodeSystemMissingElementLintItem extends FhirElementLintItem {
    /**
     * Constructs a new validation item with a default message indicating the missing element name.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical URL or local reference of the CodeSystem
     * @param elementName   the name of the missing element (e.g. "url", "name")
     */
    public FhirCodeSystemMissingElementLintItem(File resourceFile,
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
    public FhirCodeSystemMissingElementLintItem(File resourceFile,
                                                String fhirReference,
                                                String description,
                                                boolean custom) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.CODE_SYSTEM_MISSING_ELEMENT,
                description);
    }
}
