package dev.dsf.utils.validator;

import dev.dsf.utils.validator.repo.RepositoryManager;
import dev.dsf.utils.validator.build.MavenBuilder;
import dev.dsf.utils.validator.util.MavenUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;

/**
 * The {@code Main} class combines local and remote validation flows into a single entry point.
 * It can:
 * <ul>
 *   <li>Use a local path to validate BPMN and FHIR files directly (via {@link DsfValidatorImpl}).</li>
 *   <li>Use a remote repository URL, clone it, build it with Maven, and then validate BPMN and FHIR files.</li>
 * </ul>
 *
 * <p><strong>Main Steps:</strong></p>
 * <ol>
 *   <li>Parse command-line arguments with picocli.</li>
 *   <li>Check if {@code --localPath} or {@code --remoteRepo} is provided.</li>
 *   <li>If remote, clone via {@link dev.dsf.utils.validator.repo.RepositoryManager}.</li>
 *   <li>Locate and run Maven using {@link dev.dsf.utils.validator.util.MavenUtil} and
 *       {@link dev.dsf.utils.validator.build.MavenBuilder}.</li>
 *   <li>Use {@link DsfValidatorImpl#validateAllBpmnFiles(File, File)} to recursively validate BPMN files.</li>
 * </ol>
 *
 * <p><strong>References and Further Reading:</strong></p>
 * <ul>
 *   <li><a href="https://www.eclipse.org/jgit/">JGit Documentation</a></li>
 *   <li><a href="https://picocli.info/">picocli</a></li>
 *   <li><a href="https://maven.apache.org/">Apache Maven</a></li>
 *   <li><a href="https://hl7.org/fhir/">HL7 FHIR Specification</a></li>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 * </ul>
 */
@Command(
        name = "Main",
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

        // Determine project directory (local or cloned)
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

        // Build the project with Maven
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

        // Create a timestamped reports directory (e.g., "reports_01042025_154530")
        File reportsDir = createReportsDirectory();

        // Validate BPMN and FHIR using the DSF validator
        DsfValidatorImpl dsfValidator = new DsfValidatorImpl();
        dsfValidator.validateAllBpmnFiles(projectDir, reportsDir);

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
     * Creates a "reports_" directory with the current date/time in the format {@code ddMMyyyy_HHmmss}.
     * Example: {@code reports_01042025_154530}.
     *
     * @return A {@link File} pointing to the created directory (may be null if creation fails).
     */
    private File createReportsDirectory()
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
}
