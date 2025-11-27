package dev.dsf.linter.exception;

/**
 * Exception thrown when the DSF BPE API version cannot be determined (UNKNOWN)
 * and linting cannot proceed without knowing the correct API version.
 * <p>
 * This exception is designed to be fatal, causing the linting process to abort.
 * The API version must be explicitly detected before linting can proceed.
 * </p>
 */
public class ApiVersionUnknownException extends RuntimeException {

    /**
     * Constructs a new ApiVersionUnknownException with a custom message.
     *
     * @param message the detail message
     */
    public ApiVersionUnknownException(String message) {
        super(message);
    }

}

