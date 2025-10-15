package dev.dsf.linter.util.resource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.w3c.dom.Document;

/**
 * Primary class for locating and finding FHIR resource files within DSF projects.
 * Supports both Maven-style and flat directory layouts.
 *
 * @see FhirResourceParser
 * @see FhirResourceExtractor
 */
public class FhirResourceLocator {

    private static final String ACTIVITY_DEFINITION_DIR = "src/main/resources/fhir/ActivityDefinition";
    private static final String STRUCTURE_DEFINITION_DIR = "src/main/resources/fhir/StructureDefinition";
    private static final String QUESTIONNAIRE_DIR = "src/main/resources/fhir/Questionnaire";
    private static final String STRUCTURE_DEFINITION_DIR_FLAT = "fhir/StructureDefinition";
    private static final String ACTIVITY_DEFINITION_DIR_FLAT = "fhir/ActivityDefinition";
    private static final String QUESTIONNAIRE_DIR_FLAT = "fhir/Questionnaire";

    private static final FhirResourceExtractor extractor = new FhirResourceExtractor();

    /**
     * Checks if an ActivityDefinition resource matching the given message name exists.
     *
     * @param messageName the message name to search for
     * @param projectRoot the root directory of the project containing FHIR resources
     * @return {@code true} if an ActivityDefinition with the specified message name is found
     */
    public static boolean activityDefinitionExists(String messageName, File projectRoot) {
        return isDefinitionByContent(messageName, projectRoot,
                ACTIVITY_DEFINITION_DIR, true)
                || isDefinitionByContent(messageName, projectRoot,
                ACTIVITY_DEFINITION_DIR_FLAT, true);
    }

    /**
     * Checks whether a StructureDefinition resource with the given canonical profile URL exists.
     *
     * @param profileValue the canonical URL of the StructureDefinition
     * @param projectRoot the project root folder containing FHIR resources
     * @return {@code true} if a matching StructureDefinition exists
     */
    public static boolean structureDefinitionExists(String profileValue, File projectRoot) {
        String base = removeVersionSuffix(profileValue);

        return isDefinitionByContent(base, projectRoot,
                STRUCTURE_DEFINITION_DIR, false)
                || isDefinitionByContent(base, projectRoot,
                STRUCTURE_DEFINITION_DIR_FLAT, false);
    }

    /**
     * Checks whether an ActivityDefinition resource exists with a matching url
     * that corresponds to the supplied instantiatesCanonical value.
     *
     * @param canonical the canonical URL string used in Task.instantiatesCanonical
     * @param projectRoot the project root containing FHIR resources
     * @return {@code true} if a corresponding ActivityDefinition is found
     */
    public static boolean activityDefinitionExistsForInstantiatesCanonical(String canonical,
                                                                           File projectRoot) {
        String base = removeVersionSuffix(canonical);
        return findActivityDefinitionFile(base, projectRoot) != null;
    }

    /**
     * Attempts to find a StructureDefinition XML file that matches the provided canonical URL (with version stripped).
     * <p>
     * The search is performed in both Maven-structured and flat layouts under the project root.
     * </p>
     *
     * @param profileValue the profile canonical (e.g. "http://example.org/StructureDefinition/task-xyz|1.0")
     * @param projectRoot  the project root containing FHIR resources
     * @return the {@code File} of the first matching StructureDefinition, or {@code null} if none is found
     */
    public static File findStructureDefinitionFile(String profileValue, File projectRoot) {
        String base = removeVersionSuffix(profileValue);

        File file = tryFindStructureFile(base, projectRoot, STRUCTURE_DEFINITION_DIR);
        if (file != null) return file;

        return tryFindStructureFile(base, projectRoot, STRUCTURE_DEFINITION_DIR_FLAT);
    }

    /**
     * Attempts to locate a FHIR {@code ActivityDefinition} XML file whose {@code <url>} value
     * matches the provided {@code instantiatesCanonical} reference (with version suffix removed).
     *
     * <p>This method supports both layout styles:</p>
     * <ul>
     *   <li><strong>Maven-style layout</strong>: {@code src/main/resources/fhir/ActivityDefinition}</li>
     *   <li><strong>Flat layout</strong>: {@code fhir/ActivityDefinition}</li>
     * </ul>
     *
     * <p>Only the first matching file is returned. If no match is found in either layout,
     * this method returns {@code null}.</p>
     *
     * <p>Example: Given {@code instantiatesCanonical = "http://example.org/Process/xyz|1.0"}, the method will
     * look for an ActivityDefinition with:</p>
     * <pre>{@code
     *   <ActivityDefinition>
     *     <url value="http://example.org/Process/xyz"/>
     *   </ActivityDefinition>
     * }</pre>
     *
     * @param canonical the {@code instantiatesCanonical} reference from the Task (may include version)
     * @param projectRoot the root directory of the FHIR project (as determined by {@code determineProjectRoot})
     * @return the first matching {@code ActivityDefinition} file, or {@code null} if none found
     */
    public static File findActivityDefinitionForInstantiatesCanonical(String canonical,
                                                                      File projectRoot) {
        String baseCanon = removeVersionSuffix(canonical);

        File file = tryFindActivityFile(baseCanon, projectRoot, ACTIVITY_DEFINITION_DIR);
        if (file != null) return file;

        return tryFindActivityFile(baseCanon, projectRoot, ACTIVITY_DEFINITION_DIR_FLAT);
    }

    /**
     * Checks whether a Questionnaire resource exists that matches the given formKey (ignoring version suffix).
     * <p>
     * This method searches both Maven-style and flat folder layouts for XML Questionnaire resources with a matching URL.
     * </p>
     *
     * @param formKey      the form key used in user tasks, possibly including a "|version" suffix
     * @param projectRoot  the project root containing FHIR resources
     * @return {@code true} if a matching Questionnaire is found; {@code false} otherwise
     */
    public static boolean questionnaireExists(String formKey, File projectRoot) {
        if (formKey == null || formKey.isBlank()) {
            return false;
        }

        String baseKey = formKey.split("\\|")[0].trim();

        return questionnaireExistsInDir(baseKey, projectRoot, QUESTIONNAIRE_DIR)
                || questionnaireExistsInDir(baseKey, projectRoot, QUESTIONNAIRE_DIR_FLAT);
    }

    /**
     * Checks whether any FHIR ActivityDefinition resource contains the specified message name.
     *
     * @param message the message name to search for
     * @param projectRoot the root directory of the project
     * @return {@code true} if at least one ActivityDefinition contains the given message name
     */
    public static boolean activityDefinitionHasMessageName(String message, File projectRoot) {
        return searchForMessageNameInDir(message, projectRoot, ACTIVITY_DEFINITION_DIR)
                || searchForMessageNameInDir(message, projectRoot, ACTIVITY_DEFINITION_DIR_FLAT);
    }

    // Private helper methods

    /**
     * A helper method that checks either ActivityDefinition or StructureDefinition files
     * to see if they contain the specified value.
     * <p>
     * This method traverses all .xml files in the given {@code definitionDir} (under {@code projectRoot}).
     * If it is checking for an ActivityDefinition, it will look for the presence of the specified value
     * in an ActivityDefinition context; otherwise, it looks in a StructureDefinition context.
     * </p>
     *
     * @param value               the value to search for (e.g., messageName, profileValue)
     * @param projectRoot         the project root directory
     * @param definitionDir       relative directory path (ActivityDefinition or StructureDefinition)
     * @param isActivityDefinition indicates whether we are searching for ActivityDefinition files
     * @return {@code true} if a file is found containing the specified value; {@code false} otherwise
     */
    private static boolean isDefinitionByContent(String value, File projectRoot,
                                                 String definitionDir, boolean isActivityDefinition) {
        File dir = new File(projectRoot, definitionDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(FhirFileUtils::isFhirFile)
                    .anyMatch(p -> fileContainsValue(p, value,
                            isActivityDefinition ? FhirDefinitionType.ACTIVITY_DEFINITION
                                    : FhirDefinitionType.STRUCTURE_DEFINITION));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Determines if a particular FHIR file (either an ActivityDefinition or a StructureDefinition)
     * contains the specified value.
     *
     * @param filePath       the path to the FHIR file (XML or JSON)
     * @param value          the string value to look for
     * @param type the FHIR resource type selector
     * @return {@code true} if the file contains the given value for the specified resource type, {@code false} otherwise
     */
    private static boolean fileContainsValue(Path filePath, String value, FhirDefinitionType type) {
        try {
            Document doc = FhirResourceParser.parseFhirFile(filePath);
            if (doc == null) return false;

            String rootName = doc.getDocumentElement().getLocalName();

            return switch (type) {
                case ACTIVITY_DEFINITION -> {
                    if (!"ActivityDefinition".equals(rootName)) yield false;
                    yield extractor.activityDefinitionContainsMessageName(doc, value);
                }
                case STRUCTURE_DEFINITION -> {
                    if (!"StructureDefinition".equals(rootName)) yield false;
                    yield extractor.structureDefinitionContainsValue(doc, value);
                }
            };
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Finds an ActivityDefinition file whose {@code url} element matches the given canonical value (version removed).
     * <p>
     * Searches both Maven-style and flat directory layouts.
     * </p>
     *
     * @param canonical    the instantiatesCanonical value to look for
     * @param projectRoot  the root project directory
     * @return the first matching ActivityDefinition {@code File}, or {@code null} if not found
     */
    private static File findActivityDefinitionFile(String canonical, File projectRoot) {
        String base = removeVersionSuffix(canonical);

        File file = tryFindActivityFile(base, projectRoot, ACTIVITY_DEFINITION_DIR);
        if (file != null) return file;

        return tryFindActivityFile(base, projectRoot, ACTIVITY_DEFINITION_DIR_FLAT);
    }

    /**
     * Searches a specific subdirectory for an ActivityDefinition file matching the given canonical value.
     *
     * @param canonical    the canonical URL to match (without version suffix)
     * @param projectRoot  the root folder of the project
     * @param relDir       the relative directory path under {@code projectRoot} to scan
     * @return the first file that matches, or {@code null} if no match is found
     */
    private static File tryFindActivityFile(String canonical, File projectRoot, String relDir) {
        File dir = new File(projectRoot, relDir);
        if (!dir.isDirectory()) return null;

        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(FhirFileUtils::isFhirFile)
                    .filter(p -> fileContainsInstantiatesCanonical(p, canonical))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Verifies if the given XML file contains an ActivityDefinition with a {@code url} element
     * matching the supplied canonical value (with version suffix removed).
     *
     * @param filePath       the path to the XML file
     * @param canonicalValue the canonical URL to match
     * @return {@code true} if the canonical URL is found; {@code false} otherwise
     */
    private static boolean fileContainsInstantiatesCanonical(Path filePath, String canonicalValue) {
        try {
            Document doc = FhirResourceParser.parseFhirFile(filePath);
            if (doc == null) return false;

            String rootName = doc.getDocumentElement().getLocalName();
            if (!"ActivityDefinition".equals(rootName)) return false;

            return extractor.activityDefinitionContainsInstantiatesCanonical(doc, canonicalValue);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Attempts to locate a StructureDefinition file with a matching canonical URL inside the given directory.
     *
     * @param profileValue the canonical profile URL to match
     * @param projectRoot  the root directory containing FHIR resources
     * @param relDir       the relative subdirectory under {@code projectRoot} to search
     * @return the matching {@code File}, or {@code null} if none is found
     */
    private static File tryFindStructureFile(String profileValue, File projectRoot, String relDir) {
        File dir = new File(projectRoot, relDir);
        if (!dir.isDirectory()) return null;

        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(FhirFileUtils::isFhirFile)
                    .filter(p -> fileContainsValue(p, profileValue,
                            FhirDefinitionType.STRUCTURE_DEFINITION))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Scans a specific subdirectory under the given {@code projectRoot} for Questionnaire XML files
     * and checks whether any of them declare a {@code <url>} element matching the provided {@code baseKey}.
     *
     * <p>This method supports both Maven-style and flat folder layouts. It validates each file by:
     * <ol>
     *   <li>Parsing the XML document</li>
     *   <li>Verifying that the root element is {@code Questionnaire}</li>
     *   <li>Checking if a {@code <url>} element has a {@code value} equal to {@code baseKey}</li>
     * </ol>
     * </p>
     *
     * @param baseKey     the form key to search for (must not include a version suffix)
     * @param projectRoot the root directory of the project containing FHIR resources
     * @param relDir      the relative subdirectory path (e.g., {@code fhir/Questionnaire} or {@code src/main/resources/fhir/Questionnaire})
     * @return {@code true} if a matching Questionnaire is found; {@code false} otherwise
     */
    private static boolean questionnaireExistsInDir(String baseKey, File projectRoot, String relDir) {
        File dir = new File(projectRoot, relDir);
        if (!dir.isDirectory()) return false;

        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(FhirFileUtils::isFhirFile)
                    .anyMatch(p -> {
                        try {
                            Document doc = FhirResourceParser.parseFhirFile(p);
                            return doc != null
                                    && "Questionnaire".equals(doc.getDocumentElement().getLocalName())
                                    && extractor.questionnaireContainsUrl(doc, baseKey);
                        } catch (Exception e) {
                            return false;
                        }
                    });
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean searchForMessageNameInDir(String message, File projectRoot, String relDir) {
        File dir = new File(projectRoot, relDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(FhirFileUtils::isFhirFile)
                    .anyMatch(p -> {
                        try {
                            Document doc = FhirResourceParser.parseFhirFile(p);
                            if (doc == null) return false;

                            if (!"ActivityDefinition".equals(doc.getDocumentElement().getLocalName())) {
                                return false;
                            }

                            return extractor.activityDefinitionContainsMessageName(doc, message);
                        } catch (Exception e) {
                            return false;
                        }
                    });
        } catch (Exception e) {
            return false;
        }
    }

    private static String removeVersionSuffix(String value) {
        if (value == null) return null;
        int pipeIndex = value.indexOf("|");
        return (pipeIndex != -1) ? value.substring(0, pipeIndex) : value;
    }
}