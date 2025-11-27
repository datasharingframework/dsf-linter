package dev.dsf.linter.constants;

/**
 * Represents different BPMN element types for interface validation.
 * Each type maps to specific expected interfaces in V1 and V2 APIs.
 */
public enum BpmnElementType {

    /**
     * BPMN ServiceTask - implements business logic.
     */
    SERVICE_TASK,

    /**
     * BPMN SendTask - sends messages to external participants.
     */
    SEND_TASK,

    /**
     * BPMN ReceiveTask - waits for incoming messages.
     */
    RECEIVE_TASK,

    /**
     * BPMN Message Intermediate Throw Event.
     */
    MESSAGE_INTERMEDIATE_THROW_EVENT,

    /**
     * BPMN Message End Event.
     */
    MESSAGE_END_EVENT,

    /**
     * BPMN UserTask listener.
     */
    USER_TASK_LISTENER,

    /**
     * BPMN Execution listener.
     */
    EXECUTION_LISTENER,

    /**
     * Generic/unknown element type - uses general validation.
     */
    GENERIC
}