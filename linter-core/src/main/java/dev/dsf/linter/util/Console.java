package dev.dsf.linter.util;

import dev.dsf.linter.logger.LogDecorators;
import dev.dsf.linter.logger.Logger;

/**
 * Utility class for colored console output using ANSI escape codes.
 * All output goes through the Logger to ensure consistent behavior across different environments.
 * By default, colors are disabled and must be explicitly enabled via enableColors().
 */
public class Console {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[38;2;63;137;153m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private static boolean colorEnabled = false;
    private static Logger logger;

    private Console() {
    }

    /**
     * Initialize the Console with a Logger instance.
     * Must be called before using any color methods.
     *
     * @param logger The logger instance to use for all console output
     */
    public static void init(Logger logger) {
        Console.logger = logger;
    }

    /**
     * Enable colored output.
     * By default, colors are disabled.
     * This also enables colors for LogDecorators.
     * Respects environment variables like NO_COLOR and terminal capabilities.
     */
    public static void enableColors() {
        // Only enable if the environment supports it
        if (isColorSupported()) {
            colorEnabled = true;
            LogDecorators.enableColors();
        }
    }

    /**
     * Disable colored output.
     * This also disables colors for LogDecorators.
     */
    public static void disableColors() {
        colorEnabled = false;
        LogDecorators.disableColors();
    }

    /**
     * Prints a message in red color.
     *
     * @param message The message to print
     */
    public static void red(String message) {
        printColored(message, ANSI_RED);
    }

    /**
     * Prints a red error message directly to System.err.
     * This method bypasses the logger and is intended for critical errors
     * that must be written to stderr regardless of logger configuration.
     * Note: This always shows color regardless of colorEnabled setting.
     *
     * @param message The error message to print
     */
    public static void redErr(String message) {
        System.err.println(ANSI_RED + message + ANSI_RESET);
        System.err.flush();
    }

    /**
     * Prints a message in green color.
     *
     * @param message The message to print
     */
    public static void green(String message) {
        printColored(message, ANSI_GREEN);
    }

    /**
     * Prints a message in yellow color.
     *
     * @param message The message to print
     */
    public static void yellow(String message) {
        printColored(message, ANSI_YELLOW);
    }

    /**
     * Prints a message in blue color.
     *
     * @param message The message to print
     */
    public static void blue(String message) {
        printColored(message, ANSI_BLUE);
    }

    /**
     * Prints a message in cyan color.
     *
     * @param message The message to print
     */
    public static void cyan(String message) {
        printColored(message, ANSI_CYAN);
    }

    /**
     * Prints a message in bold style.
     *
     * @param message The message to print
     */
    public static void bold(String message) {
        printColored(message, ANSI_BOLD);
    }

    /**
     * Prints a colored message using the logger.
     * Falls back to System.out if logger is not initialized.
     * Colors are only applied if colorEnabled is true.
     *
     * @param message   The message to print
     * @param colorCode The ANSI color code to use
     */
    private static void printColored(String message, String colorCode) {
        String output = colorEnabled ? (colorCode + message + ANSI_RESET) : message;

        if (logger != null) {
            logger.info(output);
        } else {
            System.out.println(output);
        }
    }

    /**
     * Checks if ANSI color codes are supported in the current environment.
     * Respects standard environment variables and terminal capabilities.
     *
     * @return true if colors should be enabled
     */
    private static boolean isColorSupported() {
        // Explicit disable via environment variable (takes precedence)
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null && !noColor.isEmpty()) {
            return false;
        }

        // Explicit enable via environment variable (common in CI)
        String forceColor = System.getenv("FORCE_COLOR");
        if (forceColor != null && !forceColor.equals("0")) {
            return true;
        }

        // Check TERM environment variable
        String term = System.getenv("TERM");
        if (term != null && term.equals("dumb")) {
            return false;
        }

        // On Windows, check for modern terminal support
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String wt = System.getenv("WT_SESSION");
            String ansicon = System.getenv("ANSICON");
            // Windows Terminal or ANSICON present
            if (wt != null || ansicon != null) {
                return true;
            }
        }

        // Default: enable colors (most modern environments support it)
        return true;
    }
}