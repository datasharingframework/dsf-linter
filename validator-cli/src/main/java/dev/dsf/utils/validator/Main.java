package dev.dsf.utils.validator;

import dev.dsf.utils.validator.build.MavenBuilder;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.repo.RepositoryManager;
import dev.dsf.utils.validator.util.MavenUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * <p>
 * The {@code Main} class serves as the entry point for validating both BPMN and FHIR files,
 * either from a local path or from a remote Git repository that must be cloned and built.
 * It uses <strong>picocli</strong> for command-line argument parsing, <strong>JGit</strong>
 * for cloning remote repositories, and <strong>Maven</strong> for building the cloned
 * project (if applicable).
 * </p>
 *
 * <p><strong>Main Responsibilities:</strong></p>
 * <ol>
 *   <li>Parse command-line arguments using {@link picocli.CommandLine}.</li>
 *   <li>Either use a local project directory or clone a remote Git repository.</li>
 *   <li>Run an Apache Maven build to ensure the project is built.</li>
 *   <li>Invoke the {@link DsfValidatorImpl} to generate reports in the new structure:
 *       <ul>
 *         <li>A single {@code report} folder at the root.</li>
 *         <li>Inside it, <em>bpmnReports</em>, each containing
 *             <em>success</em> and <em>other</em> subfolders, as well as a
 *             <code>_aggregated.json</code> for that file type.</li>
 *         <li>An additional <code>aggregated.json</code> file at the root that combines
 *             <em>all</em> BPMN items.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p><strong>Example Usage:</strong></p>
 * <pre>
 *   java -jar dsf-validator.jar --localPath /path/to/local/project
 *
 *   or
 *
 *   java -jar dsf-validator.jar --remoteRepo https://github.com/user/some-bpmn-project.git
 * </pre>
 *
 * <h3>References and Further Reading:</h3>
 * <ul>
 *   <li><a href="https://picocli.info/">Picocli Documentation</a> – for command-line parsing</li>
 *   <li><a href="https://www.eclipse.org/jgit/">JGit Documentation</a> – for Git repository cloning</li>
 *   <li><a href="https://maven.apache.org/">Apache Maven</a> – for building the cloned project</li>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0</a> – for BPMN file validation specifics</li>
 * </ul>
 */
@Command(
        name = "Main",
        mixinStandardHelpOptions = true,
        description = "Validates BPMN and FHIR files either from a local path or by cloning a remote repository."
)
public class Main implements Callable<Integer>
{
    /**
     * The local project directory to validate, if specified.
     * If {@code null}, the validator will attempt to use a remote Git repository.
     */
    @Option(
            names = {"--localPath"},
            description = "Path to a local project directory (if you want to validate locally)."
    )
    private File localPath;

    /**
     * The remote Git repository URL to clone and validate, if specified.
     * If empty, the validator will attempt to use a local path instead.
     */
    @Option(
            names = {"--remoteRepo"},
            description = "URL of a remote Git repository (if you want to clone and validate remotely)."
    )
    private String remoteRepoUrl;

    /**
     * Entry point for the Java application. Delegates to picocli's
     * {@code CommandLine} processing.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args)
    {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    /**
     * <p>
     * Called by picocli after parsing command-line arguments. Ensures either
     * {@code --localPath} or {@code --remoteRepo} is provided (but not both), attempts
     * to build the project via Maven, and triggers BPMN & FHIR validations
     * through {@link DsfValidatorImpl}.
     * </p>
     *
     * <p><strong>Flow:</strong></p>
     * <ol>
     *   <li>If {@code --remoteRepo} is specified, clone the repository using {@link RepositoryManager}.</li>
     *   <li>Locate Maven with {@link MavenUtil#locateMavenExecutable()}.</li>
     *   <li>Build the project with {@link MavenBuilder#buildProject(File, String)}.</li>
     *   <li>Create or identify the <code>report</code> directory for writing the new structure (bpmnReports, fhirReports, etc.).</li>
     *   <li>Invoke the DSF validator methods for BPMN, returning the respective items.</li>
     *   <li>Combine both sets into a single list to generate a <code>report/aggregated.json</code>.</li>
     * </ol>
     *
     * @return an integer exit code (0 indicates success)
     * @throws Exception if any error occurs during cloning, building, or validation
     */
    @Override
    public Integer call() throws Exception
    {
        // 1. Validate arguments
        if ((localPath == null || !localPath.isDirectory()) &&
                (remoteRepoUrl == null || remoteRepoUrl.isEmpty()))
        {
            System.err.println("ERROR: You must specify either --localPath (a valid directory) " +
                    "or --remoteRepo (a valid URL).");
            return 1;
        }

        // 2. Determine project directory (local or cloned)
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

        // 3. Locate Maven
        String mavenCmd = MavenUtil.locateMavenExecutable();
        if (mavenCmd == null)
        {
            System.err.println("ERROR: Maven executable not found. Ensure Maven is installed and in your PATH.");
            return 1;
        }

        // 4. Build the project with Maven
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

        // 5. Create the DSF validator and produce the new "report" structure
        DsfValidatorImpl dsfValidator = new DsfValidatorImpl();
        // Where the new "report" folder is created or cleaned up
        File reportRoot = new File("report");
        reportRoot.mkdirs();

        // Validate BPMN (split by severity) => returns all BPMN items
        List<AbstractValidationItem> allBpmnItems = dsfValidator.validateAllBpmnFilesSplitNewStructure(projectDir, reportRoot);

        // Validate FHIR (split by severity) => returns all FHIR items
        //todo

        // Combine them for a global aggregated JSON
        List<AbstractValidationItem> combinedItems = new ArrayList<>(allBpmnItems);
        //combinedItems.addAll(allFhirItems);
        // Write to "report/aggregated.json"
        ValidationOutput globalOutput = new ValidationOutput(combinedItems);
        File globalJson = new File(reportRoot, "aggregated.json");
        globalOutput.writeResultsAsJson(globalJson);
        System.out.println("Wrote combined BPMN+FHIR issues to: " + globalJson.getAbsolutePath());

        System.out.println("\nValidation process finished!");
        return 0;
    }

    /**
     * <p>
     * Clones the specified remote Git repository into a temporary local directory
     * using the {@link RepositoryManager}.
     * </p>
     *
     * <p>
     * The method computes a directory name from the repository URL by taking the substring
     * after the final '{@code /}'. The repository is then cloned into the system's
     * temporary directory, as determined by {@code System.getProperty("java.io.tmpdir")}.
     * </p>
     *
     * @param remoteUrl the URL of the remote Git repository to clone
     * @return the local {@link File} directory of the cloned repository, or {@code null} if cloning fails
     */
    private File cloneRepository(String remoteUrl)
    {
        // Derive local folder name from the repository URL
        String repositoryName = remoteUrl.substring(remoteUrl.lastIndexOf('/') + 1);

        // Create a directory under the system temp folder
        File cloneDir = new File(System.getProperty("java.io.tmpdir"), repositoryName);

        // Clone using JGit via RepositoryManager
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
}
