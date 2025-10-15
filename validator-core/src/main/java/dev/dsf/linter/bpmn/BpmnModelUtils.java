package dev.dsf.linter.bpmn;

import org.camunda.bpm.model.bpmn.instance.*;

import java.util.Collection;
import java.util.Optional;

import static dev.dsf.linter.util.ValidationUtils.isEmpty;

public class BpmnModelUtils {

    /**
     * Extracts the implementation class specified on a BPMN element.
     * <p>
     * The method first checks the element's direct "camunda:class" attribute. If not found,
     * it then inspects nested {@link MessageEventDefinition}s for elements like {@link ThrowEvent}
     * or {@link EndEvent} to find a "camunda:class" attribute there.
     * </p>
     *
     * @param element The BPMN {@link BaseElement} from which to extract the implementation class.
     * @return An {@link Optional} containing the class name if found, otherwise an empty Optional.
     */
    public static Optional<String> extractImplementationClass(BaseElement element) {
        // 1. Check for a direct "camunda:class" attribute on the element itself.
        String implClass = element.getAttributeValueNs("http://camunda.org/schema/1.0/bpmn", "class");
        if (!isEmpty(implClass)) {
            return Optional.of(implClass);
        }

        // 2. If not found, check for a class within nested event definitions.
        // IMPORTANT: Always check from the most specific to the most general type.
        Collection<EventDefinition> definitions = null;
        if (element instanceof EndEvent endEvent) { // FIRST, check for the more specific EndEvent
            definitions = endEvent.getEventDefinitions();
        } else if (element instanceof ThrowEvent throwEvent) { // THEN, check for the more general ThrowEvent
            definitions = throwEvent.getEventDefinitions();
        }

        // 3. Use the helper to extract the class from the definitions.
        return getCamundaClassFromMessageEvents(definitions);
    }

    /**
     * Scans a collection of event definitions to find the Camunda class from a MessageEventDefinition.
     *
     * @param definitions The collection of {@link EventDefinition}s to search through.
     * @return A {@link java.util.Optional} containing the class name if found, otherwise an empty Optional.
     */
    private static Optional<String> getCamundaClassFromMessageEvents(Collection<EventDefinition> definitions) {
        if (definitions == null) {
            return Optional.empty();
        }

        return definitions.stream()
                .filter(MessageEventDefinition.class::isInstance)
                .map(MessageEventDefinition.class::cast)
                .map(MessageEventDefinition::getCamundaClass)
                .filter(className -> !isEmpty(className))
                .findFirst();
    }
}
