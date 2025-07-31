package dev.dsf.utils.validator.util;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <h2>FHIR CodeSystem Cache for DSF Validators – v3 (2025‑05)</h2>
 *
 * <p>
 * This class implements a centralized, thread-safe cache for known FHIR {@code CodeSystem} codes
 * used throughout the DSF (Digital Sample Framework) validation process. It is used by multiple
 * validators to verify whether a referenced {@code Coding} is declared and recognized.
 * </p>
 *
 * <h3>Main Responsibilities</h3>
 * <ul>
 *   <li>Registers core DSF {@code CodeSystem} entries at startup</li>
 *   <li>Dynamically discovers additional {@code CodeSystem} definitions by scanning
 *       {@code src/main/resources/fhir/CodeSystem} directories</li>
 *   <li>Supports fast lookup of known codes per system for validation use cases</li>
 *   <li>Offers utilities to register, clear, and debug the contents of the cache</li>
 *   <li>Loads both XML and JSON CodeSystem definitions</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * <p>
 * Internally, the class uses a {@link ConcurrentHashMap} with thread-safe sets to store code
 * values per system. It is safe to use concurrently across threads during validation.
 * </p>
 *
 * <h3>Debugging</h3>
 * <p>
 * If the system property {@code -Ddsf.debug.codesystem=true} is set, the class prints detailed
 * output to {@code System.out} during loading and summarization.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * FhirAuthorizationCache.seedFromProjectFolder(projectDir);
 * if (FhirAuthorizationCache.isUnknown("http://dsf.dev/fhir/CodeSystem/practitioner-role", "HRP_USER")) {
 *     // handle missing code
 * }
 * }</pre>
 *
 */
public final class FhirAuthorizationCache
{
    /**
     * DSF core CodeSystem URI for process authorizations.
     */
    public static final String CS_PROCESS_AUTH = "http://dsf.dev/fhir/CodeSystem/process-authorization";

    /**
     * DSF core CodeSystem URI for read access tags.
     */
    public static final String CS_READ_ACCESS = "http://dsf.dev/fhir/CodeSystem/read-access-tag";

    /**
     * DSF core CodeSystem URI for practitioner roles.
     */
    public static final String CS_PRACT_ROLE = "http://dsf.dev/fhir/CodeSystem/practitioner-role";

    /**
     * DSF core CodeSystem URI for organization roles.
     */
    public static final String CS_ORG_ROLE = "http://dsf.dev/fhir/CodeSystem/organization-role";

    /**
     * DSF CodeSystem URI for FHIR resource types (heuristic check).
     */
    public static final String CS_RESOURCE_TYPE = "http://dsf.dev/fhir/CodeSystem/resource-type";

    /**
     * FHIR Task URI for Task status values.
     */
    public static final String CS_TASK_STATUS = "http://hl7.org/fhir/task-status";

    private static final boolean DEBUG = Boolean.getBoolean("dsf.debug.codesystem");

    private static final Map<String, Set<String>> CODES_BY_SYSTEM = new ConcurrentHashMap<>();

    static
    {
        // Register official DSF codes (release v1.7)
        register(CS_PROCESS_AUTH, Set.of(
                "LOCAL_ALL", "LOCAL_ALL_PRACTITIONER", "LOCAL_ORGANIZATION",
                "LOCAL_ORGANIZATION_PRACTITIONER", "LOCAL_ROLE", "LOCAL_ROLE_PRACTITIONER",
                "REMOTE_ALL", "REMOTE_ORGANIZATION", "REMOTE_ROLE"));

        register(CS_READ_ACCESS, Set.of(
                "ALL", "LOCAL", "ORGANIZATION", "ROLE"));

        register(CS_PRACT_ROLE, Set.of(
                "DSF_ADMIN", "UAC_USER", "COS_USER", "CRR_USER", "DIC_USER", "DMS_USER",
                "DTS_USER", "HRP_USER", "TTP_USER", "AMS_USER", "ORGANIZATION_USER"));

        register(CS_ORG_ROLE, Set.of(
                "COORDINATOR", "DATA_PROVIDER", "TTP", "RESEARCH_STUDY", "HRP", "QA"));

        register(CS_ORG_ROLE, Set.of( "UAC", "COS", "CRR", "DIC", "DMS", "DTS", "HRP",
                "TTP", "AMS"));

        register(CS_TASK_STATUS, Set.of(
                "draft", "requested", "received", "accepted", "rejected", "ready",
                "cancelled", "in-progress", "on-hold", "failed", "completed", "entered-in-error"));

    }

    private FhirAuthorizationCache() { /* Utility class – no instantiation */ }

    /**
     * Registers or merges a given set of codes under the specified CodeSystem URI.
     * If the system does not exist yet, it will be initialized automatically.
     *
     * @param system the CodeSystem URI
     * @param codes  the set of code strings to register
     */
    public static void register(String system, Set<String> codes)
    {
        if (system == null || codes == null || codes.isEmpty()) return;
        CODES_BY_SYSTEM.computeIfAbsent(system, s -> ConcurrentHashMap.newKeySet())
                .addAll(codes);
    }

    /**
     * Determines whether a code is unknown for a given CodeSystem URI.
     * This method returns {@code true} only if the code is not registered
     * (i.e., is invalid or unsupported).
     *
     * @param system the CodeSystem URI
     * @param code   the code string to check
     * @return {@code true} if the code is unknown for the system; otherwise {@code false}
     */
    public static boolean isUnknown(String system, String code)
    {
        if (system == null || code == null || code.isBlank()) return false;
        Set<String> known = CODES_BY_SYSTEM.get(system);
        if (known != null) return !known.contains(code);
        if (CS_RESOURCE_TYPE.equals(system))
            return !Character.isUpperCase(code.charAt(0)); // heuristic
        return true;
    }

    /**
     * Returns an unmodifiable snapshot of all known codes for the specified CodeSystem.
     *
     * @param system the CodeSystem URI
     * @return a set of codes; empty if none are registered
     */
    public static Set<String> getCodes(String system)
    {
        return CODES_BY_SYSTEM.getOrDefault(system, Collections.emptySet());
    }

    /**
     * Clears all cached codes from all CodeSystems.
     * Primarily intended for unit testing.
     */
    public static void clearAll()
    {
        CODES_BY_SYSTEM.clear();
    }

    /**
     * Recursively scans the given project root directory for directories named
     * {@code src/main/resources/fhir/CodeSystem} or {@code fhir/CodeSystem} and loads
     * all {@code *.xml} and {@code *.json} files found within them.
     *
     * <p>
     * This method supports both Maven-style resource layouts and flat directory
     * structures as commonly produced by exploded plugin JARs in CI pipelines.
     * </p>
     *
     * <p>
     * All discovered CodeSystem definitions will be parsed and their system/code
     * pairs registered in the internal cache for later use during FHIR validation.
     * </p>
     *
     * <p>
     * This method must be called before executing any validation that relies on known
     * {@code CodeSystem} code values (e.g., to check {@code Coding.code} or {@code ValueSet} references).
     * </p>
     *
     * @param projectRoot the root of the project or a file within it; used to determine base traversal path
     */

    public static void seedFromProjectFolder(File projectRoot)
    {
        if (projectRoot == null)
            return;

        if (!projectRoot.isDirectory()) {
            projectRoot = projectRoot.getParentFile();
            if (projectRoot == null || !projectRoot.isDirectory())
                return;
        }

        final Path MAVEN_LAYOUT = Paths.get("src", "main", "resources", "fhir", "CodeSystem");
        final Path FLAT_LAYOUT  = Paths.get("fhir", "CodeSystem");   // fallback for exploded JARs

        try (Stream<Path> walk = Files.walk(projectRoot.toPath()))
        {
            walk.filter(Files::isDirectory) // cheap test first
                    .filter(p -> p.endsWith(MAVEN_LAYOUT) || p.endsWith(FLAT_LAYOUT))
                    .forEach(FhirAuthorizationCache::loadAllFhirInDirectory);
        }
        catch (IOException e)
        {
            System.err.println("[CodeSystem-Cache] Failed to walk project tree: " + e.getMessage());
        }

        if (DEBUG)
            dumpStatistics();
    }

    /**
     * Loads all FHIR CodeSystem files (XML and JSON) in the specified directory and registers any valid CodeSystems.
     *
     * @param dir the directory containing {@code CodeSystem} XML and JSON files
     */
    private static void loadAllFhirInDirectory(Path dir)
    {
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString().toLowerCase();
                if (name.endsWith(".xml")) {
                    loadSingleCodeSystem(p);
                } else if (name.endsWith(".json")) {
                    loadJsonFile(p);
                }
            });
        }
        catch (IOException ignore) { /* directory not readable */ }
    }

    /**
     * Parses a single {@code CodeSystem} XML file and registers its system and code elements
     * if valid and well-formed.
     *
     * @param xml the path to the XML file
     */
    private static void loadSingleCodeSystem(Path xml)
    {
        try (FileInputStream fis = new FileInputStream(xml.toFile()))
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(fis);

            if (!"CodeSystem".equals(doc.getDocumentElement().getLocalName())) return;

            String system = (String) XPathFactory.newInstance().newXPath()
                    .compile("/*[local-name()='CodeSystem']/*[local-name()='url']/@value")
                    .evaluate(doc, XPathConstants.STRING);
            if (system == null || system.isBlank()) return;

            NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                    .compile("/*[local-name()='CodeSystem']/*[local-name()='concept']/*[local-name()='code']/@value")
                    .evaluate(doc, XPathConstants.NODESET);
            if (nodes == null || nodes.getLength() == 0) return;

            Set<String> codes = ConcurrentHashMap.newKeySet(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++)
                codes.add(nodes.item(i).getTextContent());

            register(system, codes);

            if (DEBUG)
                System.out.printf("[Cache‑DEBUG] %s → %s (%,d codes)%n",
                        xml.getFileName(), system, codes.size());
        }
        catch (Exception ignore) { /* invalid or non-parsable XML */ }
    }

    /**
     * Parses a single {@code CodeSystem} JSON file and registers its system and code elements
     * if valid and well-formed.
     *
     * @param json the path to the JSON file
     */
    private static void loadJsonFile(Path json)
    {
        try (InputStream in = Files.newInputStream(json)) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(in);

            if (root == null || !"CodeSystem".equals(root.path("resourceType").asText())) return;

            String systemUrl = root.path("url").asText();
            if (systemUrl == null || systemUrl.isBlank()) return;

            // Collect all codes from concept (including nested concepts)
            Set<String> codes = new HashSet<>();
            collectJsonConceptCodes(root.path("concept"), codes);

            if (!codes.isEmpty()) {
                register(systemUrl, codes);

                if (DEBUG) {
                    System.out.printf("[Cache‑DEBUG] JSON loaded %s → %s (%,d codes)%n",
                            json.getFileName(), systemUrl, codes.size());
                }
            }
        }
        catch (Exception e) {
            if (DEBUG) {
                System.err.println("[CodeSystem-Cache] Failed to parse JSON " + json + ": " + e.getMessage());
            }
        }
    }

    /**
     * Recursively extracts code values from JSON concept nodes.
     *
     * @param node the JSON node containing concept definitions
     * @param out  the set to collect code values into
     */
    private static void collectJsonConceptCodes(JsonNode node, Set<String> out)
    {
        if (node == null || node.isNull()) return;

        if (node.isArray()) {
            for (JsonNode concept : node) {
                JsonNode code = concept.get("code");
                if (code != null && code.isValueNode() && !code.asText().isBlank()) {
                    out.add(code.asText());
                }
                // Recurse into nested concepts, if present
                collectJsonConceptCodes(concept.get("concept"), out);
            }
        }
    }

    /**
     * Outputs the current CodeSystem cache statistics to the console.
     * Only visible if debug logging is enabled via system property.
     */
    private static void dumpStatistics()
    {
        System.out.println("=== CodeSystem cache (summary) ===");
        CODES_BY_SYSTEM.forEach((sys, set) ->
                System.out.printf(" • %s → %,d code(s)%n", sys, set.size()));
        System.out.println("==================================");
    }
}