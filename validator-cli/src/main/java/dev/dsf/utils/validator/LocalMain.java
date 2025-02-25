package dev.dsf.utils.validator;

import dev.dsf.utils.validator.build.MavenBuilder;
import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.util.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class LocalMain {
    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        System.out.print("Please enter the local project path: ");
        String projectPath = scanner.nextLine();

        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            System.err.println("❌ ERROR: The provided path does not exist or is not a directory: "
                    + projectDir.getAbsolutePath());
            return;
        }

        // Locate Maven executable
        String mavenCmd = MavenUtil.locateMavenExecutable();
        if (mavenCmd == null) {
            // Error message already printed in MavenUtil
            return;
        }

        // Build the project with Maven (clean, compile, and copy dependencies)
        MavenBuilder mavenBuilder = new MavenBuilder();
        try {
            if (!mavenBuilder.buildProject(projectDir, mavenCmd)) {
                return;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("❌ ERROR: Exception during Maven build: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // BPMN files should be located in the 'src/main/resources/bpe' directory within the project.
        File bpmnDirFile = new File(projectDir, "src/main/resources/bpe");
        if (!bpmnDirFile.exists() || !bpmnDirFile.isDirectory()) {
            System.err.println("❌ ERROR: BPMN directory does not exist: " + bpmnDirFile.getAbsolutePath());
            return;
        }

        System.out.println("🔍 Searching for BPMN files in: " + bpmnDirFile.getAbsolutePath());

        File[] fileArray = bpmnDirFile.listFiles((dir, name) -> name.toLowerCase().endsWith(".bpmn"));
        List<Path> bpmnPaths = fileArray != null
                ? Arrays.stream(fileArray).map(File::toPath).toList()
                : new ArrayList<>();

        if (bpmnPaths.isEmpty()) {
            System.err.println("❌ ERROR: No BPMN files found in the directory: " + bpmnDirFile.getAbsolutePath());
            return;
        }

        // Validate BPMN files
        BPMNValidator bpmnValidator = new BPMNValidator();
        for (Path bpmnPath : bpmnPaths) {
            System.out.println("\n🔍 Validating BPMN file: " + bpmnPath.getFileName());
            ValidationOutput output = bpmnValidator.validateBpmnFile(bpmnPath);
            output.printResults();
            System.out.println("✅ Validation completed for: " + bpmnPath.getFileName());
        }
        System.out.println("\n✅ All BPMN files successfully validated!");
    }
}
