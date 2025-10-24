package dev.dsf.linter.input;

import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.repo.RepositoryManager;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Unified input resolver that handles all supported input types for DSF validation.
 * <p>
 * This class provides a single entry point for resolving various input sources
 * into a standardized directory structure that can be processed by the validator.
 * It transparently handles local directories, Git repositories, and JAR files
 * (both local and remote).
 * </p>
 *
 * <h3>Resolution Strategy:</h3>
 * <ol>
 *   <li>Detect input type based on path/URL format</li>
 *   <li>Apply appropriate handler for the input type</li>
 *   <li>Return unified ResolutionResult with directory path</li>
 *   <li>Track whether cleanup is needed (for temporary resources)</li>
 * </ol>
 *
 * <h3>Supported Input Formats:</h3>
 * <ul>
 *   <li><b>Local Directory:</b> /path/to/project</li>
 *   <li><b>Git Repository:</b> https://github.com/user/repo</li>
 *   <li><b>Local JAR:</b> /path/to/plugin.jar</li>
 *   <li><b>Remote JAR:</b> https://example.com/plugin.jar</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class InputResolver {

    private final Logger logger;
    private final RepositoryManager repositoryManager;
    private final JarHandler jarHandler;

    /**
     * Result of input resolution containing the resolved directory and metadata.
     *
     * @param resolvedPath the directory path ready for validation
     * @param inputType the detected type of the original input
     * @param requiresCleanup true if the directory is temporary and needs cleanup
     * @param originalInput the original input string provided by the user
     */
    public record ResolutionResult(
            Path resolvedPath,
            InputType inputType,
            boolean requiresCleanup,
            String originalInput
    ) {}

    /**
     * Constructs a new InputResolver with the specified logger.
     *
     * @param logger the logger for resolution messages
     */
    public InputResolver(Logger logger) {
        this.logger = logger;
        this.repositoryManager = new RepositoryManager();
        this.jarHandler = new JarHandler(logger);
    }

    /**
     * Resolves an input path/URL to a directory that can be validated.
     * <p>
     * This method automatically detects the input type and applies the
     * appropriate resolution strategy. The returned directory is guaranteed
     * to exist and be readable.
     * </p>
     *
     * @param input the input path or URL to resolve
     * @return Optional containing the resolution result, or empty if resolution fails
     */
    public Optional<ResolutionResult> resolve(String input) {
        if (input == null || input.isBlank()) {
            logger.error("Input cannot be null or blank");
            return Optional.empty();
        }

        InputType type = detectInputType(input);
        logger.info("Detected input type: " + type);

        try {
            return switch (type) {
                case LOCAL_DIRECTORY -> resolveLocalDirectory(input);
                case GIT_REPOSITORY -> resolveGitRepository(input);
                case LOCAL_JAR_FILE -> resolveLocalJar(input);
                case REMOTE_JAR_URL -> resolveRemoteJar(input);
            };
        } catch (Exception e) {
            logger.error("Failed to resolve input: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Detects the input type based on path/URL characteristics.
     * <p>
     * Detection rules (in order of precedence):
     * <ol>
     *   <li>Ends with .jar → check if local file or URL</li>
     *   <li>Starts with http://, https://, git://, ssh://, git@ → Git repository</li>
     *   <li>Otherwise → Local directory</li>
     * </ol>
     * </p>
     *
     * @param input the input string to analyze
     * @return the detected InputType
     */
    private InputType detectInputType(String input) {
        String normalized = input.trim().toLowerCase();

        // Check for JAR files first
        if (normalized.endsWith(".jar")) {
            if (isRemoteUrl(normalized)) {
                return InputType.REMOTE_JAR_URL;
            } else {
                return InputType.LOCAL_JAR_FILE;
            }
        }

        // Check for Git repositories
        if (isGitRepository(input)) {
            return InputType.GIT_REPOSITORY;
        }

        // Default to local directory
        return InputType.LOCAL_DIRECTORY;
    }

    /**
     * Checks if an input string represents a remote URL.
     *
     * @param input the input to check
     * @return true if input is an HTTP/HTTPS URL
     */
    private boolean isRemoteUrl(String input) {
        return input.startsWith("http://") || input.startsWith("https://");
    }

    /**
     * Checks if an input string represents a Git repository.
     *
     * @param input the input to check
     * @return true if input matches Git repository patterns
     */
    private boolean isGitRepository(String input) {
        return input.startsWith("http://") ||
                input.startsWith("https://") ||
                input.startsWith("git://") ||
                input.startsWith("ssh://") ||
                input.contains("git@");
    }

    /**
     * Resolves a local directory input.
     *
     * @param input the directory path
     * @return ResolutionResult if directory exists and is valid
     */
    private Optional<ResolutionResult> resolveLocalDirectory(String input) {
        Path path = Path.of(input);

        if (!Files.exists(path)) {
            logger.error("Directory does not exist: " + input);
            return Optional.empty();
        }

        if (!Files.isDirectory(path)) {
            logger.error("Path is not a directory: " + input);
            return Optional.empty();
        }

        logger.info("Using local directory: " + path.toAbsolutePath());

        return Optional.of(new ResolutionResult(
                path.toAbsolutePath(),
                InputType.LOCAL_DIRECTORY,
                false,
                input
        ));
    }

    /**
     * Resolves a Git repository by cloning it to a temporary directory.
     *
     * @param input the Git repository URL
     * @return ResolutionResult with cloned repository path
     */
    private Optional<ResolutionResult> resolveGitRepository(String input) {
        String repositoryName = input.substring(input.lastIndexOf('/') + 1)
                .replace(".git", "");

        Path clonePath = Path.of(
                System.getProperty("java.io.tmpdir"),
                "dsf-validator-git-" + repositoryName
        );

        // Delete existing clone if present
        if (Files.exists(clonePath)) {
            logger.debug("Removing existing clone at: " + clonePath);
            jarHandler.deleteDirectoryRecursively(clonePath);
        }

        try {
            logger.info("Cloning Git repository: " + input);
            File result = repositoryManager.getRepository(input, clonePath.toFile());

            return Optional.of(new ResolutionResult(
                    result.toPath(),
                    InputType.GIT_REPOSITORY,
                    true,
                    input
            ));

        } catch (GitAPIException e) {
            logger.error("Failed to clone Git repository: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolves a local JAR file by extracting it to a temporary directory.
     *
     * @param input the local JAR file path
     * @return ResolutionResult with extracted JAR contents
     */
    private Optional<ResolutionResult> resolveLocalJar(String input) {
        try {
            logger.info("Processing local JAR file: " + input);
            JarHandler.JarProcessingResult result = jarHandler.processJar(input, false);

            return Optional.of(new ResolutionResult(
                    result.extractedDir(),
                    InputType.LOCAL_JAR_FILE,
                    result.isTemporary(),
                    input
            ));

        } catch (IOException | IllegalStateException e) {
            logger.error("Failed to process local JAR: " + e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Resolves a remote JAR by downloading and extracting it.
     *
     * @param input the JAR file URL
     * @return ResolutionResult with extracted JAR contents
     */
    private Optional<ResolutionResult> resolveRemoteJar(String input) {
        try {
            logger.info("Processing remote JAR from URL: " + input);
            JarHandler.JarProcessingResult result = jarHandler.processJar(input, true);

            return Optional.of(new ResolutionResult(
                    result.extractedDir(),
                    InputType.REMOTE_JAR_URL,
                    result.isTemporary(),
                    input
            ));

        } catch (IOException | IllegalStateException | InterruptedException e) {
            logger.error("Failed to process remote JAR: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Cleans up temporary resources created during resolution.
     * <p>
     * Should be called after validation is complete for inputs
     * that have requiresCleanup=true.
     * </p>
     *
     * @param result the resolution result to clean up
     */
    public void cleanup(ResolutionResult result) {
        if (result.requiresCleanup() && result.resolvedPath() != null) {
            logger.info("Cleaning up temporary resources: " + result.resolvedPath());
            jarHandler.deleteDirectoryRecursively(result.resolvedPath());
        }
    }
}