package dev.dsf.linter.util.linting;

import dev.dsf.linter.output.item.FhirElementLintItemSuccess;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.*;
import java.io.File;
import java.util.List;

/**
 * Abstract base class for all FHIR resource linters used in the DSF linting framework.
 *
 * <p>
 * This class defines a common structure and utility methods for FHIR linters that operate on
 * DOM-based XML {@link Document} instances. Subclasses must implement the linting logic for
 * specific FHIR resource types such as {@code Questionnaire}, {@code Task}, {@code ValueSet}, etc.
 * </p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Defines an abstract API for checking whether a resource can be linted
 *       ({@link #canLint(Document)})</li>
 *   <li>Defines an abstract API for executing linting and producing structured results
 *       ({@link #lint(Document, File)})</li>
 *   <li>Provides reusable XPath utilities to extract values from FHIR XML resources</li>
 *   <li>Provides convenience methods for reporting linting results</li>
 * </ul>
 *
 * <p>
 * linters extending this class typically operate on a single FHIR resource type and are
 * automatically discovered via {@code ServiceLoader} or registered manually.
 * </p>
 *
 */
public abstract class AbstractFhirInstanceLinter
{
    /*
      API to be implemented by concrete linters
      */

    /**
     * Determines whether this linter can process the given FHIR resource.
     * Typically, this check is performed by inspecting the root element's local name
     * of the provided {@link Document}.
     *
     * @param document the FHIR XML document to inspect
     * @return {@code true} if the linter supports this document type; {@code false} otherwise
     */
    public abstract boolean canLint(Document document);

    /**
     * Performs all linting checks on the given FHIR resource and collects results
     * in the form of lint items (success, warnings, or errors).
     *
     * @param document     the FHIR resource document to lint
     * @param resourceFile the corresponding file on disk (used for reference reporting)
     * @return a list of lint items indicating success or problems found
     */
    public abstract List<?> lint(Document document, File resourceFile);

    /*
      Shared XPath helpers (unchanged)
       */

    /**
     * Evaluates the given XPath expression on the provided document and returns the resulting node list.
     *
     * @param doc       the XML document to query
     * @param xpathExpr the XPath expression
     * @return a {@link NodeList} of matching nodes, or {@code null} if evaluation fails
     */
    protected NodeList evaluateXPath(Document doc, String xpathExpr)
    {
        try
        {
            XPath xPath = XPathFactory.newInstance().newXPath();
            return (NodeList) xPath.compile(xpathExpr).evaluate(doc, XPathConstants.NODESET);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Evaluates a relative XPath expression starting from the given context node.
     *
     * @param node         the context node from which to evaluate
     * @param relativeExpr the relative XPath expression
     * @return a {@link NodeList} of matching child nodes, or {@code null} if evaluation fails
     */
    protected static NodeList evaluateXPath(Node node, String relativeExpr)
    {
        try
        {
            XPath xPath = XPathFactory.newInstance().newXPath();
            return (NodeList) xPath.compile(relativeExpr).evaluate(node, XPathConstants.NODESET);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Extracts the text value of the first node that matches the relative XPath expression from the given node.
     *
     * @param node         the context node
     * @param relativeExpr the XPath expression relative to the node
     * @return the text content of the first matching node, or {@code null} if no match
     */
    protected static String extractSingleNodeValue(Node node, String relativeExpr)
    {
        NodeList nodes = evaluateXPath(node, relativeExpr);
        if (nodes != null && nodes.getLength() > 0 && nodes.item(0) != null)
            return nodes.item(0).getTextContent();
        return null;
    }

    /*
      Convenience helpers shared by all linters
      */

    /**
     * Records a successful linting step.
     *
     * <p>This helper creates a {@link FhirElementLintItemSuccess} item to document
     * a passed linting check. It mirrors the success reporting approach used in
     * {@code FhirTaskLinter} and is designed for reuse in other linters.</p>
     *
     * @param file    the file being linted
     * @param ref     a string reference (e.g., resource URL)
     * @param message a message describing the successful linting
     * @return a success item to be added to the result list
     */
    protected FhirElementLintItemSuccess ok(File file, String ref, String message)
    {
        return new FhirElementLintItemSuccess(file, ref, message);
    }

    /**
     * Utility method that checks whether a string is {@code null} or contains only whitespace.
     *
     * @param s the string to check
     * @return {@code true} if the string is blank; {@code false} otherwise
     */
    protected static boolean blank(String s)
    {
        return s == null || s.isBlank();
    }

    /**
     * Convenience wrapper for {@link #extractSingleNodeValue(Node, String)}.
     *
     * @param ctx the context node
     * @param xp  the relative XPath expression
     * @return the extracted string value, or {@code null}
     */
    protected String val(Node ctx, String xp)
    {
        return extractSingleNodeValue(ctx, xp);
    }

    /**
     * Convenience wrapper for {@link #evaluateXPath(Node, String)}.
     *
     * @param ctx the context node
     * @param xp  the relative XPath expression
     * @return a {@link NodeList} of matching child nodes, or {@code null}
     */
    protected NodeList xp(Node ctx, String xp)
    {
        return evaluateXPath(ctx, xp);
    }

    /**
     * Extracts the effective value from a FHIR value[x] choice element.
     *
     * <p>This method is designed to parse the value from a parent node (like {@code <input>})
     * that contains one of the many {@code value[x]} variants defined by FHIR. It handles the
     * following common cases:</p>
     * <ul>
     * <li><b>Primitive types:</b> Extracts the {@code value} attribute from elements like
     * {@code <valueString value="..." />}, {@code <valueBoolean value="..." />}, etc.</li>
     * <li><b>valueReference:</b> Handles both <strong>direct references</strong> (via a nested {@code reference} element)
     * and <strong>logical references</strong> (via a nested {@code identifier} element).</li>
     * <li><b>valueIdentifier:</b> Extracts the value from a nested {@code value} element within an identifier.</li>
     * </ul>
     *
     * <p>The method attempts to find a value by checking for child nodes in this specific order of precedence:</p>
     * <ol>
     * <li>Any element whose name starts with "value" (e.g., {@code valueString}, {@code valueInstant}).</li>
     * <li>A direct reference via the path {@code valueReference/reference/@value}.</li>
     * <li>A logical reference via the path {@code valueReference/identifier/value/@value}.</li>
     * <li>An identifier via the path {@code valueIdentifier/value/@value}.</li>
     * </ol>
     *
     * @param node The parent DOM node, typically a FHIR element like {@code input} or {@code output},
     * which contains a {@code value[x]} child.
     * @return The extracted string value if found; otherwise, {@code null}.
     *
     * @see <a href="http://hl7.org/fhir/R4/references.html#Reference">FHIR R4 DataType: Reference</a>
     * @see <a href="https://www.hl7.org/fhir/R4/formats.html#choice">FHIR R4 Choice Data Types</a>
     */
    protected static String extractValueX(Node node)
    {
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++)
        {
            Node k = kids.item(i);

            // case 1 – primitive value[x]: <valueString value="..."/>
            if (k.getNodeName().startsWith("value") && k.hasAttributes())
            {
                Node v = k.getAttributes().getNamedItem("value");
                if (v != null) return v.getNodeValue();
            }

            // case 2 – reference: handles direct and logical references
            if ("valueReference".equals(k.getNodeName()))
            {
                // First, try to find a direct reference (e.g., <reference value="..."/>)
                String directRef = extractSingleNodeValue(k, "./*[local-name()='reference']/@value");
                if (directRef != null && !directRef.isBlank())
                {
                    return directRef;
                }

                // If not found, try to find a logical reference (e.g., <identifier><value value="..."/></identifier>)
                String logicalRef = extractSingleNodeValue(k, "./*[local-name()='identifier']/*[local-name()='value']/@value");
                if (logicalRef != null && !logicalRef.isBlank())
                {
                    return logicalRef;
                }
            }

            // case 3 – valueIdentifier: <valueIdentifier><value value="..."/></valueIdentifier>
            if ("valueIdentifier".equals(k.getNodeName())) {
                String idVal = extractSingleNodeValue(
                        k, "./*[local-name()='value']/@value");
                if (idVal != null && !idVal.isBlank()) return idVal;
            }

        }

        return null;
    }

}
