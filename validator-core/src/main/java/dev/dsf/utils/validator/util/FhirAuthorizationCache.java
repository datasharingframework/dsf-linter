package dev.dsf.utils.validator.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Caches the DSF authorization codes discovered from a local
 * CodeSystem resource with URL "http://dsf.dev/fhir/CodeSystem/process-authorization".
 * <p>
 * This class provides static methods to store and retrieve codes. It is not meant to be instantiated.
 * Hence, the private constructor throws an exception if someone tries to instantiate it.
 * </p>
 */
public class FhirAuthorizationCache
{
    private static final Set<String> AUTH_CODES = new HashSet<>();

    /**
     * Private constructor ensures the class cannot be instantiated.
     * If reflection somehow tries to create an instance, it will fail with an exception.
     */
    private FhirAuthorizationCache()
    {
        throw new AssertionError("Utility class FhirAuthorizationCache cannot be instantiated.");
    }

    /**
     * Adds one or more codes discovered from the DSF process-authorization CodeSystem.
     *
     * @param codes a set of codes to add to the authorization cache
     */
    public static void addCodes(Set<String> codes)
    {
        AUTH_CODES.addAll(codes);
    }

    /**
     * Returns an unmodifiable snapshot of the currently known DSF authorization codes.
     *
     * @return an unmodifiable set of all cached authorization codes
     */
    public static Set<String> getCodes()
    {
        return Collections.unmodifiableSet(AUTH_CODES);
    }

    /**
     * Checks if the provided {@code code} is known/valid from
     * the DSF process-authorization CodeSystem discovered so far.
     *
     * @param code the code to check
     * @return {@code true} if the code is in the cache; {@code false} otherwise
     */
    public static boolean isKnownAuthorizationCode(String code)
    {
        return AUTH_CODES.contains(code);
    }

    /**
     * Checks if the provided {@code code} for the given DSF CodeSystem is known.
     * <p>
     * This method supports multiple DSF CodeSystems such as:
     * <ul>
     *   <li>http://dsf.dev/fhir/CodeSystem/read-access-tag</li>
     *   <li>http://dsf.dev/fhir/CodeSystem/process-authorization</li>
     *   <li>http://dsf.dev/fhir/CodeSystem/practitioner-role</li>
     *   <li>http://dsf.dev/fhir/CodeSystem/bpmn-message</li>
     *   <li>http://dsf.dev/fhir/CodeSystem/bpmn-task-profile</li>
     *   <li>http://dsf.dev/fhir/CodeSystem/organization-role</li>
     *   <li>http://dsf.dev/fhir/CodeSystem/resource-type</li>
     * </ul>
     * </p>
     *
     * @param system the CodeSystem URL
     * @param code   the code to check
     * @return {@code true} if the code is known for the given CodeSystem, {@code false} otherwise.
     */
    public static boolean isKnownDsfCode(String system, String code)
    {
        if ("http://dsf.dev/fhir/CodeSystem/process-authorization".equals(system))
        {
            return AUTH_CODES.contains(code);
        }
        else if ("http://dsf.dev/fhir/CodeSystem/read-access-tag".equals(system))
        {
            // Known read-access-tag codes
            Set<String> knownReadAccessTagCodes = Set.of("ALL", "LOCAL", "ORGANIZATION", "ROLE", "PRACTITIONER", "ROLE_PRACTITIONER");
            return knownReadAccessTagCodes.contains(code);
        }
        else if ("http://dsf.dev/fhir/CodeSystem/practitioner-role".equals(system))
        {
            // Known practitioner role codes (that is an example, but we will improve it)
            Set<String> knownPractitionerRoleCodes = Set.of("DSF_ADMIN", "ORGANIZATION_USER");
            return knownPractitionerRoleCodes.contains(code);
        }
        else if ("http://dsf.dev/fhir/CodeSystem/bpmn-message".equals(system))
        {
            // Known BPMN message names (that is an example, but we will improve it)
            Set<String> knownBpmnMessages = Set.of("startPing", "pong");
            return knownBpmnMessages.contains(code);
        }
        else if ("http://dsf.dev/fhir/CodeSystem/bpmn-task-profile".equals(system))
        {
            return code != null && !code.isBlank();
        }
        else if ("http://dsf.dev/fhir/CodeSystem/organization-role".equals(system))
        {
            // Known organization roles (that is an example, but we will improve it)
            Set<String> knownOrganizationRoles = Set.of("DATA_PROVIDER", "COORDINATOR");
            return knownOrganizationRoles.contains(code);
        }
        else if ("http://dsf.dev/fhir/CodeSystem/resource-type".equals(system))
        {
            // Known resource types (that is an example, but we will improve it)
            Set<String> knownResourceTypes = Set.of("Task", "DocumentReference");
            return knownResourceTypes.contains(code);
        }
        // For unknown CodeSystems, return false.
        return false;
    }

    /**
     * Returns the list of known DSF CodeSystem URLs.
     *
     * @return a list of known DSF CodeSystem URLs.
     */
    public static List<String> getKnownDsfCodeSystems()
    {
        return List.of(
                "http://dsf.dev/fhir/CodeSystem/read-access-tag",
                "http://dsf.dev/fhir/CodeSystem/process-authorization",
                "http://dsf.dev/fhir/CodeSystem/practitioner-role",
                "http://dsf.dev/fhir/CodeSystem/bpmn-message",
                "http://dsf.dev/fhir/CodeSystem/bpmn-task-profile",
                "http://dsf.dev/fhir/CodeSystem/organization-role",
                "http://dsf.dev/fhir/CodeSystem/resource-type"
        );
    }

    /**
     * Clears all cached codes.
     * (Use with caution; typically only for testing or re-initialization.)
     */
    public static void clearCache()
    {
        AUTH_CODES.clear();
    }
}
