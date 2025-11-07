package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.FloatingElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.resource.FhirResourceLocator;
import org.camunda.bpm.model.bpmn.instance.*;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static dev.dsf.linter.bpmn.BpmnElementLinter.*;
import static dev.dsf.linter.bpmn.BpmnModelUtils.extractImplementationClass;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * The {@code BpmnEventLinter} class provides linter logic for various BPMN events,
 * including StartEvent, EndEvent, IntermediateThrowEvent, IntermediateCatchEvent, and BoundaryEvent.
 * <p>
 * This class verifies that BPMN events meet the required configuration criteria such as:
 * <ul>
 *   <li>Non-empty event names</li>
 *   <li>Correct usage of message references (e.g., MessageEventDefinition) and signal references</li>
 *   <li>Proper field injections (e.g., for profile, messageName, and instantiatesCanonical)</li>
 *   <li>linting of implementation classes and execution listener classes</li>
 *   <li>Cross-checking references against FHIR resources using {@link FhirResourceLocator}</li>
 * </ul>
 * </p>
 * <p>
 * The linters are tailored according to the BPMN event type. For example, message events are further
 * linted to ensure that their associated messages have valid references in the FHIR domain.
 * </p>
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
public class BpmnEventLinter {
    private final File projectRoot;

    /**
     * Constructs a new {@code BpmnEventLinter} with the specified project root.
     *
     * @param projectRoot the root directory of the project containing FHIR and BPMN resources
     */
    public BpmnEventLinter(File projectRoot) {
        this.projectRoot = projectRoot;
    }

    // START EVENT LINTER

    /**
     * lints a {@link StartEvent}.
     * <p>
     * If the event contains a {@link MessageEventDefinition}, the method delegates to
     * {@link #lintMessageStartEvent(StartEvent, List, File, String)}; otherwise, it performs
     * generic start event linting via {@link #lintGenericStartEvent(StartEvent, List, File, String)}.
     * </p>
     *
     * @param startEvent the {@link StartEvent} to be linted
     * @param issues     a list to which any linting issues will be added
     * @param bpmnFile   the BPMN file under linting
     * @param processId  the identifier of the BPMN process containing the event
     */
    public void lintStartEvent(
            StartEvent startEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        if (!startEvent.getEventDefinitions().isEmpty()
                && startEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {
            lintMessageStartEvent(startEvent, issues, bpmnFile, processId);
        } else {
            lintGenericStartEvent(startEvent, issues, bpmnFile, processId);
        }
    }

    /**
     * lints a {@link StartEvent} that contains a {@link MessageEventDefinition}.
     * <p>
     * This linter now reports both negative (error/warn) findings <em>and</em>
     * positive (success) outcomes for each relevant check:
     * <ul>
     *   <li><strong>Event name</strong>: emits a warning if empty, otherwise logs a success.</li>
     *   <li><strong>Message name</strong>: emits a warning if empty, otherwise logs a success.</li>
     *   <li><strong>FHIR references</strong>:
     *     <ul>
     *       <li>Checks for an {@code ActivityDefinition} matching the message name: warns if not found, logs a success if found.</li>
     *       <li>Checks for a {@code StructureDefinition} matching the message name: errors if not found, logs a success if found.</li>
     *     </ul>
     *   </li>
     * </ul>
     * </p>
     *
     * @param startEvent the {@link StartEvent} to be linted
     * @param issues     a list to which any linting issues (errors, warnings, successes) will be added
     * @param bpmnFile   the BPMN file under linting
     * @param processId  the identifier of the BPMN process containing the event
     */
    private void lintMessageStartEvent(
            StartEvent startEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = startEvent.getId();
        var locator = FhirResourceLocator.create(projectRoot);

        // 1) Check that the start event has a non-empty name
        if (isEmpty(startEvent.getName())) {
            issues.add(new BpmnEventNameEmptyLintItem(
                    elementId,
                    bpmnFile,
                    processId,
                    "'" + elementId + "' has no name."
            ));
        } else {
            // success: the event name is non-empty
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Start event has a non-empty name: '" + startEvent.getName() + "'"
            ));
        }

        // 2) Retrieve the message event definition and verify the message name
        MessageEventDefinition messageDef =
                (MessageEventDefinition) startEvent.getEventDefinitions().iterator().next();

        if (messageDef.getMessage() == null || isEmpty(messageDef.getMessage().getName())) {
            // negative scenario: message is missing or has an empty name
            issues.add(new BpmnMessageStartEventMessageNameEmptyLintItem(
                    elementId,
                    bpmnFile,
                    processId
            ));
        } else {
            // success: the message name is not empty
            String msgName = messageDef.getMessage().getName();
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Message name is not empty: '" + msgName + "'"
            ));

            // 3) Check references in FHIR resources
            //    (ActivityDefinition and StructureDefinition existence)

            boolean activityDefFound = locator.activityDefinitionExists(msgName, projectRoot);
            if (!activityDefFound) {
                // negative scenario
                issues.add(new BpmnNoActivityDefinitionFoundForMessageLintItem(
                        LinterSeverity.ERROR,
                        elementId,
                        bpmnFile,
                        processId,
                        msgName,
                        "No ActivityDefinition found for messageName: " + msgName
                ));
            } else {
                // success: the message name is supported by some ActivityDefinition
                issues.add(new BpmnElementLintItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "ActivityDefinition found for messageName: '" + msgName + "'"
                ));
            }

            if (activityDefFound) {
                // only check StructureDefinition if we already found an ActivityDefinition
                // (adjust logic as needed)
                boolean structureDefFound = locator.structureDefinitionExists(msgName, projectRoot);
                if (!structureDefFound) {
                    // negative scenario
                    issues.add(new BpmnNoStructureDefinitionFoundForMessageLintItem(
                            LinterSeverity.ERROR,
                            elementId,
                            bpmnFile,
                            processId,
                            msgName,
                            "No StructureDefinition found for messageName: " + msgName
                    ));
                } else {
                    // success: the message name is supported by a matching StructureDefinition
                    issues.add(new BpmnElementLintItemSuccess(
                            elementId,
                            bpmnFile,
                            processId,
                            "StructureDefinition found for messageName: '" + msgName + "'"
                    ));
                }
            }
        }
    }


    /**
     * Performs generic linter on a {@link StartEvent} that does not contain a {@link MessageEventDefinition}.
     * <p>
     * If the start event is not part of a {@link SubProcess}:
     * <ul>
     *   <li>If the event name is empty, a warning is issued.</li>
     *   <li>If the event name is non-empty, a success item is recorded.</li>
     * </ul>
     * </p>
     *
     * @param startEvent the {@link StartEvent} to be linted
     * @param issues     a list to which any linting issues (warnings or success items) will be added
     * @param bpmnFile   the BPMN file under linting
     * @param processId  the identifier of the BPMN process containing the event
     */
    private void lintGenericStartEvent(
            StartEvent startEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = startEvent.getId();
        // Only process start events that are not part of a SubProcess
        if (!(startEvent.getParentElement() instanceof SubProcess)) {
            if (isEmpty(startEvent.getName())) {
                // Negative scenario: name is empty
                issues.add(new BpmnStartEventNotPartOfSubProcessLintItem(
                        elementId, bpmnFile, processId));
            } else {
                // Success: start event has a non-empty name
                issues.add(new BpmnElementLintItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "Generic start event is a part of subprocess, and has a non-empty name: '" + startEvent.getName() + "'"
                ));
            }
        }
    }


    // INTERMEDIATE THROW EVENT LINTER

    /**
     * lints an {@link IntermediateThrowEvent}.
     * <p>
     * This method delegates the linting based on the type of event definition:
     * <ul>
     *   <li>If the event definition is a {@link MessageEventDefinition}, it calls
     *       {@link #lintMessageIntermediateThrowEvent(IntermediateThrowEvent, List, File, String)}.</li>
     *   <li>If the event definition is a {@link SignalEventDefinition}, it calls
     *       {@link #lintSignalIntermediateThrowEvent(IntermediateThrowEvent, List, File, String)}.</li>
     * </ul>
     * </p>
     *
     * @param throwEvent the {@link IntermediateThrowEvent} to be linted
     * @param issues     a list to which any linting issues will be added
     * @param bpmnFile   the BPMN file under linting
     * @param processId  the identifier of the BPMN process containing the event
     */
    public void lintIntermediateThrowEvent(
            IntermediateThrowEvent throwEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        if (!throwEvent.getEventDefinitions().isEmpty()
                && throwEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {
            lintMessageIntermediateThrowEvent(throwEvent, issues, bpmnFile, processId);
        } else if (!throwEvent.getEventDefinitions().isEmpty()
                && throwEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {
            lintSignalIntermediateThrowEvent(throwEvent, issues, bpmnFile, processId);
        }
    }

    /**
     * lints an {@link IntermediateThrowEvent} that contains a {@link MessageEventDefinition}.
     * <p>
     * The linter performs common message event lints and checks the event definition’s message reference:
     * <ul>
     *   <li>If the message reference is not null, a warning is issued.</li>
     *   <li>If the message reference is null, a success item is recorded indicating that no message was provided.</li>
     * </ul>
     * </p>
     *
     * @param throwEvent the {@link IntermediateThrowEvent} to be linted
     * @param issues     a list to which any lint issues (warnings or info items) will be added
     * @param bpmnFile   the BPMN file under linting
     * @param processId  the identifier of the BPMN process containing the event
     */
    private void lintMessageIntermediateThrowEvent(
            IntermediateThrowEvent throwEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        // Perform common message event lints
        lintCommonMessageEvent(throwEvent, issues, bpmnFile, processId);

        MessageEventDefinition msgDef =
                (MessageEventDefinition) throwEvent.getEventDefinitions().iterator().next();

        if (msgDef.getMessage() != null) {
            String messageName = msgDef.getMessage().getName();
            // Negative scenario: message reference is provided, which is not expected.
            issues.add(new BpmnMessageIntermediateThrowEventHasMessageLintItem(
                    throwEvent.getId(), bpmnFile, processId,
                    "Message Intermediate Throw Event has a message with name: " + messageName));
        } else {
            // Info scenario: no message reference is provided, as wished.
            issues.add(new BpmnMessageIntermediateThrowEventHasMessageLintItem(
                    throwEvent.getId(),
                    bpmnFile,
                    processId
            ));
        }
    }


    /**
     * lints an {@link IntermediateThrowEvent} that contains a {@link SignalEventDefinition}.
     * <p>
     * The linter ensures that:
     * <ul>
     *   <li>The event name is not empty – if it is, a warning is issued; if not, a success item is recorded.</li>
     *   <li>The associated signal is present and has a non-empty name – if not, an error is recorded; if it is, a success item is recorded.</li>
     * </ul>
     * </p>
     *
     * @param throwEvent the {@link IntermediateThrowEvent} to be linted
     * @param issues     a list to which any lint issues (errors, warnings, successes) will be added
     * @param bpmnFile   the BPMN file under linting
     * @param processId  the identifier of the BPMN process containing the event
     */
    private void lintSignalIntermediateThrowEvent(
            IntermediateThrowEvent throwEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = throwEvent.getId();

        // 1) lint the event name
        if (isEmpty(throwEvent.getName())) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Throw Event name is empty",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.WARN,
                    FloatingElementType.SIGNAL_INTERMEDIATE_THROW_EVENT_NAME_IS_EMPTY
            ));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Signal Intermediate Throw Event name is not empty: '" + throwEvent.getName() + "'"
            ));
        }

        // 2) lint the associated signal
        SignalEventDefinition def =
                (SignalEventDefinition) throwEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || isEmpty(def.getSignal().getName())) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Signal is empty in Signal Intermediate Throw Event",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.ERROR,
                    FloatingElementType.SIGNAL_IS_EMPTY_IN_SIGNAL_INTERMEDIATE_THROW_EVENT
            ));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Signal is present in Signal Intermediate Throw Event with name: '" + def.getSignal().getName() + "'"
            ));
        }
    }


    // END EVENT LINTER

    /**
     * lints an {@link EndEvent}.
     * <p>
     * This method delegates linting based on the type of event definition:
     * <ul>
     *   <li>If the event contains a {@link MessageEventDefinition}, it calls
     *       {@link #lintMessageEndEvent(EndEvent, List, File, String)}.</li>
     *   <li>If the event contains a {@link SignalEventDefinition}, it calls
     *       {@link #lintSignalEndEvent(EndEvent, List, File, String)}.</li>
     *   <li>If no specific event definition is present, it calls
     *       {@link #lintGenericEndEvent(EndEvent, List, File, String)}.</li>
     * </ul>
     * </p>
     *
     * @param endEvent  the {@link EndEvent} to be linted
     * @param issues    a list to which any lint issues will be added
     * @param bpmnFile  the BPMN file under linting
     * @param processId the identifier of the BPMN process containing the event
     */
    public void lintEndEvent(
            EndEvent endEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        if (!endEvent.getEventDefinitions().isEmpty()
                && endEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {
            lintMessageEndEvent(endEvent, issues, bpmnFile, processId);
        } else if (!endEvent.getEventDefinitions().isEmpty()
                && endEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {
            lintSignalEndEvent(endEvent, issues, bpmnFile, processId);
        } else {
            lintGenericEndEvent(endEvent, issues, bpmnFile, processId);
        }
    }

    /**
     * lints an {@link EndEvent} that contains a {@link MessageEventDefinition}.
     * <p>
     * This method performs common message event linter for the end event by delegating
     * to {@link #lintCommonMessageEvent(FlowElement, List, File, String)}.
     * </p>
     *
     * @param endEvent  the {@link EndEvent} to be linted
     * @param issues    a list to which any lint issues will be added
     * @param bpmnFile  the BPMN file under linting
     * @param processId the identifier of the BPMN process containing the event
     */
    private void lintMessageEndEvent(
            EndEvent endEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        lintCommonMessageEvent(endEvent, issues, bpmnFile, processId);
    }

    /**
     * lints an {@link EndEvent} that does not contain a {@link MessageEventDefinition}.
     * <p>
     * For such events, the linter checks include:
     * <ul>
     *   <li>If the event is not part of a {@link SubProcess}, its name must not be empty.
     *       - If non-empty, a success item is recorded.</li>
     *   <li>If the event is part of a {@link SubProcess}, the property {@code camunda:asyncAfter} must be set to {@code true}.
     *       - If true, a success item is recorded; otherwise, a warning is issued.</li>
     *   <li>Verifying that execution listener classes referenced in the event's extension elements exist on the classpath.</li>
     * </ul>
     * </p>
     *
     * @param endEvent  the {@link EndEvent} to be linted
     * @param issues    a list to which any lint issues (errors, warnings, or success items) will be added
     * @param bpmnFile  the BPMN file under linting
     * @param processId the identifier of the BPMN process containing the event
     */
    private void lintGenericEndEvent(
            EndEvent endEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = endEvent.getId();

        // 1) If the End Event is not part of a SubProcess, its name must not be empty.
        if (!(endEvent.getParentElement() instanceof SubProcess)) {
            if (isEmpty(endEvent.getName())) {
                issues.add(new BpmnEndEventNotPartOfSubProcessLintItem(
                        elementId, bpmnFile, processId));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "End event (not part of a SubProcess) has a non-empty name: '" + endEvent.getName() + "'"
                ));
            }
        }

        // 2) If the End Event is part of a SubProcess, camunda:asyncAfter must be true.
        if (endEvent.getParentElement() instanceof SubProcess) {
            if (!endEvent.isCamundaAsyncAfter()) {
                issues.add(new BpmnFloatingElementLintItem(
                        elementId, bpmnFile, processId,
                        "End Event inside a SubProcess should have asyncAfter=true",
                        LintingType.BPMN_FLOATING_ELEMENT,
                        LinterSeverity.WARN,
                        FloatingElementType.END_EVENT_INSIDE_A_SUB_PROCESS_SHOULD_HAVE_ASYNC_AFTER_TRUE
                ));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "End Event inside a SubProcess has asyncAfter=true"
                ));
            }
        }

        // 3) Check that execution listener classes referenced in the event's extension elements exist on the classpath.
        checkExecutionListenerClasses(endEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }


    /**
     * lints an {@link EndEvent} that contains a {@link SignalEventDefinition}.
     * <p>
     * The linter ensures that:
     * <ul>
     *   <li>The event name is not empty – if it is empty, a warning is issued; otherwise, a success item is recorded.</li>
     *   <li>The associated signal is present and its name is not empty – if not, an error is issued; otherwise, a success item is recorded.</li>
     * </ul>
     * </p>
     *
     * @param endEvent  the {@link EndEvent} to be linted
     * @param issues    a list to which any lint issues (errors, warnings, or success items) will be added
     * @param bpmnFile  the BPMN file under linting
     * @param processId the identifier of the BPMN process containing the event
     */
    private void lintSignalEndEvent(
            EndEvent endEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = endEvent.getId();

        // lint that the event name is not empty.
        if (isEmpty(endEvent.getName())) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Signal End Event name is empty",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.WARN,
                    FloatingElementType.SIGNAL_END_EVENT_NAME_IS_EMPTY
            ));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Signal End Event name is not empty: '" + endEvent.getName() + "'"
            ));
        }

        // lint that the associated signal is present and its name is not empty.
        SignalEventDefinition def = (SignalEventDefinition) endEvent.getEventDefinitions().iterator().next();
        if (def.getSignal() == null || isEmpty(def.getSignal().getName())) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Signal is empty in Signal End Event",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.ERROR,
                    FloatingElementType.SIGNAL_IS_EMPTY_IN_SIGNAL_END_EVENT
            ));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Signal is present in Signal End Event with name: '" + def.getSignal().getName() + "'"
            ));
        }
    }

    // INTERMEDIATE CATCH EVENT LINTER

    /**
     * lints an {@link IntermediateCatchEvent}.
     * <p>
     * Based on the type of event definition contained in the catch event, this method delegates linting to:
     * <ul>
     *   <li>{@link #lintMessageIntermediateCatchEvent(IntermediateCatchEvent, List, File, String)} for a {@link MessageEventDefinition}</li>
     *   <li>{@link #lintTimerIntermediateCatchEvent(IntermediateCatchEvent, List, File, String)} for a {@link TimerEventDefinition}</li>
     *   <li>{@link #lintSignalIntermediateCatchEvent(IntermediateCatchEvent, List, File, String)} for a {@link SignalEventDefinition}</li>
     *   <li>{@link #lintConditionalIntermediateCatchEvent(IntermediateCatchEvent, List, File, String)} for a {@link ConditionalEventDefinition}</li>
     * </ul>
     * </p>
     *
     * @param catchEvent the {@link IntermediateCatchEvent} to be linted
     * @param issues     a list to which any lint issues will be added
     * @param bpmnFile   the BPMN file under linting
     * @param processId  the identifier of the BPMN process containing the event
     */
    public void lintIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {
            lintMessageIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        } else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof TimerEventDefinition) {
            lintTimerIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        } else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {
            lintSignalIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        } else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof ConditionalEventDefinition) {
            lintConditionalIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        }
        // ... additional event definitions if needed
    }


    private void lintMessageIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = catchEvent.getId();

        // Check that the catch event has a non-empty name.
        if (isEmpty(catchEvent.getName())) {
            issues.add(new BpmnMessageIntermediateCatchEventNameEmptyLintItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Intermediate Catch Event has a non-empty name: '" + catchEvent.getName() + "'"
            ));
        }

        // Retrieve the message event definition.
        MessageEventDefinition def = (MessageEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        // Check that the message is present and its name is not empty.
        if (def.getMessage() == null || isEmpty(def.getMessage().getName())) {
            issues.add(new BpmnMessageStartEventMessageNameEmptyLintItem(elementId, bpmnFile, processId));
        } else {
            // Record a success for non-empty message name.
            String msgName = def.getMessage().getName();
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Message name is not empty: '" + msgName + "'"
            ));

            // lint the message name against FHIR resources.
            checkMessageName(msgName, issues, elementId, bpmnFile, processId, projectRoot);
        }
    }



    private void lintTimerIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = catchEvent.getId();

        // Check that the event name is not empty.
        if (isEmpty(catchEvent.getName())) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Timer Intermediate Catch Event name is empty",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.WARN,
                    FloatingElementType.TIMER_INTERMEDIATE_CATCH_EVENT_NAME_IS_EMPTY
            ));
        } else {
            // Success case: non-empty event name.
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Timer Intermediate Catch Event has a non-empty name: '" + catchEvent.getName() + "'"
            ));
        }

        // lint the timer definition.
        TimerEventDefinition timerDef = (TimerEventDefinition) catchEvent.getEventDefinitions().iterator().next();
        checkTimerDefinition(elementId, issues, bpmnFile, processId, timerDef);
    }


    /**
     * lints an {@link IntermediateCatchEvent} that contains a {@link SignalEventDefinition}.
     * <p>
     * The linter ensures:
     * <ul>
     *   <li>The event name is not empty – if empty, a warning is issued; if non-empty, a success item is recorded.</li>
     *   <li>The associated signal is present and its name is not empty – if missing or empty, an error is issued; if present, a success item is recorded.</li>
     * </ul>
     * </p>
     *
     * @param catchEvent the {@link IntermediateCatchEvent} to be linted
     * @param issues     a list to which any lint issues (warnings, errors, or success items) will be added
     * @param bpmnFile   the BPMN file under linting
     * @param processId  the identifier of the BPMN process containing the event
     */
    private void lintSignalIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = catchEvent.getId();

        // lint that the event name is not empty.
        if (isEmpty(catchEvent.getName())) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Catch Event name is empty",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.WARN,
                    FloatingElementType.SIGNAL_INTERMEDIATE_CATCH_EVENT_NAME_IS_EMPTY
            ));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Signal Intermediate Catch Event name is not empty: '" + catchEvent.getName() + "'"
            ));
        }

        // lint that the associated signal is present and its name is not empty.
        SignalEventDefinition def = (SignalEventDefinition) catchEvent.getEventDefinitions().iterator().next();
        if (def.getSignal() == null || isEmpty(def.getSignal().getName())) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Signal is empty in Signal Intermediate Catch Event",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.ERROR,
                    FloatingElementType.SIGNAL_IS_EMPTY_IN_SIGNAL_INTERMEDIATE_CATCH_EVENT
            ));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Signal is present in Signal Intermediate Catch Event with name: '" + def.getSignal().getName() + "'"
            ));
        }
    }


    /**
     * lints an {@link IntermediateCatchEvent} that contains a {@link ConditionalEventDefinition}.
     * <p>
     * The linter for a conditional catch event involves checking the event's variable name,
     * variable events, condition type, and the presence of a condition expression.
     * </p>
     *
     * @param catchEvent the {@link IntermediateCatchEvent} to be linted
     * @param issues     a list to which any lint issues will be added
     * @param bpmnFile   the BPMN file under linting
     * @param processId  the identifier of the BPMN process containing the event
     */
    private void lintConditionalIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        checkConditionalEvent(catchEvent, issues, bpmnFile, processId);
    }

    // BOUNDARY EVENT LINTER

    /**
     * lints a {@link BoundaryEvent}.
     * <p>
     * Based on the type of event definition contained in the boundary event, this method delegates
     * linting to either:
     * <ul>
     *   <li>{@link #lintMessageBoundaryEvent(BoundaryEvent, List, File, String)} if a {@link MessageEventDefinition} is present</li>
     *   <li>{@link #lintErrorBoundaryEvent(BoundaryEvent, List, File, String)} if an {@link ErrorEventDefinition} is present</li>
     * </ul>
     * </p>
     *
     * @param boundaryEvent the {@link BoundaryEvent} to be linted
     * @param issues        a list to which any lint issues will be added
     * @param bpmnFile      the BPMN file under linting
     * @param processId     the identifier of the BPMN process containing the event
     */
    public void lintBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        if (!boundaryEvent.getEventDefinitions().isEmpty()) {
            if (boundaryEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {
                lintMessageBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
            } else if (boundaryEvent.getEventDefinitions().iterator().next() instanceof ErrorEventDefinition) {
                lintErrorBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
            }
        }
    }

    /**
     * lints a {@link BoundaryEvent} that contains a {@link MessageEventDefinition}.
     * <p>
     * The linter includes:
     * <ul>
     *   <li>Checking that the event name is not empty – if empty, a warning is issued; otherwise, a success item is recorded.</li>
     *   <li>Ensuring that the associated message's name is not empty – if empty, a warning is issued; otherwise, a success item is recorded.</li>
     *   <li>Verifying that the message name is recognized in FHIR resources:
     *       <ul>
     *         <li>If no matching ActivityDefinition is found, a warning is issued; if found, a success item is recorded.</li>
     *         <li>If an ActivityDefinition is found and no matching StructureDefinition is found, an error is issued;
     *             if found, a success item is recorded.</li>
     *       </ul>
     *   </li>
     * </ul>
     * </p>
     *
     * @param boundaryEvent the {@link BoundaryEvent} to be linted
     * @param issues        a list to which any lint issues (warnings, errors, or success items) will be added
     * @param bpmnFile      the BPMN file under linting
     * @param processId     the identifier of the BPMN process containing the event
     */
    private void lintMessageBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = boundaryEvent.getId();
        var locator = FhirResourceLocator.create(projectRoot);

        // 1) lint the boundary event name.
        if (isEmpty(boundaryEvent.getName())) {
            issues.add(new BpmnMessageBoundaryEventNameEmptyLintItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Boundary event has a non-empty name: '" + boundaryEvent.getName() + "'"
            ));
        }

        // 2) lint that the associated message exists and its name is not empty.
        MessageEventDefinition def =
                (MessageEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();

        if (def.getMessage() == null || isEmpty(def.getMessage().getName())) {
            issues.add(new BpmnMessageStartEventMessageNameEmptyLintItem(
                    elementId, bpmnFile, processId));
        } else {
            String msgName = def.getMessage().getName();
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Message name is not empty: '" + msgName + "'"
            ));

            // 3) lint the FHIR ActivityDefinition.
            boolean activityFound = false;
            if (!locator.activityDefinitionExists(msgName, projectRoot)) {
                issues.add(new BpmnNoActivityDefinitionFoundForMessageLintItem(
                        LinterSeverity.ERROR,
                        elementId,
                        bpmnFile,
                        processId,
                        msgName,
                        "No ActivityDefinition found for messageName: " + msgName
                ));
            } else {
                activityFound = true;
                issues.add(new BpmnElementLintItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "ActivityDefinition found for messageName: '" + msgName + "'"
                ));
            }

            // 4) lint the FHIR StructureDefinition (only if ActivityDefinition was found).
            if (activityFound) {
                if (!locator.structureDefinitionExists(msgName, projectRoot)) {
                    issues.add(new BpmnNoStructureDefinitionFoundForMessageLintItem(
                            LinterSeverity.ERROR,
                            elementId,
                            bpmnFile,
                            processId,
                            msgName,
                            "No StructureDefinition found for messageName: " + msgName
                    ));
                } else {
                    issues.add(new BpmnElementLintItemSuccess(
                            elementId,
                            bpmnFile,
                            processId,
                            "StructureDefinition found for messageName: '" + msgName + "'"
                    ));
                }
            }
        }
    }



    private void lintErrorBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        checkErrorBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
    }

    // COMMON MESSAGE EVENT LINTER (shared)

    /**
     * Performs common linter for a BPMN event that uses a message definition,
     * such as an {@link IntermediateThrowEvent} or {@link EndEvent} with a {@link MessageEventDefinition}.
     * <p>
     * The common linter include:
     * <ul>
     *   <li>Ensuring the event name is not empty – if the name is empty, a warning is recorded;
     *       if it is non-empty, a success item is recorded.</li>
     *   <li>Extracting and linting the implementation class (e.g., checking that it is not empty, exists,
     *       and implements the required interface such as {@code JavaDelegate}).</li>
     *   <li>linting field injections for attributes such as <code>profile</code>, <code>messageName</code>,
     *       and <code>instantiatesCanonical</code> using the {@link BpmnFieldInjectionLinter}.</li>
     * </ul>
     * </p>
     *
     * @param event     the BPMN {@link FlowElement} (such as an IntermediateThrowEvent or EndEvent) to be linted
     * @param issues    a list to which any lint issues (warnings or success items) will be added
     * @param bpmnFile  the BPMN file under linting
     * @param processId the identifier of the BPMN process containing the event
     */
    private void lintCommonMessageEvent(
            FlowElement event,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = event.getId();

        // Check the event name.
        if (isEmpty(event.getName())) {
            issues.add(new BpmnEventNameEmptyLintItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        } else {
            // Success case: record that the event name is non-empty.
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Event name is not empty: '" + event.getName() + "'"
            ));
        }

        // Extract and lint the implementation class.
        Optional<String> implementationClassOptional = extractImplementationClass(event);

        // lint if the implementation class is present.
        if (implementationClassOptional.isEmpty()) {
            issues.add(new BpmnMessageSendEventImplementationClassEmptyLintItem(elementId, bpmnFile, processId));
        } else {
            // If present, lint the class itself.
            String implClass = implementationClassOptional.get();
            lintImplementationClass(implClass, elementId, bpmnFile, processId, issues, projectRoot);
        }

        // lint field injections.
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(event, issues, bpmnFile, processId, projectRoot);
    }
}