package dev.dsf.linter.util.linting;

import static dev.dsf.linter.constants.DsfApiConstants.V1_PLUGIN_INTERFACE;
import static dev.dsf.linter.constants.DsfApiConstants.V2_PLUGIN_INTERFACE;

public final class PluginLintingUtils {

    private PluginLintingUtils() {
        // Utility class
    }

    /**
     * Checks if the given class implements either v1 or v2 ProcessPluginDefinition interface.
     *
     * @param candidateClass the class to check
     * @param classLoader the classloader to use for loading interfaces
     * @return true if the class implements one of the interfaces
     */
    public static boolean implementsProcessPluginDefinition(Class<?> candidateClass, ClassLoader classLoader) {
        try {
            if (Class.forName(V2_PLUGIN_INTERFACE, false, classLoader).isAssignableFrom(candidateClass)) {
                return true;
            }
        } catch (ClassNotFoundException ignored) {
            // V2 not available, try V1
        }

        try {
            if (Class.forName(V1_PLUGIN_INTERFACE, false, classLoader).isAssignableFrom(candidateClass)) {
                return true;
            }
        } catch (ClassNotFoundException ignored) {
            // V1 not available either
        }

        return false;
    }

    /**
     * Verifies that the class has all required plugin method signatures.
     *
     * @param candidateClass the class to check
     * @return true if all required methods are present
     */
    public static boolean hasPluginSignature(Class<?> candidateClass) {
        try {
            candidateClass.getMethod("getName");
            candidateClass.getMethod("getProcessModels");
            candidateClass.getMethod("getFhirResourcesByProcessId");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Determines if the given class is assignable to the V2 ProcessPluginDefinition interface.
     *
     * @param candidateClass the class to check
     * @param classLoader the classloader to resolve the DSF API interface
     * @return true if the class implements the V2 interface
     */
    public static boolean isV2Plugin(Class<?> candidateClass, ClassLoader classLoader) {
        try {
            return Class.forName(V2_PLUGIN_INTERFACE, false, classLoader).isAssignableFrom(candidateClass);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Determines if the given class is assignable to the V1 ProcessPluginDefinition interface.
     *
     * @param candidateClass the class to check
     * @param classLoader the classloader to resolve the DSF API interface
     * @return true if the class implements the V1 interface
     */
    public static boolean isV1Plugin(Class<?> candidateClass, ClassLoader classLoader) {
        try {
            return Class.forName(V1_PLUGIN_INTERFACE, false, classLoader).isAssignableFrom(candidateClass);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}