package dev.dsf.linter.util.resource;

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

import dev.dsf.linter.logger.Logger;

import static dev.dsf.linter.classloading.ProjectClassLoaderFactory.getOrCreateProjectClassLoader;


/**
 * <h2>FHIR CodeSystem Cache for DSF linters – v1 (2025‑05)</h2>
 *
 * <p>
 * This class implements a centralized, thread-safe cache for known FHIR {@code CodeSystem} codes
 * used throughout the DSF (Data Sharing Framework) linting process. It is used by multiple
 * linters to verify whether a referenced {@code Coding} is declared and recognized.
 * </p>
 *
 * <h3>Main Responsibilities</h3>
 * <ul>
 *   <li>Registers core DSF {@code CodeSystem} entries at startup</li>
 *   <li>Dynamically discovers additional {@code CodeSystem} definitions by scanning
 *       {@code src/main/resources/fhir/CodeSystem} directories</li>
 *   <li>Supports fast lookup of known codes per system for linting use cases</li>
 *   <li>Offers utilities to register, clear, and debug the contents of the cache</li>
 *   <li>Loads both XML and JSON CodeSystem definitions</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * <p>
 * Internally, the class uses a {@link ConcurrentHashMap} with thread-safe sets to store code
 * values per system. It is safe to use concurrently across threads during linting.
 * </p>
 *
 * <h3>Debugging</h3>
 * <p>
 * Debug output is controlled via the {@code --verbose} option passed to the linter.
 * When verbose mode is enabled, the class prints detailed output during loading and summarization.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * FhirAuthorizationCache.setLogger(logger);
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
     * DSF core CodeSystem URI for BPMN message slices ({@code message-name}, {@code business-key},
     * {@code correlation-key}).
     */
    public static final String CS_BPMN_MESSAGE = "http://dsf.dev/fhir/CodeSystem/bpmn-message";

    /**
     * FHIR Task URI for Task status values.
     */
    public static final String CS_TASK_STATUS = "http://hl7.org/fhir/task-status";

    private static Logger logger;

    private static final Map<String, Set<String>> CODES_BY_SYSTEM = new ConcurrentHashMap<>();

    /**
     * Set of CodeSystem URIs that are referenced by at least one known ValueSet's
     * {@code compose.include.system} entry. Populated during
     * {@link #seedFromProjectAndClasspath(File)}.
     */
    private static final Set<String> SYSTEMS_IN_VALUE_SETS = ConcurrentHashMap.newKeySet();

    /**
     * Index of known ValueSets keyed by their canonical {@code url}.
     * Each entry maps to the set of {@code compose.include.system} URIs
     * declared by that ValueSet. Populated during
     * {@link #seedFromProjectAndClasspath(File)}.
     *
     * <p>Used for <strong>binding-driven</strong> terminology checks, e.g.,
     * verifying that a {@code Task.input.type.coding.system} is allowed by the
     * specific ValueSet referenced in a profile's {@code binding.valueSet}.</p>
     */
    private static final Map<String, Set<String>> SYSTEMS_PER_VALUE_SET = new ConcurrentHashMap<>();

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

        register(CS_BPMN_MESSAGE, Set.of("message-name", "business-key", "correlation-key"));

        // The bpmn-message CodeSystem is always included in DSF's well-known ValueSets
        SYSTEMS_IN_VALUE_SETS.add(CS_BPMN_MESSAGE);
    }

    private FhirAuthorizationCache() { /* Utility class – no instantiation */ }

    /**
     * Sets the logger instance used for debug output.
     * This method must be called before {@link #seedFromProjectAndClasspath(File)}.
     *
     * @param logger the logger instance to use
     */
    public static void setLogger(Logger logger) {
        FhirAuthorizationCache.logger = logger;
    }

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

            logger.debug(String.format("[Cache-DEBUG] %s → %s (%,d codes)",
                        xml.getFileName(), system, codes.size()));
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

                logger.debug(String.format("[Cache-DEBUG] JSON loaded %s → %s (%,d codes)",
                        json.getFileName(), systemUrl, codes.size()));
            }
        }
        catch (Exception e) {
                logger.debug("[CodeSystem-Cache] Failed to parse JSON " + json + ": " + e.getMessage());
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
     * Outputs the current CodeSystem cache statistics to the logger.
     * Only visible if verbose logging is enabled.
     * Shows both code count and individual code values for each system.
     */
    private static void dumpStatistics()
    {
        logger.debug("=== CodeSystem cache (summary) ===");
        CODES_BY_SYSTEM.forEach((sys, set) -> {
            logger.debug(String.format(" • %s → %,d code(s)", sys, set.size()));
            set.forEach(code -> logger.debug("   - " + code));
        });
        logger.debug("==================================");
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

        // ---- CodeSystem seeding ----
        Set<File> allCodeSystemFiles = new LinkedHashSet<>(findCodeSystemsOnDisk(projectRoot));

        // 2) Classpath scan: fhir/CodeSystem/*.xml and *.json from dependency JARs or directories
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            allCodeSystemFiles.addAll(findResourcesOnClasspath(cl, "fhir/CodeSystem"));
        } catch (Exception e) {
            logger.debug("[CodeSystem-Cache] Failed to scan classpath: " + e.getMessage());
            // keep going; disk results might still be sufficient
        }

        // 3) Feed everything through the same parse path you already use
        for (File cs : allCodeSystemFiles) {
            loadCodeSystemFile(cs);
        }

        // ---- ValueSet seeding (compose.include.system → SYSTEMS_IN_VALUE_SETS) ----
        Set<File> allValueSetFiles = new LinkedHashSet<>(findValueSetsOnDisk(projectRoot));
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            allValueSetFiles.addAll(findResourcesOnClasspath(cl, "fhir/ValueSet"));
        } catch (Exception e) {
            logger.debug("[ValueSet-Cache] Failed to scan classpath: " + e.getMessage());
        }
        for (File vs : allValueSetFiles) {
            loadValueSetFile(vs);
        }

        dumpStatistics();
    }

    // ---- Helper methods ----

    /**
     * Returns CodeSystem files under typical project locations.
     */
    private static Collection<File> findCodeSystemsOnDisk(File projectRoot) {
        return findFhirResourcesOnDisk(projectRoot, List.of(
                "src/main/resources/fhir/CodeSystem",
                "target/classes/fhir/CodeSystem",
                "fhir/CodeSystem"));
    }

    /**
     * Returns ValueSet files under typical project locations.
     */
    private static Collection<File> findValueSetsOnDisk(File projectRoot) {
        return findFhirResourcesOnDisk(projectRoot, List.of(
                "src/main/resources/fhir/ValueSet",
                "target/classes/fhir/ValueSet",
                "fhir/ValueSet"));
    }

    /**
     * Generic disk scanner: lists {@code .xml} and {@code .json} files under the given
     * relative subdirectories of {@code projectRoot}.
     */
    private static Collection<File> findFhirResourcesOnDisk(File projectRoot, List<String> candidates) {
        List<File> out = new ArrayList<>();
        for (String dir : candidates) {
            File d = new File(projectRoot, dir);
            File[] files = d.isDirectory()
                    ? d.listFiles(f -> f.isFile() && (f.getName().endsWith(".xml") || f.getName().endsWith(".json")))
                    : null;
            if (files != null) out.addAll(Arrays.asList(files));
        }
        return out;
    }

    /**
     * Finds FHIR resource files on the classpath under {@code basePath}
     * (both directories and JARs), materializing JAR entries to temp files.
     */
    private static Collection<File> findResourcesOnClasspath(ClassLoader cl, String basePath) throws IOException
    {
        List<File> out = new ArrayList<>();

        // A) enumerate basePath URLs (dirs or inside JARs)
        Enumeration<URL> urls = cl.getResources(basePath);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String protocol = url.getProtocol();

            if ("file".equals(protocol)) {
                // Directory on classpath -> list *.xml and *.json
                File dir = new File(url.getPath());
                File[] files = dir.isDirectory()
                        ? dir.listFiles(f -> f.isFile() && (f.getName().endsWith(".xml") || f.getName().endsWith(".json")))
                        : null;
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
                                Path tmp = Files.createTempFile("fhir-", "-" + fileName);
                                try (InputStream in = jar.getInputStream(e)) {
                                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                                }
                                tmp.toFile().deleteOnExit();
                                out.add(tmp.toFile());
                            }
                        }
                    }
                } catch (IOException ioe) {
                    logger.debug("[FHIR-Cache] Failed to read JAR (" + basePath + "): " + ioe.getMessage());
                }
            }
        }

        // B) defensive de-dup
        return out.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Parses a ValueSet file (XML or JSON) and registers each
     * {@code compose.include.system} URI into {@link #SYSTEMS_IN_VALUE_SETS}.
     */
    private static void loadValueSetFile(File f) {
        String name = f.getName().toLowerCase();
        if (name.endsWith(".xml")) loadValueSetXml(f.toPath());
        else if (name.endsWith(".json")) loadValueSetJson(f.toPath());
    }

    private static void loadValueSetXml(Path xml) {
        try (FileInputStream fis = new FileInputStream(xml.toFile())) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(fis);
            if (!"ValueSet".equals(doc.getDocumentElement().getLocalName())) return;

            String vsUrl = (String) XPathFactory.newInstance().newXPath()
                    .compile("/*[local-name()='ValueSet']/*[local-name()='url']/@value")
                    .evaluate(doc, XPathConstants.STRING);

            NodeList systems = (NodeList) XPathFactory.newInstance().newXPath()
                    .compile("/*[local-name()='ValueSet']/*[local-name()='compose']" +
                             "/*[local-name()='include']/*[local-name()='system']/@value")
                    .evaluate(doc, XPathConstants.NODESET);
            if (systems == null) return;
            for (int i = 0; i < systems.getLength(); i++) {
                String sys = systems.item(i).getTextContent();
                registerValueSetSystem(sys);
                indexValueSetSystem(vsUrl, sys);
                logger.debug("[Cache-DEBUG] ValueSet " + xml.getFileName() + " (" + vsUrl + ") includes system: " + sys);
            }
        } catch (Exception ignore) { /* invalid or non-parsable file */ }
    }

    private static void loadValueSetJson(Path json) {
        try (InputStream in = Files.newInputStream(json)) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(in);
            if (root == null || !"ValueSet".equals(root.path("resourceType").asText())) return;
            String vsUrl = root.path("url").asText(null);
            com.fasterxml.jackson.databind.JsonNode includes = root.path("compose").path("include");
            if (includes.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode inc : includes) {
                    String sys = inc.path("system").asText(null);
                    if (sys != null && !sys.isBlank()) {
                        registerValueSetSystem(sys);
                        indexValueSetSystem(vsUrl, sys);
                        logger.debug("[Cache-DEBUG] ValueSet " + json.getFileName() + " (" + vsUrl + ") includes system: " + sys);
                    }
                }
            }
        } catch (Exception ignore) { /* invalid or non-parsable file */ }
    }

    /**
     * Indexes a single {@code compose.include.system} URI under the given ValueSet canonical URL.
     *
     * @param vsUrl canonical URL of the ValueSet (may be {@code null} or blank; in that case the entry is skipped)
     * @param system CodeSystem URI referenced in {@code compose.include.system}
     */
    private static void indexValueSetSystem(String vsUrl, String system) {
        if (vsUrl == null || vsUrl.isBlank() || system == null || system.isBlank()) return;
        SYSTEMS_PER_VALUE_SET
                .computeIfAbsent(vsUrl, k -> ConcurrentHashMap.newKeySet())
                .add(system);
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

    // ---- ValueSet system tracking ----

    /**
     * Registers a CodeSystem URI as being referenced by a known ValueSet.
     * Called during ValueSet scanning in {@link #seedFromProjectAndClasspath(File)}.
     *
     * @param system the CodeSystem URI referenced in a ValueSet's {@code compose.include.system}
     */
    public static void registerValueSetSystem(String system) {
        if (system != null && !system.isBlank())
            SYSTEMS_IN_VALUE_SETS.add(system);
    }

    /**
     * Returns {@code true} if the given CodeSystem URI is referenced by at least one
     * known ValueSet's {@code compose.include.system}.
     *
     * @param system the CodeSystem URI to check
     * @return {@code true} if the system is included in at least one known ValueSet
     */
    public static boolean isSystemInAnyValueSet(String system) {
        return system != null && SYSTEMS_IN_VALUE_SETS.contains(system);
    }

    /**
     * Returns {@code true} if a ValueSet with the given canonical URL has been loaded.
     * Version suffixes (everything after {@code |}) are stripped before comparison.
     *
     * @param valueSetUrl canonical URL of the ValueSet, optionally versioned ({@code url|version})
     * @return {@code true} if the ValueSet is known to the cache
     */
    public static boolean isValueSetLoaded(String valueSetUrl) {
        if (valueSetUrl == null || valueSetUrl.isBlank()) return false;
        return SYSTEMS_PER_VALUE_SET.containsKey(stripVersion(valueSetUrl));
    }

    /**
     * Returns the set of CodeSystem URIs referenced by the given ValueSet's
     * {@code compose.include.system} entries. Version suffixes on {@code valueSetUrl}
     * are stripped before lookup.
     *
     * @param valueSetUrl canonical URL of the ValueSet, optionally versioned ({@code url|version})
     * @return an immutable view of the referenced system URIs; empty if the ValueSet is not loaded
     */
    public static Set<String> getSystemsInValueSet(String valueSetUrl) {
        if (valueSetUrl == null || valueSetUrl.isBlank()) return Collections.emptySet();
        Set<String> s = SYSTEMS_PER_VALUE_SET.get(stripVersion(valueSetUrl));
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
    }

    private static String stripVersion(String canonical) {
        int pipe = canonical.indexOf('|');
        return pipe >= 0 ? canonical.substring(0, pipe) : canonical;
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