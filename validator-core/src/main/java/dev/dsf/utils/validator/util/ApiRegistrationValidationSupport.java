package dev.dsf.utils.validator.util;

import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.item.MissingServiceLoaderRegistrationValidationItem;
import dev.dsf.utils.validator.item.PluginValidationItemSuccess;
import dev.dsf.utils.validator.item.AbstractValidationItem;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * <h2>ServiceLoader Registration Validator</h2>
 *
 * <p>
 * This utility class performs a validation check to ensure that a given DSF plugin module registers its
 * {@code ProcessPluginDefinition} implementation via the Java {@code ServiceLoader} mechanism. It uses
 * {@link ApiVersionDetector} to identify the API version and the registration location, and then emits
 * the appropriate validation result.
 * </p>
 *
 * <p>
 * The validation result is appended to the provided output list of {@link AbstractValidationItem}s.
 * Duplicate reports for the same plugin root directory and version are suppressed internally to avoid
 * redundant messages in multi-module or repeated validations.
 * </p>
 *
 * <h3>Validation Outcome</h3>
 * <ul>
 *   <li>If the ServiceLoader provider file is found, a {@link PluginValidationItemSuccess} is added to the output.</li>
 *   <li>If the provider file is missing, a {@link MissingServiceLoaderRegistrationValidationItem} is added instead.</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * This class is not thread-safe due to its use of a static {@link Set} to suppress duplicate messages.
 *
 * @see ApiVersionDetector
 * @see PluginValidationItemSuccess
 * @see MissingServiceLoaderRegistrationValidationItem
 */
public final class ApiRegistrationValidationSupport
{
    private final ApiVersionDetector detector = new ApiVersionDetector();
    private static final Set<String> reported = new HashSet<>();

    /**
     * Runs the ServiceLoader registration validation for a given plugin directory.
     *
     * <p>
     * This method checks if the provided plugin directory contains a valid ServiceLoader provider file
     * for {@code ProcessPluginDefinition}. If found, it reports a success item. If not found, it reports
     * an error item. Duplicate findings are suppressed based on a unique key derived from the validation
     * scope, path, and API version.
     * </p>
     *
     * @param scope a textual label to contextualize the validation scope (e.g., "Pre-build check")
     * @param root the root directory of the plugin project (e.g., exploded build directory)
     * @param out the list to which validation items are added
     */
    public void run(String scope, Path root, List<AbstractValidationItem> out) throws MissingServiceRegistrationException
    {
        String keyPrefix = scope + ":" + root.toAbsolutePath();

        var opt = detector.detect(root);
        if (opt.isPresent())
        {
            var dv = opt.get();
            String key = keyPrefix + ":" + dv.version() + ":" + dv.foundAt();
            if (reported.add(key))
            {
                out.add(new PluginValidationItemSuccess(
                        root.toFile(),
                        "META-INF/services",
                        scope + ": ServiceLoader registration OK (" + dv.version() + ") at " + dv.foundAt()));
                ApiVersionHolder.setVersion(dv.version());
            }
        }
        else
        {
            String key = keyPrefix + ":MISSING";
            if (reported.add(key))
            {
                out.add(new MissingServiceLoaderRegistrationValidationItem(
                        root.toFile(),
                        "META-INF/services",
                        scope + ": ServiceLoader provider file for ProcessPluginDefinition not found."));
            }
        }
    }

    /**
     * Clears the internal deduplication cache of reported validation results.
     *
     * <p>
     * This method is useful in testing scenarios or when performing multiple validation runs within
     * the same application lifecycle to ensure that all results are re-evaluated.
     * </p>
     */
    public static void clearReportedCache()
    {
        reported.clear();
    }
}
