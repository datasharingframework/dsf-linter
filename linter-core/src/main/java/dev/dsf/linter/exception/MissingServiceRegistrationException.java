package dev.dsf.linter.exception;

/**
 * Exception thrown when no ProcessPluginDefinition service registration is found.
 */
public class MissingServiceRegistrationException extends Exception {
    public MissingServiceRegistrationException(String message) {
        super(message);
    }
}

