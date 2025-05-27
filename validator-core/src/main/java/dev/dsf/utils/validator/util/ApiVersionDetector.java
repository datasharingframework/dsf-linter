// src/main/java/dev/dsf/utils/validator/util/ApiVersionDetector.java
package dev.dsf.utils.validator.util;

import java.io.IOException;
import java.nio.file.*;

/**
 * Detects whether the exploded / source tree uses DSF BPE API&nbsp;v1 or v2.
 *
 * Strategy (in this order) :
 * <ol>
 *   <li>Look for a {@code ProcessPluginDefinition} file in any recognised
 *       <code>META-INF/services</code> directory – Maven/Gradle <i>and</i>
 *       flat layout.</li>
 *   <li>Fallback – look for the v2 task interface class file
 *       {@code dev/dsf/bpe/v2/task/ServiceTask.class} somewhere under the
 *       project root.</li>
 * </ol>
 * <p>
 * If nothing is found we default to&nbsp;v1 to keep the validator running.
 * </p>
 */
public class ApiVersionDetector
{
    private static final String GLOB = "dev.dsf.bpe.*.ProcessPluginDefinition";

    public static String detectVersion(Path projectRoot)
    {
        /* 1) Search in every known META-INF/services location */
        Path[] candidateDirs = {
                // Maven / Gradle workspace
                projectRoot.resolve("src/main/resources/META-INF/services"),
                // flat, exploded-JAR / CI layout
                projectRoot.resolve("META-INF/services")
        };

        for (Path dir : candidateDirs)
        {
            String v = detectFromServicesDir(dir);
            if (v != null)
                return v;
        }

        /* 2) Fallback: look for the v2 ServiceTask.class in the file tree */
        try
        {
            PathMatcher matcher = projectRoot.getFileSystem()
                    .getPathMatcher("glob:**/dev/dsf/bpe/v2/task/ServiceTask.class");

            try (var paths = Files.walk(projectRoot))
            {
                if (paths.anyMatch(matcher::matches))
                    return "v2";
            }
        }
        catch (IOException ignored) { /* continue with default */ }

        /* 3) Default – assume legacy API v1 */
        return "v1";
    }

    // helpers
    private static String detectFromServicesDir(Path dir)
    {
        if (!Files.isDirectory(dir))
            return null;

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, GLOB))
        {
            for (Path p : ds)
            {
                String f = p.getFileName().toString();
                if (f.startsWith("dev.dsf.bpe.v2"))
                    return "v2";
                if (f.startsWith("dev.dsf.bpe.v1"))
                    return "v1";
            }
        }
        catch (IOException ignored) { /* just give up and return null */ }
        return null;
    }
}
