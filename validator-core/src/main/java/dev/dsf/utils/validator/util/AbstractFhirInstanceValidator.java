package dev.dsf.utils.validator.util;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.*;
import java.io.File;
import java.util.List;

/**
 * Abstract base for FHIR resource validators. Provides helper methods for DOM parsing and placeholders checks.
 */
public abstract class AbstractFhirInstanceValidator
{
    /**
     * Checks if the validator can handle the given FHIR resource {@link Document} (by checking the root element).
     *
     * @param document the XML Document of the resource
     * @return true if this validator can handle it, false otherwise
     */
    public abstract boolean canValidate(Document document);

    /**
     * Performs the validation checks on the given FHIR resource Document.
     *
     * @param document the XML Document of the resource
     * @param resourceFile the file from which the document was loaded
     * @return list of issues found
     */
    public abstract List<?> validate(Document document, File resourceFile);

    /**
     * Evaluates an XPath expression on the given Document, returns a NodeList result (may be empty).
     * <p>
     * This version always starts from the root of the Document.
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
     * Evaluates an XPath expression on a specific Node, returning a NodeList result (may be empty).
     * <p>
     * This version is useful for sub-queries relative to a specific element.
     */
    protected NodeList evaluateXPath(Node node, String relativeExpr)
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
     * Extracts the string value of the first matching node from an XPath expression
     * evaluated on a {@link Document}.
     *
     * @param doc the XML Document
     * @param xpathExpr the XPath expression
     * @return the extracted text, or null if not found
     */
    protected String extractSingleNodeValue(Document doc, String xpathExpr)
    {
        NodeList nodes = evaluateXPath(doc, xpathExpr);
        if (nodes != null && nodes.getLength() > 0 && nodes.item(0) != null)
            return nodes.item(0).getTextContent();
        return null;
    }

    /**
     * Extracts the string value of the first matching node from an XPath expression
     * evaluated on a {@link Node}.
     *
     * @param node the Node
     * @param relativeExpr the XPath expression relative to that node
     * @return the extracted text, or null if not found
     */
    protected String extractSingleNodeValue(Node node, String relativeExpr)
    {
        NodeList nodes = evaluateXPath(node, relativeExpr);
        if (nodes != null && nodes.getLength() > 0 && nodes.item(0) != null)
            return nodes.item(0).getTextContent();
        return null;
    }
}