package dev.dsf.linter.util.resource;

/**
 * Enumeration representing different types of FHIR definition resources
 * that can be validated by the FhirValidator utility.
 */
public enum FhirDefinitionType {
    /**
     * Represents FHIR ActivityDefinition resources
     */
    ACTIVITY_DEFINITION,

    /**
     * Represents FHIR StructureDefinition resources
     */
    STRUCTURE_DEFINITION
}
