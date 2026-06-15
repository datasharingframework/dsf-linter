package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.BpmnElementLintItem;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Specialized linter class for validating BPMN process-level attributes and DSF-specific requirements.
 *
 * <p>
 * The {@code BpmnProcessLinter} provides comprehensive validation for BPMN 2.0 process definitions
 * used in DSF (Data Sharing Framework) workflows. It validates process-level attributes including
 * process ID patterns, process count per file, history time to live settings, and executable configuration.
 * </p>
 *
 * <h2>Architecture</h2>
 * <p>
 * This class is a specialized sub-linter that is invoked by the {@link BpmnModelLinter} during the validation
 * of a complete BPMN model. It focuses exclusively on process-level validations as required by the DSF Framework.
 * </p>
 *
 * <h2>Validation Categories</h2>
 * <p>
 * The linter performs the following categories of checks:
 * </p>
 *
 * <h3>Process ID Validation</h3>
 * <ul>
 *   <li><strong>Pattern Matching</strong>: Validates that the process ID matches the DSF pattern
 *       {@code domain_processname} (e.g., {@code testorg_myprocess}). Domain and process name must
 *       consist only of alphanumeric characters and hyphens.</li>
 *   <li><strong>Empty ID Check</strong>: Reports an error if the process ID is empty or not defined.</li>
 * </ul>
 *
 * <h3>Process Count Validation</h3>
 * <ul>
 *   <li><strong>Single Process Requirement</strong>: Validates that each BPMN file contains exactly one
 *       process definition, as required by the DSF Framework.</li>
 * </ul>
 *
 * <h3>History Time To Live Validation</h3>
 * <ul>
 *   <li><strong>Attribute Check</strong>: Warns if {@code camunda:historyTimeToLive} is not set.
 *       DSF uses default value {@code P30D} (30 days) at runtime. Best practice is to set explicitly.</li>
 * </ul>
 *
 * <h3>Process Executable Validation</h3>
 * <ul>
 *   <li><strong>Executable Flag</strong>: Validates that the process has {@code isExecutable="true"},
 *       which is required for the process to be deployable and executable by the process engine.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * File projectRoot = new File("/path/to/project");
 * BpmnProcessLinter linter = new BpmnProcessLinter(projectRoot);
 *
 * BpmnModelInstance model = Bpmn.readModelFromFile(new File("process.bpmn"));
 * File bpmnFile = new File("process.bpmn");
 * List<BpmnElementLintItem> issues = new ArrayList<>();
 *
 * linter.lintProcesses(model, bpmnFile, issues);
 *
 * for (BpmnElementLintItem issue : issues) {
 *     System.out.println(issue.getSeverity() + ": " + issue.getMessage());
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe as it is implemented as an immutable record with no mutable state.
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://github.com/datasharingframework/dsf">DSF Framework</a> - Process validation requirements</li>
 *   <li><a href="https://docs.camunda.org/manual/latest/reference/bpmn20/custom-extensions/extension-attributes/#historyTimeToLive">
 *       Camunda historyTimeToLive</a></li>
 * </ul>
 *
 * @see BpmnModelLinter
 * @see BpmnElementLintItem
 * @since 1.0
 */
public record BpmnProcessLinter(File projectRoot) {

    /**
     * Pattern for validating BPMN Process IDs.
     * <p>
     * The Process ID must follow the format: {@code domain_processname}
     * where both domain and processname consist only of alphanumeric characters and hyphens.
     * </p>
     * <p>
     * Examples of valid IDs:
     * <ul>
     *   <li>{@code testorg_myprocess}</li>
     *   <li>{@code dsf-dev_download-allowlist}</li>
     * </ul>
     * </p>
     *
     * @see <a href="https://github.com/datasharingframework/dsf">DSF Framework</a>
     */
    private static final String PROCESS_ID_PATTERN_STRING = "^(?<domainNoDots>[a-zA-Z0-9-]+)_(?<processName>[a-zA-Z0-9-]+)$";
    private static final Pattern PROCESS_ID_PATTERN = Pattern.compile(PROCESS_ID_PATTERN_STRING);

    /**
     * Constructs a new {@code BpmnProcessLinter} instance with the specified project root directory.
     *
     * @param projectRoot the root directory of the project; must not be {@code null}
     * @throws IllegalArgumentException if {@code projectRoot} is {@code null}
     */
    public BpmnProcessLinter {
        if (projectRoot == null) {
            throw new IllegalArgumentException("Project root must not be null");
        }
    }

    /**
     * Performs comprehensive validation of all process definitions in a BPMN model.
     *
     * <p>
     * This method validates:
     * <ul>
     *   <li>Process count (must be exactly one)</li>
     *   <li>Process ID pattern</li>
     *   <li>History time to live attribute</li>
     *   <li>Executable flag</li>
     * </ul>
     * </p>
     *
     * @param model    the BPMN model instance to validate; must not be {@code null}
     * @param bpmnFile the source BPMN file being validated; used for issue location reporting
     * @param issues   the list to add validation issues to
     * @return the extracted process ID, or an empty string if no process is found
     */
    public String lintProcesses(BpmnModelInstance model, File bpmnFile, List<BpmnElementLintItem> issues) {
        Collection<Process> processes = model.getModelElementsByType(Process.class);

        // Validate process count - must be exactly one process per BPMN file
        validateProcessCount(processes, bpmnFile, issues);

        // Validate process-level attributes for each process
        for (Process process : processes) {
            validateHistoryTimeToLive(process, bpmnFile, issues);
            validateProcessExecutable(process, bpmnFile, issues);
        }

        // Extract and validate process ID
        String processId = extractProcessId(model);
        validateProcessIdPattern(processId, bpmnFile, issues);

        return processId;
    }

    /**
     * Extracts the process ID from the first process definition found in the BPMN model.
     *
     * <p>
     * This method searches the BPMN model for {@link Process} elements and returns the ID of the first
     * process found. If the model contains multiple process definitions, only the first one is considered.
     * </p>
     *
     * @param model the BPMN model instance to extract the process ID from; must not be {@code null}
     * @return the process ID as a {@link String}, or an empty string if no process is found or if
     * the process ID is {@code null}. Never returns {@code null}
     */
    String extractProcessId(BpmnModelInstance model) {
        return model.getModelElementsByType(Process.class)
                .stream()
                .map(Process::getId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }

    /**
     * Validates that the BPMN Process ID matches the required DSF pattern.
     *
     * <p>
     * The Process ID must follow the pattern: {@code domain_processname}
     * where both domain and processname consist only of alphanumeric characters and hyphens.
     * </p>
     *
     * @param processId the process ID to validate
     * @param bpmnFile  the BPMN file for error reporting
     * @param issues    the list to add validation issues to
     */
    void validateProcessIdPattern(String processId, File bpmnFile, List<BpmnElementLintItem> issues) {
        if (processId == null || processId.isEmpty()) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR,
                    LintingType.BPMN_PROCESS_ID_EMPTY,
                    "Process",
                    bpmnFile,
                    "",
                    "BPMN Process ID is empty or not defined."
            ));
            return;
        }

        if (!PROCESS_ID_PATTERN.matcher(processId).matches()) {
            String description = String.format(
                    "Process ID '%s' does not match the required pattern '%s'. " +
                    "Expected format: domain_processname (e.g., testorg_myprocess, dsf-dev_download-allowlist).",
                    processId,
                    PROCESS_ID_PATTERN_STRING
            );
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR,
                    LintingType.BPMN_PROCESS_ID_PATTERN_MISMATCH,
                    processId,
                    bpmnFile,
                    processId,
                    description
            ));
        } else {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.SUCCESS,
                    LintingType.SUCCESS,
                    processId,
                    bpmnFile,
                    processId,
                    String.format("Process ID '%s' matches the required pattern.", processId)
            ));
        }
    }

    /**
     * Validates that the BPMN file contains exactly one process definition.
     *
     * <p>
     * According to DSF Framework requirements, each BPMN file must contain exactly one process.
     * </p>
     *
     * @param processes the collection of processes found in the BPMN model
     * @param bpmnFile  the BPMN file for error reporting
     * @param issues    the list to add validation issues to
     */
    void validateProcessCount(Collection<Process> processes, File bpmnFile, List<BpmnElementLintItem> issues) {
        int processCount = processes.size();

        if (processCount == 0) {

            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR,
                    LintingType.BPMN_FILE_NO_PROCESS,
                    "Process",
                    bpmnFile,
                    "",
                    String.format("BPMN file '%s' contains no process definition. Expected exactly 1 process.",
                            bpmnFile.getName())
            ));
        } else if (processCount > 1) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR,
                    LintingType.BPMN_FILE_MULTIPLE_PROCESSES,
                    "Process",
                    bpmnFile,
                    "",
                    String.format("BPMN file '%s' contains %d processes. Expected exactly 1 process.",
                            bpmnFile.getName(), processCount)
            ));
        } else {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.SUCCESS,
                    LintingType.SUCCESS,
                    processes.iterator().next().getId(),
                    bpmnFile,
                    processes.iterator().next().getId(),
                    String.format("BPMN file '%s' contains exactly 1 process.", bpmnFile.getName())
            ));
        }
    }

    /**
     * Validates that the process has a historyTimeToLive attribute set.
     *
     * <p>
     * If {@code camunda:historyTimeToLive} is not set (null or empty), a WARNING is issued.
     * DSF automatically sets the default value 'P30D' (30 days) at runtime, but it is best
     * practice to explicitly set this attribute in the BPMN file.
     * </p>
     *
     * @param process  the BPMN process to validate
     * @param bpmnFile the BPMN file for error reporting
     * @param issues   the list to add validation issues to
     */
    void validateHistoryTimeToLive(Process process, File bpmnFile, List<BpmnElementLintItem> issues) {
        String processId = process.getId() != null ? process.getId() : "";

        String historyTimeToLive = process.getCamundaHistoryTimeToLiveString();

        if (historyTimeToLive == null || historyTimeToLive.isBlank()) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.WARN,
                    LintingType.BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING,
                    processId,
                    bpmnFile,
                    processId,
                    String.format("Process '%s': camunda:historyTimeToLive is not set. " +
                            "DSF uses default 'P30D' (30 days). Best practice: set explicitly, e.g., 'P30D'.",
                            processId)
            ));
        } else {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.SUCCESS,
                    LintingType.SUCCESS,
                    processId,
                    bpmnFile,
                    processId,
                    String.format("Process '%s': historyTimeToLive is set to '%s'.", processId, historyTimeToLive)
            ));
        }
    }

    /**
     * Validates that the process has isExecutable set to true.
     *
     * <p>
     * Processes without {@code isExecutable="true"} cannot be executed by the process engine.
     * This is a critical requirement for DSF process plugins.
     * </p>
     *
     * @param process  the BPMN process to validate
     * @param bpmnFile the BPMN file for error reporting
     * @param issues   the list to add validation issues to
     */
    void validateProcessExecutable(Process process, File bpmnFile, List<BpmnElementLintItem> issues) {
        String processId = process.getId() != null ? process.getId() : "";

        if (!process.isExecutable()) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR,
                    LintingType.BPMN_PROCESS_NOT_EXECUTABLE,
                    processId,
                    bpmnFile,
                    processId,
                    String.format("Process '%s': isExecutable is not set to 'true'. " +
                            "The process cannot be deployed and executed by the process engine.",
                            processId)
            ));
        } else {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.SUCCESS,
                    LintingType.SUCCESS,
                    processId,
                    bpmnFile,
                    processId,
                    String.format("Process '%s': isExecutable is set to 'true'.", processId)
            ));
        }
    }
}
