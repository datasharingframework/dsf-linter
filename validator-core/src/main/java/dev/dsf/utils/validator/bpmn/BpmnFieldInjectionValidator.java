package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.util.FhirValidator;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.BpmnValidationUtils;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaField;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The {@code BpmnFieldInjectionValidator} class provides static methods for validating
 * Camunda field injections in BPMN elements. These methods ensure that <code>camunda:field</code>
 * elements on BPMN tasks and message events are correctly configured by checking for valid references
 * such as {@code profile}, {@code messageName}, and {@code instantiatesCanonical}. The class validates
 * that these references exist in the corresponding FHIR resources (e.g., StructureDefinition and
 * ActivityDefinition) and that the field values are specified as string literals rather than expressions.
 *
 * <p>
 * This validator is designed to support BPMN validation processes where consistency between BPMN field
 * injections and FHIR resource definitions must be maintained. It handles both direct field injections on
 * BPMN elements and nested field injections within elements such as MessageEventDefinition.
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
     * Validates message send field injections for a given BPMN element.
     *
     * <p>
     * This method validates the use of <code>camunda:field</code> injections on the provided BPMN element in two steps:
     * </p>
     *
     * <ol>
     *   <li>
     *     <b>Direct Element Validation:</b> It checks the extension elements on the BPMN element itself.
     *     If the element contains any Camunda fields (as determined by
     *     {@link BpmnFieldInjectionValidator#hasCamundaFields(ExtensionElements)}), the method validates these fields
     *     using {@link BpmnFieldInjectionValidator#validateCamundaFields(ExtensionElements, String, List, File, String, File)}.
     *   </li>
     *   <li>
     *     <b>Nested MessageEventDefinition Validation:</b> For BPMN elements that are instances of {@link ThrowEvent}
     *     or {@link EndEvent}, it further inspects their attached {@link MessageEventDefinition} elements. For each
     *     message event definition that contains extension elements with Camunda fields, the same validation is applied.
     *   </li>
     * </ol>
     *
     * <p>
     * Any validation issues encountered are added to the provided list of {@link BpmnElementValidationItem}.
     * </p>
     *
     * @param element     the BPMN {@link BaseElement} to be validated for message send field injections
     * @param issues      the list of {@link BpmnElementValidationItem} where any detected validation issues will be added
     * @param bpmnFile    the BPMN file under validation
     * @param processId   the identifier of the BPMN process containing the element
     * @param projectRoot the root directory of the project that contains the FHIR and BPMN resources
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

    /**
     * Checks whether the provided {@link ExtensionElements} contains any Camunda field elements.
     *
     * <p>
     * This method retrieves a collection of {@link CamundaField} objects from the given
     * {@code extensionElements} using its element query and filtering by type.
     * It returns {@code true} if the collection is not {@code null} and contains at least one element;
     * otherwise, it returns {@code false}.
     * </p>
     *
     * @param extensionElements the {@link ExtensionElements} instance to be checked for Camunda fields;
     *                          may be {@code null}
     * @return {@code true} if at least one {@link CamundaField} is present in the provided {@code extensionElements};
     *         {@code false} otherwise
     */
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
     * Validates the Camunda fields present in the provided {@link ExtensionElements} and adds
     * corresponding validation issues to the provided list.
     *
     * <p>
     * This method performs a multi-step validation of the Camunda fields within the given
     * {@code extensionElements}. It first extracts all {@link CamundaField} objects using the element query.
     * For each field, the method retrieves the Camunda name, expression, and literal value.
     * If the literal value is missing, it attempts to read nested string content.
     * If an expression (rather than a literal string) is found, a
     * {@link BpmnFieldInjectionNotStringLiteralValidationItem} is added to the issues list.
     * </p>
     *
     * <p>
     * The validation behavior is as follows:
     * <ol>
     *   <li>
     *     For fields with the name <b>profile</b>:
     *     <ul>
     *       <li>It invokes {@link BpmnValidationUtils#checkProfileField(String, File, String, List, String, File)}
     *           to validate the profile field.</li>
     *       <li>It records the literal value and checks if a StructureDefinition exists for that profile via
     *           {@link FhirValidator#structureDefinitionExists(String, File)}.</li>
     *     </ul>
     *   </li>
     *   <li>
     *     For fields with the name <b>messageName</b>:
     *     <ul>
     *       <li>If the literal value is empty, it adds a {@link BpmnFieldInjectionMessageValueEmptyValidationItem}
     *           to the issues list.</li>
     *       <li>Otherwise, it records the message name value for later cross-checking.</li>
     *     </ul>
     *   </li>
     *   <li>
     *     For fields with the name <b>instantiatesCanonical</b>:
     *     <ul>
     *       <li>It invokes {@link BpmnValidationUtils#checkInstantiatesCanonicalField(String, String, File, String, List, File)}
     *           to validate the field and records the literal value.</li>
     *     </ul>
     *   </li>
     *   <li>
     *     For any other field names, it adds a {@link BpmnUnknownFieldInjectionValidationItem} to the issues list.
     *   </li>
     * </ol>
     * </p>
     *
     * <p>
     * After processing the individual fields, the method performs cross-check validation if a valid profile field was found:
     * <ol>
     *   <li>
     *     It locates the actual StructureDefinition file by invoking
     *     {@link FhirValidator#findStructureDefinitionFile(String, File)}.
     *   </li>
     *   <li>
     *     If the file is found, it is parsed (using an inline parsing method from
     *     {@link BpmnValidationUtils}) to obtain a {@link org.w3c.dom.Document} representation.
     *   </li>
     *   <li>
     *     If an <code>instantiatesCanonical</code> value was provided in the BPMN, the method checks whether the
     *     StructureDefinition contains a non-empty <code>&lt;fixedCanonical&gt;</code> element under
     *     <code>Task.instantiatesCanonical</code>. An error is added if this element is missing or empty.
     *   </li>
     *   <li>
     *     The method then checks for a <code>&lt;fixedString&gt;</code> element under
     *     <code>Task.input:message-name.value[x]</code> and adds an error if it is missing or empty.
     *   </li>
     *   <li>
     *     Finally, if an <code>instantiatesCanonical</code> value is provided, it validates that an
     *     ActivityDefinition exists for that canonical using
     *     {@link FhirValidator#activityDefinitionExistsForInstantiatesCanonical(String, File)}.
     *     If a messageName value is provided, it further checks that the ActivityDefinition contains the messageName
     *     using {@link FhirValidator#activityDefinitionHasMessageName(String, File)}.
     *   </li>
     * </ol>
     * </p>
     *
     * @param extensionElements the {@link ExtensionElements} containing the Camunda fields to be validated
     * @param elementId         the identifier of the BPMN element being validated
     * @param issues            the list of {@link BpmnElementValidationItem} where validation issues will be added
     * @param bpmnFile          the BPMN file under validation
     * @param processId         the identifier of the BPMN process containing the element
     * @param projectRoot       the root directory of the project containing FHIR and BPMN resources
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
                        elementId, bpmnFile, processId, fieldName, "Field injection '" + fieldName + "' is not provided as a string literal"));
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
                            ValidationSeverity.WARN,
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
