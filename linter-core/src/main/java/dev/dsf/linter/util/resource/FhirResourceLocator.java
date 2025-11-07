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
 * Refactored FhirResourceLocator using generic providers.
 * Eliminates duplication and improves consistency.
 */
public final class FhirResourceLocator {

    private static final String ACTIVITY_DEFINITION_DIR = "src/main/resources/fhir/ActivityDefinition";
    private static final String STRUCTURE_DEFINITION_DIR = "src/main/resources/fhir/StructureDefinition";
    private static final String QUESTIONNAIRE_DIR = "src/main/resources/fhir/Questionnaire";
    private static final String ACTIVITY_DEFINITION_DIR_FLAT = "fhir/ActivityDefinition";
    private static final String STRUCTURE_DEFINITION_DIR_FLAT = "fhir/StructureDefinition";
    private static final String QUESTIONNAIRE_DIR_FLAT = "fhir/Questionnaire";

    private final ResourceProvider<FhirResourceEntry> provider;
    private final FhirResourceExtractor extractor;

    private FhirResourceLocator(ResourceProvider<FhirResourceEntry> provider) {
        this.provider = provider;
        this.extractor = new FhirResourceExtractor();
    }

    /**
     * Creates a FhirResourceLocator with automatic provider detection.
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
     * Factory for custom provider.
     */
    public static FhirResourceLocator from(ResourceProvider<FhirResourceEntry> provider) {
        return new FhirResourceLocator(provider);
    }

    public boolean activityDefinitionExists(String messageName, File projectRoot) {
        return searchInDirectories(
                entry -> checkActivityDefinitionForMessage(entry, messageName),
                ACTIVITY_DEFINITION_DIR,
                ACTIVITY_DEFINITION_DIR_FLAT
        );
    }

    public boolean structureDefinitionExists(String profileValue, File projectRoot) {
        String base = ResourcePathNormalizer.removeVersionSuffix(profileValue);
        return searchInDirectories(
                entry -> checkStructureDefinitionForValue(entry, base),
                STRUCTURE_DEFINITION_DIR,
                STRUCTURE_DEFINITION_DIR_FLAT
        );
    }

    public boolean activityDefinitionExistsForInstantiatesCanonical(String canonical, File projectRoot) {
        String base = ResourcePathNormalizer.removeVersionSuffix(canonical);
        return findActivityDefinitionFile(base, projectRoot) != null;
    }

    public File findStructureDefinitionFile(String profileValue, File projectRoot) {
        String base = ResourcePathNormalizer.removeVersionSuffix(profileValue);
        return findFileInDirectories(
                entry -> checkStructureDefinitionForValue(entry, base),
                STRUCTURE_DEFINITION_DIR,
                STRUCTURE_DEFINITION_DIR_FLAT
        );
    }

    public File findActivityDefinitionForInstantiatesCanonical(String canonical, File projectRoot) {
        String baseCanon = ResourcePathNormalizer.removeVersionSuffix(canonical);
        return findFileInDirectories(
                entry -> checkActivityDefinitionForInstantiatesCanonical(entry, baseCanon),
                ACTIVITY_DEFINITION_DIR,
                ACTIVITY_DEFINITION_DIR_FLAT
        );
    }

    public boolean questionnaireExists(String formKey, File projectRoot) {
        if (formKey == null || formKey.isBlank()) {
            return false;
        }

        String baseKey = formKey.split("\\|")[0].trim();
        return searchInDirectories(
                entry -> checkQuestionnaireForUrl(entry, baseKey),
                QUESTIONNAIRE_DIR,
                QUESTIONNAIRE_DIR_FLAT
        );
    }

    public boolean activityDefinitionHasMessageName(String message, File projectRoot) {
        return searchInDirectories(
                entry -> checkActivityDefinitionForMessage(entry, message),
                ACTIVITY_DEFINITION_DIR,
                ACTIVITY_DEFINITION_DIR_FLAT
        );
    }

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

        File fhirDir = new File(projectRoot, "fhir");
        File srcMainResources = new File(projectRoot, "src/main/resources");
        boolean hasExtractedResources = (fhirDir.exists() && fhirDir.isDirectory()) ||
                (srcMainResources.exists() && srcMainResources.isDirectory());

        // If resources are already extracted to filesystem, only use JarResourceProvider
        // for dependency JARs, not for the extracted main JAR
        if (hasExtractedResources) {
            File targetDeps = new File(projectRoot, "target/dependency");
            File targetDependencies = new File(projectRoot, "target/dependencies");

            // Only use JarResourceProvider for dependency JARs
            return (targetDeps.exists() && targetDeps.isDirectory())
                    || (targetDependencies.exists() && targetDependencies.isDirectory());
        }

        // No extracted resources: check for JAR files in root or dependency directories
        File[] jars = projectRoot.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars != null && jars.length > 0) {
            return true;
        }

        File targetDeps = new File(projectRoot, "target/dependency");
        File targetDependencies = new File(projectRoot, "target/dependencies");

        return (targetDeps.exists() && targetDeps.isDirectory())
                || (targetDependencies.exists() && targetDependencies.isDirectory());
    }

    private File findActivityDefinitionFile(String canonical, File projectRoot) {
        String base = ResourcePathNormalizer.removeVersionSuffix(canonical);
        return findFileInDirectories(
                entry -> checkActivityDefinitionForInstantiatesCanonical(entry, base),
                ACTIVITY_DEFINITION_DIR,
                ACTIVITY_DEFINITION_DIR_FLAT
        );
    }
}