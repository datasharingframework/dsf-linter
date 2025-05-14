// src/main/java/dev/dsf/utils/validator/util/ApiVersionDetector.java
package dev.dsf.utils.validator.util;

import java.io.IOException;
import java.nio.file.*;

/**
 * Detects whether the project is using dev.dsf.bpe.v1 or v2
 * by looking for the ProcessPluginDefinition file under
 * src/main/resources/META-INF/services.
 */
public class ApiVersionDetector {
    public static String detectVersion(Path projectRoot) {
        Path servicesDir = projectRoot.resolve("src/main/resources/META-INF/services");
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(
                servicesDir, "dev.dsf.bpe.*.ProcessPluginDefinition")) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (name.startsWith("dev.dsf.bpe.v2")) {
                    return "v2";
                } else if (name.startsWith("dev.dsf.bpe.v1")) {
                    return "v1";
                }
            }
        } catch (IOException ignored) {
            // fallback to default
        }
        return "v1";
    }
}
