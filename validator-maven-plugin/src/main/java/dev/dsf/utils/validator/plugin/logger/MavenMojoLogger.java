package dev.dsf.utils.validator.plugin.logger;

import dev.dsf.utils.validator.logger.Logger;

/**
 * An adapter that implements our custom Logger interface
 * by delegating calls to an SLF4J Logger instance.
 */
public class MavenMojoLogger implements Logger {

    private final org.slf4j.Logger slf4jLogger;

    public MavenMojoLogger(org.slf4j.Logger slf4jLogger) {
        this.slf4jLogger = slf4jLogger;
    }

    @Override
    public void info(String message) {
        slf4jLogger.info(message);
    }

    @Override
    public void warn(String message) {
        slf4jLogger.warn(message);
    }

    @Override
    public void error(String message) {
        slf4jLogger.error(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        slf4jLogger.error(message, throwable);
    }
}