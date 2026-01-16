package dev.dsf.linter.output.item;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

/**
 * Base class for FHIR linting items.
 * <p>
 * Stores the resource file name for JSON serialization.
 * </p>
 */
public abstract class FhirLintItem extends AbstractLintItem {

    @JsonProperty("resourceFile")
    protected final String resourceFile;

    /**
     * Constructs a FhirLintItem with severity, type, and resource file.
     *
     * @param severity     the linting severity
     * @param type         the linting type/category
     * @param resourceFile the resource file name
     */
    public FhirLintItem(LinterSeverity severity, LintingType type, String resourceFile) {
        super(severity, type);
        this.resourceFile = (resourceFile != null) ? resourceFile : "unknown.xml";
    }

    /**
     * Constructs a FhirLintItem with severity and resource file (backward compatible).
     *
     * @param severity     the linting severity
     * @param resourceFile the resource file name
     * @deprecated Use {@link #FhirLintItem(LinterSeverity, LintingType, String)} instead
     */
    @Deprecated
    public FhirLintItem(LinterSeverity severity, String resourceFile) {
        this(severity, LintingType.UNKNOWN, resourceFile);
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String getFhirFile() {
        return resourceFile;
    }

    @Override
    public String toString() {
        return getFhirFile();
    }
}
