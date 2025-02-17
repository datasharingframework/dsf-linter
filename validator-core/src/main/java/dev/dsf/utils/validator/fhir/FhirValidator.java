package dev.dsf.utils.validator.fhir;

import org.w3c.dom.Document;
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
import java.util.stream.Stream;

/**
 * FhirValidator provides static methods to validate FHIR resources such as
 * ActivityDefinitions and StructureDefinitions by parsing the XML content.
 * <p>
 * It checks if a given message name (or profile) exists as a FHIR ActivityDefinition
 * or StructureDefinition by scanning the corresponding directories in the project.
 * The matching is performed by parsing the XML content and using XPath expressions.
 * </p>
 */
public class FhirValidator {
    private static final String ACTIVITY_DEFINITION_DIR = "src/main/resources/fhir/ActivityDefinition";
    private static final String STRUCTURE_DEFINITION_DIR = "src/main/resources/fhir/StructureDefinition";

    /**
     * Checks whether an ActivityDefinition exists (by content!) for the given message name.
     *
     * @param messageName the message name to check
     * @param projectRoot the project root directory
     * @return true if the content of at least one ActivityDefinition file references this message name
     */
    public static boolean activityDefinitionExists(String messageName, File projectRoot) {
        System.out.println("♫ DEBUG: Checking ActivityDefinition for messageName: " + messageName);
        boolean result = isDefinitionByContent(messageName, projectRoot, ACTIVITY_DEFINITION_DIR, true);
        System.out.println("♫ DEBUG: ActivityDefinition exists for '" + messageName + "': " + result);
        return result;
    }

    /**
     * Checks whether a StructureDefinition exists (by content!) for the given profile value.
     *
     * @param profileValue the profile value to check
     * @param projectRoot  the project root directory
     * @return true if the content of at least one StructureDefinition file references this profile
     */
    public static boolean structureDefinitionExists(String profileValue, File projectRoot) {
        System.out.println("❁ DEBUG: Checking StructureDefinition for profileValue: " + profileValue);
        boolean result = isDefinitionByContent(profileValue, projectRoot, STRUCTURE_DEFINITION_DIR, false);
        System.out.println("❁ DEBUG: StructureDefinition exists for '" + profileValue + "': " + result);
        return result;
    }


    /**
     * Searches for a definition (ActivityDefinition or StructureDefinition) by parsing the XML content of Dateien in the given directory.
     *
     * @param value               the string to search for (e.g. messageName or profile value)
     * @param projectRoot         the project root directory
     * @param definitionDir       the relative directory (ActivityDefinition or StructureDefinition)
     * @param isActivityDefinition if true, search in ActivityDefinition files, sonst in StructureDefinition
     * @return true if a matching definition is found, false otherwise
     */
    private static boolean isDefinitionByContent(String value, File projectRoot, String definitionDir, boolean isActivityDefinition) {
        File dir = new File(projectRoot, definitionDir);
        //System.out.println("♫ DEBUG: Searching in directory: " + dir.getAbsolutePath());
        if (!dir.exists() || !dir.isDirectory()) {
            //System.out.println("♫ DEBUG: Directory " + dir.getAbsolutePath() + " does not exist or is not a directory.");
            return false;
        }

        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            boolean found = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    //.peek(p -> System.out.println("DEBUG: Checking file: " + p))
                    .anyMatch(p -> {
                        boolean contains = fileContainsValue(p, value, isActivityDefinition);
                        //System.out.println("♫ DEBUG: File " + p.getFileName() + " contains value '" + value + "': " + contains);
                        return contains;
                    });
            //System.out.println("♫ DEBUG: isDefinitionByContent for value '" + value + "' returned: " + found);
            return found;
        } catch (Exception e) {
            //System.err.println("♫ DEBUG: Exception in isDefinitionByContent: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Parses the XML content of a single file and checks if it references the given value.
     *
     * @param filePath      the path to the XML file
     * @param value         the value to search for
     * @param isDefinition  if true, perform ActivityDefinition check; if false, StructureDefinition check
     * @return true if the file's content references the given value, false otherwise
     */
    private static boolean fileContainsValue(Path filePath, String value, boolean isDefinition) {
        try {
            //System.out.println("♫ DEBUG: Parsing file: " + filePath.toString());
            Document doc = parseXml(filePath);
            if (doc == null) {
                //System.out.println("♫ DEBUG: Document is null for file: " + filePath.toString());
                return false;
            }

            String rootName = doc.getDocumentElement().getLocalName();
            //System.out.println("♫ DEBUG: Root element of file " + filePath.getFileName() + ": " + rootName);
            if (isDefinition && !"ActivityDefinition".equals(rootName)) {
                //System.out.println("♫ DEBUG: Skipping file " + filePath.getFileName() + " because it is not an ActivityDefinition.");
                return false;
            }
            if (!isDefinition && !"StructureDefinition".equals(rootName)) {
                //System.out.println("♫ DEBUG: Skipping file " + filePath.getFileName() + " because it is not a StructureDefinition.");
                return false;
            }

            if (isDefinition) {
                boolean result = activityDefinitionContainsMessageName(doc, value);
                //System.out.println("♫ DEBUG: activityDefinitionContainsMessageName for '" + value + "' in file " + filePath.getFileName() + ": " + result);
                return result;
            } else {
                boolean result = structureDefinitionContainsValue(doc, value);
                //System.out.println("♫ DEBUG: structureDefinitionContainsValue for '" + value + "' in file " + filePath.getFileName() + ": " + result);
                return result;
            }
        } catch (Exception ex) {
            //System.err.println("♫ DEBUG: Error parsing file " + filePath + ": " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Parses the XML file into a DOM Document.
     *
     * @param filePath the path to the XML file
     * @return the parsed Document
     * @throws Exception if parsing fails
     */
    private static Document parseXml(Path filePath) throws Exception {
        //System.out.println("♫ DEBUG: Parsing XML file: " + filePath.toString());
        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(fis);
            //System.out.println("♫ DEBUG: Successfully parsed XML file: " + filePath.getFileName());
            return doc;
        }
    }

    /**
     * Checks if an ActivityDefinition has an extension with url="message-name" that matches the given value.
     *
     * @param doc         the parsed XML Document
     * @param messageName the message name to search for
     * @return true if the messageName is found, false otherwise
     * @throws XPathExpressionException if XPath evaluation fails
     */
    private static boolean activityDefinitionContainsMessageName(Document doc, String messageName)
            throws XPathExpressionException {
        String xpathExpr = "//*[local-name()='extension' and @url='message-name']/*[local-name()='valueString' and @value='" + messageName + "']";
        //System.out.println("♫ DEBUG: Evaluating XPath for ActivityDefinition: " + xpathExpr);
        boolean exists = evaluateXPathExists(doc, xpathExpr);
        //System.out.println("♫ DEBUG: XPath evaluation result for ActivityDefinition: " + exists);
        return exists;
    }

    /**
     * Checks if a StructureDefinition contains a fixedString or valueString element with the given value.
     *
     * @param doc the parsed XML Document
     * @param val the value to search for
     * @return true if the value is found, false otherwise
     * @throws XPathExpressionException if XPath evaluation fails
     */
    private static boolean structureDefinitionContainsValue(Document doc, String val)
            throws XPathExpressionException {
        String xpathExpr = "//*[local-name()='fixedString' or local-name()='valueString'][@value='" + val + "']";
        //System.out.println("♫ DEBUG: Evaluating XPath for StructureDefinition: " + xpathExpr);
        boolean exists = evaluateXPathExists(doc, xpathExpr);
        //System.out.println("♫ DEBUG: XPath evaluation result for StructureDefinition: " + exists);
        return exists;
    }

    /**
     * Evaluates the given XPath expression on the Document and returns true if at least one node is found.
     *
     * @param doc       the XML Document
     * @param xpathExpr the XPath expression to evaluate
     * @return true if the expression matches one or more nodes, false otherwise
     * @throws XPathExpressionException if XPath evaluation fails
     */
    private static boolean evaluateXPathExists(Document doc, String xpathExpr) throws XPathExpressionException {
        XPathExpression expr = XPathFactory.newInstance().newXPath().compile(xpathExpr);
        NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        int count = (nodes != null ? nodes.getLength() : 0);
        //System.out.println("♫ DEBUG: XPath found " + count + " nodes for expression: " + xpathExpr);
        return (count > 0);
    }
}
