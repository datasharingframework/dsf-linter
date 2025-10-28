package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * Validation item indicating that a BPMN file referenced in the ProcessPluginDefinition
 * was found, but exists outside the expected resource root directory for this plugin.
 * <p>
 * This typically indicates:
 * <ul>
 *   <li>Incorrect project structure or resource organization</li>
 *   <li>Classpath pollution from other modules or dependencies</li>
 *   <li>Resource resolved from wrong plugin in multi-plugin projects</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 3.0.0
 */
public class PluginDefinitionBpmnFileReferencedFoundOutsideExpectedRootLintItem extends PluginLintItem {

    private final String expectedRoot;
    private final String actualLocation;

    /**
     * Constructs a validation item for a BPMN file found outside expected root.
     *
     * @param pluginName     The name of the plugin referencing the file
     * @param bpmnFile       The BPMN file that was found
     * @param reference      The original reference from plugin definition
     * @param expectedRoot   The expected resource root directory path
     * @param actualLocation The actual location where the file was found
     */
    public PluginDefinitionBpmnFileReferencedFoundOutsideExpectedRootLintItem(
            String pluginName,
            File bpmnFile,
            String reference,
            String expectedRoot,
            String actualLocation) {
        super(
                LinterSeverity.ERROR,
                bpmnFile,
                pluginName,
                formatMessage(reference, expectedRoot, actualLocation)
        );
        this.expectedRoot = expectedRoot;
        this.actualLocation = actualLocation;
    }

    /**
     * Formats the validation message.
     */
    private static String formatMessage(String reference, String expectedRoot, String actualLocation) {
        return String.format(
                """
                        BPMN file '%s' referenced by plugin but found outside expected resource root.
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