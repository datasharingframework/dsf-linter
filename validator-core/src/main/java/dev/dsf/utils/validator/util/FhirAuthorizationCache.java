package dev.dsf.utils.validator.util;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <h2>FHIR CodeSystem Cache for DSF Validators – v3 (2025‑05)</h2>
 *
 * <p>
 * This class implements a centralized, thread-safe cache for known FHIR {@code CodeSystem} codes
 * used throughout the DSF (Data Sharing Framework) validation process. It is used by multiple
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
        return true;
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

    /**
     * Seeds the CodeSystem cache from both the project directory (disk) and the project classpath
     * (dependencies & plugin JAR). Keeps parsing logic centralized by materializing classpath resources
     * to temporary files.
     *
     * @param projectRoot the root of the project used to determine base traversal path and classpath setup
     */
    public static void seedFromProjectAndClasspath(File projectRoot)
    {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Set<File> allCodeSystemFiles = new LinkedHashSet<>(findCodeSystemsOnDisk(projectRoot));

        // 2) Classpath scan: fhir/CodeSystem/*.xml and *.json from dependency JARs or directories
        try {
            ClassLoader cl = BpmnValidationUtils.getOrCreateProjectClassLoader(projectRoot);
            allCodeSystemFiles.addAll(findCodeSystemsOnClasspath(cl));
        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("[CodeSystem-Cache] Failed to scan classpath: " + e.getMessage());
            }
            // keep going; disk results might still be sufficient
        }

        // 3) Feed everything through the same parse path you already use
        for (File cs : allCodeSystemFiles) {
            loadCodeSystemFile(cs);
        }

        if (DEBUG)
            dumpStatistics();
    }

    // ---- Helper methods ----

    /**
     * Returns CodeSystem files under typical project locations (no changes to your current logic).
     */
    private static Collection<File> findCodeSystemsOnDisk(File projectRoot)
    {
        List<String> candidates = List.of(
                "src/main/resources/fhir/CodeSystem",
                "target/classes/fhir/CodeSystem",
                "fhir/CodeSystem" // exploded plugin root case
        );
        List<File> out = new ArrayList<>();
        for (String dir : candidates) {
            File d = new File(projectRoot, dir);
            File[] xmls = d.isDirectory() ? d.listFiles(f -> f.isFile() && (f.getName().endsWith(".xml") || f.getName().endsWith(".json"))) : null;
            if (xmls != null) out.addAll(Arrays.asList(xmls));
        }
        return out;
    }

    /**
     * Finds CodeSystem XMLs and JSONs on the classpath under "fhir/CodeSystem" (both directories and JARs).
     */
    private static Collection<File> findCodeSystemsOnClasspath(ClassLoader cl) throws IOException
    {
        final String basePath = "fhir/CodeSystem";
        List<File> out = new ArrayList<>();

        // A) enumerate basePath URLs (dirs or inside JARs)
        Enumeration<URL> urls = cl.getResources(basePath);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String protocol = url.getProtocol();

            if ("file".equals(protocol)) {
                // Directory on classpath -> list *.xml and *.json
                File dir = new File(url.getPath());
                File[] files = dir.isDirectory() ? dir.listFiles(f -> f.isFile() &&
                        (f.getName().endsWith(".xml") || f.getName().endsWith(".json"))) : null;
                if (files != null) out.addAll(Arrays.asList(files));
            } else if ("jar".equals(protocol)) {
                // JAR -> iterate entries
                try {
                    JarURLConnection conn = (JarURLConnection) url.openConnection();
                    try (JarFile jar = conn.getJarFile()) {
                        for (JarEntry e : Collections.list(jar.entries())) {
                            if (!e.isDirectory()
                                    && e.getName().startsWith(basePath + "/")
                                    && (e.getName().endsWith(".xml") || e.getName().endsWith(".json"))) {
                                // materialize to temp file
                                String fileName = Paths.get(e.getName()).getFileName().toString();
                                Path tmp = Files.createTempFile("cs-", "-" + fileName);
                                try (InputStream in = jar.getInputStream(e)) {
                                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                                }
                                tmp.toFile().deleteOnExit();
                                out.add(tmp.toFile());
                            }
                        }
                    }
                } catch (IOException ioe) {
                    if (DEBUG) {
                        System.err.println("[CodeSystem-Cache] Failed to read JAR: " + ioe.getMessage());
                    }
                    // ignore this JAR and keep going
                }
            }
            // Note: removed problematic fallback logic that was always false
        }

        // B) defensive de-dup
        return out.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Single-file load hook that delegates to existing XML/JSON parsing logic.
     */
    private static void loadCodeSystemFile(File f)
    {
        String name = f.getName().toLowerCase();
        if (name.endsWith(".xml")) {
            loadSingleCodeSystem(f.toPath());
        } else if (name.endsWith(".json")) {
            loadJsonFile(f.toPath());
        }
    }

    /** True if we have any codes cached for this CodeSystem URL. */
    public static boolean containsSystem(String system) {
        return system != null && CODES_BY_SYSTEM.containsKey(system);
    }

    /** True if the given code is known under the given CodeSystem URL. */
    public static boolean isKnown(String system, String code) {
        if (system == null || code == null || code.isBlank()) return false;
        Set<String> known = CODES_BY_SYSTEM.get(system);
        return known != null && known.contains(code);
    }

    /** Return all CodeSystem URLs that contain the given code (used for false-URL diagnosis). */
    public static Set<String> findSystemsContainingCode(String code) {
        if (code == null || code.isBlank()) return Collections.emptySet();
        return CODES_BY_SYSTEM.entrySet().stream()
                .filter(e -> e.getValue().contains(code))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
