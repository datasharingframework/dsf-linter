package dev.dsf.utils.validator;

import dev.dsf.utils.validator.build.MavenBuilder;
import dev.dsf.utils.validator.repo.RepositoryManager;
import dev.dsf.utils.validator.util.MavenUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * <p>
 * Entry point for validating BPMN and FHIR files contained in either a local project directory
 * or a remote Git repository. If a remote repository is specified, it is cloned before validation.
 * </p>
 *
 * <p>
 * This class uses:
 * <ul>
 *   <li><strong>Picocli</strong> – for command-line argument parsing</li>
 *   <li><strong>JGit</strong> – for Git repository cloning</li>
 *   <li><strong>Maven</strong> – for building the cloned or local project before validation</li>
 * </ul>
 * </p>
 *
 * <p><strong>Main responsibilities:</strong></p>
 * <ol>
 *   <li>Parse command-line arguments.</li>
 *   <li>Clone a remote Git repository or use a specified local project directory.</li>
 *   <li>Build the project using Maven.</li>
 *   <li>Run validations for BPMN and FHIR files and generate reports.</li>
 * </ol>
 *
 * <h3>Example Usage:</h3>
 * <pre>
 *   java -jar dsf-validator.jar --localPath /path/to/local/project
 *
 *   or
 *
 *   java -jar dsf-validator.jar --remoteRepo https://github.com/user/project.git
 * </pre>
 *
 * <h3>References:</h3>
 * <ul>
 *   <li><a href="https://picocli.info/">Picocli Documentation</a></li>
 *   <li><a href="https://www.eclipse.org/jgit/">JGit Documentation</a></li>
 *   <li><a href="https://maven.apache.org/">Apache Maven Documentation</a></li>
 * </ul>
 */
@Command(
        name = "Main",
        mixinStandardHelpOptions = true,
        description = "Validates BPMN and FHIR files from a local path or a remote Git repository."
)
public class Main implements Callable<Integer>
{
    /**
     * Path to a local project directory to be validated.
     */
    @Option(names = "--localPath", description = "Path to a local project directory.")
    private File localPath;

    /**
     * URL of a remote Git repository to be cloned and validated.
     */
    @Option(names = "--remoteRepo", description = "URL of a remote Git repository to be cloned.")
    private String remoteRepoUrl;

    /**
     * Main method that initializes command-line processing with Picocli.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args)
    {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Method executed after command-line parsing is completed.
     * Handles validation process including cloning (if necessary), building with Maven, and running validations.
     *
     * @return exit code (0 if successful, non-zero otherwise)
     * @throws Exception if an unexpected error occurs
     */
    @Override
    public Integer call() throws Exception
    {
        // Validate that exactly one of --localPath or --remoteRepo is provided.
        if ((localPath == null || !localPath.isDirectory()) && (remoteRepoUrl == null || remoteRepoUrl.isBlank()))
        {
            System.err.println("ERROR: Specify either --localPath or --remoteRepo.");
            return 1;
        }
        if (localPath != null && remoteRepoUrl != null)
        {
            System.err.println("ERROR: Use only one of --localPath or --remoteRepo, not both.");
            return 1;
        }

        // Determine the project directory (either local or cloned).
        File projectDir = (remoteRepoUrl != null) ? cloneRepository(remoteRepoUrl) : localPath;
        if (projectDir == null)
        {
            return 1;
        }

        // Locate Maven executable and build the project.
        MavenBuilder builder = new MavenBuilder();
        String mavenExecutable = MavenUtil.locateMavenExecutable();
        if (mavenExecutable == null)
        {
            System.err.println("ERROR: Maven executable not found in PATH.");
            return 1;
        }
        if (!builder.buildProject(projectDir, mavenExecutable,
                "-B", "-DskipTests", "-Dformatter.skip=true", "-Dexec.skip=true", "clean", "package"))
        {
            System.err.println("ERROR: Maven 'package' phase failed.");
            return 1;
        }

        // Validate the project files (BPMN and FHIR).
        DsfValidatorImpl validator = new DsfValidatorImpl();
        ValidationOutput output = validator.validate(projectDir.toPath());

        System.out.printf("%nValidation finished – %d issue(s) found.%n", output.validationItems().size());
        System.out.println("Reports written to: " + new File("report").getAbsolutePath());
        return 0;
    }

    /**
     * Clones a remote Git repository into a temporary directory under the system's temp folder.
     *
     * @param remoteUrl the remote repository URL
     * @return a {@link File} representing the cloned repository's directory, or {@code null} if cloning fails
     */
    private File cloneRepository(String remoteUrl)
    {
        String repositoryName = remoteUrl.substring(remoteUrl.lastIndexOf('/') + 1);
        File cloneDir = new File(System.getProperty("java.io.tmpdir"), repositoryName);

        RepositoryManager repoManager = new RepositoryManager();
        try
        {
            return repoManager.getRepository(remoteUrl, cloneDir);
        }
        catch (GitAPIException e)
        {
            System.err.println("ERROR: Failed to clone repository: " + e.getMessage());
            return null;
        }
    }
}
