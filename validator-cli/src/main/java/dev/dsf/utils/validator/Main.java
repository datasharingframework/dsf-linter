package dev.dsf.utils.validator;


import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.repo.RepositoryManager;
import dev.dsf.utils.validator.util.Console;

import dev.dsf.utils.validator.logger.ConsoleLogger;
import org.eclipse.jgit.api.errors.GitAPIException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * <h2>DSF Validator CLI Entry Point</h2>
 *
 * <p>
 * This is the main class for running the DSF Validator as a command-line application.
 * It supports validation of both BPMN and FHIR resources from:
 * </p>
 * <ul>
 * <li>A local project directory, using {@code --localPath}</li>
 * <li>A remote Git repository, using {@code --remoteRepo}</li>
 * </ul>
 *
 * <p>
 * The class performs the following high-level operations:
 * </p>
 * <ol>
 * <li>Parses command-line arguments using <a href="https://picocli.info/">Picocli</a>.</li>
 * <li>Validates that exactly one input source is provided (local path or remote repository).</li>
 * <li>For remote repositories, clones them to a temporary directory using {@link RepositoryManager}.</li>
 * <li>Initializes and runs the main validation logic within {@link DsfValidatorImpl}.</li>
 * <li>Catches and handles various error scenarios with appropriate error messages and exit codes.</li>
 * </ol>
 *
 * <h3>Command-line Usage</h3>
 * <pre>{@code
 * java -jar dsf-validator.jar --localPath /path/to/local/project
 * java -jar dsf-validator.jar --remoteRepo https://gitlab.com/org/project.git
 * }</pre>
 *
 * <h3>Exit Codes</h3>
 * <ul>
 * <li>{@code 0} - Validation completed successfully</li>
 * <li>{@code 1} - Error occurred (invalid arguments, cloning failure, validation failure, etc.)</li>
 * </ul>
 *
 * <h3>Error Handling</h3>
 * <ul>
 * <li>{@link IllegalStateException} - ProcessPluginDefinition discovery failure</li>
 * <li>{@link ResourceValidationException} - BPMN or FHIR resource parsing errors</li>
 * <li>{@link GitAPIException} - Git repository cloning failures</li>
 * <li>Generic {@link Exception} - Other unexpected errors during validation</li>
 * </ul>
 *
 * <h3>Behavior Notes</h3>
 * <ul>
 * <li>Exactly one of {@code --localPath} or {@code --remoteRepo} must be provided.</li>
 * <li>Local paths must exist and be directories.</li>
 * <li>Remote repositories are cloned to temporary directories under system temp folder.</li>
 * <li>If the {@code ProcessPluginDefinition} cannot be uniquely discovered, validation is aborted.</li>
 * <li>If a BPMN or FHIR resource referenced by the plugin definition contains a syntax error, validation is aborted.</li>
 * <li>The validator internally handles the Maven build and generates all reports.</li>
 * </ul>
 *
 * @see DsfValidatorImpl
 * @see RepositoryManager
 * @see ResourceValidationException
 */
@Command(
        name = "Main",
        mixinStandardHelpOptions = true,
        description = "Validates BPMN and FHIR files from a local path or a remote Git repository based on a ProcessPluginDefinition."
)
public class Main implements Callable<Integer>
{
    /** Path to a local project directory to be validated. */
    @Option(names = "--localPath", description = "Path to a local project directory.")
    private File localPath;

    /** URL of a remote Git repository to be cloned and validated. */
    @Option(names = "--remoteRepo", description = "URL of a remote Git repository to be cloned.")
    private String remoteRepoUrl;

    /** Main method that initializes command-line processing with Picocli. */
    public static void main(String[] args)
    {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Main execution logic. This method prepares the environment and delegates the entire
     * validation workflow to {@link DsfValidatorImpl}. It is responsible for catching
     * fatal exceptions and translating them into a clear console output and exit code.
     *
     * @return exit code (0 if successful, 1 if a fatal error occurred)
     */
    @Override
    public Integer call()
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
        Optional<File> projectDirOpt = (remoteRepoUrl != null)
                ? cloneRepository(remoteRepoUrl)
                : Optional.ofNullable(localPath);

        if (projectDirOpt.isEmpty())
        {
            return 1;
        }

        File projectDir = projectDirOpt.get();

        // The validator orchestrates the entire process.
        DsfValidatorImpl validator = new DsfValidatorImpl(new ConsoleLogger());

        try
        {
            // This single call runs the entire validation workflow, which includes:
            // - Maven build
            // - ProcessPluginDefinition discovery
            // - Validation of referenced resources
            // - Leftover detection and reporting
            validator.validate(projectDir.toPath());
        }
        catch (IllegalStateException e) // Catches failure from PluginDefinitionDiscovery
        {
            Console.red("\nFATAL ERROR: Could not discover a unique ProcessPluginDefinition implementation.");
            Console.red(e.getMessage());
            Console.red("Validation aborted.");
            return 1;
        }
        catch (ResourceValidationException e) // Catches parsing/syntax errors in BPMN or FHIR files
        {
            Console.red("\nFATAL ERROR: A resource file could not be parsed, which indicates a syntax error.");
            Console.red("File: " + e.getFilePath().toString());
            Console.red("Reason: " + e.getCause().getMessage());
            Console.red("Validation aborted.");
            return 1;
        }
        catch (Exception e) // Generic catch-all for other unexpected errors like build failures
        {
            System.err.println("\nAn unexpected error occurred during validation: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }

        return 0; // Success
    }

    /**
     * Clones a remote Git repository into a temporary directory under the system's temp folder.
     *
     * @param remoteUrl the remote repository URL
     * @return an {@link Optional} containing the cloned repository's directory, or empty if cloning fails
     */
    private Optional<File> cloneRepository(String remoteUrl)
    {
        String repositoryName = remoteUrl.substring(remoteUrl.lastIndexOf('/') + 1); //
        File cloneDir = new File(System.getProperty("java.io.tmpdir"), repositoryName); //

        RepositoryManager repoManager = new RepositoryManager(); //
        try
        {
            File result = repoManager.getRepository(remoteUrl, cloneDir); //
            return Optional.ofNullable(result); //
        }
        catch (GitAPIException e)
        {
            System.err.println("ERROR: Failed to clone repository: " + e.getMessage()); //
            return Optional.empty(); //
        }
    }
}