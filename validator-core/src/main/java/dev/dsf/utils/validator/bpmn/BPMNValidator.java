package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.DsfValidatorImpl;
import dev.dsf.utils.validator.ValidationOutput;

import java.nio.file.Path;

/**
 * BPMNValidator is responsible for validating BPMN files.
 * It delegates the validation to the DsfValidatorImpl.
 */
public class BPMNValidator {

    private final DsfValidatorImpl validator;

    public BPMNValidator() {
        this.validator = new DsfValidatorImpl();
    }

    /**
     * Validates the BPMN file located at the given path.
     *
     * @param bpmnFilePath the path to the BPMN file.
     * @return a ValidationOutput containing all validation issues.
     */
    public ValidationOutput validateBpmnFile(Path bpmnFilePath) {
        return validator.validate(bpmnFilePath);
    }
}
