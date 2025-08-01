package dev.dsf.utils.validator;

import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.bpmn.BpmnModelValidator;
import dev.dsf.utils.validator.fhir.FhirResourceValidator;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;
import dev.dsf.utils.validator.item.FhirElementValidationItem;
import dev.dsf.utils.validator.item.UnparsableBpmnFileValidationItem;
import dev.dsf.utils.validator.item.PluginValidationItem;
import dev.dsf.utils.validator.item.PluginValidationItemSuccess;
import dev.dsf.utils.validator.item.MissingServiceLoaderRegistrationValidationItem;
import dev.dsf.utils.validator.util.FhirAuthorizationCache;
import dev.dsf.utils.validator.util.FhirFileUtils;
import dev.dsf.utils.validator.util.ApiRegistrationValidationSupport;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.stream.Collectors;

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
 * │   ├── success/                             ← Individual and aggregated BPMN success items
 * │   ├── other/                               ← BPMN items with warnings/errors
 * │   └── bpmn_issues_aggregated.json
 * ├── fhirReports/
 * │   ├── success/                             ← Individual and aggregated FHIR success items
 * │   ├── other/                               ← FHIR items with warnings/errors
 * │   └── fhir_issues_aggregated.json
 * ├── pluginReports/                           ← Plugin validation directory
 * │   ├── success/                             ← Contains only PluginValidationItemSuccess
 * │   ├── other/                               ← Contains PluginValidationItems excluding success
 * │   └── plugin_issues_aggregated.json        ← All PluginValidationItems (success + others)
 * └── aggregated.json                          ← Combined BPMN + FHIR + Plugin validation results
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
            File reportRoot = new File(System.getProperty("dsf.report.dir", "report"));

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

    /**
     * Validates a single file by determining its type based on file extension.
     *
     * @param path the file to validate
     * @return validation output with results, or empty output if file type is unsupported
     */
    private ValidationOutput validateSingleFile(Path path) {
        if (path == null || !Files.exists(path)) {
            System.err.println("Error: File does not exist: " + path);
            return new ValidationOutput(Collections.emptyList());
        }

        String fileName = path.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".bpmn")) {
            return validateBpmn(path);
        } else if (fileName.endsWith(".xml") || fileName.endsWith(".json")) {
            // Check if it's actually a FHIR file
            if (FhirFileUtils.isFhirFile(path)) {
                return validateFhir(path);
            } else {
                System.err.println("Warning: File " + fileName + " has XML/JSON extension but is not a valid FHIR file");
                return new ValidationOutput(Collections.emptyList());
            }
        } else {
            System.err.println("Warning: Unsupported file type: " + fileName + ". Supported types: .bpmn, .xml (FHIR), .json (FHIR)");
            return new ValidationOutput(Collections.emptyList());
        }
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
        bpmnFolder.mkdirs();

        // 2) Collect BPMN files using unified finder
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

        // 3) Validate each BPMN file and write individual reports
        File successFolder = new File(bpmnFolder, "success");
        File otherFolder = new File(bpmnFolder, "other");
        successFolder.mkdirs();
        otherFolder.mkdirs();

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

            // Write individual file reports
            String pid = out.getProcessId();
            if (pid.isBlank())
            {
                pid = bpmnFile.getName().replace(".bpmn", "");
            }
            String parentFolder = bpmnFile.getParentFile().getName();

            if (!successItems.isEmpty())
            {
                ValidationOutput so = new ValidationOutput(successItems);
                File outFile = new File(successFolder,
                        "bpmn_issues_" + parentFolder + "_" + pid + ".json");
                so.writeResultsAsJson(outFile);
            }

            if (!otherItems.isEmpty())
            {
                ValidationOutput oo = new ValidationOutput(otherItems);
                File outFile = new File(otherFolder,
                        "bpmn_issues_" + parentFolder + "_" + pid + ".json");
                oo.writeResultsAsJson(outFile);
            }
        }

        // 4) Write unified sub-reports using common method
        writeSubReports("bpmn", successAggregator, otherAggregator, bpmnFolder);

        return allBpmnItems;
    }

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
        fhirFolder.mkdirs();

        // 2) Collect FHIR files using unified finder
        File fhirRoot = findDirectoryRecursively(projectDir.toPath(), "src/main/resources/fhir");
        if (fhirRoot == null)
        {
            System.err.println("WARNING: 'src/main/resources/fhir' not found in " + projectDir.getAbsolutePath());
            return Collections.emptyList();
        }

        List<File> fhirFiles = findFhirFilesRecursively(fhirRoot.toPath());

        if (fhirFiles.isEmpty())
        {
            System.err.println("No .xml or .json FHIR files in " + fhirRoot.getAbsolutePath());
            return Collections.emptyList();
        }

        // We'll accumulate success, other, and everything
        List<AbstractValidationItem> allFhirItems = new ArrayList<>();
        List<AbstractValidationItem> successAggregator = new ArrayList<>();
        List<AbstractValidationItem> otherAggregator = new ArrayList<>();

        // 3) Validate each FHIR file and write individual reports
        File successFolder = new File(fhirFolder, "success");
        File otherFolder = new File(fhirFolder, "other");
        successFolder.mkdirs();
        otherFolder.mkdirs();

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

            // Write individual file reports
            String baseName = f.getName();
            int idx = baseName.lastIndexOf('.');
            if (idx > 0)
            {
                baseName = baseName.substring(0, idx);
            }
            String parentFolder = f.getParentFile().getName();

            if (!successItems.isEmpty())
            {
                ValidationOutput so = new ValidationOutput(successItems);
                File outFile = new File(successFolder,
                        "fhir_issues_" + parentFolder + "_" + baseName + ".json");
                so.writeResultsAsJson(outFile);
            }

            if (!otherItems.isEmpty())
            {
                ValidationOutput oo = new ValidationOutput(otherItems);
                File outFile = new File(otherFolder,
                        "fhir_issues_" + parentFolder + "_" + baseName + ".json");
                oo.writeResultsAsJson(outFile);
            }
        }

        // 4) Write unified sub-reports using common method
        writeSubReports("fhir", successAggregator, otherAggregator, fhirFolder);

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
     * Unified file finder using NIO PathMatcher for flexible file filtering.
     * Uses try-with-resources for automatic stream management.
     *
     * @param root the root directory to search
     * @param glob the glob pattern
     * @return list of matching files
     */
    private List<File> findFiles(Path root, String glob)
    {
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }

        PathMatcher matcher = root.getFileSystem().getPathMatcher(glob);
        try (var stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(matcher::matches)
                .map(Path::toFile)
                .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error walking directory " + root + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * <p>
     * Recursively finds all FHIR files under the given {@code rootPath} using the centralized
     * file type detection from {@link FhirFileUtils}.
     * </p>
     *
     * @param rootPath  The root path to traverse
     * @return a list of {@link File} objects that are valid FHIR files (XML or JSON)
     */
    private List<File> findFhirFilesRecursively(Path rootPath)
    {
        return findFiles(rootPath, "glob:**/*.{xml,json}").stream()
            .filter(f -> FhirFileUtils.isFhirFile(f.toPath()))
            .collect(Collectors.toList());
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
        String cleanExt = extension.startsWith(".") ? extension.substring(1) : extension;
        return findFiles(rootPath, "glob:**/*." + cleanExt);
    }

    /**
     * Common reporter to write validation items split by success/other with aggregated reports.
     * Creates the necessary directory structure and writes individual and aggregated JSON files.
     *
     * @param prefix the file prefix (e.g., "bpmn", "fhir", "plugin")
     * @param success list of successful validation items
     * @param other list of non-successful validation items
     * @param targetDir the target directory for reports
     */
    private void writeSubReports(String prefix,
                                List<AbstractValidationItem> success,
                                List<AbstractValidationItem> other,
                                File targetDir)
    {
        File successDir = new File(targetDir, "success");
        File otherDir = new File(targetDir, "other");
        successDir.mkdirs();
        otherDir.mkdirs();

        // Write success reports
        if (!success.isEmpty()) {
            new ValidationOutput(success).writeResultsAsJson(
                new File(successDir, prefix + "_success_aggregated.json"));
        }

        // Write other reports
        if (!other.isEmpty()) {
            new ValidationOutput(other).writeResultsAsJson(
                new File(otherDir, prefix + "_other_aggregated.json"));
        }

        // Write combined aggregated report
        List<AbstractValidationItem> all = new ArrayList<>(success.size() + other.size());
        all.addAll(success);
        all.addAll(other);
        if (!all.isEmpty()) {
            new ValidationOutput(all).writeResultsAsJson(
                new File(targetDir, prefix + "_issues_aggregated.json"));
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

    /**
     * Runs ServiceLoader validation using {@link ApiRegistrationValidationSupport} and prints results to console.
     *
     * <p>This method is called twice: once before and once after the Maven build to give feedback
     * about whether the {@code ProcessPluginDefinition} registration is detectable in both source and compiled outputs.</p>
     *
     * @param label label to prefix output (e.g., "Pre-build check", "Post-build check")
     * @param root  project root directory to validate
     * @throws IOException if directory traversal fails
     */
    public void runServiceLoaderCheck(String label, Path root) throws IOException
    {
        List<AbstractValidationItem> raw = new ArrayList<>();
        ApiRegistrationValidationSupport.validate(root, raw);

        if (!raw.isEmpty())
        {
            System.out.println("\n[" + label + "] ServiceLoader registration results:");
            for (AbstractValidationItem it : raw)
            {
                System.out.println(" - " + it.getSeverity() + ": ServiceLoader registration check found an item; verify META-INF/services for dev.dsf.bpe.v2/v1.ProcessPluginDefinition.");
            }
        }
    }

    /**
     * Collects plugin validation items (ServiceLoader registration checks) for the given project root.
     *
     * @param root the project root
     * @return a list of plugin validation items as {@link PluginValidationItem}
     * @throws IOException if traversal fails
     */
    public List<AbstractValidationItem> collectPluginItems(Path root) throws IOException
    {
        List<AbstractValidationItem> raw = new ArrayList<>();
        ApiRegistrationValidationSupport.validate(root, raw);

        // Convert raw items to proper PluginValidationItem types
        List<AbstractValidationItem> pluginItems = new ArrayList<>();

        // Use only the directory name for the "file" property
        String projectName = root.toFile().getName();

        if (raw.isEmpty()) {
            // No registration found - create missing service item
            pluginItems.add(new MissingServiceLoaderRegistrationValidationItem(
                    new File(projectName),
                    "META-INF/services",
                    "No ProcessPluginDefinition service registration found"
            ));
        } else {
            // Convert existing items to plugin validation items
            for (AbstractValidationItem item : raw) {
                if (item.getSeverity() == ValidationSeverity.SUCCESS) {
                    pluginItems.add(new PluginValidationItemSuccess(
                            new File(projectName),
                            "META-INF/services",
                            "ProcessPluginDefinition service registration found"
                    ));
                } else {
                    pluginItems.add(new MissingServiceLoaderRegistrationValidationItem(
                            new File(projectName),
                            "META-INF/services",
                            "ProcessPluginDefinition service registration issue: " + item.getSeverity()
                    ));
                }
            }
        }

        return pluginItems;
    }

    /**
     * Writes plugin validation items under:
     * <pre>
     * report/pluginReports/
     *   ├── plugin_issues_aggregated.json
     *   ├── success/
     *   │     ├── plugin_success_aggregated.json
     *   │     └── plugin_issue_success_XXX.json
     *   └── other/
     *         ├── plugin_other_aggregated.json
     *         └── plugin_issue_other_XXX.json
     * </pre>
     *
     * @param items      plugin items to write
     * @param pluginRoot the plugin report root directory (e.g., {@code new File("report", "pluginReports")})
     * @throws IOException if writing fails
     */
    public void writePluginReports(List<AbstractValidationItem> items, File pluginRoot) throws IOException
    {
        if (!pluginRoot.isDirectory() && !pluginRoot.mkdirs())
            throw new IOException("Could not create directory: " + pluginRoot.getAbsolutePath());

        // Split by severity
        List<AbstractValidationItem> success = new ArrayList<>();
        List<AbstractValidationItem> other   = new ArrayList<>();
        for (AbstractValidationItem it : items)
        {
            if (it instanceof PluginValidationItemSuccess)
                success.add(it);
            else
                other.add(it);
        }

        // Root-level aggregated
        new ValidationOutput(items)
                .writeResultsAsJson(new File(pluginRoot, "plugin_issues_aggregated.json"));

        // Success folder (individual + aggregated)
        File successDir = new File(pluginRoot, "success");
        if (!successDir.isDirectory() && !successDir.mkdirs())
            throw new IOException("Could not create directory: " + successDir.getAbsolutePath());
        writePerItem(success, successDir, "plugin_issue_success_%03d.json");
        new ValidationOutput(success)
                .writeResultsAsJson(new File(successDir, "plugin_success_aggregated.json"));

        // Other folder (individual + aggregated)
        File otherDir = new File(pluginRoot, "other");
        if (!otherDir.isDirectory() && !otherDir.mkdirs())
            throw new IOException("Could not create directory: " + otherDir.getAbsolutePath());
        writePerItem(other, otherDir, "plugin_issue_other_%03d.json");
        new ValidationOutput(other)
                .writeResultsAsJson(new File(otherDir, "plugin_other_aggregated.json"));
    }

    /**
     * Writes one JSON file per item into the given folder using the given filename pattern.
     *
     * @param items    items to serialize
     * @param dir      target folder
     * @param pattern  file name pattern (e.g., {@code "plugin_issue_success_%03d.json"})
     * @throws IOException if writing fails
     */
    private void writePerItem(List<AbstractValidationItem> items, File dir, String pattern) throws IOException
    {
        for (int i = 0; i < items.size(); i++)
        {
            AbstractValidationItem it = items.get(i);
            String fileName = String.format(pattern, i + 1);
            new ValidationOutput(new ArrayList<>(List.of(it)))
                    .writeResultsAsJson(new File(dir, fileName));
        }
    }
}
