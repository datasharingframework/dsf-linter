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

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Validates a Camunda BPMN model according to a predefined set of rules.
 *
 * <p>This validator examines various BPMN elements and checks that they conform
 * to the following requirements:
 * <ul>
 *   <li><b>Service Tasks:</b> Must have a non-empty name and a valid Java class
 *       implementation (i.e., the class must exist and implement the JavaDelegate interface).</li>
 *   <li><b>Message Start Events:</b> Must have a non-empty name and a valid message definition.
 *       The message must correspond to known ActivityDefinition and StructureDefinition artifacts.</li>
 *   <li><b>Message Intermediate Throw Events and Send Tasks:</b> Validate the implementation
 *       class and field injections such as "profile", "messageName", and "instantiatesCanonical".
 *       They generate warnings or errors if required values are missing, if types are incorrect,
 *       if version placeholders are absent, or if the FHIR definitions (ActivityDefinition and
 *       StructureDefinition) do not exist.</li>
 *   <li><b>Message End Events:</b> Follow similar checks as the intermediate throw events,
 *       with additional constraints regarding asynchronous execution for specific scenarios.</li>
 *   <li><b>Message Intermediate Catch and Boundary Events:</b> Ensure that both the name and
 *       message definition are properly specified.</li>
 *   <li><b>Exclusive Gateways and Sequence Flows:</b> Verify that exclusive gateways with multiple
 *       outgoing flows have a name, and that sequence flows originating from exclusive gateways
 *       include necessary condition expressions (unless marked as the default flow).</li>
 *   <li><b>Start and End Events:</b> Require non-empty names unless the event is part of a subprocess.
 *       End events additionally check for asynchronous configuration and, when extension listeners are present,
 *       the existence of the referenced Java classes.</li>
 *   <li><b>Timer Intermediate Catch Events:</b> Check that a timer type is specified via timeDate,
 *       timeCycle, or timeDuration. If a fixed time is used, an informational message is generated;
 *       for cycle or duration timers, the absence of a version placeholder prompts a warning.</li>
 *   <li><b>Signal and Conditional Events:</b> Validate that all required fields (such as signal names or
 *       condition attributes) are provided and correctly configured.</li>
 *   <li><b>User Tasks:</b> Must have a non-empty name and an external form key that begins with
 *       "external:" and corresponds to an existing questionnaire. Task listeners are also checked
 *       to ensure that any specified Java classes exist.</li>
 *   <li><b>Error Boundary Events:</b> Ensure that error events have non-empty error names, error codes,
 *       and a defined code variable.</li>
 *   <li><b>Receive Tasks:</b> Follow similar validation as message start events for names and messages.</li>
 *   <li><b>Expanded SubProcesses and Event Based Gateways:</b> For multi-instance subprocesses, asynchronous
 *       execution is validated, and for event-based gateways, any defined extension listeners are checked
 *       for valid Java class implementations.</li>
 * </ul>
 * </p>
 *
 * <p>The validator uses a provided project root directory to locate compiled classes and dependency JARs,
 * which is necessary for verifying the existence of Java classes and ensuring they implement the required interfaces.</p>
 *
 * <p>Validation issues are collected and returned as a list of {@code BpmnElementValidationItem} objects.
 * Each item represents a specific error or warning encountered during the validation process.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 *   File bpmnFile = new File("path/to/model.bpmn");
 *   BpmnModelInstance model = ...; // load the BPMN model
 *   BpmnModelValidator validator = new BpmnModelValidator();
 *   validator.setProjectRoot(new File("path/to/project/root"));
 *   List&lt;BpmnElementValidationItem&gt; issues = validator.validateModel(model, bpmnFile, "processId");
 *   // Process the list of validation issues as needed
 * </pre>
 *
 * @author Mohamad Khalil Malla
 * @version 1.0
 */

public class BpmnModelValidator
{

    private File projectRoot;


    /**
     * Sets the project root directory.
     *
     * @param projectRoot the root folder of the project
     */
    public void setProjectRoot(File projectRoot)
    {
        this.projectRoot = projectRoot;
    }

    /**
     * Validates the entire BPMN model and returns a list of validation issues.
     *
     * @param model     the Camunda BPMN model instance
     * @param bpmnFile  the BPMN file
     * @param processId the process id (or empty string if not available)
     * @return list of BPMN element validation items
     */
    public List<BpmnElementValidationItem> validateModel(BpmnModelInstance model, File bpmnFile, String processId) {
        List<BpmnElementValidationItem> issues = new ArrayList<>();
        Collection<FlowElement> flowElements = model.getModelElementsByType(FlowElement.class);

        for (FlowElement element : flowElements) {

            if (element instanceof ServiceTask serviceTask) {

                validateServiceTask(serviceTask, issues, bpmnFile, processId);

            } else if (element instanceof StartEvent startEvent) {

                if (!startEvent.getEventDefinitions().isEmpty() &&
                        startEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {

                    validateMessageStartEvent(startEvent, issues, bpmnFile, processId);

                } else {

                    validateStartEvent(startEvent, issues, bpmnFile, processId);

                }
            } else if (element instanceof IntermediateThrowEvent throwEvent) {

                if (!throwEvent.getEventDefinitions().isEmpty() &&
                        throwEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {

                    validateMessageIntermediateThrowEvent(throwEvent, issues, bpmnFile, processId);

                } else if (!throwEvent.getEventDefinitions().isEmpty() &&
                        throwEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {

                    validateSignalIntermediateThrowEvent(throwEvent, issues, bpmnFile, processId);

                }
            } else if (element instanceof EndEvent endEvent) {

                if (!endEvent.getEventDefinitions().isEmpty() &&
                        endEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {

                    validateMessageEndEvent(endEvent, issues, bpmnFile, processId);

                } else if (!endEvent.getEventDefinitions().isEmpty() &&
                        endEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {

                    validateSignalEndEvent(endEvent, issues, bpmnFile, processId);

                } else {

                    validateEndEvent(endEvent, issues, bpmnFile, processId);

                }
            } else if (element instanceof IntermediateCatchEvent catchEvent) {

                if (!catchEvent.getEventDefinitions().isEmpty() &&
                        catchEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {

                    validateMessageIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);

                } else if (!catchEvent.getEventDefinitions().isEmpty() &&
                        catchEvent.getEventDefinitions().iterator().next() instanceof TimerEventDefinition) {

                    validateTimerIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);

                } else if (!catchEvent.getEventDefinitions().isEmpty() &&
                        catchEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {

                    validateSignalIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);

                } else if (!catchEvent.getEventDefinitions().isEmpty() &&
                        catchEvent.getEventDefinitions().iterator().next() instanceof ConditionalEventDefinition) {

                    validateConditionalIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);

                }
            } else if (element instanceof BoundaryEvent boundaryEvent) {

                if (!boundaryEvent.getEventDefinitions().isEmpty()) {

                    if (boundaryEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {

                        validateMessageBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);

                    } else if (boundaryEvent.getEventDefinitions().iterator().next() instanceof ErrorEventDefinition) {

                        validateErrorBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
                    }
                }
            } else if (element instanceof ExclusiveGateway exclusiveGateway) {

                validateExclusiveGateway(exclusiveGateway, issues, bpmnFile, processId);

            } else if (element instanceof SequenceFlow sequenceFlow) {

                validateSequenceFlow(sequenceFlow, issues, bpmnFile, processId);

            } else if (element instanceof UserTask userTask) {

                validateUserTask(userTask, issues, bpmnFile, processId);

            } else if (element instanceof SendTask sendTask) {

                validateSendTask(sendTask, issues, bpmnFile, processId);

            } else if (element instanceof ReceiveTask receiveTask) {

                validateReceiveTask(receiveTask, issues, bpmnFile, processId);

            } else if (element instanceof SubProcess subProcess) {

                validateSubProcess(subProcess, issues, bpmnFile, processId);

            } else if (element instanceof EventBasedGateway gateway) {

                validateEventBasedGateway(gateway, issues, bpmnFile, processId);

            }
        }
        return issues;
    }

    // SERVICE TASK VALIDATION

    private void validateServiceTask(ServiceTask task,
                                     List<BpmnElementValidationItem> issues,
                                     File bpmnFile,
                                     String processId)
    {
        String elementId = task.getId();

        // Name: warn if empty
        if (isEmpty(task.getName()))
        {
            issues.add(new BpmnServiceTaskNameEmptyValidationItem(elementId, bpmnFile, processId));
        }

        // Implementation: error if empty, class not found, or not implementing JavaDelegate
        String implClass = task.getCamundaClass();
        if (isEmpty(implClass))
        {
            issues.add(new BpmnServiceTaskImplementationClassEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            if (!classExists(implClass))
            {
                issues.add(new BpmnServiceTaskImplementationClassNotFoundValidationItem(elementId, bpmnFile, processId, implClass));
            }
            else if (!implementsJavaDelegate(implClass))
            {
                issues.add(new BpmnServiceTaskImplementationClassNotImplementingJavaDelegateValidationItem(elementId, bpmnFile, processId, implClass));
            }
        }
    }

    // MESSAGE START EVENT VALIDATION

    private void validateMessageStartEvent(StartEvent startEvent,
                                           List<BpmnElementValidationItem> issues,
                                           File bpmnFile,
                                           String processId) {
        String elementId = startEvent.getId();
        if (isEmpty(startEvent.getName())) {
            issues.add(new BpmnMessageStartEventNameEmptyValidationItem(elementId, bpmnFile, processId));
        }
        MessageEventDefinition messageDef = (MessageEventDefinition) startEvent.getEventDefinitions().iterator().next();
        validateMessageDefinition(startEvent, messageDef, issues, bpmnFile, processId);
    }

    // MESSAGE INTERMEDIATE THROW EVENT VALIDATION

    private void validateMessageIntermediateThrowEvent(IntermediateThrowEvent throwEvent,
                                                       List<BpmnElementValidationItem> issues,
                                                       File bpmnFile,
                                                       String processId) {
        String elementId = throwEvent.getId();

        // Warn if name is empty
        if (isEmpty(throwEvent.getName())) {
            issues.add(new BpmnMessageSendEventNameEmptyValidationItem(elementId, bpmnFile, processId));
        }

        // Extract the implementation class from either the event attribute or its messageEventDefinition.
        String implClass = extractImplementationClass(throwEvent);

        // Validate implementation
        validateImplementationClass(implClass, elementId, bpmnFile, processId, issues);

        // Validate field injections (profile, messageName, instantiatesCanonical)
        validateMessageSendFieldInjections(throwEvent, issues, bpmnFile, processId);

        // If a Message definition is present, issue a warning.
        for (EventDefinition def : throwEvent.getEventDefinitions()) {
            if (def instanceof MessageEventDefinition) {
                issues.add(new BpmnMessageSendEventHasDefinitionValidationItem(elementId, bpmnFile, processId));
            }
        }
    }


    /**
     * Validates the implementation class for a BPMN element.
     * <p>
     * The method checks whether the provided implementation class is empty. If not, it verifies that the class exists
     * (using the custom class loader, which includes both the compiled classes and dependency JARs) and that it implements
     * the {@code org.camunda.bpm.engine.delegate.JavaDelegate} interface. Depending on the outcome, the appropriate
     * validation issue is added to the list.
     * </p>
     *
     * @param implClass the implementation class string to validate.
     * @param elementId the BPMN element id.
     * @param bpmnFile  the BPMN file being validated.
     * @param processId the process id (or an empty string if not available).
     * @param issues    the list of BPMN element validation issues to add to.
     */
    private void validateImplementationClass(String implClass,
                                             String elementId,
                                             File bpmnFile,
                                             String processId,
                                             List<BpmnElementValidationItem> issues) {
        if (isEmpty(implClass)) {
            issues.add(new BpmnMessageSendEventImplementationClassEmptyValidationItem(elementId, bpmnFile, processId));
        } else if (!classExists(implClass)) {
            issues.add(new BpmnMessageSendEventImplementationClassNotFoundValidationItem(elementId, bpmnFile, processId, implClass));
        } else if (!implementsJavaDelegate(implClass)) {
            issues.add(new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateValidationItem(elementId, bpmnFile, processId, implClass));
        }
    }


    // MESSAGE END EVENT VALIDATION

    private void validateMessageEndEvent(EndEvent endEvent,
                                         List<BpmnElementValidationItem> issues,
                                         File bpmnFile,
                                         String processId)
    {
        String elementId = endEvent.getId();

        // Name: warn if empty
        if (isEmpty(endEvent.getName()))
        {
            issues.add(new BpmnMessageSendEventNameEmptyValidationItem(elementId, bpmnFile, processId));
        }

        // Validate implementation (similar to intermediate throw event)
        String implClass = extractImplementationClass(endEvent);
        checkImplementationClass(implClass, elementId, bpmnFile, processId, issues);

        // Validate field injections
        validateMessageSendFieldInjections(endEvent, issues, bpmnFile, processId);

        // Example rule: if the end event is named "send result to requester", it should have asyncBefore=true.
        if ("send result to requester".equalsIgnoreCase(endEvent.getName()))
        {
            if (!endEvent.isCamundaAsyncBefore())
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Message End Event named 'send result to requester' should be asyncBefore=true",
                        ValidationType.BPMN_FLOATING_ELEMENT
                ));
            }
        }
    }

    // MESSAGE INTERMEDIATE CATCH EVENT VALIDATION

    private void validateMessageIntermediateCatchEvent(IntermediateCatchEvent catchEvent,
                                                       List<BpmnElementValidationItem> issues,
                                                       File bpmnFile,
                                                       String processId)
    {
        String elementId = catchEvent.getId();

        if (isEmpty(catchEvent.getName()))
        {
            issues.add(new BpmnMessageStartEventNameEmptyValidationItem(elementId, bpmnFile, processId));
        }

        MessageEventDefinition def = (MessageEventDefinition)
                catchEvent.getEventDefinitions().iterator().next();

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

    private void validateMessageBoundaryEvent(BoundaryEvent boundaryEvent,
                                              List<BpmnElementValidationItem> issues,
                                              File bpmnFile,
                                              String processId) {
        String elementId = boundaryEvent.getId();
        if (isEmpty(boundaryEvent.getName())) {
            issues.add(new BpmnMessageStartEventNameEmptyValidationItem(elementId, bpmnFile, processId));
        }
        MessageEventDefinition def = (MessageEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();
        validateMessageDefinition(boundaryEvent, def, issues, bpmnFile, processId);
    }

    // ERROR BOUNDARY EVENT VALIDATION

    /**
     * Validates an Error Boundary Event by checking the following:
     * <ul>
     *   <li>If the boundary event's name is empty, a warning is added.</li>
     *   <li>If the associated ErrorEventDefinition’s error element is missing or its name is empty, an error is added.</li>
     *   <li>If the error element’s errorCode is missing or empty, an error is added.</li>
     *   <li>If the errorCodeVariable attribute (from Camunda's namespace) is empty, an error is added.</li>
     * </ul>
     *
     * @param boundaryEvent the boundary event to validate.
     * @param issues        the list of BPMN element validation issues to add to.
     * @param bpmnFile      the BPMN file being validated.
     * @param processId     the process id (or an empty string if not available).
     */
    private void validateErrorBoundaryEvent(BoundaryEvent boundaryEvent,
                                            List<BpmnElementValidationItem> issues,
                                            File bpmnFile,
                                            String processId) {
        String elementId = boundaryEvent.getId();

        // Warn if the boundary event's name is empty.
        if (isEmpty(boundaryEvent.getName())) {
            issues.add(new BpmnErrorBoundaryEventNameEmptyValidationItem(elementId, bpmnFile, processId));
        }

        // Retrieve the ErrorEventDefinition from the boundary event.
        ErrorEventDefinition errorDef = (ErrorEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();

        // Check if the error element exists and its name is not empty.
        if (errorDef.getError() == null || isEmpty(errorDef.getError().getName())) {
            issues.add(new BpmnErrorBoundaryEventErrorNameEmptyValidationItem(elementId, bpmnFile, processId));
        }

        // Check if the error element's errorCode exists and is not empty.
        if (errorDef.getError() == null || isEmpty(errorDef.getError().getErrorCode())) {
            issues.add(new BpmnErrorBoundaryEventErrorCodeEmptyValidationItem(elementId, bpmnFile, processId));
        }

        // Validate the code variable (assumed to be a Camunda attribute).
        String errorCodeVariable = boundaryEvent.getAttributeValueNs("http://camunda.org/schema/1.0/bpmn", "errorCodeVariable");
        if (isEmpty(errorCodeVariable)) {
            issues.add(new BpmnErrorBoundaryEventErrorCodeVariableEmptyValidationItem(elementId, bpmnFile, processId));
        }
    }


    // EXCLUSIVE GATEWAY VALIDATION

    private void validateExclusiveGateway(ExclusiveGateway gateway,
                                          List<BpmnElementValidationItem> issues,
                                          File bpmnFile,
                                          String processId)
    {
        String elementId = gateway.getId();

        if (gateway.getOutgoing() != null && gateway.getOutgoing().size() > 1)
        {
            if (isEmpty(gateway.getName()))
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Exclusive Gateway has multiple outgoing flows but no name",
                        ValidationType.BPMN_SEQUENCE_FLOW_AMBIGUOUS
                ));
            }
        }
    }

    // SEQUENCE FLOW VALIDATION

    private void validateSequenceFlow(SequenceFlow flow,
                                      List<BpmnElementValidationItem> issues,
                                      File bpmnFile,
                                      String processId)
    {
        String elementId = flow.getId();

        if (flow.getSource() instanceof ExclusiveGateway gw)
        {
            if (isEmpty(flow.getName()))
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Sequence Flow from an Exclusive Gateway is missing a name",
                        ValidationType.BPMN_SEQUENCE_FLOW_AMBIGUOUS
                ));
            }
            if (flow.getConditionExpression() == null && !flow.equals(gw.getDefault()))
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Non-default Sequence Flow from an Exclusive Gateway has no condition",
                        ValidationType.BPMN_SEQUENCE_FLOW_AMBIGUOUS
                ));
            }
        }
    }

    // START EVENT VALIDATION

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
                issues.add(new BpmnProcessIdMissingValidationItem(elementId, bpmnFile, processId));
            }
        }
    }

    // END EVENT VALIDATION

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
                issues.add(new BpmnProcessIdMissingValidationItem(elementId, bpmnFile, processId));
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

        // Validate extension listeners: check if Java classes exist
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
     * Validates a Timer Intermediate Catch Event by checking its configuration.
     * <p>
     * This method performs several checks:
     * <ul>
     *   <li>It verifies that the event has a non-empty name. If the name is empty, a validation issue is added.</li>
     *   <li>It retrieves the {@code TimerEventDefinition} from the event's definitions.</li>
     *   <li>It extracts the timer expressions: {@code timeDate}, {@code timeCycle}, and {@code timeDuration}.</li>
     *   <li>
     *     It checks whether at least one of these expressions is provided:
     *     <ul>
     *       <li>If all expressions are empty, a validation issue is added indicating that the timer type is missing.</li>
     *       <li>If a {@code timeDate} is provided, an informational validation issue is added to indicate that a fixed date/time is used.</li>
     *       <li>
     *         If either {@code timeCycle} or {@code timeDuration} is provided, the method checks whether the timer expression
     *         contains a version placeholder (e.g., "#{version}"). If not, a warning is issued indicating that the timer value appears fixed.
     *       </li>
     *     </ul>
     *   </li>
     * </ul>
     * </p>
     *
     * @param catchEvent the BPMN Intermediate Catch Event to validate, which is expected to be a timer event
     * @param issues the list to which any validation issues will be added
     * @param bpmnFile the BPMN file being validated
     * @param processId the process identifier associated with the BPMN model
     */
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

        TimerEventDefinition timerDef = (TimerEventDefinition)
                catchEvent.getEventDefinitions().iterator().next();

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
                String timerValue = !isTimeCycleEmpty ? timeCycleExpr.getTextContent() : timeDurationExpr.getTextContent();
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

        SignalEventDefinition def = (SignalEventDefinition)
                catchEvent.getEventDefinitions().iterator().next();

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

        SignalEventDefinition def = (SignalEventDefinition)
                throwEvent.getEventDefinitions().iterator().next();

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

        SignalEventDefinition def = (SignalEventDefinition)
                endEvent.getEventDefinitions().iterator().next();

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
            // Example check to verify if the corresponding questionnaire exists
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

    private void validateSendTask(SendTask sendTask,
                                  List<BpmnElementValidationItem> issues,
                                  File bpmnFile,
                                  String processId)
    {
        String elementId = sendTask.getId();

        if (isEmpty(sendTask.getName()))
        {
            issues.add(new BpmnMessageSendEventNameEmptyValidationItem(elementId, bpmnFile, processId));
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
                issues.add(new BpmnMessageSendTaskImplementationClassNotFound(elementId, bpmnFile, processId, implClass));
            }
            else if (!implementsJavaDelegate(implClass))
            {
                issues.add(new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateValidationItem(elementId, bpmnFile, processId, implClass));
            }
        }

        // Validate field injections (same as in MESSAGE INTERMEDIATE THROW EVENT)
        validateMessageSendFieldInjections(sendTask, issues, bpmnFile, processId);
    }

    // RECEIVE TASK VALIDATION

    private void validateReceiveTask(ReceiveTask receiveTask,
                                     List<BpmnElementValidationItem> issues,
                                     File bpmnFile,
                                     String processId)
    {
        String elementId = receiveTask.getId();

        if (isEmpty(receiveTask.getName()))
        {
            issues.add(new BpmnMessageStartEventNameEmptyValidationItem(elementId, bpmnFile, processId));
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

        ConditionalEventDefinition condDef = (ConditionalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

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
                    ValidationType.BPMN_FLOATING_ELEMENT, ValidationSeverity.INFO
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
     * Validates the field injections on a BPMN element for message send tasks or events.
     * <p>
     * This method checks the extension elements of the given BPMN element for any Camunda field injections.
     * It validates specific fields by name:
     * <ul>
     *   <li><b>profile:</b>
     *       <ul>
     *         <li>Ensures the field is non-empty.</li>
     *         <li>Checks that the value contains a version placeholder (e.g., "#{version}").</li>
     *         <li>Uses the FhirValidator to verify that a corresponding StructureDefinition exists.</li>
     *       </ul>
     *   </li>
     *   <li><b>messageName:</b>
     *       <ul>
     *         <li>Ensures the field is non-empty.</li>
     *         <li>Uses the FhirValidator to check that a corresponding ActivityDefinition exists.</li>
     *         <li>If a profile has been specified, verifies that the message name matches the profile.</li>
     *       </ul>
     *   </li>
     *   <li><b>instantiatesCanonical:</b>
     *       <ul>
     *         <li>Ensures the field is non-empty.</li>
     *         <li>Checks that the value contains a version placeholder.</li>
     *         <li>Verifies that an ActivityDefinition has been found.</li>
     *         <li>If a profile exists, ensures that the canonical value matches the profile.</li>
     *       </ul>
     *   </li>
     *   <li><b>Unknown Fields:</b>
     *       <ul>
     *         <li>If any field injection with an unknown field name is encountered, a validation error is generated.
     *             Only the fields "profile", "messageName", and "instantiatesCanonical" are allowed.</li>
     *       </ul>
     *   </li>
     * </ul>
     * For each field, the method ensures that the value is provided as a string literal rather than an expression.
     * Any validation issues are added to the provided list of issues.
     * </p>
     *
     * @param element the BPMN element containing extension elements with field injections
     * @param issues the list to which any validation issues will be added
     * @param bpmnFile the BPMN file being validated
     * @param processId the process identifier associated with the BPMN model
     */
    private void validateMessageSendFieldInjections(BaseElement element,
                                                    List<BpmnElementValidationItem> issues,
                                                    File bpmnFile,
                                                    String processId)
    {
        if (element.getExtensionElements() == null)
            return;

        Collection<CamundaField> fields = element.getExtensionElements().getElementsQuery()
                .filterByType(CamundaField.class).list();

        boolean profileFound = false;
        boolean activityDefFound = false;
        String profileValue = null;

        for (CamundaField field : fields)
        {
            String fieldName = field.getCamundaName();
            String stringValue = field.getCamundaStringValue();
            String exprValue = field.getCamundaExpression();
            String rawValue = !isEmpty(stringValue) ? stringValue : exprValue;

            // Type check: must be a string literal (not an expression)
            if (field.getCamundaStringValue() == null)
            {
                issues.add(new BpmnFieldInjectionNotStringLiteralValidationItem(
                        element.getId(), bpmnFile, processId, fieldName));
                continue;
            }

            switch (fieldName)
            {
                case "profile" -> {
                    if (isEmpty(rawValue))
                    {
                        issues.add(new BpmnMessageSendEventProfileEmptyValidationItem(element.getId(), bpmnFile, processId));
                    }
                    else
                    {
                        profileFound = true;
                        profileValue = rawValue;
                        if (!containsVersionPlaceholder(rawValue))
                        {
                            issues.add(new BpmnMessageSendEventProfileNoVersionPlaceholderValidationItem(element.getId(), bpmnFile, processId, rawValue));
                        }
                        // Use the FhirValidator to check for the existence of the StructureDefinition.
                        if (!FhirValidator.structureDefinitionExists(rawValue, projectRoot))
                        {
                            issues.add(new BpmnMessageSendEventProfileNotFoundValidationItem(element.getId(), bpmnFile, processId, rawValue));
                        }
                    }
                }
                case "messageName" -> {
                    if (isEmpty(rawValue))
                    {
                        issues.add(new BpmnMessageSendEventMessageNameEmptyValidationItem(element.getId(), bpmnFile, processId));
                    }
                    else
                    {
                        // Use FhirValidator to check for the existence of the ActivityDefinition.
                        if (!FhirValidator.activityDefinitionExists(rawValue, projectRoot))
                        {
                            issues.add(new BpmnMessageSendEventMessageNameNotPresentInActivityDefinitionValidationItem(element.getId(), bpmnFile, processId, rawValue));
                        }
                        else
                        {
                            activityDefFound = true;
                        }
                        if (profileFound && !messageNameMatchesProfile(profileValue, rawValue))
                        {
                            issues.add(new BpmnMessageSendEventMessageNameNotMatchingProfileValidationItem(element.getId(), bpmnFile, processId, profileValue, rawValue));
                        }
                    }
                }
                case "instantiatesCanonical" -> {
                    if (isEmpty(rawValue))
                    {
                        issues.add(new BpmnMessageSendEventInstantiatesCanonicalEmptyValidationItem(element.getId(), bpmnFile, processId));
                    }
                    else
                    {
                        if (!containsVersionPlaceholder(rawValue))
                        {
                            issues.add(new BpmnMessageSendEventInstantiatesCanonicalNoVersionPlaceholderValidationItem(element.getId(), bpmnFile, processId, rawValue));
                        }
                        if (!activityDefFound)
                        {
                            issues.add(new BpmnMessageSendEventNoActivityDefinitionYetValidationItem(element.getId(), bpmnFile, processId, rawValue));
                        }
                        if (profileFound && !canonicalMatchesProfile(profileValue, rawValue))
                        {
                            issues.add(new BpmnMessageSendEventInstantiatesCanonicalNotMatchingProfileValidationItem(element.getId(), bpmnFile, processId, profileValue, rawValue));
                        }
                    }
                }
                default -> {
                    // Unknown field injection encountered: Report a validation error.
                    issues.add(new BpmnUnknownFieldInjectionValidationItem(element.getId(), bpmnFile, processId, fieldName));
                }
            }
        }
    }


    // UTILITY METHODS

    /**
     * Checks if the given string is null or empty.
     *
     * @param value the string to check
     * @return true if empty, false otherwise
     */
    private boolean isEmpty(String value)
    {
        return (value == null || value.trim().isEmpty());
    }

    /**
     * Checks whether a class with the given fully qualified name exists in the classpath.
     * <p>
     * This method first attempts to load the class using the current thread's context class loader.
     * The "current context class loader" refers to the ClassLoader that is associated with the thread
     * currently executing this code. It is typically set by the runtime environment (for example, an
     * application server or a standalone Java application) and represents the classes available to that thread.
     * </p>
     * <p>
     * If the class is not found using the context class loader, the method then attempts to load the class
     * from the compiled classes directory (e.g. "target/classes" or "build/classes") within the project root,
     * as well as from any dependency JARs found in the "target/dependency" directory.
     * </p>
     * <p>
     * The method returns {@code true} if the class is found by any of these mechanisms; otherwise, it returns {@code false}.
     * </p>
     *
     * @param className the fully qualified name of the class to check (e.g., "com.example.MyClass")
     * @return {@code true} if the class exists, {@code false} otherwise
     */
    private boolean classExists(String className) {
        //System.out.println("DEBUG: Checking if class exists: " + className);
        try {
            // First, try using the current context class loader.
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            //System.out.println("DEBUG: Using context class loader: " + cl);
            cl.loadClass(className);
            //System.out.println("DEBUG: Class found using context class loader.");
            return true;
        } catch (ClassNotFoundException e) {
            //System.out.println("DEBUG: Class not found using context class loader: " + className);
        } catch (Throwable t) {
            System.err.println("DEBUG: Throwable caught while trying to load class " + className + ": " + t.getMessage());
            t.printStackTrace();
        }

        if (projectRoot != null) {
            try {
                // Locate the compiled classes directory (Maven or Gradle)
                File classesDir = new File(projectRoot, "target/classes");
                if (!classesDir.exists()) {
                    classesDir = new File(projectRoot, "build/classes");
                }
                //System.out.println("DEBUG: Using compiled classes directory: " + classesDir.getAbsolutePath());

                List<URL> urlList = new ArrayList<>();
                if (classesDir.exists()) {
                    urlList.add(classesDir.toURI().toURL());
                } else {
                    System.err.println("DEBUG: Compiled classes directory not found in project root: " + projectRoot.getAbsolutePath());
                }

                // Add dependency JARs from target/dependency, if available.
                File dependencyDir = new File(projectRoot, "target/dependency");
                if (dependencyDir.exists() && dependencyDir.isDirectory()) {
                    File[] jars = dependencyDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
                    if (jars != null) {
                        for (File jar : jars) {
                            urlList.add(jar.toURI().toURL());
                            //System.out.println("DEBUG: Adding dependency JAR: " + jar.getAbsolutePath());
                        }
                    }
                } else {
                    System.out.println("DEBUG: No dependency directory found at: " + dependencyDir.getAbsolutePath());
                }

                URL[] urls = urlList.toArray(new URL[0]);
                /* System.out.println("DEBUG: Created URLClassLoader with URLs:");
                for (URL url : urls) {
                    System.out.println("  " + url);
                } */
                ClassLoader urlCl = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
                urlCl.loadClass(className);
                //System.out.println("DEBUG: Class found using URLClassLoader from compiled classes and dependencies.");
                return true;
            } catch (Throwable t) {
                System.err.println("DEBUG: Throwable caught while trying to load class '" + className +
                        "' from compiled classes and dependencies: " + t.getMessage());
                t.printStackTrace();
            }
        } else {
            System.err.println("DEBUG: projectRoot is null. Cannot locate compiled classes.");
        }
        return false;
    }

    /**
     * Checks whether the class with the given fully qualified name implements the
     * {@code org.camunda.bpm.engine.delegate.JavaDelegate} interface.
     * <p>
     * This method constructs a custom URLClassLoader that includes both the compiled
     * classes directory (either "target/classes" or "build/classes") and any dependency
     * JARs from "target/dependency" (if available). It then loads the candidate class and the
     * JavaDelegate interface using the same custom class loader. If the candidate class is
     * assignable to the JavaDelegate interface, the method returns {@code true}.
     * </p>
     *
     * @param className the fully qualified name of the candidate class to check.
     * @return {@code true} if the candidate class implements {@code org.camunda.bpm.engine.delegate.JavaDelegate},
     *         {@code false} otherwise.
     */
    private boolean implementsJavaDelegate(String className) {
        try {
            // Locate the compiled classes' directory.
            File classesDir = new File(projectRoot, "target/classes");
            if (!classesDir.exists()) {
                classesDir = new File(projectRoot, "build/classes");
            }
            List<URL> urlList = new ArrayList<>();
            if (classesDir.exists()) {
                urlList.add(classesDir.toURI().toURL());
            } else {
                System.err.println("DEBUG: Compiled classes directory not found in project root: "
                        + projectRoot.getAbsolutePath());
            }

            // Add dependency JARs from target/dependency, if available.
            File dependencyDir = new File(projectRoot, "target/dependency");
            if (dependencyDir.exists() && dependencyDir.isDirectory()) {
                File[] jars = dependencyDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
                if (jars != null) {
                    for (File jar : jars) {
                        urlList.add(jar.toURI().toURL());
                    }
                }
            } else {
                System.out.println("DEBUG: No dependency directory found at: "
                        + dependencyDir.getAbsolutePath());
            }

            // Create a custom URLClassLoader with the compiled classes and dependency JARs.
            URL[] urls = urlList.toArray(new URL[0]);
            ClassLoader customCl = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());

            // Load the candidate class using the custom class loader.
            Class<?> candidateClass = Class.forName(className, true, customCl);
            // Load the JavaDelegate interface using the same custom class loader.
            Class<?> delegateInterface = Class.forName("org.camunda.bpm.engine.delegate.JavaDelegate", true, customCl);

            // Return true if candidateClass implements JavaDelegate.
            return delegateInterface.isAssignableFrom(candidateClass);
        } catch (Throwable t) {
            System.err.println("DEBUG: Exception in implementsJavaDelegate for " + className + ": " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }


    /**
     * Checks if the raw value contains a version placeholder (e.g., "#{version}").
     *
     * @param rawValue the string to check
     * @return true if the placeholder exists, false otherwise
     */
    private boolean containsVersionPlaceholder(String rawValue)
    {
        return (rawValue != null && rawValue.contains("#{version}"));
    }


    /**
     * Checks whether the given message name is referenced within the profile value.
     * <p>
     * This method is used as part of the validation process for SEND TASK, MESSAGE END EVENT,
     * and MESSAGE INTERMEDIATE THROW EVENT BPMN elements. In these events, field injections are used
     * to define critical configuration properties. The "profile" field usually contains a canonical
     * URL (possibly with additional metadata separated by a pipe ("|")), which defines a FHIR StructureDefinition.
     * This method extracts the canonical part (everything before the pipe) and then checks whether the
     * provided message name appears within that canonical URL (ignoring case differences). This ensures that
     * the message referenced by the event is consistent with the expected profile configuration.
     * </p>
     *
     * @param profileValue the full profile value, which may include extra metadata separated by a pipe ("|");
     *                     only the portion before the pipe is used for comparison.
     * @param messageName the name of the message that should be referenced within the canonical part of the profile.
     * @return {@code true} if the canonical part of the profile contains the message name (ignoring case),
     *         {@code false} otherwise.
     */

    private boolean messageNameMatchesProfile(String profileValue, String messageName)
    {
        if (profileValue == null || messageName == null) return false;
        int pipeIndex = profileValue.indexOf("|");
        if (pipeIndex != -1)
        {
            profileValue = profileValue.substring(0, pipeIndex);
        }
        return profileValue.toLowerCase().contains(messageName.toLowerCase());
    }

    /**
     * Validates the consistency between the provided profile and canonical values by checking for a common keyword.
     * <p>
     * This method is used in the validation of SEND TASK, MESSAGE END EVENT, and MESSAGE INTERMEDIATE THROW EVENT BPMN elements.
     * In these events, field injections for "profile" and "instantiatesCanonical" are specified, and these values often contain
     * additional metadata appended after a pipe ("|"). The method extracts the base value (i.e. the part before the pipe)
     * from both inputs. It then verifies, in a case-insensitive manner, that both base values contain a common keyword.
     * <em>Note:</em> In this implementation the keyword "ping" is used as an example. In other scenarios, the keyword
     * may differ; the check should be adapted accordingly if a different common identifier is required.
     * </p>
     *
     * @param profileVal   the profile value from the BPMN field injection, potentially including extra metadata after a pipe.
     * @param canonicalVal the canonical value from the BPMN field injection, potentially including extra metadata after a pipe.
     * @return {@code true} if both the extracted profile value and canonical value contain the common keyword (ignoring case);
     *         {@code false} otherwise.
     */

    private boolean canonicalMatchesProfile(String profileVal, String canonicalVal)
    {
        if (profileVal == null || canonicalVal == null) return false;
        int pIdx = profileVal.indexOf("|");
        if (pIdx != -1) profileVal = profileVal.substring(0, pIdx);
        int cIdx = canonicalVal.indexOf("|");
        if (cIdx != -1) canonicalVal = canonicalVal.substring(0, cIdx);
        return profileVal.toLowerCase().contains("ping") && canonicalVal.toLowerCase().contains("ping");
    }

    /**
     * Validates the message name against known ActivityDefinition and StructureDefinition.
     *
     * @param messageName the message name to check
     * @param issues      list of validation issues to add to
     * @param elementId   the BPMN element id
     * @param bpmnFile    the BPMN file
     * @param processId   the process id
     */
    private void checkMessageName(String messageName,
                                  List<BpmnElementValidationItem> issues,
                                  String elementId,
                                  File bpmnFile,
                                  String processId) {
        System.out.println("დ DEBUG: Checking message name '" + messageName + "' for BPMN element " + elementId);

        boolean actDefExists = FhirValidator.activityDefinitionExists(messageName, projectRoot);
        boolean structDefExists = FhirValidator.structureDefinitionExists(messageName, projectRoot);

        System.out.println("დ DEBUG: ActivityDefinition exists: " + actDefExists);
        System.out.println("დ DEBUG: StructureDefinition exists: " + structDefExists);

        if (!actDefExists) {
            issues.add(new BpmnMessageSendEventMessageNameNotPresentInActivityDefinitionValidationItem(
                    elementId, bpmnFile, processId, messageName));
        }
        if (!structDefExists) {
            issues.add(new BpmnMessageSendEventMessageNameNotMatchingProfileValidationItem(
                    elementId, bpmnFile, processId, "", messageName));
        }
    }



    /**
     * Validates the implementation class for emptiness, existence, and JavaDelegate implementation.
     *
     * @param implClass the implementation class name
     * @param elementId the BPMN element id
     * @param bpmnFile  the BPMN file
     * @param processId the process id
     * @param issues    list of validation issues to add to
     */
    private void checkImplementationClass(String implClass,
                                          String elementId,
                                          File bpmnFile,
                                          String processId,
                                          List<BpmnElementValidationItem> issues)
    {
        validateImplementationClass(implClass, elementId, bpmnFile, processId, issues);
    }

    /**
     * Checks whether a corresponding questionnaire exists for the given form key.
     * <p>
     * This example implementation determines the existence of a questionnaire by verifying
     * if the provided form key contains the substring "questionnaire". In a production
     * environment, this method should be replaced with a more robust check against an actual
     * repository or registry of questionnaires.
     * </p>
     *
     * @param formKey the external form key, typically referencing a questionnaire form
     * @return {@code true} if the form key contains "questionnaire"; {@code false} otherwise
     */

    // read JavaDoc
    private boolean questionnaireExists(String formKey)
    {
        // Example: if formKey contains "questionnaire", then it exists
        return formKey.contains("questionnaire");
    }

    /**
     * Extracts the implementation class from a BPMN element that has event definitions.
     * <p>
     * First, the method checks if the element has a "camunda:class" attribute directly.
     * If not found, it iterates over its event definitions and returns the camunda:class
     * value from the first MessageEventDefinition found.
     * </p>
     *
     * @param element the BPMN element (such as an IntermediateThrowEvent or EndEvent)
     * @return the implementation class, or an empty string if not found.
     */
    private String extractImplementationClass(BaseElement element) {
        // Try to read the attribute directly from the element.
        String implClass = element.getAttributeValueNs("class", "http://camunda.org/schema/1.0/bpmn");
        if (!isEmpty(implClass)) {
            return implClass;
        }
        // Otherwise, iterate over event definitions (if any).
        Collection<EventDefinition> eventDefs = element.getChildElementsByType(EventDefinition.class);
        for (EventDefinition def : eventDefs) {
            if (def instanceof MessageEventDefinition messageDef) {
                implClass = messageDef.getCamundaClass();
                if (!isEmpty(implClass)) {
                    return implClass;
                }
            }
        }
        return "";
    }

    /**
     * Validates that a message definition has a non-empty message name and that the name
     * exists in both a known ActivityDefinition and StructureDefinition.
     *
     * @param element   the BPMN element (used for element ID)
     * @param messageDef the message event definition containing the message reference
     * @param issues    list to which any validation issues are added
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process id (or empty string if not available)
     */
    private void validateMessageDefinition(FlowElement element,
                                           MessageEventDefinition messageDef,
                                           List<BpmnElementValidationItem> issues,
                                           File bpmnFile,
                                           String processId) {
        String elementId = element.getId();
        if (messageDef.getMessage() == null || isEmpty(messageDef.getMessage().getName())) {
            issues.add(new BpmnMessageStartEventMessageNameEmptyValidationItem(elementId, bpmnFile, processId));
        } else {
            // Check if an ActivityDefinition exists for the message name.
            String msgName = messageDef.getMessage().getName();
            if (!FhirValidator.activityDefinitionExists(msgName, projectRoot)) {
                issues.add(new BpmnMessageStartEventMessageNameNotPresentInActivityDefinitionValidationItem(
                        elementId, bpmnFile, processId, msgName));
            }
            // Check if a StructureDefinition exists for the message name.
            if (!FhirValidator.structureDefinitionExists(msgName, projectRoot)) {
                issues.add(new BpmnMessageStartEventMessageNameNotMatchingProfileValidationItem(
                        elementId, bpmnFile, processId, msgName));
            }
        }
    }

}
