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
 * <h2>DSF Maven Plugin: Validator Mojo</h2>
 *
 * <p>
 * This Mojo provides an integration point for running the DSF Validator directly from a Maven build.
 * It validates both BPMN and FHIR resources located in the DSF project source tree and writes JSON-formatted reports
 * to a dedicated output directory inside the Maven {@code target} folder.
 * </p>
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li>Runs automatically during the Maven {@code verify} phase</li>
 *   <li>Supports validation of:
 *     <ul>
 *       <li>BPMN process models under {@code src/main/resources/bpe}</li>
 *       <li>FHIR resource files under {@code src/main/resources/fhir}</li>
 *     </ul>
 *   </li>
 *   <li>Writes categorized report files into:
 *     <code>${project.build.directory}/dsf-validation-reports</code>
 *   </li>
 *   <li>Prints a summary and detected DSF BPE API version to the console</li>
 * </ul>
 *
 * <h3>Usage Instructions</h3>
 *
 * <h4>CLI Execution</h4>
 * <pre>{@code
 * mvn dev.dsf.utils.validator:validator-maven-plugin:verify
 * }</pre>
 *
 * <h4>POM Integration</h4>
 * <pre>{@code
 * <plugin>
 *   <groupId>dev.dsf.utils.validator</groupId>
 *   <artifactId>validator-maven-plugin</artifactId>
 *   <version>1.2</version>
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
 * <h3>Report Output Structure</h3>
 * <pre>
 * target/dsf-validation-reports/
 * ├── bpmnReports/
 * │   ├── success/ → Individual and aggregated BPMN success reports
 * │   ├── other/   → Individual and aggregated BPMN error/warning reports
 * │   └── bpmn_issues_aggregated.json
 * ├── fhirReports/
 * │   ├── success/ → Individual and aggregated FHIR success reports
 * │   ├── other/   → Individual and aggregated FHIR error/warning reports
 * │   └── fhir_issues_aggregated.json
 * └── aggregated.json (combined BPMN + FHIR report)
 * </pre>
 *
 * <h3>Requirements</h3>
 * <ul>
 *   <li>Assumes the Maven project is correctly structured and compiled</li>
 *   <li>Requires the project classpath to be resolvable at runtime</li>
 * </ul>
 *
 * @author Khalil Malla
 * @version 1.2
 * @since 1.0.0
 * @see dev.dsf.utils.validator.DsfValidatorImpl
 * @see org.apache.maven.plugin.AbstractMojo
 * @see <a href="https://maven.apache.org/developers/mojo-api.html">Maven Mojo API</a>
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
