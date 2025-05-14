package dev.dsf.utils.validator.util;

import dev.dsf.utils.validator.item.FhirElementValidationItemSuccess;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.*;
import java.io.File;
import java.util.List;

/**
 * Abstract base class for FHIR resource validators.
 *
 * <p>This class provides common utility methods to assist in validating FHIR resources
 * represented as DOM {@link Document} objects. It defines the basic structure for
 * all concrete validators, and centralizes frequently used XPath and utility logic
 * for consistent use across all subclasses.</p>
 *
 * <p>Subclasses must implement the validation logic by overriding the abstract methods
 * {@link #canValidate(Document)} and {@link #validate(Document, File)}.</p>
 */
public abstract class AbstractFhirInstanceValidator
{
    /*
      API to be implemented by concrete validators
      */

    /**
     * Determines whether this validator can process the given FHIR resource.
     * Typically, this check is performed by inspecting the root element's local name
     * of the provided {@link Document}.
     *
     * @param document the FHIR XML document to inspect
     * @return {@code true} if the validator supports this document type; {@code false} otherwise
     */
    public abstract boolean canValidate(Document document);

    /**
     * Performs all validation checks on the given FHIR resource and collects results
     * in the form of validation items (success, warnings, or errors).
     *
     * @param document     the FHIR resource document to validate
     * @param resourceFile the corresponding file on disk (used for reference reporting)
     * @return a list of validation items indicating success or problems found
     */
    public abstract List<?> validate(Document document, File resourceFile);

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
     * Extracts the text value of the first node that matches the given XPath expression in the document.
     *
     * @param doc       the XML document to search
     * @param xpathExpr the XPath expression
     * @return the text content of the first matching node, or {@code null} if no match
     */
    protected String extractSingleNodeValue(Document doc, String xpathExpr)
    {
        NodeList nodes = evaluateXPath(doc, xpathExpr);
        if (nodes != null && nodes.getLength() > 0 && nodes.item(0) != null)
            return nodes.item(0).getTextContent();
        return null;
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
      Convenience helpers shared by all validators
      */

    /**
     * Records a successful validation step.
     *
     * <p>This helper creates a {@link FhirElementValidationItemSuccess} item to document
     * a passed validation check. It mirrors the success reporting approach used in
     * {@code FhirTaskValidator} and is designed for reuse in other validators.</p>
     *
     * @param file    the file being validated
     * @param ref     a string reference (e.g., resource URL)
     * @param message a message describing the successful validation
     * @return a success item to be added to the result list
     */
    protected FhirElementValidationItemSuccess ok(File file, String ref, String message)
    {
        return new FhirElementValidationItemSuccess(file, ref, message);
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
     * Extracts the effective value from a FHIR value[x] element.
     *
     * <p>This method handles both:</p>
     * <ul>
     *   <li><b>Primitive types</b> such as {@code valueString}, {@code valueBoolean}, etc., where the {@code value}
     *       attribute is located directly on the {@code value[x]} element.</li>
     *   <li><b>Complex types</b> such as {@code valueReference}, where the actual value (e.g., a {@code reference} string)
     *       is nested within a child element of the {@code valueReference} element.</li>
     * </ul>
     *
     * <p>Specifically:</p>
     * <ul>
     *   <li>If a child node starts with {@code value} (e.g., {@code valueString}) and has a {@code value} attribute,
     *       that attribute's content is returned.</li>
     *   <li>If the child node is {@code valueReference}, it attempts to extract the {@code reference} attribute
     *       from a nested {@code reference} element.</li>
     * </ul>
     *
     * @param node the parent node (typically an {@code input} or {@code output} element)
     * @return the extracted string value if present; {@code null} if no value could be found
     *
     * @see <a href="https://hl7.org/fhir/xml.html">FHIR XML Representation Rules (hl7.org)</a>
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

            // case 2 – Reference / CodableReference: <valueReference><reference value="..."/></valueReference>
            if ("valueReference".equals(k.getNodeName()))
            {
                String ref = extractSingleNodeValue(k, "./*[local-name()='reference']/@value");
                if (ref != null && !ref.isBlank()) return ref;
            }

            // case 3 – logical reference: <valueReference><identifier><value value="..."/></identifier>
            if ("valueReference".equals(k.getNodeName())) {
                String idValue = extractSingleNodeValue(
                        k, "./*[local-name()='identifier']/*[local-name()='value']/@value");
                if (idValue != null && !idValue.isBlank()) return idValue;
            }

            // case 4 – valueIdentifier: <valueIdentifier><value value="..."/></valueIdentifier>
            if ("valueIdentifier".equals(k.getNodeName())) {
                String idVal = extractSingleNodeValue(
                        k, "./*[local-name()='value']/@value");
                if (idVal != null && !idVal.isBlank()) return idVal;
            }

        }

        return null;
    }

}
