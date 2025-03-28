package dev.dsf.utils.validator;

import dev.dsf.utils.validator.bpmn.BpmnModelValidator;
import dev.dsf.utils.validator.fhir.FhirResourceValidator;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;
import dev.dsf.utils.validator.item.FhirElementValidationItem;
import dev.dsf.utils.validator.item.UnparsableBpmnFileValidationItem;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <p>
 * An implementation of the DSF Validator interface capable of validating either
 * <strong>BPMN</strong> or <strong>FHIR</strong> files based on file extension or content.
 * </p>
 *
 * <p>
 * This class accomplishes the following:
 * <ul>
 *   <li>Determines if the provided file is a BPMN or FHIR resource (e.g., by checking file extension).</li>
 *   <li>For BPMN files:
 *     <ol>
 *       <li>Checks if the file exists and is readable.</li>
 *       <li>Parses the file into a {@link BpmnModelInstance}.</li>
 *       <li>Extracts the first {@link Process} ID, if present.</li>
 *       <li>Finds the project root directory by locating a {@code pom.xml} file.</li>
 *       <li>Validates the BPMN model via {@link BpmnModelValidator}.</li>
 *     </ol>
 *   </li>
 *   <li>For FHIR files:
 *     <ol>
 *       <li>Checks if the file exists and is readable.</li>
 *     </ol>
 *   </li>
 *   <li>Aggregates all issues into a single {@link ValidationOutput} object.</li>
 * </ul>
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 *   <li>BPMN 2.0: <a href="https://www.omg.org/spec/BPMN/2.0">https://www.omg.org/spec/BPMN/2.0</a></li>
 *   <li>HL7 FHIR: <a href="https://hl7.org/fhir">https://hl7.org/fhir</a></li>
 * </ul>
 */
public class DsfValidatorImpl implements DsfValidator
{
    private final BpmnModelValidator bpmnModelValidator;

    /**
     * Constructs a new {@code DsfValidatorImpl} with default BPMN and FHIR validators.
     */
    public DsfValidatorImpl()
    {
        this.bpmnModelValidator = new BpmnModelValidator();

    }

    /**
     * Validates the given file, delegating to either BPMN-specific or FHIR-specific logic
     * depending on the file's extension.
     *
     * <p><strong>BPMN logic flow:</strong>
     * <ol>
     *   <li>Check if the file exists.</li>
     *   <li>Check if the file is readable via {@link #isFileReadable(Path)}.</li>
     *   <li>Attempt to parse the file as a BPMN model.</li>
     *   <li>Extract the first BPMN {@link Process} ID.</li>
     *   <li>Determine the project root by finding a {@code pom.xml} file.</li>
     *   <li>Validate the BPMN model via {@link BpmnModelValidator}.</li>
     * </ol>
     *
     * <p><strong>FHIR logic flow:</strong>
     * <ol>
     *   <li>Check if the file exists and is readable.</li>
     *   <li>Use {@link FhirResourceValidator} to parse and validate the resource.</li>
     * </ol>
     *
     * @param path the {@link Path} to either a BPMN file or a FHIR resource file
     * @return a {@link ValidationOutput} containing all validation issues encountered
     */
    @Override
    public ValidationOutput validate(Path path)
    {
        // 1) Decide if it's BPMN or FHIR by checking the file extension (adapt as needed)
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".bpmn"))
        {
            return validateBpmn(path);
        }
        else
        {
            return validateFhir(path);
        }
    }

    /**
     * Validates a BPMN file by:
     * <ul>
     *   <li>Checking file existence.</li>
     *   <li>Ensuring readability via {@link #isFileReadable(Path)}.</li>
     *   <li>Attempting to parse the BPMN model.</li>
     *   <li>Finding the {@code processId} of the first {@link Process}.</li>
     *   <li>Searching upward for a {@code pom.xml} file to determine project root.</li>
     *   <li>Delegating to the {@link BpmnModelValidator} for BPMN-specific checks.</li>
     * </ul>
     *
     * @param path a {@link Path} pointing to a BPMN file
     * @return a {@link ValidationOutput} with any BPMN-related validation issues
     */
    private ValidationOutput validateBpmn(Path path)
    {
        List<AbstractValidationItem> allIssues = new ArrayList<>();

        // Check existence
        if (!Files.exists(path))
        {
            System.err.println("Error: The file does not exist: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // Check readability
        if (!isFileReadable(path))
        {
            System.err.println("Error: The file is not readable: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // Parse BPMN model
        BpmnModelInstance model;
        try
        {
            model = Bpmn.readModelFromFile(path.toFile());
        }
        catch (Exception e)
        {
            System.err.println("Error reading BPMN file: " + e.getMessage());
            e.printStackTrace();
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // Extract process ID
        String processId = extractProcessId(model);

        // Determine project root
        File projectRoot = getProjectRoot(path);

        // Validate BPMN model
        bpmnModelValidator.setProjectRoot(projectRoot);
        List<BpmnElementValidationItem> bpmnIssues =
                bpmnModelValidator.validateModel(model, path.toFile(), processId);

        allIssues.addAll(bpmnIssues);
        return buildOutput(allIssues);
    }

    /**
     * Validates a FHIR resource by:
     * <ul>
     *   <li>Checking file existence and readability.</li>
     *   <li>Delegating parsing and validation to {@link FhirResourceValidator}.</li>
     * </ul>
     *
     * @param path a {@link Path} pointing to a FHIR resource file
     * @return a {@link ValidationOutput} containing FHIR validation issues
     */
    private ValidationOutput validateFhir(Path path)
    {
        //todo
        return null;
    }

    /**
     * Checks if the given file is readable using {@link Files#isReadable(Path)}.
     * <p>
     * Can be overridden or mocked in tests to simulate various conditions.
     *
     * @param path the path to the file
     * @return {@code true} if the file is readable, {@code false} otherwise
     */
    protected boolean isFileReadable(Path path)
    {
        return Files.isReadable(path);
    }

    /**
     * Builds a {@link ValidationOutput} from the provided list of validation items.
     *
     * @param items a list of validation items
     * @return a new {@link ValidationOutput} containing all items
     */
    private ValidationOutput buildOutput(List<AbstractValidationItem> items)
    {
        return new ValidationOutput(items);
    }

    /**
     * Finds the project root by walking upward in the directory structure until a {@code pom.xml} is found.
     *
     * @param bpmnFilePath the path to the BPMN file (used to derive the starting directory)
     * @return the directory containing {@code pom.xml}, or the BPMN file's parent directory if not found
     */
    private File getProjectRoot(Path bpmnFilePath)
    {
        Path current = bpmnFilePath.getParent();
        while (current != null)
        {
            File pom = new File(current.toFile(), "pom.xml");
            if (pom.exists())
            {
                return current.toFile();
            }
            current = current.getParent();
        }

        // Fallback: the BPMN file's parent directory
        assert bpmnFilePath.getParent() != null;
        return bpmnFilePath.getParent().toFile();
    }

    /**
     * Extracts the {@code id} of the first {@link Process} element in the BPMN model.
     *
     * @param model the parsed {@link BpmnModelInstance}
     * @return the ID of the first process, or an empty string if none exists
     * @see <a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a>
     */
    private String extractProcessId(BpmnModelInstance model)
    {
        Collection<Process> processes = model.getModelElementsByType(Process.class);
        if (!processes.isEmpty())
        {
            Process process = processes.iterator().next();
            if (process.getId() != null && !process.getId().trim().isEmpty())
            {
                return process.getId();
            }
        }
        return "";
    }
}
