package dev.dsf.linter.output;

/**
 * Enumeration of all lint issue types with optional default messages.
 * <p>
 * Each enum constant represents a specific type of linting issue that can be
 * detected during validation. The type serves as a unique identifier and can
 * optionally provide a default human-readable message.
 * </p>
 */
public enum LintingType {

    // ==================== GENERAL ====================
    UNKNOWN("Unknown issue."),
    SUCCESS("Validation successful."),


    // ==================== BPMN SERVICE TASK ====================
    BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_EMPTY("Service task implementation class is empty."),
    BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_NOT_FOUND("Service task implementation class not found."),
    BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_NOT_IMPLEMENTING_JAVA_DELEGATE("Service task implementation class does not implement JavaDelegate."),
    BPMN_SERVICE_TASK_NAME_EMPTY("Service task name is empty."),
    BPMN_SERVICE_TASK_IMPLEMENTATION_NOT_EXIST("Service task implementation does not exist."),
    BPMN_SERVICE_TASK_NO_INTERFACE_CLASS_IMPLEMENTING("Service task implementation class does not implement required interface."),
    BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_NOT_EXTENDING_ABSTRACT_SERVICE_DELEGATE("Service task implementation class does not extend AbstractServiceDelegate."),

    // ==================== BPMN MESSAGE SEND EVENT ====================
    BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_EMPTY("Message send event implementation class is empty."),
    BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND("Message send event implementation class not found."),
    BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_IMPLEMENTING_JAVA_DELEGATE("Message send event implementation class does not implement JavaDelegate."),

    // ==================== BPMN MESSAGE SEND TASK ====================
    BPMN_MESSAGE_SEND_TASK_IMPLEMENTATION_CLASS_EMPTY("Message send task implementation class is empty."),
    BPMN_MESSAGE_SEND_TASK_IMPLEMENTATION_CLASS_NOT_FOUND("Message send task implementation class not found."),

    // ==================== BPMN SEND TASK ====================
    BPMN_SEND_TASK_NO_INTERFACE_CLASS_IMPLEMENTING("Send task implementation class does not implement required interface."),
    BPMN_SEND_TASK_IMPLEMENTATION_CLASS_NOT_EXTENDING_ABSTRACT_TASK_MESSAGE_SEND("Send task implementation class does not extend AbstractTaskMessageSend."),

    // ==================== BPMN FLOW ====================
    BPMN_MESSAGE_START_EVENT_NOT_FOUND("Message start event not found."),
    BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_EMPTY("Message start event message name is empty."),
    BPMN_FLOATING_ELEMENT("BPMN element is outside of message start event triggered flow."),
    BPMN_FLOW_ELEMENT("BPMN flow element issue."),

    // ==================== BPMN EVENTS ====================
    BPMN_EVENT_NAME_EMPTY("Event name is empty."),
    BPMN_ERROR_BOUNDARY_EVENT_ERROR_CODE_EMPTY("Error boundary event error code is empty."),
    BPMN_ERROR_BOUNDARY_EVENT_ERROR_CODE_VARIABLE_EMPTY("Error boundary event error code variable is empty."),
    BPMN_ERROR_BOUNDARY_EVENT_ERROR_NAME_EMPTY("Error boundary event error name is empty."),
    BPMN_ERROR_BOUNDARY_EVENT_NAME_EMPTY("Error boundary event name is empty."),
    BPMN_MESSAGE_INTERMEDIATE_THROW_EVENT_HAS_MESSAGE("Message intermediate throw event has message."),
    BPMN_START_EVENT_NOT_PART_OF_SUB_PROCESS("Start event is not part of subprocess."),
    BPMN_END_EVENT_NOT_PART_OF_SUB_PROCESS("End event is not part of subprocess."),
    BPMN_MESSAGE_INTERMEDIATE_CATCH_EVENT_NAME_EMPTY("Message intermediate catch event name is empty."),
    BPMN_MESSAGE_INTERMEDIATE_CATCH_EVENT_MESSAGE_NAME_EMPTY("Message intermediate catch event message name is empty."),
    BPMN_MESSAGE_BOUNDARY_EVENT_NAME_EMPTY("Message boundary event name is empty."),
    BPMN_MESSAGE_BOUNDARY_EVENT_MESSAGE_NAME_EMPTY("Message boundary event message name is empty."),
    BPMN_SIGNAL_INTERMEDIATE_THROW_EVENT_NAME_EMPTY("Signal intermediate throw event name is empty."),
    BPMN_SIGNAL_INTERMEDIATE_THROW_EVENT_SIGNAL_EMPTY("Signal intermediate throw event signal is empty."),
    BPMN_SIGNAL_END_EVENT_NAME_EMPTY("Signal end event name is empty."),
    BPMN_SIGNAL_END_EVENT_SIGNAL_EMPTY("Signal end event signal is empty."),
    BPMN_END_EVENT_INSIDE_SUB_PROCESS_SHOULD_HAVE_ASYNC_AFTER_TRUE("End event inside subprocess should have asyncAfter=true."),
    BPMN_END_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING("End event implementation class does not implement required interface."),

    // ==================== BPMN GATEWAYS ====================
    BPMN_EXCLUSIVE_GATEWAY_HAS_MULTIPLE_OUTGOING_FLOWS_BUT_NAME_IS_EMPTY("Exclusive gateway has multiple outgoing flows but name is empty."),
    BPMN_INCLUSIVE_GATEWAY_HAS_MULTIPLE_OUTGOING_FLOWS_BUT_NAME_IS_EMPTY("Inclusive gateway has multiple outgoing flows but name is empty."),

    // ==================== BPMN USER TASK ====================
    BPMN_USER_TASK_NAME_EMPTY("User task name is empty."),
    BPMN_USER_TASK_FORM_KEY_EMPTY("User task formKey is empty."),
    BPMN_USER_TASK_FORM_KEY_IS_NOT_AN_EXTERNAL_FORM("User task formKey is not an external form."),
    BPMN_USER_TASK_LISTENER_MISSING_CLASS_ATTRIBUTE("User task listener is missing class attribute."),
    BPMN_USER_TASK_LISTENER_JAVA_CLASS_NOT_FOUND("User task listener Java class not found."),
    BPMN_USER_TASK_LISTENER_NOT_EXTENDING_OR_IMPLEMENTING_REQUIRED_CLASS("User task listener does not extend or implement required class."),
    BPMN_USER_TASK_LISTENER_INCOMPLETE_TASK_OUTPUT_FIELDS("User task listener has incomplete task output fields."),
    BPMN_USER_TASK_LISTENER_TASK_OUTPUT_CODE_INVALID_FHIR_RESOURCE("User task listener task output code references invalid FHIR resource."),
    BPMN_USER_TASK_LISTENER_TASK_OUTPUT_SYSTEM_INVALID_FHIR_RESOURCE("User task listener task output system references invalid FHIR resource."),
    BPMN_USER_TASK_LISTENER_TASK_OUTPUT_VERSION_NO_PLACEHOLDER("User task listener task output version does not use placeholder."),

    // ==================== BPMN FIELD INJECTION ====================
    BPMN_FIELD_INJECTION_NOT_STRING_LITERAL("Field injection value is not a string literal."),
    BPMN_UNKNOWN_FIELD_INJECTION("Unknown field injection."),
    BPMN_FIELD_INJECTION_INSTANTIATES_CANONICAL_EMPTY("Field injection instantiatesCanonical is empty."),
    BPMN_FIELD_INJECTION_INSTANTIATES_CANONICAL_NO_VERSION_PLACEHOLDER("Field injection instantiatesCanonical does not use version placeholder."),
    BPMN_FIELD_INJECTION_PROFILE_EMPTY("Field injection profile is empty."),
    BPMN_FIELD_INJECTION_PROFILE_NO_VERSION_PLACEHOLDER("Field injection profile does not use version placeholder."),
    BPMN_FIELD_INJECTION_MESSAGE_VALUE_EMPTY("Field injection message value is empty."),

    // ==================== BPMN SUBPROCESS ====================
    BPMN_SUB_PROCESS_HAS_MULTI_INSTANCE_BUT_IS_NOT_ASYNC_BEFORE_TRUE("Subprocess has multi-instance but is not asyncBefore=true."),

    // ==================== BPMN EXECUTION LISTENER ====================
    BPMN_EXECUTION_LISTENER_CLASS_NOT_FOUND("Execution listener class not found."),
    BPMN_EXECUTION_LISTENER_NOT_IMPLEMENTING_REQUIRED_INTERFACE("Execution listener does not implement required interface."),

    // ==================== BPMN PROCESS ====================
    BPMN_PROCESS_ID_PATTERN_MISMATCH("Process ID does not match required pattern: domain_processname (e.g. testorg_myprocess)."),
    BPMN_PROCESS_ID_EMPTY("Process ID is empty."),

    // ==================== BPMN GENERAL ====================
    BPMN_PRACTITIONERS_HAS_NO_VALUE_OR_NULL("Practitioners has no value or is null."),
    BPMN_PRACTITIONER_ROLE_HAS_NO_VALUE_OR_NULL("PractitionerRole has no value or is null."),
    BPMN_NO_ACTIVITY_DEFINITION_FOUND_FOR_MESSAGE("No ActivityDefinition found for message."),
    BPMN_NO_STRUCTURE_DEFINITION_FOUND_FOR_MESSAGE("No StructureDefinition found for message."),

    // ==================== FHIR GENERAL ====================
    INVALID_FHIR_URL("Invalid FHIR URL."),
    INVALID_FHIR_STATUS("Invalid FHIR status."),
    INVALID_FHIR_ACCESS_TAG("Invalid FHIR access tag."),
    MISSING_FHIR_ACCESS_TAG("Missing FHIR access tag."),
    FHIR_STATUS_IS_NOT_SET_AS_UNKNOWN("FHIR status is not set as unknown."),
    INVALID_FHIR_KIND("Invalid FHIR kind."),
    FHIR_KIND_NOT_SET_AS_TASK("FHIR kind is not set as Task."),
    NO_EXTENSION_PROCESS_AUTHORIZATION_FOUND("No extension process-authorization found."),
    MISSING_READ_ACCESS_TAG("Missing read access tag."),

    // ==================== FHIR ACTIVITY DEFINITION ====================
    ACTIVITY_DEFINITION_ENTRY_INVALID_REQUESTER("ActivityDefinition entry has invalid requester."),
    ACTIVITY_DEFINITION_ENTRY_INVALID_RECIPIENT("ActivityDefinition entry has invalid recipient."),
    ACTIVITY_DEFINITION_ENTRY_MISSING_REQUESTER("ActivityDefinition entry is missing requester."),
    ACTIVITY_DEFINITION_ENTRY_MISSING_RECIPIENT("ActivityDefinition entry is missing recipient."),
    ACTIVITY_DEFINITION_PROFILE_NO_PLACEHOLDER("ActivityDefinition profile does not use placeholder."),
    ACTIVITY_DEFINITION_MISSING_PROFILE("ActivityDefinition is missing profile."),

    // ==================== FHIR STRUCTURE DEFINITION ====================
    STRUCTURE_DEFINITION_READ_ACCESS_TAG_MISSING("StructureDefinition read access tag missing."),
    STRUCTURE_DEFINITION_URL_MISSING("StructureDefinition URL missing."),
    STRUCTURE_DEFINITION_VERSION_NO_PLACEHOLDER("StructureDefinition version does not use placeholder."),
    STRUCTURE_DEFINITION_DATE_NO_PLACEHOLDER("StructureDefinition date does not use placeholder."),
    STRUCTURE_DEFINITION_SNAPSHOT_PRESENT("StructureDefinition snapshot should not be present."),
    STRUCTURE_DEFINITION_ELEMENT_ID_DUPLICATE("StructureDefinition element ID is duplicate."),
    STRUCTURE_DEFINITION_ELEMENT_ID_MISSING("StructureDefinition element is missing ID."),
    STRUCTURE_DEFINITION_DIFFERENTIAL_MISSING("StructureDefinition differential is missing."),
    STRUCTURE_DEFINITION_INVALID_STATUS("StructureDefinition has invalid status."),
    STRUCTURE_DEFINITION_SLICE_MAX_TOO_HIGH("StructureDefinition slice max exceeds base max."),
    STRUCTURE_DEFINITION_SLICE_MIN_SUM_EXCEEDS_MAX("StructureDefinition slice min sum exceeds max."),
    STRUCTURE_DEFINITION_SLICE_MIN_SUM_ABOVE_BASE_MIN("StructureDefinition slice min sum above base min."),

    // ==================== FHIR TASK ====================
    FHIR_TASK_MISSING_INSTANTIATES_CANONICAL("Task is missing instantiatesCanonical."),
    FHIR_TASK_REQUESTER_ORGANIZATION_NO_PLACEHOLDER("Task requester organization does not use placeholder."),
    FHIR_TASK_RECIPIENT_ORGANIZATION_NO_PLACEHOLDER("Task recipient organization does not use placeholder."),
    FHIR_TASK_DATE_NO_PLACEHOLDER("Task date does not use placeholder."),
    FHIR_TASK_MISSING_REQUESTER("Task is missing requester."),
    FHIR_TASK_MISSING_RECIPIENT("Task is missing recipient."),
    FHIR_TASK_UNKNOWN_STATUS("Task has unknown status."),
    FHIR_TASK_CORRELATION_MISSING_BUT_REQUIRED("Task correlation is missing but required."),
    FHIR_TASK_MISSING_PROFILE("Task is missing profile."),
    FHIR_TASK_MISSING_STATUS("Task is missing status."),
    FHIR_TASK_STATUS_NOT_DRAFT("Task status is not 'draft'."),
    FHIR_TASK_VALUE_IS_NOT_SET_AS_ORDER("Task intent is not 'order'."),
    FHIR_TASK_INVALID_REQUESTER("Task requester identifier system is invalid."),
    FHIR_TASK_INVALID_RECIPIENT("Task recipient identifier system is invalid."),
    FHIR_TASK_INSTANTIATES_CANONICAL_PLACEHOLDER("Task instantiatesCanonical placeholder issue."),
    FHIR_TASK_UNKNOWN_INSTANTIATES_CANONICAL("Task instantiatesCanonical references unknown ActivityDefinition."),
    FHIR_TASK_COULD_NOT_LOAD_PROFILE("Could not load profile for Task."),
    FHIR_TASK_MISSING_INPUT("Task is missing input elements."),
    FHIR_TASK_INPUT_REQUIRED_CODING_SYSTEM_AND_CODING_CODE("Task input missing required system or code."),
    FHIR_TASK_INPUT_MISSING_VALUE("Task input is missing value."),
    FHIR_TASK_INPUT_DUPLICATE_SLICE("Task has duplicate input slice."),
    FHIR_TASK_REQUIRED_INPUT_WITH_CODE_MESSAGE_NAME("Task input with code message-name is required."),
    FHIR_TASK_STATUS_REQUIRED_INPUT_BUSINESS_KEY("Task business-key input required for current status."),
    FHIR_TASK_BUSINESS_KEY_EXISTS("Task business-key exists but should not."),
    FHIR_TASK_BUSINESS_KEY_CHECK_IS_SKIPPED("Task business key check skipped."),
    FHIR_TASK_CORRELATION_EXISTS("Task correlation-key exists but is not allowed."),
    FHIR_TASK_INPUT_INSTANCE_COUNT_BELOW_MIN("Task input instance count below minimum."),
    FHIR_TASK_INPUT_INSTANCE_COUNT_EXCEEDS_MAX("Task input instance count exceeds maximum."),
    FHIR_TASK_INPUT_SLICE_COUNT_BELOW_SLICE_MIN("Task input slice count below slice minimum."),
    FHIR_TASK_INPUT_SLICE_COUNT_EXCEEDS_SLICE_MAX("Task input slice count exceeds slice maximum."),
    FHIR_TASK_UNKNOWN_CODE("Task has unknown code."),
    FHIR_TASK_REQUESTER_ID_NOT_EXIST("Task requester ID does not exist."),
    FHIR_TASK_REQUESTER_ID_NO_PLACEHOLDER("Task requester ID missing placeholder."),
    FHIR_TASK_RECIPIENT_ID_NOT_EXIST("Task recipient ID does not exist."),
    FHIR_TASK_RECIPIENT_ID_NO_PLACEHOLDER("Task recipient ID missing placeholder."),

    // ==================== FHIR VALUE SET ====================
    FHIR_VALUE_SET_MISSING_URL("ValueSet is missing URL."),
    FHIR_VALUE_SET_MISSING_NAME("ValueSet is missing name."),
    FHIR_VALUE_SET_MISSING_TITLE("ValueSet is missing title."),
    FHIR_VALUE_SET_MISSING_PUBLISHER("ValueSet is missing publisher."),
    FHIR_VALUE_SET_MISSING_DESCRIPTION("ValueSet is missing description."),
    FHIR_VALUE_SET_VERSION_NO_PLACEHOLDER("ValueSet version does not use placeholder."),
    FHIR_VALUE_SET_DATE_NO_PLACEHOLDER("ValueSet date does not use placeholder."),
    FHIR_VALUE_SET_MISSING_COMPOSE_INCLUDE("ValueSet is missing compose.include."),
    FHIR_VALUE_SET_INCLUDE_MISSING_SYSTEM("ValueSet include is missing system."),
    FHIR_VALUE_SET_INCLUDE_VERSION_NO_PLACEHOLDER("ValueSet include version does not use placeholder."),
    FHIR_VALUE_SET_CONCEPT_MISSING_CODE("ValueSet concept is missing code."),
    FHIR_VALUE_SET_DUPLICATE_CONCEPT_CODE("ValueSet has duplicate concept code."),
    FHIR_VALUE_SET_UNKNOWN_CODE("ValueSet has unknown code."),
    FHIR_VALUE_SET_ORGANIZATION_ROLE_MISSING_VALID_CODE_VALUE("ValueSet organization role is missing valid code value."),
    FHIR_VALUE_SET_MISSING_READ_ACCESS_TAG_ALL_OR_LOCAL("ValueSet is missing read access tag ALL or LOCAL."),
    FHIR_VALUE_SET_FALSE_URL_REFERENCED("ValueSet references false URL."),

    // ==================== FHIR CODE SYSTEM ====================
    CODE_SYSTEM_MISSING_ELEMENT("CodeSystem is missing element."),
    CODE_SYSTEM_INVALID_STATUS("CodeSystem has invalid status."),
    CODE_SYSTEM_MISSING_CONCEPT("CodeSystem is missing concept."),
    CODE_SYSTEM_CONCEPT_MISSING_CODE("CodeSystem concept is missing code."),
    CODE_SYSTEM_DUPLICATE_CODE("CodeSystem has duplicate code."),
    CODE_SYSTEM_CONCEPT_MISSING_DISPLAY("CodeSystem concept is missing display."),
    CODE_SYSTEM_VERSION_NO_PLACEHOLDER("CodeSystem version does not use placeholder."),
    CODE_SYSTEM_DATE_NO_PLACEHOLDER("CodeSystem date does not use placeholder."),

    // ==================== FHIR QUESTIONNAIRE ====================
    QUESTIONNAIRE_MISSING_META_PROFILE("Questionnaire is missing meta.profile."),
    QUESTIONNAIRE_INVALID_META_PROFILE("Questionnaire has invalid meta.profile."),
    QUESTIONNAIRE_MISSING_READ_ACCESS_TAG("Questionnaire is missing read access tag."),
    QUESTIONNAIRE_INVALID_STATUS("Questionnaire has invalid status."),
    QUESTIONNAIRE_VERSION_NO_PLACEHOLDER("Questionnaire version does not use placeholder."),
    QUESTIONNAIRE_DATE_NO_PLACEHOLDER("Questionnaire date does not use placeholder."),
    QUESTIONNAIRE_MISSING_ITEM("Questionnaire is missing item."),
    QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_LINK_ID("Questionnaire item is missing linkId."),
    QUESTIONNAIRE_DUPLICATE_LINK_ID("Questionnaire has duplicate linkId."),
    QUESTIONNAIRE_UNUSUAL_LINK_ID("Questionnaire has unusual linkId."),
    QUESTIONNAIRE_MANDATORY_ITEM_INVALID_TYPE("Questionnaire mandatory item has invalid type."),
    QUESTIONNAIRE_MANDATORY_ITEM_NOT_REQUIRED("Questionnaire mandatory item is not required."),
    QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TEXT("Questionnaire item is missing text."),
    QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TYPE("Questionnaire item is missing type."),
    BPMN_USER_TASK_QUESTIONNAIRE_NOT_FOUND("User Task questionnaire not found for formKey."),

    // ==================== PLUGIN DEFINITION ====================
    PLUGIN_DEFINITION_BPMN_FILE_NOT_FOUND("Plugin definition BPMN file not found."),
    PLUGIN_DEFINITION_FHIR_RESOURCE_NOT_FOUND("Plugin definition FHIR resource not found."),
    PLUGIN_DEFINITION_BPMN_FILE_OUTSIDE_ROOT("Plugin definition BPMN file found outside expected root."),
    PLUGIN_DEFINITION_FHIR_FILE_OUTSIDE_ROOT("Plugin definition FHIR file found outside expected root."),
    PLUGIN_DEFINITION_NO_FHIR_RESOURCES_DEFINED("Plugin definition has no FHIR resources defined."),
    PLUGIN_DEFINITION_NO_PROCESS_MODEL_DEFINED("Plugin definition has no process model defined."),
    PLUGIN_DEFINITION_MISSING_SERVICE_LOADER_REGISTRATION("Plugin definition is missing ServiceLoader registration."),
    PLUGIN_DEFINITION_PROCESS_PLUGIN_RESOURCE_NOT_LOADED("Plugin definition process plugin resource not loaded."),
    PLUGIN_DEFINITION_UNPARSABLE_BPMN_RESOURCE("Plugin definition BPMN resource could not be parsed."),
    PLUGIN_DEFINITION_UNPARSABLE_FHIR_RESOURCE("Plugin definition FHIR resource could not be parsed.");

    private final String defaultMessage;

    LintingType() {
        this.defaultMessage = null;
    }

    LintingType(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    /**
     * Returns the default message for this lint type, or null if none defined.
     *
     * @return the default message or null
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * Returns the default message for this lint type, or a fallback if none defined.
     *
     * @param fallback the fallback message to use if no default is defined
     * @return the default message or the fallback
     */
    public String getDefaultMessageOrElse(String fallback) {
        return defaultMessage != null ? defaultMessage : fallback;
    }
}
