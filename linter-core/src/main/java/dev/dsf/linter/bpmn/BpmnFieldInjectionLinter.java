package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.resource.FhirResourceExtractor;
import dev.dsf.linter.util.resource.FhirResourceLocator;
import dev.dsf.linter.util.resource.FhirResourceParser;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaField;
import org.camunda.bpm.model.xml.instance.DomElement;

import java.io.File;
import java.util.*;

import static dev.dsf.linter.bpmn.BpmnElementLinter.checkInstantiatesCanonicalField;
import static dev.dsf.linter.bpmn.BpmnElementLinter.checkProfileField;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * lints {@code <camunda:field>} injections on BPMN elements.
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
 * <p>The linter also performs cross‑checks between BPMN configuration and
 * the corresponding FHIR artefacts.</p>
 */
public class BpmnFieldInjectionLinter {

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
     * lints message send field injections for a given BPMN element.
     *
     * <p>
     * This method lints the use of <code>camunda:field</code> injections on the provided BPMN element in two steps:
     * </p>
     *
     * <ol>
     *   <li>
     *     <b>Direct Element Linter:</b> It checks the extension elements on the BPMN element itself.
     *     If the element contains any Camunda fields (as determined by
     *     {@link BpmnFieldInjectionLinter#hasCamundaFields(ExtensionElements)}), the method lints these fields
     *     using {@link BpmnFieldInjectionLinter#lintCamundaFields(ExtensionElements, String, List, File, String, File)}.
     *   </li>
     *   <li>
     *     <b>Nested MessageEventDefinition Linter:</b> For BPMN elements that are instances of {@link ThrowEvent}
     *     or {@link EndEvent}, it further inspects their attached {@link MessageEventDefinition} elements. For each
     *     message event definition that contains extension elements with Camunda fields, the same linting is applied.
     *   </li>
     * </ol>
     *
     * <p>
     * Any lint issues encountered are added to the provided list of {@link BpmnElementLintItem}.
     * </p>
     *
     * @param element     the BPMN {@link BaseElement} to be linted for message send field injections
     * @param issues      the list of {@link BpmnElementLintItem} where any detected linting issues will be added
     * @param bpmnFile    the BPMN file under linting
     * @param processId   the identifier of the BPMN process containing the element
     * @param projectRoot the root directory of the project that contains the FHIR and BPMN resources
     */
    public static void lintMessageSendFieldInjections(BaseElement element,
                                                      List<BpmnElementLintItem> issues,
                                                      File bpmnFile,
                                                      String processId,
                                                      File projectRoot) {
        // 1) direct extension elements on the element itself
        ExtensionElements extEls = element.getExtensionElements();
        if (hasCamundaFields(extEls)) {
            lintCamundaFields(extEls, element.getId(), issues, bpmnFile, processId, projectRoot);
        }

        // 2) embedded MessageEventDefinition fields (ThrowEvent / EndEvent)
        Collection<EventDefinition> eventDefs = (element instanceof EndEvent ee) ? ee.getEventDefinitions()
                : (element instanceof ThrowEvent te) ? te.getEventDefinitions() : Collections.emptyList();

        for (EventDefinition def : eventDefs) {
            if (def instanceof MessageEventDefinition msgDef && hasCamundaFields(msgDef.getExtensionElements())) {
                lintCamundaFields(msgDef.getExtensionElements(), element.getId(), issues, bpmnFile, processId, projectRoot);
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
        if (!isEmpty(literal)) {
            return new FieldValue(FieldValueType.STRING, literal.trim());
        }

        // 2) attribute expression
        String expr = field.getCamundaExpression();
        if (!isEmpty(expr)) {
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


    private static void lintCamundaFields(ExtensionElements extensionElements,
                                          String elementId,
                                          List<BpmnElementLintItem> issues,
                                          File bpmnFile,
                                          String processId,
                                          File projectRoot) {
        var locator = FhirResourceLocator.create(projectRoot);
        Collection<CamundaField> fields = extensionElements.getElementsQuery().filterByType(CamundaField.class).list();

        // remember values for cross‑checks
        String profileVal = null;
        String messageNameVal = null;
        String instantiatesVal = null;
        boolean structureFoundForProfile = false;

        for (CamundaField field : fields) {
            String fieldName = field.getCamundaName();
            FieldValue fv = readFieldValue(field);

            // If field value is a plain string literal, record a success for clarity.
            if (fv != null && fv.type == FieldValueType.STRING) {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "Field injection '" + fieldName + "' provided as string literal"));
            }

            // expression? -> immediate error + skip further processing of this field
            if (fv != null && fv.type == FieldValueType.EXPRESSION) {
                issues.add(new BpmnFieldInjectionNotStringLiteralLintItem(
                        elementId, bpmnFile, processId, fieldName,
                        "Field injection '" + fieldName + "' is provided as expression, expected string literal"));
                continue;
            }


            String literal = (fv != null) ? fv.value : null;

            switch (fieldName) {
                case "profile" -> {
                    checkProfileField(elementId, bpmnFile, processId, issues, literal, projectRoot);
                    profileVal = literal;
                    if (!isEmpty(literal) && locator.structureDefinitionExists(literal, projectRoot)) {
                        structureFoundForProfile = true;
                        issues.add(new BpmnElementLintItemSuccess(
                                elementId,
                                bpmnFile,
                                processId,
                                "StructureDefinition for profile '" + literal + "' found"));
                    }
                }
                case "messageName" -> {
                    if (isEmpty(literal)) {
                        issues.add(new BpmnFieldInjectionMessageValueEmptyLintItem(elementId, bpmnFile, processId));
                    } else {
                        messageNameVal = literal;
                        issues.add(new BpmnElementLintItemSuccess(elementId, bpmnFile, processId,
                                "Field 'messageName' is valid with value: '" + literal + "'"));
                    }
                }
                case "instantiatesCanonical" -> {
                    checkInstantiatesCanonicalField(elementId, literal, bpmnFile, processId, issues, projectRoot);
                    instantiatesVal = literal;
                }
                default -> issues.add(new BpmnUnknownFieldInjectionLintItem(elementId, bpmnFile, processId, fieldName));
            }
        }

        /*  cross‑checks that need both BPMN + FHIR context  */
        if (structureFoundForProfile && !isEmpty(profileVal)) {
            performCrossChecks(elementId, issues, bpmnFile, processId, projectRoot,
                    profileVal, instantiatesVal, messageNameVal);
        }
    }

    /* helper that outsources the bulky FHIR cross‑linting logic */
    /**
     * Performs semantic cross‑checks between the BPMN field values and
     * the referenced FHIR resources.
     *
     * @param elementId      BPMN element id
     * @param issues         collector for lints findings
     * @param bpmnFile       source BPMN file
     * @param processId      id of the enclosing process definition
     * @param projectRoot    DSF project root directory
     * @param profileVal     canonical URL of the <code>StructureDefinition</code>
     * @param instantiatesVal canonical URL used in <code>instantiatesCanonical</code>
     * @param messageNameVal expected messageName fixed value
     */
    private static void performCrossChecks(String elementId,
                                           List<BpmnElementLintItem> issues,
                                           File bpmnFile,
                                           String processId,
                                           File projectRoot,
                                           String profileVal,
                                           String instantiatesVal,
                                           String messageNameVal) {
        var locator = FhirResourceLocator.create(projectRoot);
        File structureFile = locator.findStructureDefinitionFile(profileVal, projectRoot);
        if (structureFile == null) return; // Warn already added earlier.

        try {
            var doc = FhirResourceParser.parseFhirFile(structureFile.toPath());

            if (!isEmpty(instantiatesVal)) {
                String fixedCanonical = FhirResourceExtractor.getTaskInstantiatesCanonicalValue(doc);
                if (fixedCanonical == null) {
                    issues.add(new BpmnNoStructureDefinitionFoundForMessageLintItem(LinterSeverity.ERROR,
                            elementId, bpmnFile, processId, structureFile.getName(),
                            "StructureDefinition lacks <fixedCanonical> for Task.instantiatesCanonical"));
                } else if (fixedCanonical.isBlank()) {
                    issues.add(new BpmnNoStructureDefinitionFoundForMessageLintItem(LinterSeverity.ERROR,
                            elementId, bpmnFile, processId, structureFile.getName(),
                            "<fixedCanonical> present but empty in StructureDefinition"));
                } else {
                    issues.add(new BpmnElementLintItemSuccess(elementId, bpmnFile, processId,
                            "StructureDefinition contains valid <fixedCanonical>."));
                }
            }

            String fixedString = FhirResourceExtractor.getTaskMessageNameFixedStringValue(doc);
            if (fixedString == null || fixedString.isBlank()) {
                issues.add(new BpmnNoStructureDefinitionFoundForMessageLintItem(LinterSeverity.ERROR,
                        elementId, bpmnFile, processId, structureFile.getName(),
                        "StructureDefinition has no valid <fixedString> for message‑name."));
            } else {
                issues.add(new BpmnElementLintItemSuccess(elementId, bpmnFile, processId,
                        "StructureDefinition contains valid <fixedString>."));
            }
        } catch (Exception ignored) { /* parsing errors are ignored */ }

        // ActivityDefinition checks
        if (!isEmpty(instantiatesVal)) {
            boolean actDefFound = locator.activityDefinitionExistsForInstantiatesCanonical(instantiatesVal, projectRoot);
            if (!actDefFound) {
                issues.add(new BpmnNoActivityDefinitionFoundForMessageLintItem(LinterSeverity.WARN,
                        elementId, bpmnFile, processId, instantiatesVal,
                        "No ActivityDefinition found for instantiatesCanonical " + instantiatesVal));
            } else {
                issues.add(new BpmnElementLintItemSuccess(elementId, bpmnFile, processId,
                        "ActivityDefinition exists for instantiatesCanonical: '" + instantiatesVal + "'."));

                if (!isEmpty(messageNameVal) &&
                        !locator.activityDefinitionHasMessageName(messageNameVal, projectRoot)) {
                    issues.add(new BpmnNoActivityDefinitionFoundForMessageLintItem(LinterSeverity.ERROR,
                            elementId, bpmnFile, processId, instantiatesVal,
                            "ActivityDefinition does not contain message name '" + messageNameVal + "'."));
                }
            }
        }
    }

}