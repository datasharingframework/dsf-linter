package dev.dsf.linter.input;

import dev.dsf.linter.logger.Logger;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Handler for JAR file operations including download, extraction, and cleanup.
 * <p>
 * This class provides utilities to work with JAR files as input sources for
 * the DSF linter. It handles both local and remote JAR files transparently.
 * </p>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Download JAR files from HTTP/HTTPS URLs</li>
 *   <li>Extract JAR contents to temporary directory</li>
 *   <li>lint JAR structure for DSF plugin requirements</li>
 *   <li>Automatic cleanup of temporary resources</li>
 * </ul>
 *
 * <h3>JAR Structure Requirements:</h3>
 * <p>
 * A valid DSF plugin JAR must contain:
 * <ul>
 *   <li>META-INF/services/dev.dsf.bpe.v1.ProcessPluginDefinition or v2 equivalent</li>
 *   <li>At least one .class file (compiled plugin implementation)</li>
 *   <li>Optional: BPMN files (typically in bpe/ directory)</li>
 *   <li>Optional: FHIR resources (typically in fhir/ directory)</li>
 * </ul>
 * </p>
 *
 * <h3>References:</h3>
 * <ul>
 *   <li><a href="https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jarGuide.html">
 *       Oracle JAR File Specification</a></li>
 *   <li><a href="https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html">
 *       Java ServiceLoader Documentation</a></li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class JarHandler {

    private static final int DOWNLOAD_BUFFER_SIZE = 8192;
    private static final int DOWNLOAD_TIMEOUT_MS = 30000;

    private final Logger logger;

    /**
     * Constructs a new JarHandler with the specified logger.
     *
     * @param logger the logger for operation messages
     */
    public JarHandler(Logger logger) {
        this.logger = logger;
    }

    /**
     * Result of JAR processing containing the extracted directory and metadata.
     *
     * @param extractedDir the directory containing extracted JAR contents
     * @param jarName the original JAR file name
     * @param isTemporary true if the directory should be cleaned up after linting
     */
    public record JarProcessingResult(Path extractedDir, String jarName, boolean isTemporary) {}

    /**
     * Processes a JAR file (local or remote) and extracts it to a directory.
     * <p>
     * This method handles both local JAR files and remote JAR URLs transparently.
     * For JAR files, it also resolves required DSF plugin dependencies via Maven
     * to ensure all API interfaces are available for linting.
     * </p>
     *
     * @param jarPath the path or URL to the JAR file
     * @param isRemote true if jarPath is an HTTP/HTTPS URL
     * @return JarProcessingResult containing extraction information
     * @throws IOException if download, extraction, or dependency resolution fails
     * @throws IllegalStateException if JAR structure is invalid
     * @throws InterruptedException if Maven dependency resolution is interrupted
     */
    public JarProcessingResult processJar(String jarPath, boolean isRemote)
            throws IOException, IllegalStateException, InterruptedException {

        Path jarFile;
        String jarName;

        if (isRemote) {
            logger.info("Downloading JAR from: " + jarPath);
            jarFile = downloadJar(jarPath);
            jarName = extractJarNameFromUrl(jarPath);
        } else {
            jarFile = Paths.get(jarPath);
            jarName = jarFile.getFileName().toString();

            if (!Files.exists(jarFile)) {
                throw new IOException("JAR file not found: " + jarPath);
            }

            if (!Files.isRegularFile(jarFile)) {
                throw new IOException("Path is not a file: " + jarPath);
            }
        }

        logger.info("Processing JAR: " + jarName);

        // lint JAR structure before extraction
        lintJarStructure(jarFile);

        // Create extraction directory
        String cleanName = jarName.replaceAll("[^a-zA-Z0-9._-]", "_")
                .replace(".jar", "");

        Path tempBase = Paths.get(System.getProperty("java.io.tmpdir"));
        Path extractDir = tempBase.resolve("dsf-linter-" + cleanName);

        // Delete if exists, then recreate
        if (Files.exists(extractDir)) {
            deleteDirectoryRecursively(extractDir);
        }

        Files.createDirectories(extractDir);

        try {
            // Step 1: Copy JAR to extraction directory
            Path jarCopyPath = extractDir.resolve(jarName);
            Files.copy(jarFile, jarCopyPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Copied JAR to extraction directory: " + jarCopyPath.getFileName());

            // Step 2: Extract JAR contents
            extractJarContents(jarFile, extractDir);

            logger.info("JAR processing complete: " + extractDir.toAbsolutePath());

        } catch (IOException e) {
            // Cleanup on failure
            deleteDirectoryRecursively(extractDir);
            throw e;
        }

        return new JarProcessingResult(extractDir, jarName, true);
    }

    /**
     * Extracts JAR contents to the specified directory.
     * <p>
     * This method extracts all files from the JAR while preserving the directory structure.
     * The JAR file itself should already be copied to the extraction directory before calling this.
     * </p>
     *
     * @param jarFile the JAR file to extract
     * @param extractDir the target directory for extraction
     * @throws IOException if extraction fails
     */
    private void extractJarContents(Path jarFile, Path extractDir) throws IOException {
        try (JarFile jar = new JarFile(jarFile.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            int extractedCount = 0;

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                Path entryPath = extractDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());

                    try (InputStream in = jar.getInputStream(entry)) {
                        Files.copy(in, entryPath, StandardCopyOption.REPLACE_EXISTING);
                        extractedCount++;
                    }
                }
            }

            logger.info("Extracted " + extractedCount + " files from JAR");
        }
    }

    /**
     * Downloads a JAR file from an HTTP/HTTPS URL to a temporary location.
     * <p>
     * Uses standard Java URLConnection with configurable timeout.
     * The downloaded file is placed in the system temporary directory
     * with a unique name to avoid conflicts.
     * </p>
     *
     * @param urlString the HTTP/HTTPS URL of the JAR file
     * @return Path to the downloaded JAR file
     * @throws IOException if download fails or connection times out
     */
    private Path downloadJar(String urlString) throws IOException {
        URL url = new URL(urlString);
        String jarName = extractJarNameFromUrl(urlString);

        Path tempJar = Files.createTempFile("dsf-linter-jar-", "-" + jarName);

        logger.debug("Downloading to temporary file: " + tempJar);

        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(DOWNLOAD_TIMEOUT_MS);
        connection.setReadTimeout(DOWNLOAD_TIMEOUT_MS);

        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(tempJar)) {

            byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            logger.info("Downloaded " + formatBytes(totalBytes));

        } catch (IOException e) {
            // Cleanup on failure
            Files.deleteIfExists(tempJar);
            throw new IOException("Failed to download JAR from " + urlString + ": " + e.getMessage(), e);
        }

        return tempJar;
    }

    /**
     * lints that a JAR file contains the required DSF plugin structure.
     * <p>
     * Checks performed:
     * <ul>
     *   <li>JAR file is readable and valid</li>
     *   <li>Contains at least one .class file</li>
     *   <li>Contains META-INF/services directory</li>
     *   <li>Contains ProcessPluginDefinition service registration</li>
     * </ul>
     * </p>
     *
     * @param jarFile the JAR file to lint
     * @throws IOException if JAR cannot be read
     * @throws IllegalStateException if required structure is missing
     */
    private void lintJarStructure(Path jarFile) throws IOException, IllegalStateException {
        logger.debug("Linting JAR structure: " + jarFile.getFileName());

        try (JarFile jar = new JarFile(jarFile.toFile())) {
            boolean hasClassFiles = false;
            boolean hasMetaInfServices = false;
            boolean hasPluginDefinition = false;

            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class")) {
                    hasClassFiles = true;
                }

                if (name.startsWith("META-INF/services/")) {
                    hasMetaInfServices = true;

                    if (name.contains("ProcessPluginDefinition")) {
                        hasPluginDefinition = true;
                        logger.debug("Found plugin definition: " + name);
                    }
                }
            }

            if (!hasClassFiles) {
                throw new IllegalStateException(
                        "Invalid JAR: No compiled classes found. " +
                                "This does not appear to be a compiled DSF plugin."
                );
            }

            if (!hasMetaInfServices || !hasPluginDefinition) {
                throw new IllegalStateException(
                        "Invalid JAR: Missing META-INF/services/ProcessPluginDefinition. " +
                                "This does not appear to be a valid DSF plugin. " +
                                "Ensure the JAR contains proper ServiceLoader registration."
                );
            }

            logger.debug("JAR structure linting passed");

        } catch (IOException e) {
            throw new IOException("Failed to read JAR file: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the JAR file name from a URL.
     *
     * @param url the URL string
     * @return the JAR file name
     */
    private String extractJarNameFromUrl(String url) {
        String path = url.substring(url.lastIndexOf('/') + 1);

        // Remove query parameters if present
        int queryIndex = path.indexOf('?');
        if (queryIndex > 0) {
            path = path.substring(0, queryIndex);
        }

        // Ensure .jar extension
        if (!path.endsWith(".jar")) {
            path += ".jar";
        }

        return path;
    }

    /**
     * Deletes a directory and all its contents recursively.
     * <p>
     * Used for cleanup of temporary extraction directories.
     * Failures are logged but do not throw exceptions to allow
     * cleanup attempts to continue.
     * </p>
     *
     * @param directory the directory to delete
     */
    public void deleteDirectoryRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            logger.debug("Could not delete: " + file.getAbsolutePath());
                        }
                    });

            logger.debug("Cleaned up temporary directory: " + directory);

        } catch (IOException e) {
            logger.warn("Failed to clean up temporary directory " + directory + ": " + e.getMessage());
        }
    }

    /**
     * Formats byte count as human-readable string.
     *
     * @param bytes the byte count
     * @return formatted string (e.g., "1.5 MB")
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}