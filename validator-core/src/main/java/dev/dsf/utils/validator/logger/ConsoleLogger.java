package dev.dsf.utils.validator.logger;

import dev.dsf.utils.validator.util.Console;

/**
 * Implements the Logger interface to write messages to the standard
 * System.out and System.err console streams. It uses the Console
 * utility to colorize error messages.
 */
public class ConsoleLogger implements Logger {

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
}