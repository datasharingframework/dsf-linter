package dev.dsf.utils.validator.util;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for Maven-related operations.
 */
public class MavenUtil {

    /**
     * Locates the Maven executable.
     * <p>
     * If the environment variable MAVEN_HOME is set, this method uses that path;
     * otherwise, it attempts to locate Maven using the Windows "where" command.
     * </p>
     *
     * @return the full path to the Maven executable (e.g. mvn.cmd), or null if not found.
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
