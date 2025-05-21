package dev.dsf.utils.validator;

import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.bpmn.BpmnModelValidator;
import dev.dsf.utils.validator.fhir.FhirResourceValidator;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;
import dev.dsf.utils.validator.item.FhirElementValidationItem;
import dev.dsf.utils.validator.item.UnparsableBpmnFileValidationItem;
import dev.dsf.utils.validator.util.FhirAuthorizationCache;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static dev.dsf.utils.validator.util.ReportCleaner.prepareCleanReportDirectory;

/**
 * <h2>DSF Validator Implementation</h2>
 *
 * <p>
 * This class provides a full implementation of the {@link DsfValidator} interface and supports
 * validation of both BPMN and FHIR resources within a DSF project.
 * The validator determines the type of file (BPMN or FHIR) based on its extension and applies
 * appropriate validation logic. It also supports recursive validation of entire project directories.
 * </p>
 *
 * <h3>Validation Modes</h3>
 * <ul>
 *   <li><b>Single File Validation</b> – Supports individual BPMN (.bpmn) and FHIR (.xml/.json) files</li>
 *   <li><b>Project Folder Validation</b> – Recursively validates all BPMN and FHIR resources
 *       under <code>src/main/resources/bpe</code> and <code>src/main/resources/fhir</code></li>
 * </ul>
 *
 * <h3>Report Output Structure</h3>
 * <p>
 * The validator writes its output to the {@code report/} folder using the following structure:
 * </p>
 *
 * <pre>
 * report/
 * ├── bpmnReports/
 * │   ├── success/
 * │   │   ├── bpmn_issues_*.json
 * │   │   └── bpmn_success_aggregated.json
 * │   ├── other/
 * │   │   ├── bpmn_issues_*.json
 * │   │   └── bpmn_other_aggregated.json
 * │   └── bpmn_issues_aggregated.json
 * ├── fhirReports/
 * │   ├── success/
 * │   │   ├── fhir_issues_*.json
 * │   │   └── fhir_success_aggregated.json
 * │   ├── other/
 * │   │   ├── fhir_issues_*.json
 * │   │   └── fhir_other_aggregated.json
 * │   └── fhir_issues_aggregated.json
 * └── aggregated.json
 * </pre>
 *
 * <p>
 * Each individual report file contains a list of {@link dev.dsf.utils.validator.item.AbstractValidationItem}
 * with timestamps and categorized validation results.
 * </p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * DsfValidator validator = new DsfValidatorImpl();
 * ValidationOutput result = validator.validate(Path.of("src/main/resources/fhir"));
 * }</pre>
 *
 * <h3>See Also</h3>
 * <ul>
 *   <li>{@link BPMNValidator}</li>
 *   <li>{@link FhirResourceValidator}</li>
 *   <li>{@link dev.dsf.utils.validator.ValidationOutput}</li>
 * </ul>
 *
 */
public class DsfValidatorImpl implements DsfValidator

{
    private final FhirResourceValidator fhirResourceValidator;

    /**
     * Constructs a default {@code DsfValidatorImpl} that includes a
     * {@link FhirResourceValidator} for validating FHIR resources.
     */
    public DsfValidatorImpl()
    {
        this.fhirResourceValidator = new FhirResourceValidator();
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

        // add DSF‑CodeSystem‑Cache
        File projectRoot = Files.isDirectory(path)
                ? path.toFile()
                : getProjectRoot(path);
        FhirAuthorizationCache.seedFromProjectFolder(projectRoot);
        // A) ProjectFolder
        if (Files.isDirectory(path)) {
            File reportRoot = new File("report");
            prepareCleanReportDirectory(reportRoot);

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
            if (pid.isBlank())
            {
                pid = bpmnFile.getName().replace(".bpmn", "");
            }

            // Determine containing folder name (e.g. “ping” or “pong”)
            String parentFolder = bpmnFile.getParentFile().getName();

            // Write success JSON if relevant
            if (!successItems.isEmpty())
            {
                ValidationOutput so = new ValidationOutput(successItems);
                File outFile = new File(successFolder,
                        "bpmn_issues_" + parentFolder + "_" + pid + ".json");
                so.writeResultsAsJson(outFile);
            }

            // Write other JSON if relevant
            if (!otherItems.isEmpty())
            {
                ValidationOutput oo = new ValidationOutput(otherItems);
                File outFile = new File(otherFolder,
                        "bpmn_issues_" + parentFolder + "_" + pid + ".json");
                oo.writeResultsAsJson(outFile);
            }
        }

        // 4+5) Write aggregated BPMN sub-reports
        writeAggregatedReport("bpmn_success", successAggregator, successFolder);
        writeAggregatedReport("bpmn_other",   otherAggregator,   otherFolder);
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
        // 1) Setup subfolders
        File fhirFolder = new File(reportRoot, "fhirReports");
        File successFolder = new File(fhirFolder, "success");
        File otherFolder = new File(fhirFolder, "other");

        fhirFolder.mkdirs();
        successFolder.mkdirs();
        otherFolder.mkdirs();

        // 2) Collect FHIR files (.xml or .json as needed; here we use .xml)
        File fhirRoot = findDirectoryRecursively(projectDir.toPath(), "src/main/resources/fhir");
        if (fhirRoot == null)
        {
            System.err.println("WARNING: 'src/main/resources/fhir' not found in " + projectDir.getAbsolutePath());
            return Collections.emptyList();
        }
        List<File> fhirFiles = findFilesWithExtensionRecursively(fhirRoot.toPath(), ".xml");
        if (fhirFiles.isEmpty())
        {
            System.err.println("No .xml FHIR files in " + fhirRoot.getAbsolutePath());
            return Collections.emptyList();
        }

        // We'll accumulate success, other, and everything
        List<AbstractValidationItem> allFhirItems = new ArrayList<>();
        List<AbstractValidationItem> successAggregator = new ArrayList<>();
        List<AbstractValidationItem> otherAggregator = new ArrayList<>();

        // 3) Validate each FHIR file
        for (File f : fhirFiles)
        {
            System.out.println("Validating FHIR file: " + f.getName());
            ValidationOutput output = fhirResourceValidator.validateSingleFile(f.toPath());
            output.printResults();

            allFhirItems.addAll(output.validationItems());

            List<AbstractValidationItem> successItems = output.validationItems().stream()
                    .filter(this::isSuccessItem)
                    .collect(Collectors.toList());
            List<AbstractValidationItem> otherItems = output.validationItems().stream()
                    .filter(x -> !isSuccessItem(x))
                    .collect(Collectors.toList());

            successAggregator.addAll(successItems);
            otherAggregator.addAll(otherItems);

            // base name
            String baseName = f.getName();
            int idx = baseName.lastIndexOf('.');
            if (idx > 0)
            {
                baseName = baseName.substring(0, idx);
            }

            // Determine containing folder name (the last segment under “fhir”)
            String parentFolder = f.getParentFile().getName();
            // success
            if (!successItems.isEmpty())
            {
                ValidationOutput so = new ValidationOutput(successItems);
                File outFile = new File(successFolder,
                        "fhir_issues_" + parentFolder + "_" + baseName + ".json");
                so.writeResultsAsJson(outFile);
            }
            // other
            if (!otherItems.isEmpty())
            {
                ValidationOutput oo = new ValidationOutput(otherItems);
                File outFile = new File(otherFolder,
                        "fhir_issues_" + parentFolder + "_" + baseName + ".json");
                oo.writeResultsAsJson(outFile);
            }
        }

        // 4+5) Write aggregated FHIR sub-reports
        writeAggregatedReport("fhir_success", successAggregator, successFolder);
        writeAggregatedReport("fhir_other",   otherAggregator,   otherFolder);

        // 6) Write aggregated FHIR (all items)
        if (!allFhirItems.isEmpty())
        {
            ValidationOutput aggFhir = new ValidationOutput(allFhirItems);
            File allFile = new File(fhirFolder, "fhir_issues_aggregated.json");
            aggFhir.writeResultsAsJson(allFile);
        }

        return allFhirItems;
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

        if (isFileReadable(path))
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
        List<AbstractValidationItem> allIssues = new ArrayList<>();
        if (!Files.exists(path))
        {
            allIssues.add(new FhirElementValidationItem(
                    "File not found",
                    ValidationSeverity.ERROR,
                    path.getFileName().toString()));
            return new ValidationOutput(allIssues);
        }
        if (isFileReadable(path))
        {
            allIssues.add(new FhirElementValidationItem(
                    "File not readable",
                    ValidationSeverity.ERROR,
                    path.getFileName().toString()));
            return new ValidationOutput(allIssues);
        }

        ValidationOutput single = fhirResourceValidator.validateSingleFile(path);
        allIssues.addAll(single.validationItems());
        return new ValidationOutput(allIssues);
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
        return !Files.isReadable(path);
    }
    /**
     * Attempts to locate the root directory of a Maven project by traversing up the directory tree
     * from the given file path until a {@code pom.xml} file is found.
     * <p>
     * If no {@code pom.xml} is found during the traversal, the method falls back to returning
     * the parent directory of the given path. If the parent is also {@code null}, it returns
     * the current working directory ({@code "."}).
     * </p>
     *
     * @param filePath the path to a file or directory from which to start the search
     * @return the directory containing {@code pom.xml}, or the file's parent directory,
     *         or the current working directory if neither is found
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
     * Recursively searches for a sub-directory that matches either
     * <ul>
     *   <li>the full Maven style path (e.g. {@code src/main/resources/bpe}) or</li>
     *   <li>the plain folder name (e.g. {@code bpe})</li>
     * </ul>
     *
     * @param rootPath        the directory from which to start searching
     * @param relativeSubPath the Maven-style sub-path to locate
     * @return a {@link File} pointing to the first matching directory, or {@code null} if nothing is found
     */
    private File findDirectoryRecursively(Path rootPath, String relativeSubPath)
    {
        // Extract the last path segment once so we do not compute it in every loop
        String simpleName = Paths.get(relativeSubPath).getFileName().toString();

        Queue<Path> queue = new LinkedList<>();
        queue.offer(rootPath);

        while (!queue.isEmpty())
        {
            Path current = queue.poll();
            if (!Files.isDirectory(current))
                continue;

            // 1) Try the full Maven-style path
            Path candidateFull = current.resolve(relativeSubPath);
            if (Files.isDirectory(candidateFull))
                return candidateFull.toFile();

            // 2) Fallback: try only the last segment (flat layout)
            Path candidateSimple = current.resolve(simpleName);
            if (Files.isDirectory(candidateSimple))
                return candidateSimple.toFile();

            // Enqueue sub-directories for breadth-first search
            try
            {
                Files.list(current)
                        .filter(Files::isDirectory)
                        .forEach(queue::offer);
            }
            catch (IOException e)
            {
                // Ignore unreadable folders and continue searching
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
            return validateFhir(file);
        else {
            System.err.println("Unrecognized extension for: " + file);
            return new ValidationOutput(List.of());
        }
    }
    /**
     * Writes an aggregated JSON file named "<prefix>_aggregated.json" in das gegebene Verzeichnis.
     *
     * @param prefix   z. B. "bpmn_success", "bpmn_other", "fhir_success", "fhir_other"
     * @param items    die List<AbstractValidationItem> zum Aggregieren
     * @param folder   das Ziel-Verzeichnis
     */
    private void writeAggregatedReport(String prefix,
                                       List<AbstractValidationItem> items,
                                       File folder) {
        if (items.isEmpty()) return;
        ValidationOutput output = new ValidationOutput(items);
        File outFile = new File(folder, prefix + "_aggregated.json");
        output.writeResultsAsJson(outFile);
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
