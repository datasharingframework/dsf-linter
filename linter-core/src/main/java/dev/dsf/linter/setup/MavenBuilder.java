package dev.dsf.linter.setup;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import static dev.dsf.linter.classloading.ClassInspector.logger;


/**
 * Responsible for building a project using Maven.
 * <p>
 * This class provides methods to execute Maven goals within a given project directory.
 * It supports flexible goal execution as well as a default build process including
 * clean, compile, and copy-dependencies.
 */
public class MavenBuilder
{
    /**
     * Executes a Maven build with the specified goals.
     *
     * @param projectDir the directory containing the Maven project
     * @param mavenCmd   the Maven executable (e.g. "mvn" or full path to mvn)
     * @param goals      one or more Maven goals or lifecycle phases to execute
     * @return true if the build succeeds, false otherwise
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the process is interrupted
     */
    public boolean buildProject(File projectDir, String mavenCmd, String... goals)
            throws IOException, InterruptedException
    {
        logger.info("Building project with Maven goals: " + String.join(" ", goals));

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(buildCommand(mavenCmd, goals));
        processBuilder.directory(projectDir);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                logger.info(line);
            }
        }

        if (!process.waitFor(5, TimeUnit.MINUTES))
        {
            process.destroy();
            System.err.println("ERROR: Maven build timed out.");
            return false;
        }

        int exitCode = process.exitValue();
        if (exitCode != 0)
        {
            System.err.println("ERROR: Maven build failed with exit code " + exitCode);
            return false;
        }

        logger.info("Maven build succeeded");
        return true;
    }

    /**
     * Constructs the full command array for the Maven execution.
     *
     * @param mavenCmd the Maven executable
     * @param goals    the Maven goals or phases to include
     * @return a String array containing the full command to execute
     */
    private String[] buildCommand(String mavenCmd, String... goals)
    {
        String[] command = new String[goals.length + 1];
        command[0] = mavenCmd;
        System.arraycopy(goals, 0, command, 1, goals.length);
        return command;
    }
}

