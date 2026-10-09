package dev.dsf.linter.exception;

/**
 * Thrown when no ProcessPluginDefinition implementation could be discovered.
 * <p>
 * Unchecked and a subtype of {@link IllegalStateException}, so existing handlers keep working.
 * {@code DsfLinter} turns it into an ERROR lint item instead of aborting without a report.
 * </p>
 */
public class NoPluginFoundException extends IllegalStateException {
    public NoPluginFoundException(String message) {
        super(message);
    }
}
