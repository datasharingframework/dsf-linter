/*
package dev.dsf.utils.validator.bpmn;


import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import dev.dsf.utils.validator.fhir.FhirValidator;
import dev.dsf.utils.validator.item.*;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaField;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;


import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
/*
/**
 * <p>
 * The {@code BpmnModelValidator} is responsible for validating Camunda BPMN models against
 * various business logic and FHIR-related constraints. It ensures that BPMN elements
 * such as Tasks, Events, Gateways, and others comply with specific naming, class-implementation,
 * and FHIR resource referencing rules.
 * </p>
 *
 * <h2>Overview of Validation Checks</h2>
 * <ul>
 *   <li><strong>Service Tasks</strong>: Checks non-empty name, implementation class existence,
 *       and {@code JavaDelegate} implementation.</li>
 *   <li><strong>Message Start/Intermediate/End Events</strong>:
 *       Ensures non-empty names, verifies corresponding message definitions, checks
 *       implementation class references, and validates field injections (e.g., {@code profile},
 *       {@code messageName}, {@code instantiatesCanonical}).</li>
 *   <li><strong>Error Boundary Events</strong>:
 *       Splits validation into two scenarios (with or without {@code errorRef}).
 *       Logs WARN if boundary name is empty, ERROR if error name/code is missing, and WARN
 *       if {@code errorCodeVariable} is empty.</li>
 *   <li><strong>Exclusive Gateways and Sequence Flows</strong>:
 *       If an ExclusiveGateway has multiple outgoing flows, warns if the Sequence Flow has no name
 *       and errors if no condition is present on non-default flows.</li>
 *   <li><strong>User Tasks</strong>:
 *       Checks that the user task name is not empty, verifies {@code formKey} format,
 *       and ensures the external questionnaire resource is findable.</li>
 *   <li><strong>Timer/Signal/Conditional Events</strong>:
 *       Validates presence of expected configuration (time expressions, signal definitions,
 *       condition expressions) and warns if certain placeholders or parameters are missing.</li>
 *   <li><strong>SubProcesses</strong>:
 *       If multi-instance, checks if the sub-process is configured with {@code asyncBefore}.</li>
 *   <li><strong>Field Injections</strong>:
 *       For BPMN elements that utilize {@code <camunda:field>} (e.g., SendTask, Message Throw/End Events),
 *       checks:
 *       <ul>
 *         <li>{@code profile}: Non-empty, optional version placeholder, existence in FHIR StructureDefinition.</li>
 *         <li>{@code messageName}: Non-empty, optional cross-check with the previously found {@code profile} string.</li>
 *         <li>{@code instantiatesCanonical}: Non-empty, warns if missing version placeholder, checks existence in
 *         FHIR ActivityDefinition, and cross-checks message name if found.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>
 * See below for method-level JavaDocs detailing each validation rule. In addition,
 * references are provided to official BPMN and FHIR documentation:
 * </p>
 *
 * @see <a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a>
 * @see <a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">
 *      Camunda Extension Elements</a>
 * @see <a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a>
 * @see <a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a>
 */
/*
public class BpmnModelValidatorOldVersion
{
    private File projectRoot;

    /**
     * Sets the project root directory used for validation, which is needed for
     * loading compiled classes and dependencies from the file system.
     *
     * @param projectRoot
     *         the root directory of the project (e.g., containing {@code target/classes} or {@code build/classes})
     */
/*
    public void setProjectRoot(File projectRoot)
    {
        this.projectRoot = projectRoot;
    }

    /**
     * <p>
     * Validates a given {@link BpmnModelInstance} against various business and
     * FHIR-related constraints, collecting any violations in a list of
     * {@link BpmnElementValidationItem}. Each item denotes a specific issue
     * (e.g., empty name, missing field injection, invalid reference).
     * </p>
     *
     * <p>
     * The checks performed here include (but are not limited to):
     * </p>
     * <ul>
     *   <li>ServiceTask: name not empty, class must exist, class must implement
     *       {@code JavaDelegate}.</li>
     *   <li>Message Events: checks name, message definitions, and the correctness
     *       of field injections such as {@code profile}, {@code messageName}, and
     *       {@code instantiatesCanonical} (including references to FHIR
     *       StructureDefinition and ActivityDefinition).</li>
     *   <li>Error Boundary Events: if {@code errorRef} is present, check that
     *       error name/code is not empty; if boundary name is empty => WARN, and
     *       {@code errorCodeVariable} missing => WARN.</li>
     *   <li>Exclusive Gateway / Sequence Flow: if the gateway has more than one
     *       outgoing flow, warns if a flow is unnamed, errors if no condition
     *       on non-default flows.</li>
     *   <li>Various other events (Timer, Signal, Conditional) each have specialized
     *       checks for completeness and placeholder usage.</li>
     * </ul>
     *
     * @param model
     *         the BPMN model to validate
     * @param bpmnFile
     *         the source {@code .bpmn} file (used only for logging in the validation items)
     * @param processId
     *         the BPMN process identifier (also used for logging in validation items)
     * @return a list of validation items representing all discovered issues
     */

/*
    public List<BpmnElementValidationItem> validateModel(
            BpmnModelInstance model,
            File bpmnFile,
            String processId)
    {
        List<BpmnElementValidationItem> issues = new ArrayList<>();
        Collection<FlowElement> flowElements = model.getModelElementsByType(FlowElement.class);

        for (FlowElement element : flowElements)
        {
            if (element instanceof ServiceTask serviceTask)
            {
                validateServiceTask(serviceTask, issues, bpmnFile, processId);
            }
            else if (element instanceof StartEvent startEvent)
            {
                if (!startEvent.getEventDefinitions().isEmpty()
                        && startEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition)
                {
                    validateMessageStartEvent(startEvent, issues, bpmnFile, processId);
                }
                else
                {
                    validateStartEvent(startEvent, issues, bpmnFile, processId);
                }
            }
            else if (element instanceof IntermediateThrowEvent throwEvent)
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
            }
            else if (element instanceof EndEvent endEvent)
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
                    validateEndEvent(endEvent, issues, bpmnFile, processId);
                }
            }
            else if (element instanceof IntermediateCatchEvent catchEvent)
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
            }
            else if (element instanceof BoundaryEvent boundaryEvent)
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
            else if (element instanceof ExclusiveGateway exclusiveGateway)
            {
                validateExclusiveGateway(exclusiveGateway, issues, bpmnFile, processId);
            }
            else if (element instanceof SequenceFlow sequenceFlow)
            {
                validateSequenceFlow(sequenceFlow, issues, bpmnFile, processId);
            }
            else if (element instanceof UserTask userTask)
            {
                validateUserTask(userTask, issues, bpmnFile, processId);
            }
            else if (element instanceof SendTask sendTask)
            {
                validateSendTask(sendTask, issues, bpmnFile, processId);
            }
            else if (element instanceof ReceiveTask receiveTask)
            {
                validateReceiveTask(receiveTask, issues, bpmnFile, processId);
            }
            else if (element instanceof SubProcess subProcess)
            {
                validateSubProcess(subProcess, issues, bpmnFile, processId);
            }
            else if (element instanceof EventBasedGateway gateway)
            {
                validateEventBasedGateway(gateway, issues, bpmnFile, processId);
            }
        }
        return issues;
    }

    /**
     * Validates that a {@link ServiceTask} has a non-empty name, a non-empty
     * {@code camunda:class}, that the class can be loaded, and that it implements
     * {@code org.camunda.bpm.engine.delegate.JavaDelegate}.
     *
     * @param task
     *         the {@link ServiceTask} to validate
     * @param issues
     *         the list to collect any identified validation items
     * @param bpmnFile
     *         the .bpmn file for reference
     * @param processId
     *         the process identifier for logging reference
     */
/*
    private void validateServiceTask(ServiceTask task,
                                     List<BpmnElementValidationItem> issues,
                                     File bpmnFile,
                                     String processId)
    {
        String elementId = task.getId();
        if (isEmpty(task.getName()))
        {
            issues.add(new BpmnServiceTaskNameEmptyValidationItem(elementId, bpmnFile, processId));
        }

        String implClass = task.getCamundaClass();
        if (implClass == null)
        {
            issues.add(new BpmnServiceTaskImplementationNotExistValidationItem(elementId, bpmnFile, processId));
        }
        else if (implClass.trim().isEmpty())
        {
            issues.add(new BpmnServiceTaskImplementationClassEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            // is the class in the real usage?
            if (!classExists(implClass))
            {
                issues.add(new BpmnServiceTaskImplementationClassNotFoundValidationItem(
                        elementId, bpmnFile, processId, implClass));
            }
            // has the class java delegate?
            else if (!implementsJavaDelegate(implClass))
            {
                issues.add(new BpmnServiceTaskImplementationClassNotImplementingJavaDelegateValidationItem(
                        elementId, bpmnFile, processId, implClass));
            }
        }
    }

    // MESSAGE START EVENT VALIDATION

    /**
     * Validates a {@link StartEvent} that specifically contains a {@link MessageEventDefinition}.
     * Checks that:
     * <ul>
     *   <li>Event name is not empty</li>
     *   <li>{@code Message.name} is not empty</li>
     *   <li>{@code Message.name} corresponds to valid references in FHIR resources (ActivityDefinition
     *       and StructureDefinition)</li>
     * </ul>
     *
     * @param startEvent
     *         the {@link StartEvent} to validate
     * @param issues
     *         the list of validation items
     * @param bpmnFile
     *         the BPMN file reference
     * @param processId
     *         the process identifier
     */
/*
    private void validateMessageStartEvent(StartEvent startEvent,
                                           List<BpmnElementValidationItem> issues,
                                           File bpmnFile,
                                           String processId)
    {
        String elementId = startEvent.getId();
        if (isEmpty(startEvent.getName()))
        {
            issues.add(new BpmnEventNameEmptyValidationItem(elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }
        MessageEventDefinition messageDef =
                (MessageEventDefinition) startEvent.getEventDefinitions().iterator().next();
        validateMessageDefinition(startEvent, messageDef, issues, bpmnFile, processId);
    }

    // MESSAGE INTERMEDIATE THROW EVENT VALIDATION

    /**
     * Validates an {@link IntermediateThrowEvent} that has a {@link MessageEventDefinition}.
     * Includes checks for:
     * <ul>
     *   <li>Non-empty event name</li>
     *   <li>Implementation class validity</li>
     *   <li>Field injections</li>
     *   <li>A warning if the {@code <bpmn:message>} reference is not null (in most
     *       Camunda scenarios for an Intermediate Throw Message, the engine uses
     *       {@code <camunda:class>} instead)</li>
     * </ul>
     *
     * @param throwEvent
     *         the IntermediateThrowEvent to validate
     * @param issues
     *         the list to store validation findings
     * @param bpmnFile
     *         reference to the BPMN file for context
     * @param processId
     *         the process id
     */
/*
    private void validateMessageIntermediateThrowEvent(IntermediateThrowEvent throwEvent,
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
     * Checks the {@code camunda:class} reference for a message event element, verifying the
     * class is non-empty, exists on the classpath, and implements {@code JavaDelegate}.
     *
     * @param implClass
     *         the class name from {@code camunda:class}
     * @param elementId
     *         the BPMN element id
     * @param bpmnFile
     *         reference to the BPMN file
     * @param processId
     *         the process id
     * @param issues
     *         the list to store any validation issues
     */
/*
    private void validateImplementationClass(String implClass,
                                             String elementId,
                                             File bpmnFile,
                                             String processId,
                                             List<BpmnElementValidationItem> issues)
    {
        if (isEmpty(implClass))
        {
            issues.add(new BpmnMessageSendEventImplementationClassEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else if (!classExists(implClass))
        {
            issues.add(new BpmnMessageSendEventImplementationClassNotFoundValidationItem(
                    elementId, bpmnFile, processId, implClass));
        }
        else if (!implementsJavaDelegate(implClass))
        {
            issues.add(new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateValidationItem(
                    elementId, bpmnFile, processId, implClass));
        }
    }

    // MESSAGE END EVENT VALIDATION

    /**
     * Validates a {@link EndEvent} that has a {@link MessageEventDefinition}.
     * Ensures:
     * <ul>
     *   <li>Non-empty event name</li>
     *   <li>Implementation class correctness</li>
     *   <li>Field injections checked for required fields</li>
     *   <li>If the event name is "send result to requester", checks {@code asyncBefore} is true</li>
     * </ul>
     *
     * @param endEvent
     *         the {@link EndEvent} to validate
     * @param issues
     *         the validation item list
     * @param bpmnFile
     *         reference to the BPMN file
     * @param processId
     *         the process id
     */
/*
    private void validateMessageEndEvent(EndEvent endEvent,
                                         List<BpmnElementValidationItem> issues,
                                         File bpmnFile,
                                         String processId)
    {
        validateCommonMessageEvent(endEvent, issues, bpmnFile, processId);

        // Example rule: if the end event is named "send result to requester", it should have asyncBefore=true.
        /*
        if ("send result to requester".equalsIgnoreCase(endEvent.getName()))
        {
            if (!endEvent.isCamundaAsyncBefore())
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        endEvent.getId(), bpmnFile, processId,
                        "Message End Event named 'send result to requester' should have asyncBefore=true",
                        ValidationType.BPMN_FLOATING_ELEMENT
                ));
            }
        } */
/*
    }
    /*

    // MESSAGE INTERMEDIATE CATCH EVENT VALIDATION

    /**
     * Validates an {@link IntermediateCatchEvent} containing a {@link MessageEventDefinition}.
     * Checks that the event name and message name are not empty, and verifies that the
     * message name is found in the relevant FHIR resources.
     *
     * @param catchEvent
     *         the {@link IntermediateCatchEvent}
     * @param issues
     *         the validation list
     * @param bpmnFile
     *         reference to BPMN file
     * @param processId
     *         process identifier
     */
/*
    private void validateMessageIntermediateCatchEvent(IntermediateCatchEvent catchEvent,
                                                       List<BpmnElementValidationItem> issues,
                                                       File bpmnFile,
                                                       String processId)
    {
        String elementId = catchEvent.getId();

        if (isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnMessageIntermediateCatchEventNameEmptyValidationItem (elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }

        MessageEventDefinition def =
                (MessageEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        if (def.getMessage() == null || isEmpty(def.getMessage().getName()))
        {
            issues.add(new BpmnMessageStartEventMessageNameEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            checkMessageName(def.getMessage().getName(), issues, elementId, bpmnFile, processId);
        }
    }

    // MESSAGE BOUNDARY EVENT VALIDATION

    /**
     * Validates a {@link BoundaryEvent} that references a {@link MessageEventDefinition}.
     * Checks for non-empty name, non-empty message name, and if that name is recognized
     * in FHIR resources.
     *
     * @param boundaryEvent
     *         the {@link BoundaryEvent}
     * @param issues
     *         the validation item list
     * @param bpmnFile
     *         reference to BPMN file
     * @param processId
     *         process identifier
     */
/*
    private void validateMessageBoundaryEvent(BoundaryEvent boundaryEvent,
                                              List<BpmnElementValidationItem> issues,
                                              File bpmnFile,
                                              String processId)
    {
        String elementId = boundaryEvent.getId();
        if (isEmpty(boundaryEvent.getName()))
        {
            issues.add(new BpmnMessageBoundaryEventNameEmptyValidationItem (elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }
        MessageEventDefinition def =
                (MessageEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();
        validateMessageDefinition(boundaryEvent, def, issues, bpmnFile, processId);
    }

    // ERROR BOUNDARY EVENT VALIDATION

    /**
     * Validates an {@link BoundaryEvent} containing an {@link ErrorEventDefinition}.
     * Splits the logic based on whether {@code errorRef} is set:
     * <ul>
     *   <li>If {@code errorRef} is present, checks that {@code <bpmn:error>} has a non-empty
     *       name and non-empty error code. Missing values => ERROR.</li>
     *   <li>Regardless, if boundary event name is empty => WARN.</li>
     *   <li>If the {@code errorCodeVariable} attribute is absent => WARN.</li>
     * </ul>
     *
     * @param boundaryEvent
     *         the boundary event to validate
     * @param issues
     *         the validation issues collector
     * @param bpmnFile
     *         BPMN file reference
     * @param processId
     *         process id
     */
/*
    private void validateErrorBoundaryEvent(BoundaryEvent boundaryEvent,
                                            List<BpmnElementValidationItem> issues,
                                            File bpmnFile,
                                            String processId)
    {
        String elementId = boundaryEvent.getId();

        if (isEmpty(boundaryEvent.getName()))
        {
            // WARN if boundary event's name is empty
            issues.add(new BpmnErrorBoundaryEventNameEmptyValidationItem(
                    elementId, bpmnFile, processId
            ));
        }

        ErrorEventDefinition errorDef =
                (ErrorEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();

        if (errorDef.getError() != null)
        {
            // Scenario A: errorRef is present
            if (isEmpty(errorDef.getError().getName()))
            {
                issues.add(new BpmnErrorBoundaryEventErrorNameEmptyValidationItem(
                        elementId, bpmnFile, processId
                ));
            }
            if (isEmpty(errorDef.getError().getErrorCode()))
            {
                issues.add(new BpmnErrorBoundaryEventErrorCodeEmptyValidationItem(
                        elementId, bpmnFile, processId
                ));
            }
        }
        // else => scenario B: muss be implemented

        String errorCodeVariable = errorDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "errorCodeVariable");
        if (isEmpty(errorCodeVariable))
        {
            issues.add(new BpmnErrorBoundaryEventErrorCodeVariableEmptyValidationItem(
                    elementId, bpmnFile, processId
            ));
        }
    }

    // EXCLUSIVE GATEWAY VALIDATION

    /**
     * Validates an {@link ExclusiveGateway}. Primarily defers to {@code validateSequenceFlow}
     * for condition checks but may place additional rules for name presence if needed.
     *
     * @param gateway
     *         the {@link ExclusiveGateway} to validate
     * @param issues
     *         the validation item collector
     * @param bpmnFile
     *         BPMN file reference
     * @param processId
     *         process id
     */
/*
    private void validateExclusiveGateway(ExclusiveGateway gateway,
                                          List<BpmnElementValidationItem> issues,
                                          File bpmnFile,
                                          String processId)
    {
        String elementId = gateway.getId();

        if (isEmpty(gateway.getName()))
        {
            if (gateway.getOutgoing() != null && gateway.getOutgoing().size() > 1)
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Exclusive Gateway has multiple outgoing flows but name is empty.",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.WARN
                ));
            }
        }

    }

    // SEQUENCE FLOW VALIDATION
    /**
     * Validates a sequence flow based on its source element's outgoing flows.
     * <p>
     * The validation applies only when the source element has more than one outgoing sequence flow.
     * It issues:
     * <ul>
     *   <li>A warning that the sequence flow originates from a source with multiple outgoing flows.</li>
     *   <li>A warning if the sequence flow name is empty.</li>
     *   <li>An error if the condition expression is missing and the sequence flow is not the default flow
     *       (for example, from an ExclusiveGateway).</li>
     * </ul>
     *
     * @param flow      The sequence flow to validate.
     * @param issues    The list of validation issues to be appended.
     * @param bpmnFile  The BPMN file in context.
     * @param processId The process identifier.
     */
/*
    private void validateSequenceFlow(SequenceFlow flow,
                                      List<BpmnElementValidationItem> issues,
                                      File bpmnFile,
                                      String processId) {
        String elementId = flow.getId();
        FlowElement source = flow.getSource();

        // Ensure the source is a FlowNode to access the outgoing flows.
        if (source instanceof FlowNode) {
            FlowNode flowNode = (FlowNode) source;
            if (flowNode.getOutgoing() != null && flowNode.getOutgoing().size() > 1) {
                // Warn that the sequence flow originates from a source with multiple outgoing flows.
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Sequence flow originates from a source with multiple outgoing flows.",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.WARN
                ));

                // Warn if the sequence flow's name is empty.
                if (isEmpty(flow.getName())) {
                    issues.add(new BpmnFloatingElementValidationItem(
                            elementId, bpmnFile, processId,
                            "Sequence flow name is empty.",
                            ValidationType.BPMN_FLOATING_ELEMENT,
                            ValidationSeverity.WARN
                    ));
                }

                // If the condition expression is missing, check if the flow is not the default flow.
                if (flow.getConditionExpression() == null) {
                    // For ExclusiveGateway sources, only non-default flows must have a condition.
                    if (source instanceof ExclusiveGateway) {
                        ExclusiveGateway gateway = (ExclusiveGateway) source;
                        if (!flow.equals(gateway.getDefault())) {
                            issues.add(new BpmnFloatingElementValidationItem(
                                    elementId, bpmnFile, processId,
                                    "Non-default sequence flow from an ExclusiveGateway is missing a condition expression.",
                                    ValidationType.BPMN_SEQUENCE_FLOW_AMBIGUOUS,
                                    ValidationSeverity.ERROR
                            ));
                        }
                    }
                    // Optionally, add further checks for other gateway types if needed.
                }
            }
        }
    }

    // START EVENT VALIDATION

    /**
     * Validates a {@link StartEvent} that does NOT have a {@link MessageEventDefinition}.
     * Simply warns if the name is empty (and if it's not part of a {@link SubProcess}).
     *
     * @param startEvent
     *         the StartEvent to validate
     * @param issues
     *         the validation collector
     * @param bpmnFile
     *         BPMN file reference
     * @param processId
     *         process id
     */
/*
    private void validateStartEvent(StartEvent startEvent,
                                    List<BpmnElementValidationItem> issues,
                                    File bpmnFile,
                                    String processId)
    {
        String elementId = startEvent.getId();

        if (isEmpty(startEvent.getName()))
        {
            // Warn if the Start Event is not part of a SubProcess
            if (!(startEvent.getParentElement() instanceof SubProcess))
            {
                issues.add(new BpmnStartEventNotPartOfSubProcessValidationItem(elementId, bpmnFile, processId));
            }
        }
    }

    // END EVENT VALIDATION

    /**
     * Validates an {@link EndEvent} that does NOT have a {@link MessageEventDefinition}.
     * Checks:
     * <ul>
     *   <li>Non-empty name (if not in a SubProcess)</li>
     *   <li>{@code camunda:asyncAfter} must be true if the event is inside a SubProcess</li>
     *   <li>Any extension listeners referencing {@code camunda:class} must exist on the classpath</li>
     * </ul>
     *
     * @param endEvent
     *         the EndEvent to validate
     * @param issues
     *         list for collecting validation items
     * @param bpmnFile
     *         BPMN file reference
     * @param processId
     *         process identifier
     */
/*
    private void validateEndEvent(EndEvent endEvent,
                                  List<BpmnElementValidationItem> issues,
                                  File bpmnFile,
                                  String processId)
    {
        String elementId = endEvent.getId();

        if (isEmpty(endEvent.getName()))
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

        // Check extension listeners
        if (endEvent.getExtensionElements() != null)
        {
            Collection<CamundaExecutionListener> listeners =
                    endEvent.getExtensionElements().getElementsQuery()
                            .filterByType(CamundaExecutionListener.class).list();
            for (CamundaExecutionListener listener : listeners)
            {
                String implClass = listener.getCamundaClass();
                if (!isEmpty(implClass) && !classExists(implClass))
                {
                    issues.add(new BpmnFloatingElementValidationItem(
                            elementId, bpmnFile, processId,
                            "Execution listener class not found: " + implClass,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND
                    ));
                }
            }
        }
    }

    // TIMER INTERMEDIATE CATCH EVENT VALIDATION

    /**
     * Validates an {@link IntermediateCatchEvent} containing a {@link TimerEventDefinition}.
     * Checks:
     * <ul>
     *   <li>Non-empty name</li>
     *   <li>Presence of one of {@code timeDate}, {@code timeCycle}, {@code timeDuration}</li>
     *   <li>If {@code timeDate} is used, logs an INFO message to verify it is intended</li>
     *   <li>If {@code timeCycle} or {@code timeDuration} is used, warns if the version placeholder
     *       (e.g., {@code #{version}}) is missing</li>
     * </ul>
     *
     * @param catchEvent
     *         the IntermediateCatchEvent for a timer
     * @param issues
     *         the validation collector
     * @param bpmnFile
     *         BPMN file reference
     * @param processId
     *         process identifier
     */
/*
    private void validateTimerIntermediateCatchEvent(IntermediateCatchEvent catchEvent,
                                                     List<BpmnElementValidationItem> issues,
                                                     File bpmnFile,
                                                     String processId)
    {
        String elementId = catchEvent.getId();

        if (isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Timer Intermediate Catch Event name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        TimerEventDefinition timerDef =
                (TimerEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        // Retrieve the timer expressions (they may be null)
        Expression timeDateExpr = timerDef.getTimeDate();
        Expression timeCycleExpr = timerDef.getTimeCycle();
        Expression timeDurationExpr = timerDef.getTimeDuration();

        // Check if the timer type is set by verifying that at least one expression is non-null and non-empty.
        boolean isTimeDateEmpty = (timeDateExpr == null || isEmpty(timeDateExpr.getTextContent()));
        boolean isTimeCycleEmpty = (timeCycleExpr == null || isEmpty(timeCycleExpr.getTextContent()));
        boolean isTimeDurationEmpty = (timeDurationExpr == null || isEmpty(timeDurationExpr.getTextContent()));

        if (isTimeDateEmpty && isTimeCycleEmpty && isTimeDurationEmpty)
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Timer type is empty (no timeDate, timeCycle, or timeDuration)",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
        else
        {
            // If timeDate is set, issue an info message (fixed date/time)
            if (!isTimeDateEmpty)
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Timer type is a fixed date/time (timeDate) – please verify if this is intended",
                        ValidationType.BPMN_FLOATING_ELEMENT, ValidationSeverity.INFO
                ));
            }
            else if (!isTimeCycleEmpty || !isTimeDurationEmpty)
            {
                String timerValue = !isTimeCycleEmpty ? timeCycleExpr.getTextContent()
                        : timeDurationExpr.getTextContent();
                if (!containsVersionPlaceholder(timerValue))
                {
                    issues.add(new BpmnFloatingElementValidationItem(
                            elementId, bpmnFile, processId,
                            "Timer value appears fixed (no version placeholder found)",
                            ValidationType.BPMN_FLOATING_ELEMENT
                    ));
                }
            }
        }
    }

    // SIGNAL INTERMEDIATE CATCH EVENT VALIDATION

    /**
     * Validates an {@link IntermediateCatchEvent} containing a {@link SignalEventDefinition}.
     * Checks non-empty name and non-empty signal.
     *
     * @param catchEvent
     *         the signal IntermediateCatchEvent
     * @param issues
     *         the validation collector
     * @param bpmnFile
     *         BPMN file reference
     * @param processId
     *         process id
     */
/*
    private void validateSignalIntermediateCatchEvent(IntermediateCatchEvent catchEvent,
                                                      List<BpmnElementValidationItem> issues,
                                                      File bpmnFile,
                                                      String processId)
    {
        String elementId = catchEvent.getId();

        if (isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Catch Event name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        SignalEventDefinition def =
                (SignalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || isEmpty(def.getSignal().getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal is empty in Signal Intermediate Catch Event",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
    }

    // SIGNAL INTERMEDIATE THROW EVENT VALIDATION
    /**
     * Validates an {@link IntermediateThrowEvent} containing a {@link SignalEventDefinition}.
     * Checks the non-empty name and signal presence.
     *
     * @param throwEvent
     *         the signal IntermediateThrowEvent
     * @param issues
     *         validation issue collector
     * @param bpmnFile
     *         reference to BPMN file
     * @param processId
     *         process id
     */
/*
    private void validateSignalIntermediateThrowEvent(IntermediateThrowEvent throwEvent,
                                                      List<BpmnElementValidationItem> issues,
                                                      File bpmnFile,
                                                      String processId)
    {
        String elementId = throwEvent.getId();

        if (isEmpty(throwEvent.getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Throw Event name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        SignalEventDefinition def =
                (SignalEventDefinition) throwEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || isEmpty(def.getSignal().getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal is empty in Signal Intermediate Throw Event",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
    }

    // SIGNAL END EVENT VALIDATION
    /**
     * Validates an {@link EndEvent} containing a {@link SignalEventDefinition}.
     * Ensures the event name and signal name are not empty.
     *
     * @param endEvent
     *         the signal EndEvent
     * @param issues
     *         validation item list
     * @param bpmnFile
     *         reference to BPMN file
     * @param processId
     *         process identifier
     */
/*
    private void validateSignalEndEvent(EndEvent endEvent,
                                        List<BpmnElementValidationItem> issues,
                                        File bpmnFile,
                                        String processId)
    {
        String elementId = endEvent.getId();

        if (isEmpty(endEvent.getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal End Event name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        SignalEventDefinition def =
                (SignalEventDefinition) endEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || isEmpty(def.getSignal().getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Signal is empty in Signal End Event",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
    }

    // USER TASK VALIDATION
    /**
     * Validates a {@link UserTask}, checking:
     * <ul>
     *   <li>Non-empty task name</li>
     *   <li>Non-empty {@code camunda:formKey} that starts with {@code "external:"}</li>
     *   <li>If the formKey references a known questionnaire resource (simple substring check in this example)</li>
     *   <li>Any task listeners referencing a Java class must exist on the classpath</li>
     * </ul>
     *
     * @param userTask
     *         the {@link UserTask} to validate
     * @param issues
     *         the list of validation items
     * @param bpmnFile
     *         reference to BPMN file
     * @param processId
     *         process id
     */
/*
    private void validateUserTask(UserTask userTask,
                                  List<BpmnElementValidationItem> issues,
                                  File bpmnFile,
                                  String processId)
    {
        String elementId = userTask.getId();

        if (isEmpty(userTask.getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "User Task name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        String formKey = userTask.getCamundaFormKey();
        if (isEmpty(formKey))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "User Task formKey is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
        else
        {
            if (!formKey.startsWith("external:"))
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "User Task formKey is not an external form: " + formKey,
                        ValidationType.BPMN_FLOATING_ELEMENT
                ));
            }
            // Example check to verify if the corresponding questionnaire
            if (!questionnaireExists(formKey))
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "User Task questionnaire not found for formKey: " + formKey,
                        ValidationType.BPMN_FLOATING_ELEMENT
                ));
            }
        }

        // Check task listeners
        if (userTask.getExtensionElements() != null)
        {
            Collection<CamundaTaskListener> listeners =
                    userTask.getExtensionElements().getElementsQuery()
                            .filterByType(CamundaTaskListener.class)
                            .list();
            for (CamundaTaskListener listener : listeners)
            {
                String implClass = listener.getCamundaClass();
                if (!isEmpty(implClass) && !classExists(implClass))
                {
                    issues.add(new BpmnFloatingElementValidationItem(
                            elementId, bpmnFile, processId,
                            "Task listener class not found: " + implClass,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND
                    ));
                }
            }
        }
    }

    // SEND TASK VALIDATION
    /**
     * Validates a {@link SendTask}, checking:
     * <ul>
     *   <li>Non-empty name</li>
     *   <li>Implementation class: non-empty, exists, implements {@code JavaDelegate}</li>
     *   <li>Field injections for {@code profile}, {@code messageName}, and
     *       {@code instantiatesCanonical}</li>
     * </ul>
     *
     * @param sendTask
     *         the {@link SendTask} to validate
     * @param issues
     *         list collecting validation items
     * @param bpmnFile
     *         reference to the BPMN file
     * @param processId
     *         process id
     */
/*
    private void validateSendTask(SendTask sendTask,
                                  List<BpmnElementValidationItem> issues,
                                  File bpmnFile,
                                  String processId)
    {
        String elementId = sendTask.getId();

        if (isEmpty(sendTask.getName()))
        {
            issues.add(new BpmnEventNameEmptyValidationItem(elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        }

        String implClass = sendTask.getCamundaClass();
        if (isEmpty(implClass))
        {
            issues.add(new BpmnMessageSendTaskImplementationClassEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            if (!classExists(implClass))
            {
                issues.add(new BpmnMessageSendTaskImplementationClassNotFoundValidationItem(
                        elementId, bpmnFile, processId, implClass));
            }
            else if (!implementsJavaDelegate(implClass))
            {
                issues.add(new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateValidationItem(
                        elementId, bpmnFile, processId, implClass));
            }
        }
        // Validate field injections (same as in MESSAGE INTERMEDIATE THROW EVENT)
        validateMessageSendFieldInjections(sendTask, issues, bpmnFile, processId);
    }

    // RECEIVE TASK VALIDATION
    /**
     * Validates a {@link ReceiveTask}, checking:
     * <ul>
     *   <li>Non-empty name</li>
     *   <li>Non-empty message definition {@code message.getName()}</li>
     *   <li>{@code message.getName()} must appear in ActivityDefinition/StructureDefinition</li>
     * </ul>
     *
     * @param receiveTask
     *         the {@link ReceiveTask} to validate
     * @param issues
     *         the validation collector
     * @param bpmnFile
     *         reference to BPMN file
     * @param processId
     *         process id
     */
/*
    private void validateReceiveTask(ReceiveTask receiveTask,
                                     List<BpmnElementValidationItem> issues,
                                     File bpmnFile,
                                     String processId)
    {
        String elementId = receiveTask.getId();

        if (isEmpty(receiveTask.getName()))
        {
            issues.add(new BpmnEventNameEmptyValidationItem (elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }

        if (receiveTask.getMessage() == null || isEmpty(receiveTask.getMessage().getName()))
        {
            issues.add(new BpmnMessageStartEventMessageNameEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            checkMessageName(receiveTask.getMessage().getName(), issues, elementId, bpmnFile, processId);
        }
    }

    // SUB PROCESS VALIDATION
    /**
     * Validates a {@link SubProcess}, checking if it has multi-instance loop
     * characteristics and if so, enforcing {@code camunda:asyncBefore=true}.
     *
     * @param subProcess
     *         the {@link SubProcess} to validate
     * @param issues
     *         list of validation items
     * @param bpmnFile
     *         BPMN file reference
     * @param processId
     *         process identifier
     */
/*
    private void validateSubProcess(SubProcess subProcess,
                                    List<BpmnElementValidationItem> issues,
                                    File bpmnFile,
                                    String processId)
    {
        String elementId = subProcess.getId();

        if (subProcess.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics multi)
        {
            if (!multi.isCamundaAsyncBefore())
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "SubProcess has multi-instance but is not asyncBefore=true",
                        ValidationType.BPMN_FLOATING_ELEMENT
                ));
            }
        }
    }

    // EVENT BASED GATEWAY VALIDATION
    /**
     * Validates an {@link EventBasedGateway} by checking for extension elements
     * (execution listeners) that specify a Java class, verifying that each class
     * can be found on the classpath.
     *
     * @param gateway
     *         the {@link EventBasedGateway} to validate
     * @param issues
     *         the validation issues collector
     * @param bpmnFile
     *         reference to BPMN file
     * @param processId
     *         process id
     */
/*
    private void validateEventBasedGateway(EventBasedGateway gateway,
                                           List<BpmnElementValidationItem> issues,
                                           File bpmnFile,
                                           String processId)
    {
        String elementId = gateway.getId();

        if (gateway.getExtensionElements() != null)
        {
            Collection<CamundaExecutionListener> listeners =
                    gateway.getExtensionElements().getElementsQuery()
                            .filterByType(CamundaExecutionListener.class)
                            .list();
            for (CamundaExecutionListener listener : listeners)
            {
                String implClass = listener.getCamundaClass();
                if (!isEmpty(implClass) && !classExists(implClass))
                {
                    issues.add(new BpmnFloatingElementValidationItem(
                            elementId, bpmnFile, processId,
                            "Execution listener class not found for Event Based Gateway: " + implClass,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND
                    ));
                }
            }
        }
    }

    // CONDITIONAL INTERMEDIATE CATCH EVENT VALIDATION
    /**
     * Validates an {@link IntermediateCatchEvent} containing a {@link ConditionalEventDefinition}.
     * Checks that:
     * <ul>
     *   <li>The event name is not empty</li>
     *   <li>{@code camunda:variableName} is set</li>
     *   <li>{@code camunda:variableEvents} is set</li>
     *   <li>{@code camunda:conditionType} is set and typically equals {@code "expression"}</li>
     *   <li>A non-empty condition expression is present</li>
     * </ul>
     *
     * @param catchEvent
     *         the IntermediateCatchEvent to validate
     * @param issues
     *         the validation list
     * @param bpmnFile
     *         reference to BPMN file
     * @param processId
     *         process identifier
     */
/*
    private void validateConditionalIntermediateCatchEvent(IntermediateCatchEvent catchEvent,
                                                           List<BpmnElementValidationItem> issues,
                                                           File bpmnFile,
                                                           String processId)
    {
        String elementId = catchEvent.getId();

        if (isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        ConditionalEventDefinition condDef =
                (ConditionalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        // Condition - Variable Name
        String variableName = condDef.getCamundaVariableName();
        if (isEmpty(variableName))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variable name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        // Condition - Events
        String variableEvents = condDef.getAttributeValueNs("http://camunda.org/schema/1.0/bpmn", "variableEvents");
        if (isEmpty(variableEvents))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variableEvents is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        // Condition - Type
        String conditionType = condDef.getAttributeValueNs("http://camunda.org/schema/1.0/bpmn", "conditionType");
        if (isEmpty(conditionType))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event condition type is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
        else if (!"expression".equalsIgnoreCase(conditionType))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event condition type is not 'expression': " + conditionType,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.INFO
            ));
        }

        // Condition - Expression
        if (condDef.getCondition() == null || isEmpty(condDef.getCondition().getRawTextContent()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event expression is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
    }

    // FIELD INJECTIONS VALIDATION (for Message Send Tasks/Events)
    /**
     * Validates all field injections (e.g., {@code <camunda:field>}) found directly
     * on a {@link BaseElement} or nested under a {@link MessageEventDefinition}.
     * Checks the presence and correctness of:
     * <ul>
     *   <li>{@code profile}</li>
     *   <li>{@code messageName}</li>
     *   <li>{@code instantiatesCanonical}</li>
     * </ul>
     * Cross-references the {@link FhirValidator} to ensure these values are recognized
     * in appropriate FHIR resources (e.g., {@code StructureDefinition}, {@code ActivityDefinition}).
     *
     * @param element
     *         the BPMN element to inspect
     * @param issues
     *         the validation items list
     * @param bpmnFile
     *         the BPMN file reference
     * @param processId
     *         the process identifier
     */
/*
    private void validateMessageSendFieldInjections(
            BaseElement element,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        // 1) Attempt to retrieve <camunda:field> from the BPMN element's ExtensionElements
        ExtensionElements extensionElements = element.getExtensionElements();
        if (hasCamundaFields(extensionElements))
        {
            validateCamundaFields(extensionElements, element.getId(), issues, bpmnFile, processId);
        }

        // 2) For ThrowEvent/EndEvent, also check nested fields in the attached MessageEventDefinition
        Collection<EventDefinition> eventDefinitions = new ArrayList<>();
        if (element instanceof ThrowEvent throwEvent)
        {
            eventDefinitions = throwEvent.getEventDefinitions();
        }
        else if (element instanceof EndEvent endEvent)
        {
            eventDefinitions = endEvent.getEventDefinitions();
        }

        for (EventDefinition eventDef : eventDefinitions)
        {
            if (eventDef instanceof MessageEventDefinition messageDef)
            {
                ExtensionElements msgExtEl = messageDef.getExtensionElements();
                if (hasCamundaFields(msgExtEl))
                {
                    validateCamundaFields(msgExtEl, element.getId(), issues, bpmnFile, processId);
                }
            }
        }
    }

    /**
     * Determines if the given {@link ExtensionElements} object contains any
     * {@code <camunda:field>} elements.
     *
     * @param extensionElements
     *         the extension elements to inspect
     * @return {@code true} if at least one field is present; {@code false} otherwise
     */
/*
    private boolean hasCamundaFields(ExtensionElements extensionElements)
    {
        if (extensionElements == null) return false;
        Collection<CamundaField> fields = extensionElements
                .getElementsQuery()
                .filterByType(CamundaField.class)
                .list();
        return fields != null && !fields.isEmpty();
    }

    /**
     * Parses and validates the {@code <camunda:field>} elements for a BPMN element.
     * <p>
     * Specifically handles the three known fields:
     * <ul>
     *   <li><b>profile</b>: Must be non-empty, warns if no version placeholder, checks
     *       existence in {@code StructureDefinition}, then cross-checks the
     *       {@code messageName} and {@code instantiatesCanonical} usage.</li>
     *   <li><b>messageName</b>: Must be non-empty, cross-checks substring presence in
     *       {@code profile} if found valid in the {@code StructureDefinition}.</li>
     *   <li><b>instantiatesCanonical</b>: Must be non-empty, warns if no version placeholder,
     *       checks for matching {@code ActivityDefinition}, cross-checks the presence of
     *       {@code messageName} in the found {@code ActivityDefinition}, and checks if
     *       the corresponding {@code StructureDefinition} has a {@code fixedCanonical}
     *       referencing the same canonical (if a valid {@code profile} was found).</li>
     * </ul>
     * For other field names, a generic "unknown field" item is added.
     * </p>
     *
     * @param extensionElements
     *         the extension elements container
     * @param elementId
     *         the BPMN element ID
     * @param issues
     *         the validation items collector
     * @param bpmnFile
     *         reference to the BPMN file
     * @param processId
     *         process identifier
     */
/*
    public void validateCamundaFields(
            ExtensionElements extensionElements,
            String elementId,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        // Get all <camunda:field> elements
        Collection<CamundaField> camundaFields = extensionElements
                .getElementsQuery()
                .filterByType(CamundaField.class)
                .list();

        String profileValue = null;
        boolean profileHasPlaceholder = false;
        boolean structureFoundForProfile = false;

        String messageNameValue = null;

        String instantiatesValue = null;
        boolean hasInstantiatesPlaceholder = false;

        // Parse the fields
        for (CamundaField field : camundaFields)
        {
            String fieldName = field.getCamundaName();
            String exprValue = field.getCamundaExpression();
            String literalValue = field.getCamundaStringValue(); // getCamundaStringValue searched only in string-type
            if (literalValue == null || literalValue.trim().isEmpty())
            {
                // Try to read nested <camunda:string> content
                literalValue = tryReadNestedStringContent(field);
            }

            // If expression is used => error
            if (exprValue != null && !exprValue.isBlank())
            {
                issues.add(new BpmnFieldInjectionNotStringLiteralValidationItem(
                        elementId, bpmnFile, processId, fieldName));
                continue;
            }

            if (literalValue != null)
            {
                literalValue = literalValue.trim();
            }

            switch (fieldName)
            {
                case "profile":
                {
                    if (isEmpty(literalValue))
                    {
                        issues.add(new BpmnFieldInjectionProfileEmptyValidationItem(elementId, bpmnFile, processId));
                        break;
                    }
                    profileHasPlaceholder = containsVersionPlaceholder(literalValue);
                    if (!profileHasPlaceholder)
                    {
                        issues.add(new BpmnFieldInjectionProfileNoVersionPlaceholderValidationItem(
                                elementId, bpmnFile, processId, literalValue));
                    }
                    profileValue = literalValue;

                    boolean structExists = FhirValidator.structureDefinitionExists(profileValue, projectRoot);
                    if (!structExists)
                    {
                        issues.add(new BpmnFieldInjectionMissingStructureDefinitionForProfileValidationItem(
                                elementId, bpmnFile, processId,
                                "Profile not found in StructureDefinition: " + profileValue));
                    }
                    else
                    {
                        structureFoundForProfile = true;
                    }
                    break;
                }
                case "messageName":
                {
                    if (isEmpty(literalValue))
                    {
                        issues.add(new BpmnFieldInjectionMessageValueEmptyValidationItem(
                                elementId, bpmnFile, processId));
                        break;
                    }
                    messageNameValue = literalValue;
                    break;
                }
                case "instantiatesCanonical":
                {
                    if (isEmpty(literalValue))
                    {
                        issues.add(new BpmnFieldInjectionInstantiatesCanonicalEmptyValidationItem(
                                elementId, bpmnFile, processId));
                        break;
                    }
                    instantiatesValue = literalValue;
                    hasInstantiatesPlaceholder = containsVersionPlaceholder(literalValue);
                    if (!hasInstantiatesPlaceholder)
                    {
                        issues.add(new BpmnFieldInjectionInstantiatesCanonicalNoVersionPlaceholderValidationItem(
                                elementId, bpmnFile, processId));
                    }
                    break;
                }
                default:
                {
                    issues.add(new BpmnUnknownFieldInjectionValidationItem(
                            elementId, bpmnFile, processId, fieldName));
                    break;
                }
            }
        }

        // Second pass: cross-check logic
        if (structureFoundForProfile && !isEmpty(profileValue))
        {
            if (!isEmpty(messageNameValue) && !doesProfileContainMessageNameParts(profileValue, messageNameValue)) {
                issues.add(new BpmnFieldInjectionMessageValueNotPresentInProfileValueValidationItem(
                        elementId, bpmnFile, processId,
                        "The 'messageName' value [" + messageNameValue
                                + "] is not contained in the 'profile' [" + profileValue + "]."
                ));
            }

            if (!isEmpty(instantiatesValue))
            {

                boolean hasFixedCanonical = FhirValidator.structureDefinitionHasFixedCanonical(instantiatesValue, projectRoot);
                if (!hasFixedCanonical)
                {
                    issues.add(new BpmnFieldInjectionInstantiatesCanonicalNotInStructureDefinitionValidationItem(
                            elementId, bpmnFile, processId,
                            "The StructureDefinition does not reference instantiatesCanonical: " + instantiatesValue
                    ));
                }
            }
        }

        if (!isEmpty(instantiatesValue))
        {

            boolean foundInActDef = FhirValidator.activityDefinitionExistsForInstantiatesCanonical(instantiatesValue, projectRoot);
            if (!foundInActDef)
            {
                issues.add(new BpmnFieldInjectionInstantiatesCanonicalNotInActivityDefinitionValidationItem(
                        elementId, bpmnFile, processId,
                        "instantiatesCanonical not found in any ActivityDefinition: " + instantiatesValue
                ));
            }
            else
            {
                if (!isEmpty(messageNameValue))
                {
                    boolean hasMsgName = FhirValidator.activityDefinitionHasMessageName(
                            messageNameValue, projectRoot);
                    if (!hasMsgName)
                    {
                        issues.add(new BpmnFieldInjectionMessageValueNotPresentInActivityDefinitionValidationItem (
                                elementId, bpmnFile, processId,
                                "ActivityDefinition found for canonical " + instantiatesValue
                                        + " but the message value is not present in any ActivityDefinition '" + messageNameValue + "'"
                        ));
                    }
                }
            }
        }
    }

    /**
     * Attempts to read any nested {@code <camunda:string>} text content if the
     * {@code camunda:stringValue} is not set directly on the {@code CamundaField}.
     *
     * @param field
     *         the {@link CamundaField} whose nested text might be extracted
     * @return the text content of the nested {@code <camunda:string>}, or null if not found
     */
/*
    private String tryReadNestedStringContent(CamundaField field)
    {
        if (field == null) return null;
        DomElement domEl = field.getDomElement();
        if (domEl != null)
        {
            Collection<DomElement> childEls = domEl.getChildElements();
            for (DomElement child : childEls)
            {
                if ("string".equals(child.getLocalName())
                        && "http://camunda.org/schema/1.0/bpmn".equals(child.getNamespaceURI()))
                {
                    return child.getTextContent();
                }
            }
        }
        return null;
    }

    /**
     * Validates that a specified {@code messageName} is found in FHIR ActivityDefinition
     * and StructureDefinition. If not present, adds corresponding validation errors.
     *
     * @param messageName
     *         the message name to check
     * @param issues
     *         the issues collector
     * @param elementId
     *         the BPMN element ID
     * @param bpmnFile
     *         reference to BPMN file
     * @param processId
     *         process identifier
     */
/*
    private void checkMessageName(String messageName,
                                  List<BpmnElementValidationItem> issues,
                                  String elementId,
                                  File bpmnFile,
                                  String processId)
    {
        boolean actDefExists = FhirValidator.activityDefinitionExists(messageName, projectRoot);
        boolean structDefExists = FhirValidator.structureDefinitionExists(messageName, projectRoot);

        if (!actDefExists)
        {
            issues.add(new BpmnMessageSendEventMessageNameNotPresentInActivityDefinitionValidationItem(
                    elementId, bpmnFile, processId, messageName));
        }
        if (!structDefExists)
        {
            issues.add(new BpmnMessageSendEventMessageNameNotMatchingProfileValidationItem(
                    elementId, bpmnFile, processId, "", messageName));
        }
    }

    /**
     * Extracts the {@code camunda:class} attribute from a BPMN event, searching both the
     * element itself and any attached {@link MessageEventDefinition}.
     *
     * @param element
     *         the BPMN element
     * @return the class name if found, otherwise an empty string
     */
/*
    private String extractImplementationClass(BaseElement element)
    {
        String implClass = element.getAttributeValueNs("class", "http://camunda.org/schema/1.0/bpmn");
        if (!isEmpty(implClass)) return implClass;

        if (element instanceof ThrowEvent throwEvent)
        {
            for (EventDefinition def : throwEvent.getEventDefinitions())
            {
                if (def instanceof MessageEventDefinition messageDef)
                {
                    implClass = messageDef.getCamundaClass();
                    if (!isEmpty(implClass))
                    {
                        return implClass;
                    }
                }
            }
        }
        else if (element instanceof EndEvent endEvent)
        {
            for (EventDefinition def : endEvent.getEventDefinitions())
            {
                if (def instanceof MessageEventDefinition messageDef)
                {
                    implClass = messageDef.getCamundaClass();
                    if (!isEmpty(implClass))
                    {
                        return implClass;
                    }
                }
            }
        }
        return "";
    }

    /**
     * Validates the message definition of a BPMN element (start/boundary/catch/throw/end event)
     * by checking if the {@code messageName} is non-empty and recognized in FHIR resources.
     *
     * @param element
     *         the BPMN element
     * @param messageDef
     *         the {@link MessageEventDefinition} to validate
     * @param issues
     *         validation items collector
     * @param bpmnFile
     *         BPMN file reference
     * @param processId
     *         process id
     */
/*
    private void validateMessageDefinition(FlowElement element,
                                           MessageEventDefinition messageDef,
                                           List<BpmnElementValidationItem> issues,
                                           File bpmnFile,
                                           String processId)
    {
        String elementId = element.getId();
        if (messageDef.getMessage() == null || isEmpty(messageDef.getMessage().getName()))
        {
            issues.add(new BpmnMessageStartEventMessageNameEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            // Check if an ActivityDefinition exists for the message name.
            String msgName = messageDef.getMessage().getName();
            if (!FhirValidator.activityDefinitionExists(msgName, projectRoot))
            {
                issues.add(new BpmnMessageStartEventMessageNameNotPresentInActivityDefinitionValidationItem(
                        elementId, bpmnFile, processId, msgName));
            }
            // Check if a StructureDefinition exists for the message name.
            if (!FhirValidator.structureDefinitionExists(msgName, projectRoot))
            {
                issues.add(new BpmnMessageStartEventMessageNameNotMatchingProfileValidationItem(
                        elementId, bpmnFile, processId, msgName));
            }
        }
    }

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
     *
     * @param event
     *         the BPMN flow element
     * @param issues
     *         the validation collector
     * @param bpmnFile
     *         reference to BPMN file
     * @param processId
     *         process identifier
     */
/*
    private void validateCommonMessageEvent(FlowElement event,
                                            List<BpmnElementValidationItem> issues,
                                            File bpmnFile,
                                            String processId)
    {
        String elementId = event.getId();
        if (isEmpty(event.getName()))
        {
            issues.add(new BpmnEventNameEmptyValidationItem(elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        }
        String implClass = extractImplementationClass(event);
        validateImplementationClass(implClass, elementId, bpmnFile, processId, issues);
        validateMessageSendFieldInjections(event, issues, bpmnFile, processId);
    }

    /**
     * Checks whether a formKey (for a {@link UserTask}) presumably references an existing
     * questionnaire. In this example, we simply check for the substring "questionnaire."
     *
     * @param formKey
     *         the form key from the BPMN user task
     * @return true if the substring "questionnaire" is found, false otherwise
     */
/*
    private boolean questionnaireExists(String formKey)
    {
        return formKey.contains("questionnaire");
    }

    /**
     * Determines whether the provided string is null or empty.
     *
     * @param value
     *         the string to check
     * @return {@code true} if the string is null or empty, {@code false} otherwise
     */
/*
    private boolean isEmpty(String value)
    {
        return (value == null || value.trim().isEmpty());
    }

    /**
     * Checks if a string contains the {@code #{version}} placeholder.
     *
     * @param rawValue
     *         the string to check
     * @return {@code true} if {@code #{version}} is present, {@code false} otherwise
     */
/*
    private boolean containsVersionPlaceholder(String rawValue)
    {
        return (rawValue != null && rawValue.contains("#{version}"));
    }


    /**
     * Checks if a fully-qualified class name can be loaded from:
     * <ol>
     *   <li>The current thread context ClassLoader</li>
     *   <li>A fallback {@link URLClassLoader} built from {@code target/classes} or
     *       {@code build/classes} plus the {@code target/dependency} JARs</li>
     * </ol>
     *
     * @param className
     *         the fully qualified name (e.g., "com.example.MyDelegate")
     * @return {@code true} if the class is found, {@code false} otherwise
     */
/*
    private boolean classExists(String className)
    {
        try
        {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            cl.loadClass(className);
            return true;
        }
        catch (ClassNotFoundException e)
        {
            // fallback
        }
        catch (Throwable t)
        {
            System.err.println("DEBUG: Throwable while trying context CL: " + t.getMessage());
        }

        if (projectRoot != null)
        {
            try
            {
                File classesDir = new File(projectRoot, "target/classes");
                if (!classesDir.exists())
                {
                    classesDir = new File(projectRoot, "build/classes");
                }

                List<URL> urlList = new ArrayList<>();
                if (classesDir.exists())
                {
                    urlList.add(classesDir.toURI().toURL());
                }

                File dependencyDir = new File(projectRoot, "target/dependency");
                if (dependencyDir.exists() && dependencyDir.isDirectory())
                {
                    File[] jars = dependencyDir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
                    if (jars != null)
                    {
                        for (File jar : jars)
                        {
                            urlList.add(jar.toURI().toURL());
                        }
                    }
                }

                URL[] urls = urlList.toArray(new URL[0]);
                ClassLoader urlCl = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
                urlCl.loadClass(className);
                return true;
            }
            catch (Throwable t)
            {
                System.err.println("DEBUG: Throwable while custom loading " + className + ": " + t.getMessage());
            }
        }
        return false;
    }

    /**
     * Checks if the given class name implements {@code org.camunda.bpm.engine.delegate.JavaDelegate}.
     *
     * @param className
     *         the fully qualified name of the class to check
     * @return {@code true} if the class implements {@code JavaDelegate}, otherwise {@code false}
     */
/*
    private boolean implementsJavaDelegate(String className)
    {
        try
        {
            File classesDir = new File(projectRoot, "target/classes");
            if (!classesDir.exists())
            {
                classesDir = new File(projectRoot, "build/classes");
            }

            List<URL> urlList = new ArrayList<>();
            if (classesDir.exists())
            {
                urlList.add(classesDir.toURI().toURL());
            }

            File dependencyDir = new File(projectRoot, "target/dependency");
            if (dependencyDir.exists() && dependencyDir.isDirectory())
            {
                File[] jars = dependencyDir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
                if (jars != null)
                {
                    for (File jar : jars)
                    {
                        urlList.add(jar.toURI().toURL());
                    }
                }
            }

            URL[] urls = urlList.toArray(new URL[0]);
            ClassLoader customCl = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());

            Class<?> candidateClass = Class.forName(className, true, customCl);
            Class<?> delegateInterface = Class.forName("org.camunda.bpm.engine.delegate.JavaDelegate", true, customCl);

            return delegateInterface.isAssignableFrom(candidateClass);
        }
        catch (Throwable t)
        {
            System.err.println("DEBUG: Exception in implementsJavaDelegate for " + className + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * Checks if all parts of the given message name, split at uppercase letters and lowercased,
     * are present in the profile value in the same order.
     *
     * <p>For example, given:
     * <ul>
     *   <li>profileValue: "http://dsf.dev/fhir/StructureDefinition/task-start-ping|#{version}"</li>
     *   <li>messageNameValue: "startPing"</li>
     * </ul>
     * The method will split "startPing" into ["start", "Ping"], convert them to ["start", "ping"],
     * and then verify that "start" occurs before "ping" within the profileValue (ignoring case).
     *
     * @param profileValue     The profile string to search within.
     * @param messageNameValue The camelCase message name to check.
     * @return {@code true} if all parts of the message name are found sequentially in the profile; {@code false} otherwise.
     */
/*
    private boolean doesProfileContainMessageNameParts(String profileValue, String messageNameValue) {
        if (profileValue == null || messageNameValue == null) {
            return false;
        }

        // Split the message name at positions preceding uppercase letters (camelCase splitting)
        // For example, "startPing" becomes ["start", "Ping"].
        String[] parts = messageNameValue.split("(?=[A-Z])");

        // Convert the profile to lower case for case-insensitive matching.
        String profileLower = profileValue.toLowerCase();
        int lastFoundIndex = -1;

        // Check each part sequentially.
        for (String part : parts) {
            // Convert each part to lower case.
            String lowerPart = part.toLowerCase();
            // Find the part in the profile, starting after the previous part's index.
            int foundIndex = profileLower.indexOf(lowerPart, lastFoundIndex + 1);
            if (foundIndex == -1) {
                // The part was not found in the profile value.
                return false;
            }
            lastFoundIndex = foundIndex;
        }

        return true;
    }
}

 */