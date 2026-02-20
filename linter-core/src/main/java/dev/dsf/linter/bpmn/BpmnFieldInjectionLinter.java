package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.resource.FhirResourceExtractor;
import dev.dsf.linter.util.resource.FhirResourceLocator;
import dev.dsf.linter.util.resource.FhirResourceParser;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaField;
import org.camunda.bpm.model.xml.instance.DomElement;

import java.io.File;
import java.util.*;

import static dev.dsf.linter.util.linting.LintingUtils.containsPlaceholder;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Specialized linter class for validating Camunda field injections on BPMN elements against
 * business logic and FHIR-related constraints.
 * 
 * <p>
 * The {@code BpmnFieldInjectionLinter} serves as a specialized component for performing comprehensive
 * validation of Camunda field injections (e.g., {@code <camunda:field>}) used in BPMN 2.0 models
 * for Camunda workflows. It ensures that field injections are properly configured as string literals,
 * follow DSF naming conventions, and reference valid FHIR resources. The linter performs both direct
 * field validation and cross-validation between BPMN configuration and corresponding FHIR artifacts.
 * </p>
 *
 * <h2>Architecture</h2>
 * <p>
 * This class is designed as a utility class with static methods that can be called from specialized
 * linter classes such as {@link BpmnTaskLinter}, {@link BpmnEventLinter}, and {@link BpmnModelLinter}.
 * The validation process involves two main steps:
 * </p>
 * <ol>
 *   <li><strong>Direct Element Validation</strong>: Checks extension elements on the BPMN element itself</li>
 *   <li><strong>Nested Event Definition Validation</strong>: For {@link ThrowEvent} and {@link EndEvent}
 *       elements, inspects attached {@link MessageEventDefinition} elements for field injections</li>
 * </ol>
 *
 * <h2>Validation Categories</h2>
 * <p>
 * The linter performs the following categories of checks:
 * </p>
 *
 * <h3>Field Value Type Validation</h3>
 * <ul>
 *   <li><strong>String Literal Requirement</strong>: Validates that field values are provided as
 *       string literals rather than expressions, ensuring static configuration that can be validated
 *       at linting time</li>
 *   <li><strong>Expression Detection</strong>: Issues errors when field values are provided as
 *       expressions, which cannot be statically validated</li>
 * </ul>
 *
 * <h3>Profile Field Validation</h3>
 * <ul>
 *   <li><strong>Non-Empty Validation</strong>: Ensures that profile field values are not empty</li>
 *   <li><strong>Version Placeholder Validation</strong>: Validates that profile field values contain
 *       version placeholders (e.g., {@code ${version}} or {@code #{version}}) for dynamic configuration</li>
 *   <li><strong>FHIR StructureDefinition Validation</strong>: Verifies that profile field values correspond
 *       to existing FHIR StructureDefinition resources in the project</li>
 * </ul>
 *
 * <h3>Message Name Field Validation</h3>
 * <ul>
 *   <li><strong>Non-Empty Validation</strong>: Ensures that messageName field values are not empty</li>
 *   <li><strong>Cross-Validation</strong>: Performs cross-validation with ActivityDefinition resources
 *       to ensure message names are properly defined</li>
 * </ul>
 *
 * <h3>InstantiatesCanonical Field Validation</h3>
 * <ul>
 *   <li><strong>Non-Empty Validation</strong>: Ensures that instantiatesCanonical field values are not empty</li>
 *   <li><strong>Version Placeholder Validation</strong>: Validates that instantiatesCanonical field values
 *       contain version placeholders for dynamic configuration</li>
 *   <li><strong>FHIR ActivityDefinition Validation</strong>: Verifies that instantiatesCanonical field values
 *       correspond to existing FHIR ActivityDefinition resources in the project</li>
 * </ul>
 *
 * <h3>FHIR Cross-Validation</h3>
 * <p>
 * The linter performs semantic cross-checks between BPMN field values and referenced FHIR resources:
 * </p>
 * <ul>
 *   <li><strong>StructureDefinition Fixed Values</strong>: Validates that StructureDefinition resources
 *       contain the required fixed values for {@code Task.instantiatesCanonical} and message name</li>
 *   <li><strong>ActivityDefinition Message Name</strong>: Verifies that ActivityDefinition resources
 *       contain the message name specified in the BPMN field injection</li>
 *   <li><strong>Resource Consistency</strong>: Ensures consistency between BPMN configuration and
 *       FHIR resource definitions</li>
 * </ul>
 *
 * <h2>Supported Field Names</h2>
 * <p>
 * The linter validates the following standard DSF field names:
 * </p>
 * <ul>
 *   <li><strong>profile</strong>: Canonical URL of the FHIR StructureDefinition resource</li>
 *   <li><strong>messageName</strong>: Name of the message used in FHIR ActivityDefinition and StructureDefinition</li>
 *   <li><strong>instantiatesCanonical</strong>: Canonical URL used in FHIR ActivityDefinition resources</li>
 * </ul>
 * <p>
 * Any other field names are reported as unknown fields, which may indicate configuration errors.
 * </p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * File projectRoot = new File("/path/to/project");
 * 
 * List<BpmnElementLintItem> issues = new ArrayList<>();
 * BaseElement element = ...; // obtained from BPMN model (e.g., SendTask, EndEvent)
 * File bpmnFile = new File("process.bpmn");
 * String processId = "myProcess";
 * 
 * BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
 *     element, issues, bpmnFile, processId, projectRoot);
 * 
 * for (BpmnElementLintItem issue : issues) {
 *     System.out.println(issue.getSeverity() + ": " + issue.getMessage());
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe as it only contains static methods with no shared mutable state.
 * All methods operate on their parameters and do not maintain any internal state.
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">Camunda Extension Elements</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/delegation-code/field-injection/">Camunda Field Injection</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 *
 * @see BpmnModelLinter
 * @see BpmnTaskLinter
 * @see BpmnEventLinter
 * @see BpmnElementLintItem
 * @since 1.0
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
                issues.add(BpmnElementLintItem.success(
                        elementId,
                        bpmnFile,
                        processId,
                        "Field injection '" + fieldName + "' provided as string literal"));
            }

            // expression? -> immediate error + skip further processing of this field
            if (fv != null && fv.type == FieldValueType.EXPRESSION) {
                issues.add(new BpmnElementLintItem(
                        LinterSeverity.ERROR, LintingType.BPMN_FIELD_INJECTION_NOT_STRING_LITERAL,
                        elementId, bpmnFile, processId,
                        "Field injection '" + fieldName + "' is provided as expression, expected string literal")); //todo in api v2 is allowed
                continue;
            }


            String literal = (fv != null) ? fv.value : null;

            switch (fieldName) {
                case "profile" -> {
                    checkProfileField(elementId, bpmnFile, processId, issues, literal, projectRoot);
                    profileVal = literal;
                    if (!isEmpty(literal) && locator.structureDefinitionExists(literal, projectRoot)) {
                        structureFoundForProfile = true;
                        issues.add(BpmnElementLintItem.success(
                                elementId,
                                bpmnFile,
                                processId,
                                "StructureDefinition for profile '" + literal + "' found"));
                    }
                }
                case "messageName" -> {
                    if (isEmpty(literal)) {
                        issues.add(new BpmnElementLintItem(
                                LinterSeverity.ERROR, LintingType.BPMN_FIELD_INJECTION_MESSAGE_VALUE_EMPTY,
                                elementId, bpmnFile, processId,
                                "Field injection messageName is empty"));
                    } else {
                        messageNameVal = literal;
                        issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                                "Field 'messageName' is valid with value: '" + literal + "'"));
                    }
                }
                case "instantiatesCanonical" -> {
                    checkInstantiatesCanonicalField(elementId, literal, bpmnFile, processId, issues, projectRoot);
                    instantiatesVal = literal;
                }
                default -> issues.add(new BpmnElementLintItem(
                        LinterSeverity.WARN, LintingType.BPMN_UNKNOWN_FIELD_INJECTION,
                        elementId, bpmnFile, processId,
                        "Unknown field injection: " + fieldName));
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
                    issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                            LintingType.BPMN_NO_STRUCTURE_DEFINITION_FOUND_FOR_MESSAGE,
                            elementId, bpmnFile, processId,
                            "StructureDefinition lacks <fixedCanonical> for Task.instantiatesCanonical"));
                } else if (fixedCanonical.isBlank()) {
                    issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                            LintingType.BPMN_NO_STRUCTURE_DEFINITION_FOUND_FOR_MESSAGE,
                            elementId, bpmnFile, processId,
                            "<fixedCanonical> present but empty in StructureDefinition"));
                } else {
                    issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                            "StructureDefinition contains valid <fixedCanonical>."));
                }
            }

            String fixedString = FhirResourceExtractor.getTaskMessageNameFixedStringValue(doc);
            if (fixedString == null || fixedString.isBlank()) {
                issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                        LintingType.BPMN_NO_STRUCTURE_DEFINITION_FOUND_FOR_MESSAGE,
                        elementId, bpmnFile, processId,
                        "StructureDefinition has no valid <fixedString> for message‑name."));
            } else {
                issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                        "StructureDefinition contains valid <fixedString>."));
            }
        } catch (Exception ignored) { /* parsing errors are ignored */ }

        // ActivityDefinition checks
        if (!isEmpty(instantiatesVal)) {
            boolean actDefFound = locator.activityDefinitionExistsForInstantiatesCanonical(instantiatesVal, projectRoot);
            if (!actDefFound) {
                issues.add(new BpmnElementLintItem(LinterSeverity.WARN,
                        LintingType.BPMN_NO_ACTIVITY_DEFINITION_FOUND_FOR_MESSAGE,
                        elementId, bpmnFile, processId,
                        "No ActivityDefinition found for instantiatesCanonical " + instantiatesVal));
            } else {
                issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                        "ActivityDefinition exists for instantiatesCanonical: '" + instantiatesVal + "'."));

                if (!isEmpty(messageNameVal) &&
                        !locator.activityDefinitionHasMessageName(messageNameVal, projectRoot)) {
                    issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                            LintingType.BPMN_NO_ACTIVITY_DEFINITION_FOUND_FOR_MESSAGE,
                            elementId, bpmnFile, processId,
                            "ActivityDefinition does not contain message name '" + messageNameVal + "'."));
                }
            }
        }
    }

    /**
     * Checks the "profile" field value for validity.
     * <p>
     * This method verifies that the profile field is not empty, contains a version placeholder,
     * and corresponds to an existing FHIR StructureDefinition. If any check fails, an appropriate
     * lint issue is added. Additionally, if a check passes, a success item is recorded.
     * </p>
     *
     * @param elementId   the identifier of the BPMN element being lintated
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the identifier of the BPMN process containing the element
     * @param issues      the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param literalValue the literal value of the profile field from the BPMN element
     * @param projectRoot the project root directory containing FHIR resources
     */
    public static void checkProfileField(
            String elementId,
            File bpmnFile,
            String processId,
            List<BpmnElementLintItem> issues,
            String literalValue,
            File projectRoot) {
        var locator = FhirResourceLocator.create(projectRoot);
        if (isEmpty(literalValue)) {
            issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                    LintingType.BPMN_FIELD_INJECTION_PROFILE_EMPTY,
                    elementId, bpmnFile, processId,
                    "Field injection profile is empty"));
            return;
        }

        issues.add(BpmnElementLintItem.success(
                elementId, bpmnFile, processId,
                "Profile field is provided with value: '" + literalValue + "'"));

        if (!containsPlaceholder(literalValue)) {
            issues.add(new BpmnElementLintItem(LinterSeverity.WARN,
                    LintingType.BPMN_FIELD_INJECTION_PROFILE_NO_VERSION_PLACEHOLDER,
                    elementId, bpmnFile, processId,
                    "Profile field does not contain version placeholder: " + literalValue));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Profile field contains a version placeholder: '" + literalValue + "'"));
        }

        if (!locator.structureDefinitionExists(literalValue, projectRoot)) {
            issues.add(new BpmnElementLintItem(LinterSeverity.WARN,
                    LintingType.BPMN_NO_STRUCTURE_DEFINITION_FOUND_FOR_MESSAGE,
                    elementId, bpmnFile, processId,
                    "StructureDefinition for the profile: [" + literalValue + "] not found."));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "StructureDefinition found for profile: '" + literalValue + "'"));
        }
    }

    /**
     * Checks instantiatesCanonical field injection.
     *
     * @param elementId   the identifier of the BPMN element being lintated
     * @param literalValue the literal value of the instantiatesCanonical field from the BPMN element
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the identifier of the BPMN process containing the element
     * @param issues      the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param projectRoot the project root directory containing FHIR resources
     */
    public static void checkInstantiatesCanonicalField(
            String elementId,
            String literalValue,
            File bpmnFile,
            String processId,
            List<BpmnElementLintItem> issues,
            File projectRoot) {

        if (isEmpty(literalValue)) {
            issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                    LintingType.BPMN_FIELD_INJECTION_INSTANTIATES_CANONICAL_EMPTY,
                    elementId, bpmnFile, processId,
                    "Field injection instantiatesCanonical is empty"));
            return;
        }

        if (!containsPlaceholder(literalValue)) {
            issues.add(new BpmnElementLintItem(LinterSeverity.WARN,
                    LintingType.BPMN_FIELD_INJECTION_INSTANTIATES_CANONICAL_NO_VERSION_PLACEHOLDER,
                    elementId, bpmnFile, processId,
                    "instantiatesCanonical does not contain version placeholder"));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "instantiatesCanonical field is valid with value: '" + literalValue + "'"));
        }
    }

}