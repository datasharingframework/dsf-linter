package dev.dsf.linter.util;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility class for safely invoking reflective method calls on DSF plugin delegates.
 * This avoids code duplication for reflection-based resource retrieval.
 */
public final class ReflectionUtils
{
    private ReflectionUtils() { /* utility class */ }

    /**
     * Invokes the {@code getFhirResourcesByProcessId()} method reflectively on a given delegate.
     *
     * @param delegate      The instance whose method should be invoked
     * @param delegateClass The class of the delegate
     * @return Map of process IDs to lists of FHIR resource names (never {@code null})
     * @throws RuntimeException if the reflective call fails for any reason
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> getFhirResourcesByProcessId(Object delegate, Class<?> delegateClass)
    {
        try
        {
            Method m = delegateClass.getMethod("getFhirResourcesByProcessId");
            Map<String, List<String>> result = (Map<String, List<String>>) m.invoke(delegate);
            return result != null ? result : Collections.emptyMap();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error invoking getFhirResourcesByProcessId()", e);
        }
    }
}
