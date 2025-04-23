package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;


/**
 * Represents a validation issue encountered while examining a FHIR resource
 * (e.g., ActivityDefinition, StructureDefinition, CodeSystem, ValueSet, or Task).
 *
 * <p>This class extends {@link FhirValidationItem} by adding:
 * <ul>
 *   <li>Path to the FHIR resource file</li>
 *   <li>FHIR reference (URL or canonical reference)</li>
 *   <li>Type of validation issue encountered (via {@link ValidationType})</li>
 *   <li>A descriptive message detailing the validation problem</li>
 * </ul>
 *
 * <p>References:
 * <ul>
 *   <li>
 *     <a href="https://hl7.org/fhir/overview.html">
 *       HL7 FHIR Overview
 *     </a>
 *   </li>
 * </ul>
 * </p>
 */
public class FhirElementValidationItem extends FhirValidationItem
{
    private final String fhirReference;
    private final ValidationType issueType;
    private final String description;
    private final String resourceId;

    /**
     * Constructs a new {@code FhirElementValidationItem} with all required fields.
     *
     * @param severity       the validation severity (e.g., ERROR, WARN, INFO)
     * @param resourceFile   the file where the resource was loaded from
     * @param fhirReference  a canonical URL or local reference identifying the resource
     * @param issueType      the category or type of the validation issue
     * @param description    human-readable message describing the problem
     */
    public FhirElementValidationItem(
            ValidationSeverity severity,
            String resourceFile,
            String fhirReference,
            ValidationType issueType,
            String description)
    {
        super(severity, resourceFile);
        this.fhirReference = fhirReference;
        this.issueType = issueType;
        this.description = description;
        this.resourceId = "unknown_resource";
    }

    /**
     * Constructs a new {@code FhirElementValidationItem} with all required fields plus a resource ID.
     *
     * @param severity       the validation severity
     * @param resourceFile   the file where the resource was loaded from
     * @param fhirReference  the canonical URL or reference identifying the resource
     * @param issueType      the category or type of the validation issue
     * @param description    human-readable message describing the problem
     * @param resourceId     the FHIR resource ID, if known
     */
    public FhirElementValidationItem(
            ValidationSeverity severity,
            String resourceFile,
            String fhirReference,
            ValidationType issueType,
            String description,
            String resourceId
    ) {
        super(severity, resourceFile);
        this.fhirReference = fhirReference;
        this.issueType = issueType;
        this.description = description;
        this.resourceId = resourceId;
    }

    /**
     * <p>A convenience constructor to allow minimal creation with only a message and severity.</p>
     * <p>All other fields are defaulted or set to {@code null} or {@code "unknown_resource"}. Use this
     * when the file or resource details are unavailable.</p>
     *
     * @param description a human-readable description of the problem
     * @param severity    the validation severity
     */
    public FhirElementValidationItem(String description, ValidationSeverity severity, String resourceFile)
    {
        super(severity,resourceFile);
        this.fhirReference = "";
        this.issueType = ValidationType.UNKNOWN;
        this.description = description;
        this.resourceId = "unknown_resource";
    }

    public String getResourceFile()
    {
        return resourceFile;
    }

    public String getFhirReference()
    {
        return fhirReference;
    }

    public ValidationType getIssueType()
    {
        return issueType;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    @Override
    public String toString()
    {
        return "[" + getSeverity() + "] " + issueType + " (" + fhirReference + "): " + description
                + " [file=" + getResourceFile() + "]";
    }

    public String getResourceId() {
        return resourceId;
    }
}
