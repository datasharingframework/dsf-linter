package dev.dsf.linter.util.resource;

import dev.dsf.linter.util.cache.ConcurrentCache;

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
 * Uses ConcurrentCache for thread-safe caching with cleanup callbacks.
 *
 * @param <T> the type of resource entry (BpmnResourceEntry or FhirResourceEntry)
 * @since 1.2.0
 */
public final class JarResourceProvider<T> implements ResourceProvider<T>, Closeable {

    private final File projectRoot;
    private final ResourceEntryFactory<T> entryFactory;
    private final Predicate<String> resourceFilter;
    private final String resourceTypeName;
    private final ConcurrentCache<String, JarFile> jarCache;
    private final ConcurrentCache<String, List<T>> indexCache;
    private final Set<String> jarKeys; // Track JAR keys separately since ConcurrentCache doesn't expose them
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

        // Use ConcurrentCache with cleanup callback
        this.jarCache = new ConcurrentCache<>(jarFile -> {
            try {
                jarFile.close();
            } catch (IOException e) {
                // Best effort cleanup
            }
        });

        this.indexCache = new ConcurrentCache<>();
        this.jarKeys = ConcurrentHashMap.newKeySet(); // Thread-safe set for JAR keys
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

        String normalizedDir = ResourcePathNormalizer.normalizeDirectory(directory);

        return indexCache.get(getCacheKey()).stream().flatMap(Collection::stream)
                .filter(entry -> getPath(entry).startsWith(normalizedDir));
    }

    @Override
    public InputStream openResource(String path) throws IOException {
        ensureIndexed();

        String normalizedPath = path.replace('\\', '/');

        for (String jarKey : getJarKeys()) {
            Optional<JarFile> jarFileOpt = jarCache.get(jarKey);
            if (jarFileOpt.isPresent()) {
                JarFile jarFile = jarFileOpt.get();
                JarEntry entry = jarFile.getJarEntry(normalizedPath);
                if (entry != null) {
                    return jarFile.getInputStream(entry);
                }
            }
        }

        throw new IOException("Resource not found in any JAR: " + path);
    }

    @Override
    public boolean resourceExists(String path) {
        ensureIndexed();

        String normalizedPath = path.replace('\\', '/');

        for (String jarKey : getJarKeys()) {
            Optional<JarFile> jarFileOpt = jarCache.get(jarKey);
            if (jarFileOpt.isPresent()) {
                JarFile jarFile = jarFileOpt.get();
                if (jarFile.getJarEntry(normalizedPath) != null) {
                    return true;
                }
            }
        }

        return false;
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
        jarCache.clear(); // Cleanup callback closes JARs
        indexCache.clear();
        jarKeys.clear();
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
        String jarKey = jarPath.toString();

        try {
            JarFile jarFile = new JarFile(jarPath.toFile());
            jarCache.put(jarKey, jarFile);
            jarKeys.add(jarKey); // Track the JAR key

            List<T> entries = jarFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> resourceFilter.test(entry.getName()))
                    .map(entry -> entryFactory.create(entry.getName()))
                    .toList();

            // Store all entries under a single cache key
            String cacheKey = getCacheKey();
            Optional<List<T>> existingEntriesOpt = indexCache.get(cacheKey);
            if (existingEntriesOpt.isEmpty()) {
                indexCache.put(cacheKey, new ArrayList<>(entries));
            } else {
                existingEntriesOpt.get().addAll(entries);
            }

        } catch (IOException e) {
            // Silently skip
        }
    }

    private String getCacheKey() {
        return projectRoot.getAbsolutePath() + "::all";
    }

    private Set<String> getJarKeys() {
        return jarKeys; // Return tracked JAR keys
    }

    private String getPath(T entry) {
        if (entry instanceof FhirResourceEntry fhir) {
            return fhir.path();
        }
        throw new IllegalStateException("Unknown entry type: " + entry.getClass());
    }
}