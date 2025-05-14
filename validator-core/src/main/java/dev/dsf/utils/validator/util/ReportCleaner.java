package dev.dsf.utils.validator.util;

import java.io.File;

/**
 * Utility class for cleaning up report directories before writing new validation results.
 *
 * <p>This class is used to ensure that any previously existing report directories are removed
 * before generating new output during the validation process. This avoids accumulation of stale
 * or outdated validation data across builds.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * File reportDir = new File("target/dsf-validation-reports");
 * ReportCleaner.prepareCleanReportDirectory(reportDir);
 * }</pre>
 */
public class ReportCleaner
{
    /**
     * Deletes the given directory and all of its contents recursively.
     *
     * @param directory the directory to delete (can be a file or folder)
     * @return {@code true} if deletion was successful or the directory does not exist,
     *         {@code false} if any file could not be deleted
     */
    public static boolean deleteDirectoryRecursively(File directory)
    {
        if (directory == null || !directory.exists())
            return true;

        File[] allContents = directory.listFiles();
        if (allContents != null)
        {
            for (File file : allContents)
            {
                boolean deleted = deleteDirectoryRecursively(file);
                if (!deleted)
                {
                    System.err.println("WARNING: Failed to delete file or folder: " + file.getAbsolutePath());
                    return false;
                }
            }
        }

        return directory.delete();
    }

    /**
     * Prepares a clean report directory by deleting the old one (if it exists)
     * and creating a new empty directory in its place.
     *
     * @param reportDirectory the report directory to clean and prepare
     */
    public static void prepareCleanReportDirectory(File reportDirectory)
    {
        if (reportDirectory.exists())
        {
            System.out.println("INFO: Deleting existing report directory: " + reportDirectory.getAbsolutePath());
            boolean deleted = deleteDirectoryRecursively(reportDirectory);
            if (!deleted)
            {
                System.err.println("ERROR: Failed to fully delete the report directory: " + reportDirectory.getAbsolutePath());
            }
        }

        if (!reportDirectory.exists() && !reportDirectory.mkdirs())
        {
            System.err.println("ERROR: Failed to create report directory: " + reportDirectory.getAbsolutePath());
        }
    }
}
