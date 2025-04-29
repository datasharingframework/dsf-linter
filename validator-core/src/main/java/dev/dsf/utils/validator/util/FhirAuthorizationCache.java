package dev.dsf.utils.validator.util;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h2>DSF CodeSystem Cache</h2>
 * Utility class that maintains an in‑memory, thread‑safe cache of codes taken from selected
 * <a href="https://dsf.dev/">Digital Square Framework</a> (DSF) CodeSystems.
 * <p>
 * The primary purpose is to support validation logic that checks whether a <em>code</em> occurring in an
 * ActivityDefinition (or any other FHIR resource) is allowed by the DSF specification.  At runtime the
 * cache is pre‑filled with <b>all</b> codes of the
 * {@code http://dsf.dev/fhir/CodeSystem/process-authorization} CodeSystem so that the validators can
 * operate even when no CodeSystem bundle has been parsed yet. Additional codes may be registered via
 * {@link #addCodes(Set)} — for example when the validator application loads a newer DSF release bundle
 * on start‑up.
 * </p>
 * <p>
 * <strong>Scope:</strong> Only CodeSystems that are required by the core FHIR validators live here.  BPMN‑specific
 * systems such as {@code bpmn-message} or {@code bpmn-task-profile} are validated by the BPMN layer and are
 * intentionally <em>not</em> covered by this cache.
 * </p>
 * <p>
 * This class is a pure static utility and therefore cannot be instantiated.
 * </p>
 */
public final class FhirAuthorizationCache
{
    /*
     * Static code sets (extend when DSF introduces new codes)
     */

    /** All requester/recipient codes as defined in DSF 1.0.0 <em>process‑authorization</em>. */
    private static final Set<String> PROCESS_AUTHORIZATION_CODES = Set.of(
            "LOCAL_ALL",
            "LOCAL_ALL_PRACTITIONER",
            "LOCAL_ORGANIZATION",
            "LOCAL_ORGANIZATION_PRACTITIONER",
            "LOCAL_ROLE",
            "LOCAL_ROLE_PRACTITIONER",
            "REMOTE_ALL",
            "REMOTE_ORGANIZATION",
            "REMOTE_ROLE"
    );

    /** Valid codes for the <em>read‑access‑tag</em> CodeSystem. */
    private static final Set<String> READ_ACCESS_TAG_CODES = Set.of(
            "ALL", "LOCAL", "ORGANIZATION", "ROLE", "PRACTITIONER", "ROLE_PRACTITIONER");

    /** Example practitioner‑role codes. Extend this set when your installation requires more. */
    private static final Set<String> PRACTITIONER_ROLE_CODES = Set.of("DSF_ADMIN", "ORGANIZATION_USER");

    /** Example organization‑role codes. Extend as needed. */
    private static final Set<String> ORGANIZATION_ROLE_CODES = Set.of("DATA_PROVIDER", "COORDINATOR");


    /**
     * Thread‑safe backing set for codes of <em>process‑authorization</em>.
     * Starts with the predefined codes and can be updated at runtime.
     */
    private static final Set<String> AUTH_CODES = ConcurrentHashMap.newKeySet();

    /* Static initializer: preload default codes exactly once. */
    static
    {
        AUTH_CODES.addAll(PROCESS_AUTHORIZATION_CODES);
    }

    /** Prevent instantiation. */
    private FhirAuthorizationCache()
    {
        throw new AssertionError("Utility class must not be instantiated");
    }

    /*
     * Public API
     */

    /**
     * Registers additional <em>process‑authorization</em> codes. Duplicate entries are ignored.
     *
     * @param codes the codes to add; {@code null} is ignored
     */
    public static void addCodes(Set<String> codes)
    {
        if (codes != null)
            AUTH_CODES.addAll(codes);
    }

    /**
     * Returns an unmodifiable snapshot of all cached <em>process‑authorization</em> codes. Intended for
     * diagnostic and unit‑test purposes.
     */
    @Deprecated
    public static Set<String> getCodes()
    {
        return Collections.unmodifiableSet(AUTH_CODES);
    }

    /**
     * True when the supplied code is contained in the <em>process‑authorization</em> cache.
     */
    public static boolean isKnownAuthorizationCode(String code)
    {
        return AUTH_CODES.contains(code);
    }

    public static boolean isKnownDsfCode(String system, String code)
    {
        if (system == null || code == null || code.isBlank())
            return false;

        return switch (system)
        {
            case "http://dsf.dev/fhir/CodeSystem/process-authorization" -> AUTH_CODES.contains(code);
            case "http://dsf.dev/fhir/CodeSystem/read-access-tag"       -> READ_ACCESS_TAG_CODES.contains(code);
            case "http://dsf.dev/fhir/CodeSystem/practitioner-role"     -> PRACTITIONER_ROLE_CODES.contains(code);
            case "http://dsf.dev/fhir/CodeSystem/organization-role"     -> ORGANIZATION_ROLE_CODES.contains(code);
            case "http://dsf.dev/fhir/CodeSystem/resource-type"         -> Character.isUpperCase(code.charAt(0));
            default                                                      -> false;
        };
    }


    @Deprecated
    public static List<String> getKnownDsfCodeSystems()
    {
        return List.of(
                "http://dsf.dev/fhir/CodeSystem/read-access-tag",
                "http://dsf.dev/fhir/CodeSystem/process-authorization",
                "http://dsf.dev/fhir/CodeSystem/practitioner-role",
                "http://dsf.dev/fhir/CodeSystem/organization-role",
                "http://dsf.dev/fhir/CodeSystem/resource-type"
        );
    }

    /**
     * Clears the runtime cache. <strong>Use only in unit tests!</strong> Production code should never empty
     * the cache after initialisation.
     */
    @Deprecated
    public static void clearCache()
    {
        AUTH_CODES.clear();
    }
}
