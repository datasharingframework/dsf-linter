package dev.dsf.utils.validator.util;

import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.item.MissingServiceLoaderRegistrationValidationItem;
import dev.dsf.utils.validator.item.PluginValidationItemSuccess;
import dev.dsf.utils.validator.item.AbstractValidationItem;

import java.nio.file.Path;
import java.util.List;

/**
 * Wires ApiVersionDetector to validation items.:
 * - emits a success item if ServiceLoader registration is found;
 * - emits an error item if registration is missing.
 */
public final class ApiRegistrationValidationSupport {

    private ApiRegistrationValidationSupport() { /* utility */ }

    /**
     * Adds validation items based on ServiceLoader registration presence.
     *
     * @param root project/plugin root to scan
     * @param out  collector for validation items
     */
    public static void validate(Path root, List<AbstractValidationItem> out) {
        ApiVersionDetector detector = new ApiVersionDetector();
        try {
            String version = detector.detectOrThrow(root);
            out.add(new PluginValidationItemSuccess(
                    root.toFile(),
                    "META-INF/services",
                    "ServiceLoader registration found for ProcessPluginDefinition (" + version + ")."));
        } catch (MissingServiceRegistrationException e) {
            out.add(new MissingServiceLoaderRegistrationValidationItem(
                    root.toFile(),
                    "META-INF/services",
                    e.getMessage()));
        }
    }
}
