package dev.dsf.linter.output.item;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Represents a single linting result for a FHIR resource, such as a {@code Task}, {@code ActivityDefinition},
 * {@code StructureDefinition}, {@code ValueSet}, or {@code CodeSystem}.
 * <p>
 * This is a concrete class that can be instantiated directly. The {@link LintingType}
 * serves as the unique identifier for the type of issue, replacing the need for
 * many individual subclasses.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>
 * // Instead of: new FhirTaskMissingInputLintItem(resourceFile, fhirReference)
 * // Use:
 * new FhirElementLintItem(
 *     LinterSeverity.ERROR,
 *     LintingType.Fhir_TASK_MISSING_INPUT,
 *     resourceFile,
 *     fhirReference,
 *     "Task is missing input element"
 * );
 *
 * // Or with default message:
 * FhirElementLintItem.of(
 *     LinterSeverity.ERROR,
 *     LintingType.Fhir_TASK_MISSING_INPUT,
 *     resourceFile,
 *     fhirReference
 * );
 * </pre>
 *
 * @see LintingType
 * @see LinterSeverity
 */
public class FhirElementLintItem extends FhirLintItem {
    @JsonProperty("fhirReference")
    private final String fhirReference;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("resourceId")
    private final String resourceId;

    /**
     * Constructs a {@code FhirElementLintItem} with all parameters.
     *
     * @param severity      the lint severity
     * @param type          the lint type/category - serves as unique identifier
     * @param resourceFile  the name of the file from which the resource was loaded
     * @param fhirReference the canonical or local reference to the FHIR resource
     * @param description   a human-readable explanation of the issue
     */
    public FhirElementLintItem(
            LinterSeverity severity,
            LintingType type,
            String resourceFile,
            String fhirReference,
            String description) {
        super(severity, type, resourceFile);
        this.fhirReference = fhirReference;
        this.description = description;
        this.resourceId = deriveId(fhirReference);
    }

    /**
     * Constructs a {@code FhirElementLintItem} with explicit resourceId.
     *
     * @param severity      the lint severity
     * @param type          the lint type/category
     * @param resourceFile  the name of the file from which the resource was loaded
     * @param fhirReference the canonical or local reference to the FHIR resource
     * @param description   a human-readable explanation of the issue
     * @param resourceId    the resource ID, or null to derive it from the reference
     */
    public FhirElementLintItem(
            LinterSeverity severity,
            LintingType type,
            String resourceFile,
            String fhirReference,
            String description,
            String resourceId) {
        super(severity, type, resourceFile);
        this.fhirReference = fhirReference;
        this.description = description;
        this.resourceId = (resourceId == null || resourceId.isBlank())
                ? deriveId(fhirReference)
                : resourceId;
    }

    /**
     * Constructs a {@code FhirElementLintItem} with a File object.
     *
     * @param severity      the lint severity
     * @param type          the lint type/category
     * @param resourceFile  the FHIR resource file
     * @param fhirReference the FHIR reference
     * @param description   the description
     */
    public FhirElementLintItem(
            LinterSeverity severity,
            LintingType type,
            File resourceFile,
            String fhirReference,
            String description) {
        this(severity, type,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference, description);
    }

    /**
     * Factory method that uses the default message from LintingType.
     *
     * @param severity      the lint severity
     * @param type          the lint type (must have a default message)
     * @param resourceFile  the FHIR resource file
     * @param fhirReference the FHIR reference
     * @return a new FhirElementLintItem
     */
    public static FhirElementLintItem of(LinterSeverity severity,
                                         LintingType type,
                                         File resourceFile,
                                         String fhirReference) {
        return new FhirElementLintItem(severity, type, resourceFile, fhirReference,
                type.getDefaultMessageOrElse("FHIR element issue"));
    }

    /**
     * Factory method for SUCCESS items.
     *
     * @param resourceFile  the FHIR resource file
     * @param fhirReference the FHIR reference
     * @param description   the success description
     * @return a new FhirElementLintItem with SUCCESS severity
     */
    public static FhirElementLintItem success(File resourceFile,
                                              String fhirReference,
                                              String description) {
        return new FhirElementLintItem(LinterSeverity.SUCCESS, LintingType.SUCCESS,
                resourceFile, fhirReference, description);
    }

    /**
     * Factory method for SUCCESS items with resourceId.
     *
     * @param resourceFile  the FHIR resource file
     * @param fhirReference the FHIR reference
     * @param description   the success description
     * @param resourceId    the resource ID
     * @return a new FhirElementLintItem with SUCCESS severity
     */
    public static FhirElementLintItem success(File resourceFile,
                                              String fhirReference,
                                              String description,
                                              String resourceId) {
        return new FhirElementLintItem(LinterSeverity.SUCCESS, LintingType.SUCCESS,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference, description, resourceId);
    }

    /**
     * Backward compatible constructor (deprecated).
     *
     * @deprecated Use constructor with LintingType instead
     */
    @Deprecated
    public FhirElementLintItem(
            LinterSeverity severity,
            String resourceFile,
            String fhirReference,
            LintingType issueType,
            String description) {
        this(severity, issueType, resourceFile, fhirReference, description);
    }

    /**
     * Backward compatible constructor with resourceId (deprecated).
     *
     * @deprecated Use constructor with LintingType as second parameter instead
     */
    @Deprecated
    public FhirElementLintItem(
            LinterSeverity severity,
            String resourceFile,
            String fhirReference,
            LintingType issueType,
            String description,
            String resourceId) {
        this(severity, issueType, resourceFile, fhirReference, description, resourceId);
    }

    /**
     * Minimal constructor for fallback use cases.
     *
     * @deprecated Use the full constructor instead
     */
    @Deprecated
    public FhirElementLintItem(String description, LinterSeverity severity, String resourceFile) {
        super(severity, LintingType.UNKNOWN, resourceFile);
        this.fhirReference = "";
        this.description = description;
        this.resourceId = "unknown_resource";
    }

    public String getResourceFile() {
        return resourceFile;
    }

    public String getFhirReference() {
        return fhirReference;
    }

    public LintingType getIssueType() {
        return getType();
    }

    public String getResourceId() {
        return resourceId;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] %s (reference=%s, file=%s) : %s",
                getSeverity(),
                getType(),
                fhirReference,
                getResourceFile(),
                description
        );
    }

    /**
     * Derives a FHIR resource ID from the canonical or local reference.
     *
     * @param ref the FHIR reference
     * @return the derived resource ID
     */
    private static String deriveId(String ref) {
        if (ref == null || ref.isBlank())
            return "unknown_resource";

        String withoutVersion = ref.split("\\|", 2)[0];
        String lastPart = withoutVersion.substring(withoutVersion.lastIndexOf('/') + 1);
        if (lastPart.contains("#"))
            lastPart = lastPart.substring(lastPart.indexOf('#') + 1);
        return lastPart.isBlank() ? "unknown_resource" : lastPart;
    }
}
