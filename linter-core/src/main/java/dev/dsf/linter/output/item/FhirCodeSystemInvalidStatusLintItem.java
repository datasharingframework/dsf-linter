package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the {@code status} element of a CodeSystem
 * does not have the required value {@code unknown}.
 *
 * <p>This check is required by the DSF CodeSystem base profile to ensure
 * that the <code>status</code> field is not accidentally set to a permanent state
 * before deployment. The actual value will later be filled in by the BPE.</p>
 *
 * <p>This issue corresponds to {@link LintingType#CODE_SYSTEM_INVALID_STATUS}.</p>
 */
public class FhirCodeSystemInvalidStatusLintItem extends FhirElementLintItem {
    /**
     * Constructs a new Lint Item with the default description for invalid status value.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical URL or local reference of the CodeSystem
     * @param actualStatus  the value that was found (e.g. "active", "draft")
     */
    public FhirCodeSystemInvalidStatusLintItem(File resourceFile,
                                               String fhirReference,
                                               String actualStatus) {
        this(resourceFile, fhirReference,
                "CodeSystem <status> must be 'unknown' (found '" + actualStatus + "').", false);
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical URL or local reference of the CodeSystem
     * @param description   a custom lint description describing the issue
     * @param custom        whether this is a custom message
     */
    public FhirCodeSystemInvalidStatusLintItem(File resourceFile,
                                               String fhirReference,
                                               String description,
                                               boolean custom) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.CODE_SYSTEM_INVALID_STATUS,
                description);
    }
}
