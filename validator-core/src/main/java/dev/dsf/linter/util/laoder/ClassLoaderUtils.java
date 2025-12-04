package dev.dsf.linter.util.laoder;

import java.util.concurrent.Callable;

public final class ClassLoaderUtils {

    private ClassLoaderUtils() {
        // Utility class
    }

    /**
     * Executes the given operation with a temporary thread context classloader.
     * Automatically restores the original classloader after execution.
     *
     * @param temporaryClassLoader the classloader to set temporarily
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails
     */
    public static <T> T withTemporaryContextClassLoader(
            ClassLoader temporaryClassLoader,
            Callable<T> operation) throws Exception {

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(temporaryClassLoader);
            return operation.call();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    /**
     * Executes the given operation with a temporary thread context classloader.
     * Version for operations that don't return a value.
     *
     * @param temporaryClassLoader the classloader to set temporarily
     * @param operation the operation to execute
     * @throws Exception if the operation fails
     */
    public static void withTemporaryContextClassLoader(
            ClassLoader temporaryClassLoader,
            RunnableWithException operation) throws Exception {

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(temporaryClassLoader);
            operation.run();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @FunctionalInterface
    public interface RunnableWithException {
        void run() throws Exception;
    }
}