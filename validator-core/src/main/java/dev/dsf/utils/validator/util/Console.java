package dev.dsf.utils.validator.util;

/**
 * Utility class for colored console output using ANSI escape codes.
 */
public class Console {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private static final boolean COLOR_ENABLED = isColorSupported();

    private Console() {
    }

    private static boolean isColorSupported() {
        String os = System.getProperty("os.name").toLowerCase();
        String term = System.getenv("TERM");

        if (os.contains("win")) {
            String ansicon = System.getenv("ANSICON");
            String wt = System.getenv("WT_SESSION");
            return ansicon != null || wt != null || System.console() != null;
        }

        return term != null && !term.equals("dumb");
    }

    public static void red(String message) {
        printColored(message, ANSI_RED, System.err);
    }

    public static void green(String message) {
        printColored(message, ANSI_GREEN, System.out);
    }

    public static void yellow(String message) {
        printColored(message, ANSI_YELLOW, System.out);
    }

    public static void blue(String message) {
        printColored(message, ANSI_BLUE, System.out);
    }

    public static void cyan(String message) {
        printColored(message, ANSI_CYAN, System.out);
    }

    public static void bold(String message) {
        printColored(message, ANSI_BOLD, System.out);
    }

    private static void printColored(String message, String colorCode, java.io.PrintStream stream) {
        if (COLOR_ENABLED) {
            stream.println(colorCode + message + ANSI_RESET);
        } else {
            stream.println(message);
        }
    }

    public static void printWithSeverity(String message, String severity) {
        switch (severity) {
            case "ERROR" -> red(message);
            case "WARN" -> yellow(message);
            case "SUCCESS" -> green(message);
            default -> System.out.println(message);
        }
    }
}