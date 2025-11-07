package dev.dsf.linter.util.resource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for FHIR file operations.
 * <p>
 * This class provides helper methods for identifying and working with FHIR resource
 * files. FHIR resources can be stored in either XML or JSON format, and this utility
 * helps determine if a given file represents a FHIR resource based on its extension.
 * </p>
 */
public final class FhirFileUtils {

    private FhirFileUtils() { /* utility class */ }

    /**
     * Checks if a file path represents a FHIR file (either XML or JSON).
     *
     * @param p the path to check
     * @return true if the path is a regular file with .xml or .json extension, false otherwise
     */
    public static boolean isFhirFile(Path p) {
        String fileName = p.toString().toLowerCase();
        return Files.isRegularFile(p) &&
                (fileName.endsWith(".xml") || fileName.endsWith(".json"));
    }
}

