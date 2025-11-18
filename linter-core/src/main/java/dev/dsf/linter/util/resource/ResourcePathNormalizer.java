package dev.dsf.linter.util.resource;

/**
 * Utility class for normalizing resource paths from plugin definitions.
 * <p>
 * This class provides static methods to normalize resource references by:
 * </p>
 * <ul>
 *   <li>Removing common prefixes (classpath:)</li>
 *   <li>Normalizing path separators (backslashes to forward slashes)</li>
 *   <li>Removing leading slashes</li>
 *   <li>Handling version suffixes in canonical references</li>
 * </ul>
 * <p>
 * All methods are thread-safe and stateless.
 * </p>
 */
public final class ResourcePathNormalizer {

    private ResourcePathNormalizer() {
        // Utility class
    }

    /**
     * Normalizes a resource reference from plugin definitions.
     * <p>
     * Performs the following transformations:
     * </p>
     * <ul>
     *   <li>Strips "classpath:" prefix</li>
     *   <li>Removes leading slashes and backslashes</li>
     *   <li>Converts backslashes to forward slashes</li>
     *   <li>Trims whitespace</li>
     * </ul>
     *
     * @param ref the resource reference to normalize
     * @return normalized path, or empty string if input is null
     */
    public static String normalize(String ref) {
        if (ref == null) {
            return "";
        }

        String normalized = ref.trim();

        if (normalized.startsWith("classpath:")) {
            normalized = normalized.substring("classpath:".length());
        }

        while (normalized.startsWith("/") || normalized.startsWith("\\")) {
            normalized = normalized.substring(1);
        }

        normalized = normalized.replace('\\', '/');

        return normalized;
    }

    /**
     * Normalizes a directory path for use in resource lookup.
     * <p>
     * Ensures the path ends with a forward slash if non-empty.
     * </p>
     *
     * @param directory the directory path to normalize
     * @return normalized directory path with trailing slash
     */
    public static String normalizeDirectory(String directory) {
        if (directory == null || directory.isEmpty()) {
            return "";
        }

        String normalized = directory.replace('\\', '/');

        if (!normalized.endsWith("/")) {
            normalized += "/";
        }

        return normalized;
    }

    /**
     * Removes the version suffix (e.g., "|1.0") from a given string if present.
     * <p>
     * This is commonly used to normalize canonical references that might include
     * a version specifier. For example, "http://example.org|1.0" becomes "http://example.org".
     * </p>
     *
     * @param value the string from which to remove the suffix
     * @return the string without the "|..." part, or the original string if no pipe was found
     */
    public static String removeVersionSuffix(String value) {
        if (value == null) {
            return null;
        }
        int pipeIndex = value.indexOf("|");
        return (pipeIndex != -1) ? value.substring(0, pipeIndex) : value;
    }
}