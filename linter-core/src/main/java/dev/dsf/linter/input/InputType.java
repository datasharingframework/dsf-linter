package dev.dsf.linter.input;

/**
 * Enumeration of supported input types for the DSF linter.
 * <p>
 * This enum defines the different types of input sources that can be
 * linted by the DSF linter, enabling unified handling of various
 * project formats and locations.
 * </p>
 *
 * <h3>Supported Input Types:</h3>
 * <ul>
 *   <li><b>LOCAL_DIRECTORY</b> - Standard Maven/Gradle project directory on local filesystem</li>
 *   <li><b>GIT_REPOSITORY</b> - Remote Git repository (HTTP/HTTPS/SSH)</li>
 *   <li><b>LOCAL_JAR_FILE</b> - Compiled JAR file on local filesystem</li>
 *   <li><b>REMOTE_JAR_URL</b> - JAR file accessible via HTTP/HTTPS</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public enum InputType {

    /**
     * Local directory containing a Maven or Gradle project.
     * <p>
     * Expected structure: pom.xml or build.gradle in root,
     * with standard src/main/resources layout.
     * </p>
     */
    LOCAL_DIRECTORY,

    /**
     * Remote Git repository URL.
     * <p>
     * Supports protocols: http://, https://, git://, ssh://, git@
     * Repository will be cloned to temporary directory before linting.
     * </p>
     */
    GIT_REPOSITORY,

    /**
     * Local JAR file containing a compiled DSF plugin.
     * <p>
     * Expected structure: Standard JAR with META-INF/services registration,
     * BPMN files in bpe/, FHIR resources in fhir/.
     * </p>
     */
    LOCAL_JAR_FILE,

    /**
     * Remote JAR file accessible via HTTP/HTTPS.
     * <p>
     * JAR will be downloaded to temporary directory before extraction.
     * Supports standard HTTP/HTTPS URLs including Maven repository URLs.
     * </p>
     */
    REMOTE_JAR_URL
}