package dev.dsf.linter.logger;

/**
 * A simple logging interface to decouple the core linter logic
 * from specific logging frameworks like System.out or Maven Log.
 */
public interface Logger {
    void info(String message);
    void warn(String message);
    void error(String message);
    void error(String message, Throwable throwable);
    void debug(String message);

    /**
     * Returns true if the logger is in verbose mode.
     * @return true for verbose mode, otherwise false
     */
    boolean verbose();

    boolean isVerbose();
}