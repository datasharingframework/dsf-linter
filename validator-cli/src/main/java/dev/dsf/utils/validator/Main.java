package dev.dsf.utils.validator;

import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.fhir.FhirResourceValidator;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.FhirElementValidationItem;
import dev.dsf.utils.validator.repo.RepositoryManager;
import dev.dsf.utils.validator.build.MavenBuilder;
import dev.dsf.utils.validator.util.MavenUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * The {@code MainCombined} class combines local and remote validation flows into a single entry point.
 * It can:
 * <ul>
 *   <li>Use a local path to validate BPMN and FHIR files directly.</li>
 *   <li>Use a remote repository URL, clone it, build it with Maven, and then validate BPMN and FHIR files.</li>
 * </ul>
 *
 * <p><b>Main Steps:</b>
 * <ol>
 *   <li>Parse command-line arguments with picocli.</li>
 *   <li>Check if {@code --localPath} or {@code --remoteRepo} is provided.</li>
 *   <li>If remote, clone via {@link dev.dsf.utils.validator.repo.RepositoryManager}.</li>
 *   <li>Locate and run Maven using {@link dev.dsf.utils.validator.util.MavenUtil} and
 *       {@link dev.dsf.utils.validator.build.MavenBuilder}.</li>
 *   <li>Recursively validate BPMN files (under {@code src/main/resources/bpe}) with {@link BPMNValidator} and write
 *       them to an aggregated JSON.</li>
 *   <li>Validate FHIR resources by calling {@link FhirResourceValidator#validateAllFhirResources(File)} (like in the
 *       {@code Main} class), then write them to an aggregated JSON file named {@code fhir_issues_aggregated.json}.</li>
 * </ol>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 *   java -jar validator-cli-1.0-SNAPSHOT.jar --localPath /path/to/local/project
 *   java -jar validator-cli-1.0-SNAPSHOT.jar --remoteRepo https://github.com/username/some-repo.git
 * </pre>
 *
 * <p><b>References and Further Reading:</b>
 * <ul>
 *   <li><a href="https://www.eclipse.org/jgit/">JGit Documentation</a></li>
 *   <li><a href="https://picocli.info/">Picocli</a></li>
 *   <li><a href="https://maven.apache.org/">Apache Maven</a></li>
 *   <li><a href="https://hl7.org/fhir/">HL7 FHIR Specification</a></li>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 * </ul>
 */
@Command(
        name = "MainCombined",
        mixinStandardHelpOptions = true,
        description = "Validates BPMN and FHIR files either from a local path or by cloning a remote repository."
)
public class Main implements Callable<Integer>
{
    @Option(
            names = {"--localPath"},
            description = "Path to a local project directory (if you want to validate locally)."
    )
    private File localPath;

    @Option(
            names = {"--remoteRepo"},
            description = "URL of a remote Git repository (if you want to clone and validate remotely)."
    )
    private String remoteRepoUrl;

    /**
     * Main entry point for the application.
     *
     * @param args Command-line arguments
     */
    public static void main(String[] args)
    {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Called by picocli once command-line parsing is done.
     *
     * @return exit code
     * @throws Exception if any I/O or process error occurs
     */
    @Override
    public Integer call() throws Exception
    {
        // Validate arguments
        if ((localPath == null || !localPath.isDirectory()) && (remoteRepoUrl == null || remoteRepoUrl.isEmpty()))
        {
            System.err.println("ERROR: You must specify either --localPath (a valid directory) or --remoteRepo (a valid URL).");
            return 1;
        }

        // Determine the project directory (local or cloned)
        File projectDir;
        if (remoteRepoUrl != null && !remoteRepoUrl.isEmpty())
        {
            System.out.println("Cloning repository from: " + remoteRepoUrl);
            projectDir = cloneRepository(remoteRepoUrl);
            if (projectDir == null)
            {
                System.err.println("ERROR: Could not clone repository.");
                return 1;
            }
        }
        else
        {
            projectDir = localPath;
        }

        // Locate Maven
        String mavenCmd = MavenUtil.locateMavenExecutable();
        if (mavenCmd == null)
        {
            System.err.println("ERROR: Maven executable not found. Ensure Maven is installed and in your PATH.");
            return 1;
        }

        // Build the project
        MavenBuilder mavenBuilder = new MavenBuilder();
        try
        {
            if (!mavenBuilder.buildProject(projectDir, mavenCmd))
            {
                System.err.println("ERROR: Maven build failed.");
                return 1;
            }
        }
        catch (IOException | InterruptedException e)
        {
            System.err.println("ERROR: Exception during Maven build: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }

        // Validate BPMN
        validateAllBpmnFiles(projectDir);

        // Validate FHIR (like "Main" class: single aggregator from validateAllFhirResources)
        validateAllFhirResources(projectDir);

        System.out.println("\nValidation process finished!");
        return 0;
    }

    /**
     * Clones the given remote repository into a temporary directory.
     *
     * @param remoteUrl the remote repository URL
     * @return the local directory where the repository was cloned, or null if an error occurred
     */
    private File cloneRepository(String remoteUrl)
    {
        // Extract repository name from URL
        String repositoryName = remoteUrl.substring(remoteUrl.lastIndexOf('/') + 1);
        // Create a local directory in the system temp folder
        File cloneDir = new File(System.getProperty("java.io.tmpdir"), repositoryName);

        RepositoryManager repoManager = new RepositoryManager();
        try
        {
            return repoManager.getRepository(remoteUrl, cloneDir);
        }
        catch (GitAPIException e)
        {
            System.err.println("ERROR: Failed to clone repository: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Recursively finds all BPMN files under {@code src/main/resources/bpe}, validates them
     * one-by-one, writes per-file JSON using the BPMN {@code processId}, and produces an
     * aggregated JSON file at the end.
     *
     * @param projectDir the root project directory
     */
    private void validateAllBpmnFiles(File projectDir)
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

        BPMNValidator bpmnValidator = new BPMNValidator();
        List<AbstractValidationItem> allBpmnItems = new ArrayList<>();

        for (File file : bpmnFiles)
        {
            System.out.println("\nValidating BPMN file: " + file.getName());
            ValidationOutput output = bpmnValidator.validateBpmnFile(file.toPath());
            output.printResults();

            // aggregator
            allBpmnItems.addAll(output.getValidationItems());

            // per-file JSON
            String processId = output.getProcessId();
            File jsonFile = new File("bpmn_issues_" + processId + ".json");
            output.writeResultsAsJson(jsonFile);

            System.out.println("Validation completed for: " + file.getName()
                    + " => BPMN JSON: " + jsonFile.getAbsolutePath());
        }

        // aggregator JSON
        ValidationOutput aggregatedBpmn = new ValidationOutput(allBpmnItems);
        File aggregatedJson = new File("bpmn_issues_aggregated.json");
        aggregatedBpmn.writeResultsAsJson(aggregatedJson);

        System.out.println("\nAll BPMN validation items aggregated in: " + aggregatedJson.getName());
    }

    /**
     * Validates all FHIR resources by calling {@code fhirValidator.validateAllFhirResources(...)}
     * (like the "Main" class). Writes a single aggregated JSON named "fhir_issues_aggregated.json".
     */
    private void validateAllFhirResources(File projectDir)
    {
        //todo
    }

    /**
     * Recursively searches for a sub-path like "src/main/resources/bpe" or "src/main/resources/fhir"
     * starting from the given root path.
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
     * Recursively finds all files under the given {@code rootPath} that have the specified extension.
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
}
