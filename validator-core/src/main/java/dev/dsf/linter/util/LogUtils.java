package dev.dsf.linter.util;

import dev.dsf.linter.logger.Logger;

/** Centralized exception logging helpers. */
public final class LogUtils {
    private LogUtils() {}

    public static void logAndRethrow(Logger logger, String message, Throwable t) {
        logger.error(message, t);
        if (t instanceof RuntimeException re) throw re;
        throw new RuntimeException(message, t);
    }

    public static void log(Logger logger, String message, Throwable t) {
        logger.error(message, t);
    }
}
