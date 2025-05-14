package dev.dsf.utils.validator.plugin;

import dev.dsf.utils.validator.DsfValidatorImpl;
import dev.dsf.utils.validator.ValidationOutput;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.util.ApiVersionDetector;
import dev.dsf.utils.validator.util.ApiVersionHolder;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static dev.dsf.utils.validator.util.ReportCleaner.prepareCleanReportDirectory;

/**
 * <p>
 * Maven Mojo for validating both BPMN and FHIR files using the DSF Validator.
 * </p>
 *
 * <p>
 * The validation results are written to the following directory within the build folder:
 * </p>
 *
 * <pre>
 * ${project.build.directory}/dsf-validation-reports/
 * ├── bpmnReports/
 * │   ├── success/
 * │   │   ├── bpmn_issues_<processId>.json
 * │   │   └── aggregated.json  ← Aggregated BPMN success issues
 * │   ├── other/
 * │   │   ├── bpmn_issues_<processId>.json
 * │   │   └── aggregated.json  ← Aggregated BPMN other issues
 * │   └── bpmn_issues_aggregated.json      ← All BPMN issues (success + others)
 * ├── fhirReports/
 * │   ├── success/
 * │   │   ├── fhir_issues_<baseName>.json
 * │   │   └── aggregated.json  ← Aggregated FHIR success issues
 * │   ├── other/
 * │   │   ├── fhir_issues_<baseName>.json
 * │   │   └── aggregated.json  ← Aggregated FHIR other issues
 * │   └── fhir_issues_aggregated.json      ← All FHIR issues (success + others)
 * └── aggregated.json                      ← Combined BPMN and FHIR issues (global)
 * </pre>
 *
 * <p>
 * Each generated JSON file follows this format:
 * </p>
 *
 * <pre>
 * {
 *   "timestamp": "2025-04-11 17:45:30",
 *   "validationItems": [
 *     {
 *       "severity": "ERROR",
 *       "message": "Missing start event...",
 *       ...
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>
 * Note: The DSF Validator now performs validation for both BPMN and FHIR files.
 * </p>
 *
 * <h3>How to use this plugin</h3>
 *
 * <p>Execute via command line:</p>
 * <pre>
 * mvn dev.dsf.utils.validator:validator-maven-plugin:verify
 * </pre>
 *
 * <p>Or include in your POM:</p>
 * <pre>{@code
 * <plugin>
 *   <groupId>dev.dsf.utils.validator</groupId>
 *   <artifactId>validator-maven-plugin</artifactId>
 *   <version>1.0-SNAPSHOT</version>
 *   <executions>
 *     <execution>
 *       <phase>verify</phase>
 *       <goals>
 *         <goal>verify</goal>
 *       </goals>
 *     </execution>
 *   </executions>
 * </plugin>
 * }</pre>
 *
 * <h3>References</h3>
 * <ul>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/model-api/bpmn-model-api/">
 *       Camunda BPMN Model API</a></li>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">
 *       BPMN 2.0 Specification</a></li>
 *   <li><a href="https://github.com/FasterXML/jackson-databind">
 *       Jackson JSON Project</a></li>
 *   <li><a href="https://hl7.org/fhir">
 *       FHIR Specification</a></li>
 *   <li><a href="https://maven.apache.org/developers/mojo-api.html">
 *       Maven Plugin API</a></li>
 * </ul>
 *
 * @author Khalil Malla
 * @version 1.2
 */
@Mojo(
        name = "verify",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        threadSafe = true
)
public class ValidateMojo extends AbstractMojo
{
    /**
     * The build directory of the project (typically the {@code target} folder).
     * The report folder will be placed inside this directory under "dsf-validation-reports".
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File buildDirectory;

    /**
     * The output directory containing compiled classes (target/classes).
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private String classesDirectory;

    /**
     * Project classpath elements (compile + runtime) for resolving project dependencies.
     */
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> classpathElements;

    /**
     * Executes the BPMN and FHIR validation process.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void execute() throws MojoExecutionException
    {
        // Assume Maven is executed from the project's root directory.
        File projectDir = new File(".");

        // Prepare a ClassLoader that includes project classes and dependencies
        try
        {
            List<URL> urls = new ArrayList<>();
            urls.add(new File(classesDirectory).toURI().toURL());
            for (String element : classpathElements)
            {
                urls.add(new File(element).toURI().toURL());
            }
            URLClassLoader projectClassLoader = new URLClassLoader(
                    urls.toArray(new URL[0]),
                    Thread.currentThread().getContextClassLoader()
            );
            Thread.currentThread().setContextClassLoader(projectClassLoader);
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Failed to set up project classloader", e);
        }

        // Create a new DSF Validator implementation instance.
        DsfValidatorImpl validator = new DsfValidatorImpl();

        // Define the "dsf-validation-reports" folder within the project's build directory.
        File reportRoot = new File(buildDirectory, "dsf-validation-reports");
        prepareCleanReportDirectory(reportRoot);

        // Detect and store API version before any validation
        String apiVersion = ApiVersionDetector.detectVersion(projectDir.toPath());
        ApiVersionHolder.setVersion(apiVersion);

        // 1) Validate BPMN files
        List<AbstractValidationItem> allBpmnItems = validator.validateAllBpmnFilesSplitNewStructure(projectDir, reportRoot);

        // 2) Validate FHIR files
        List<AbstractValidationItem> allFhirItems = validator.validateAllFhirResourcesSplitNewStructure(projectDir, reportRoot);

        // 3) Combine BPMN and FHIR validation items into a global aggregated report
        List<AbstractValidationItem> globalItems = new ArrayList<>();
        globalItems.addAll(allBpmnItems);
        globalItems.addAll(allFhirItems);
        ValidationOutput globalOutput = new ValidationOutput(globalItems);
        File globalJson = new File(reportRoot, "aggregated.json");
        globalOutput.writeResultsAsJson(globalJson);

        // Log the completion message.
        getLog().info("Validation completed. See folder: " + reportRoot.getAbsolutePath());

        // Print detected API version in red
        System.out.println("\u001B[31mDetected DSF BPE API version: "
                + apiVersion + "\u001B[0m");
    }
}
