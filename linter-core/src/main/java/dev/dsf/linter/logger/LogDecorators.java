package dev.dsf.linter.logger;

/**
 * Utility for styled log output.
 * Prints a blank line before each message and colors the message text in mint (#63C7A6).
 * Colors are only applied when Console colors are enabled.
 */
public final class LogDecorators
{
    private static final String RESET = "\u001B[0m";
    private static final String MINT  = "\u001B[38;2;99;199;166m"; // RGB(99,199,166)

    private static boolean colorEnabled = false;

    private LogDecorators() { /* Utility class */ }

    /**
     * Enable colored output for LogDecorators.
     * Should be called when Console colors are enabled.
     */
    public static void enableColors() {
        colorEnabled = true;
    }

    /**
     * Disable colored output for LogDecorators.
     */
    public static void disableColors() {
        colorEnabled = false;
    }

    /**
     * Logs a blank line, then a mint-colored message (if colors are enabled).
     *
     * @param logger  The logger to use
     * @param message The message to log
     */
    public static void infoMint(Logger logger, String message)
    {
        logger.info(""); // blank line for spacing

        if (colorEnabled) {
            logger.info(MINT + message + RESET);
        } else {
            logger.info(message);
        }
    }
}