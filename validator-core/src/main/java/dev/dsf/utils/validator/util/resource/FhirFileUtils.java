package dev.dsf.utils.validator.util.resource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for FHIR file detection and operations.
 * Provides centralized logic for identifying FHIR files by extension.
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

    /**
     * Checks if the given path represents a valid XML file.
     *
     * @param p the path to check
     * @return true if the path is a regular file with .xml extension, false otherwise
     */
    public static boolean isXmlFile(Path p) {
        return Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".xml");
    }

    /**
     * Checks if the given path represents a valid JSON file.
     *
     * @param p the path to check
     * @return true if the path is a regular file with .json extension, false otherwise
     */
    public static boolean isJsonFile(Path p) {
        return Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".json");
    }
}

