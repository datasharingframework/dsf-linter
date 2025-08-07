package dev.dsf.utils.validator.util;

/** Minimal console color helper (ANSI). */
public final class Console
{
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";
    private Console() { }
    public static void red(String msg) { System.err.println(RED + msg + RESET); }
}

