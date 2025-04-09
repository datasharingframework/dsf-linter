package dev.dsf.utils.validator.plugin;

import dev.dsf.utils.validator.DsfValidatorImpl;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * <p>
 * A custom Maven plugin that runs the DSF (Data Sharing Framework) Validator to check BPMN files
 * under a specified project directory. By default, it writes validation reports to a timestamped subdirectory inside
 * {@code ${project.build.directory}/dsf-validation-reports}.
 * </p>
 *
 * <p><strong>Main Responsibilities:</strong></p>
 * <ul>
 *   <li>Identify the project directory to be validated (from {@code project.basedir} by default).</li>
 *   <li>Create a timestamped reports subdirectory under the configured reports directory.</li>
 *   <li>Invoke the DSF validator to validate all BPMN files in the project.</li>
 *   <li>Optionally, FHIR files can be validated as well (uncomment or add a method call).</li>
 * </ul>
 *
 * <p><strong>Usage Example in a {@code pom.xml}:</strong></p>
 * <pre>{@code
 *   <plugin>
 *       <groupId>dev.dsf.utils.validator</groupId>
 *       <artifactId>dsf-validator-plugin</artifactId>
 *       <version>1.0.0</version>
 *       <executions>
 *           <execution>
 *               <goals>
 *                   <goal>validate</goal>
 *               </goals>
 *           </execution>
 *       </executions>
 *   </plugin>
 * }</pre>
 *
 * <p>
 * You can run this plugin by invoking:
 * </p>
 * <pre>{@code
 *   mvn dev.dsf.utils.validator:dsf-validator-plugin:validate
 * }</pre>
 *
 * <h3>References</h3>
 * <ul>
 *   <li><a href="https://maven.apache.org/guides/plugin/guide-java-plugin-development.html">
 *       Maven Plugin Development Guide</a></li>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://hl7.org/fhir">HL7 FHIR</a> (if FHIR validation is also required)</li>
 *   <li><a href="https://github.com/highmed/highmed-dsf">DSF GitHub</a></li>
 * </ul>
 */
@Mojo(name = "validate")
public class ValidateMojo extends AbstractMojo
{
    /**
     * <p>
     * The root directory of the project to validate. Defaults to the current Maven project's base directory.
     * </p>
     * <p>
     * This parameter can be overridden on the command line, for example:
     * {@code mvn dev.dsf.utils.validator:dsf-validator-plugin:validate -Dvalidate.projectDir=/path/to/project}
     * </p>
     */
    @Parameter(property = "validate.projectDir", defaultValue = "${project.basedir}")
    private File projectDir;

    /**
     * <p>
     * The base directory where validation reports will be written. Defaults to
     * {@code ${project.build.directory}/dsf-validation-reports}.
     * </p>
     * <p>
     * A timestamped subdirectory will be created inside this directory to hold the
     * actual report files (e.g., {@code dsf-validation-reports/reports_09042025_153045}).
     * </p>
     */
    @Parameter(property = "validate.reportsDir", defaultValue = "${project.build.directory}/dsf-validation-reports")
    private File reportsDir;

    /**
     * <p>
     * Invoked by Maven when the {@code validate} goal is called. This method:
     * </p>
     * <ol>
     *   <li>Logs basic information (project path, base reports path).</li>
     *   <li>Creates a timestamped subdirectory within the base reports directory.</li>
     *   <li>Instantiates a {@link DsfValidatorImpl} to validate all BPMN files within the project.</li>
     *   <li>Optionally, you can validate FHIR files as well by adding a call to
     *       {@code validator.validateAllFhirResources(projectDir, finalReportsDir)}.</li>
     * </ol>
     *
     * @throws MojoExecutionException if any validation or I/O error occurs
     */
    @Override
    public void execute() throws MojoExecutionException
    {
        getLog().info("DSF Validator Plugin started.");
        getLog().info("Project directory: " + projectDir.getAbsolutePath());
        getLog().info("Base reports directory: " + reportsDir.getAbsolutePath());

        // Build a timestamped subdirectory name (e.g. "reports_09042025_153045")
        String dateTimeStr = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
        File finalReportsDir = new File(reportsDir, "reports_" + dateTimeStr);

        try
        {
            // Ensure the final (timestamped) reports directory is available
            if (!finalReportsDir.exists() && !finalReportsDir.mkdirs())
            {
                throw new MojoExecutionException(
                        "Could not create reports directory: " + finalReportsDir.getAbsolutePath()
                );
            }

            getLog().info("Final (timestamped) reports directory: " + finalReportsDir.getAbsolutePath());

            // Create and run the validator
            DsfValidatorImpl validator = new DsfValidatorImpl();
            validator.validateAllBpmnFiles(projectDir, finalReportsDir);

        }
        catch (Exception e)
        {
            getLog().error("Validation failed", e);
            throw new MojoExecutionException("DSF validation failed", e);
        }

        getLog().info("DSF validation completed successfully.");
    }
}
