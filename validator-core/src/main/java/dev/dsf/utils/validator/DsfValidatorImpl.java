package dev.dsf.utils.validator;

import dev.dsf.utils.validator.bpmn.BpmnModelValidator;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;
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
 *       <li>Validates the BPMN model via a {@link BpmnModelValidator} instantiated with the project root.</li>
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

    /**
     * Constructs a new {@code DsfValidatorImpl} with the default FHIR validator.
     * Note that the BPMN validator is instantiated per file validation with the
     * appropriate project root.
     */
    public DsfValidatorImpl()
    {

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
     *   <li>Instantiate a {@link BpmnModelValidator} with the project root and validate the BPMN model.</li>
     * </ol>
     *
     * <p><strong>FHIR logic flow:</strong>
     * <ol>
     *   <li>Check if the file exists and is readable.</li>
     * </ol>
     *
     * @param path the {@link Path} to either a BPMN file or a FHIR resource file
     * @return a {@link ValidationOutput} containing all validation issues encountered
     */
    @Override
    public ValidationOutput validate(Path path)
    {
        // Determine file type by extension (adapt logic as needed)
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

        // Check if the file exists
        if (!Files.exists(path))
        {
            System.err.println("Error: The file does not exist: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // Check if the file is readable
        if (!isFileReadable(path))
        {
            System.err.println("Error: The file is not readable: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // Attempt to parse the BPMN model
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

        // Extract the first BPMN process ID
        String processId = extractProcessId(model);

        // Determine the project root by searching for a pom.xml file
        File projectRoot = getProjectRoot(path);

        // Instantiate a new BPMN validator with the determined project root
        BpmnModelValidator validator = new BpmnModelValidator(projectRoot);
        List<BpmnElementValidationItem> bpmnIssues = validator.validateModel(model, path.toFile(), processId);
        allIssues.addAll(bpmnIssues);
        return buildOutput(allIssues);
    }


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
     * Constructs a {@link ValidationOutput} from the provided list of validation items.
     *
     * @param items a list of validation items
     * @return a new {@link ValidationOutput} containing all items
     */
    private ValidationOutput buildOutput(List<AbstractValidationItem> items)
    {
        return new ValidationOutput(items);
    }

    /**
     * Finds the project root directory by traversing upward from the BPMN file path until a {@code pom.xml}
     * file is located. If no {@code pom.xml} is found, the BPMN file's parent directory is returned.
     *
     * @param bpmnFilePath the path to the BPMN file
     * @return the directory containing {@code pom.xml} or the BPMN file's parent directory if not found
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
        // Fallback to the BPMN file's parent directory
        assert bpmnFilePath.getParent() != null;
        return bpmnFilePath.getParent().toFile();
    }

    /**
     * Extracts the {@code id} of the first {@link Process} element found in the BPMN model.
     *
     * @param model the parsed {@link BpmnModelInstance}
     * @return the process ID if found; otherwise, an empty string
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