package dev.dsf.utils.validator;

import dev.dsf.utils.validator.build.MavenBuilder;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.plugin.PluginDefinitionDiscovery;
import dev.dsf.utils.validator.plugin.PluginDefinitionDiscovery.PluginAdapter;
import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.fhir.FhirResourceValidator;
import dev.dsf.utils.validator.util.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <h1>DSF Validator Implementation</h1>
 *
 * <p>
 * This class is the primary implementation of the {@link DsfValidator} interface, orchestrating a
 * robust, definition-driven validation process for DSF-compliant projects. Unlike a simple
 * file-scanner, this implementation uses the project's {@code ProcessPluginDefinition} as the
 * single source of truth, ensuring that only relevant and referenced resources are validated.
 * </p>
 *
 * <h2>Core Validation Workflow</h2>
 * <p>
 * The validation process follows a strict, sequential workflow designed to fail fast on critical errors:
 * </p>
 * <ol>
 * <li><b>Maven Build:</b> The project is first built using Maven to ensure all classes are compiled
 * and dependencies are available for class-loading.</li>
 * <li><b>Plugin Discovery:</b> It discovers the unique {@code ProcessPluginDefinition} implementation
 * within the project. The process aborts if zero or multiple definitions are found.</li>
 * <li><b>Referenced Resource Validation:</b> It validates <em>only</em> the BPMN and FHIR files
 * explicitly listed in the discovered plugin definition. The process aborts on the first
 * file that fails to parse, indicating a syntax error.</li>
 * <li><b>Leftover File Detection:</b> The validator scans the project's resource directories
 * (e.g., {@code src/main/resources/bpe}) and compares the found files against the list from the
 * plugin definition. Any file that exists on disk but is not referenced is reported as a "leftover" warning.</li>
 * <li><b>ServiceLoader Registration Check:</b> It verifies that the {@code ProcessPluginDefinition}
 * is correctly registered in {@code META-INF/services} for Java's ServiceLoader mechanism.</li>
 * <li><b>Structured Report Generation:</b> All findings (successes, warnings, errors) are aggregated and
 * written to a detailed, structured report in the {@code /report} directory.</li>
 * </ol>
 *
 * <h2>Report Output Structure</h2>
 * <p>
 * The validator generates a comprehensive set of JSON reports under the {@code report/} folder:
 * </p>
 * <pre>
 * report/
 * ├── bpmnReports/
 * │   ├── success/
 * │   ├── other/
 * │   └── bpmn_issues_aggregated.json
 * ├── fhirReports/
 * │   ├── success/
 * │   ├── other/
 * │   └── fhir_issues_aggregated.json
 * ├── pluginReports/
 * │   ├── success/
 * │   ├── other/
 * │   └── plugin_issues_aggregated.json
 * └── aggregated.json  (A combined report of all findings)
 * </pre>
 *
 * <h2>Error Handling</h2>
 * <p>
 * The process is designed to be strict and will terminate immediately under the following conditions:
 * <ul>
 * <li>A fatal Maven build failure occurs.</li>
 * <li>A unique {@code ProcessPluginDefinition} cannot be discovered.</li>
 * <li>A referenced BPMN or FHIR resource file cannot be parsed due to syntax errors.</li>
 * </ul>
 * </p>
 *
 * @see DsfValidator
 * @see PluginDefinitionDiscovery
 * @see ValidationOutput
 * @author Gemini
 */
public class DsfValidatorImpl implements DsfValidator
{
    private final FhirResourceValidator fhirResourceValidator;
    private final Logger logger;
    /**
     * Constructs a default {@code DsfValidatorImpl} that includes a
     * {@link FhirResourceValidator} for validating FHIR resources.
     */
    public DsfValidatorImpl(Logger logger)
    {
        this.logger = logger;
        this.fhirResourceValidator = new FhirResourceValidator();
    }

    /**
     * Orchestrates the entire validation workflow for a given project directory.
     * This method executes the core logic as described in the class documentation.
     *
     * @param path The path to the root of the project directory to be validated.
     * @return A {@link ValidationOutput} object containing the complete, aggregated results of the run.
     * @throws ResourceValidationException if a referenced resource file contains a syntax error.
     * @throws IllegalStateException if a unique {@code ProcessPluginDefinition} cannot be discovered.
     * @throws IOException if a file system error occurs during report writing or file discovery.
     * @throws InterruptedException if the Maven build process is interrupted.
     * @throws MissingServiceRegistrationException if the ServiceLoader check fails.
     */
    @Override
    public ValidationOutput validate(Path path) throws ResourceValidationException, IllegalStateException, IOException, InterruptedException, MissingServiceRegistrationException {
        if (!Files.isDirectory(path))
        {
            logger.error("ERROR: This validation workflow only supports project directories.");
            return ValidationOutput.empty();
        }

        File projectDir = path.toFile();

        // Step 2.1: Build the project with Maven
        MavenBuilder builder = new MavenBuilder();
        String mavenExecutable = MavenUtil.locateMavenExecutable();
        if (mavenExecutable == null)
        {
            throw new IllegalStateException("Maven executable not found in PATH.");
        }
        if (!builder.buildProject(projectDir, mavenExecutable,
                "-B", "-DskipTests", "-Dformatter.skip=true", "-Dexec.skip=true", "clean", "package", "dependency:copy-dependencies"))
        {
            throw new RuntimeException("Maven 'package' phase failed.");
        }

        // Step 2.2: Find plugin definition (throws IllegalStateException on error)
        PluginAdapter pluginAdapter = PluginDefinitionDiscovery.discoverSingle(projectDir);
        logger.info("Discovered ProcessPluginDefinition: " + pluginAdapter.sourceClass().getName());

        // Detect API version
        ApiVersionDetector detector = new ApiVersionDetector();
        detector.detect(path).ifPresent(v -> ApiVersionHolder.setVersion(v.version()));

        // add DSF‑CodeSystem‑Cache
        FhirAuthorizationCache.seedFromProjectFolder(projectDir);

        // Step 2.3: Collect referenced files from the plugin definition
        logger.info("Gathering referenced resources from " + pluginAdapter.sourceClass().getSimpleName() + "...");
        Set<String> referencedBpmnPaths = new HashSet<>(pluginAdapter.getProcessModels());
        Set<String> referencedFhirPaths = pluginAdapter.getFhirResourcesByProcessId().values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        // Define the correct base path for resources, which is typically src/main/resources
        File resourcesDir = new File(projectDir, "src/main/resources");

        // Resolve file paths against the resources directory
        List<File> bpmnFilesToValidate = referencedBpmnPaths.stream()
                .map(p -> new File(resourcesDir, p)).distinct().collect(Collectors.toList());

        List<File> fhirFilesToValidate = referencedFhirPaths.stream()
                .map(p -> new File(resourcesDir, p)).distinct().collect(Collectors.toList());


        // Prepare report directory
        File reportRoot = new File(System.getProperty("dsf.report.dir", "report"));
        ReportCleaner.prepareCleanReportDirectory(reportRoot);

        // Step 2.4: Validate ONLY the referenced files
        List<AbstractValidationItem> bpmnItems = validateAllBpmnFilesSplitNewStructure(bpmnFilesToValidate, reportRoot, projectDir);
        List<AbstractValidationItem> fhirItems = validateAllFhirResourcesSplitNewStructure(fhirFilesToValidate, reportRoot, projectDir);

        // Step 2.5: Find and report "leftovers" (unreferenced files)
        List<AbstractValidationItem> leftoverItems = findAndReportLeftovers(projectDir, resourcesDir, referencedBpmnPaths, referencedFhirPaths);

        // Collect other plugin items (e.g., ServiceLoader checks)
        List<AbstractValidationItem> serviceLoaderItems = collectPluginItems(path);

        List<AbstractValidationItem> allPluginItems = new ArrayList<>();
        allPluginItems.addAll(leftoverItems);
        allPluginItems.addAll(serviceLoaderItems);

        // Step 2.6: Write all reports
        writePluginReports(allPluginItems, new File(reportRoot, "pluginReports"));

        List<AbstractValidationItem> combined = new ArrayList<>();
        combined.addAll(bpmnItems);
        combined.addAll(fhirItems);
        combined.addAll(allPluginItems);
        ValidationOutput finalOutput = new ValidationOutput(combined);
        finalOutput.writeResultsAsJson(new File(reportRoot, "aggregated.json"));

        System.out.printf("%nValidation finished – %d issue(s) found (%d BPMN/FHIR + %d plugin).%n",
                combined.size(), bpmnItems.size() + fhirItems.size(), allPluginItems.size());
        logger.info("Reports written to: " + reportRoot.getAbsolutePath());

        printDetectedApiVersion();

        return finalOutput;
    }



    // BPMN (Split with sub-aggregators)

    /**
     * Validates a list of BPMN files and generates structured reports.
     *
     * <p>This method validates each BPMN file in the provided list, categorizes results into
     * 'success' and 'other' (errors/warnings), and writes individual and aggregated JSON reports
     * to the specified directory structure.</p>
     *
     * <p>For each file that cannot be found on disk, an error validation item is created.
     * Files that exist are validated using {@link BPMNValidator}, and parsing failures result
     * in a {@link ResourceValidationException} that aborts the entire validation process.</p>
     *
     * <p>Report structure generated:</p>
     * <pre>
     * reportRoot/bpmnReports/
     * ├── success/
     * │   ├── bpmn_issues_{parentFolder}_{processId}.json (per file)
     * │   └── bpmn_success_aggregated.json
     * ├── other/
     * │   ├── bpmn_issues_{parentFolder}_{processId}.json (per file)
     * │   └── bpmn_other_aggregated.json
     * └── bpmn_issues_aggregated.json (combined)
     * </pre>
     *
     * @param bpmnFiles  The list of BPMN files to validate
     * @param reportRoot The root directory for report output (e.g., "report/")
     * @param projectDir The root directory of the project, used as the base for relative paths.
     * @return A list of all validation items generated during BPMN validation
     * @throws ResourceValidationException if any BPMN file contains syntax errors that prevent parsing
     */
    public List<AbstractValidationItem> validateAllBpmnFilesSplitNewStructure(List<File> bpmnFiles, File reportRoot, File projectDir) throws ResourceValidationException
    {
        // 1) Setup subfolders for BPMN reports
        File bpmnFolder = new File(reportRoot, "bpmnReports");
        bpmnFolder.mkdirs();
        File successFolder = new File(bpmnFolder, "success");
        File otherFolder = new File(bpmnFolder, "other");
        successFolder.mkdirs();
        otherFolder.mkdirs();

        if (bpmnFiles.isEmpty())
        {
            logger.info("No referenced .bpmn files to validate.");
            return Collections.emptyList();
        }

        // Initialize aggregators for all results
        List<AbstractValidationItem> allBpmnItems = new ArrayList<>();
        List<AbstractValidationItem> successAggregator = new ArrayList<>();
        List<AbstractValidationItem> otherAggregator = new ArrayList<>();

        BPMNValidator validator = new BPMNValidator();

        // 3) Validate each BPMN file from the provided list
        for (File bpmnFile : bpmnFiles)
        {
            if (!bpmnFile.exists()) {
                logger.warn("Referenced BPMN file not found on disk: " + bpmnFile.getAbsolutePath());

                // FIX: Use projectDir as the base for the relative path
                BpmnFileReferencedButNotFoundValidationItem notFoundItem = new BpmnFileReferencedButNotFoundValidationItem(
                        ValidationSeverity.ERROR, bpmnFile, "Referenced BPMN file not found at " + getRelativePath(bpmnFile, projectDir));

                allBpmnItems.add(notFoundItem);
                otherAggregator.add(notFoundItem); // This ensures it gets into bpmn_other_aggregated.json
                continue;
            }

            logger.info("Validating BPMN file: " + bpmnFile.getName());
            ValidationOutput out;
            List<AbstractValidationItem> itemsForThisFile;
            // This call will throw ResourceValidationException on parsing failure
            out = validator.validateBpmnFile(bpmnFile.toPath());
            itemsForThisFile = new ArrayList<>(out.validationItems());

            // Add a success item indicating the file was found and is readable
            String processId = out.getProcessId();
            if (processId.isBlank()) {
                processId = "[unknown_process]";
            }

            itemsForThisFile.add(new BpmnElementValidationItemSuccess(processId, bpmnFile, processId, "Referenced BPMN file found and is readable."));

            out.printResults();

            allBpmnItems.addAll(itemsForThisFile);

            List<AbstractValidationItem> successItems = itemsForThisFile.stream()
                    .filter(this::isSuccessItem)
                    .collect(Collectors.toList());
            List<AbstractValidationItem> otherItems = itemsForThisFile.stream()
                    .filter(x -> !isSuccessItem(x))
                    .collect(Collectors.toList());

            successAggregator.addAll(successItems);
            otherAggregator.addAll(otherItems);

            // Write individual file reports based on the results
            String pid = out.getProcessId();
            if (pid.isBlank())
            {
                pid = bpmnFile.getName().replace(".bpmn", "");
            }
            String parentFolderName = bpmnFile.getParentFile().getName();

            if (!successItems.isEmpty())
            {
                new ValidationOutput(successItems).writeResultsAsJson(new File(successFolder,
                        "bpmn_issues_" + parentFolderName + "_" + pid + ".json"));
            }

            if (!otherItems.isEmpty())
            {
                new ValidationOutput(otherItems).writeResultsAsJson(new File(otherFolder,
                        "bpmn_issues_" + parentFolderName + "_" + pid + ".json"));
            }
        }

        // 4) Write unified sub-reports for all BPMN files
        writeSubReports("bpmn", successAggregator, otherAggregator, bpmnFolder);

        return allBpmnItems;
    }


    /**
     * Validates a list of FHIR resource files and generates structured reports.
     *
     * <p>This method validates each FHIR file in the provided list using {@link FhirResourceValidator},
     * categorizes results into 'success' and 'other' (errors/warnings), and writes individual and
     * aggregated JSON reports to the specified directory structure.</p>
     *
     * <p>For each file that cannot be found on disk, an error validation item is created.
     * Files that exist are validated, and parsing failures result in a {@link ResourceValidationException}
     * that aborts the entire validation process.</p>
     *
     * <p>Report structure generated:</p>
     * <pre>
     * reportRoot/fhirReports/
     * ├── success/
     * │   ├── fhir_issues_{parentFolder}_{baseName}.json (per file)
     * │   └── fhir_success_aggregated.json
     * ├── other/
     * │   ├── fhir_issues_{parentFolder}_{baseName}.json (per file)
     * │   └── fhir_other_aggregated.json
     * └── fhir_issues_aggregated.json (combined)
     * </pre>
     *
     * @param fhirFiles  The list of FHIR resource files to validate
     * @param reportRoot The root directory for report output (e.g., "report/")
     * @param projectDir The root directory of the project, used as the base for relative paths.
     * @return A list of all validation items generated during FHIR validation
     * @throws ResourceValidationException if any FHIR file contains syntax errors that prevent parsing
     */
    public List<AbstractValidationItem> validateAllFhirResourcesSplitNewStructure(List<File> fhirFiles, File reportRoot, File projectDir) throws ResourceValidationException
    {
        // 1) Setup subfolders for FHIR reports
        File fhirFolder = new File(reportRoot, "fhirReports");
        fhirFolder.mkdirs();
        File successFolder = new File(fhirFolder, "success");
        File otherFolder = new File(fhirFolder, "other");
        successFolder.mkdirs();
        otherFolder.mkdirs();

        if (fhirFiles.isEmpty())
        {
            logger.info("No referenced FHIR files to validate.");
            return Collections.emptyList();
        }

        // Initialize aggregators for all results
        List<AbstractValidationItem> allFhirItems = new ArrayList<>();
        List<AbstractValidationItem> successAggregator = new ArrayList<>();
        List<AbstractValidationItem> otherAggregator = new ArrayList<>();

        // 3) Validate each FHIR file from the provided list
        for (File fhirFile : fhirFiles)
        {
            if (!fhirFile.exists()) {
                logger.warn(" Referenced FHIR file not found on disk: " + fhirFile.getAbsolutePath());

                // FIX: Use projectDir as the base for the relative path
                FhirFileReferencedButNotFoundValidationItem notFoundItem = new FhirFileReferencedButNotFoundValidationItem(
                        "Referenced FHIR file not found at " + getRelativePath(fhirFile, projectDir), ValidationSeverity.ERROR, fhirFile);

                allFhirItems.add(notFoundItem);
                otherAggregator.add(notFoundItem); // This ensures it gets into fhir_other_aggregated.json
                continue;
            }

            logger.info("Validating FHIR file: " + fhirFile.getName());
            ValidationOutput output;
            List<AbstractValidationItem> itemsForThisFile;
            // This call will throw ResourceValidationException on parsing failure
            output = fhirResourceValidator.validateSingleFile(fhirFile.toPath());
            itemsForThisFile = new ArrayList<>(output.validationItems());

            // Add a success item indicating the file was found and is readable
            String fhirReference = itemsForThisFile.stream()
                    .filter(item -> item instanceof FhirElementValidationItem)
                    .map(item -> ((FhirElementValidationItem) item).getFhirReference())
                    .filter(ref -> ref != null && !ref.isBlank())
                    .findFirst()
                    .orElse("unknown_reference");

            itemsForThisFile.add(new FhirElementValidationItemSuccess(fhirFile, fhirReference, "Referenced FHIR file found and is readable."));

            output.printResults();

            allFhirItems.addAll(itemsForThisFile);

            List<AbstractValidationItem> successItems = itemsForThisFile.stream()
                    .filter(this::isSuccessItem)
                    .collect(Collectors.toList());
            List<AbstractValidationItem> otherItems = itemsForThisFile.stream()
                    .filter(x -> !isSuccessItem(x))
                    .collect(Collectors.toList());

            successAggregator.addAll(successItems);
            otherAggregator.addAll(otherItems);

            // Write individual file reports based on the results
            String baseName = fhirFile.getName();
            int idx = baseName.lastIndexOf('.');
            if (idx > 0)
            {
                baseName = baseName.substring(0, idx);
            }
            String parentFolderName = fhirFile.getParentFile().getName();

            if (!successItems.isEmpty())
            {
                new ValidationOutput(successItems).writeResultsAsJson(new File(successFolder,
                        "fhir_issues_" + parentFolderName + "_" + baseName + ".json"));
            }

            if (!otherItems.isEmpty())
            {
                new ValidationOutput(otherItems).writeResultsAsJson(new File(otherFolder,
                        "fhir_issues_" + parentFolderName + "_" + baseName + ".json"));
            }
        }

        // 4) Write unified sub-reports for all FHIR files
        writeSubReports("fhir", successAggregator, otherAggregator, fhirFolder);

        return allFhirItems;
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
            logger.error("Error walking directory " + root + ": " + e.getMessage());
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
     * Recursively finds all BPMN files (ending in .bpmn) under the given {@code rootPath}.
     *
     * @param rootPath The root path to traverse.
     * @return A list of {@link File} objects that are BPMN files.
     */
    private List<File> findBpmnFilesRecursively(Path rootPath)
    {
        // sole purpose is to find BPMN files.
        return findFiles(rootPath, "glob:**/*.bpmn");
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
     * Collects plugin validation items (ServiceLoader registration checks) for the given project root.
     *
     * @param root the project root
     * @return a list of plugin validation items as {@link PluginValidationItem}
     */
    public List<AbstractValidationItem> collectPluginItems(Path root) throws  MissingServiceRegistrationException {
        List<AbstractValidationItem> raw = new ArrayList<>();
        ApiRegistrationValidationSupport support = new ApiRegistrationValidationSupport();
        support.run("Plugin collection", root, raw);

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
            // Use the items directly from the support class
            pluginItems.addAll(raw);
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
     * Prints the detected DSF BPE API version to the console with red formatting.
     * This method should be called after validation is complete to display the version at the end.
     */
    public void printDetectedApiVersion() {
        ApiVersion apiVersion = ApiVersionHolder.getVersion();
        String versionStr = switch (apiVersion) {
            case V1 -> "v1";
            case V2 -> "v2";
            case UNKNOWN -> "unknown";
        };
        logger.info("\u001B[31mDetected DSF BPE API version: " + versionStr + "\u001B[0m");
    }

    /**
     * Finds all BPMN and FHIR files on disk and compares them to the sets of referenced files.
     * Generates validation items for any unreferenced files ("leftovers").
     *
     * @param projectDir          The root directory of the project.
     * @param resourcesDir        The 'src/main/resources' directory, used as the base for relative paths.
     * @param referencedBpmnPaths A set of relative paths to BPMN files from the plugin definition.
     * @param referencedFhirPaths A set of relative paths to FHIR files from the plugin definition.
     * @return A list of validation items for leftover checks.
     */
    private List<AbstractValidationItem> findAndReportLeftovers(File projectDir, File resourcesDir, Set<String> referencedBpmnPaths, Set<String> referencedFhirPaths)
    {
        // Find all actual files on disk
        File bpeRoot = new File(resourcesDir, "bpe");
        List<File> actualBpmnFiles = (bpeRoot.exists()) ? findBpmnFilesRecursively(bpeRoot.toPath()) : Collections.emptyList();

        File fhirRoot = new File(resourcesDir, "fhir");
        List<File> actualFhirFiles = (fhirRoot.exists()) ? findFhirFilesRecursively(fhirRoot.toPath()) : Collections.emptyList();

        // Convert the paths of actual files to be relative to the resources directory
        Set<String> actualBpmnPaths = actualBpmnFiles.stream()
                .map(f -> getRelativePath(f, resourcesDir)) // Use resourcesDir as the base
                .collect(Collectors.toSet());
        Set<String> actualFhirPaths = actualFhirFiles.stream()
                .map(f -> getRelativePath(f, resourcesDir)) // Use resourcesDir as the base
                .collect(Collectors.toSet());

        // Find the difference: files on disk that are not in the definition
        actualBpmnPaths.removeAll(referencedBpmnPaths);
        actualFhirPaths.removeAll(referencedFhirPaths);

        List<AbstractValidationItem> items = new ArrayList<>();
        if (actualBpmnPaths.isEmpty() && actualFhirPaths.isEmpty())
        {
            // No leftovers found -> success item
            items.add(new PluginValidationItemSuccess(projectDir, projectDir.getName(),
                    "All BPMN and FHIR resources found in the project are correctly referenced in ProcessPluginDefinition."));
        }
        else
        {
            // Report all leftovers as warnings
            actualBpmnPaths.forEach(path -> {
                items.add(new ProcessPluginRessourceNotLoadedValidationItem(new File(resourcesDir, path), path, null));
            });
            actualFhirPaths.forEach(path -> {
                items.add(new ProcessPluginRessourceNotLoadedValidationItem(new File(resourcesDir, path), path, null));
            });
        }

        return items;
    }

    /**
     * Wandelt einen absoluten Dateipfad in einen relativen Pfad um (mit '/' als Trennzeichen).
     */
    private String getRelativePath(File file, File baseDir)
    {
        String relative = baseDir.toPath().relativize(file.toPath()).toString();
        return relative.replace(File.separator, "/");
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
