package dev.dsf.utils.validator.util.resource;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Utility class for extracting values and checking content within FHIR resource documents.
 * Provides XPath-based extraction and validation methods.
 */
public class FhirResourceExtractor {

    private static final String STRUCTURE_DEFINITION_DIR = "src/main/resources/fhir/StructureDefinition";
    private static final String STRUCTURE_DEFINITION_DIR_FLAT = "fhir/StructureDefinition";


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
        if (doc == null) {
            return null;
        }

        try {
            String xpathExpr =
                    "//*[local-name()='element' and @id='Task.instantiatesCanonical']" +
                            "/*[local-name()='fixedCanonical']";

            NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                    .compile(xpathExpr)
                    .evaluate(doc, XPathConstants.NODESET);

            return extractValueFromFirstNode(nodes);
        } catch (Exception ignored) {
            return null;
        }
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
        if (doc == null) {
            return null;
        }

        try {
            String xpathExpr =
                    "//*[local-name()='element' and @id='Task.input:message-name.value[x]']" +
                            "/*[local-name()='fixedString']";

            NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                    .compile(xpathExpr)
                    .evaluate(doc, XPathConstants.NODESET);

            return extractValueFromFirstNode(nodes);
        } catch (Exception ignored) {
            return null;
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
    public Set<String> getAllMessageValuesFromStructureDefinitions(File projectRoot) {
        Set<String> messageValues = new HashSet<>();

        // Classic Maven/Gradle layout
        collectMessageNames(projectRoot, STRUCTURE_DEFINITION_DIR, messageValues);
        // Flat CI / exploded-JAR layout
        collectMessageNames(projectRoot, STRUCTURE_DEFINITION_DIR_FLAT, messageValues);

        return messageValues;
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
    public boolean activityDefinitionContainsMessageName(Document doc, String messageName)
            throws XPathExpressionException {
        String xpathExpr = "//*[local-name()='extension' and @url='message-name']" +
                "/*[(local-name()='valueString' or local-name()='fixedString') and @value='" +
                messageName + "']";
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
    public boolean structureDefinitionContainsValue(Document doc, String val)
            throws XPathExpressionException {
        String xpathExpr = "//*[local-name()='url'][@value='" + val + "'] | " +
                "//*[local-name()='fixedString' or local-name()='valueString'][@value='" +
                val + "']";
        return evaluateXPathExists(doc, xpathExpr);
    }

    /**
     * Checks if the given ActivityDefinition Document contains a url element
     * that matches the supplied canonical value (version removed).
     *
     * @param doc the DOM Document representing an ActivityDefinition
     * @param canonicalValue the canonical URL to match
     * @return true if the URL is found, false otherwise
     * @throws XPathExpressionException if the XPath expression is invalid
     */
    public boolean activityDefinitionContainsInstantiatesCanonical(Document doc,
                                                                   String canonicalValue) throws XPathExpressionException {
        String searchValue = removeVersionSuffix(canonicalValue);

        String xpathExpr = "/*[local-name()='ActivityDefinition']/*[local-name()='url' " +
                "and @value='" + searchValue + "']";
        return evaluateXPathExists(doc, xpathExpr);
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
    public boolean questionnaireContainsUrl(Document doc, String url)
            throws XPathExpressionException {
        return evaluateXPathExists(doc,
                "/*[local-name()='Questionnaire']/*[local-name()='url' and @value='" +
                        url + "']");
    }

    // Private helper methods

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
    private void collectMessageNames(File projectRoot, String relDir,
                                     Set<String> messageValues) {
        File dir = new File(projectRoot, relDir);
        if (!dir.isDirectory()) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            List<Path> fhirFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(FhirFileUtils::isFhirFile)
                    .toList();

            for (Path fhirFile : fhirFiles) {
                Document doc = FhirResourceParser.parseFhirFile(fhirFile);
                if (doc == null) {
                    continue;
                }

                String xpathExpr =
                        "//*[local-name()='element' and @id='Task.input:message-name.value[x]']" +
                                "/*[(local-name()='fixedString' or local-name()='valueString')]/@value";

                NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                        .compile(xpathExpr)
                        .evaluate(doc, XPathConstants.NODESET);

                for (int i = 0; i < nodes.getLength(); i++) {
                    String value = nodes.item(i).getTextContent();
                    if (value != null && !value.isBlank()) {
                        messageValues.add(value.trim());
                    }
                }
            }
        } catch (Exception ignored) {
            // Parsing failures are non-fatal - just skip the offending file
        }
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
        if (nodes == null || nodes.getLength() == 0) {
            return null;
        }

        Node node = nodes.item(0);
        if (node != null && node.getAttributes() != null) {
            Node valueAttr = node.getAttributes().getNamedItem("value");
            if (valueAttr != null) {
                return valueAttr.getNodeValue();
            }
        }
        return null;
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
    private boolean evaluateXPathExists(Document doc, String xpathExpr)
            throws XPathExpressionException {
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
    private String removeVersionSuffix(String value) {
        if (value == null) return null;
        int pipeIndex = value.indexOf("|");
        return (pipeIndex != -1) ? value.substring(0, pipeIndex) : value;
    }
}