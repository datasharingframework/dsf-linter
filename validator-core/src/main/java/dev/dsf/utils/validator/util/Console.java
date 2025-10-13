package dev.dsf.utils.validator.util;

/**
 * Utility class for colored console output using ANSI escape codes.
 */
public class Console {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[38;2;63;137;153m";
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
        printColored(message, ANSI_RED);
    }

    public static void green(String message) {
        printColored(message, ANSI_GREEN);
    }

    public static void yellow(String message) {
        printColored(message, ANSI_YELLOW);
    }

    public static void blue(String message) {
        printColored(message, ANSI_BLUE);
    }

    public static void cyan(String message) {
        printColored(message, ANSI_CYAN);
    }

    public static void bold(String message) {
        printColored(message, ANSI_BOLD);
    }

    private static void printColored(String message, String colorCode) {
        if (COLOR_ENABLED) {
            System.out.println(colorCode + message + ANSI_RESET);
        } else {
            System.out.println(message);
        }
    }

}