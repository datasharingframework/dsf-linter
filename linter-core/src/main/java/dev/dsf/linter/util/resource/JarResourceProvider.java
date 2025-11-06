package dev.dsf.linter.util.resource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Generic JAR-backed provider for resources (BPMN or FHIR).
 * Supports caching and indexing for efficient resource lookup.
 *
 * @param <T> the type of resource entry (BpmnResourceEntry or FhirResourceEntry)
 */
public final class JarResourceProvider<T> implements ResourceProvider<T>, Closeable {

    private final File projectRoot;
    private final ResourceEntryFactory<T> entryFactory;
    private final Predicate<String> resourceFilter;
    private final String resourceTypeName;
    private final Map<String, JarFile> jarCache;
    private final Map<String, List<T>> indexCache;
    private boolean indexed;

    private JarResourceProvider(File projectRoot,
                                ResourceEntryFactory<T> entryFactory,
                                Predicate<String> resourceFilter,
                                String resourceTypeName) {
        if (projectRoot == null) {
            throw new IllegalArgumentException("Project root cannot be null");
        }

        this.projectRoot = projectRoot;
        this.entryFactory = entryFactory;
        this.resourceFilter = resourceFilter;
        this.resourceTypeName = resourceTypeName;
        this.jarCache = new ConcurrentHashMap<>();
        this.indexCache = new ConcurrentHashMap<>();
        this.indexed = false;
    }

    /**
     * Factory method for FHIR resources.
     */
    public static JarResourceProvider<FhirResourceEntry> forFhir(File projectRoot) {
        return new JarResourceProvider<>(
                projectRoot,
                FhirResourceEntry::fromPath,
                name -> {
                    String lower = name.toLowerCase();
                    return (lower.endsWith(".xml") || lower.endsWith(".json"))
                            && (lower.contains("/fhir/") || lower.startsWith("fhir/"));
                },
                "FHIR"
        );
    }

    @Override
    public Stream<T> listResources(String directory) {
        ensureIndexed();

        String normalizedDir = normalizeDirectory(directory);

        return indexCache.values().stream()
                .flatMap(List::stream)
                .filter(entry -> getPath(entry).startsWith(normalizedDir));
    }

    @Override
    public InputStream openResource(String path) throws IOException {
        ensureIndexed();

        String normalizedPath = path.replace('\\', '/');

        for (JarFile jarFile : jarCache.values()) {
            JarEntry entry = jarFile.getJarEntry(normalizedPath);
            if (entry != null) {
                return jarFile.getInputStream(entry);
            }
        }

        throw new IOException("Resource not found in any JAR: " + path);
    }

    @Override
    public boolean resourceExists(String path) {
        ensureIndexed();

        String normalizedPath = path.replace('\\', '/');

        return jarCache.values().stream()
                .anyMatch(jarFile -> jarFile.getJarEntry(normalizedPath) != null);
    }

    @Override
    public String getDescription() {
        return String.format("JAR[%s: %s, %d JARs]",
                resourceTypeName,
                projectRoot.getAbsolutePath(),
                jarCache.size());
    }

    @Override
    public void close() throws IOException {
        for (JarFile jarFile : jarCache.values()) {
            try {
                jarFile.close();
            } catch (IOException e) {
                // Best effort
            }
        }
        jarCache.clear();
        indexCache.clear();
        indexed = false;
    }

    private synchronized void ensureIndexed() {
        if (indexed) {
            return;
        }

        indexJars();
        indexed = true;
    }

    private void indexJars() {
        indexJarsInDirectory(projectRoot, false);

        File targetDeps = new File(projectRoot, "target/dependency");
        if (targetDeps.exists() && targetDeps.isDirectory()) {
            indexJarsInDirectory(targetDeps, true);
        }

        File targetDependencies = new File(projectRoot, "target/dependencies");
        if (targetDependencies.exists() && targetDependencies.isDirectory()) {
            indexJarsInDirectory(targetDependencies, true);
        }
    }

    private void indexJarsInDirectory(File directory, boolean recursive) {
        if (!directory.isDirectory()) {
            return;
        }

        try (Stream<Path> paths = recursive
                ? Files.walk(directory.toPath())
                : Files.list(directory.toPath())) {

            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .forEach(this::indexJarFile);

        } catch (IOException e) {
            // Silently skip
        }
    }

    private void indexJarFile(Path jarPath) {
        try {
            JarFile jarFile = new JarFile(jarPath.toFile());
            String jarKey = jarPath.toString();

            jarCache.put(jarKey, jarFile);

            List<T> entries = jarFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> resourceFilter.test(entry.getName()))
                    .map(entry -> entryFactory.create(entry.getName()))
                    .toList();

            indexCache.put(jarKey, entries);

        } catch (IOException e) {
            // Silently skip
        }
    }

    private String normalizeDirectory(String directory) {
        if (directory == null) {
            return "";
        }

        String normalized = directory.replace('\\', '/');

        if (!normalized.endsWith("/")) {
            normalized += "/";
        }

        return normalized;
    }

    private String getPath(T entry) {
        if (entry instanceof FhirResourceEntry fhir) {
            return fhir.path();
        }
        throw new IllegalStateException("Unknown entry type: " + entry.getClass());
    }
}