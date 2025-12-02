package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.resource.FhirResourceLocator;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;

import java.io.File;
import java.util.List;

/**
 * Utility class for validating BPMN message names against FHIR resources.
 * <p>
 * This class provides validation methods to ensure that message names in BPMN elements
 * correspond to existing FHIR ActivityDefinition and StructureDefinition resources.
 * </p>
 */
public final class BpmnMessageLinter {

    private BpmnMessageLinter() {
        // Utility class - no instantiation
    }

    /**
     * Checks the message name against FHIR resources.
     * <p>
     * Validates that the message name corresponds to existing FHIR ActivityDefinition
     * and StructureDefinition resources in the project.
     * </p>
     *
     * @param messageName the message name to validate
     * @param issues      the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param elementId   the identifier of the BPMN element being validated
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the identifier of the BPMN process containing the element
     * @param projectRoot the project root directory containing FHIR resources
     */
    public static void checkMessageName(
            String messageName,
            List<BpmnElementLintItem> issues,
            String elementId,
            File bpmnFile,
            String processId,
            File projectRoot) {
        var locator = FhirResourceLocator.create(projectRoot);

        // Check for a matching ActivityDefinition.
        if (locator.activityDefinitionExists(messageName, projectRoot)) {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "ActivityDefinition found for messageName: '" + messageName + "'"
            ));
        } else {
            issues.add(new BpmnNoActivityDefinitionFoundForMessageLintItem(
                    LinterSeverity.ERROR,
                    elementId,
                    bpmnFile,
                    processId,
                    messageName,
                    "No ActivityDefinition found for messageName: " + messageName
            ));
        }

        // Check for a matching StructureDefinition.
        if (locator.structureDefinitionExists(messageName, projectRoot)) {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "StructureDefinition found for messageName: '" + messageName + "'"
            ));
        } else {
            issues.add(new BpmnNoStructureDefinitionFoundForMessageLintItem(
                    LinterSeverity.ERROR,
                    elementId,
                    bpmnFile,
                    processId,
                    messageName,
                    "StructureDefinition [" + messageName + "] not found."
            ));
        }
    }

    /**
     * Validates FHIR references (ActivityDefinition and StructureDefinition) for a message name.
     * <p>
     * This method is used by event linters to validate message references.
     * It first checks for ActivityDefinition, and only if found, checks for StructureDefinition.
     * </p>
     *
     * @param msgName   the message name to validate
     * @param elementId the identifier of the BPMN element being validated
     * @param issues    the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile  the BPMN file under lint
     * @param processId the identifier of the BPMN process containing the element
     * @param locator   the FHIR resource locator instance
     * @param projectRoot the project root directory containing FHIR resources
     */
    public static void lintFhirReferences(
            String msgName,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            FhirResourceLocator locator,
            File projectRoot) {

        boolean activityDefFound = locator.activityDefinitionExists(msgName, projectRoot);

        if (!activityDefFound) {
            issues.add(new BpmnNoActivityDefinitionFoundForMessageLintItem(
                    LinterSeverity.ERROR, elementId, bpmnFile, processId, msgName,
                    "No ActivityDefinition found for messageName: " + msgName));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "ActivityDefinition found for messageName: '" + msgName + "'"));

        }
        if (!locator.structureDefinitionExists(msgName, projectRoot)) {
            issues.add(new BpmnNoStructureDefinitionFoundForMessageLintItem(
                    LinterSeverity.ERROR, elementId, bpmnFile, processId, msgName,
                    "No StructureDefinition found for messageName: " + msgName));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "StructureDefinition found for messageName: '" + msgName + "'"));
        }
    }

    /**
     * Validates a message event definition and its FHIR references.
     * <p>
     * This method validates that the message event definition has a non-empty message name
     * and checks the corresponding FHIR ActivityDefinition and StructureDefinition resources.
     * </p>
     *
     * @param messageDef the message event definition to validate
     * @param elementId  the identifier of the BPMN element being validated
     * @param issues     the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile   the BPMN file under lint
     * @param processId  the identifier of the BPMN process containing the element
     * @param projectRoot the project root directory containing FHIR resources
     */
    public static void lintMessageEventDefinition(
            MessageEventDefinition messageDef,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String msgName = messageDef.getMessage().getName();
        issues.add(new BpmnElementLintItemSuccess(
                elementId, bpmnFile, processId,
                "Message name is not empty: '" + msgName + "'"));

        FhirResourceLocator locator = FhirResourceLocator.create(projectRoot);
        lintFhirReferences(msgName, elementId, issues, bpmnFile, processId, locator, projectRoot);
    }
}

