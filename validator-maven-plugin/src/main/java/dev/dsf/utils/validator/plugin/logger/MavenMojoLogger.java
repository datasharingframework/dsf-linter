package dev.dsf.utils.validator.plugin.logger;

import dev.dsf.utils.validator.logger.Logger;

/**
 * An adapter that implements the Logger interface by delegating calls to an SLF4J Logger instance.
 * This record is immutable and holds the necessary components for logging.
 *
 * @param slf4jLogger The SLF4J Logger to delegate to.
 * @param verbose     True to enable verbose logging, otherwise false.
 */
public record MavenMojoLogger(org.slf4j.Logger slf4jLogger, boolean verbose) implements Logger
{
    @Override
    public void info(String message)
    {
        slf4jLogger.info(message);
    }

    @Override
    public void warn(String message)
    {
        slf4jLogger.warn(message);
    }

    @Override
    public void error(String message)
    {
        slf4jLogger.error(message);
    }

    @Override
    public void error(String message, Throwable throwable)
    {
        slf4jLogger.error(message, throwable);
    }

    @Override
    public void debug(String message)
    {
        // Only print debug messages if verbose mode is enabled.
        if (verbose)
        {
            slf4jLogger.debug(message);
        }
    }

    @Override
    public boolean isVerbose()
    {
        // Returns the value of the 'verbose' component of this record.
        return verbose;
    }
}
