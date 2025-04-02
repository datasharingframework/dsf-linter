package dev.dsf.utils.validator;

import dev.dsf.utils.validator.build.MavenBuilder;
import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.fhir.FhirResourceValidator;
import dev.dsf.utils.validator.item.FhirElementValidationItem;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.util.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * The {@code LocalMain} class serves as the entry point for validating BPMN files
 * within a locally available Maven project. It also validates FHIR resources
 * after compiling the project.
 * <p>
 * The flow of the application includes:
 * <ul>
 *   <li>Reading the local project path from the console.</li>
 *   <li>Checking if the provided path exists and is a directory.</li>
 *   <li>Locating the Maven executable using the helper class {@link dev.dsf.utils.validator.util.MavenUtil}.</li>
 *   <li>Building the project (clean, compile, and copy dependencies) using
 *       {@link dev.dsf.utils.validator.build.MavenBuilder}.</li>
 *   <li>Searching for BPMN files in the directory <code>src/main/resources/bpe</code>.</li>
 *   <li>Validating the found BPMN files using the {@link dev.dsf.utils.validator.bpmn.BPMNValidator}.</li>
 *   <li>Validating FHIR resources under <code>src/main/resources/fhir</code> using
 *       {@link dev.dsf.utils.validator.fhir.FhirResourceValidator}.</li>
 * </ul>
 * </p>
 * <p>
 * For more details on JavaDoc, see the
 * <a href="https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html">Oracle JavaDoc documentation</a>.
 * </p>
 */
public class LocalMain
{
    public static void main(String[] args)
    {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Please enter the local project path: ");
        String projectPath = scanner.nextLine();

        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory())
        {
            System.err.println("ERROR: The provided path does not exist or is not a directory: "
                    + projectDir.getAbsolutePath());
            return;
        }

        // Locate Maven executable
        String mavenCmd = MavenUtil.locateMavenExecutable();
        if (mavenCmd == null)
        {
            // Error message already printed in MavenUtil
            return;
        }

        // Build the project with Maven (clean, compile, and copy dependencies)
        MavenBuilder mavenBuilder = new MavenBuilder();
        try
        {
            if (!mavenBuilder.buildProject(projectDir, mavenCmd))
            {
                return;
            }
        }
        catch (IOException | InterruptedException e)
        {
            System.err.println("ERROR: Exception during Maven build: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // BPMN files should be located in the 'src/main/resources/bpe' directory within the project.
        File bpmnDirFile = new File(projectDir, "src/main/resources/bpe");
        if (!bpmnDirFile.exists() || !bpmnDirFile.isDirectory())
        {
            System.err.println("ERROR: BPMN directory does not exist: " + bpmnDirFile.getAbsolutePath());
            return;
        }

        System.out.println("Searching for BPMN files in: " + bpmnDirFile.getAbsolutePath());

        File[] fileArray = bpmnDirFile.listFiles((dir, name) -> name.toLowerCase().endsWith(".bpmn"));
        List<Path> bpmnPaths = (fileArray != null)
                ? Arrays.stream(fileArray).map(File::toPath).toList()
                : new ArrayList<>();

        if (bpmnPaths.isEmpty())
        {
            System.err.println("ERROR: No BPMN files found in the directory: " + bpmnDirFile.getAbsolutePath());
            return;
        }

        // Validate BPMN files
        BPMNValidator bpmnValidator = new BPMNValidator();
        for (Path bpmnPath : bpmnPaths)
        {
            System.out.println("\nValidating BPMN file: " + bpmnPath.getFileName());
              ValidationOutput output = bpmnValidator.validateBpmnFile(bpmnPath);
            output.printResults();
            System.out.println("Validation completed for: " + bpmnPath.getFileName());
        }

        // Now validate FHIR resources in src/main/resources/fhir
        System.out.println("\nNow validating FHIR resources in: " + projectDir.getAbsolutePath());
        FhirResourceValidator fhirValidator = new FhirResourceValidator();
        List<FhirElementValidationItem> fhirIssues = fhirValidator.validateAllFhirResources(projectDir);

        // Convert to ValidationOutput and print
        List<AbstractValidationItem> items = new ArrayList<>(fhirIssues);
        ValidationOutput fhirOutput = new ValidationOutput(items);
        fhirOutput.printResults();

        System.out.println("\nValidation process finished!");
    }
}
