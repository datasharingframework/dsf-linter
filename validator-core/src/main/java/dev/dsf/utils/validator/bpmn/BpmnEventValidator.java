package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.ValidationType;
import dev.dsf.utils.validator.fhir.FhirValidator;
import dev.dsf.utils.validator.item.*;
import org.camunda.bpm.model.bpmn.instance.*;

import java.io.File;
import java.util.List;

/**
 * <p>
 * The {@code BpmnEventValidator} class handles validation logic for BPMN events:
 * StartEvent, EndEvent, IntermediateThrowEvent, IntermediateCatchEvent, and BoundaryEvent.
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
public class BpmnEventValidator
{
    private final File projectRoot;

    public BpmnEventValidator(File projectRoot)
    {
        this.projectRoot = projectRoot;
    }

    // ----------------------------------------------------
    // START EVENT VALIDATION
    // ----------------------------------------------------

    /**
     * Validates a {@link StartEvent}. If it contains a {@link MessageEventDefinition}, the message is validated,
     * otherwise it checks name presence.
     */
    public void validateStartEvent(
            StartEvent startEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        if (!startEvent.getEventDefinitions().isEmpty()
                && startEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition)
        {
            validateMessageStartEvent(startEvent, issues, bpmnFile, processId);
        }
        else
        {
            validateGenericStartEvent(startEvent, issues, bpmnFile, processId);
        }
    }

    /**
     * Validates a {@link StartEvent} that specifically contains a {@link MessageEventDefinition}.
     * Checks that:
     * <ul>
     *   <li>Event name is not empty</li>
     *   <li>{@code Message.name} is not empty</li>
     *   <li>{@code Message.name} corresponds to valid references in FHIR resources</li>
     * </ul>
     */
    private void validateMessageStartEvent(
            StartEvent startEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = startEvent.getId();
        if (BpmnValidationUtils.isEmpty(startEvent.getName()))
        {
            issues.add(new BpmnEventNameEmptyValidationItem(elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }

        MessageEventDefinition messageDef =
                (MessageEventDefinition) startEvent.getEventDefinitions().iterator().next();

        if (messageDef.getMessage() == null || BpmnValidationUtils.isEmpty(messageDef.getMessage().getName()))
        {
            issues.add(new BpmnMessageStartEventMessageNameEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            // Check references in FHIR resources
            String msgName = messageDef.getMessage().getName();
            if (!FhirValidator.activityDefinitionExists(msgName, projectRoot))
            {
                issues.add(new BpmnMessageStartEventMessageNameNotPresentInActivityDefinitionValidationItem(
                        elementId, bpmnFile, processId, msgName));
            }
            if (!FhirValidator.structureDefinitionExists(msgName, projectRoot))
            {
                issues.add(new BpmnMessageStartEventMessageNameNotMatchingProfileValidationItem(
                        elementId, bpmnFile, processId, msgName));
            }
        }
    }

    /**
     * Validates a {@link StartEvent} that does NOT have a {@link MessageEventDefinition}.
     * Simply warns if the name is empty (and if it's not part of a {@link SubProcess}).
     */
    private void validateGenericStartEvent(
            StartEvent startEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = startEvent.getId();
        if (BpmnValidationUtils.isEmpty(startEvent.getName()))
        {
            // Warn if the Start Event is not part of a SubProcess
            if (!(startEvent.getParentElement() instanceof SubProcess))
            {
                issues.add(new BpmnStartEventNotPartOfSubProcessValidationItem(elementId, bpmnFile, processId));
            }
        }
    }

    // ----------------------------------------------------
    // INTERMEDIATE THROW EVENT VALIDATION
    // ----------------------------------------------------

    public void validateIntermediateThrowEvent(
            IntermediateThrowEvent throwEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        if (!throwEvent.getEventDefinitions().isEmpty()
                && throwEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition)
        {
            validateMessageIntermediateThrowEvent(throwEvent, issues, bpmnFile, processId);
        }
        else if (!throwEvent.getEventDefinitions().isEmpty()
                && throwEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition)
        {
            validateSignalIntermediateThrowEvent(throwEvent, issues, bpmnFile, processId);
        }
        // ... additional checks for other event definitions if needed
    }

    /**
     * Validates an {@link IntermediateThrowEvent} that has a {@link MessageEventDefinition}.
     * Includes checks for:
     * <ul>
     *   <li>Non-empty event name</li>
     *   <li>Implementation class validity</li>
     *   <li>Field injections</li>
     *   <li>A warning if the {@code <bpmn:message>} reference is not null</li>
     * </ul>
     */
    private void validateMessageIntermediateThrowEvent(
            IntermediateThrowEvent throwEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        validateCommonMessageEvent(throwEvent, issues, bpmnFile, processId);

        MessageEventDefinition msgDef =
                (MessageEventDefinition) throwEvent.getEventDefinitions().iterator().next();
        if (msgDef.getMessage() != null)
        {
            String messageName = msgDef.getMessage().getName();
            issues.add(new BpmnMessageIntermediateThrowEventHasMessageValidationItem(
                    throwEvent.getId(), bpmnFile, processId, "Message Intermediate Throw Event has a message with name: " + messageName));
        }
    }

    /**
     * Validates an {@link IntermediateThrowEvent} containing a {@link SignalEventDefinition}.
     * Checks the non-empty name and signal presence.
     */
    private void validateSignalIntermediateThrowEvent(
            IntermediateThrowEvent throwEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = throwEvent.getId();

        if (BpmnValidationUtils.isEmpty(throwEvent.getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Throw Event name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        SignalEventDefinition def =
                (SignalEventDefinition) throwEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || BpmnValidationUtils.isEmpty(def.getSignal().getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal is empty in Signal Intermediate Throw Event",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
    }

    // ----------------------------------------------------
    // END EVENT VALIDATION
    // ----------------------------------------------------

    public void validateEndEvent(
            EndEvent endEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        if (!endEvent.getEventDefinitions().isEmpty()
                && endEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition)
        {
            validateMessageEndEvent(endEvent, issues, bpmnFile, processId);
        }
        else if (!endEvent.getEventDefinitions().isEmpty()
                && endEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition)
        {
            validateSignalEndEvent(endEvent, issues, bpmnFile, processId);
        }
        else
        {
            validateGenericEndEvent(endEvent, issues, bpmnFile, processId);
        }
    }

    /**
     * Validates a {@link EndEvent} that has a {@link MessageEventDefinition}.
     * Ensures:
     * <ul>
     *   <li>Non-empty event name</li>
     *   <li>Implementation class correctness</li>
     *   <li>Field injections checked for required fields</li>
     * </ul>
     */
    private void validateMessageEndEvent(
            EndEvent endEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        validateCommonMessageEvent(endEvent, issues, bpmnFile, processId);
    }

    /**
     * Validates an {@link EndEvent} (with no MessageEventDefinition).
     * Checks:
     * <ul>
     *   <li>Non-empty name (if not in a SubProcess)</li>
     *   <li>{@code camunda:asyncAfter} must be true if the event is inside a SubProcess</li>
     *   <li>Extension listeners referencing {@code camunda:class} must exist on the classpath</li>
     * </ul>
     */
    private void validateGenericEndEvent(
            EndEvent endEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = endEvent.getId();

        if (BpmnValidationUtils.isEmpty(endEvent.getName()))
        {
            if (!(endEvent.getParentElement() instanceof SubProcess))
            {
                issues.add(new BpmnEndEventNotPartOfSubProcessValidationItem(elementId, bpmnFile, processId));
            }
        }

        if (endEvent.getParentElement() instanceof SubProcess)
        {
            if (!endEvent.isCamundaAsyncAfter())
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "End Event inside a SubProcess should have asyncAfter=true",
                        ValidationType.BPMN_FLOATING_ELEMENT
                ));
            }
        }

        BpmnValidationUtils.checkExecutionListenerClasses(endEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * Validates an {@link EndEvent} containing a {@link SignalEventDefinition}.
     * Ensures the event name and signal name are not empty.
     */
    private void validateSignalEndEvent(
            EndEvent endEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = endEvent.getId();

        if (BpmnValidationUtils.isEmpty(endEvent.getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal End Event name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        SignalEventDefinition def =
                (SignalEventDefinition) endEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || BpmnValidationUtils.isEmpty(def.getSignal().getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal is empty in Signal End Event",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
    }

    // ----------------------------------------------------
    // INTERMEDIATE CATCH EVENT VALIDATION
    // ----------------------------------------------------

    public void validateIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition)
        {
            validateMessageIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        }
        else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof TimerEventDefinition)
        {
            validateTimerIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        }
        else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition)
        {
            validateSignalIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        }
        else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof ConditionalEventDefinition)
        {
            validateConditionalIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        }
        // ... additional event definitions if needed
    }

    /**
     * Validates an {@link IntermediateCatchEvent} containing a {@link MessageEventDefinition}.
     * Checks that the event name and message name are not empty, and verifies that the
     * message name is found in the relevant FHIR resources.
     */
    private void validateMessageIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = catchEvent.getId();

        if (BpmnValidationUtils.isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnMessageIntermediateCatchEventNameEmptyValidationItem (
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }

        MessageEventDefinition def = (MessageEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        if (def.getMessage() == null || BpmnValidationUtils.isEmpty(def.getMessage().getName()))
        {
            issues.add(new BpmnMessageStartEventMessageNameEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            BpmnValidationUtils.checkMessageName(def.getMessage().getName(), issues, elementId, bpmnFile, processId, projectRoot);
        }
    }

    /**
     * Validates an {@link IntermediateCatchEvent} containing a {@link TimerEventDefinition}.
     * Checks:
     * <ul>
     *   <li>Non-empty name</li>
     *   <li>Presence of one of {@code timeDate}, {@code timeCycle}, {@code timeDuration}</li>
     *   <li>If {@code timeDate} is used, logs an info message</li>
     *   <li>If {@code timeCycle} or {@code timeDuration} is used, warns if no version placeholder</li>
     * </ul>
     */
    private void validateTimerIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = catchEvent.getId();

        if (BpmnValidationUtils.isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Timer Intermediate Catch Event name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        TimerEventDefinition timerDef = (TimerEventDefinition) catchEvent.getEventDefinitions().iterator().next();
        BpmnValidationUtils.checkTimerDefinition(elementId, issues, bpmnFile, processId, timerDef);
    }

    /**
     * Validates an {@link IntermediateCatchEvent} containing a {@link SignalEventDefinition}.
     * Checks non-empty name and non-empty signal.
     */
    private void validateSignalIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = catchEvent.getId();

        if (BpmnValidationUtils.isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Catch Event name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        SignalEventDefinition def = (SignalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || BpmnValidationUtils.isEmpty(def.getSignal().getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal is empty in Signal Intermediate Catch Event",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
    }

    /**
     * Validates an {@link IntermediateCatchEvent} containing a {@link ConditionalEventDefinition}.
     * Checks the variable name, variable events, condition type, and expression presence.
     */
    private void validateConditionalIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        BpmnValidationUtils.checkConditionalEvent(catchEvent, issues, bpmnFile, processId);
    }

    // ----------------------------------------------------
    // BOUNDARY EVENT VALIDATION
    // ----------------------------------------------------

    public void validateBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        if (!boundaryEvent.getEventDefinitions().isEmpty())
        {
            if (boundaryEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition)
            {
                validateMessageBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
            }
            else if (boundaryEvent.getEventDefinitions().iterator().next() instanceof ErrorEventDefinition)
            {
                validateErrorBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
            }
        }
    }

    /**
     * Validates a {@link BoundaryEvent} that references a {@link MessageEventDefinition}.
     * Checks for non-empty name, non-empty message name, and if that name is recognized
     * in FHIR resources.
     */
    private void validateMessageBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = boundaryEvent.getId();
        if (BpmnValidationUtils.isEmpty(boundaryEvent.getName()))
        {
            issues.add(new BpmnMessageBoundaryEventNameEmptyValidationItem (
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }
        MessageEventDefinition def =
                (MessageEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();

        if (def.getMessage() == null || BpmnValidationUtils.isEmpty(def.getMessage().getName()))
        {
            issues.add(new BpmnMessageStartEventMessageNameEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            String msgName = def.getMessage().getName();
            if (!FhirValidator.activityDefinitionExists(msgName, projectRoot))
            {
                issues.add(new BpmnMessageStartEventMessageNameNotPresentInActivityDefinitionValidationItem(
                        elementId, bpmnFile, processId, msgName));
            }
            if (!FhirValidator.structureDefinitionExists(msgName, projectRoot))
            {
                issues.add(new BpmnMessageStartEventMessageNameNotMatchingProfileValidationItem(
                        elementId, bpmnFile, processId, msgName));
            }
        }
    }

    /**
     * Validates an {@link BoundaryEvent} containing an {@link ErrorEventDefinition}.
     * Splits logic based on whether {@code errorRef} is set.
     */
    private void validateErrorBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        BpmnValidationUtils.checkErrorBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
    }

    // ----------------------------------------------------
    // COMMON MESSAGE EVENT VALIDATION (shared)
    // ----------------------------------------------------

    /**
     * Performs "common" validations for an event that uses a message definition, such
     * as {@link IntermediateThrowEvent} or {@link EndEvent} with a {@link MessageEventDefinition}.
     * Ensures:
     * <ul>
     *   <li>Event name not empty</li>
     *   <li>Implementation class is valid (non-empty, exists, implements {@code JavaDelegate})</li>
     *   <li>Field injections for {@code profile}, {@code messageName}, {@code instantiatesCanonical}
     *       are present and correct</li>
     * </ul>
     */
    private void validateCommonMessageEvent(
            FlowElement event,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = event.getId();
        if (BpmnValidationUtils.isEmpty(event.getName()))
        {
            issues.add(new BpmnEventNameEmptyValidationItem(elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        }

        // Extract and validate the implementation class
        String implClass = BpmnValidationUtils.extractImplementationClass(event);
        BpmnValidationUtils.validateImplementationClass(implClass, elementId, bpmnFile, processId, issues, projectRoot);

        // Validate field injections
        BpmnFieldInjectionValidator.validateMessageSendFieldInjections(event, issues, bpmnFile, processId, projectRoot);
    }
}
