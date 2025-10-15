package dev.dsf.linter.util.api;

import java.nio.file.Path;

/**
 * Value object containing API version detection results with provenance information.
 *
 * @param version the detected API version
 * @param foundAt the path where evidence was found
 * @param source how the version was detected
 */
public record DetectedVersion(ApiVersion version, Path foundAt, DetectionSource source) {}

