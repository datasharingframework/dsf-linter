package dev.dsf.utils.validator.util;

import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.exception.ResourceValidationException;

import java.io.IOException;
import java.nio.file.Path;

public interface DsfValidator {

    /**
     * Validates a project/directory. Implementation should expect the following folder structure to be present:
     *      project-root
     *      |-- src
     *        |-- main
     *          |-- java
     *          |-- resources
     *            |-- bpe
     *            |-- fhir
     * Where the bpe subdirectory contains .bpmn files to validate and the fhir subdirectory contains more directories
     * corresponding to different FHIR resources e.g. ActivityDefinition or StructureDefinition. These directories are named
     * the same as their FHIR resource counterparts.
     * @param path Path to a project/directory which should be validated and where the outlined directory structure is found
     * @return A ValidationOutput-Object containing the results of the validation
     * @throws ResourceValidationException if a resource has a syntax error.
     * @throws IllegalStateException if a unique ProcessPluginDefinition cannot be found.
     * @throws IOException if a file system error occurs.
     */
    ValidationOutput validate(Path path) throws ResourceValidationException, IllegalStateException, IOException, InterruptedException, MissingServiceRegistrationException;
}
