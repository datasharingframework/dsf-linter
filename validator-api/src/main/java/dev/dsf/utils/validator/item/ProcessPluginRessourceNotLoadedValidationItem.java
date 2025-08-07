package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import java.io.File;

/**
 * Validation item indicating that a plugin resource located in the repository
 * is NOT referenced by the ProcessPluginDefinition and therefore was not loaded/validated.
 * <p>
 * Severity: {@link ValidationSeverity#WARN}. This item is intended for reporting only
 * and must not abort the validation run.
 * <p>
 * Notes:
 * <ul>
 *   <li>The class name intentionally uses "Ressource" (double 's') to match the requested external type.</li>
 *   <li>Put instances of this item under {@code report/pluginReports/other/}.</li>
 *   <li>Use {@link PluginValidationItemSuccess} under {@code report/pluginReports/success/} when no leftovers exist.</li>
 * </ul>
 */
public final class ProcessPluginRessourceNotLoadedValidationItem extends PluginValidationItem
{
    /**
     * Creates a new warning item for an unreferenced plugin resource.
     *
     * @param file     the physical file that exists in the repository (leftover)
     * @param location optional logical location/context (e.g., relative path in the repo)
     * @param message  optional additional message; if {@code null} or empty a default message is used
     */
    public ProcessPluginRessourceNotLoadedValidationItem(File file, String location, String message)
    {
        super(ValidationSeverity.WARN,
              file,
              location,
              (message == null || message.isBlank())
                  ? "Resource exists but is not referenced by ProcessPluginDefinition (not loaded)"
                  : message);
    }
}
