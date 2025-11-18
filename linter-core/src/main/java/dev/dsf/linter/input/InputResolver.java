package dev.dsf.linter.input;

import dev.dsf.linter.logger.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Input resolver that handles JAR file inputs for DSF Linter.
 * <p>
 * This class provides a single entry point for resolving JAR file sources
 * (both local and remote) into a standardized directory structure that can be
 * processed by the linter.
 * </p>
 *
 * <h3>Resolution Strategy:</h3>
 * <ol>
 *   <li>Detect input type (local or remote JAR)</li>
 *   <li>Process JAR file (download if remote, extract)</li>
 *   <li>Return ResolutionResult with extracted directory path</li>
 *   <li>Track whether cleanup is needed (for temporary resources)</li>
 * </ol>
 *
 * <h3>Supported Input Formats:</h3>
 * <ul>
 *   <li><b>Local JAR:</b> C:\path\to\plugin.jar or /path/to/plugin.jar</li>
 *   <li><b>Remote JAR:</b> https://example.com/plugin.jar</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class InputResolver {

    private final Logger logger;
    private final JarHandler jarHandler;

    /**
     * Result of input resolution containing the resolved directory and metadata.
     *
     * @param resolvedPath the directory path ready for linting
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
        this.jarHandler = new JarHandler(logger);
    }

    /**
     * Resolves a JAR file (local or remote) to a directory that can be linted.
     * <p>
     * This method automatically detects whether the JAR is local or remote,
     * downloads if necessary, and extracts the JAR contents.
     * </p>
     *
     * @param input the JAR file path or URL to resolve
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
     * Detection rules:
     * <ol>
     *   <li>Validates that input ends with .jar</li>
     *   <li>Determines if JAR is local file or remote URL</li>
     * </ol>
     * </p>
     *
     * @param input the input string to analyze
     * @return the detected InputType
     * @throws IllegalArgumentException if input is not a JAR file
     */
    public InputType detectInputType(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input cannot be null or blank");
        }

        String normalized = input.trim().toLowerCase();

        // Validate that input is a JAR file
        if (!normalized.endsWith(".jar")) {
            throw new IllegalArgumentException(
                "Input must be a JAR file (ending with .jar). Got: " + input
            );
        }

        // Determine if remote or local
        if (isRemoteUrl(normalized)) {
            return InputType.REMOTE_JAR_URL;
        } else {
            return InputType.LOCAL_JAR_FILE;
        }
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
     * Should be called after linting is complete for inputs
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

    /**
     * Deletes a directory and all its contents recursively.
     * <p>
     * This method delegates to the JarHandler for consistent cleanup behavior.
     * </p>
     *
     * @param directory the directory to delete
     */
    public void deleteDirectoryRecursively(Path directory) {
        jarHandler.deleteDirectoryRecursively(directory);
    }

    /**
     * Extracts a safe name from the JAR file path for use in temporary directory names.
     * <p>
     * This method creates sanitized names suitable for file system use by:
     * <ul>
     *   <li>Extracting the filename from the path/URL</li>
     *   <li>Removing the .jar extension</li>
     *   <li>Sanitizing special characters</li>
     * </ul>
     * </p>
     *
     * @param inputPath the original JAR file path or URL
     * @param inputType the detected input type
     * @return a sanitized name suitable for directory names
     */
    public String extractInputName(String inputPath, InputType inputType) {
        String name;

        switch (inputType) {
            case LOCAL_JAR_FILE -> {
                Path path = Path.of(inputPath);
                name = path.getFileName().toString().replace(".jar", "");
            }
            case REMOTE_JAR_URL -> {
                String path = inputPath.substring(inputPath.lastIndexOf('/') + 1);
                int queryIndex = path.indexOf('?');
                if (queryIndex > 0) {
                    path = path.substring(0, queryIndex);
                }
                name = path.replace(".jar", "");
            }
            default -> name = "unknown";
        }

        // Sanitize name for use in file system
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}