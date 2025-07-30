package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.BpmnValidationUtils;
import dev.dsf.utils.validator.util.FhirValidator;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaField;
import org.camunda.bpm.model.xml.instance.DomElement;

import java.io.File;
import java.util.*;

/**
 * Validates {@code <camunda:field>} injections on BPMN elements.
 *
 * <p>Checks that</p>
 * <ul>
 *   <li>fields are provided as <em>string literals</em> (not expressions),</li>
 *   <li>mandatory field names
 *       {@code profile}, {@code messageName}, {@code instantiatesCanonical}
 *       follow DSF conventions, and</li>
 *   <li>referenced FHIR resources (StructureDefinition / ActivityDefinition)
 *       exist and contain the required fixed values.</li>
 * </ul>
 *
 * <p>The validator also performs cross‑checks between BPMN configuration and
 * the corresponding FHIR artefacts.</p>
 */
public class BpmnFieldInjectionValidator {

    /** Camunda namespace URI for extensions */
    private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";

    /**
     * Field types: either STRING (literal) or EXPRESSION.
     */
    private enum FieldValueType { STRING, EXPRESSION }

    /**
     * Wrapper for field value type and value content.
     *
     * @param type  the kind of field content (literal or expression)
     * @param value the actual string value
     */
    private record FieldValue(FieldValueType type, String value) { }

    /*
     public API
      */

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
    public static void validateMessageSendFieldInjections(BaseElement element,
                                                          List<BpmnElementValidationItem> issues,
                                                          File bpmnFile,
                                                          String processId,
                                                          File projectRoot) {
        // 1) direct extension elements on the element itself
        ExtensionElements extEls = element.getExtensionElements();
        if (hasCamundaFields(extEls)) {
            validateCamundaFields(extEls, element.getId(), issues, bpmnFile, processId, projectRoot);
        }

        // 2) embedded MessageEventDefinition fields (ThrowEvent / EndEvent)
        Collection<EventDefinition> eventDefs = (element instanceof ThrowEvent te) ? te.getEventDefinitions()
                : (element instanceof EndEvent ee) ? ee.getEventDefinitions() : Collections.emptyList();

        for (EventDefinition def : eventDefs) {
            if (def instanceof MessageEventDefinition msgDef && hasCamundaFields(msgDef.getExtensionElements())) {
                validateCamundaFields(msgDef.getExtensionElements(), element.getId(), issues, bpmnFile, processId, projectRoot);
            }
        }
    }

    /* --- internal helpers ---------------------------------------------------------------- */

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
     * @param elements the {@link ExtensionElements} instance to be checked for Camunda fields;
     *                          may be {@code null}
     * @return {@code true} if at least one {@link CamundaField} is present in the provided {@code extensionElements};
     * {@code false} otherwise
     */
    private static boolean hasCamundaFields(ExtensionElements elements) {

        return elements != null && !elements.getElementsQuery().filterByType(CamundaField.class).list().isEmpty();
    }

    /**
     * Reads the value of a {@link CamundaField}.
     *
     * @param field the field to inspect (may be {@code null})
     * @return a {@link FieldValue} describing type and content,
     *         or {@code null} if the field contains no value at all
     */
    private static FieldValue readFieldValue(CamundaField field) {
        if (field == null) return null;

        // 1) attribute stringValue
        String literal = field.getCamundaStringValue();
        if (!BpmnValidationUtils.isEmpty(literal)) {
            return new FieldValue(FieldValueType.STRING, literal.trim());
        }

        // 2) attribute expression
        String expr = field.getCamundaExpression();
        if (!BpmnValidationUtils.isEmpty(expr)) {
            return new FieldValue(FieldValueType.EXPRESSION, expr.trim());
        }

        // 3) nested elements
        DomElement domEl = field.getDomElement();
        if (domEl != null) {
            for (DomElement child : domEl.getChildElements()) {
                if (!CAMUNDA_NS.equals(child.getNamespaceURI())) continue;

                switch (child.getLocalName()) {
                    case "string" -> {
                        return new FieldValue(FieldValueType.STRING, child.getTextContent().trim());
                    }
                    case "expression" -> {
                        return new FieldValue(FieldValueType.EXPRESSION, child.getTextContent().trim());
                    }
                    default -> {
                    }
                }
            }
        }
        return null; // completely empty field
    }

    /**
     * Validates the Camunda fields present in the provided {@link ExtensionElements} and adds
     * corresponding validation issues to the provided list.
     * <p>
     * This method performs a multi-step validation of the Camunda fields within the given
     * {@code extensionElements}. It first extracts all {@link CamundaField} objects using the element query.
     * For each field, the method retrieves the Camunda name, expression, and literal value.
     * If the literal value is missing, it attempts to read nested string content.
     * If an expression (rather than a literal string) is found, a
     * {@link BpmnFieldInjectionNotStringLiteralValidationItem} is added to the issues list.
     * </p>
     * <p>
     * The validation behavior is as follows:
     * <ol>
     *   <li>
     *     For fields with the name <b>profile</b>:
     *     <ul>
     *       <li>It invokes {@link BpmnValidationUtils#checkProfileField(String, File, String, List, String, File)}
     *           to validate the profile field.</li>
     *       <li>It records the literal value and checks if a StructureDefinition exists for that profile via
     *           {@link FhirValidator#structureDefinitionExists(String, File)}. If the field is provided and valid,
     *           a success item is added.</li>
     *     </ul>
     *   </li>
     *   <li>
     *     For fields with the name <b>messageName</b>:
     *     <ul>
     *       <li>If the literal value is empty, it adds a {@link BpmnFieldInjectionMessageValueEmptyValidationItem}
     *           to the issues list.</li>
     *       <li>Otherwise, it records the message name value for later cross-checking and adds a success item.</li>
     *     </ul>
     *   </li>
     *   <li>
     *     For fields with the name <b>instantiatesCanonical</b>:
     *     <ul>
     *       <li>It invokes {@link BpmnValidationUtils#checkInstantiatesCanonicalField(String, String, File, String, List, File)}
     *           to validate the field and records the literal value. If the field is provided, a success item is added.</li>
     *     </ul>
     *   </li>
     *   <li>
     *     For any other field names, it adds a {@link BpmnUnknownFieldInjectionValidationItem} to the issues list.
     *     (No success item is added in this default branch.)
     *   </li>
     * </ol>
     * </p>
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
     *     If a BPMN had an <code>instantiatesCanonical</code> value, the method checks whether the
     *     StructureDefinition contains a non-empty <code>&lt;fixedCanonical&gt;</code> element under
     *     <code>Task.instantiatesCanonical</code>. An error is added if this element is missing or empty;
     *     otherwise, a success item is recorded.
     *   </li>
     *   <li>
     *     The method then checks for a <code>&lt;fixedString&gt;</code> element under
     *     <code>Task.input:message-name.value[x]</code> and adds an error if it is missing or empty;
     *     otherwise, a success item is recorded.
     *   </li>
     *   <li>
     *     Finally, if an <code>instantiatesCanonical</code> value is provided, it validates that an
     *     ActivityDefinition exists for that canonical using
     *     {@link FhirValidator#activityDefinitionExistsForInstantiatesCanonical(String, File)}.
     *     If no ActivityDefinition is found, a warning is added; if found, a success item is recorded.
     *     If a messageName value is provided, it further checks that the ActivityDefinition contains the messageName
     *     using {@link FhirValidator#activityDefinitionHasMessageName(String, File)}.
     *     An error is added if not; otherwise, a success item is recorded.
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
    private static void validateCamundaFields(ExtensionElements extensionElements,
                                              String elementId,
                                              List<BpmnElementValidationItem> issues,
                                              File bpmnFile,
                                              String processId,
                                              File projectRoot) {
        Collection<CamundaField> fields = extensionElements.getElementsQuery().filterByType(CamundaField.class).list();

        // remember values for cross‑checks
        String profileVal = null;
        String messageNameVal = null;
        String instantiatesVal = null;
        boolean structureFoundForProfile = false;

        for (CamundaField field : fields) {
            String fieldName = field.getCamundaName();
            FieldValue fv = readFieldValue(field);

            //  expression? -> immediate info + skip further processing of this field
            if (fv != null && fv.type == FieldValueType.EXPRESSION) {
                issues.add(new BpmnFieldInjectionNotStringLiteralValidationItem(
                        elementId, bpmnFile, processId, fieldName,
                        "Field injection '" + fieldName + "' is provided as expression, expected string literal"));
                continue;
            }

            String literal = (fv != null) ? fv.value : null;

            switch (fieldName) {
                case "profile" -> {
                    BpmnValidationUtils.checkProfileField(elementId, bpmnFile, processId, issues, literal, projectRoot);
                    profileVal = literal;
                    if (!BpmnValidationUtils.isEmpty(literal) && FhirValidator.structureDefinitionExists(literal, projectRoot)) {
                        structureFoundForProfile = true;
                    }
                }
                case "messageName" -> {
                    if (BpmnValidationUtils.isEmpty(literal)) {
                        issues.add(new BpmnFieldInjectionMessageValueEmptyValidationItem(elementId, bpmnFile, processId));
                    } else {
                        messageNameVal = literal;
                        issues.add(new BpmnElementValidationItemSuccess(elementId, bpmnFile, processId,
                                "Field 'messageName' is valid with value: '" + literal + "'"));
                    }
                }
                case "instantiatesCanonical" -> {
                    BpmnValidationUtils.checkInstantiatesCanonicalField(elementId, literal, bpmnFile, processId, issues, projectRoot);
                    instantiatesVal = literal;
                }
                default -> issues.add(new BpmnUnknownFieldInjectionValidationItem(elementId, bpmnFile, processId, fieldName));
            }
        }

        /*  cross‑checks that need both BPMN + FHIR context  */
        if (structureFoundForProfile && !BpmnValidationUtils.isEmpty(profileVal)) {
            performCrossChecks(elementId, issues, bpmnFile, processId, projectRoot,
                    profileVal, instantiatesVal, messageNameVal);
        }
    }

    /* helper that outsources the bulky FHIR cross‑validation logic */
    /**
     * Performs semantic cross‑checks between the BPMN field values and
     * the referenced FHIR resources.
     *
     * @param elementId      BPMN element id
     * @param issues         collector for validation findings
     * @param bpmnFile       source BPMN file
     * @param processId      id of the enclosing process definition
     * @param projectRoot    DSF project root directory
     * @param profileVal     canonical URL of the <code>StructureDefinition</code>
     * @param instantiatesVal canonical URL used in <code>instantiatesCanonical</code>
     * @param messageNameVal expected messageName fixed value
     */
    private static void performCrossChecks(String elementId,
                                           List<BpmnElementValidationItem> issues,
                                           File bpmnFile,
                                           String processId,
                                           File projectRoot,
                                           String profileVal,
                                           String instantiatesVal,
                                           String messageNameVal) {
        File structureFile = FhirValidator.findStructureDefinitionFile(profileVal, projectRoot);
        if (structureFile == null) return; // Warn already added earlier.

        try {
            var doc = FhirValidator.parseFhirFile(structureFile.toPath());

            if (!BpmnValidationUtils.isEmpty(instantiatesVal)) {
                String fixedCanonical = FhirValidator.getTaskInstantiatesCanonicalValue(doc);
                if (fixedCanonical == null) {
                    issues.add(new FhirStructureDefinitionValidationItem(ValidationSeverity.ERROR,
                            elementId, bpmnFile, processId, structureFile.getName(),
                            "StructureDefinition lacks <fixedCanonical> for Task.instantiatesCanonical"));
                } else if (fixedCanonical.isBlank()) {
                    issues.add(new FhirStructureDefinitionValidationItem(ValidationSeverity.ERROR,
                            elementId, bpmnFile, processId, structureFile.getName(),
                            "<fixedCanonical> present but empty in StructureDefinition"));
                } else {
                    issues.add(new BpmnElementValidationItemSuccess(elementId, bpmnFile, processId,
                            "StructureDefinition contains valid <fixedCanonical>."));
                }
            }

            String fixedString = FhirValidator.getTaskMessageNameFixedStringValue(doc);
            if (fixedString == null || fixedString.isBlank()) {
                issues.add(new FhirStructureDefinitionValidationItem(ValidationSeverity.ERROR,
                        elementId, bpmnFile, processId, structureFile.getName(),
                        "StructureDefinition has no valid <fixedString> for message‑name."));
            } else {
                issues.add(new BpmnElementValidationItemSuccess(elementId, bpmnFile, processId,
                        "StructureDefinition contains valid <fixedString>."));
            }
        } catch (Exception ignored) { /* parsing errors are ignored */ }

        // ActivityDefinition checks
        if (!BpmnValidationUtils.isEmpty(instantiatesVal)) {
            boolean actDefFound = FhirValidator.activityDefinitionExistsForInstantiatesCanonical(instantiatesVal, projectRoot);
            if (!actDefFound) {
                issues.add(new FhirActivityDefinitionValidationItem(ValidationSeverity.WARN,
                        elementId, bpmnFile, processId, instantiatesVal,
                        "No ActivityDefinition found for instantiatesCanonical " + instantiatesVal));
            } else {
                issues.add(new BpmnElementValidationItemSuccess(elementId, bpmnFile, processId,
                        "ActivityDefinition exists for instantiatesCanonical: '" + instantiatesVal + "'."));

                if (!BpmnValidationUtils.isEmpty(messageNameVal) &&
                        !FhirValidator.activityDefinitionHasMessageName(messageNameVal, projectRoot)) {
                    issues.add(new FhirActivityDefinitionValidationItem(ValidationSeverity.ERROR,
                            elementId, bpmnFile, processId, instantiatesVal,
                            "ActivityDefinition does not contain message name '" + messageNameVal + "'."));
                }
            }
        }
    }
}