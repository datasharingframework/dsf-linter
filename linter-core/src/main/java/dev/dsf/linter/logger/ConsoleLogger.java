package dev.dsf.linter.logger;

/**
 * Implements the Logger interface to write messages to the standard
 * System.out and System.err console streams with colored output.
 */
public record ConsoleLogger(boolean verbose) implements Logger {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    @Override
    public void info(String message) {
        System.out.println(message);
        System.out.flush();
    }

    @Override
    public void warn(String message) {
        System.err.println(ANSI_YELLOW + "WARN: " + message + ANSI_RESET);
        System.err.flush();
    }

    @Override
    public void error(String message) {
        System.err.println(ANSI_RED + "ERROR: " + message + ANSI_RESET);
        System.err.flush();
    }

    @Override
    public void error(String message, Throwable throwable) {
        System.err.println(ANSI_RED + "ERROR: " + message + ANSI_RESET);
        if (throwable != null) {
            // Print stack trace to standard error stream.
            throwable.printStackTrace(System.err);
        }
        System.err.flush();
    }

    @Override
    public void debug(String message) {
        // Only print debug messages if verbose mode is enabled.
        if (verbose) {
            System.out.println("DEBUG: " + message);
            System.out.flush();
        }
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }
}