package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * Lint Item indicating that a FHIR resource file referenced in the ProcessPluginDefinition
 * was found, but exists outside the expected resource root directory for this plugin.
 * <p>
 * This typically indicates:
 * <ul>
 *   <li>Incorrect project structure or resource organization</li>
 *   <li>Classpath pollution from other modules or dependencies</li>
 *   <li>Resource resolved from wrong plugin in multi-plugin projects</li>
 *   <li>Resource incorrectly loaded from dependency JAR</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 3.0.0
 */
public class PluginDefinitionFhirFileReferencedFoundOutsideExpectedRootLintItem extends PluginLintItem {

    private final String expectedRoot;
    private final String actualLocation;

    /**
     * Constructs a Lint Item for a FHIR file found outside expected root.
     *
     * @param pluginName     The name of the plugin referencing the file
     * @param fhirFile       The FHIR resource file that was found
     * @param reference      The original reference from plugin definition
     * @param expectedRoot   The expected resource root directory path
     * @param actualLocation The actual location where the file was found
     */
    public PluginDefinitionFhirFileReferencedFoundOutsideExpectedRootLintItem(
            String pluginName,
            File fhirFile,
            String reference,
            String expectedRoot,
            String actualLocation) {
        super(
                LinterSeverity.ERROR,
                fhirFile,
                pluginName,
                formatMessage(reference, expectedRoot, actualLocation)
        );
        this.expectedRoot = expectedRoot;
        this.actualLocation = actualLocation;
    }

    /**
     * Formats the linting message.
     */
    private static String formatMessage(String reference, String expectedRoot, String actualLocation) {
        return String.format(
                """
                        FHIR resource '%s' referenced by plugin but found outside expected resource root.
                          Expected root: %s
                          Actual location: %s
                        This indicates incorrect project structure or classpath pollution.""",
                reference, expectedRoot, actualLocation
        );
    }

    public String getExpectedRoot() {
        return expectedRoot;
    }

    public String getActualLocation() {
        return actualLocation;
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] %s (file=%s, pluginName=%s, expectedRoot=%s, actualLocation=%s)",
                getSeverity(),
                this.getClass().getSimpleName(),
                getFileName(),  // ← Use getFileName() for short path
                getLocation(),
                expectedRoot,
                actualLocation
        );
    }
}