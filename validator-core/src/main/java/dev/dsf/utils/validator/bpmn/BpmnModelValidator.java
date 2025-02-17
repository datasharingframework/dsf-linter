package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaField;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class BpmnModelValidator
{
    // 1) Known "message-name" extracted from ActivityDefinitions
    // we have to add the rest
    private static final Set<String> KNOWN_MESSAGE_NAMES = new HashSet<>(Arrays.asList(
            "startPingAutostart",
            "stopPingAutostart",
            "startPing",
            "stopPing",
            "ping",
            "pong"
    ));

    // 2) Known "StructureDefinition" URLs extracted from your list

    // For instance, from our provided StructureDefinition resources:
    // - http://dsf.dev/fhir/StructureDefinition/task-start-ping-autostart|#{version}
    // - http://dsf.dev/fhir/StructureDefinition/task-stop-ping-autostart|#{version}
    // - http://dsf.dev/fhir/StructureDefinition/task-ping|#{version}
    // - http://dsf.dev/fhir/StructureDefinition/task-pong|#{version}
    // - http://dsf.dev/fhir/StructureDefinition/extension-ping-status|#{version}
    // ... etc.
    // we have to add the rest
    private static final Set<String> KNOWN_STRUCTURE_DEF_URLS = new HashSet<>(Arrays.asList(
            "http://dsf.dev/fhir/StructureDefinition/task-start-ping-autostart",
            "http://dsf.dev/fhir/StructureDefinition/task-stop-ping-autostart",
            "http://dsf.dev/fhir/StructureDefinition/task-ping",
            "http://dsf.dev/fhir/StructureDefinition/task-pong",
            "http://dsf.dev/fhir/StructureDefinition/extension-ping-status",
            "http://dsf.dev/fhir/StructureDefinition/task-start-ping",
            "http://dsf.dev/fhir/StructureDefinition/task-stop-ping",

            "http://dsf.dev/fhir/StructureDefinition/task-base"
    ));


    /**
     * Validate the entire BPMN model and return a list of issues found.
     */
    public List<BpmnIssue> validateModel(BpmnModelInstance model)
    {
        List<BpmnIssue> issues = new ArrayList<>();

        Collection<FlowElement> flowElements =
                model.getModelElementsByType(FlowElement.class);

        for (FlowElement element : flowElements)
        {
            if (element instanceof ServiceTask)
            {
                validateServiceTask((ServiceTask) element, issues);
            }
            else if (element instanceof StartEvent startEvent)
            {
                if (!startEvent.getEventDefinitions().isEmpty()
                        && startEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition)
                {
                    validateMessageStartEvent(startEvent, issues);
                }
                else
                {
                    validateStartEvent(startEvent, issues);
                }
            }
            else if (element instanceof IntermediateThrowEvent throwEvent)
            {
                if (!throwEvent.getEventDefinitions().isEmpty()
                        && throwEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition)
                {
                    validateMessageIntermediateThrowEvent(throwEvent, issues);
                }
                else if (!throwEvent.getEventDefinitions().isEmpty()
                        && throwEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition)
                {
                    validateSignalIntermediateThrowEvent(throwEvent, issues);
                }
            }
            else if (element instanceof IntermediateCatchEvent catchEvent)
            {
                if (!catchEvent.getEventDefinitions().isEmpty()
                        && catchEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition)
                {
                    validateMessageIntermediateCatchEvent(catchEvent, issues);
                }
                else if (!catchEvent.getEventDefinitions().isEmpty()
                        && catchEvent.getEventDefinitions().iterator().next() instanceof TimerEventDefinition)
                {
                    validateTimerIntermediateCatchEvent(catchEvent, issues);
                }
                else if (!catchEvent.getEventDefinitions().isEmpty()
                        && catchEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition)
                {
                    validateSignalIntermediateCatchEvent(catchEvent, issues);
                }
                else if (!catchEvent.getEventDefinitions().isEmpty()
                        && catchEvent.getEventDefinitions().iterator().next() instanceof ConditionalEventDefinition)
                {
                    validateConditionalIntermediateCatchEvent(catchEvent, issues);
                }
            }
            else if (element instanceof EndEvent endEvent)
            {
                if (!endEvent.getEventDefinitions().isEmpty()
                        && endEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition)
                {
                    validateMessageEndEvent(endEvent, issues);
                }
                else if (!endEvent.getEventDefinitions().isEmpty()
                        && endEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition)
                {
                    validateSignalEndEvent(endEvent, issues);
                }
                else
                {
                    validateEndEvent(endEvent, issues);
                }
            }
            else if (element instanceof BoundaryEvent boundaryEvent)
            {
                if (!boundaryEvent.getEventDefinitions().isEmpty()
                        && boundaryEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition)
                {
                    validateMessageBoundaryEvent(boundaryEvent, issues);
                }
                else if (!boundaryEvent.getEventDefinitions().isEmpty()
                        && boundaryEvent.getEventDefinitions().iterator().next() instanceof ErrorEventDefinition)
                {
                    validateErrorBoundaryEvent(boundaryEvent, issues);
                }
            }
            else if (element instanceof ExclusiveGateway)
            {
                validateExclusiveGateway((ExclusiveGateway) element, issues);
            }
            else if (element instanceof SequenceFlow)
            {
                validateSequenceFlow((SequenceFlow) element, issues);
            }
            else if (element instanceof UserTask)
            {
                validateUserTask((UserTask) element, issues);
            }
            else if (element instanceof SendTask)
            {
                validateSendTask((SendTask) element, issues);
            }
            else if (element instanceof ReceiveTask)
            {
                validateReceiveTask((ReceiveTask) element, issues);
            }
            else if (element instanceof SubProcess)
            {
                validateSubProcess((SubProcess) element, issues);
            }
            else if (element instanceof EventBasedGateway)
            {
                validateEventBasedGateway((EventBasedGateway) element, issues);
            }
        }

        return issues;
    }

    // SERVICE TASK
    private void validateServiceTask(ServiceTask task, List<BpmnIssue> issues)
    {
        if (isEmpty(task.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_EMPTY,
                    "Service Task name is empty"
            ));
        }

        String implClass = task.getCamundaClass();
        if (isEmpty(implClass))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_EMPTY,
                    "Implementation class is missing for Service Task"
            ));
        }
        else
        {
            if (!classExists(implClass))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.ERROR,
                        ValidationType.BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_NOT_FOUND,
                        "Implementation class '" + implClass + "' not found"
                ));
            }
            else
            {
                // Check if JavaDelegate
                if (!implementsJavaDelegate(implClass))
                {
                    issues.add(new BpmnIssue(
                            ValidationSeverity.ERROR,
                            ValidationType.BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_NOT_FOUND,
                            "Implementation class '" + implClass + "' does not implement JavaDelegate"
                    ));
                }
            }
        }
    }

    // MESSAGE START EVENT
    private void validateMessageStartEvent(StartEvent startEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(startEvent.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_EMPTY,
                    "Message Start Event name is empty"
            ));
        }

        MessageEventDefinition messageDef =
                (MessageEventDefinition) startEvent.getEventDefinitions().iterator().next();
        if (messageDef.getMessage() == null || isEmpty(messageDef.getMessage().getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_EMPTY,
                    "Message name is missing in Message Start Event"
            ));
        }
        else
        {
            String messageName = messageDef.getMessage().getName();
            if (!activityDefinitionExists(messageName))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_NOT_PRESENT_IN_ACTIVITY_DEFINITION,
                        "No ActivityDefinition found for message name: " + messageName
                ));
            }
            if (!structureDefinitionExists(messageName))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_NOT_MATCHING_PROFILE,
                        "No StructureDefinition found matching message name: " + messageName
                ));
            }
        }
    }

    private void validateStartEvent(StartEvent startEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(startEvent.getName()))
        {
            if (!(startEvent.getParentElement() instanceof SubProcess))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_PROCESS_ID_MISSING,
                        "Start Event name is empty (not part of a subprocess)"
                ));
            }
        }
    }

    // MESSAGE INTERMEDIATE THROW EVENT
    private void validateMessageIntermediateThrowEvent(IntermediateThrowEvent throwEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(throwEvent.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_MESSAGE_SEND_EVENT_MESSAGE_NAME_EMPTY,
                    "Message Intermediate Throw Event name is empty"
            ));
        }

        String implClass = throwEvent.getAttributeValueNs("class", "http://camunda.org/schema/1.0/bpmn");
        if (isEmpty(implClass))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_EMPTY,
                    "Implementation class is missing for Message Intermediate Throw Event"
            ));
        }
        else
        {
            if (!classExists(implClass))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.ERROR,
                        ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                        "Implementation class '" + implClass + "' not found (Message Intermediate Throw Event)"
                ));
            }
            else
            {
                if (!implementsJavaDelegate(implClass))
                {
                    issues.add(new BpmnIssue(
                            ValidationSeverity.ERROR,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                            "Implementation class '" + implClass + "' does not implement JavaDelegate"
                    ));
                }
            }
        }

        validateMessageSendFieldInjections(throwEvent, issues);

        for (EventDefinition ed : throwEvent.getEventDefinitions())
        {
            if (ed instanceof MessageEventDefinition)
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_MESSAGE_SEND_EVENT_MESSAGE_NAME_EMPTY,
                        "A MessageEventDefinition is present in Message Intermediate Throw Event (warn if present)"
                ));
            }
        }
    }

    // MESSAGE END EVENT
    private void validateMessageEndEvent(EndEvent endEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(endEvent.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_MESSAGE_SEND_EVENT_MESSAGE_NAME_EMPTY,
                    "Message End Event name is empty"
            ));
        }

        String implClass = endEvent.getAttributeValueNs("class", "http://camunda.org/schema/1.0/bpmn");
        if (isEmpty(implClass))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_EMPTY,
                    "Implementation class is missing for Message End Event"
            ));
        }
        else
        {
            if (!classExists(implClass))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.ERROR,
                        ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                        "Implementation class '" + implClass + "' not found (Message End Event)"
                ));
            }
            else
            {
                if (!implementsJavaDelegate(implClass))
                {
                    issues.add(new BpmnIssue(
                            ValidationSeverity.ERROR,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                            "Implementation class '" + implClass + "' does not implement JavaDelegate"
                    ));
                }
            }
        }

        validateMessageSendFieldInjections(endEvent, issues);
    }

    // MESSAGE INTERMEDIATE CATCH EVENT
    private void validateMessageIntermediateCatchEvent(IntermediateCatchEvent catchEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_EMPTY,
                    "Message Intermediate Catch Event name is empty"
            ));
        }

        MessageEventDefinition def =
                (MessageEventDefinition) catchEvent.getEventDefinitions().iterator().next();
        if (def.getMessage() == null || isEmpty(def.getMessage().getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_EMPTY,
                    "Message name is missing in Message Intermediate Catch Event"
            ));
        }
        else
        {
            String msgName = def.getMessage().getName();
            if (!activityDefinitionExists(msgName))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_NOT_PRESENT_IN_ACTIVITY_DEFINITION,
                        "No ActivityDefinition found for message name: " + msgName
                ));
            }
            if (!structureDefinitionExists(msgName))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_NOT_MATCHING_PROFILE,
                        "No StructureDefinition found matching message name: " + msgName
                ));
            }
        }
    }

    // MESSAGE BOUNDARY EVENT
    private void validateMessageBoundaryEvent(BoundaryEvent boundaryEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(boundaryEvent.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_EMPTY,
                    "Message Boundary Event name is empty"
            ));
        }

        MessageEventDefinition def =
                (MessageEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();
        if (def.getMessage() == null || isEmpty(def.getMessage().getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_EMPTY,
                    "Message name is missing in Message Boundary Event"
            ));
        }
        else
        {
            String msgName = def.getMessage().getName();
            if (!activityDefinitionExists(msgName))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_NOT_PRESENT_IN_ACTIVITY_DEFINITION,
                        "No ActivityDefinition found for message name: " + msgName
                ));
            }
            if (!structureDefinitionExists(msgName))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_NOT_MATCHING_PROFILE,
                        "No StructureDefinition found matching message name: " + msgName
                ));
            }
        }
    }

    // EXCLUSIVE GATEWAY
    private void validateExclusiveGateway(ExclusiveGateway gateway, List<BpmnIssue> issues)
    {
        if (gateway.getOutgoing() != null && gateway.getOutgoing().size() > 1)
        {
            if (isEmpty(gateway.getName()))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_SEQUENCE_FLOW_AMBIGUOUS,
                        "Exclusive Gateway name is empty but has multiple outgoing flows"
                ));
            }
        }
    }

    // SEQUENCE FLOW
    private void validateSequenceFlow(SequenceFlow flow, List<BpmnIssue> issues)
    {
        if (flow.getSource() instanceof ExclusiveGateway)
        {
            if (isEmpty(flow.getName()))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_SEQUENCE_FLOW_AMBIGUOUS,
                        "Sequence Flow name is empty and is outgoing flow from an exclusive gateway"
                ));
            }
            if (flow.getConditionExpression() == null
                    && !flow.equals(((ExclusiveGateway) flow.getSource()).getDefault()))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.ERROR,
                        ValidationType.BPMN_SEQUENCE_FLOW_AMBIGUOUS,
                        "Sequence Flow has no condition but is non-default flow from Exclusive Gateway"
                ));
            }
        }
    }

    // END EVENT
    private void validateEndEvent(EndEvent endEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(endEvent.getName()))
        {
            if (!(endEvent.getParentElement() instanceof SubProcess))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_PROCESS_ID_MISSING,
                        "End Event name is empty and not part of a subprocess"
                ));
            }
        }

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
                    issues.add(new BpmnIssue(
                            ValidationSeverity.ERROR,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                            "Execution listener class not found: " + implClass
                    ));
                }
            }
        }
    }

    // TIMER INTERMEDIATE CATCH EVENT
    private void validateTimerIntermediateCatchEvent(IntermediateCatchEvent catchEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Timer Intermediate Catch Event name is empty"
            ));
        }

        TimerEventDefinition timerDef = (TimerEventDefinition) catchEvent.getEventDefinitions().iterator().next();
        if (timerDef.getTimeDate() == null && timerDef.getTimeCycle() == null && timerDef.getTimeDuration() == null)
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Timer type is empty (no timeDate, timeCycle or timeDuration) on Intermediate Catch Event"
            ));
        }
        else if (timerDef.getTimeDate() != null)
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.INFO,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Timer uses timeDate in Intermediate Catch Event"
            ));

            String rawValue = timerDef.getTimeDate().getRawTextContent();
            if (rawValue != null && !rawValue.contains("#{") && !rawValue.contains("${"))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        "Timer value seems to be fixed (no placeholder) in timeDate"
                ));
            }
        }
    }

    // SIGNAL INTERMEDIATE CATCH EVENT
    private void validateSignalIntermediateCatchEvent(IntermediateCatchEvent catchEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Signal Intermediate Catch Event name is empty"
            ));
        }

        SignalEventDefinition def = (SignalEventDefinition) catchEvent.getEventDefinitions().iterator().next();
        if (def.getSignal() == null || isEmpty(def.getSignal().getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Signal is empty in Signal Intermediate Catch Event"
            ));
        }
    }

    // SIGNAL INTERMEDIATE THROW EVENT
    private void validateSignalIntermediateThrowEvent(IntermediateThrowEvent throwEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(throwEvent.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Signal Intermediate Throw Event name is empty"
            ));
        }

        SignalEventDefinition def = (SignalEventDefinition) throwEvent.getEventDefinitions().iterator().next();
        if (def.getSignal() == null || isEmpty(def.getSignal().getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Signal is empty in Signal Intermediate Throw Event"
            ));
        }
    }

    // SIGNAL END EVENT
    private void validateSignalEndEvent(EndEvent endEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(endEvent.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Signal End Event name is empty"
            ));
        }

        SignalEventDefinition def = (SignalEventDefinition) endEvent.getEventDefinitions().iterator().next();
        if (def.getSignal() == null || isEmpty(def.getSignal().getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Signal is empty in Signal End Event"
            ));
        }
    }

    // USER TASK
    private void validateUserTask(UserTask userTask, List<BpmnIssue> issues)
    {
        if (isEmpty(userTask.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "User Task name is empty"
            ));
        }

        String formKey = userTask.getCamundaFormKey();
        if (isEmpty(formKey))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "User Task formKey is empty"
            ));
        }
        else
        {
            if (!formKey.startsWith("external:"))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.ERROR,
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        "User Task formKey is not an external form: " + formKey
                ));
            }
        }

        if (userTask.getExtensionElements() != null)
        {
            Collection<CamundaTaskListener> listeners =
                    userTask.getExtensionElements().getElementsQuery()
                            .filterByType(CamundaTaskListener.class).list();
            for (CamundaTaskListener listener : listeners)
            {
                String implClass = listener.getCamundaClass();
                if (!isEmpty(implClass) && !classExists(implClass))
                {
                    issues.add(new BpmnIssue(
                            ValidationSeverity.ERROR,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                            "Task listener class not found: " + implClass
                    ));
                }
            }
        }
    }

    // ERROR BOUNDARY EVENT
    private void validateErrorBoundaryEvent(BoundaryEvent boundaryEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(boundaryEvent.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Error Boundary Event name is empty"
            ));
        }

        ErrorEventDefinition errorDef = (ErrorEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();
        if (errorDef.getError() != null)
        {
            org.camunda.bpm.model.bpmn.instance.Error theError = errorDef.getError();
            if (isEmpty(theError.getName()))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.ERROR,
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        "Error name is empty in Error Boundary Event"
                ));
            }
            if (isEmpty(theError.getErrorCode()))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.ERROR,
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        "Error code is empty in Error Boundary Event"
                ));
            }
            String codeVariable = errorDef.getAttributeValueNs("errorCodeVariable", "http://camunda.org/schema/1.0/bpmn");
            if (isEmpty(codeVariable))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.ERROR,
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        "Error codeVariable is empty in Error Boundary Event"
                ));
            }
        }
    }

    // SEND TASK
    private void validateSendTask(SendTask sendTask, List<BpmnIssue> issues)
    {
        if (isEmpty(sendTask.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_MESSAGE_SEND_TASK_MESSAGE_NAME_EMPTY,
                    "Send Task name is empty"
            ));
        }

        String implClass = sendTask.getCamundaClass();
        if (isEmpty(implClass))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_MESSAGE_SEND_TASK_IMPLEMENTATION_CLASS_EMPTY,
                    "Implementation class is missing for Send Task"
            ));
        }
        else
        {
            if (!classExists(implClass))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.ERROR,
                        ValidationType.BPMN_MESSAGE_SEND_TASK_IMPLEMENTATION_CLASS_NOT_FOUND,
                        "Implementation class '" + implClass + "' not found (Send Task)"
                ));
            }
            else
            {
                if (!implementsJavaDelegate(implClass))
                {
                    issues.add(new BpmnIssue(
                            ValidationSeverity.ERROR,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                            "Implementation class '" + implClass + "' does not implement JavaDelegate (Send Task)"
                    ));
                }
            }
        }

        // Field injections
        validateMessageSendFieldInjections(sendTask, issues);
    }

    // CONDITIONAL INTERMEDIATE CATCH EVENT
    private void validateConditionalIntermediateCatchEvent(IntermediateCatchEvent catchEvent, List<BpmnIssue> issues)
    {
        if (isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Conditional Intermediate Catch Event name is empty"
            ));
        }

        ConditionalEventDefinition condDef =
                (ConditionalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        String variableName = condDef.getCamundaVariableName();
        if (isEmpty(variableName))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Conditional Intermediate Catch Event variable name is empty"
            ));
        }

        String variableEvents = condDef.getAttributeValueNs("variableEvents", "http://camunda.org/schema/1.0/bpmn");
        if (isEmpty(variableEvents))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Conditional Intermediate Catch Event variableEvents is empty"
            ));
        }

        String conditionType = condDef.getAttributeValueNs("conditionType", "http://camunda.org/schema/1.0/bpmn");
        if (isEmpty(conditionType))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Conditional Intermediate Catch Event condition type is empty"
            ));
        }
        else
        {
            if (!"expression".equalsIgnoreCase(conditionType))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.INFO,
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        "Conditional Intermediate Catch Event condition type is not 'expression': " + conditionType
                ));
            }
        }

        if (condDef.getCondition() == null || isEmpty(condDef.getCondition().getRawTextContent()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    "Conditional Intermediate Catch Event expression is empty"
            ));
        }
    }

    // RECEIVE TASK
    private void validateReceiveTask(ReceiveTask receiveTask, List<BpmnIssue> issues)
    {
        if (isEmpty(receiveTask.getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.WARN,
                    ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_EMPTY,
                    "Receive Task name is empty"
            ));
        }

        if (receiveTask.getMessage() == null || isEmpty(receiveTask.getMessage().getName()))
        {
            issues.add(new BpmnIssue(
                    ValidationSeverity.ERROR,
                    ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_EMPTY,
                    "Message is missing in Receive Task"
            ));
        }
        else
        {
            String msgName = receiveTask.getMessage().getName();
            if (!activityDefinitionExists(msgName))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_NOT_PRESENT_IN_ACTIVITY_DEFINITION,
                        "No ActivityDefinition found for message name: " + msgName
                ));
            }
            if (!structureDefinitionExists(msgName))
            {
                issues.add(new BpmnIssue(
                        ValidationSeverity.WARN,
                        ValidationType.BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_NOT_MATCHING_PROFILE,
                        "No StructureDefinition found matching message name: " + msgName
                ));
            }
        }
    }

    // SUBPROCESS
    private void validateSubProcess(SubProcess subProcess, List<BpmnIssue> issues)
    {
        // template for placeholder for multi-instance checks, etc.
    }

    // EVENT BASED GATEWAY
    private void validateEventBasedGateway(EventBasedGateway gateway, List<BpmnIssue> issues)
    {
        if (gateway.getExtensionElements() != null)
        {
            Collection<CamundaExecutionListener> listeners =
                    gateway.getExtensionElements().getElementsQuery()
                            .filterByType(CamundaExecutionListener.class).list();
            for (CamundaExecutionListener listener : listeners)
            {
                String implClass = listener.getCamundaClass();
                if (!isEmpty(implClass) && !classExists(implClass))
                {
                    issues.add(new BpmnIssue(
                            ValidationSeverity.ERROR,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                            "Execution listener class not found for Event Based Gateway: " + implClass
                    ));
                }
            }
        }
    }


    // FIELD INJECTIONS (profile, messageName, instantiatesCanonical)

    private void validateMessageSendFieldInjections(BaseElement element, List<BpmnIssue> issues)
    {
        if (element.getExtensionElements() == null)
            return;

        Collection<CamundaField> fields = element.getExtensionElements().getElementsQuery()
                .filterByType(CamundaField.class)
                .list();

        boolean profileFound = false;
        boolean activityDefFound = false;
        String profileValue = null;

        for (CamundaField field : fields)
        {
            String fieldName = field.getCamundaName();
            String stringVal = field.getCamundaStringValue();
            String exprVal = field.getCamundaExpression();
            String rawVal = !isEmpty(stringVal) ? stringVal : exprVal;

            if ("profile".equals(fieldName))
            {
                if (isEmpty(rawVal))
                {
                    issues.add(new BpmnIssue(
                            ValidationSeverity.ERROR,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_PROFILE_EMPTY,
                            "'profile' field is empty"
                    ));
                }
                else
                {
                    profileFound = true;
                    profileValue = rawVal;

                    if (!containsVersionPlaceholder(rawVal))
                    {
                        issues.add(new BpmnIssue(
                                ValidationSeverity.WARN,
                                ValidationType.BPMN_MESSAGE_SEND_EVENT_PROFILE_NOT_FOUND,
                                "'profile' does not contain version placeholder"
                        ));
                    }

                    // Check if the profile is in our known structure definitions
                    if (!structureDefinitionExists(rawVal))
                    {
                        issues.add(new BpmnIssue(
                                ValidationSeverity.ERROR,
                                ValidationType.BPMN_MESSAGE_SEND_EVENT_PROFILE_NOT_FOUND,
                                "No recognized StructureDefinition for profile: " + rawVal
                        ));
                    }
                }
            }
            else if ("messageName".equals(fieldName))
            {
                if (isEmpty(rawVal))
                {
                    issues.add(new BpmnIssue(
                            ValidationSeverity.ERROR,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_MESSAGE_NAME_EMPTY,
                            "'messageName' field is empty"
                    ));
                }
                else
                {
                    // Check if the message name is among known ActivityDefinitions
                    if (!activityDefinitionExists(rawVal))
                    {
                        issues.add(new BpmnIssue(
                                ValidationSeverity.ERROR,
                                ValidationType.BPMN_MESSAGE_SEND_EVENT_MESSAGE_NAME_NOT_PRESENT_IN_ACTIVITY_DEFINITION,
                                "messageName '" + rawVal + "' not found in ActivityDefinition"
                        ));
                    }
                    else
                    {
                        activityDefFound = true;
                    }

                    if (profileFound && !messageNameMatchesProfile(profileValue, rawVal))
                    {
                        issues.add(new BpmnIssue(
                                ValidationSeverity.ERROR,
                                ValidationType.BPMN_MESSAGE_SEND_EVENT_MESSAGE_NAME_NOT_MATCHING_PROFILE,
                                "messageName '" + rawVal + "' does not match profile: " + profileValue
                        ));
                    }
                }
            }
            else if ("instantiatesCanonical".equals(fieldName))
            {
                if (isEmpty(rawVal))
                {
                    issues.add(new BpmnIssue(
                            ValidationSeverity.ERROR,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_INSTANTIATES_CANONICAL_EMPTY,
                            "'instantiatesCanonical' field is empty"
                    ));
                }
                else
                {
                    if (!containsVersionPlaceholder(rawVal))
                    {
                        issues.add(new BpmnIssue(
                                ValidationSeverity.WARN,
                                ValidationType.BPMN_MESSAGE_SEND_EVENT_INSTANTIATES_CANONICAL_NOT_MATCHING_PROFILE,
                                "'instantiatesCanonical' does not contain version placeholder"
                        ));
                    }

                    if (!activityDefFound)
                    {
                        issues.add(new BpmnIssue(
                                ValidationSeverity.WARN,
                                ValidationType.BPMN_MESSAGE_SEND_EVENT_MESSAGE_NAME_NOT_PRESENT_IN_ACTIVITY_DEFINITION,
                                "No recognized ActivityDefinition yet for instantiatesCanonical: " + rawVal
                        ));
                    }

                    if (profileFound && !canonicalMatchesProfile(profileValue, rawVal))
                    {
                        issues.add(new BpmnIssue(
                                ValidationSeverity.ERROR,
                                ValidationType.BPMN_MESSAGE_SEND_EVENT_INSTANTIATES_CANONICAL_NOT_MATCHING_PROFILE,
                                "'instantiatesCanonical' does not match the given profile"
                        ));
                    }
                }
            }
        }
    }

    // UTILITY METHODS
    private boolean isEmpty(String value)
    {
        return (value == null || value.trim().isEmpty());
    }

    private boolean classExists(String className)
    {
        try
        {
            Class.forName(className);
            return true;
        }
        catch (ClassNotFoundException e)
        {
            return false;
        }
    }

    private boolean implementsJavaDelegate(String className)
    {
        try
        {
            Class<?> clazz = Class.forName(className);
            Class<?> delegate = Class.forName("org.camunda.bpm.engine.delegate.JavaDelegate");
            return delegate.isAssignableFrom(clazz);
        }
        catch (ClassNotFoundException e)
        {
            return false;
        }
    }

    /**
     * Check if there is a placeholder like '#{version}' in the string.
     */
    private boolean containsVersionPlaceholder(String rawVal)
    {
        return rawVal != null && rawVal.contains("#{version}");
    }

    /**
     * Now references the known message-names from the real ActivityDefinitions you provided.
     */
    private boolean activityDefinitionExists(String messageName)
    {
        if (isEmpty(messageName)) return false;
        // Check against the set of known message names
        return KNOWN_MESSAGE_NAMES.contains(messageName.trim());
    }

    /**
     * Check if the given URL or string references a known structure definition
     * from our FHIR resources. We do a simplistic "starts with" check for the base URL,
     * ignoring the '|#{version}' suffix if any.
     */
    private boolean structureDefinitionExists(String possibleProfileUrl)
    {
        if (isEmpty(possibleProfileUrl)) return false;

        // If the user includes '|#{version}', let's remove that to compare the base
        String base = possibleProfileUrl;
        int pipeIndex = base.indexOf("|");
        if (pipeIndex != -1) {
            base = base.substring(0, pipeIndex);
        }
        return KNOWN_STRUCTURE_DEF_URLS.contains(base);
    }

    /**
     * Example: does the messageName appear in the profile string? Usually you'd do
     * something more advanced (like parse the StructureDefinition and check if the
     * 'fixedString' = messageName).
     */
    private boolean messageNameMatchesProfile(String profileVal, String messageName)
    {
        // remove '|#{version}' from profile if needed
        String base = profileVal;
        int pipeIndex = base.indexOf("|");
        if (pipeIndex != -1) {
            base = base.substring(0, pipeIndex);
        }
        // naive check: if both have "ping" etc. For demonstration only
        return base.toLowerCase().contains(messageName.toLowerCase());
    }

    /**
     * Another naive check: if both contain "ping", "autostart", etc. This is
     * purely for demonstration.
     */
    private boolean canonicalMatchesProfile(String profileVal, String canonicalVal)
    {
        // strip version from both
        String pVal = profileVal;
        int pIdx = pVal.indexOf("|");
        if (pIdx != -1) pVal = pVal.substring(0, pIdx);

        String cVal = canonicalVal;
        int cIdx = cVal.indexOf("|");
        if (cIdx != -1) cVal = cVal.substring(0, cIdx);

        // naive logic
        return pVal.toLowerCase().contains("ping") && cVal.toLowerCase().contains("ping");
    }
}
