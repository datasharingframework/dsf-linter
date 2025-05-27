package dev.dsf.utils.validator.util;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathConstants;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Utility class for validating and analyzing FHIR resource files such as {@code ActivityDefinition},
 * {@code StructureDefinition}, and {@code Questionnaire} within DSF projects.
 * <p>
 * This class supports both Maven-style and flat directory layouts for locating FHIR resource files.
 * It provides static methods for:
 * <ul>
 *   <li>Verifying the existence of ActivityDefinition and StructureDefinition resources by canonical reference or content.</li>
 *   <li>Checking for specific extensions or canonical URLs within resource definitions.</li>
 *   <li>Locating files that match Task-related metadata such as {@code instantiatesCanonical} and {@code message-name}.</li>
 *   <li>Evaluating authorization conditions for Task requesters and recipients based on DSF extensions.</li>
 * </ul>
 * </p>
 *
 * <h3>Directory support</h3>
 * <p>
 * The validator supports both:
 * <ul>
 *   <li>Maven-style structure: {@code src/main/resources/fhir/ResourceType/...}</li>
 *   <li>Flat layout: {@code fhir/ResourceType/...}</li>
 * </ul>
 * The search logic is designed to be resilient by falling back to either format when one is not found.
 * </p>
 *
 * <h3>XPath evaluation</h3>
 * <p>
 * Many methods use XPath expressions to extract values or validate FHIR structural elements.
 * This allows inspection of XML resources without depending on specific Java model bindings.
 * </p>
 *
 * <h3>Key responsibilities</h3>
 * <ul>
 *   <li>Canonical normalization (removal of version suffixes).</li>
 *   <li>Content-based validation of ActivityDefinition and StructureDefinition resources.</li>
 *   <li>Support for instantiatesCanonical and message-name extraction and validation.</li>
 *   <li>Authorization checking for requester and recipient based on FHIR extensions.</li>
 *   <li>Dynamic file search across multiple directory structures.</li>
 * </ul>
 *
 * <p>
 * References:
 * <ul>
 *   <li>HL7 FHIR Standard: <a href="https://www.hl7.org/fhir/">https://www.hl7.org/fhir/</a></li>
 *   <li>DSF Specification: <a href="https://github.com/datasharingframework/dsf">https://github.com/datasharingframework/dsf</a></li>
 * </ul>
 * </p>
 */
public class FhirValidator
{
    private static final String ACTIVITY_DEFINITION_DIR = "src/main/resources/fhir/ActivityDefinition";
    private static final String STRUCTURE_DEFINITION_DIR = "src/main/resources/fhir/StructureDefinition";
    private static final String QUESTIONNAIRE_DIR = "src/main/resources/fhir/Questionnaire";
    private static final String STRUCTURE_DEFINITION_DIR_FLAT = "fhir/StructureDefinition";
    private static final String ACTIVITY_DEFINITION_DIR_FLAT  = "fhir/ActivityDefinition";
    private static final String QUESTIONNAIRE_DIR_FLAT        = "fhir/Questionnaire";

    /**
     * Checks if an ActivityDefinition resource matching the given message name exists.
     * <p>
     * This method searches both Maven-style and flat directory layouts under the given {@code projectRoot}
     * for any XML file representing a valid FHIR {@code ActivityDefinition} that contains the given message name
     * in an appropriate extension structure.
     * </p>
     *
     * @param messageName the message name to search for
     * @param projectRoot the root directory of the project containing FHIR resources
     * @return {@code true} if an ActivityDefinition with the specified message name is found; {@code false} otherwise
     */
    public static boolean activityDefinitionExists(String messageName,
                                                   File projectRoot)
    {
        return  isDefinitionByContent(messageName, projectRoot,
                ACTIVITY_DEFINITION_DIR,       true)
                ||  isDefinitionByContent(messageName, projectRoot,
                ACTIVITY_DEFINITION_DIR_FLAT,  true);
    }

    /**
     * Checks whether a StructureDefinition resource with the given canonical profile URL exists.
     * <p>
     * Any version suffix (e.g., "|1.0") is stripped before matching.
     * The search is performed against both Maven-style and flat folder layouts under {@code projectRoot}.
     * </p>
     *
     * @param profileValue the canonical URL of the StructureDefinition, optionally including a version suffix
     * @param projectRoot  the project root folder containing FHIR resources
     * @return {@code true} if a matching StructureDefinition exists; {@code false} otherwise
     */
    public static boolean structureDefinitionExists(String profileValue,
                                                    File projectRoot)
    {
        String base = removeVersionSuffix(profileValue);

        // Maven layout OR flat layout
        return  isDefinitionByContent(base, projectRoot,
                STRUCTURE_DEFINITION_DIR,     false)
                ||  isDefinitionByContent(base, projectRoot,
                STRUCTURE_DEFINITION_DIR_FLAT, false);
    }


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
    private static boolean isDefinitionByContent(String value,
                                                 File projectRoot,
                                                 String definitionDir,
                                                 boolean isActivityDefinition)
    {
        File dir = new File(projectRoot, definitionDir);
        if (!dir.exists() || !dir.isDirectory())
        {
            return false;
        }

        try (Stream<Path> paths = Files.walk(dir.toPath()))
        {
            List<Path> xmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .toList();

            return xmlFiles.stream().anyMatch(p -> fileContainsValue(p, value, isActivityDefinition));
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * Determines if a particular XML file (either an ActivityDefinition or a StructureDefinition)
     * contains the specified value.
     * <p>
     * Depending on whether {@code isDefinition} is {@code true} (ActivityDefinition) or {@code false}
     * (StructureDefinition), this method loads the file, verifies that its root element matches the
     * expected resource type, and then delegates the actual search to either
     * {@link #activityDefinitionContainsMessageName(Document, String)} or
     * {@link #structureDefinitionContainsValue(Document, String)}.
     * </p>
     *
     * @param filePath       the path to the XML file
     * @param value          the string value to look for
     * @param isDefinition   {@code true} if searching in an ActivityDefinition, {@code false} if searching in a StructureDefinition
     * @return {@code true} if the file contains the specified value, {@code false} otherwise
     */
    private static boolean fileContainsValue(Path filePath, String value, boolean isDefinition)
    {
        try
        {
            Document doc = parseXml(filePath);
            if (doc == null) return false;

            String rootName = doc.getDocumentElement().getLocalName();
            if (isDefinition && !"ActivityDefinition".equals(rootName)) return false;
            if (!isDefinition && !"StructureDefinition".equals(rootName)) return false;

            // Use the existing method to search either the ActivityDefinition or the StructureDefinition:
            if (isDefinition)
                return activityDefinitionContainsMessageName(doc, value);
            else
                return structureDefinitionContainsValue(doc, value);
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    /**
     * Parses an XML file at the given {@code filePath} into a {@link Document} object.
     * <p>
     * This method is namespace-aware and uses a {@link DocumentBuilder} to load and parse
     * the XML content. If an error occurs (e.g., file not found, parse error), it throws an exception.
     * </p>
     *
     * @param filePath the path to the XML file
     * @return a {@link Document} representing the parsed XML, or {@code null} if an error occurs
     * @throws Exception if there is an I/O or parsing error
     */
    private static Document parseXml(Path filePath) throws Exception
    {
        try (FileInputStream fis = new FileInputStream(filePath.toFile()))
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(fis);
        }
    }

    /**
     * Extracts all distinct message name values defined within {@code StructureDefinition} XML resources
     * found in the given project directory.
     * <p>
     * Specifically, this method looks for FHIR structure elements that represent Task input elements
     * with the ID {@code Task.input:message-name.value[x]} and a nested {@code fixedString} or {@code valueString}
     * element containing a {@code value} attribute.
     * </p>
     *
     * <p>
     * The method supports both Maven-style folder layout (e.g., {@code src/main/resources/fhir/StructureDefinition})
     * and flat layout (e.g., {@code fhir/StructureDefinition}) as found in CI/CD builds or exploded JARs.
     * </p>
     *
     * <p>
     * Example matched fragment:
     * <pre>{@code
     * <element id="Task.input:message-name.value[x]">
     *   <fixedString value="dashboardReportSend"/>
     * </element>
     * }</pre>
     * </p>
     *
     * @param projectRoot The root directory of the project, which contains the {@code StructureDefinition} XML files.
     * @return A {@link Set} of all distinct message-name values (e.g., {@code {"ping", "pong", "dashboardReportSend"}}).
     */
    public static Set<String> getAllMessageValuesFromStructureDefinitions(File projectRoot)
    {
        Set<String> messageValues = new HashSet<>();

        // 1) classic Maven/Gradle layout
        collectMessageNames(projectRoot, STRUCTURE_DEFINITION_DIR,      messageValues);
        // 2) flat CI / exploded-JAR layout
        collectMessageNames(projectRoot, STRUCTURE_DEFINITION_DIR_FLAT, messageValues);

        return messageValues;
    }

    /**
     * Helper method that scans a specific subdirectory under the project root for {@code StructureDefinition}
     * XML files and extracts any message name values defined using {@code fixedString} or {@code valueString}
     * elements inside {@code Task.input:message-name.value[x]} elements.
     * <p>
     * This method is layout-agnostic and does not perform fallback; it assumes the provided path exists
     * and is valid for the given structure layout (Maven-style or flat).
     * </p>
     *
     * <p>
     * Any extracted values are added to the supplied {@code messageValues} set, which is mutated in-place.
     * Invalid or non-parsable XML files are skipped silently.
     * </p>
     *
     * @param projectRoot   The root folder of the project that contains FHIR resource subdirectories.
     * @param relDir        The relative subdirectory (e.g., {@code src/main/resources/fhir/StructureDefinition}).
     * @param messageValues A mutable {@link Set} where extracted message-name values are accumulated.
     */
    private static void collectMessageNames(File projectRoot, String relDir, Set<String> messageValues)
    {
        File dir = new File(projectRoot, relDir);
        if (!dir.isDirectory())
            return;

        try (Stream<Path> paths = Files.walk(dir.toPath()))
        {
            List<Path> xmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .toList();

            for (Path xml : xmlFiles)
            {
                Document doc = parseXml(xml);
                if (doc == null)
                    continue;

                // <element id="Task.input:message-name.value[x]"> … <fixedString|valueString value="…"/>
                String xpathExpr =
                        "//*[local-name()='element' and @id='Task.input:message-name.value[x]']"
                                + "/*[(local-name()='fixedString' or local-name()='valueString')]/@value";

                NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                        .compile(xpathExpr)
                        .evaluate(doc, XPathConstants.NODESET);

                for (int i = 0; i < nodes.getLength(); i++)
                {
                    String v = nodes.item(i).getTextContent();
                    if (v != null && !v.isBlank())
                        messageValues.add(v.trim());
                }
            }
        }
        catch (Exception ignored)
        {
            // Parsing failures are non-fatal – just skip the offending file
        }
    }

    /**
     * Checks whether any FHIR {@code ActivityDefinition} resource under the given {@code projectRoot}
     * contains the specified message name within a {@code <valueString>} or {@code <fixedString>}
     * inside an {@code <extension url="message-name">}.
     * <p>
     * This method supports both Maven-style directory layouts (e.g.,
     * {@code src/main/resources/fhir/ActivityDefinition/...}) and flat directory structures
     * commonly found in CI/CD environments (e.g., {@code fhir/ActivityDefinition/...}).
     * </p>
     *
     * @param message     the message name to search for (e.g., {@code dashboardReportSend})
     * @param projectRoot the root directory of the project or exploded JAR file structure
     * @return {@code true} if at least one ActivityDefinition contains the given message name; {@code false} otherwise
     */
    public static boolean activityDefinitionHasMessageName(String message, File projectRoot)
    {
        // Collect message names from both layouts
        List<String> allMessageNames = new ArrayList<>();

        allMessageNames.addAll(getMessageNamesFromDir(message, projectRoot, ACTIVITY_DEFINITION_DIR));
        allMessageNames.addAll(getMessageNamesFromDir(message, projectRoot, ACTIVITY_DEFINITION_DIR_FLAT));

        // Return true if the message is among the collected names
        return allMessageNames.contains(message);
    }

    /**
     * Searches the specified relative directory (under {@code projectRoot}) for FHIR
     * {@code ActivityDefinition} XML files that declare an {@code extension} element with
     * {@code url="message-name"} and a nested {@code valueString} or {@code fixedString} with
     * the specified value.
     * <p>
     * This method is directory-specific and does not include layout fallbacks.
     * It should be called by higher-level methods that apply layout strategies.
     * </p>
     *
     * @param message     the message name to locate (e.g., {@code ping}, {@code pong}, {@code dashboardReportSend})
     * @param projectRoot the root folder of the project or plugin being validated
     * @param relDir      the relative subdirectory where ActivityDefinition files are located
     * @return {@code true} if a matching ActivityDefinition is found; {@code false} otherwise
     */
    private static List<String> getMessageNamesFromDir(String message, File projectRoot, String relDir)
    {
        List<String> foundMessageNames = new ArrayList<>();

        File dir = new File(projectRoot, relDir);
        if (!dir.exists() || !dir.isDirectory())
            return foundMessageNames;

        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            List<Path> xmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .toList();

            for (Path xml : xmlFiles) {
                Document doc = parseXml(xml);
                if (doc == null) continue;

                if (!"ActivityDefinition".equals(doc.getDocumentElement().getLocalName()))
                    continue;

                // XPath to find: <extension url="message-name"><valueString value="..." />
                String xpathExpr = "//*[local-name()='extension' and @url='message-name']" +
                        "/*[(local-name()='valueString' or local-name()='fixedString') and @value='" + message + "']";

                NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                        .compile(xpathExpr)
                        .evaluate(doc, XPathConstants.NODESET);

                for (int i = 0; i < nodes.getLength(); i++) {
                    Node node = nodes.item(i);
                    if (node.getAttributes() != null) {
                        Node valueAttr = node.getAttributes().getNamedItem("value");
                        if (valueAttr != null) {
                            foundMessageNames.add(valueAttr.getNodeValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log if needed, return collected so far
        }
        return foundMessageNames;
    }

    /**
     * Checks if the given {@link Document}, presumed to be an ActivityDefinition,
     * contains the specified message name in an "extension" element with
     * {@code url="message-name"}.
     *
     * @param doc         the XML {@link Document} of the ActivityDefinition
     * @param messageName the message name to search for
     * @return {@code true} if found, {@code false} otherwise
     * @throws XPathExpressionException if the XPath evaluation fails
     */
    private static boolean activityDefinitionContainsMessageName(Document doc, String messageName)
            throws XPathExpressionException
    {
        // Minimal fallback check
        String xpathExpr = "//*[local-name()='extension' and @url='message-name']" +
                "/*[(local-name()='valueString' or local-name()='fixedString') and @value='" + messageName + "']";
        return evaluateXPathExists(doc, xpathExpr);
    }

    /**
     * Checks whether the given {@link Document}, presumed to be a StructureDefinition,
     * contains the specified string value in a URL element or a fixed/value string element.
     *
     * @param doc the XML {@link Document} of the StructureDefinition
     * @param val the string value to search for
     * @return {@code true} if found, {@code false} otherwise
     * @throws XPathExpressionException if the XPath evaluation fails
     */
    private static boolean structureDefinitionContainsValue(Document doc, String val)
            throws XPathExpressionException
    {
        // Minimal fallback check
        String xpathExpr = "//*[local-name()='url'][@value='" + val + "'] | " +
                "//*[local-name()='fixedString' or local-name()='valueString'][@value='" + val + "']";
        return evaluateXPathExists(doc, xpathExpr);
    }

    /**
     * Evaluates the provided XPath expression on the given {@link Document} and checks
     * whether any nodes match.
     *
     * @param doc       the {@link Document} to evaluate against
     * @param xpathExpr the XPath expression to execute
     * @return {@code true} if the XPath finds one or more nodes, {@code false} otherwise
     * @throws XPathExpressionException if the XPath expression is invalid or the evaluation fails
     */
    private static boolean evaluateXPathExists(Document doc, String xpathExpr) throws XPathExpressionException
    {
        XPathExpression expr = XPathFactory.newInstance().newXPath().compile(xpathExpr);
        NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        return (nodes != null && nodes.getLength() > 0);
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
    private static String removeVersionSuffix(String value)
    {
        if (value == null) return null;
        int pipeIndex = value.indexOf("|");
        return (pipeIndex != -1) ? value.substring(0, pipeIndex) : value;
    }
    /**
     * Checks if the given ActivityDefinition Document contains a {@code url} element
     * that matches the supplied canonical value (version removed).
     *
     * @param doc            the DOM {@code Document} representing an ActivityDefinition
     * @param canonicalValue the canonical URL to match
     * @return {@code true} if the URL is found; {@code false} otherwise
     * @throws XPathExpressionException if the XPath expression is invalid
     */

    private static boolean activityDefinitionContainsInstantiatesCanonical(Document doc, String canonicalValue)
            throws XPathExpressionException {

        String searchValue = removeVersionSuffix(canonicalValue);

        String xpathExpr = "/*[local-name()='ActivityDefinition']/*[local-name()='url' and @value='" + searchValue + "']";
        return evaluateXPathExists(doc, xpathExpr);
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
            Document doc = parseXml(filePath);
            if (doc == null) return false;
            String rootName = doc.getDocumentElement().getLocalName();
            if (!"ActivityDefinition".equals(rootName)) return false;
            return activityDefinitionContainsInstantiatesCanonical(doc, canonicalValue);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Checks whether an ActivityDefinition resource exists with a matching {@code url}
     * that corresponds to the supplied {@code instantiatesCanonical} value.
     * <p>
     * The version part of the canonical is stripped before lookup. Both structured and flat directories are supported.
     * </p>
     *
     * @param canonical    the canonical URL string used in Task.instantiatesCanonical
     * @param projectRoot  the project root containing FHIR resources
     * @return {@code true} if a corresponding ActivityDefinition is found; {@code false} otherwise
     */
    public static boolean activityDefinitionExistsForInstantiatesCanonical(String canonical,
                                                                           File projectRoot)
    {
        String base = removeVersionSuffix(canonical);

        return  findActivityDefinitionFile(base, projectRoot) != null;
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
    public static File findStructureDefinitionFile(String profileValue,
                                                   File projectRoot)
    {
        String base = removeVersionSuffix(profileValue);

        File f = tryFindStructureFile(base, projectRoot, STRUCTURE_DEFINITION_DIR);
        if (f != null) return f;

        return tryFindStructureFile(base, projectRoot, STRUCTURE_DEFINITION_DIR_FLAT);
    }

    /**
     * Checks if the provided StructureDefinition {@link Document} contains a <code>&lt;url&gt;</code>
     * element with a value matching the given profile value.
     * <p>
     * This method uses an XPath expression to search for a <code>&lt;url&gt;</code> element with the specified value.
     * </p>
     *
     * @param doc          the XML {@link Document} of the StructureDefinition
     * @param profileValue the profile value to search for
     * @return {@code true} if a matching <code>&lt;url&gt;</code> element is found; {@code false} otherwise
     * @throws XPathExpressionException if the XPath evaluation fails
     */
    private static boolean structureDefinitionHasUrlValue(Document doc, String profileValue)
            throws XPathExpressionException
    {
        String xpathExpr = "//*[local-name()='url' and @value='" + profileValue + "']";
        return evaluateXPathExists(doc, xpathExpr);
    }


    /**
     * Retrieves the canonical value for the "Task.instantiatesCanonical" element from the given XML Document.
     * <p>
     * This method uses an XPath expression to locate an element with a local name "element" and an attribute
     * "id" equal to "Task.instantiatesCanonical". It then selects its child element with the local name "fixedCanonical"
     * and extracts the value of its "value" attribute.
     * </p>
     *
     * @param doc the XML Document to be searched; may be {@code null}
     * @return the value of the "value" attribute from the first "fixedCanonical" node found, or {@code null}
     *         if the document is {@code null}, the node is not found, or an error occurs during evaluation
     */
    public static String getTaskInstantiatesCanonicalValue(Document doc) {
        if (doc == null)
            return null;
        try {
            String xpathExpr =
                    "//*[local-name()='element' and @id='Task.instantiatesCanonical']" +
                            "/*[local-name()='fixedCanonical']";
            NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                    .compile(xpathExpr)
                    .evaluate(doc, XPathConstants.NODESET);
            return extractValueFromFirstNode(nodes);
        } catch (Exception ignored) {

        }
        return null;
    }

    /**
     * Retrieves the fixed string value for the task message name from the given XML Document.
     * <p>
     * This method searches for an element whose local name is "element" and has an attribute
     * "id" with the value "Task.input:message-name.value[x]". It then looks for a child element
     * with the local name "fixedString" and extracts its "value" attribute.
     * </p>
     *
     * @param doc the XML Document to search in; may be {@code null}
     * @return the value of the "value" attribute from the first "fixedString" node found, or {@code null}
     *         if the document is {@code null} or if any errors occur during evaluation
     */
    public static String getTaskMessageNameFixedStringValue(Document doc) {
        if (doc == null)
            return null;
        try {
            String xpathExpr =
                    "//*[local-name()='element' and @id='Task.input:message-name.value[x]']" +
                            "/*[local-name()='fixedString']";
            NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                    .compile(xpathExpr)
                    .evaluate(doc, XPathConstants.NODESET);
            return extractValueFromFirstNode(nodes);
        } catch (Exception ignored) {
        }
        return null;
    }


    /**
     * Extracts the value of the "value" attribute from the first node in the provided NodeList.
     * <p>
     * If the provided NodeList is null or empty, this method returns {@code null}.
     * Otherwise, it retrieves the first node and, if the node and its attributes are not null,
     * attempts to extract the "value" attribute. If the attribute exists, its value is returned;
     * otherwise, {@code null} is returned.
     * </p>
     *
     * @param nodes the NodeList from which to extract the attribute value
     * @return the value of the "value" attribute from the first node, or {@code null} if the NodeList is
     *         null, empty, or the attribute is not present
     */
    private static String extractValueFromFirstNode(NodeList nodes) {
        if (nodes == null || nodes.getLength() == 0)
            return null;
        Node node = nodes.item(0);
        if (node != null && node.getAttributes() != null) {
            Node valAttr = node.getAttributes().getNamedItem("value");
            if (valAttr != null) {
                return valAttr.getNodeValue();
            }
        }
        return null;
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
    public static boolean questionnaireExists(String formKey,
                                              File projectRoot)
    {
        if (formKey == null || formKey.isBlank())
            return false;

        String baseKey = formKey.split("\\|")[0].trim();

        return  questionnaireExistsInDir(baseKey, projectRoot, QUESTIONNAIRE_DIR)
                || questionnaireExistsInDir(baseKey, projectRoot, QUESTIONNAIRE_DIR_FLAT);
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
                                                                      File projectRoot)
    {
        String baseCanon = removeVersionSuffix(canonical);

        // 1) Maven / Gradle workspace
        File f = tryFindActivityFile(baseCanon, projectRoot, ACTIVITY_DEFINITION_DIR);
        if (f != null)
            return f;

        // 2) Flat CI / exploded-JAR layout
        return tryFindActivityFile(baseCanon, projectRoot, ACTIVITY_DEFINITION_DIR_FLAT);
    }

    /**
     * Evaluates whether the given {@code requesterOrgId} is permitted according
     * to the {@code extension-process-authorization} rules inside the supplied
     * ActivityDefinition XML file.
     *
     * <p>The logic is intentionally simple:</p>
     * <ul>
     *   <li>If any requester code is <code>LOCAL_ALL</code> / <code>LOCAL_ALL_PRACTITIONER</code>
     *       &rArr; <b>authorised</b></li>
     *   <li>If the ActivityDefinition names one or more concrete organisation
     *       identifiers (valueIdentifier.value) and {@code requesterOrgId} is
     *       one of them &rArr; <b>authorised</b></li>
     *   <li>Else &rArr; <b>not authorised</b></li>
     * </ul>
     */
    public static boolean isRequesterAuthorised(File activityDefinitionFile,
                                                String requesterOrgId)
    {
        try
        {
            Document doc = parseXml(activityDefinitionFile.toPath());
            if (doc == null) return false;

            // 1) Any generic code => allow all local orgs
            String genericXpath =
                    "//*[local-name()='extension' and @url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization']"
                            + "/*[local-name()='extension' and @url='requester']"
                            + "/*[local-name()='valueCoding']/*[local-name()='code'][@value='LOCAL_ALL' or @value='LOCAL_ALL_PRACTITIONER']";
            if (evaluateXPathExists(doc, genericXpath))
                return true;

            // 2) Collect concrete org identifiers
            String orgXpath =
                    "//*[local-name()='extension' and @url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization']"
                            + "/*[local-name()='extension' and @url='requester']"
                            + "/*[local-name()='valueCoding']"
                            + "/*[local-name()='extension' and @url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-organization' "
                            + "           or @url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-organization-practitioner']"
                            + "//*[local-name()='valueIdentifier']/*[local-name()='value']/@value";

            NodeList vals = (NodeList) XPathFactory.newInstance().newXPath()
                    .compile(orgXpath)
                    .evaluate(doc, XPathConstants.NODESET);

            Set<String> allowed = new HashSet<>();
            for (int i = 0; i < vals.getLength(); i++)
                allowed.add(vals.item(i).getTextContent());

            return allowed.contains(requesterOrgId);
        }
        catch (Exception e)
        {
            return false;
        }
    }



    /**
     * Checks whether the given recipient organization identifier is authorized to receive
     * the Task according to the {@code ActivityDefinition}'s
     * {@code extension-process-authorization} rules.
     *
     * <p>This method evaluates the {@code ActivityDefinition} resource for:
     * <ul>
     *   <li>Generic recipient permissions such as {@code LOCAL_ALL} or {@code LOCAL_ALL_PRACTITIONER}.</li>
     *   <li>Explicitly listed organization identifiers inside nested extensions
     *       (e.g., {@code extension-process-authorization-organization} or
     *       {@code extension-process-authorization-organization-practitioner}).</li>
     * </ul>
     * If either condition is met, the recipient is considered authorized.</p>
     *
     * <p>If the ActivityDefinition cannot be parsed or does not declare any of the
     * relevant recipient extensions, this method returns {@code false}.</p>
     *
     * @param activityDefinitionFile the {@code ActivityDefinition} XML file to check
     * @param recipientOrgId         the recipient's organization identifier (e.g., {@code hs-heilbronn.de})
     * @return {@code true} if the recipient is authorized; {@code false} otherwise
     */
    public static boolean isRecipientAuthorised(File activityDefinitionFile,
                                                String recipientOrgId)
    {
        try
        {
            Document doc = parseXml(activityDefinitionFile.toPath());
            if (doc == null) return false;

            /* 1) generic LOCAL_ALL / LOCAL_ALL_PRACTITIONER → allow */
            String genericXpath =
                    "//*[local-name()='extension' and @url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization']"
                            + "/*[local-name()='extension' and @url='recipient']"
                            + "/*[local-name()='valueCoding']/*[local-name()='code']"
                            + "[@value='LOCAL_ALL' or @value='LOCAL_ALL_PRACTITIONER']";
            if (evaluateXPathExists(doc, genericXpath))
                return true;

            /* 2) concrete organisation identifiers */
            String orgXpath =
                    "//*[local-name()='extension' and @url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization']"
                            + "/*[local-name()='extension' and @url='recipient']"
                            + "/*[local-name()='valueCoding']"
                            + "/*[local-name()='extension' and ("
                            + "@url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-organization' or "
                            + "@url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-organization-practitioner')]"
                            + "//*[local-name()='valueIdentifier']/*[local-name()='value']/@value";

            NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                    .compile(orgXpath)
                    .evaluate(doc, XPathConstants.NODESET);

            Set<String> allowed = new HashSet<>();
            for (int i = 0; i < nodes.getLength(); i++)
                allowed.add(nodes.item(i).getTextContent());

            return allowed.contains(recipientOrgId);
        }
        catch (Exception ignored) { }
        return false;
    }

    /**
     * Attempts to locate a StructureDefinition file with a matching canonical URL inside the given directory.
     *
     * @param profileValue the canonical profile URL to match
     * @param projectRoot  the root directory containing FHIR resources
     * @param relDir       the relative subdirectory under {@code projectRoot} to search
     * @return the matching {@code File}, or {@code null} if none is found
     */
    private static File tryFindStructureFile(String profileValue,
                                             File projectRoot,
                                             String relDir)
    {
        File dir = new File(projectRoot, relDir);
        if (!dir.isDirectory()) return null;

        try (Stream<Path> paths = Files.walk(dir.toPath()))
        {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .filter(p -> fileContainsValue(p, profileValue, /*isActivity=*/false))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);
        }
        catch (Exception e) { return null; }
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
    private static File findActivityDefinitionFile(String canonical,
                                                   File projectRoot)
    {
        String base = removeVersionSuffix(canonical);

        File f = tryFindActivityFile(base, projectRoot, ACTIVITY_DEFINITION_DIR);
        if (f != null) return f;

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
    private static File tryFindActivityFile(String canonical,
                                            File projectRoot,
                                            String relDir)
    {
        File dir = new File(projectRoot, relDir);
        if (!dir.isDirectory()) return null;

        try (Stream<Path> p = Files.walk(dir.toPath()))
        {
            return p.filter(Files::isRegularFile)
                    .filter(x -> x.toString().toLowerCase().endsWith(".xml"))
                    .filter(x -> fileContainsInstantiatesCanonical(x, canonical))
                    .map(Path::toFile)
                    .findFirst().orElse(null);
        }
        catch (Exception e) { return null; }
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
    private static boolean questionnaireExistsInDir(String baseKey,
                                                    File projectRoot,
                                                    String relDir)
    {
        File dir = new File(projectRoot, relDir);
        if (!dir.isDirectory()) return false;

        try (Stream<Path> paths = Files.walk(dir.toPath()))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .anyMatch(p -> {
                        try {
                            Document d = parseXml(p);
                            return d != null
                                    && "Questionnaire".equals(d.getDocumentElement().getLocalName())
                                    && questionnaireContainsUrl(d, baseKey);
                        } catch (Exception e) { return false; }
                    });
        }
        catch (Exception e) { return false; }
    }
    /**
     * Checks whether the given Questionnaire {@link Document} contains a {@code <url>} element
     * whose {@code value} attribute exactly matches the provided canonical URL.
     *
     * <p>This method assumes that the document's root element is {@code Questionnaire}.
     * It uses XPath to find the {@code <url>} element and compare its {@code value} to the target string.</p>
     *
     * @param doc the parsed XML {@link Document} representing a FHIR Questionnaire
     * @param url the canonical URL to search for (without version suffix)
     * @return {@code true} if a matching {@code <url value="...">} element exists; {@code false} otherwise
     * @throws XPathExpressionException if an XPath evaluation error occurs
     */
    private static boolean questionnaireContainsUrl(Document doc, String url)
            throws XPathExpressionException
    {
        return evaluateXPathExists(doc,
                "/*[local-name()='Questionnaire']/*[local-name()='url' and @value='" + url + "']");
    }

}
