package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import dev.dsf.utils.validator.fhir.FhirValidator;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.BpmnValidationUtils;
import org.camunda.bpm.model.bpmn.instance.*;

import java.io.File;
import java.util.List;

/**
 * The {@code BpmnEventValidator} class provides validation logic for various BPMN events,
 * including StartEvent, EndEvent, IntermediateThrowEvent, IntermediateCatchEvent, and BoundaryEvent.
 * <p>
 * This class verifies that BPMN events meet the required configuration criteria such as:
 * <ul>
 *   <li>Non-empty event names</li>
 *   <li>Correct usage of message references (e.g., MessageEventDefinition) and signal references</li>
 *   <li>Proper field injections (e.g., for profile, messageName, and instantiatesCanonical)</li>
 *   <li>Validation of implementation classes and execution listener classes</li>
 *   <li>Cross-checking references against FHIR resources using {@link FhirValidator}</li>
 * </ul>
 * </p>
 * <p>
 * The validations are tailored according to the BPMN event type. For example, message events are further
 * validated to ensure that their associated messages have valid references in the FHIR domain.
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
public class BpmnEventValidator
{
    private final File projectRoot;

    /**
     * Constructs a new {@code BpmnEventValidator} with the specified project root.
     *
     * @param projectRoot the root directory of the project containing FHIR and BPMN resources
     */
    public BpmnEventValidator(File projectRoot)
    {
        this.projectRoot = projectRoot;
    }

    // START EVENT VALIDATION

    /**
     * Validates a {@link StartEvent}.
     * <p>
     * If the event contains a {@link MessageEventDefinition}, the method delegates to
     * {@link #validateMessageStartEvent(StartEvent, List, File, String)}; otherwise, it performs
     * generic start event validation via {@link #validateGenericStartEvent(StartEvent, List, File, String)}.
     * </p>
     *
     * @param startEvent the {@link StartEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
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
     * Validates a {@link StartEvent} that contains a {@link MessageEventDefinition}.
     * <p>
     * The validation includes:
     * <ul>
     *   <li>Ensuring the event name is not empty.</li>
     *   <li>Checking that the associated message's name is not empty.</li>
     *   <li>Verifying that the message name corresponds to valid FHIR resources by checking for the existence
     *       of an ActivityDefinition and a StructureDefinition using {@link FhirValidator}.</li>
     * </ul>
     * </p>
     *
     * @param startEvent the {@link StartEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
     */
    private void validateMessageStartEvent(
            StartEvent startEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = startEvent.getId();
        if (BpmnValidationUtils.isEmpty(startEvent.getName())) {
            issues.add(new BpmnEventNameEmptyValidationItem(elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }

        MessageEventDefinition messageDef =
                (MessageEventDefinition) startEvent.getEventDefinitions().iterator().next();

        if (messageDef.getMessage() == null || BpmnValidationUtils.isEmpty(messageDef.getMessage().getName())) {
            issues.add(new BpmnMessageStartEventMessageNameEmptyValidationItem(elementId, bpmnFile, processId));
        } else {
            // Check references in FHIR resources
            String msgName = messageDef.getMessage().getName();
            boolean found = false;
            if (!FhirValidator.activityDefinitionExists(msgName, projectRoot)) {
                issues.add(new FhirActivityDefinitionValidationItem(
                        ValidationSeverity.WARN,
                        elementId,
                        bpmnFile,
                        processId,
                        msgName,
                        "No ActivityDefinition found for messageName: " + msgName
                ));
            } else {
                found = true;
            }
            if (found) {
                if (!FhirValidator.structureDefinitionExists(msgName, projectRoot)) {
                    issues.add(new FhirStructureDefinitionValidationItem(ValidationSeverity.ERROR,
                            elementId,
                            bpmnFile,
                            processId,
                            msgName,
                            "No StructureDefinition found for messageName: " + msgName
                    ));
                }
            }
        }
    }

    /**
     * Performs generic validation on a {@link StartEvent} that does not contain a {@link MessageEventDefinition}.
     * <p>
     * If the start event is not part of a {@link SubProcess} and its name is empty, a warning is issued.
     * </p>
     *
     * @param startEvent the {@link StartEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
     */
    private void validateGenericStartEvent(
            StartEvent startEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = startEvent.getId();
        // Warn if the Start Event is not part of a SubProcess and hast no name
        if (!(startEvent.getParentElement() instanceof SubProcess))
        {
            if (BpmnValidationUtils.isEmpty(startEvent.getName()))
            {
                issues.add(new BpmnStartEventNotPartOfSubProcessValidationItem(elementId, bpmnFile, processId));
            }
        }
    }

    // INTERMEDIATE THROW EVENT VALIDATION

    /**
     * Validates an {@link IntermediateThrowEvent}.
     * <p>
     * This method delegates the validation based on the type of event definition:
     * <ul>
     *   <li>If the event definition is a {@link MessageEventDefinition}, it calls
     *       {@link #validateMessageIntermediateThrowEvent(IntermediateThrowEvent, List, File, String)}.</li>
     *   <li>If the event definition is a {@link SignalEventDefinition}, it calls
     *       {@link #validateSignalIntermediateThrowEvent(IntermediateThrowEvent, List, File, String)}.</li>
     * </ul>
     * </p>
     *
     * @param throwEvent the {@link IntermediateThrowEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
     */
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
     * Validates an {@link IntermediateThrowEvent} that contains a {@link MessageEventDefinition}.
     * <p>
     * The validation includes performing common message event validations and issuing a warning if the
     * event definition's message reference is not null.
     * </p>
     *
     * @param throwEvent the {@link IntermediateThrowEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
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
     * Validates an {@link IntermediateThrowEvent} that contains a {@link SignalEventDefinition}.
     * <p>
     * The validation ensures that the event name is not empty and that the associated signal is present
     * and has a non-empty name.
     * </p>
     *
     * @param throwEvent the {@link IntermediateThrowEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
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
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.ERROR
            ));
        }
    }

    // END EVENT VALIDATION

    /**
     * Validates an {@link EndEvent}.
     * <p>
     * This method delegates validation based on the type of event definition:
     * <ul>
     *   <li>If the event contains a {@link MessageEventDefinition}, it calls
     *       {@link #validateMessageEndEvent(EndEvent, List, File, String)}.</li>
     *   <li>If the event contains a {@link SignalEventDefinition}, it calls
     *       {@link #validateSignalEndEvent(EndEvent, List, File, String)}.</li>
     *   <li>If no specific event definition is present, it calls
     *       {@link #validateGenericEndEvent(EndEvent, List, File, String)}.</li>
     * </ul>
     * </p>
     *
     * @param endEvent the {@link EndEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
     */
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
     * Validates an {@link EndEvent} that contains a {@link MessageEventDefinition}.
     * <p>
     * This method performs common message event validations for the end event by delegating
     * to {@link #validateCommonMessageEvent(FlowElement, List, File, String)}.
     * </p>
     *
     * @param endEvent the {@link EndEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
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
     * Validates an {@link EndEvent} that does not contain a {@link MessageEventDefinition}.
     * <p>
     * For such events, the validation checks include:
     * <ul>
     *   <li>If the event is not part of a {@link SubProcess}, its name must not be empty.</li>
     *   <li>If the event is part of a {@link SubProcess}, the property {@code camunda:asyncAfter} must be set to {@code true}.</li>
     *   <li>Verifying that execution listener classes referenced in the event's extension elements exist on the classpath.</li>
     * </ul>
     * </p>
     *
     * @param endEvent the {@link EndEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
     */
    private void validateGenericEndEvent(
            EndEvent endEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = endEvent.getId();
        // Warn if the End Event is not part of a SubProcess and hast no name
        if (!(endEvent.getParentElement() instanceof SubProcess))
        {
            if (BpmnValidationUtils.isEmpty(endEvent.getName()))
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
     * Validates an {@link EndEvent} that contains a {@link SignalEventDefinition}.
     * <p>
     * The validation ensures that:
     * <ul>
     *   <li>The event name is not empty.</li>
     *   <li>The associated signal is present and its name is not empty.</li>
     * </ul>
     * </p>
     *
     * @param endEvent the {@link EndEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
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
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.ERROR
            ));
        }
    }

    // INTERMEDIATE CATCH EVENT VALIDATION

    /**
     * Validates an {@link IntermediateCatchEvent}.
     * <p>
     * Based on the type of event definition contained in the catch event, this method delegates validation to:
     * <ul>
     *   <li>{@link #validateMessageIntermediateCatchEvent(IntermediateCatchEvent, List, File, String)} for a {@link MessageEventDefinition}</li>
     *   <li>{@link #validateTimerIntermediateCatchEvent(IntermediateCatchEvent, List, File, String)} for a {@link TimerEventDefinition}</li>
     *   <li>{@link #validateSignalIntermediateCatchEvent(IntermediateCatchEvent, List, File, String)} for a {@link SignalEventDefinition}</li>
     *   <li>{@link #validateConditionalIntermediateCatchEvent(IntermediateCatchEvent, List, File, String)} for a {@link ConditionalEventDefinition}</li>
     * </ul>
     * </p>
     *
     * @param catchEvent the {@link IntermediateCatchEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
     */
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
     * Validates an {@link IntermediateCatchEvent} that contains a {@link MessageEventDefinition}.
     * <p>
     * The validation ensures:
     * <ul>
     *   <li>The event name is not empty.</li>
     *   <li>The associated message's name is not empty.</li>
     *   <li>The message name is verified against FHIR resources using {@link BpmnValidationUtils#checkMessageName(String, List, String, File, String, File)}</li>
     * </ul>
     * </p>
     *
     * @param catchEvent the {@link IntermediateCatchEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
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
     * Validates an {@link IntermediateCatchEvent} that contains a {@link TimerEventDefinition}.
     * <p>
     * The validation for a timer event includes:
     * <ul>
     *   <li>Ensuring the event name is not empty.</li>
     *   <li>Verifying that one of the timer definitions ({@code timeDate}, {@code timeCycle}, or {@code timeDuration}) is present.</li>
     *   <li>If {@code timeDate} is used, logging an informational message.</li>
     *   <li>If {@code timeCycle} or {@code timeDuration} is used, issuing a warning if no version placeholder is present.</li>
     * </ul>
     * </p>
     *
     * @param catchEvent the {@link IntermediateCatchEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
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
     * Validates an {@link IntermediateCatchEvent} that contains a {@link SignalEventDefinition}.
     * <p>
     * The validation ensures:
     * <ul>
     *   <li>The event name is not empty.</li>
     *   <li>The associated signal is present and its name is not empty.</li>
     * </ul>
     * </p>
     *
     * @param catchEvent the {@link IntermediateCatchEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
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
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.ERROR
            ));
        }
    }

    /**
     * Validates an {@link IntermediateCatchEvent} that contains a {@link ConditionalEventDefinition}.
     * <p>
     * The validation for a conditional catch event involves checking the event's variable name,
     * variable events, condition type, and the presence of a condition expression.
     * </p>
     *
     * @param catchEvent the {@link IntermediateCatchEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
     */
    private void validateConditionalIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        BpmnValidationUtils.checkConditionalEvent(catchEvent, issues, bpmnFile, processId);
    }

    // BOUNDARY EVENT VALIDATION

    /**
     * Validates a {@link BoundaryEvent}.
     * <p>
     * Based on the type of event definition contained in the boundary event, this method delegates
     * validation to either:
     * <ul>
     *   <li>{@link #validateMessageBoundaryEvent(BoundaryEvent, List, File, String)} if a {@link MessageEventDefinition} is present</li>
     *   <li>{@link #validateErrorBoundaryEvent(BoundaryEvent, List, File, String)} if an {@link ErrorEventDefinition} is present</li>
     * </ul>
     * </p>
     *
     * @param boundaryEvent the {@link BoundaryEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
     */
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
     * Validates a {@link BoundaryEvent} that contains a {@link MessageEventDefinition}.
     * <p>
     * The validation includes:
     * <ul>
     *   <li>Checking that the event name is not empty.</li>
     *   <li>Ensuring that the associated message's name is not empty.</li>
     *   <li>Verifying that the message name is recognized in FHIR resources using {@link FhirValidator}.</li>
     * </ul>
     * </p>
     *
     * @param boundaryEvent the {@link BoundaryEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
     */
    private void validateMessageBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = boundaryEvent.getId();
        if (BpmnValidationUtils.isEmpty(boundaryEvent.getName())) {
            issues.add(new BpmnMessageBoundaryEventNameEmptyValidationItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }
        MessageEventDefinition def =
                (MessageEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();

        if (def.getMessage() == null || BpmnValidationUtils.isEmpty(def.getMessage().getName())) {
            issues.add(new BpmnMessageStartEventMessageNameEmptyValidationItem(elementId, bpmnFile, processId));
        } else {
            String msgName = def.getMessage().getName();
            boolean found = false;
            if (!FhirValidator.activityDefinitionExists(msgName, projectRoot)) {
                issues.add(new FhirActivityDefinitionValidationItem(
                        ValidationSeverity.WARN,
                        elementId,
                        bpmnFile,
                        processId,
                        msgName,
                        "No ActivityDefinition found for messageName: " + msgName
                ));
            } else {
                found = true;
            }
            if (found) {
                if (!FhirValidator.structureDefinitionExists(msgName, projectRoot)) {
                    issues.add(new FhirStructureDefinitionValidationItem(
                            ValidationSeverity.ERROR,
                            elementId,
                            bpmnFile,
                            processId,
                            msgName,
                            "No StructureDefinition found for messageName: " + msgName
                    ));
                }
            }
        }
    }
    /**
     * Validates a {@link BoundaryEvent} that contains an {@link ErrorEventDefinition}.
     * <p>
     * The method delegates validation to {@link BpmnValidationUtils#checkErrorBoundaryEvent(BoundaryEvent, List, File, String)},
     * which verifies the presence and correctness of the error reference and configuration.
     * </p>
     *
     * @param boundaryEvent the {@link BoundaryEvent} to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
     */
    private void validateErrorBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        BpmnValidationUtils.checkErrorBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
    }

    // COMMON MESSAGE EVENT VALIDATION (shared)

    /**
     * Performs common validations for a BPMN event that uses a message definition,
     * such as an {@link IntermediateThrowEvent} or {@link EndEvent} with a {@link MessageEventDefinition}.
     * <p>
     * The common validations include:
     * <ul>
     *   <li>Ensuring the event name is not empty.</li>
     *   <li>Extracting and validating the implementation class (e.g., checking that it is not empty, exists,
     *       and implements the required interface such as {@code JavaDelegate}).</li>
     *   <li>Validating field injections for attributes such as <code>profile</code>, <code>messageName</code>,
     *       and <code>instantiatesCanonical</code> using the {@link BpmnFieldInjectionValidator}.</li>
     * </ul>
     * </p>
     *
     * @param event the BPMN {@link FlowElement} (such as an IntermediateThrowEvent or EndEvent) to be validated
     * @param issues a list to which any validation issues will be added
     * @param bpmnFile the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
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
