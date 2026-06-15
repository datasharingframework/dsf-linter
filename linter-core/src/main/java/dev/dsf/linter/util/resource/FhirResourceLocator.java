package dev.dsf.linter.util.resource;

import org.w3c.dom.Document;

import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Locates and queries FHIR resources within a project structure.
 * <p>
 * This class provides methods to search for specific FHIR resources such as:
 * <ul>
 *   <li>ActivityDefinitions by message name or canonical URL</li>
 *   <li>StructureDefinitions by profile value</li>
 *   <li>Questionnaires by form key/URL</li>
 * </ul>
 * </p>
 * <p>
 * The locator supports both file system and JAR-based resources through a
 * {@link ResourceProvider} abstraction. It automatically detects the appropriate
 * provider based on the project structure.
 * </p>
 * <p>
 * Resources are searched in standard FHIR directory structures for extracted JARs.
 * </p>
 *
 * @see ResourceProvider
 * @see FhirResourceEntry
 */
public final class FhirResourceLocator {

    private static final String ACTIVITY_DEFINITION_DIR = "fhir/ActivityDefinition";
    private static final String STRUCTURE_DEFINITION_DIR = "fhir/StructureDefinition";
    private static final String QUESTIONNAIRE_DIR = "fhir/Questionnaire";

    private final ResourceProvider<FhirResourceEntry> provider;
    private final FhirResourceExtractor extractor;

    private FhirResourceLocator(ResourceProvider<FhirResourceEntry> provider) {
        this.provider = provider;
        this.extractor = new FhirResourceExtractor();
    }

    /**
     * Creates a FhirResourceLocator with automatic provider detection.
     * <p>
     * Automatically determines whether to use a composite provider (file system + JAR)
     * or just file system provider based on the project structure and presence of
     * JAR files.
     * </p>
     *
     * @param projectRoot the root directory of the extracted JAR project
     * @return a new FhirResourceLocator instance configured for the project structure
     */
    public static FhirResourceLocator create(File projectRoot) {
        ResourceProvider<FhirResourceEntry> provider;

        if (hasJarResources(projectRoot)) {
            provider = new CompositeResourceProvider<>(
                    "FHIR",
                    FileSystemResourceProvider.forFhir(projectRoot),
                    JarResourceProvider.forFhir(projectRoot)
            );
        } else {
            provider = FileSystemResourceProvider.forFhir(projectRoot);
        }

        return new FhirResourceLocator(provider);
    }

    /**
     * Factory method for creating a locator with a custom provider.
     * <p>
     * This allows using a custom resource provider implementation instead of
     * the automatic detection mechanism.
     * </p>
     *
     * @param provider the custom resource provider to use
     * @return a new FhirResourceLocator instance using the specified provider
     */
    public static FhirResourceLocator from(ResourceProvider<FhirResourceEntry> provider) {
        return new FhirResourceLocator(provider);
    }

    /**
     * Checks if an ActivityDefinition exists for the given message name.
     *
     * @param messageName the message name to search for
     * @param projectRoot the project root directory (currently unused, kept for API compatibility)
     * @return true if an ActivityDefinition with the specified message name exists
     */
    public boolean activityDefinitionExists(String messageName, File projectRoot) {
        return searchInDirectories(
                entry -> checkActivityDefinitionForMessage(entry, messageName),
                ACTIVITY_DEFINITION_DIR
        );
    }

    /**
     * Checks if a StructureDefinition exists for the given profile value.
     * <p>
     * Automatically removes version suffixes from the profile value before searching.
     * </p>
     *
     * @param profileValue the profile value/URL to search for
     * @param projectRoot the project root directory (currently unused, kept for API compatibility)
     * @return true if a StructureDefinition with the specified profile value exists
     */
    public boolean structureDefinitionExists(String profileValue, File projectRoot) {
        String base = ResourcePathNormalizer.removeVersionSuffix(profileValue);
        return searchInDirectories(
                entry -> checkStructureDefinitionForValue(entry, base),
                STRUCTURE_DEFINITION_DIR
        );
    }

    /**
     * Checks if an ActivityDefinition exists for the given instantiates canonical URL.
     * <p>
     * Automatically removes version suffixes from the canonical URL before searching.
     * </p>
     *
     * @param canonical the canonical URL to search for
     * @param projectRoot the project root directory (currently unused, kept for API compatibility)
     * @return true if an ActivityDefinition with the specified canonical URL exists
     */
    public boolean activityDefinitionExistsForInstantiatesCanonical(String canonical, File projectRoot) {
        String base = ResourcePathNormalizer.removeVersionSuffix(canonical);
        return findActivityDefinitionFile(base, projectRoot) != null;
    }

    /**
     * Finds the file containing the StructureDefinition for the given profile value.
     * <p>
     * Automatically removes version suffixes from the profile value before searching.
     * Resources from JAR files are materialized to temporary files.
     * </p>
     *
     * @param profileValue the profile value/URL to search for
     * @param projectRoot the project root directory (currently unused, kept for API compatibility)
     * @return the File containing the StructureDefinition, or null if not found
     */
    public File findStructureDefinitionFile(String profileValue, File projectRoot) {
        String base = ResourcePathNormalizer.removeVersionSuffix(profileValue);
        return findFileInDirectories(
                entry -> checkStructureDefinitionForValue(entry, base),
                STRUCTURE_DEFINITION_DIR
        );
    }

    /**
     * Finds the file containing the ActivityDefinition for the given instantiates canonical URL.
     * <p>
     * Automatically removes version suffixes from the canonical URL before searching.
     * Resources from JAR files are materialized to temporary files.
     * </p>
     *
     * @param canonical the canonical URL to search for
     * @param projectRoot the project root directory (currently unused, kept for API compatibility)
     * @return the File containing the ActivityDefinition, or null if not found
     */
    public File findActivityDefinitionForInstantiatesCanonical(String canonical, File projectRoot) {
        String baseCanon = ResourcePathNormalizer.removeVersionSuffix(canonical);
        return findFileInDirectories(
                entry -> checkActivityDefinitionForInstantiatesCanonical(entry, baseCanon),
                ACTIVITY_DEFINITION_DIR
        );
    }

    /**
     * Checks if a Questionnaire exists for the given form key.
     * <p>
     * Automatically extracts the base URL from the form key by removing version suffixes
     * (text after pipe character).
     * </p>
     *
     * @param formKey the form key/URL to search for
     * @param projectRoot the project root directory (currently unused, kept for API compatibility)
     * @return true if a Questionnaire with the specified form key exists, false if formKey is null or blank
     */
    public boolean questionnaireExists(String formKey, File projectRoot) {
        if (formKey == null || formKey.isBlank()) {
            return false;
        }

        String baseKey = formKey.split("\\|")[0].trim();
        return searchInDirectories(
                entry -> checkQuestionnaireForUrl(entry, baseKey),
                QUESTIONNAIRE_DIR
        );
    }

    /**
     * Checks if any ActivityDefinition has the specified message name.
     *
     * @param message the message name to search for
     * @param projectRoot the project root directory (currently unused, kept for API compatibility)
     * @return true if an ActivityDefinition with the specified message name exists
     */
    public boolean activityDefinitionHasMessageName(String message, File projectRoot) {
        return searchInDirectories(
                entry -> checkActivityDefinitionForMessage(entry, message),
                ACTIVITY_DEFINITION_DIR
        );
    }

    /**
     * Searches for a resource matching the predicate in the specified directories.
     *
     * @param predicate the predicate to match resources
     * @param directories the directories to search in
     * @return true if a matching resource is found
     */
    private boolean searchInDirectories(Predicate<FhirResourceEntry> predicate, String... directories) {
        for (String directory : directories) {
            boolean found = provider.listResources(directory)
                    .anyMatch(predicate);
            if (found) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds and returns a file matching the predicate in the specified directories.
     *
     * @param predicate the predicate to match resources
     * @param directories the directories to search in
     * @return the File containing the matching resource, or null if not found
     */
    private File findFileInDirectories(Predicate<FhirResourceEntry> predicate, String... directories) {
        for (String directory : directories) {
            Optional<File> file = provider.listResources(directory)
                    .filter(predicate)
                    .findFirst()
                    .flatMap(this::materializeToFile);

            if (file.isPresent()) {
                return file.get();
            }
        }
        return null;
    }

    /**
     * Materializes a resource entry to a temporary file.
     * <p>
     * If the resource exists in a JAR, it is extracted to a temporary file.
     * The temporary file is marked for deletion on JVM exit.
     * </p>
     *
     * @param entry the resource entry to materialize
     * @return an Optional containing the File, or empty if materialization fails
     */
    private Optional<File> materializeToFile(FhirResourceEntry entry) {
        try {
            if (!provider.resourceExists(entry.path())) {
                return Optional.empty();
            }

            Path tempFile = Files.createTempFile("fhir-resource-", "-" + entry.fileName());
            tempFile.toFile().deleteOnExit();

            try (InputStream in = provider.openResource(entry.path());
                 OutputStream out = Files.newOutputStream(tempFile)) {
                in.transferTo(out);
            }

            return Optional.of(tempFile.toFile());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private boolean checkActivityDefinitionForMessage(FhirResourceEntry entry, String messageName) {
        return checkFhirResource(entry, "ActivityDefinition",
                doc -> {
                    try {
                        return extractor.activityDefinitionContainsMessageName(doc, messageName);
                    } catch (XPathExpressionException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private boolean checkStructureDefinitionForValue(FhirResourceEntry entry, String value) {
        return checkFhirResource(entry, "StructureDefinition",
                doc -> {
                    try {
                        return extractor.structureDefinitionContainsValue(doc, value);
                    } catch (XPathExpressionException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private boolean checkActivityDefinitionForInstantiatesCanonical(FhirResourceEntry entry, String canonical) {
        return checkFhirResource(entry, "ActivityDefinition",
                doc -> {
                    try {
                        return extractor.activityDefinitionContainsInstantiatesCanonical(doc, canonical);
                    } catch (XPathExpressionException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private boolean checkQuestionnaireForUrl(FhirResourceEntry entry, String url) {
        return checkFhirResource(entry, "Questionnaire",
                doc -> {
                    try {
                        return extractor.questionnaireContainsUrl(doc, url);
                    } catch (XPathExpressionException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private boolean checkFhirResource(FhirResourceEntry entry,
                                      String expectedRootName,
                                      Predicate<Document> checkLogic) {
        try (InputStream in = provider.openResource(entry.path())) {
            Document doc = FhirResourceParser.parseFhirResource(in, entry.fileName());
            if (doc == null) {
                return false;
            }

            String rootName = doc.getDocumentElement().getLocalName();
            if (!expectedRootName.equals(rootName)) {
                return false;
            }

            return checkLogic.test(doc);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasJarResources(File projectRoot) {
        if (projectRoot == null) {
            return false;
        }

        File[] jars = projectRoot.listFiles((dir, name) -> name.endsWith(".jar"));
        return jars != null && jars.length > 0;
    }

    private File findActivityDefinitionFile(String canonical, File projectRoot) {
        String base = ResourcePathNormalizer.removeVersionSuffix(canonical);
        return findFileInDirectories(
                entry -> checkActivityDefinitionForInstantiatesCanonical(entry, base),
                ACTIVITY_DEFINITION_DIR
        );
    }
}