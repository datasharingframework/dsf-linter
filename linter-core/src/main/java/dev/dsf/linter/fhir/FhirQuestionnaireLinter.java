package dev.dsf.linter.fhir;

import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.linting.AbstractFhirInstanceLinter;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

/**
 * <h2>DSF Questionnaire linter (Profile: dsf-questionnaire 1.5.0)</h2>
 *
 * <p>lints FHIR {@code Questionnaire} resources that implement the
 * DSF profile <code>http://dsf.dev/fhir/StructureDefinition/questionnaire|1.5.0</code>.
 * The linter checks structural integrity, DSF‑specific conventions and
 * placeholder usage that is later resolved by the BPE.</p>
 *
 * <h3>Linter features</h3>
 * <ul>
 * <li><strong>Meta &amp; basic checks</strong> – presence/accuracy of <code>meta.profile</code>,
 *     <code>meta.tag[read-access-tag]</code>, <code>url</code>, <code>version</code>,
 *     <code>date</code> and <code>status</code>.</li>
 * <li><strong>Placeholder enforcement</strong> – verifies that
 *     <code>version</code> and <code>date</code> still contain the development
 *     placeholders <code>#{version}</code> and <code>#{date}</code>.</li>
 * <li><strong>Item lint</strong> – ensures the mandatory item
 *     <code>user-task-id</code> exists, has
 *     type <em>string</em> and is flagged <em>required=true</em>.
 *     Checks that every other item contains the mandatory attributes
 *     <code>linkId</code>, <code>type</code> and <code>text</code>, that
 *     linkIds are unique, and warns on non‑conformant linkId patterns.</li>
 * <li><strong>Duplicate linkId detection</strong>.</li>
 * <li><strong>Success reporting</strong> – mirrors the “success item”
 *      approach of {@link FhirTaskLinter} for a consistent report layout.</li>
 * </ul>
 *
 * <p><strong>Important:</strong>  The BPMN linter already makes sure that
 * for every <em>camunda:formKey</em> used in a User Task a matching
 * Questionnaire file exists. Therefore, this class does <em>not</em> repeat
 * that existence check.</p>
 *
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
    private static final String URL_PREFIX    = "http://dsf.dev/fhir/Questionnaire/";

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
            out.add(new FhirQuestionnaireMissingMetaProfileLintItem(file, ref));
            // Modified to use regex matching
        else if (!PROFILE_URI_PATTERN.matcher(profile).matches())
            out.add(new FhirQuestionnaireInvalidMetaProfileLintItem(file, ref, profile));
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
            out.add(new FhirQuestionnaireMissingReadAccessTagLintItem(file, ref));
        else
            out.add(ok(file, ref, "read‑access tag present"));

        /* status must be 'unknown'  */
        String status = val(doc, Q_XP + "/*[local-name()='status']/@value");
        if (!"unknown".equals(status))
            out.add(new FhirQuestionnaireInvalidStatusLintItem(file, ref, status));
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
            out.add(new FhirQuestionnaireVersionNoPlaceholderLintItem(file, ref));
        else
            out.add(ok(file, ref, "version placeholder present"));

        String date = val(doc, Q_XP + "/*[local-name()='date']/@value");
        if (date == null || !date.equals("#{date}"))
            out.add(new FhirQuestionnaireDateNoPlaceholderLintItem(file, ref));
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
     * DSF questionnaire profile. It performs the following checks on each item:</p>
     *
     * <ul>
     *   <li><b>Presence of required attributes:</b> Each item must declare {@code linkId}, {@code type}, and should have {@code text}.
     *       If any of these are missing or empty, an error is reported.</li>
     *   <li><b>Uniqueness of {@code linkId}:</b> All {@code linkId} values must be unique across the questionnaire.</li>
     *   <li><b>Pattern check for {@code linkId}:</b> A warning is issued if a {@code linkId} does not match the preferred pattern
     *       {@code [a-z0-9-]+}.</li>
     *   <li><b>Mandatory items:</b> The two required items {@code business-key} and {@code user-task-id} must:
     *     <ul>
     *       <li>Exist in the resource</li>
     *       <li>Be of type {@code string}</li>
     *       <li>Be marked with {@code required="true"}</li>
     *     </ul>
     *     Missing or malformed mandatory items result in errors.
     *   </li>
     *   <li><b>Success reporting:</b> Each correctly formed item is acknowledged with a success message.</li>
     * </ul>
     *
     * <p>If no {@code item} elements are present at all, a single error is reported and linting stops.</p>
     *
     * @param doc  the parsed DOM {@link Document} representing the Questionnaire XML
     * @param file the file the resource was loaded from (used in issue reporting)
     * @param ref  a canonical reference to the resource (e.g., {@code url} or file name)
     * @param out  the list of lint items to which results (errors, warnings, successes) will be appended
     *
     * @see #lintMandatoryItem(File, String, List, String, String, String)
     */
    private void lintItems(Document doc, File file, String ref,
                           List<FhirElementLintItem> out)

    {
        NodeList items = xp(doc, ITEM_XP);
        if (items == null || items.getLength() == 0) {
            out.add(new FhirQuestionnaireMissingItemLintItem(file, ref));
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
                out.add(new FhirQuestionnaireItemMissingAttributesLinkIdLintItem(file, ref));
                continue;
            }

            if(blank(type)) {
                out.add(new FhirQuestionnaireItemMissingAttributesTypeLintItem(file, ref));
                continue;
            }

            if (blank(text)) {
                out.add(new FhirQuestionnaireItemMissingAttributesTextLintItem(file, ref));
            }

            /* duplicate linkIds  */
            if (!linkIds.add(linkId))
                out.add(new FhirQuestionnaireDuplicateLinkIdLintItem(file, ref, linkId));

            /* warn on unusual pattern  */
            if (!linkId.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"))
                out.add(new FhirQuestionnaireUnusualLinkIdLintItem(file, ref, linkId));

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
            out.add(new FhirQuestionnaireMandatoryItemInvalidTypeLintItem(file, ref, linkId, type));
        else if (!"true".equals(required))
            out.add(new FhirQuestionnaireMandatoryItemNotRequiredLintItem(file, ref, linkId));
        else
            out.add(ok(file, ref, "mandatory item '" + linkId + "' valid"));
    }

    /*
     4) helper methods
    */

    /**
     * Computes a reference string for the FHIR resource.
     * <p>
     * If the {@code url} element is present in the {@code Questionnaire} root element, its value is returned.
     * Otherwise, the file name is used as a fallback reference.
     * </p>
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
     * Extracts the value of a FHIR primitive element (e.g., {@code linkId}, {@code type}, {@code text}, {@code required}).
     * <p>
     * First, this method tries to retrieve the value via a child element like
     * {@code <linkId value="..."/>}. If that fails (e.g., in legacy representations),
     * it falls back to reading an attribute directly from the current node
     * (e.g., {@code <item linkId="..."/>}).
     * </p>
     *
     * @param item the XML {@link Node} representing an {@code item} element
     * @param name the name of the primitive field to extract (e.g., {@code linkId}, {@code type})
     * @return the string value of the primitive field, or {@code null} if not found
     */
    private String primitive(Node item, String name) {
        String v = val(item, "./*[local-name()='" + name + "']/@value");
        return blank(v) ? val(item, "./@" + name) : v;
    }
}