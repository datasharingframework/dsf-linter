package dev.dsf.linter.util.maven;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import static dev.dsf.linter.classloading.ClassInspector.logger;

/**
 * <h2>Maven Utility</h2>
 *
 * <p>
 * The {@code MavenUtil} class provides helper methods for locating the Maven
 * executable on the system. It is designed to assist in environments where Maven
 * must be invoked programmatically (e.g., linting tools, CI/CD wrappers).
 * </p>
 *
 * <p>
 * The utility attempts to find the Maven executable in two ways:
 * <ul>
 *   <li>If the {@code MAVEN_HOME} environment variable is set, it looks under {@code MAVEN_HOME/bin}</li>
 *   <li>If not set, it uses OS-specific commands to resolve the path from the system {@code PATH}</li>
 * </ul>
 * </p>
 *
 * <p>
 * The implementation supports Windows, Linux, and macOS by detecting the operating system
 * and using the appropriate command:
 * <ul>
 *   <li><b>Windows:</b> {@code where mvn.cmd}</li>
 *   <li><b>Linux/macOS:</b> {@code which mvn}</li>
 * </ul>
 * </p>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * String mavenPath = MavenUtil.locateMavenExecutable();
 * if (mavenPath != null) {
 *     // Use mavenPath to invoke Maven processes
 * }
 * }</pre>
 *
 * <h3>See Also:</h3>
 * <ul>
 *   <li><a href="https://maven.apache.org/">Apache Maven</a></li>
 *   <li><a href="https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/where">where command (Microsoft)</a></li>
 *   <li><a href="https://linux.die.net/man/1/which">which command (Linux)</a></li>
 * </ul>
 */
public class MavenUtil {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    /**
     * Locates the Maven executable for the current operating system.
     *
     * <p>
     * The method first checks whether the {@code MAVEN_HOME} environment variable is defined.
     * If so, it attempts to find the Maven executable in the {@code bin} directory.
     * If {@code MAVEN_HOME} is not set, it uses OS-specific commands
     * to search for the Maven executable in the system {@code PATH}.
     * </p>
     *
     * <p>
     * This method is blocking and waits up to 10 seconds for the process to complete.
     * If Maven is not found or an error occurs, the method logs the problem and returns {@code null}.
     * </p>
     *
     * @return the absolute path to the Maven executable, or {@code null} if not found or not executable
     */
    public static String locateMavenExecutable() {
        String mavenCmd;
        String mavenHome = System.getenv("MAVEN_HOME");

        if (mavenHome != null && !mavenHome.isEmpty()) {
            String mavenExecutable = IS_WINDOWS ? "mvn.cmd" : "mvn";
            mavenCmd = mavenHome + File.separator + "bin" + File.separator + mavenExecutable;
            File mavenFile = new File(mavenCmd);

            if (!mavenFile.exists()) {
                System.err.println("ERROR: Maven executable not found at: " + mavenFile.getAbsolutePath());
                System.err.println("Please check if MAVEN_HOME is set correctly.");
                return null;
            }

            logger.debug("Found Maven executable via MAVEN_HOME: " + mavenCmd);
        } else {
            try {
                String locateCommand = IS_WINDOWS ? "where" : "which";
                String mavenExecutable = IS_WINDOWS ? "mvn.cmd" : "mvn";

                ProcessBuilder pbLocate = new ProcessBuilder(locateCommand, mavenExecutable);
                Process pLocate = pbLocate.start();

                if (!pLocate.waitFor(10, TimeUnit.SECONDS)) {
                    pLocate.destroy();
                    System.err.println("ERROR: Timeout while trying to locate Maven.");
                    return null;
                }

                BufferedReader locateReader = new BufferedReader(new InputStreamReader(pLocate.getInputStream()));
                String foundPath = locateReader.readLine();

                if (pLocate.exitValue() == 0 && foundPath != null && !foundPath.isEmpty()) {
                    mavenCmd = foundPath.trim();
                    logger.debug("Found Maven executable at: " + mavenCmd);
                } else {
                    System.err.println("ERROR: MAVEN_HOME is not set and Maven was not found using '" + locateCommand + "'.");
                    System.err.println("Please set MAVEN_HOME or ensure Maven is in the system PATH.");
                    return null;
                }
            } catch (Exception ex) {
                System.err.println("ERROR: Exception while trying to locate Maven: " + ex.getMessage());
                return null;
            }
        }

        return mavenCmd;
    }
}