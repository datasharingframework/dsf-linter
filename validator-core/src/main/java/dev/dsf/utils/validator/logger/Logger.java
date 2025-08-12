package dev.dsf.utils.validator.logger;

/**
 * A simple logging interface to decouple the core validator logic
 * from specific logging frameworks like System.out or Maven Log.
 */
public interface Logger {
    void info(String message);
    void warn(String message);
    void error(String message);
    void error(String message, Throwable throwable);
}