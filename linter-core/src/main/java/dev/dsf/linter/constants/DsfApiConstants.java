package dev.dsf.linter.constants;

/**
 * Central repository for all DSF API-related string constants.
 * Provides both general interface arrays and element-specific interface mappings.
 */
public final class DsfApiConstants {

    private DsfApiConstants() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    // ==================== PLUGIN DEFINITION INTERFACES ====================

    public static final String V1_PLUGIN_INTERFACE = "dev.dsf.bpe.v1.ProcessPluginDefinition";
    public static final String V2_PLUGIN_INTERFACE = "dev.dsf.bpe.v2.ProcessPluginDefinition";

    // ==================== V1 API - TASK INTERFACES ====================

    public static final String V1_JAVA_DELEGATE = "org.camunda.bpm.engine.delegate.JavaDelegate";
    public static final String V1_TASK_LISTENER = "org.camunda.bpm.engine.delegate.TaskListener";
    public static final String V1_EXECUTION_LISTENER = "org.camunda.bpm.engine.delegate.ExecutionListener";
    public static final String V1_DEFAULT_USER_TASK_LISTENER = "dev.dsf.bpe.v1.activity.DefaultUserTaskListener";

    // ==================== V2 API - ACTIVITY INTERFACES ====================

    public static final String V2_SERVICE_TASK = "dev.dsf.bpe.v2.activity.ServiceTask";
    public static final String V2_MESSAGE_SEND_TASK = "dev.dsf.bpe.v2.activity.MessageSendTask";
    public static final String V2_MESSAGE_INTERMEDIATE_THROW = "dev.dsf.bpe.v2.activity.MessageIntermediateThrowEvent";
    public static final String V2_MESSAGE_END_EVENT = "dev.dsf.bpe.v2.activity.MessageEndEvent";
    public static final String V2_USER_TASK_LISTENER = "dev.dsf.bpe.v2.activity.UserTaskListener";
    public static final String V2_EXECUTION_LISTENER = "dev.dsf.bpe.v2.activity.ExecutionListener";
    public static final String V2_DEFAULT_USER_TASK_LISTENER = "dev.dsf.bpe.v2.activity.DefaultUserTaskListener";

    // ==================== V1 API - ABSTRACT BASE CLASSES  ====================

    public static final String V1_ABSTRACT_SERVICE_DELEGATE = "dev.dsf.bpe.v1.activity.AbstractServiceDelegate";
    public static final String V1_ABSTRACT_TASK_MESSAGE_SEND = "dev.dsf.bpe.v1.activity.AbstractTaskMessageSend";

    // ==================== V1 ELEMENT-SPECIFIC INTERFACE MAPPINGS ====================

    /**
     * Expected interface for ServiceTask in V1.
     * ServiceTasks must implement JavaDelegate.
     */
    public static final String[] V1_SERVICE_TASK_INTERFACES = {
            V1_JAVA_DELEGATE
    };

    /**
     * Expected interface for SendTask in V1.
     * SendTasks must implement JavaDelegate.
     */
    public static final String[] V1_SEND_TASK_INTERFACES = {
            V1_JAVA_DELEGATE
    };

    /**
     * Expected interface for Message Intermediate Throw Event in V1.
     * Must implement JavaDelegate.
     */
    public static final String[] V1_INTERMEDIATE_THROW_INTERFACES = {
            V1_JAVA_DELEGATE
    };

    /**
     * Expected interface for Message End Event in V1.
     * Must implement JavaDelegate.
     */
    public static final String[] V1_END_EVENT_INTERFACES = {
            V1_JAVA_DELEGATE
    };

    /**
     * Expected interface for UserTask listeners in V1.
     * Must implement TaskListener (NOT JavaDelegate).
     */
    public static final String[] V1_TASK_LISTENER_INTERFACES = {
            V1_TASK_LISTENER
    };

    /**
     * Expected interface for Execution Listeners in V1.
     * Must implement ExecutionListener.
     */
    public static final String[] V1_EXECUTION_LISTENER_INTERFACES = {
            V1_EXECUTION_LISTENER
    };

    // ==================== V2 ELEMENT-SPECIFIC INTERFACE MAPPINGS ====================

    /**
     * Expected interface for ServiceTask in V2.
     */
    public static final String[] V2_SERVICE_TASK_INTERFACES = {
            V2_SERVICE_TASK
    };

    /**
     * Expected interface for SendTask in V2.
     */
    public static final String[] V2_SEND_TASK_INTERFACES = {
            V2_MESSAGE_SEND_TASK
    };

    /**
     * Expected interface for Message Intermediate Throw Event in V2.
     */
    public static final String[] V2_INTERMEDIATE_THROW_INTERFACES = {
            V2_MESSAGE_INTERMEDIATE_THROW
    };

    /**
     * Expected interface for Message End Event in V2.
     */
    public static final String[] V2_END_EVENT_INTERFACES = {
            V2_MESSAGE_END_EVENT
    };

    /**
     * Expected interfaces for UserTask listeners in V2.
     */
    public static final String[] V2_USER_TASK_LISTENER_INTERFACES = {
            V2_USER_TASK_LISTENER
    };

    /**
     * Expected interface for Execution Listeners in V2.
     * Note: V2 has its OWN ExecutionListener interface (dev.dsf.bpe.v2.activity.ExecutionListener),
     * which is DIFFERENT from the Camunda ExecutionListener used in V1!
     */
    public static final String[] V2_EXECUTION_LISTENER_INTERFACES = {
            V2_EXECUTION_LISTENER
    };

    // ==================== GENERAL INTERFACE ARRAYS (for fallback/generic validation) ====================

    /**
     * All V1 task interfaces. Used ONLY for general/fallback V1 validation.
     * For element-specific validation, use the element-specific arrays above.
     */
    public static final String[] V1_INTERFACES = {
            V1_JAVA_DELEGATE,
            V1_TASK_LISTENER,
            V1_EXECUTION_LISTENER
    };

    /**
     * All V2 activity interfaces. Used ONLY for general/fallback V2 validation.
     * For element-specific validation, use the element-specific arrays above.
     * Note: V2 uses its own ExecutionListener interface, not the Camunda one!
     */
    public static final String[] V2_INTERFACES = {
            V2_SERVICE_TASK,
            V2_MESSAGE_SEND_TASK,
            V2_MESSAGE_INTERMEDIATE_THROW,
            V2_MESSAGE_END_EVENT,
            V2_USER_TASK_LISTENER,
            V2_EXECUTION_LISTENER
    };

    // ==================== SERVICE FILE NAMES ====================

    public static final String V1_SERVICE_FILE = V1_PLUGIN_INTERFACE;
    public static final String V2_SERVICE_FILE = V2_PLUGIN_INTERFACE;
}