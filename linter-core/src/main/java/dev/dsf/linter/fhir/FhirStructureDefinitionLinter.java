package dev.dsf.linter.fhir;

import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.resource.FhirAuthorizationCache;
import dev.dsf.linter.util.linting.AbstractFhirInstanceLinter;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

/**
 * Validates FHIR {@code StructureDefinition} resources against DSF-specific constraints and authoring conventions.
 * <p>
 * This linter is a core component of the DSF linter toolchain that enforces structural, semantic,
 * and metadata requirements for {@code StructureDefinition} resources used in the Data Sharing Framework (DSF).
 * It extends {@link AbstractFhirInstanceLinter} and reports all findings as {@link FhirElementLintItem} instances.
 * </p>
 *
 * <h2>Validation Rules</h2>
 * The linter performs the following categories of checks:
 *
 * <h3>1. Metadata and Basic Elements</h3>
 * <ul>
 *   <li><b>Read Access Tag:</b> Validates that {@code meta.tag} contains a valid read-access tag with
 *       {@code system="http://dsf.dev/fhir/CodeSystem/read-access-tag"} and a recognized {@code code} value
 *       from the {@code CS_READ_ACCESS} code system.</li>
 *   <li><b>URL:</b> Ensures that the {@code url} element is present and non-empty.</li>
 *   <li><b>Status:</b> Verifies that the {@code status} element has the value {@code "unknown"} as required
 *       by DSF conventions.</li>
 * </ul>
 *
 * <h3>2. Placeholder Validation</h3>
 * <ul>
 *   <li><b>Version Placeholder:</b> Checks that the {@code version} element contains exactly the
 *       placeholder string {@code "#{version}"}, which is substituted during the DSF build process.</li>
 *   <li><b>Date Placeholder:</b> Checks that the {@code date} element contains exactly the
 *       placeholder string {@code "#{date}"}, which is substituted during the DSF build process.</li>
 * </ul>
 *
 * <h3>3. Differential and Snapshot Structure</h3>
 * <ul>
 *   <li><b>Differential Presence:</b> Verifies that a {@code differential} element exists.</li>
 *   <li><b>Snapshot Absence:</b> Warns if a {@code snapshot} element is present, as DSF profiles
 *       should only contain differentials.</li>
 *   <li><b>Element ID Presence:</b> Ensures that every {@code element} within the {@code differential}
 *       has an {@code @id} attribute.</li>
 *   <li><b>Element ID Uniqueness:</b> Verifies that all {@code element/@id} attributes are unique
 *       within the differential.</li>
 * </ul>
 *
 * <h3>4. Slice Cardinality Constraints</h3>
 * Validates slice cardinality rules according to FHIR profiling specification §5.1.0.14:
 * <ul>
 *   <li><b>SHOULD Rule:</b> The sum of all slice minimum cardinalities should be less than or equal
 *       to the base element's minimum cardinality.</li>
 *   <li><b>MUST Rule (Min Sum):</b> The sum of all slice minimum cardinalities must not exceed
 *       the base element's maximum cardinality.</li>
 *   <li><b>MUST Rule (Slice Max):</b> No individual slice's maximum cardinality may exceed
 *       the base element's maximum cardinality.</li>
 * </ul>
 *
 * <h2>Supported Resource Type</h2>
 * This linter only processes FHIR resources with root element {@code StructureDefinition}.
 * The {@link #canLint(Document)} method determines compatibility.
 *
 * <h2>Output</h2>
 * All validation results are returned as a list of {@link FhirElementLintItem} instances, including:
 * <ul>
 *   <li>Error items for constraint violations</li>
 *   <li>Warning items for deviations from recommendations</li>
 *   <li>Informational "OK" items for passed checks</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * FhirStructureDefinitionLinter linter = new FhirStructureDefinitionLinter();
 * Document xmlDoc = parseXml(new File("Task.xml"));
 * List<FhirElementLintItem> results = linter.lint(xmlDoc, new File("Task.xml"));
 * 
 * results.forEach(item -> {
 *     if (item.getSeverity() == Severity.ERROR) {
 *         System.err.println(item);
 *     }
 * });
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * This class is stateless and thread-safe. Multiple threads may safely share a single instance.
 *
 * @author DSF Team
 * @see AbstractFhirInstanceLinter
 * @see FhirElementLintItem
 * @see <a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition Specification</a>
 * @see <a href="https://hl7.org/fhir/profiling.html#slice-cardinality">FHIR Profiling §5.1.0.14 – Slice Cardinality</a>
 * @since 1.0
 */
public final class FhirStructureDefinitionLinter extends AbstractFhirInstanceLinter
{
    /*  XPATH SHORTCUTS  */
    private static final String SD_XP           = "/*[local-name()='StructureDefinition']";
    private static final String DIFFERENTIAL_XP = SD_XP + "/*[local-name()='differential']";
    private static final String SNAPSHOT_XP     = SD_XP + "/*[local-name()='snapshot']";
    private static final String ELEMENTS_XP     = DIFFERENTIAL_XP + "/*[local-name()='element']";
    private static final String META_TAG_XP     = SD_XP + "/*[local-name()='meta']/*[local-name()='tag']";
    private static final String DIFF_ELEM_XP = DIFFERENTIAL_XP + "/*[local-name()='element']";
    /*  CONSTANTS  */
    private static final String READ_TAG_SYS = "http://dsf.dev/fhir/CodeSystem/read-access-tag";


    /*  PUBLIC API  */
    /**
     * Determines whether this linter supports the given FHIR XML document.
     *
     * @param doc the parsed FHIR resource document
     * @return {@code true} if the root element is {@code StructureDefinition}, {@code false} otherwise
     */
    @Override
    public boolean canLint(Document doc)
    {
        return "StructureDefinition".equals(doc.getDocumentElement().getLocalName());
    }

    @Override
    public List<FhirElementLintItem> lint(Document doc, File resFile)
    {
        final String ref   = determineRef(doc, resFile);
        final List<FhirElementLintItem> issues = new ArrayList<>();

        /* 1 – meta / basic */
        checkMetaAndBasics(doc, resFile, ref, issues);

        /* 2 – placeholders */
        checkPlaceholders(doc, resFile, ref, issues);

        /* 3 – differential / snapshot / element IDs */
        checkDifferentialSection(doc, resFile, ref, issues);

        /* 4 - slice-count vs. min/max */
        checkSliceCardinality(doc, resFile, ref, issues);
        return issues;
    }

    /* CHECK 1: BASICS  */
    /**
     * lints the presence and correctness of key metadata elements including:
     * <ul>
     *     <li>{@code meta.profile}</li>
     *     <li>{@code meta.tag} for read-access control</li>
     *     <li>{@code url}</li>
     *     <li>{@code status}</li>
     * </ul>
     *
     * @param doc   the StructureDefinition XML document
     * @param file  the original file (used for error messages)
     * @param ref   a human-readable reference derived from the file or resource URL
     * @param out   the list where linting results are added
     */
    private void checkMetaAndBasics(Document doc,
                                    File file,
                                    String ref,
                                    List<FhirElementLintItem> out)
    {
        /* read‑access tag: must be system=READ_TAG_SYS and code ∈ registered CS_READ_ACCESS */
        boolean tagOk = false;
        NodeList tags = xp(doc, META_TAG_XP);
        if (tags != null)
        {
            for (int i = 0; i < tags.getLength(); i++)
            {
                String sys  = val(tags.item(i), "./*[local-name()='system']/@value");
                String code = val(tags.item(i), "./*[local-name()='code']/@value");
                if (READ_TAG_SYS.equals(sys)
                 && !FhirAuthorizationCache.isUnknown(
                        FhirAuthorizationCache.CS_READ_ACCESS, code))
                {
                    tagOk = true;
                    break;
                }
            }
        }
        if (!tagOk)
            out.add(new FhirStructureDefinitionMissingReadAccessTagLintItem(file, ref));
        else
            out.add(ok(file, ref, "meta.tag read‑access‑tag OK (" + tags.getLength() + " tag(s))"));

        /* url */
        String url = val(doc, SD_XP + "/*[local-name()='url']/@value");
        if (blank(url))
            out.add(new FhirStructureDefinitionMissingUrlLintItem(file, ref));
        else
            out.add(ok(file, ref, "url looks good"));

        /* status */
        String status = val(doc, SD_XP + "/*[local-name()='status']/@value");
        if (!"unknown".equals(status))
            out.add(new FhirStructureDefinitionInvalidStatusLintItem(file, ref, status));
        else
            out.add(ok(file, ref, "status = unknown"));
    }

    /*  CHECK 2: PLACEHOLDERS  */
    /**
     * lints that the StructureDefinition's {@code version} and {@code date} elements
     * contain DSF template placeholders {@code #{version}} and {@code #{date}} respectively.
     *
     * @param doc   the StructureDefinition XML document
     * @param file  the original file (used for error messages)
     * @param ref   a human-readable reference derived from the file or resource URL
     * @param out   the list where linting results are added
     */
    private void checkPlaceholders(Document doc,
                                   File file,
                                   String ref,
                                   List<FhirElementLintItem> out)
    {
        /* version */
        String version = val(doc, SD_XP + "/*[local-name()='version']/@value");
        if (version == null || !version.equals("#{version}"))
            out.add(new FhirStructureDefinitionVersionNoPlaceholderLintItem(file, ref, version));
        else
            out.add(ok(file, ref, "version placeholder present"));

        /* date */
        String date = val(doc, SD_XP + "/*[local-name()='date']/@value");
        if (date == null || !date.equals("#{date}"))
            out.add(new FhirStructureDefinitionDateNoPlaceholderLintItem(file, ref, date));
        else
            out.add(ok(file, ref, "date placeholder present"));
    }

    /*  CHECK 3: DIFF / SNAPSHOT / IDs  */
    /**
     * lints the existence of the {@code differential} element, warns if a {@code snapshot}
     * element is found, and checks that all {@code element/@id} values in the differential are
     * present and unique.
     *
     * @param doc   the StructureDefinition XML document
     * @param file  the original file (used for error messages)
     * @param ref   a human-readable reference derived from the file or resource URL
     * @param out   the list where linting results are added
     */
    private void checkDifferentialSection(Document doc,
                                          File file,
                                          String ref,
                                          List<FhirElementLintItem> out)
    {
        NodeList diffNodes = evaluateXPath(doc, DIFFERENTIAL_XP);
        NodeList snapNodes = evaluateXPath(doc, SNAPSHOT_XP);

        boolean hasDiff = diffNodes != null && diffNodes.getLength() > 0;
        boolean hasSnap = snapNodes != null && snapNodes.getLength() > 0;

        if (!hasDiff) {
            out.add(new FhirStructureDefinitionMissingDifferentialLintItem(file, ref));
        } else {
            out.add(ok(file, ref, "differential section present"));
        }

        if (hasSnap) {
            out.add(new FhirStructureDefinitionSnapshotPresentLintItem(file, ref));
        } else {
            out.add(ok(file, ref, "snapshot section absent (OK)"));
        }

        /* element/@id checks */
        NodeList elems = xp(doc, ELEMENTS_XP);
        if (elems == null) return;

        // A flag to track any ID-related errors.
        boolean idErrorFound = false;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < elems.getLength(); i++)
        {
            String id = val(elems.item(i), "./@id");
            if (blank(id))
            {
                out.add(new FhirStructureDefinitionElementWithoutIdLintItem(file, ref));
                idErrorFound = true; // Error found
                continue;
            }
            if (!ids.add(id))
            {
                out.add(new FhirStructureDefinitionDuplicateElementIdLintItem(file, ref, id));
                idErrorFound = true; // Error found
            }
        }

        // A single summary 'ok' message for all ID checks.
        if (!idErrorFound)
        {
            out.add(ok(file, ref, "all " + elems.getLength() + " element/@id attributes are present and unique (OK)"));
        }
    }

    /**
     * Lints slice cardinality rules for elements in a StructureDefinition differential,
     * as specified in the HL7 FHIR profiling standard §5.1.0.14 "Slice Cardinality".
     *
     * <p>When an element with a fixed cardinality <code>m..n</code> is sliced into multiple parts (slices),
     * the following constraints are enforced or recommended by the specification:
     *
     * <ul>
     *   <li><strong>MUST:</strong> The <strong>maximum cardinality of each individual slice</strong> must not exceed <code>n</code>.</li>
     *   <li><strong>MAY:</strong> The <strong>sum of all slice maximums</strong> may exceed <code>n</code> (not an error).</li>
     *   <li><strong>MUST:</strong> The <strong>sum of all slice minimums</strong> must be less than or equal to <code>n</code>.</li>
     *   <li><strong>SHOULD:</strong> The <strong>sum of all slice minimums</strong> should be less than or equal to <code>m</code>.</li>
     *   <li><strong>Allowed:</strong> Individual slices may declare <code>min = 0</code> even if <code>m &gt; 0</code>,
     *       but the total number of actual elements in an instance must still satisfy <code>m</code>.</li>
     * </ul>
     *
     * <p>This method parses the base element's <code>min</code>/<code>max</code> cardinalities and all first-level slice definitions
     * (ignoring nested child paths), and performs the following rule checks:
     *
     * <ul>
     *   <li><strong>Recommended (SHOULD):</strong> Verifies that the sum of all <code>slice.min</code> values is ≤ <code>base.min</code></li>
     *   <li><strong>Required (MUST):</strong> Verifies that the sum of all <code>slice.min</code> values is ≤ <code>base.max</code></li>
     *   <li><strong>Required (MUST):</strong> Verifies that no individual <code>slice.max</code> exceeds <code>base.max</code></li>
     * </ul>
     *
     * <p>All linting results (errors, informational messages, and OK confirmations) are reported via
     * {@link FhirElementLintItem} instances added to the provided output list.
     *
     * @param doc  the DOM XML document representing the StructureDefinition
     * @param file the file where the StructureDefinition was loaded from, for traceability in linting results
     * @param ref  a short string identifying the linting context (e.g., StructureDefinition URL), used in result messages
     * @param out  list to which linting results (errors, warnings, OKs) are appended
     *
     * @see <a href="https://hl7.org/fhir/profiling.html#slicing">FHIR Profiling §5.1.0.14 – Slice Cardinality</a>
     * @see FhirStructureDefinitionSliceMinSumAboveBaseMinLintItem
     * @see FhirStructureDefinitionSliceMinSumExceedsMaxLintItem
     * @see FhirStructureDefinitionSliceMaxExceedsBaseMaxLintItem
     */
    private void checkSliceCardinality(Document doc,
                                       File file,
                                       String ref,
                                       List<FhirElementLintItem> out) {
        NodeList baseElems = xp(doc, DIFF_ELEM_XP);
        if (baseElems == null)
            return;

        for (int i = 0; i < baseElems.getLength(); i++) {
            /*
             * Base element
             */
            String baseId = val(baseElems.item(i), "./@id");
            if (blank(baseId) || baseId.contains(":"))
                continue;

            String baseMinRaw = val(baseElems.item(i), "./*[local-name()='min']/@value");
            boolean baseMinSpecified = baseMinRaw != null;
            int baseMin = parseUnsignedIntOrDefault(baseMinRaw, 0);

            String baseMaxRaw = val(baseElems.item(i), "./*[local-name()='max']/@value");
            boolean baseMaxSpecified = baseMaxRaw != null;
            int baseMax = "*".equals(baseMaxRaw)
                    ? Integer.MAX_VALUE
                    : parseUnsignedIntOrDefault(baseMaxRaw, Integer.MAX_VALUE);

            /*
             * Slice roots
             */
            NodeList sliceCandidates = xp(doc, DIFFERENTIAL_XP +
                    "/*[local-name()='element' and starts-with(@id,'" + baseId + ":')]");
            if (sliceCandidates == null || sliceCandidates.getLength() == 0)
                continue;

            int sumSliceMin = 0;
            int worstSliceMax = 0;
            String offendingSlice = null;

            for (int j = 0; j < sliceCandidates.getLength(); j++) {
                String sliceId = val(sliceCandidates.item(j), "./@id");
                if (sliceId.indexOf('.', baseId.length() + 1) != -1)
                    continue;

                int sliceMin = parseUnsignedIntOrDefault(
                        val(sliceCandidates.item(j), "./*[local-name()='min']/@value"), 0);

                String sliceMaxRaw = val(sliceCandidates.item(j),
                        "./*[local-name()='max']/@value");

                int sliceMax;
                if (sliceMaxRaw == null || sliceMaxRaw.isEmpty()) {
                    // inherited from base element
                    sliceMax = baseMax;
                } else if ("*".equals(sliceMaxRaw)) {
                    sliceMax = Integer.MAX_VALUE;
                } else {
                    sliceMax = parseUnsignedIntOrDefault(sliceMaxRaw, Integer.MAX_VALUE);
                }

                sumSliceMin += sliceMin;
                if (sliceMax > worstSliceMax) {
                    worstSliceMax = sliceMax;
                    offendingSlice = sliceId;
                }
            }

            /*
             * MIN rule
             */
            if (baseMinSpecified) {
                if (sumSliceMin > baseMin)
                    out.add(new FhirStructureDefinitionSliceMinSumAboveBaseMinLintItem(
                            file, ref, baseId, baseMin, sumSliceMin));
                else
                    out.add(ok(file, ref,
                            "element '" + baseId + "': Σ min(" + sumSliceMin +
                                    ") ≤ declared min (" + baseMin + ")"));
            }

            /*
             * MAX rule
             */
            if (baseMaxSpecified && baseMax != Integer.MAX_VALUE) {
                boolean maxSumViolation = sumSliceMin > baseMax;
                boolean sliceMaxViolation = worstSliceMax > baseMax;

                if (sliceMaxViolation) {
                    String label = (worstSliceMax == Integer.MAX_VALUE) ? "*" : String.valueOf(worstSliceMax);
                    out.add(new FhirStructureDefinitionSliceMaxExceedsBaseMaxLintItem(
                            file, ref, baseId, baseMax, offendingSlice, label));
                } else {
                    out.add(ok(file, ref,
                            "element '" + baseId + "': all slice.max ≤ " + baseMax));
                }

                if (maxSumViolation) {
                    out.add(new FhirStructureDefinitionSliceMinSumExceedsMaxLintItem(
                            file, ref, baseId, baseMax, sumSliceMin));
                } else {
                    out.add(ok(file, ref,
                            "element '" + baseId + "': Σ slice.min (" + sumSliceMin + ") ≤ declared max (" + baseMax + ")"));
                }
            } else {
                out.add(ok(file, ref,
                        "element '" + baseId + "': unlimited max → no upper-bound check required"));
            }
        }
    }
            /*  HELPERS  */
    /**
     * Resolves the canonical reference for issue reporting from the StructureDefinition.
     * If the {@code url} element is present, it is used as reference; otherwise,
     * the file name is returned.
     *
     * @param doc   the StructureDefinition document
     * @param file  the original resource file
     * @return a human-readable reference string
     */
    private String determineRef(Document doc, File file)
    {
        String url = val(doc, SD_XP + "/*[local-name()='url']/@value");
        return blank(url) ? file.getName() : url;
    }

    /**
     * Parses the given string as an unsigned integer.
     * <p>
     * If the input string is {@code null}, empty, or cannot be parsed as a valid unsigned integer,
     * the method returns the provided default value.
     * <p>
     * This method is useful in situations where integer values are expected from external sources
     * (e.g. XML attributes) but may be malformed or missing. It provides safe fallback behavior
     * without throwing an exception.
     *
     * @param s the string to parse; may be {@code null} or empty
     * @param defaultVal the fallback value to return if {@code s} is {@code null},
     *                   empty, or not a valid unsigned integer
     * @return the parsed unsigned integer value, or {@code defaultVal} if parsing fails
     *
     * @see Integer#parseUnsignedInt(String)
     */
    private static int parseUnsignedIntOrDefault(String s, int defaultVal)
    {
        if (s == null || s.isEmpty()) return defaultVal;
        try { return Integer.parseUnsignedInt(s); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}