package dev.dsf.linter.logger;

import dev.dsf.linter.util.Console;

/**
 * Implements the Logger interface to write messages to the standard
 * System.out and System.err console streams. It uses the Console
 * utility to colorize error messages.
 */
public record ConsoleLogger(boolean verbose) implements Logger {

    @Override
    public void info(String message) {
        System.out.println(message);
    }

    @Override
    public void warn(String message) {
        // Using System.err for warnings, similar to errors.
        Console.red("WARN: " + message);
    }

    @Override
    public void error(String message) {
        // Reuse your existing Console class for red output.
        Console.red("ERROR: " + message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        Console.red("ERROR: " + message);
        if (throwable != null) {
            // Print stack trace to standard error stream.
            throwable.printStackTrace(System.err);
        }
    }

    @Override
    public void debug(String message) {
        // Only print debug messages if verbose mode is enabled.
        if (verbose) {
            System.out.println("DEBUG: " + message);
        }
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

}