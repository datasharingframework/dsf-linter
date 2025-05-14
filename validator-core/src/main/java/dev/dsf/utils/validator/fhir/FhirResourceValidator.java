package dev.dsf.utils.validator.fhir;

import dev.dsf.utils.validator.ValidationOutput;
import dev.dsf.utils.validator.item.FhirElementValidationItem;
import dev.dsf.utils.validator.util.AbstractFhirInstanceValidator;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Aggregates all concrete {@link AbstractFhirInstanceValidator} implementations and
 * delegates validation of FHIR XML files to the first matching validator.
 *
 * <p>Compared to the previous implementation the class has been streamlined:</p>
 * <ul>
 *   <li> Introduced a new, more flexible {@link #validateDirectory(Path)} method that
 *       walks a directory tree and returns a single aggregated {@link ValidationOutput}.</li>
 *   <li> Validators are now discovered lazily through {@link ServiceLoader} to avoid
 *       manual maintenance of the list in this class.</li>
 *   <li> Uses {@link java.nio.file.Files#walk} & Java Streams for concise IO logic.</li>
 * </ul>
 */
public final class FhirResourceValidator
{
    /** All discovered validators (cached). */
    private final List<AbstractFhirInstanceValidator> validators;

    public FhirResourceValidator()
    {
        // Discover validators via ServiceLoader OR fallback to manual list
        ServiceLoader<AbstractFhirInstanceValidator> loader =
                ServiceLoader.load(AbstractFhirInstanceValidator.class);
        validators = loader.stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toCollection(ArrayList::new));

        // Fallback in case ServiceLoader finds nothing (e.g., during tests)
        if (validators.isEmpty())
        {
            validators.add(
                    new FhirActivityDefinitionValidator()
            );
            validators.add(
                    new FhirTaskValidator()
            );
            validators.add(
                    new FhirValueSetValidator()
            );
            validators.add(
                    new FhirCodeSystemValidator()
            );
            validators.add(
                    new FhirQuestionnaireValidator()
            );
        }
    }

    /*
      Public API
       */

    /**
     * Validates every <strong>.xml</strong> file under the given directory (recursively).
     * Returns a {@link ValidationOutput} containing the aggregated issues of all resources.
     *
     * @param directory the root directory to scan (e.g. {@code src/main/resources/fhir})
     * @return aggregated validation output; {@link ValidationOutput#empty()} if no XML files found
     * @throws IOException if directory traversal fails
     */
    public ValidationOutput validateDirectory(Path directory) throws IOException
    {
        if (!Files.isDirectory(directory))
            throw new IllegalArgumentException("Not a directory: " + directory);

        try (Stream<Path> xmlFiles = Files.walk(directory).filter(this::isXmlFile))
        {
            List<FhirElementValidationItem> issues = xmlFiles
                    .map(Path::toFile)
                    .map(this::validateFileInternal)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            return new ValidationOutput(new ArrayList<>(issues));
        }
    }

    /**
     * Validates a single FHIR XML resource file and returns its validation output.
     */
    public ValidationOutput validateSingleFile(Path xmlFile)
    {
        List<FhirElementValidationItem> issues = validateFileInternal(xmlFile.toFile());
        return new ValidationOutput(new ArrayList<>(issues));
    }

    /*
      Internal helpers
      */

    private boolean isXmlFile(Path p)
    {
        return Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".xml");
    }

    private List<FhirElementValidationItem> validateFileInternal(File file)
    {
        List<FhirElementValidationItem> issues = new ArrayList<>();
        Document doc = parseXmlSafe(file);
        if (doc == null)
            return issues; // Parsing error already logged

        // Find first validator that supports this resource type
        for (AbstractFhirInstanceValidator v : validators)
        {
            if (v.canValidate(doc))
            {
                @SuppressWarnings("unchecked")
                List<FhirElementValidationItem> found = (List<FhirElementValidationItem>) v.validate(doc, file);
                issues.addAll(found);
                return issues;
            }
        }

        System.out.println("[DEBUG] No FHIR validator recognized file: " + file.getName());
        return issues;
    }

    private Document parseXmlSafe(File file)
    {
        try (InputStream in = Files.newInputStream(file.toPath()))
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(in);
        }
        catch (Exception e)
        {
            System.err.println("[WARN] Failed to parse XML " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
