package dev.dsf.utils.validator;

import dev.dsf.utils.validator.build.MavenBuilder;
import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.repo.RepositoryManager;
import dev.dsf.utils.validator.util.MavenUtil;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // Prompt the user to enter the remote repository URL via the console
        Scanner scanner = new Scanner(System.in);
        System.out.print("Please enter the remote repository URL: ");
        String remoteRepoUrl = scanner.nextLine();

        // Extract the repository name (substring after the last '/')
        String repositoryName = remoteRepoUrl.substring(remoteRepoUrl.lastIndexOf('/') + 1);

        // Create the local directory where the repository will be cloned (using the system temporary directory)
        File cloneDir = new File(System.getProperty("java.io.tmpdir"), repositoryName);

        // Clone the repository if it is not already present
        RepositoryManager repoManager = new RepositoryManager();
        try {
            cloneDir = repoManager.getRepository(remoteRepoUrl, cloneDir);
        } catch (GitAPIException e) {
            System.err.println("ERROR: Failed to clone repository: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Locate Maven executable
        String mavenCmd = MavenUtil.locateMavenExecutable();
        if (mavenCmd == null) {
            // Maven executable not found – message already printed in MavenUtil
            return;
        }

        // Build the project using Maven (clean, compile, and copy dependencies)
        MavenBuilder mavenBuilder = new MavenBuilder();
        try {
            if (!mavenBuilder.buildProject(cloneDir, mavenCmd)) {
                return;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("ERROR: Exception during Maven build: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // BPMN files are expected in the 'src/main/resources/bpe' directory within the cloned repository.
        File bpmnDirFile = new File(cloneDir, "src/main/resources/bpe");
        if (!bpmnDirFile.exists() || !bpmnDirFile.isDirectory()) {
            System.err.println("ERROR: BPMN directory does not exist: " + bpmnDirFile.getAbsolutePath());
            return;
        }

        System.out.println("🔍 Searching for BPMN files in: " + bpmnDirFile.getAbsolutePath());

        File[] fileArray = bpmnDirFile.listFiles((dir, name) -> name.toLowerCase().endsWith(".bpmn"));
        List<Path> bpmnPaths = fileArray != null
                ? Arrays.stream(fileArray).map(File::toPath).toList()
                : new ArrayList<>();

        if (bpmnPaths.isEmpty()) {
            System.err.println("ERROR: No BPMN files found in the directory: " + bpmnDirFile.getAbsolutePath());
            return;
        }

        // Validate BPMN files
        BPMNValidator bpmnValidator = new BPMNValidator();
        for (Path bpmnPath : bpmnPaths) {
            System.out.println("\nValidating BPMN file: " + bpmnPath.getFileName());
            ValidationOutput output = bpmnValidator.validateBpmnFile(bpmnPath);
            output.printResults();
            System.out.println("Finished validation for: " + bpmnPath.getFileName());
        }
        System.out.println("\nAll BPMN files validated successfully!");
    }
}
