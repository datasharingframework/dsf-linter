package dev.dsf.linter.fhir;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.FhirElementLintItem;
import dev.dsf.linter.util.linting.AbstractFhirInstanceLinter;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Linter for FHIR Questionnaire resources that conform to the DSF questionnaire profile.
 *
 * <p>This linter validates FHIR {@code Questionnaire} resources against the DSF profile
 * {@code http://dsf.dev/fhir/StructureDefinition/questionnaire|X.Y.Z}. It performs comprehensive
 * structural, semantic, and DSF-specific validation to ensure questionnaires are correctly
 * configured for use within the Data Sharing Framework (DSF) and its Business Process Engine (BPE).</p>
 *
 * <h2>Validation Scope</h2>
 *
 * <h3>1. Metadata and Profile Compliance</h3>
 * <ul>
 *   <li>Validates presence and format of {@code meta.profile} element with versioned URI pattern
 *       {@code http://dsf.dev/fhir/StructureDefinition/questionnaire|X.Y.Z}</li>
 *   <li>Verifies {@code meta.tag} contains a valid read-access-tag with system
 *       {@code http://dsf.dev/fhir/CodeSystem/read-access-tag} and one of the permitted codes:
 *       {@code ALL}, {@code LOCAL}, {@code ORGANIZATION}, or {@code ROLE}</li>
 *   <li>Enforces {@code status} element value to be {@code unknown} (DSF convention for development)</li>
 * </ul>
 *
 * <h3>2. Development Placeholder Enforcement</h3>
 * <ul>
 *   <li>Ensures {@code version} element contains the Maven placeholder {@code #{version}}</li>
 *   <li>Ensures {@code date} element contains the Maven placeholder {@code #{date}}</li>
 * </ul>
 *
 * <h3>3. Item Structure Validation</h3>
 * <ul>
 *   <li><strong>Mandatory attributes:</strong> Each {@code item} must declare {@code linkId} and
 *       {@code type}. Missing {@code text} triggers a warning as it's strongly recommended.</li>
 *   <li><strong>Unique linkIds:</strong> All {@code linkId} values must be unique across the
 *       questionnaire. Duplicates are reported as errors.</li>
 *   <li><strong>linkId pattern compliance:</strong> Recommends the pattern {@code [a-z0-9]+(-[a-z0-9]+)*}
 *       for linkIds and issues warnings for non-conformant patterns.</li>
 *   <li><strong>Mandatory item {@code user-task-id}:</strong> Must exist, be of type {@code string},
 *       and have {@code required="true"}. This item is essential for DSF workflow integration.</li>
 * </ul>
 *
 * <h2>Error Reporting</h2>
 * <p>The linter produces detailed {@link FhirElementLintItem} instances for each validation issue,
 * including success items for valid elements. This approach provides comprehensive feedback and
 * maintains consistency with other DSF linters such as {@link FhirTaskLinter}.</p>
 *
 * <h2>Integration Notes</h2>
 * <ul>
 *   <li>This linter operates on XML DOM representations of FHIR Questionnaire resources.</li>
 *   <li>It extends {@link AbstractFhirInstanceLinter} and uses XPath expressions for element extraction.</li>
 *   <li>File existence validation for questionnaires referenced in BPMN processes is handled by the
 *       BPMN linter, not by this class.</li>
 * </ul>
 *
 * @see AbstractFhirInstanceLinter
 * @see FhirTaskLinter
 * @see FhirElementLintItem
 */
public final class FhirQuestionnaireLinter extends AbstractFhirInstanceLinter
{
    /*
     XPath constants
     */
    private static final String Q_XP        = "/*[local-name()='Questionnaire']";
    private static final String ITEM_XP     = Q_XP + "/*[local-name()='item']";
    private static final String META_PRO_XP = Q_XP + "/*[local-name()='meta']/*[local-name()='profile']/@value";
    private static final String META_TAG_XP = Q_XP + "/*[local-name()='meta']/*[local-name()='tag']";

    private static final String READ_TAG_SYS  = "http://dsf.dev/fhir/CodeSystem/read-access-tag";
    private static final String PROFILE_URI   = "http://dsf.dev/fhir/StructureDefinition/questionnaire";

    // Regex for the profile URI to ensure it strictly matches "http://dsf.dev/fhir/StructureDefinition/questionnaire|X.Y.Z"
    // where X.Y.Z is a version number.
    private static final Pattern PROFILE_URI_PATTERN = Pattern.compile("^" + Pattern.quote(PROFILE_URI) + "\\|\\d+\\.\\d+\\.\\d+$");


    /* Mandatory items defined by the DSF template */
    private static final String LINKID_USERTASK = "user-task-id";

    /*
    valid values for system code
     */
    private static final Set<String> VALID_READ_ACCESS_CODES =
            Set.of("ALL", "LOCAL", "ORGANIZATION", "ROLE");
    /*
     Entry points
     */

    @Override
    public boolean canLint(Document document)
    {
        return "Questionnaire".equals(document.getDocumentElement().getLocalName());
    }

    @Override
    public List<FhirElementLintItem> lint(Document doc, File resourceFile)
    {
        final String ref = computeReference(doc, resourceFile);
        final List<FhirElementLintItem> issues = new ArrayList<>();

        checkMetaAndBasics(doc, resourceFile, ref, issues);
        checkPlaceholders(doc, resourceFile, ref, issues);
        lintItems(doc, resourceFile, ref, issues);

        return issues;
    }

    /*
     1) meta & basic elements
    */

    private void checkMetaAndBasics(Document doc, File file, String ref,
                                    List<FhirElementLintItem> out)
    {
        /* meta.profile  */
        String profile = val(doc, META_PRO_XP);
        if (blank(profile))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_MISSING_META_PROFILE,
                    file, ref, "Questionnaire is missing meta.profile."));
            // Modified to use regex matching
        else if (!PROFILE_URI_PATTERN.matcher(profile).matches())
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_INVALID_META_PROFILE,
                    file, ref, "Questionnaire has invalid meta.profile: " + profile));
        else
            out.add(ok(file, ref, "meta.profile present and valid"));

        /* meta.tag (read‑access ALL)  */
        boolean tagOk = false;
        NodeList tags = xp(doc, META_TAG_XP);
        if (tags != null) {
            for (int i = 0; i < tags.getLength(); i++) {
                String sys  = val(tags.item(i), "./*[local-name()='system']/@value");
                String code = val(tags.item(i), "./*[local-name()='code']/@value");
                if (READ_TAG_SYS.equals(sys) && VALID_READ_ACCESS_CODES.contains(code)){
                    tagOk = true; break;
                }
            }
        }
        if (!tagOk)
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_MISSING_READ_ACCESS_TAG,
                    file, ref, "Questionnaire is missing valid read-access tag."));
        else
            out.add(ok(file, ref, "read‑access tag present"));

        /* status must be 'unknown'  */
        String status = val(doc, Q_XP + "/*[local-name()='status']/@value");
        if (!"unknown".equals(status))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_INVALID_STATUS,
                    file, ref, "Questionnaire status must be 'unknown' (found '" + status + "')."));
        else
            out.add(ok(file, ref, "status = unknown"));
    }

    /*
     2) placeholder linting
    */

    private void checkPlaceholders(Document doc, File file, String ref,
                                   List<FhirElementLintItem> out)
    {
        String version = val(doc, Q_XP + "/*[local-name()='version']/@value");
        if (version == null || !version.equals("#{version}"))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_VERSION_NO_PLACEHOLDER,
                    file, ref, "Questionnaire version must be '#{version}'."));
        else
            out.add(ok(file, ref, "version placeholder present"));

        String date = val(doc, Q_XP + "/*[local-name()='date']/@value");
        if (date == null || !date.equals("#{date}"))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_DATE_NO_PLACEHOLDER,
                    file, ref, "Questionnaire date must be '#{date}'."));
        else
            out.add(ok(file, ref, "date placeholder present"));
    }

    /*
     3) item lint
    */

    /**
     * lints the {@code item} elements of a FHIR {@code Questionnaire} resource.
     *
     * <p>This method enforces the structural and semantic rules defined by the
     * DSF questionnaire profile.</p>
     *
     * @param doc  the parsed DOM {@link Document} representing the Questionnaire XML
     * @param file the file the resource was loaded from (used in issue reporting)
     * @param ref  a canonical reference to the resource (e.g., {@code url} or file name)
     * @param out  the list of lint items to which results (errors, warnings, successes) will be appended
     */
    private void lintItems(Document doc, File file, String ref,
                           List<FhirElementLintItem> out)

    {
        NodeList items = xp(doc, ITEM_XP);
        if (items == null || items.getLength() == 0) {
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_MISSING_ITEM,
                    file, ref, "Questionnaire must contain at least one item."));
            return;
        }
        Set<String> linkIds = new HashSet<>();

        for (int i = 0; i < items.getLength(); i++) {
            Node item = items.item(i);
            String linkId   = primitive(item, "linkId");
            String type     = primitive(item, "type");
            String text     = primitive(item, "text");
            String required = primitive(item, "required");

            /* completeness per item  */
            if (blank(linkId)) {
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_LINK_ID,
                        file, ref, "Questionnaire item is missing linkId."));
                continue;
            }

            if(blank(type)) {
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TYPE,
                        file, ref, "Questionnaire item is missing type."));
                continue;
            }

            if (blank(text)) {
                out.add(new FhirElementLintItem(LinterSeverity.INFO, LintingType.QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TEXT,
                        file, ref, "Questionnaire item '" + linkId + "' is missing text."));
            }

            /* duplicate linkIds  */
            if (!linkIds.add(linkId))
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_DUPLICATE_LINK_ID,
                        file, ref, "Questionnaire has duplicate linkId: " + linkId));

            /* warn on unusual pattern  */
            if (!linkId.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"))
                out.add(new FhirElementLintItem(LinterSeverity.WARN, LintingType.QUESTIONNAIRE_UNUSUAL_LINK_ID,
                        file, ref, "Questionnaire linkId '" + linkId + "' does not match recommended pattern [a-z0-9]+(-[a-z0-9]+)*."));

            /* mandatory items  */
            if (LINKID_USERTASK.equals(linkId)) {
                lintMandatoryItem(file, ref, out, linkId, type, required);
            } else {
                out.add(ok(file, ref, "item '" + linkId + "' looks good"));
            }
        }
    }

    private void lintMandatoryItem(File file, String ref,
                                   List<FhirElementLintItem> out,
                                   String linkId, String type, String required)
    {
        if (!"string".equals(type))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_MANDATORY_ITEM_INVALID_TYPE,
                    file, ref, "Mandatory item '" + linkId + "' must be of type 'string' (found '" + type + "')."));
        else if (!"true".equals(required))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.QUESTIONNAIRE_MANDATORY_ITEM_NOT_REQUIRED,
                    file, ref, "Mandatory item '" + linkId + "' must have required='true'."));
        else
            out.add(ok(file, ref, "mandatory item '" + linkId + "' valid"));
    }

    /*
     4) helper methods
    */

    /**
     * Computes a reference string for the FHIR resource.
     *
     * @param doc  the XML {@link Document} representing the FHIR resource
     * @param file the file from which the resource was loaded
     * @return the canonical {@code url} value if present, otherwise the file name
     */
    private String computeReference(Document doc, File file)
    {
        String url = val(doc, Q_XP + "/*[local-name()='url']/@value");
        return !blank(url) ? url : file.getName();
    }

    /**
     * Extracts the value of a FHIR primitive element.
     *
     * @param item the XML {@link Node} representing an {@code item} element
     * @param name the name of the primitive field to extract
     * @return the string value of the primitive field, or {@code null} if not found
     */
    private String primitive(Node item, String name) {
        String v = val(item, "./*[local-name()='" + name + "']/@value");
        return blank(v) ? val(item, "./@" + name) : v;
    }
}
