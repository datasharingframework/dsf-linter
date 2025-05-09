package dev.dsf.utils.validator.util;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h2>DSF CodeSystem Cache — v2</h2>
 * <p>A thread‑safe registry that tracks <i>all</i> codes that are relevant for the DSF core validators. 
 * Unlike the original stub, this version is fully dynamic: any CodeSystem can be registered at
 * runtime, and new codes can be merged in without restarting the validator.</p>
 *
 * <p><b>Design goals</b></p>
 * <ul>
 *   <li><b>Extensibility</b> – new CodeSystems & codes can be added via API calls.</li>
 *   <li><b>Thread‑safety</b> – uses {@link ConcurrentHashMap} and thread‑safe sets.</li>
 *   <li><b>One source of truth</b> – all look‑ups go through {@link #isKnown(String, String)}.</li>
 *   <li><b>Zero deprecations</b> – clean, stable API for future validators.</li>
 * </ul>
 *
 * <p><b>Usage</b></p>
 * <pre>{@code
 * // automatic seeding happens in the static block
 * FhirAuthorizationCache.isKnown("http://dsf.dev/fhir/CodeSystem/process-authorization", "LOCAL_ALL");
 *
 * // add a custom practitioner‑role at runtime
 * FhirAuthorizationCache.addCodes("http://dsf.dev/fhir/CodeSystem/practitioner-role",
 *                                 Set.of("BIOBANK_USER"));
 * }</pre>
 */
public final class FhirAuthorizationCache
{
    /*
      Constants
      */
    public static final String CS_PROCESS_AUTH   = "http://dsf.dev/fhir/CodeSystem/process-authorization";
    public static final String CS_READ_ACCESS    = "http://dsf.dev/fhir/CodeSystem/read-access-tag";
    public static final String CS_PRACT_ROLE     = "http://dsf.dev/fhir/CodeSystem/practitioner-role";
    public static final String CS_ORG_ROLE       = "http://dsf.dev/fhir/CodeSystem/organization-role";
    public static final String CS_RESOURCE_TYPE  = "http://dsf.dev/fhir/CodeSystem/resource-type";

    /*
      Storage
      */
    private static final Map<String, Set<String>> CODES_BY_SYSTEM = new ConcurrentHashMap<>();

    /*
     *  Bootstrap with official DSF 1.7 codes
      */
    static
    {
        // process‑authorization (core)
        register(CS_PROCESS_AUTH, Set.of(
                "LOCAL_ALL", "LOCAL_ALL_PRACTITIONER", "LOCAL_ORGANIZATION", "LOCAL_ORGANIZATION_PRACTITIONER",
                "LOCAL_ROLE", "LOCAL_ROLE_PRACTITIONER", "REMOTE_ALL", "REMOTE_ORGANIZATION", "REMOTE_ROLE"));

        // read‑access-tag (core)
        register(CS_READ_ACCESS, Set.of(
                "ALL", "LOCAL", "ORGANIZATION", "ROLE", "PRACTITIONER", "ROLE_PRACTITIONER"));

        // practitioner‑role (official DSF release v1.7)
        register(CS_PRACT_ROLE, Set.of(
                "DSF_ADMIN", "UAC_USER", "COS_USER", "CRR_USER", "DIC_USER", "DMS_USER", "DTS_USER",
                "HRP_USER", "TTP_USER", "AMS_USER", "ORGANIZATION_USER"));

        // organization‑role (official DSF release v1.7)
        register(CS_ORG_ROLE, Set.of(
                "COORDINATOR", "DATA_PROVIDER", "TTP", "RESEARCH_STUDY", "HRP", "QA"));
    }

    private FhirAuthorizationCache() { /* utility class */ }

    /*
     *  Public API
     *  */

    /**
     * Registers (or merges) a set of codes for the given CodeSystem URI.
     * If the system does not exist yet, it is created automatically.
     */
    public static void addCodes(String system, Set<String> codes)
    {
        if (system == null || codes == null || codes.isEmpty())
            return;

        CODES_BY_SYSTEM.computeIfAbsent(system, s -> ConcurrentHashMap.newKeySet())
                .addAll(codes);
    }

    /** Convenience wrapper for {@link #addCodes(String, Set)}. */
    public static void register(String system, Set<String> codes) { addCodes(system, codes); }

    /** Returns <code>true</code> iff the code is known for the given CodeSystem URI. */
    public static boolean isKnown(String system, String code)
    {
        if (system == null || code == null || code.isBlank())
            return false;
        Set<String> set = CODES_BY_SYSTEM.get(system);
        if (set != null)
            return set.contains(code);

        // special heuristic for resource‑type CodeSystem (code == FHIR resource name)
        if (CS_RESOURCE_TYPE.equals(system))
            return Character.isUpperCase(code.charAt(0));

        return false; // unknown system
    }

    /**
     * Exposes an <b>unmodifiable</b> snapshot of the codes currently cached for a system.
     * Returns an empty set if the system is unknown.
     */
    public static Set<String> getCodes(String system)
    {
        Set<String> s = CODES_BY_SYSTEM.get(system);
        return s != null ? Collections.unmodifiableSet(s) : Collections.emptySet();
    }

    /** Removes <strong>all</strong> codes for <em>all</em> systems (unit‑test helper). */
    public static void clearAll() { CODES_BY_SYSTEM.clear(); }
}
