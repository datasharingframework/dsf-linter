package dev.dsf.utils.validator;

import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.bpmn.BpmnModelValidator;
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
import java.util.stream.Collectors;
import java.util.Date;

/**
 * <p>
 * An implementation of the DSF Validator interface capable of validating either
 * <strong>BPMN</strong> or <strong>FHIR</strong> files based on file extension
 * or content.
 * </p>
 *
 * <p>
 * Primary responsibilities:
 * <ul>
 *   <li>BPMN vs FHIR detection (by file extension).</li>
 *   <li>BPMN validation logic (via {@link BpmnModelValidator}).</li>
 *   <li>Recursive discovery of BPMN files in a project structure.</li>
 *   <li>Writing validation reports (per-file and aggregated) to <em>timestamped</em> subfolders.</li>
 * </ul>
 * </p>
 *
 * <h2>Splitting by Severity: "success" vs. "others"</h2>
 * <p>
 * This class supports a mode where validated results are split into a <strong>success</strong>
 * folder (only {@code SUCCESS} severities) and an <strong>others</strong> folder (everything else)
 * under a single timestamped report directory.
 * </p>
 *
 * <h3>References</h3>
 * <ul>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/model-api/bpmn-model-api/">Camunda BPMN Model API</a></li>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://github.com/FasterXML/jackson-databind">Jackson Project for JSON</a></li>
 * </ul>
 */
public class DsfValidatorImpl implements DsfValidator
{
    /**
     * Constructs a default {@code DsfValidatorImpl}.
     */
    public DsfValidatorImpl()
    {
        // no-op
    }

    /**
     * Determines whether to validate as BPMN or FHIR based on file extension.
     * If the file ends with {@code .bpmn}, delegates to {@link #validateBpmn(Path)};
     * otherwise returns {@code null} or potentially a future FHIR validation.
     *
     * @param path the {@link Path} to either a BPMN file or a FHIR resource file
     * @return a {@link ValidationOutput} containing all validation issues encountered, or null if not recognized
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
            // Future expansions: handle .json or .xml for FHIR
            return null;
        }
    }

    /**
     * <p>
     * Validates a single BPMN file by:
     * </p>
     * <ol>
     *   <li>Checking file existence.</li>
     *   <li>Ensuring readability via {@link #isFileReadable(Path)}.</li>
     *   <li>Parsing the BPMN model with the Camunda API.</li>
     *   <li>Locating the first {@link Process} ID.</li>
     *   <li>Searching upward for a {@code pom.xml} to find the project root.</li>
     *   <li>Using {@link BpmnModelValidator} to perform BPMN-specific checks.</li>
     * </ol>
     *
     * @param path a {@link Path} pointing to a BPMN file
     * @return a {@link ValidationOutput} with any BPMN-related validation issues
     */
    private ValidationOutput validateBpmn(Path path)
    {
        List<AbstractValidationItem> allIssues = new ArrayList<>();

        // 1) Check existence
        if (!Files.exists(path))
        {
            System.err.println("Error: The file does not exist: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // 2) Check readability
        if (!isFileReadable(path))
        {
            System.err.println("Error: The file is not readable: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // 3) Parse BPMN model
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

        // 4) Extract process ID
        String processId = extractProcessId(model);

        // 5) Determine project root
        File projectRoot = getProjectRoot(path);

        // 6) Validate with BpmnModelValidator
        //    We pass only the short BPMN file name to ensure the "bpmnFile" field is short.
        File shortNameFile = new File(path.getFileName().toString());
        BpmnModelValidator validator = new BpmnModelValidator(projectRoot);
        List<BpmnElementValidationItem> bpmnIssues = validator.validateModel(model, shortNameFile, processId);
        allIssues.addAll(bpmnIssues);

        return buildOutput(allIssues);
    }

    /**
     * <p>
     * Recursively finds all BPMN files under {@code src/main/resources/bpe}, validates them
     * one-by-one, writes <strong>per-file JSON</strong> using the {@code processId} in the filename,
     * and finally writes an <strong>aggregated JSON</strong> file named
     * {@code bpmn_issues_aggregated.json}.
     * </p>
     *
     * <p>
     * This method <strong>does not</strong> split results by severity. For splitting, see
     * {@link #validateAllBpmnFilesSplitBySeverity(File, File)}.
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

        // Use BPMNValidator for each file (or call this.validate(...))
        BPMNValidator bpmnValidator = new BPMNValidator();

        for (File file : bpmnFiles)
        {
            System.out.println("\nValidating BPMN file: " + file.getName());
            ValidationOutput output = bpmnValidator.validateBpmnFile(file.toPath());
            output.printResults();

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

    /**
     * <p>
     * An advanced approach that writes BPMN validation results into two subfolders:
     * <strong>success</strong> and <strong>others</strong>. The method:
     * </p>
     * <ul>
     *   <li>Creates a <em>timestamped</em> folder (e.g., {@code reports_09042025_160816}) in {@code reportsRootDir}.</li>
     *   <li>Inside that folder, creates subfolders {@code success} and {@code others}.</li>
     *   <li>Validates each BPMN file under {@code src/main/resources/bpe}, splitting items by severity:</li>
     *   <ul>
     *     <li>{@code success} if {@link ValidationSeverity#SUCCESS}</li>
     *     <li>{@code others} if anything else (WARN, ERROR, INFO, DEBUG, etc.)</li>
     *   </ul>
     *   <li>Generates up to two JSON files per BPMN: one for success items, one for other items.</li>
     *   <li>Also writes two aggregated JSON files (success/others) across <em>all</em> validated BPMNs.</li>
     * </ul>
     *
     * @param projectDir      the root project directory
     * @param reportsRootDir  the directory in which to create the new timestamped reports folder
     */
    public void validateAllBpmnFilesSplitBySeverity(File projectDir, File reportsRootDir)
    {
        // 1) Locate the "src/main/resources/bpe" folder
        File bpmnRoot = findDirectoryRecursively(projectDir.toPath(), "src/main/resources/bpe");
        if (bpmnRoot == null)
        {
            System.err.println("WARNING: Could not find 'src/main/resources/bpe' in " + projectDir.getAbsolutePath());
            return;
        }

        // 2) Collect all .bpmn files
        List<File> bpmnFiles = findFilesWithExtensionRecursively(bpmnRoot.toPath(), ".bpmn");
        if (bpmnFiles.isEmpty())
        {
            System.err.println("No BPMN files found under: " + bpmnRoot.getAbsolutePath());
            return;
        }

        // 3) Create a timestamped top-level folder
        String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
        File topLevelReports = new File(reportsRootDir, "reports_" + timeStamp);
        if (!topLevelReports.exists() && !topLevelReports.mkdirs())
        {
            System.err.println("WARNING: Could not create top-level reports folder: " + topLevelReports.getAbsolutePath());
            return;
        }

        // 4) Create "success" and "others" subfolders
        File successFolder = new File(topLevelReports, "success");
        File othersFolder = new File(topLevelReports, "others");
        successFolder.mkdirs();
        othersFolder.mkdirs();

        // We'll accumulate success items and others items across all BPMN
        List<AbstractValidationItem> aggregatedSuccess = new ArrayList<>();
        List<AbstractValidationItem> aggregatedOthers = new ArrayList<>();

        // 5) Validate each BPMN file
        BPMNValidator bpmnValidator = new BPMNValidator();

        for (File bpmnFile : bpmnFiles)
        {
            System.out.println("\nValidating BPMN file: " + bpmnFile.getName());
            ValidationOutput output = bpmnValidator.validateBpmnFile(bpmnFile.toPath());
            output.printResults();

            // Separate success from others
            List<AbstractValidationItem> successItems = output.validationItems().stream()
                    .filter(this::isSuccessItem)
                    .collect(Collectors.toList());
            List<AbstractValidationItem> otherItems = output.validationItems().stream()
                    .filter(item -> !isSuccessItem(item))
                    .collect(Collectors.toList());

            aggregatedSuccess.addAll(successItems);
            aggregatedOthers.addAll(otherItems);

            // If we have at least one success item, write a success JSON
            if (!successItems.isEmpty())
            {
                ValidationOutput successOutput = new ValidationOutput(successItems);
                String successPid = successOutput.getProcessId();
                File outFile = new File(successFolder, "bpmn_issues_" + successPid + ".json");
                successOutput.writeResultsAsJson(outFile);
                System.out.println("  -> Wrote SUCCESS items to " + outFile.getAbsolutePath());
            }
            // If we have at least one non-success item, write an others JSON
            if (!otherItems.isEmpty())
            {
                ValidationOutput othersOutput = new ValidationOutput(otherItems);
                String othersPid = othersOutput.getProcessId();
                File outFile = new File(othersFolder, "bpmn_issues_" + othersPid + ".json");
                othersOutput.writeResultsAsJson(outFile);
                System.out.println("  -> Wrote OTHER items to " + outFile.getAbsolutePath());
            }
        }

        // 6) Write aggregated JSON for success
        if (!aggregatedSuccess.isEmpty())
        {
            ValidationOutput aggSuccessOut = new ValidationOutput(aggregatedSuccess);
            File aggFile = new File(successFolder, "bpmn_issues_aggregated.json");
            aggSuccessOut.writeResultsAsJson(aggFile);
            System.out.println("\nAggregated success items written to: " + aggFile.getAbsolutePath());
        }

        // 7) Write aggregated JSON for others
        if (!aggregatedOthers.isEmpty())
        {
            ValidationOutput aggOthersOut = new ValidationOutput(aggregatedOthers);
            File aggFile = new File(othersFolder, "bpmn_issues_aggregated.json");
            aggOthersOut.writeResultsAsJson(aggFile);
            System.out.println("Aggregated other items written to: " + aggFile.getAbsolutePath());
        }

        System.out.println("\nValidation completed. See folder: " + topLevelReports.getAbsolutePath());
    }

    /**
     * Determines if the validation item is "SUCCESS." Adjust if your success logic differs.
     *
     * @param item a validation item
     * @return {@code true} if severity == {@link ValidationSeverity#SUCCESS}, else false
     */
    private boolean isSuccessItem(AbstractValidationItem item)
    {
        return (item.getSeverity() == ValidationSeverity.SUCCESS);
    }

    /**
     * Creates and returns a new timestamped report directory in the current working directory.
     * For example, the directory name might look like: {@code reports_09042025_153045}.
     *
     * @return a {@link File} pointing to the created directory (or an existing one if it was already there)
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
     * Useful for testing, so that tests can assert on a single aggregated output.
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
            return new ValidationOutput(Collections.emptyList());
        }

        List<File> bpmnFiles = findFilesWithExtensionRecursively(bpmnRoot.toPath(), ".bpmn");
        if (bpmnFiles.isEmpty())
        {
            System.err.println("No BPMN files found under: " + bpmnRoot.getAbsolutePath());
            return new ValidationOutput(Collections.emptyList());
        }

        List<AbstractValidationItem> allBpmnItems = new ArrayList<>();
        BPMNValidator bpmnValidator = new BPMNValidator();

        for (File file : bpmnFiles)
        {
            ValidationOutput output = bpmnValidator.validateBpmnFile(file.toPath());
            allBpmnItems.addAll(output.validationItems());
        }

        return new ValidationOutput(allBpmnItems);
    }

    // Helper Methods

    /**
     * Checks if the given file is readable using {@link Files#isReadable(Path)}.
     * Override or mock in tests for special scenarios.
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
        if (bpmnFilePath.getParent() != null)
        {
            return bpmnFilePath.getParent().toFile();
        }
        return new File(".");
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
     * Recursively searches for a sub-path (e.g., {@code src/main/resources/bpe}) starting
     * from the given root path.
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
                // ignore
            }
        }
        return null;
    }

    /**
     * Recursively finds all files under the given {@code rootPath} that match
     * the specified extension.
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
     * <p>
     * Future method for validating FHIR. Currently unimplemented.
     * </p>
     *
     * @param path file path to FHIR resource
     * @return a {@link ValidationOutput} with FHIR validation issues (currently {@code null})
     */
    @SuppressWarnings("unused")
    private ValidationOutput validateFhir(Path path)
    {
        //todo
        return null;
    }
}
