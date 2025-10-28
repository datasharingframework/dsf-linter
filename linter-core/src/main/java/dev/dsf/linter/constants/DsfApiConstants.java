package dev.dsf.linter.constants;

/**
 * Central repository for all DSF API-related string constants.
 * Prevents magic strings and ensures consistency across the codebase.
 */
public final class DsfApiConstants {

    private DsfApiConstants() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    // Plugin Definition Interfaces
    public static final String V1_PLUGIN_INTERFACE = "dev.dsf.bpe.v1.ProcessPluginDefinition";
    public static final String V2_PLUGIN_INTERFACE = "dev.dsf.bpe.v2.ProcessPluginDefinition";

    // V1 API - Task Interfaces
    public static final String V1_JAVA_DELEGATE = "org.camunda.bpm.engine.delegate.JavaDelegate";
    public static final String V1_TASK_LISTENER = "org.camunda.bpm.engine.delegate.TaskListener";
    public static final String V1_DEFAULT_USER_TASK_LISTENER = "dev.dsf.bpe.v1.activity.DefaultUserTaskListener";

    // V2 API - Activity Interfaces
    public static final String V2_SERVICE_TASK = "dev.dsf.bpe.v2.activity.ServiceTask";
    public static final String V2_MESSAGE_SEND_TASK = "dev.dsf.bpe.v2.activity.MessageSendTask";
    public static final String V2_MESSAGE_INTERMEDIATE_THROW = "dev.dsf.bpe.v2.activity.MessageIntermediateThrowEvent";
    public static final String V2_MESSAGE_END_EVENT = "dev.dsf.bpe.v2.activity.MessageEndEvent";
    public static final String V2_USER_TASK_LISTENER = "dev.dsf.bpe.v2.activity.UserTaskListener";
    public static final String V2_DEFAULT_USER_TASK_LISTENER = "dev.dsf.bpe.v2.activity.DefaultUserTaskListener";

    // Combined Arrays for Validation
    public static final String[] DSF_TASK_INTERFACES = {
            V1_JAVA_DELEGATE,
            V2_SERVICE_TASK,
            V2_MESSAGE_SEND_TASK,
            V2_MESSAGE_INTERMEDIATE_THROW,
            V2_MESSAGE_END_EVENT,
            V2_USER_TASK_LISTENER
    };

    public static final String[] V1_INTERFACES = {
            V1_JAVA_DELEGATE
    };

    public static final String[] V2_INTERFACES = {
            V2_SERVICE_TASK,
            V2_MESSAGE_SEND_TASK,
            V2_MESSAGE_INTERMEDIATE_THROW,
            V2_MESSAGE_END_EVENT,
            V2_USER_TASK_LISTENER
    };

    // Service File Names
    public static final String V1_SERVICE_FILE = V1_PLUGIN_INTERFACE;
    public static final String V2_SERVICE_FILE = V2_PLUGIN_INTERFACE;
}