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
 * FhirValidator provides static methods to validate and extract relevant values from
 * FHIR resources such as ActivityDefinition and StructureDefinition.
 * <p>
 * This class includes methods for:
 * <ul>
 *   <li>Checking for the existence of an ActivityDefinition resource containing a given message name.</li>
 *   <li>Checking for the existence of a StructureDefinition resource containing a given profile (canonical) value.</li>
 *   <li>Extracting all distinct message values from StructureDefinition files.</li>
 *   <li>Verifying that an ActivityDefinition contains a specific message name in its extensions.</li>
 *   <li>Retrieving the canonical value from Task.instantiatesCanonical elements.</li>
 *   <li>Retrieving the fixed string value from Task.input:message-name.value[x] elements.</li>
 *   <li>Checking for the existence of a Questionnaire resource matching a provided formKey.</li>
 * </ul>
 * </p>
 *
 * <p>
 * References:
 * <ul>
 *   <li>HL7 FHIR Standard: <a href="https://www.hl7.org/fhir/">https://www.hl7.org/fhir/</a></li>
 *   <li>HL7 FHIR StructureDefinition:
 *       <a href="https://www.hl7.org/fhir/structuredefinition.html">
 *           https://www.hl7.org/fhir/structuredefinition.html
 *       </a>
 *   </li>
 * </ul>
 * </p>
 */
public class FhirValidator
{
    private static final String ACTIVITY_DEFINITION_DIR = "src/main/resources/fhir/ActivityDefinition";
    private static final String STRUCTURE_DEFINITION_DIR = "src/main/resources/fhir/StructureDefinition";
    private static final String QUESTIONNAIRE_DIR = "src/main/resources/fhir/Questionnaire";

    /**
     * Checks if an ActivityDefinition resource matching the given message name exists.
     * <p>
     * This method searches the {@code ACTIVITY_DEFINITION_DIR} directory under the specified
     * {@code projectRoot} for any XML file that contains the desired message name within
     * a recognized FHIR {@code ActivityDefinition} structure.
     * </p>
     *
     * @param messageName the message name to search for
     * @param projectRoot the project root directory (containing the FHIR resources)
     * @return {@code true} if an ActivityDefinition with the given message name is found;
     *         {@code false} otherwise
     */
    public static boolean activityDefinitionExists(String messageName, File projectRoot)
    {
        return isDefinitionByContent(messageName, projectRoot, ACTIVITY_DEFINITION_DIR, true);
    }

    /**
     * Checks if a StructureDefinition resource matching the given profile value exists.
     * <p>
     * This method first removes any version suffix (e.g., removing the "|1.0" part),
     * then searches the {@code STRUCTURE_DEFINITION_DIR} directory under the specified
     * {@code projectRoot} for any XML file that contains the specified profile value
     * within a recognized FHIR {@code StructureDefinition} structure.
     * </p>
     *
     * @param profileValue the canonical or profile reference string (which may contain a version suffix)
     * @param projectRoot  the project root directory (containing the FHIR resources)
     * @return {@code true} if a StructureDefinition with the given profile value is found;
     *         {@code false} otherwise
     */
    public static boolean structureDefinitionExists(String profileValue, File projectRoot)
    {
        String baseProfile = removeVersionSuffix(profileValue);
        return isDefinitionByContent(baseProfile, projectRoot, STRUCTURE_DEFINITION_DIR, false);
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
     * Extract all message values from all StructureDefinition XML files.
     * <p>
     * Specifically, it searches for elements:
     * <pre>{@code
     *   <element id="Task.input:message-name.value[x]">
     *       <fixedString value="someMessageValue" />
     *   </element>
     * }</pre>
     * and collects the <code>value</code> attributes of any such <code>fixedString</code>.
     * </p>
     *
     * @param projectRoot the project root directory
     * @return a {@link Set} of all distinct messageName values found (e.g., {"pong", "ping", ...})
     */
    public static Set<String> getAllMessageValuesFromStructureDefinitions(File projectRoot)
    {
        Set<String> messageValues = new HashSet<>();

        File dir = new File(projectRoot, STRUCTURE_DEFINITION_DIR);
        if (!dir.exists() || !dir.isDirectory())
            return messageValues;

        try (Stream<Path> paths = Files.walk(dir.toPath()))
        {
            List<Path> xmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .toList();

            for (Path xml : xmlFiles)
            {
                Document doc = parseXml(xml);
                if (doc != null)
                {
                    // XPath: find all <element id="Task.input:message-name.value[x]">
                    // then <fixedString value="XYZ"/>
                    String xpathExpr = "//*[local-name()='element' and @id='Task.input:message-name.value[x]']" +
                            "/*[local-name()='fixedString']/@value";

                    NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                            .compile(xpathExpr)
                            .evaluate(doc, XPathConstants.NODESET);

                    for (int i = 0; i < nodes.getLength(); i++)
                    {
                        String val = nodes.item(i).getTextContent();
                        if (val != null && !val.isBlank())
                        {
                            messageValues.add(val.trim());
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            // handle/log exception if needed
        }
        return messageValues;
    }

    /**
     * Checks if any ActivityDefinition in the project contains an extension with the given message name.
     * <p>
     * This method walks through all XML files under the ACTIVITY_DEFINITION_DIR, parses files that represent an ActivityDefinition,
     * extracts all <extension url="message-name">/<valueString> elements (by retrieving their "value" attribute),
     * collects these message names, and finally checks if the provided message is among them.
     *
     * @param message     The message name to search for.
     * @param projectRoot The root folder of the project.
     * @return {@code true} if the message exists in at least one ActivityDefinition, {@code false} otherwise.
     */
    public static boolean activityDefinitionHasMessageName(String message, File projectRoot) {
        File dir = new File(projectRoot, ACTIVITY_DEFINITION_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        // List to collect all message names found in the ActivityDefinition files.
        List<String> foundMessageNames = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            List<Path> xmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .toList();

            for (Path xml : xmlFiles) {
                Document doc = parseXml(xml);
                if (doc == null) {
                    continue;
                }

                // Must be an ActivityDefinition
                String rootName = doc.getDocumentElement().getLocalName();
                if (!"ActivityDefinition".equals(rootName)) {
                    continue;
                }

                // Check if there's an <extension url="message-name"><valueString value="message"/>
                String xpathExpr = "//*[local-name()='extension' and @url='message-name']" +
                        "/*[local-name()='valueString' and @value='" + message + "']";
                NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                        .compile(xpathExpr)
                        .evaluate(doc, XPathConstants.NODESET);

                if (nodes != null && nodes.getLength() > 0) {
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
            }
        } catch (Exception e) {
            return false;
        }
        // Return true if the provided message exists in the collected message names.
        return foundMessageNames.contains(message);
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


    private static boolean activityDefinitionContainsInstantiatesCanonical(Document doc, String canonicalValue)
            throws XPathExpressionException {

        String searchValue = removeVersionSuffix(canonicalValue);

        String xpathExpr = "/*[local-name()='ActivityDefinition']/*[local-name()='url' and @value='" + searchValue + "']";
        return evaluateXPathExists(doc, xpathExpr);
    }

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

    public static boolean activityDefinitionExistsForInstantiatesCanonical(String canonicalValue, File projectRoot) {
        File dir = new File(projectRoot, ACTIVITY_DEFINITION_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            List<Path> xmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .toList();
            return xmlFiles.stream().anyMatch(p -> fileContainsInstantiatesCanonical(p, canonicalValue));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Finds the first {@code .xml} file under {@link #STRUCTURE_DEFINITION_DIR} that:
     * <ul>
     *   <li>Is a FHIR StructureDefinition (root element == "StructureDefinition")</li>
     *   <li>Has a child <url value="profileValue"/></li>
     * </ul>
     *
     * @param profileValue the profile URL to look for, e.g. "http://dsf.dev/fhir/StructureDefinition/task-pong"
     * @param projectRoot the root directory of the project
     * @return the {@code File} if found, or {@code null} if not found
     */
    public static File findStructureDefinitionFile(String profileValue, File projectRoot)
    {
        // Normalize/strip version if needed
        String baseProfile = removeVersionSuffix(profileValue);

        File dir = new File(projectRoot, STRUCTURE_DEFINITION_DIR);
        if (!dir.exists() || !dir.isDirectory())
            return null;

        try (Stream<Path> paths = Files.walk(dir.toPath()))
        {
            List<Path> xmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .toList();

            for (Path p : xmlFiles)
            {
                Document doc = parseXml(p);
                if (doc == null)
                    continue;

                // must be a "StructureDefinition"
                String rootName = doc.getDocumentElement().getLocalName();
                if (!"StructureDefinition".equals(rootName))
                    continue;

                // check if there's <url value="baseProfile">
                if (structureDefinitionHasUrlValue(doc, baseProfile))
                {
                    return p.toFile();
                }
            }
        }
        catch (Exception e)
        {
            // Log if needed
        }
        return null;
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
     * Checks if a Questionnaire resource matching the given formKey exists in the questionnaire directory.
     * This method ignores any version suffix (everything after a "|" character).
     *
     * @param formKey     The formKey from the User Task, possibly containing a version suffix.
     * @param projectRoot The project root directory containing the FHIR resources.
     * @return {@code true} if a matching Questionnaire is found, {@code false} otherwise.
     */
    public static boolean questionnaireExists(String formKey, File projectRoot) {
        if (formKey == null || formKey.trim().isEmpty()) {
            return false;
        }

        // Remove the version part if present using "|" as a delimiter.
        String baseFormKey = formKey.split("\\|")[0].trim();

        File dir = new File(projectRoot, QUESTIONNAIRE_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            List<Path> xmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .toList();

            for (Path p : xmlFiles) {
                Document doc = parseXml(p); // Uses the existing parseXml method.
                if (doc == null) continue;
                // Check if the root element is "Questionnaire"
                if (!"Questionnaire".equals(doc.getDocumentElement().getLocalName())) continue;
                // Use XPath to check if there's a <url> element with the matching baseFormKey.
                if (evaluateXPathExists(doc, "//*[local-name()='url' and @value='" + baseFormKey + "']")) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

}
