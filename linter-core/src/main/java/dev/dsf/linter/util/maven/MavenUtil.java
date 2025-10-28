package dev.dsf.linter.util.maven;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

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
 * The utility attempts to find {@code mvn.cmd} in two ways:
 * <ul>
 *   <li>If the {@code MAVEN_HOME} environment variable is set, it looks under {@code MAVEN_HOME/bin/mvn.cmd}</li>
 *   <li>If not set, it invokes the {@code where mvn.cmd} command (on Windows) to resolve the path from the system {@code PATH}</li>
 * </ul>
 * </p>
 *
 * <p><strong>Limitations:</strong> This implementation currently supports only Windows-based environments due to the use of {@code mvn.cmd} and {@code where}.</p>
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
 * </ul>
 */
public class MavenUtil {

    /**
     * Locates the Maven executable for Windows environments.
     *
     * <p>
     * The method first checks whether the {@code MAVEN_HOME} environment variable is defined.
     * If so, it attempts to find {@code mvn.cmd} in the {@code bin} directory.
     * If {@code MAVEN_HOME} is not set, it uses the {@code where mvn.cmd} command
     * to search for the Maven executable in the system {@code PATH}.
     * </p>
     *
     * <p>
     * This method is blocking and waits up to 10 seconds for the {@code where} process to complete.
     * If Maven is not found or an error occurs, the method logs the problem and returns {@code null}.
     * </p>
     *
     * @return the absolute path to {@code mvn.cmd}, or {@code null} if not found or not executable
     */

    public static String locateMavenExecutable() {
        String mavenCmd;
        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome != null && !mavenHome.isEmpty()) {
            mavenCmd = mavenHome + File.separator + "bin" + File.separator + "mvn.cmd";
            File mavenFile = new File(mavenCmd);
            if (!mavenFile.exists()) {
                System.err.println("ERROR: Maven executable not found at: " + mavenFile.getAbsolutePath());
                System.err.println("Please check if MAVEN_HOME is set correctly.");
                return null;
            }
        } else {
            try {
                ProcessBuilder pbWhere = new ProcessBuilder("where", "mvn.cmd");
                Process pWhere = pbWhere.start();
                if (!pWhere.waitFor(10, TimeUnit.SECONDS)) {
                    pWhere.destroy();
                    System.err.println("ERROR: Timeout while trying to locate Maven.");
                    return null;
                }
                BufferedReader whereReader = new BufferedReader(new InputStreamReader(pWhere.getInputStream()));
                String foundPath = whereReader.readLine();
                if (pWhere.exitValue() == 0 && foundPath != null && !foundPath.isEmpty()) {
                    mavenCmd = foundPath;
                    System.out.println("Found Maven executable at: " + mavenCmd);
                } else {
                    System.err.println("ERROR: MAVEN_HOME is not set and Maven was not found using 'where'.");
                    System.err.println("Please set MAVEN_HOME or ensure Maven is in the system PATH.");
                    return null;
                }
            } catch (Exception ex) {
                System.err.println("ERROR: Exception while trying to locate Maven: " + ex.getMessage());
                ex.printStackTrace();
                return null;
            }
        }
        return mavenCmd;
    }
}
