package dev.dsf.linter;

import dev.dsf.linter.logger.Logger;

/**
 * Utility class for printing linter results to the console.
 * <p>
 * This class provides formatted output of linting results, including
 * error counts, warning counts, and leftover resources. It works uniformly
 * for any number of plugins.
 * </p>
 *
 * @author DSF Development Team
 * @since 1.2.0
 */
public class ResultPrinter {

    /**
     * Prints a summary of the linting results.
     * <p>
     * Displays total errors, warnings, and leftover resources found
     * across all linted plugins.
     * </p>
     *
     * @param result the overall linting result
     * @param logger the logger for output
     */
    public static void printResult(DsfLinter.OverallLinterResult result, Logger logger) {
        // Unified summary for any number of plugins
        logger.info(String.format(
                "\nLinting completed for %d plugin(s) with %d total plugin errors and %d total plugin warnings.",
                result.pluginLinter().size(),
                result.getPluginErrors(),
                result.getPluginWarnings()
        ));

        if (result.getLeftoverCount() > 0) {
            logger.info(String.format(
                    "Additionally, %d unreferenced project-level resource(s) were found.",
                    result.getLeftoverCount()
            ));
        }

        logger.info("Reports written to: " + result.masterReportPath().toUri());
    }

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with static methods only.
     */
    private ResultPrinter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}

