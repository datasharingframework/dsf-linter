package dev.dsf.linter.exclusion;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Loads an {@link ExclusionConfig} from a JSON file.
 *
 * <h3>Auto-discovery</h3>
 * {@link #loadFromProjectRoot(Path)} looks for a file named
 * {@value #DEFAULT_FILENAME} in the project root directory. Use
 * {@link #load(Path)} to specify an explicit path.
 *
 * <h3>File format</h3>
 * <pre>{@code
 * {
 *   "affectsExitStatus": false,
 *   "rules": [
 *     { "type": "BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING" },
 *     { "severity": "WARN", "file": "update-allow-list.bpmn" },
 *     { "messageContains": "optional field" }
 *   ]
 * }
 * }</pre>
 *
 * <p>Unknown JSON fields are silently ignored. Rules with no criteria set are skipped.</p>
 */
public class ExclusionConfigLoader {

    /** Default file name searched in the project root. */
    public static final String DEFAULT_FILENAME = "dsf-linter-exclusions.json";

    private final ObjectMapper objectMapper;

    public ExclusionConfigLoader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Looks for {@value #DEFAULT_FILENAME} inside {@code projectRoot}.
     * Returns an empty Optional when no file is found.
     *
     * @param projectRoot the project root directory
     * @return loaded config, or empty if the file does not exist
     * @throws IOException if the file exists but cannot be parsed
     */
    public Optional<ExclusionConfig> loadFromProjectRoot(Path projectRoot) throws IOException {
        if (projectRoot == null) return Optional.empty();

        Path candidate = projectRoot.resolve(DEFAULT_FILENAME);
        if (!Files.isRegularFile(candidate)) return Optional.empty();

        return Optional.of(load(candidate));
    }

    /**
     * Loads an {@link ExclusionConfig} from the given JSON file.
     *
     * @param configFile path to the JSON file
     * @return the parsed configuration
     * @throws IOException if the file cannot be read or parsed
     */
    public ExclusionConfig load(Path configFile) throws IOException {
        if (configFile == null || !Files.isRegularFile(configFile)) {
            throw new IOException("Exclusion config file not found: " + configFile);
        }

        ExclusionConfig config = objectMapper.readValue(configFile.toFile(), ExclusionConfig.class);
        validate(config, configFile);
        return config;
    }

    // -------------------------------------------------------------------------

    private void validate(ExclusionConfig config, Path source) throws IOException {
        if (config.getRules() == null) return;

        for (int i = 0; i < config.getRules().size(); i++) {
            ExclusionRule rule = config.getRules().get(i);
            if (!rule.isValid()) {
                throw new IOException(
                        "Exclusion rule #" + (i + 1) + " in '" + source.getFileName()
                        + "' has no criteria. Each rule must specify at least one of: "
                        + "type, severity, file, messageContains.");
            }
        }
    }
}
