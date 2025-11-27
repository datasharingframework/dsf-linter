package dev.dsf.linter.output;

/**
 * Floating Element Types according to BPMN 2.0 specification.
 * <p>
 * Floating Elements are Intermediate Catch Events without incoming sequence flows.
 * They can be: Message, Timer, Conditional, Signal, or Link Intermediate Catch Events.
 * </p>
 */
public enum FloatingElementType
{
    // Timer Intermediate Catch Event
    TIMER_INTERMEDIATE_CATCH_EVENT_NAME_IS_EMPTY,
    TIMER_TYPE_IS_EMPTY,
    TIMER_TYPE_IS_A_FIXED_DATE_TIME,
    TIMER_VALUE_APPEARS_FIXED_NO_PLACEHOLDER_FOUND,
    
    // Signal Intermediate Catch Event
    SIGNAL_INTERMEDIATE_CATCH_EVENT_NAME_IS_EMPTY,
    SIGNAL_IS_EMPTY_IN_SIGNAL_INTERMEDIATE_CATCH_EVENT,
    
    // Conditional Intermediate Catch Event
    CONDITIONAL_INTERMEDIATE_CATCH_EVENT_NAME_IS_EMPTY,
    CONDITIONAL_INTERMEDIATE_CATCH_EVENT_VARIABLE_NAME_IS_EMPTY,
    CONDITIONAL_INTERMEDIATE_CATCH_EVENT_VARIABLE_EVENTS_IS_EMPTY,
    CONDITIONAL_INTERMEDIATE_CATCH_EVENT_CONDITION_TYPE_IS_EMPTY,
    CONDITIONAL_INTERMEDIATE_CATCH_EVENT_CONDITION_TYPE_IS_NOT_EXPRESSION,
    CONDITIONAL_INTERMEDIATE_CATCH_EVENT_EXPRESSION_IS_EMPTY,
}
