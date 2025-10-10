package dev.dsf.utils.validator.logger;

/**
 * Utility for styled log output.
 * Prints a blank line before each message and colors the message text in mint (#63C7A6).
 */
public final class LogDecorators
{
    private static final String RESET = "\u001B[0m";
    private static final String MINT  = "\u001B[38;2;99;199;166m"; // RGB(99,199,166)

    private LogDecorators() { /* Utility class */ }

    /** Logs a blank line, then a mint-colored message. */
    public static void infoMint(Logger logger, String message)
    {
        logger.info(""); // blank line for spacing
        logger.info(MINT + message + RESET);
    }
}
