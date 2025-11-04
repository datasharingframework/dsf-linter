package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a CodeSystem resource is missing the required
 * read access tag (system = http://dsf.dev/fhir/CodeSystem/read-access-tag, code = ALL).
 *
 * <p>This validation rule is part of the DSF CodeSystem profile and ensures that
 * all CodeSystems specify the expected read-access policy.</p>
 *
 * <p>This issue corresponds to {@link LintingType#MISSING_READ_ACCESS_TAG}.</p>
 */
public class FhirCodeSystemMissingReadAccessTagLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item using the default error message for a missing read access tag.
     *
     * @param resourceFile  the file in which the CodeSystem resource was found
     * @param fhirReference the canonical URL or local reference of the CodeSystem
     */
    public FhirCodeSystemMissingReadAccessTagLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "CodeSystem is missing required meta.tag with system 'http://dsf.dev/fhir/CodeSystem/read-access-tag' and code 'ALL'.");
    }

    /**
     * Constructs a Lint Item with a custom error description.
     *
     * @param resourceFile  the file in which the CodeSystem resource was found
     * @param fhirReference the canonical URL or local reference of the CodeSystem
     * @param description   custom message describing the Lint issue
     */
    public FhirCodeSystemMissingReadAccessTagLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.MISSING_READ_ACCESS_TAG,
                description);
    }
}
