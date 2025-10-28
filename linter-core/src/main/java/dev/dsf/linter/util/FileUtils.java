package dev.dsf.linter.util;

import java.io.File;

/**
 * Utility class for common file operations.
 *
 * <p>Provides helper methods for:
 * <ul>
 * <li>Extracting file names without extensions</li>
 * <li>Extracting parent folder names</li>
 * <li>Other file-related operations</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public final class FileUtils {

    private FileUtils() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Extracts the file name without any extension.
     *
     * @param file the file to process
     * @return the file name without extension, or the original name if no extension exists
     */
    public static String getFileNameWithoutExtension(File file) {
        String name = file.getName();
        int lastDotIndex = name.lastIndexOf('.');
        return (lastDotIndex > 0) ? name.substring(0, lastDotIndex) : name;
    }

    /**
     * Extracts the file name without a specific extension.
     *
     * @param file the file to process
     * @param extension the extension to remove (e.g., ".bpmn")
     * @return the file name without the specified extension, or the original name if it doesn't end with that extension
     */
    public static String getFileNameWithoutExtension(File file, String extension) {
        String name = file.getName();
        return name.endsWith(extension)
                ? name.substring(0, name.length() - extension.length())
                : name;
    }

    /**
     * Extracts the parent folder name from a file.
     *
     * @param file the file to process
     * @return the parent folder name, or "root" if the file has no parent
     */
    public static String getParentFolderName(File file) {
        return file.getParentFile() != null
                ? file.getParentFile().getName()
                : "root";
    }
}