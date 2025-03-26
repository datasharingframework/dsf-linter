package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Represents a validation issue encountered while examining a FHIR resource (e.g. ActivityDefinition,
 * StructureDefinition, CodeSystem, ValueSet, or Task).
 *
 * <p>This class extends {@link FhirValidationItem} by adding more context such as:
 * <ul>
 *   <li>Path to the FHIR resource file</li>
 *   <li>FHIR reference (e.g., URL or canonical reference)</li>
 *   <li>Type of validation issue encountered (via {@link ValidationType})</li>
 *   <li>A descriptive message that details the validation problem</li>
 * </ul>
 * </p>
 *
 * <p>
 * References:
 * <ul>
 *   <li>
 *     <a href="https://www.hl7.org/fhir/overview.html">
 *       HL7 FHIR Overview
 *     </a>
 *   </li>
 * </ul>
 * </p>
 */
public class FhirElementValidationItem extends FhirValidationItem
{
    private final File resourceFile;
    private final String fhirReference;
    private final ValidationType issueType;
    private final String description;
    private final String resourceId;
    /**
     * Constructs a new {@code FhirElementValidationItem}.
     *
     * @param severity the validation severity (e.g., ERROR, WARN, INFO)
     * @param resourceFile the file where the resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param issueType the category or type of the validation issue
     * @param description human-readable message describing the problem
     */
    public FhirElementValidationItem(
            ValidationSeverity severity,
            File resourceFile,
            String fhirReference,
            ValidationType issueType,
            String description)
    {
        super(severity);
        this.resourceFile = resourceFile;
        this.fhirReference = fhirReference;
        this.issueType = issueType;
        this.description = description;
        this.resourceId = "unknown_resource";
    }

    public FhirElementValidationItem(
            ValidationSeverity severity,
            File resourceFile,
            String fhirReference,
            ValidationType issueType,
            String description,
            String resourceId
    ) {
        super(severity);
        this.resourceFile = resourceFile;
        this.fhirReference = fhirReference;
        this.issueType = issueType;
        this.description = description;
        this.resourceId = resourceId;
    }

    public File getResourceFile()
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
                + " [file=" + (resourceFile != null ? resourceFile.getName() : "N/A") + "]";
    }

    public String getResourceId() {
        return resourceId;
    }
}
