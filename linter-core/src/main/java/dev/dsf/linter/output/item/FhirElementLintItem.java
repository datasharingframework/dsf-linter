package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;


/**
 * Represents a single linting result for a FHIR resource, such as a {@code Task}, {@code ActivityDefinition},
 * {@code StructureDefinition}, {@code ValueSet}, or {@code CodeSystem}.
 * <p>
 * This class extends {@link FhirLintItem} and encapsulates the following:
 * <ul>
 *   <li>The name of the resource file where the issue was found</li>
 *   <li>A FHIR reference, such as a canonical URL or identifier for the resource</li>
 *   <li>A {@link LintingType} describing the specific category of issue</li>
 *   <li>A {@code resourceId}, which is derived from the FHIR reference if not explicitly provided</li>
 *   <li>A human-readable {@code description} of the issue</li>
 * </ul>
 *
 * <p>
 * This class provides multiple constructors to accommodate different use cases, including:
 * <ul>
 *   <li>Full constructor with all parameters</li>
 *   <li>Constructor with implicit {@code resourceId} derived from {@code fhirReference}</li>
 *   <li>Minimal constructor with only {@code severity}, {@code description}, and {@code resourceFile}</li>
 * </ul>
 *
 * <p>
 * The {@code resourceId} is automatically derived from the last path segment of the {@code fhirReference}
 * (excluding the version) unless explicitly set.
 * </p>
 *
 * <p>
 * Example:
 * <pre>
 *   LintingType type = LintingType.MISSING_BUSINESS_KEY;
 *   String fhirRef = "http://dsf.dev/bpe/Process/pingAutostart|1.0.0";
 *   new FhirElementLintItem(ERROR, "task.xml", fhirRef, type, "Missing business-key input");
 * </pre>
 *
 * <p>
 * References:
 * <ul>
 *   <li><a href="https://hl7.org/fhir/overview.html">HL7 FHIR Overview</a></li>
 * </ul>
 * </p>
 *
 * @see LintingType
 * @see LinterSeverity
 * @see FhirLintItem
 */

public class FhirElementLintItem extends FhirLintItem {
    private final String fhirReference;
    private final LintingType issueType;
    private final String description;
    private final String resourceId;

    /**
     * Constructs a {@code FhirElementLintItem} with the {@code resourceId} automatically
     * derived from the {@code fhirReference}.
     *
     * @param severity      the validation severity
     * @param resourceFile  the name of the file from which the resource was loaded
     * @param fhirReference the canonical or local reference to the FHIR resource
     * @param issueType     the validation category or type
     * @param description   a human-readable explanation of the issue
     */
    public FhirElementLintItem(
            LinterSeverity severity,
            String resourceFile,
            String fhirReference,
            LintingType issueType,
            String description) {
        super(severity, resourceFile);
        this.fhirReference = fhirReference;
        this.issueType = issueType;
        this.description = description;
        this.resourceId = deriveId(fhirReference);
    }

    /**
     * Constructs a {@code FhirElementLintItem} using all fields. If {@code resourceId} is null or blank,
     * it will be derived from the {@code fhirReference}.
     *
     * @param severity      the validation severity (e.g. ERROR, WARN, INFO)
     * @param resourceFile  the name of the file from which the resource was loaded
     * @param fhirReference the canonical or local reference to the FHIR resource
     * @param issueType     the validation category or type
     * @param description   a human-readable explanation of the issue
     * @param resourceId    the resource ID, or null to derive it from the reference
     */
    public FhirElementLintItem(
            LinterSeverity severity,
            String resourceFile,
            String fhirReference,
            LintingType issueType,
            String description,
            String resourceId
    ) {
        super(severity, resourceFile);
        this.fhirReference = fhirReference;
        this.issueType = issueType;
        this.description = description;
        this.resourceId = (resourceId == null || resourceId.isBlank())
                ? deriveId(fhirReference)
                : resourceId;
    }


    /**
     * Minimal constructor for fallback use cases. Initializes a basic Lint Item
     * with only a description and severity. All other fields are set to default values.
     *
     * @param description  a message describing the Lint issue
     * @param severity     the validation severity
     * @param resourceFile the name of the file containing the FHIR resource
     */
    public FhirElementLintItem(String description, LinterSeverity severity, String resourceFile) {
        super(severity, resourceFile);
        this.fhirReference = "";
        this.issueType = LintingType.UNKNOWN;
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
        return issueType;
    }


    public String getResourceId() {
        return resourceId;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Generates a human-readable string representation of this Lint Item.
     * Format: [SEVERITY] ClassName (reference=..., type=..., file=...) : description
     *
     * @return string describing the Lint issue
     */
    @Override
    public String toString() {
        return String.format(
                "[%s] %s (reference=%s, type=%s, file=%s) : %s",
                getSeverity(),
                this.getClass().getSimpleName(),
                fhirReference,
                issueType,
                getResourceFile(),
                description
        );
    }


    /**
     * Derives a FHIR resource ID from the canonical or local reference.
     * If no valid reference can be determined, returns "unknown_resource".
     *
     * @param ref the FHIR reference (e.g., "http://example.org/fhir/Task/my-task|1.0.0")
     * @return the derived resource ID (e.g., "my-task")
     */
    private static String deriveId(String ref) {
        if (ref == null || ref.isBlank())
            return "unknown_resource";

        String withoutVersion = ref.split("\\|", 2)[0];           // strip |version
        String lastPart = withoutVersion.substring(withoutVersion.lastIndexOf('/') + 1);
        if (lastPart.contains("#"))
            lastPart = lastPart.substring(lastPart.indexOf('#') + 1);
        return lastPart.isBlank() ? "unknown_resource" : lastPart;
    }
}
