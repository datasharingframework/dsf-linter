package dev.dsf.utils.validator;

import dev.dsf.utils.validator.bpmn.BpmnModelValidator;
import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;
import dev.dsf.utils.validator.item.UnparsableBpmnFileValidationItem;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * <p>
 * An implementation of the DSF Validator interface capable of validating either
 * <strong>BPMN</strong> or <strong>FHIR</strong> files based on file extension
 * or content.
 * </p>
 *
 * <p>
 * This class handles:
 * <ul>
 *   <li>Detection of BPMN vs FHIR based on file extension.</li>
 *   <li>Validation logic for both file types (BPMN).</li>
 *   <li>Recursive discovery of BPMN files in a project structure.</li>
 *   <li>Writing validation reports (per-file and aggregated) to JSON.</li>
 *   <li>Creation of a timestamped directory for validation reports.</li>
 * </ul>
 * </p>
 *
 * <h3>References</h3>
 * <ul>
 *   <li>BPMN 2.0: <a href="https://www.omg.org/spec/BPMN/2.0">https://www.omg.org/spec/BPMN/2.0</a></li>
 *   <li>Java I/O (File): <a href="https://docs.oracle.com/javase/8/docs/api/java/io/File.html">Oracle Docs</a></li>
 *   <li>Java NIO.2 File Operations: <a href="https://docs.oracle.com/javase/tutorial/essential/io/">Oracle Tutorial</a></li>
 * </ul>
 */
public class DsfValidatorImpl implements DsfValidator
{

    public DsfValidatorImpl()
    {

    }

    /**
     * @param path the {@link Path} to either a BPMN file or a FHIR resource file
     * @return a {@link ValidationOutput} containing all validation issues encountered
     */
    @Override
    public ValidationOutput validate(Path path)
    {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".bpmn"))
        {
            return validateBpmn(path);
        }
        else
        {
            return null;
        }
    }

    /**
     * <p>
     * Recursively finds all BPMN files under {@code src/main/resources/bpe}, validates them
     * one-by-one, writes a per-file JSON file (using the {@code processId}) and produces
     * an aggregated JSON file named {@code bpmn_issues_aggregated.json} in the given
     * {@code reportsDir}.
     * </p>
     *
     * <p>
     * Example usage within a calling class:
     * <pre>
     *   DsfValidatorImpl validator = new DsfValidatorImpl();
     *   validator.validateAllBpmnFiles(projectDirectory, reportOutputDirectory);
     * </pre>
     * </p>
     *
     * @param projectDir the root project directory (will search for {@code src/main/resources/bpe} inside it)
     * @param reportsDir the directory where per-file and aggregated JSON reports should be written
     */
    public void validateAllBpmnFiles(File projectDir, File reportsDir)
    {
        File bpmnRoot = findDirectoryRecursively(projectDir.toPath(), "src/main/resources/bpe");
        if (bpmnRoot == null)
        {
            System.err.println("WARNING: Could not find 'src/main/resources/bpe' in " + projectDir.getAbsolutePath());
            return;
        }

        List<File> bpmnFiles = findFilesWithExtensionRecursively(bpmnRoot.toPath(), ".bpmn");
        if (bpmnFiles.isEmpty())
        {
            System.err.println("No BPMN files found under: " + bpmnRoot.getAbsolutePath());
            return;
        }

        // Aggregator for all BPMN validation items
        List<AbstractValidationItem> allBpmnItems = new ArrayList<>();

        // Use the BPMNValidator for each file, or you could also delegate to validate(Path)
        BPMNValidator bpmnValidator = new BPMNValidator();

        for (File file : bpmnFiles)
        {
            System.out.println("\nValidating BPMN file: " + file.getName());
            ValidationOutput output = bpmnValidator.validateBpmnFile(file.toPath());
            output.printResults();

            // Add to aggregator
            allBpmnItems.addAll(output.validationItems());

            // Write per-file JSON named after the processId
            String processId = output.getProcessId();
            File jsonFile = new File(reportsDir, "bpmn_issues_" + processId + ".json");
            output.writeResultsAsJson(jsonFile);

            System.out.println("Validation completed for: " + file.getName()
                    + " => BPMN JSON: " + jsonFile.getAbsolutePath());
        }

        // Write aggregated BPMN JSON
        ValidationOutput aggregatedBpmn = new ValidationOutput(allBpmnItems);
        File aggregatedJson = new File(reportsDir, "bpmn_issues_aggregated.json");
        aggregatedBpmn.writeResultsAsJson(aggregatedJson);

        System.out.println("\nAll BPMN validation items aggregated in: " + aggregatedJson.getAbsolutePath());
    }


    public void validateAllFhirResources(File projectDir, File reportsDir)
    {
        //todo
    }

    /**
     * Validates a BPMN file by:
     * <ul>
     *   <li>Checking file existence.</li>
     *   <li>Ensuring readability via {@link #isFileReadable(Path)}.</li>
     *   <li>Parsing the BPMN model.</li>
     *   <li>Finding the {@code processId} of the first {@link Process}.</li>
     *   <li>Locating a {@code pom.xml} to determine the project root.</li>
     *   <li>Using {@link BpmnModelValidator} for BPMN-specific checks.</li>
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

        // Extract first process ID
        String processId = extractProcessId(model);

        // Determine project root (search for pom.xml)
        File projectRoot = getProjectRoot(path);

        // Validate with BpmnModelValidator
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
     * Override or mock in tests for specific scenarios.
     *
     * @param path the path to check
     * @return true if readable, false otherwise
     */
    protected boolean isFileReadable(Path path)
    {
        return Files.isReadable(path);
    }

    /**
     * Constructs a {@link ValidationOutput} from the provided list of validation items.
     *
     * @param items a list of validation items
     * @return a {@link ValidationOutput} containing all items
     */
    private ValidationOutput buildOutput(List<AbstractValidationItem> items)
    {
        return new ValidationOutput(items);
    }

    /**
     * Finds the project root by searching upwards for a {@code pom.xml}.
     * If none is found, returns the BPMN file's parent directory.
     *
     * @param bpmnFilePath path to the BPMN file
     * @return directory containing {@code pom.xml} or the file's parent if none found
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
        // Fallback
        assert bpmnFilePath.getParent() != null;
        return bpmnFilePath.getParent().toFile();
    }

    /**
     * Extracts the ID of the first {@link Process} in the BPMN model.
     *
     * @param model the BPMN model instance
     * @return the process ID if found, or an empty string otherwise
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

    /**
     * <p>
     * Recursively searches for a sub-path (e.g., "src/main/resources/bpe") starting
     * from the given root path.
     * </p>
     *
     * @param rootPath        The root directory from which to start searching
     * @param relativeSubPath The sub-path to locate (e.g., {@code src/main/resources/bpe})
     * @return a {@link File} pointing to the sub-directory if found, or {@code null} otherwise
     */
    private File findDirectoryRecursively(Path rootPath, String relativeSubPath)
    {
        Queue<Path> queue = new LinkedList<>();
        queue.offer(rootPath);

        while (!queue.isEmpty())
        {
            Path current = queue.poll();
            if (!Files.isDirectory(current))
            {
                continue;
            }

            Path candidate = current.resolve(relativeSubPath);
            if (Files.isDirectory(candidate))
            {
                return candidate.toFile();
            }

            try
            {
                Files.list(current)
                        .filter(Files::isDirectory)
                        .forEach(queue::offer);
            }
            catch (IOException e)
            {
                // Ignore
            }
        }
        return null;
    }

    /**
     * <p>
     * Recursively finds all files under the given {@code rootPath} that match the specified extension.
     * </p>
     *
     * @param rootPath  The root path to traverse
     * @param extension The file extension to match (e.g., ".bpmn", ".xml")
     * @return a list of {@link File} objects that match the specified extension
     */
    private List<File> findFilesWithExtensionRecursively(Path rootPath, String extension)
    {
        List<File> result = new ArrayList<>();
        Deque<Path> stack = new ArrayDeque<>();
        stack.push(rootPath);

        while (!stack.isEmpty())
        {
            Path currentDir = stack.pop();
            if (Files.isDirectory(currentDir))
            {
                try
                {
                    Files.list(currentDir).forEach(child -> {
                        if (Files.isDirectory(child))
                        {
                            stack.push(child);
                        }
                        else if (child.getFileName().toString().toLowerCase().endsWith(extension))
                        {
                            result.add(child.toFile());
                        }
                    });
                }
                catch (IOException e)
                {
                    // skip
                }
            }
        }
        return result;
    }

    /**
     * Creates and returns a new timestamped report directory in the current working directory.
     * For example, the directory name might look like: {@code reports_09042025_153045}.
     * <p>
     * If the directory cannot be created, a warning is printed to {@code stderr}.
     *
     * @return a {@link File} pointing to the created directory (which may already exist,
     *         or be {@code null} if creation fails)
     */
    public File createReportsDirectory()
    {
        String dateTimeStr = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
        File reportsDir = new File("reports_" + dateTimeStr);

        if (!reportsDir.exists())
        {
            boolean created = reportsDir.mkdirs();
            if (!created)
            {
                System.err.println("WARNING: Failed to create reports directory: " + reportsDir.getAbsolutePath());
            }
        }
        return reportsDir;
    }

    /**
     * Recursively searches for BPMN files under {@code src/main/resources/bpe} in the given {@code projectDir},
     * validates them, and returns a combined {@link ValidationOutput} of all issues.
     * <p>
     * This method is primarily intended for testing, so that tests can assert
     * against the aggregated {@link ValidationOutput}.
     *
     * @param projectDir the root project directory
     * @return a {@link ValidationOutput} containing all accumulated BPMN validation items
     */
    public ValidationOutput validateAllBpmnFilesForTest(File projectDir)
    {
        File bpmnRoot = findDirectoryRecursively(projectDir.toPath(), "src/main/resources/bpe");
        if (bpmnRoot == null)
        {
            System.err.println("WARNING: Could not find 'src/main/resources/bpe' in " + projectDir.getAbsolutePath());
            // No BPMN files => return an empty ValidationOutput
            return new ValidationOutput(Collections.emptyList());
        }

        List<File> bpmnFiles = findFilesWithExtensionRecursively(bpmnRoot.toPath(), ".bpmn");
        if (bpmnFiles.isEmpty())
        {
            System.err.println("No BPMN files found under: " + bpmnRoot.getAbsolutePath());
            // No BPMN files => return an empty ValidationOutput
            return new ValidationOutput(Collections.emptyList());
        }

        // Use the BPMNValidator for each file
        List<AbstractValidationItem> allBpmnItems = new ArrayList<>();
        BPMNValidator bpmnValidator = new BPMNValidator();

        for (File file : bpmnFiles)
        {
            // Validate this single BPMN file
            ValidationOutput output = bpmnValidator.validateBpmnFile(file.toPath());
            allBpmnItems.addAll(output.validationItems());
        }

        return new ValidationOutput(allBpmnItems);
    }
}

