package dev.dsf.linter.exception;

import java.nio.file.Path;

/**
 * Custom exception thrown when a resource file (BPMN or FHIR) cannot be parsed,
 * typically indicating a syntax error.
 * <p>
 * This exception is designed to be fatal, causing the validation process to abort.
 * </p>
 */
public class ResourceLinterException extends Exception
{
    private final Path filePath;

    /**
     * Constructs a new ResourceLinterException.
     *
     * @param message the detail message
     * @param filePath the path to the file that caused the error
     * @param cause the root cause of the parsing failure
     */
    public ResourceLinterException(String message, Path filePath, Throwable cause)
    {
        super(message, cause);
        this.filePath = filePath;
    }

    /**
     * @return the path to the file that failed to parse.
     */
    public Path getFilePath()
    {
        return filePath;
    }
}