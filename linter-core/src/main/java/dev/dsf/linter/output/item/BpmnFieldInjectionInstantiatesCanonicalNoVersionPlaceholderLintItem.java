package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the instantiatesCanonical field injection does not contain a version placeholder.
 * Corresponds to {@link LintingType#BPMN_FIELD_INJECTION_INSTANTIATES_CANONICAL_NO_VERSION_PLACEHOLDER}.
 */
public class BpmnFieldInjectionInstantiatesCanonicalNoVersionPlaceholderLintItem extends BpmnElementLintItem {

    /**
     * Constructs a new Lint Item for an instantiatesCanonical field injection missing a version placeholder.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnFieldInjectionInstantiatesCanonicalNoVersionPlaceholderLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "instantiatesCanonical field injection does not contain a version placeholder");
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom lint description
     */
    public BpmnFieldInjectionInstantiatesCanonicalNoVersionPlaceholderLintItem(String elementId, File bpmnFile, String processId, String description) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
