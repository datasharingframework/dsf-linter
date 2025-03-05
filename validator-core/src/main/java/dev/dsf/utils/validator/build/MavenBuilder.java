package dev.dsf.utils.validator.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Responsible for building the project using Maven.
 */
public class MavenBuilder {

    /**
     * Executes a Maven build (clean, compile, and dependency:copy-dependencies) in the given project directory.
     *
     * @param projectDir the project directory.
     * @param mavenCmd   the Maven command to execute.
     * @return true if the build succeeds, false otherwise.
     * @throws IOException          if an I/O error occurs.
     * @throws InterruptedException if the process is interrupted.
     */
    public boolean buildProject(File projectDir, String mavenCmd) throws IOException, InterruptedException {
        System.out.println("Building project with Maven (clean, compile, and copy dependencies)...");
        ProcessBuilder pb = new ProcessBuilder(mavenCmd, "clean", "compile", "dependency:copy-dependencies");
        pb.directory(projectDir);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroy();
            System.err.println("ERROR: Maven build timed out.");
            return false;
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            System.err.println("ERROR: Maven build failed with exit code " + exitCode);
            return false;
        } else {
            System.out.println("Maven build succeeded");
            return true;
        }
    }
}
