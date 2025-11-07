package dev.dsf.linter.util.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A file system-based implementation of {@link ResourceProvider}.
 * <p>
 * This provider reads resources directly from a specified root directory on the
 * local file system. It walks the directory tree to discover resources and uses
 * a factory pattern to create typed resource entries.
 * </p>
 * <p>
 * The provider validates that the root directory exists and is accessible during
 * construction. All resource paths are relative to this root directory.
 * </p>
 *
 * @param <T> the type of resource entries created by the entry factory
 * @param root the root directory containing the resources
 * @param entryFactory factory for creating resource entries from paths
 * @param resourceTypeName human-readable name describing the resource type
 */
public record FileSystemResourceProvider<T>(File root, ResourceEntryFactory<T> entryFactory,
                                            String resourceTypeName) implements ResourceProvider<T> {

    public FileSystemResourceProvider {
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(entryFactory, "entryFactory cannot be null");
        Objects.requireNonNull(resourceTypeName, "resourceTypeName cannot be null");

        if (!root.isDirectory()) {
            throw new IllegalArgumentException("Root must be a valid directory: " + root);
        }

    }


    /**
     * Factory method for FHIR resources.
     */
    public static FileSystemResourceProvider<FhirResourceEntry> forFhir(File root) {
        return new FileSystemResourceProvider<>(
                root,
                FhirResourceEntry::fromPath,
                "FHIR"
        );
    }

    @Override
    public Stream<T> listResources(String directory) {
        Path base = root.toPath().resolve(directory == null ? "" : directory);

        List<T> entries;
        try (Stream<Path> paths = Files.walk(base)) {
            entries = paths
                    .filter(Files::isRegularFile)
                    .map(p -> {
                        String relativePath = root.toPath().relativize(p)
                                .toString()
                                .replace(File.separatorChar, '/');
                        return entryFactory.create(relativePath);
                    })
                    .toList();
        } catch (IOException e) {
            return Stream.empty();
        }

        return entries.stream();
    }

    @Override
    public InputStream openResource(String path) throws IOException {
        File file = new File(root, path);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("Resource not found: " + path);
        }
        return Files.newInputStream(file.toPath());
    }

    @Override
    public boolean resourceExists(String path) {
        File file = new File(root, path);
        return file.exists() && file.isFile();
    }

    @Override
    public String getDescription() {
        return String.format("FileSystem[%s: %s]",
                resourceTypeName,
                root.getAbsolutePath());
    }
}