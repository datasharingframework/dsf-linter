package dev.dsf.linter.util.converter;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Converts FHIR JSON resources to XML.
 * The implementation is extracted from existing linters to avoid duplication.
 * All JavaDoc and comments are in English as requested.
 */
public final class JsonXmlConverter {

    private JsonXmlConverter() { /* utility class */ }

    /**
     * Converts a FHIR JSON resource to XML format.
     * This is a simplified conversion that handles the most common FHIR elements.
     */
    public static String convertJsonToXml(JsonNode jsonNode) throws Exception
    {
        if (!jsonNode.has("resourceType")) {
            throw new IllegalArgumentException("JSON does not appear to be a FHIR resource (missing resourceType)");
        }

        String resourceType = jsonNode.get("resourceType").asText();
        StringBuilder xml = new StringBuilder();

        // Add XML declaration and root element with FHIR namespace
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<").append(resourceType).append(" xmlns=\"http://hl7.org/fhir\">\n");

        // Convert each JSON property to XML elements
        jsonNode.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            // Skip resourceType as it's already used for the root element
            if (!"resourceType".equals(key)) {
                try {
                    convertJsonNodeToXml(key, value, xml, "  ");
                } catch (Exception e) {
                    System.err.println("[WARN] Error converting field " + key + ": " + e.getMessage());
                }
            }
        });

        xml.append("</").append(resourceType).append(">\n");
        return xml.toString();
    }

    /**
     * Recursively converts JSON nodes to XML elements.
     */
    static void convertJsonNodeToXml(String elementName, JsonNode node, StringBuilder xml, String indent) throws Exception
    {
        if (node.isNull()) {
            return; // Skip null values
        }

        // Special handling for embedded FHIR resources (objects with resourceType)
        if (node.isObject() && node.has("resourceType")) {
            String type = node.get("resourceType").asText();
            xml.append(indent).append("<").append(elementName).append(">\n"); // <resource>
            xml.append(indent).append("  <").append(type).append(">\n");      // <Patient>
            node.fields().forEachRemaining(e -> {
                if (!"resourceType".equals(e.getKey())) {
                    try {
                        convertJsonNodeToXml(e.getKey(), e.getValue(), xml, indent + "    ");
                    } catch (Exception ex) {
                        System.err.println("[WARN] Error converting nested resource field " + e.getKey() + ": " + ex.getMessage());
                    }
                }
            });
            xml.append(indent).append("  </").append(type).append(">\n");     // </Patient>
            xml.append(indent).append("</").append(elementName).append(">\n"); // </resource>
            return;
        }

        if (node.isValueNode()) {
            // Simple value: <element value="..."/>
            String value = node.asText();
            xml.append(indent).append("<").append(elementName).append(" value=\"")
                    .append(escapeXml(value)).append("\"/>\n");
        } else if (node.isArray()) {
            // Array: multiple elements with the same name
            for (JsonNode arrayItem : node) {
                convertJsonNodeToXml(elementName, arrayItem, xml, indent);
            }
        } else if (node.isObject()) {
            xml.append(indent).append("<").append(elementName);

            // Handle attributes first
            java.util.List<String> attributeKeys = java.util.Arrays.asList("id", "sliceName", "url");
            for (String attrKey : attributeKeys) {
                if (node.has(attrKey)) {
                    xml.append(" ").append(attrKey).append("=\"")
                            .append(escapeXml(node.get(attrKey).asText())).append("\"");
                }
            }

            // Check if we have child elements (excluding attributes)
            boolean hasChildren = false;
            java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                java.util.Map.Entry<String, JsonNode> entry = fields.next();
                if (!attributeKeys.contains(entry.getKey())) {
                    hasChildren = true;
                    break;
                }
            }

            if (!hasChildren) {
                xml.append("/>\n");
            } else {
                xml.append(">\n");

                // Process child elements (excluding attributes)
                node.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode value = entry.getValue();

                    if (!attributeKeys.contains(key)) {
                        try {
                            convertJsonNodeToXml(key, value, xml, indent + "  ");
                        } catch (Exception e) {
                            System.err.println("[WARN] Error converting nested field " + key + ": " + e.getMessage());
                        }
                    }
                });

                xml.append(indent).append("</").append(elementName).append(">\n");
            }
        }
    }

    /**
     * Escapes special XML characters in text content.
     * This method is made public to be used by other classes that need XML escaping.
     */
    public static String escapeXml(String text)
    {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
