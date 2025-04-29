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
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * An implementation of the DSF Validator interface capable of validating
 * <strong>BPMN</strong> or <strong>FHIR</strong> files based on file extension.
 * </p>
 *
 * <p><strong>Folder Structure</strong>:</p>
 * <pre>
 * report/
 *   bpmnReports/
 *     success/
 *       bpmn_issues_foo.json
 *       aggregated.json   (ALL success items from BPMN)
 *     other/
 *       bpmn_issues_bar.json
 *       aggregated.json   (ALL other items from BPMN)
 *     bpmn_issues_aggregated.json  (ALL BPMN items)
 *
 * </pre>
 *
 * <p>
 * In every JSON file, a date/time is included as the first field under "timestamp".
 * </p>
 *
 * <h3>References</h3>
 * <ul>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/model-api/bpmn-model-api/">Camunda BPMN Model API</a></li>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://github.com/FasterXML/jackson-databind">Jackson JSON Project</a></li>
 * </ul>
 */
public class DsfValidatorImpl implements DsfValidator
{

    public DsfValidatorImpl()
    {

    }

    /**
     * Determines whether to validate as BPMN or FHIR based on file extension:
     * <ul>
     *   <li>.bpmn => BPMN</li>
     *   <li>.xml/.json => FHIR</li>
     *   <li>otherwise => returns {@code null}</li>
     * </ul>
     */
    @Override
    public ValidationOutput validate(Path path) {

        // A) ProjectFolder
        if (Files.isDirectory(path)) {
            File reportRoot = new File("report");
            reportRoot.mkdirs();

            List<AbstractValidationItem> bpmnItems =
                    validateAllBpmnFilesSplitNewStructure(path.toFile(), reportRoot);

            List<AbstractValidationItem> fhirItems =
                    Optional.ofNullable(
                            validateAllFhirResourcesSplitNewStructure(path.toFile(), reportRoot)
                    ).orElse(List.of());                     // ← avoid NPE

            List<AbstractValidationItem> all = new ArrayList<>(bpmnItems);
            all.addAll(fhirItems);

            ValidationOutput combined = new ValidationOutput(all);
            combined.writeResultsAsJson(new File(reportRoot, "aggregated.json"));
            return combined;
        }

        // B) single file
        return validateSingleFile(path);
    }



    // BPMN (Split with sub-aggregators)

    /**
     * <p>
     * Validates all BPMN files in <code>src/main/resources/bpe</code> and writes them to:
     * </p>
     * <ul>
     *   <li><code>report/bpmnReports/success</code> + an <code>aggregated.json</code> of all successes</li>
     *   <li><code>report/bpmnReports/other</code> + an <code>aggregated.json</code> of all others</li>
     *   <li><code>report/bpmnReports/bpmn_issues_aggregated.json</code> for all items (success + other)</li>
     * </ul>
     *
     * @param projectDir the root project directory
     * @param reportRoot the "report" folder
     * @return the full list of BPMN {@link AbstractValidationItem} discovered
     */
    public List<AbstractValidationItem> validateAllBpmnFilesSplitNewStructure(File projectDir, File reportRoot)
    {
        // 1) Setup subfolders
        File bpmnFolder = new File(reportRoot, "bpmnReports");
        File successFolder = new File(bpmnFolder, "success");
        File otherFolder = new File(bpmnFolder, "other");

        bpmnFolder.mkdirs();
        successFolder.mkdirs();
        otherFolder.mkdirs();

        // 2) Collect BPMN files
        File bpeRoot = findDirectoryRecursively(projectDir.toPath(), "src/main/resources/bpe");
        if (bpeRoot == null)
        {
            System.err.println("WARNING: 'src/main/resources/bpe' not found in " + projectDir.getAbsolutePath());
            return Collections.emptyList();
        }
        List<File> bpmnFiles = findFilesWithExtensionRecursively(bpeRoot.toPath(), ".bpmn");
        if (bpmnFiles.isEmpty())
        {
            System.err.println("No .bpmn files in " + bpeRoot.getAbsolutePath());
            return Collections.emptyList();
        }

        // We'll accumulate success items, other items, and everything
        List<AbstractValidationItem> allBpmnItems = new ArrayList<>();
        List<AbstractValidationItem> successAggregator = new ArrayList<>();
        List<AbstractValidationItem> otherAggregator = new ArrayList<>();

        BPMNValidator validator = new BPMNValidator();

        // 3) Validate each BPMN file
        for (File bpmnFile : bpmnFiles)
        {
            System.out.println("Validating BPMN file: " + bpmnFile.getName());
            ValidationOutput out = validator.validateBpmnFile(bpmnFile.toPath());
            out.printResults();

            allBpmnItems.addAll(out.validationItems());

            List<AbstractValidationItem> successItems = out.validationItems().stream()
                    .filter(this::isSuccessItem)
                    .collect(Collectors.toList());
            List<AbstractValidationItem> otherItems = out.validationItems().stream()
                    .filter(x -> !isSuccessItem(x))
                    .collect(Collectors.toList());

            successAggregator.addAll(successItems);
            otherAggregator.addAll(otherItems);

            // We pick the processId or fallback to filename
            String pid = out.getProcessId();
            if (pid == null || pid.isBlank())
            {
                pid = bpmnFile.getName().replace(".bpmn", "");
            }

            // Write success JSON if relevant
            if (!successItems.isEmpty())
            {
                ValidationOutput so = new ValidationOutput(successItems);
                File outFile = new File(successFolder, "bpmn_issues_" + pid + ".json");
                so.writeResultsAsJson(outFile);
            }

            // Write other JSON if relevant
            if (!otherItems.isEmpty())
            {
                ValidationOutput oo = new ValidationOutput(otherItems);
                File outFile = new File(otherFolder, "bpmn_issues_" + pid + ".json");
                oo.writeResultsAsJson(outFile);
            }
        }

        // 4) Write aggregated success
        if (!successAggregator.isEmpty())
        {
            ValidationOutput successOutput = new ValidationOutput(successAggregator);
            File aggSuccess = new File(successFolder, "aggregated.json");
            successOutput.writeResultsAsJson(aggSuccess);
        }

        // 5) Write aggregated other
        if (!otherAggregator.isEmpty())
        {
            ValidationOutput otherOutput = new ValidationOutput(otherAggregator);
            File aggOther = new File(otherFolder, "aggregated.json");
            otherOutput.writeResultsAsJson(aggOther);
        }

        // 6) Write aggregated BPMN (all items)
        if (!allBpmnItems.isEmpty())
        {
            ValidationOutput agg = new ValidationOutput(allBpmnItems);
            File allFile = new File(bpmnFolder, "bpmn_issues_aggregated.json");
            agg.writeResultsAsJson(allFile);
        }

        return allBpmnItems;
    }

    // FHIR (Split with sub-aggregators)
    /**
     * <p>
     * Validates all FHIR files in <code>src/main/resources/fhir</code> and writes them to:
     * </p>
     * <ul>
     *   <li><code>report/fhirReports/success</code> + an <code>aggregated.json</code> of all successes</li>
     *   <li><code>report/fhirReports/other</code> + an <code>aggregated.json</code> of all others</li>
     *   <li><code>report/fhirReports/fhir_issues_aggregated.json</code> for all items (success + other)</li>
     * </ul>
     *
     * @param projectDir the root project directory
     * @param reportRoot the "report" folder
     * @return the full list of FHIR {@link AbstractValidationItem} discovered
     */
    public List<AbstractValidationItem> validateAllFhirResourcesSplitNewStructure(File projectDir, File reportRoot)
    {
        //todo
        return null;
    }

    // Single-file BPMN  validations
    private ValidationOutput validateBpmn(Path path)
    {
        List<AbstractValidationItem> allIssues = new ArrayList<>();

        if (!Files.exists(path))
        {
            System.err.println("Error: The file does not exist: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return new ValidationOutput(allIssues);
        }

        if (!isFileReadable(path))
        {
            System.err.println("Error: The file is not readable: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return new ValidationOutput(allIssues);
        }

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
            return new ValidationOutput(allIssues);
        }

        String processId = extractProcessId(model);
        File projectRoot = getProjectRoot(path);

        BpmnModelValidator bpmnValidator = new BpmnModelValidator(projectRoot);
        List<BpmnElementValidationItem> items = bpmnValidator.validateModel(model, path.toFile(), processId);
        allIssues.addAll(items);

        return new ValidationOutput(allIssues);
    }

    private ValidationOutput validateFhir(Path path)
    {
        //todo
        return null;
    }

    // Common Utility
    /**
     * Determines if the validation item is "SUCCESS." Adjust if your success logic differs.
     *
     * @param item a validation item
     * @return {@code true} if severity == {@link ValidationSeverity#SUCCESS}, else false
     */
    private boolean isSuccessItem(AbstractValidationItem item)
    {
        return item.getSeverity() == ValidationSeverity.SUCCESS;
    }

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
     * Finds the project root by searching upwards for a {@code pom.xml}.
     * If none is found, returns the BPMN file's parent directory.
     *
     * @param filePath path to the BPMN file or maybe also other sources in the future
     * @return directory containing {@code pom.xml} or the file's parent if none found
     */
    private File getProjectRoot(Path filePath)
    {
        Path current = filePath.getParent();
        while (current != null)
        {
            File pom = new File(current.toFile(), "pom.xml");
            if (pom.exists()) {
                return current.toFile();
            }
            current = current.getParent();
        }
        // fallback
        if (filePath.getParent() != null)
        {
            return filePath.getParent().toFile();
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
            Process first = processes.iterator().next();
            if (first.getId() != null && !first.getId().isBlank())
            {
                return first.getId();
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
                    // ignore
                }
            }
        }
        return result;
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

    /**
     * <p>
     * Validates a single file based on its file extension.
     * </p>
     *
     * <p>
     * Supported file types:
     * <ul>
     *   <li><strong>BPMN files</strong> – Files ending with {@code .bpmn} will be validated using the BPMN validation logic.</li>
     *   <li><strong>FHIR files</strong> – Files ending with {@code .xml} or {@code .json} will be validated using the FHIR validation logic (to be implemented).</li>
     * </ul>
     * </p>
     *
     * <p>
     * If the file extension is not recognized, an empty {@link ValidationOutput} will be returned, and an error message will be printed.
     * </p>
     *
     * @param file the {@link Path} of the file to validate
     * @return a {@link ValidationOutput} containing validation results; empty if the file type is unsupported
     */
    private ValidationOutput validateSingleFile(Path file) {
        String fn = file.getFileName().toString().toLowerCase();

        if (fn.endsWith(".bpmn"))
            return validateBpmn(file);
        else if (fn.endsWith(".xml") || fn.endsWith(".json"))
            return validateFhir(file);          //TODO
        else {
            System.err.println("Unrecognized extension for: " + file);
            return new ValidationOutput(List.of());
        }
    }

    /**
     * @deprecated No longer used. The reporting structure now uses a fixed "report" directory instead of timestamped folders.
     */
    @Deprecated
    public File createReportsDirectory()
    {
        File reportDir = new File("report");
        if (!reportDir.exists() && !reportDir.mkdirs())
        {
            System.err.println("WARNING: Could not create 'report' folder at: " + reportDir.getAbsolutePath());
        }
        return reportDir;
    }
}
