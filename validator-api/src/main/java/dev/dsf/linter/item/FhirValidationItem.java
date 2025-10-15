package dev.dsf.linter.item;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dsf.linter.ValidationSeverity;

// FHIR Validation Items
public abstract class FhirValidationItem extends AbstractValidationItem {

    @JsonProperty("resourceFile")  // JSON field name = "resourceFile"
    protected final String resourceFile;
    public FhirValidationItem(ValidationSeverity severity, String resourceFile) {
        super(severity);
        this.resourceFile = (resourceFile != null) ? resourceFile : "unknown.xml";
    }

    public abstract String getDescription();

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String getFhirFile()
    {
        return resourceFile;
    }

    @Override
    public String toString()
    {
        return getFhirFile();
    }

}
