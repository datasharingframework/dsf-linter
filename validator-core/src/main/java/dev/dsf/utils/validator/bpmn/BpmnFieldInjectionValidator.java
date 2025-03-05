package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.fhir.FhirValidator;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.BpmnValidationUtils;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaField;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <p>
 * The {@code BpmnFieldInjectionValidator} class provides static methods to validate
 * {@code <camunda:field>} elements on BPMN tasks and message events. It checks for
 * valid {@code profile}, {@code messageName}, and {@code instantiatesCanonical} references,
 * as well as ensuring they exist in FHIR resources.
 * </p>
 *
 * <p>
 * References:
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">Camunda Extension Elements</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 * </p>
 */
public class BpmnFieldInjectionValidator
{

    /**
     * Validates all field injections (e.g., {@code <camunda:field>}) found directly
     * on a {@link BaseElement} or nested under a {@link MessageEventDefinition}.
     */
    public static void validateMessageSendFieldInjections(
            BaseElement element,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot)
    {
        // 1) Check <camunda:field> on the BPMN element itself
        ExtensionElements extensionElements = element.getExtensionElements();
        if (BpmnFieldInjectionValidator.hasCamundaFields(extensionElements))
        {
            BpmnFieldInjectionValidator.validateCamundaFields(
                    extensionElements,
                    element.getId(),
                    issues,
                    bpmnFile,
                    processId,
                    projectRoot
            );
        }

        // 2) For ThrowEvent/EndEvent, also check nested fields in the attached MessageEventDefinition
        Collection<EventDefinition> eventDefinitions = new ArrayList<>();
        if (element instanceof ThrowEvent throwEvent)
        {
            eventDefinitions = throwEvent.getEventDefinitions();
        }
        else if (element instanceof EndEvent endEvent)
        {
            eventDefinitions = endEvent.getEventDefinitions();
        }

        for (EventDefinition eventDef : eventDefinitions)
        {
            if (eventDef instanceof MessageEventDefinition messageDef)
            {
                ExtensionElements msgExtEl = messageDef.getExtensionElements();
                if (BpmnFieldInjectionValidator.hasCamundaFields(msgExtEl))
                {
                    BpmnFieldInjectionValidator.validateCamundaFields(
                            msgExtEl,
                            element.getId(),
                            issues,
                            bpmnFile,
                            processId,
                            projectRoot
                    );
                }
            }
        }
    }

    private static boolean hasCamundaFields(ExtensionElements extensionElements)
    {
        if (extensionElements == null) return false;
        Collection<CamundaField> fields = extensionElements
                .getElementsQuery()
                .filterByType(CamundaField.class)
                .list();
        return fields != null && !fields.isEmpty();
    }

    /**
     * Parses and validates the {@code <camunda:field>} elements for a BPMN element,
     * specifically handling {@code profile}, {@code messageName}, and {@code instantiatesCanonical}.
     */
    private static void validateCamundaFields(
            ExtensionElements extensionElements,
            String elementId,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {
        Collection<CamundaField> camundaFields = extensionElements
                .getElementsQuery()
                .filterByType(CamundaField.class)
                .list();

        String profileValue = null;
        boolean structureFoundForProfile = false;
        String messageNameValue = null;
        String instantiatesValue = null;

        for (CamundaField field : camundaFields) {
            String fieldName = field.getCamundaName();
            String exprValue = field.getCamundaExpression();
            String literalValue = field.getCamundaStringValue();

            if (BpmnValidationUtils.isEmpty(literalValue)) {
                // Attempt to read nested <camunda:string> if needed
                literalValue = BpmnValidationUtils.tryReadNestedStringContent(field);
            }

            // If an expression is found, record an error/warning
            if (!BpmnValidationUtils.isEmpty(exprValue)) {
                issues.add(new BpmnFieldInjectionNotStringLiteralValidationItem(
                        elementId, bpmnFile, processId, fieldName));
                continue;
            }

            if (literalValue != null) literalValue = literalValue.trim();

            switch (fieldName) {
                case "profile": {
                    BpmnValidationUtils.checkProfileField(elementId, bpmnFile, processId, issues, literalValue, projectRoot);
                    profileValue = literalValue;
                    if (!BpmnValidationUtils.isEmpty(literalValue) && FhirValidator.structureDefinitionExists(literalValue, projectRoot)) {
                        structureFoundForProfile = true;
                    }
                    break;
                }
                case "messageName": {
                    if (BpmnValidationUtils.isEmpty(literalValue)) {
                        issues.add(new BpmnFieldInjectionMessageValueEmptyValidationItem(
                                elementId, bpmnFile, processId));
                    } else {
                        messageNameValue = literalValue;
                    }
                    break;
                }
                case "instantiatesCanonical": {
                    BpmnValidationUtils.checkInstantiatesCanonicalField(elementId, literalValue, bpmnFile, processId, issues, projectRoot);
                    instantiatesValue = literalValue;
                    break;
                }
                default: {
                    issues.add(new BpmnUnknownFieldInjectionValidationItem(
                            elementId, bpmnFile, processId, fieldName));
                    break;
                }
            }
        }

        // Cross-check logic
        if (structureFoundForProfile && !BpmnValidationUtils.isEmpty(profileValue)) {
            // 1) find the actual structure definition file
            File structureFile = FhirValidator.findStructureDefinitionFile(profileValue, projectRoot);
            if (structureFile == null) {
                // "Profile not found in StructureDefinition" is already reported above
                return;
            }
            // 2) parse it
            try {
                // parseXml is private in FhirValidator, so let's re-use it or adapt as needed:
                // We'll do a small inline parse here for brevity.
                var doc = BpmnValidationUtils.parseXml(structureFile);
                // 3) If BPMN had instantiatesCanonical, check if the file has a non-empty <fixedCanonical>
                if (!BpmnValidationUtils.isEmpty(instantiatesValue)) {
                    String fixedCanonical = FhirValidator.getTaskInstantiatesCanonicalValue(doc);
                    if (fixedCanonical == null)
                    {
                        // => "does not exist"
                        issues.add(new FhirStructureDefinitionValidationItem(ValidationSeverity.ERROR,
                                elementId,
                                bpmnFile,
                                processId,
                                structureFile.getName(),
                                "StructureDefinition [" + structureFile.getName() + "] is present, but <fixedCanonical> under Task.instantiatesCanonical is completely missing."
                        ));
                    }
                    else if (fixedCanonical.isBlank())
                    {
                        // => "is empty"
                        issues.add(new FhirStructureDefinitionValidationItem(ValidationSeverity.ERROR,
                                elementId,
                                bpmnFile,
                                processId,
                                structureFile.getName(),
                                "StructureDefinition [" + structureFile.getName() + "] does contain <fixedCanonical>, but the 'value' is empty."
                        ));
                    }
                }
                // 4) If BPMN had messageName, check if the file has a non-empty <fixedString>

                String fixedString = FhirValidator.getTaskMessageNameFixedStringValue(doc);
                if (fixedString == null)
                {
                    // => "does not exist"
                    issues.add(new FhirStructureDefinitionValidationItem(ValidationSeverity.ERROR,
                            elementId,
                            bpmnFile,
                            processId,
                            structureFile.getName(),
                            "StructureDefinition [" + structureFile.getName() + "] does not have <fixedString> under Task.input:message-name.value[x]."
                    ));
                }
                else if (fixedString.isBlank())
                {
                    // => "is empty"
                    issues.add(new FhirStructureDefinitionValidationItem(ValidationSeverity.ERROR,
                            elementId,
                            bpmnFile,
                            processId,
                            structureFile.getName(),
                            "StructureDefinition [" + structureFile.getName() + "] does have <fixedString>, but the 'value' is empty."
                    ));
                }


            } catch (Exception ignored) {

            }

            if (!BpmnValidationUtils.isEmpty(instantiatesValue)) {
                boolean foundInActDef = FhirValidator.activityDefinitionExistsForInstantiatesCanonical(instantiatesValue, projectRoot);
                if (!foundInActDef) {
                    issues.add(new FhirActivityDefinitionValidationItem(
                            ValidationSeverity.ERROR,
                            elementId,
                            bpmnFile,
                            processId,
                            instantiatesValue,
                            "instantiatesCanonical not found in any ActivityDefinition: " + instantiatesValue
                    ));
                } else {
                    if (!BpmnValidationUtils.isEmpty(messageNameValue)) {
                        boolean hasMsgName = FhirValidator.activityDefinitionHasMessageName(messageNameValue, projectRoot);
                        if (!hasMsgName) {
                            issues.add(new FhirActivityDefinitionValidationItem(
                                    ValidationSeverity.ERROR,
                                    elementId,
                                    bpmnFile,
                                    processId,
                                    instantiatesValue,
                                    "ActivityDefinition found for canonical " + instantiatesValue
                                            + " but the message value is not present in any ActivityDefinition '" + messageNameValue + "'"
                            ));
                        }
                    }
                }
            }
        }
    }
}
