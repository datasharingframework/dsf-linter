package dev.dsf.utils.validator.fhir;

import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.AbstractFhirInstanceValidator;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

/**
 * Validates FHIR {@code StructureDefinition} resources for DSF-specific constraints and authoring rules.
 * <p>
 * This validator is part of the DSF validation toolchain and ensures that {@code StructureDefinition} resources
 * comply with structural, semantic, and naming conventions required by the DSF platform.
 * The class extends {@link AbstractFhirInstanceValidator} and provides detailed feedback
 * through instances of {@link dev.dsf.utils.validator.item.FhirElementValidationItem}.
 * </p>
 *
 * <h2>Supported Validations</h2>
 * The validator performs the following checks:
 * <ul>
 *   <li><b>Meta Information:</b> Ensures that <code>meta.tag</code> are correctly set.</li>
 *   <li><b>Placeholders:</b> Validates that <code>version</code> and <code>date</code> fields include the required placeholders <code>#{version}</code> and <code>#{date}</code>.</li>
 *   <li><b>Differential Integrity:</b> Ensures the presence of a <code>differential</code> section and the absence of a <code>snapshot</code>, and that all element <code>@id</code> values are unique.</li>
 *   <li><b>Slice Cardinality:</b> Validates slice min/max constraints as defined in <a href="https://hl7.org/fhir/profiling.html#slice-cardinality">FHIR §5.1.0.14</a>.</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>
 * {@code
 * Document xml = parseStructureDefinitionXml(...);
 * List<FhirElementValidationItem> results = new FhirStructureDefinitionValidator().validate(xml, new File("task-structure.xml"));
 * results.forEach(System.out::println);
 * }
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * This class is stateless and thread-safe.
 *
 * @author DSF Team
 * @see <a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a>
 * @see <a href="https://hl7.org/fhir/profiling.html#slice-cardinality">FHIR Profiling §5.1.0.14</a>
 * @see AbstractFhirInstanceValidator
 * @see dev.dsf.utils.validator.item.FhirElementValidationItem
 */
public final class FhirStructureDefinitionValidator extends AbstractFhirInstanceValidator
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
    private static final String URL_PREFIX   = "http://dsf.dev/fhir/StructureDefinition/";

    /*  PUBLIC API  */
    /**
     * Determines whether this validator supports the given FHIR XML document.
     *
     * @param doc the parsed FHIR resource document
     * @return {@code true} if the root element is {@code StructureDefinition}, {@code false} otherwise
     */
    @Override
    public boolean canValidate(Document doc)
    {
        return "StructureDefinition".equals(doc.getDocumentElement().getLocalName());
    }

    @Override
    public List<FhirElementValidationItem> validate(Document doc, File resFile)
    {
        final String ref   = determineRef(doc, resFile);
        final List<FhirElementValidationItem> issues = new ArrayList<>();

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
     * Validates the presence and correctness of key metadata elements including:
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
     * @param out   the list where validation results are added
     */
    private void checkMetaAndBasics(Document doc,
                                    File file,
                                    String ref,
                                    List<FhirElementValidationItem> out)
    {
        /* read-access tag */
        boolean tagOk = false;
        NodeList tags = xp(doc, META_TAG_XP);
        if (tags != null)
        {
            for (int i = 0; i < tags.getLength(); i++)
            {
                String sys  = val(tags.item(i), "./*[local-name()='system']/@value");
                String code = val(tags.item(i), "./*[local-name()='code']/@value");
                if (READ_TAG_SYS.equals(sys) && "ALL".equals(code))
                {
                    tagOk = true;
                    break;
                }
            }
        }
        if (!tagOk)
            out.add(new FhirStructureDefinitionMissingReadAccessTagItem(file, ref));
        else
            out.add(ok(file, ref, "read-access tag (ALL) present"));

        /* url */
        String url = val(doc, SD_XP + "/*[local-name()='url']/@value");
        if (blank(url))
            out.add(new FhirStructureDefinitionMissingUrlItem(file, ref));
        else if (!url.startsWith(URL_PREFIX))
            out.add(new FhirStructureDefinitionInvalidUrlItem(file, ref, url));
        else
            out.add(ok(file, ref, "url looks good"));

        /* status */
        String status = val(doc, SD_XP + "/*[local-name()='status']/@value");
        if (!"unknown".equals(status))
            out.add(new FhirStructureDefinitionInvalidStatusItem(file, ref, status));
        else
            out.add(ok(file, ref, "status = unknown"));
    }

    /*  CHECK 2: PLACEHOLDERS  */
    /**
     * Validates that the StructureDefinition's {@code version} and {@code date} elements
     * contain DSF template placeholders {@code #{version}} and {@code #{date}} respectively.
     *
     * @param doc   the StructureDefinition XML document
     * @param file  the original file (used for error messages)
     * @param ref   a human-readable reference derived from the file or resource URL
     * @param out   the list where validation results are added
     */
    private void checkPlaceholders(Document doc,
                                   File file,
                                   String ref,
                                   List<FhirElementValidationItem> out)
    {
        /* version */
        String version = val(doc, SD_XP + "/*[local-name()='version']/@value");
        if (version == null || !version.equals("#{version}"))
            out.add(new FhirStructureDefinitionVersionNoPlaceholderItem(file, ref, version));
        else
            out.add(ok(file, ref, "version placeholder present"));

        /* date */
        String date = val(doc, SD_XP + "/*[local-name()='date']/@value");
        if (date == null || !date.equals("#{date}"))
            out.add(new FhirStructureDefinitionDateNoPlaceholderItem(file, ref, date));
        else
            out.add(ok(file, ref, "date placeholder present"));
    }

    /*  CHECK 3: DIFF / SNAPSHOT / IDs  */
    /**
     * Validates the existence of the {@code differential} element, warns if a {@code snapshot}
     * element is found, and checks that all {@code element/@id} values in the differential are
     * present and unique.
     *
     * @param doc   the StructureDefinition XML document
     * @param file  the original file (used for error messages)
     * @param ref   a human-readable reference derived from the file or resource URL
     * @param out   the list where validation results are added
     */
    private void checkDifferentialSection(Document doc,
                                          File file,
                                          String ref,
                                          List<FhirElementValidationItem> out)
    {
        boolean hasDiff = evaluateXPath(doc, DIFFERENTIAL_XP) != null;
        boolean hasSnap = evaluateXPath(doc, SNAPSHOT_XP)     != null;

        if (!hasDiff)
        {
            out.add(new FhirStructureDefinitionMissingDifferentialItem(file, ref));
            return;
        }
        else
            out.add(ok(file, ref, "differential section present"));

        if (hasSnap)
            out.add(new FhirStructureDefinitionSnapshotPresentItem(file, ref));

        /* element/@id checks */
        NodeList elems = xp(doc, ELEMENTS_XP);
        if (elems == null) return;

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < elems.getLength(); i++)
        {
            String id = val(elems.item(i), "./@id");
            if (blank(id))
            {
                out.add(new FhirStructureDefinitionElementWithoutIdItem(file, ref));
                continue;
            }
            if (!ids.add(id))
                out.add(new FhirStructureDefinitionDuplicateElementIdItem(file, ref, id));
        }
    }

    /**
     * Validates slice cardinality rules for elements in a StructureDefinition differential,
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
     * <p>All validation results (errors, informational messages, and OK confirmations) are reported via
     * {@link dev.dsf.utils.validator.item.FhirElementValidationItem} instances added to the provided output list.
     *
     * @param doc  the DOM XML document representing the StructureDefinition
     * @param file the file where the StructureDefinition was loaded from, for traceability in validation results
     * @param ref  a short string identifying the validation context (e.g., StructureDefinition URL), used in result messages
     * @param out  list to which validation results (errors, warnings, OKs) are appended
     *
     * @see <a href="https://hl7.org/fhir/profiling.html#slicing">FHIR Profiling §5.1.0.14 – Slice Cardinality</a>
     * @see FhirStructureDefinitionSliceMinSumAboveBaseMinItem
     * @see FhirStructureDefinitionSliceMinSumExceedsMaxItem
     * @see FhirStructureDefinitionSliceMaxExceedsBaseMaxItem
     */
    private void checkSliceCardinality(Document doc,
                                       File file,
                                       String ref,
                                       List<FhirElementValidationItem> out) {
        NodeList baseElems = xp(doc, DIFF_ELEM_XP);
        if (baseElems == null)
            return;

        for (int i = 0; i < baseElems.getLength(); i++) {
            /*
             base element
              */
            String baseId = val(baseElems.item(i), "./@id");
            if (blank(baseId) || baseId.contains(":"))
                continue;

            int baseMin = parseUnsignedIntOrDefault(
                    val(baseElems.item(i), "./*[local-name()='min']/@value"), 0);

            String baseMaxRaw = val(baseElems.item(i), "./*[local-name()='max']/@value");
            int baseMax = "*".equals(baseMaxRaw)
                    ? Integer.MAX_VALUE
                    : parseUnsignedIntOrDefault(baseMaxRaw, Integer.MAX_VALUE);
            boolean baseMinSpecified = val(baseElems.item(i),
                    "./*[local-name()='min']") != null;
            boolean baseMaxSpecified = val(baseElems.item(i),
                    "./*[local-name()='max']") != null;

            /*
             slice roots
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
              MIN rule
              */
            if (baseMinSpecified) {
                if (sumSliceMin > baseMin)
                    out.add(new FhirStructureDefinitionSliceMinSumAboveBaseMinItem(
                            file, ref, baseId, baseMin, sumSliceMin));
                else
                    out.add(ok(file, ref,
                            "element '" + baseId + "': Σ min(" + sumSliceMin +
                                    ") ≤ declared min (" + baseMin + ")"));
            }

            /*
              MAX rule
              */
            if (baseMaxSpecified && baseMax != Integer.MAX_VALUE) {
                boolean maxSumViolation = sumSliceMin > baseMax;
                boolean sliceMaxViolation = worstSliceMax > baseMax;

                if (sliceMaxViolation) {
                    String label = (worstSliceMax == Integer.MAX_VALUE) ? "*" : String.valueOf(worstSliceMax);
                    out.add(new FhirStructureDefinitionSliceMaxExceedsBaseMaxItem(
                            file, ref, baseId, baseMax, offendingSlice, label));
                } else {
                    out.add(ok(file, ref,
                            "element '" + baseId + "': all slice.max ≤ " + baseMax));
                }

                if (maxSumViolation) {
                    out.add(new FhirStructureDefinitionSliceMinSumExceedsMaxItem
                            (
                                    file, ref, baseId, baseMax, sumSliceMin));
                } else {
                    out.add(ok(file, ref,
                            "element '" + baseId + "': Σ slice.min (" + sumSliceMin + ") ≤ declared max (" + baseMax + ")"));
                }
            } else if (baseMax == Integer.MAX_VALUE) {
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