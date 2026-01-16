package dev.dsf.linter.fhir;

import dev.dsf.linter.DsfLinter;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.FhirElementLintItem;
import dev.dsf.linter.util.linting.AbstractFhirInstanceLinter;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

/**
 * Linter implementation for FHIR CodeSystem resources conforming to the DSF codesystem-base profile (version 1.0.0).
 *
 * <p>This linter validates FHIR {@code CodeSystem} resources against DSF-specific requirements.
 * Validation is performed locally using XPath-based structural checks without requiring external
 * services such as a FHIR terminology server. The linter ensures that CodeSystem resources
 * are properly structured and contain all required metadata before deployment to the
 * DSF Business Process Engine (BPE).</p>
 *
 * <h3>Validation Rules</h3>
 *
 * <h4>1. Meta Tag Validation</h4>
 * <p>Ensures that {@code meta.tag} contains the required DSF read-access tag:</p>
 * <ul>
 *   <li>System: {@code http://dsf.dev/fhir/CodeSystem/read-access-tag}</li>
 *   <li>Code: {@code ALL}</li>
 * </ul>
 *
 * <h4>2. Mandatory Element Validation</h4>
 * <p>Verifies the presence of the following required elements:</p>
 * <ul>
 *   <li>{@code url} - Canonical URL of the CodeSystem</li>
 *   <li>{@code name} - Computer-friendly name</li>
 *   <li>{@code title} - Human-friendly title</li>
 *   <li>{@code publisher} - Name of the publishing organization</li>
 *   <li>{@code content} - Content mode</li>
 *   <li>{@code caseSensitive} - Case sensitivity indicator</li>
 * </ul>
 *
 * <h4>3. Status Validation</h4>
 * <p>Confirms that {@code status} is set to {@code unknown}.</p>
 *
 * <h4>4. Placeholder Validation</h4>
 * <p>Ensures that version and date fields are set to their respective DSF template placeholders:</p>
 * <ul>
 *   <li>{@code version} must be exactly {@code #{version}}</li>
 *   <li>{@code date} must be exactly {@code #{date}}</li>
 * </ul>
 *
 * <h4>5. Concept Validation</h4>
 * <p>Validates the concept entries within the CodeSystem:</p>
 * <ul>
 *   <li>At least one {@code concept} element must be present</li>
 *   <li>Each concept must have a {@code code} element</li>
 *   <li>Each concept must have a {@code display} element</li>
 *   <li>All concept codes must be unique within the CodeSystem</li>
 * </ul>
 *
 * @see DsfLinter
 * @see AbstractFhirInstanceLinter
 * @see FhirElementLintItem
 *
 * @since 1.0.0
 */
public final class FhirCodeSystemLinter extends AbstractFhirInstanceLinter
{
    // Root & common XPaths
    private static final String CS_XP             = "/*[local-name()='CodeSystem']";
    private static final String CONCEPT_XP        = CS_XP + "/*[local-name()='concept']";

    // Read‑access‑tag constants (see DSF template)
    private static final String SYSTEM_READ_TAG   = "http://dsf.dev/fhir/CodeSystem/read-access-tag";
    private static final String CODE_READ_TAG     = "ALL";

    /**
     * Determines whether the provided XML document represents a {@code CodeSystem} resource.
     *
     * @param d the XML document to inspect
     * @return {@code true} if the root element is {@code CodeSystem}, {@code false} otherwise
     */
    @Override
    public boolean canLint(Document d)
    {
        return "CodeSystem".equals(d.getDocumentElement().getLocalName());
    }

    /**
     * Lints the given {@code CodeSystem} document for compliance with the DSF profile.
     *
     * @param doc     the parsed FHIR resource document
     * @param resFile the original file containing the document
     * @return a list of lint issues found in the document
     */
    @Override
    public List<FhirElementLintItem> lint(Document doc, File resFile)
    {
        final String ref = computeReference(doc, resFile);
        final List<FhirElementLintItem> issues = new ArrayList<>();

        checkMeta(doc, resFile, ref, issues);
        checkMandatoryElements(doc, resFile, ref, issues);
        checkPlaceholders(doc, resFile, ref, issues);
        checkConcepts(doc, resFile, ref, issues);

        return issues;
    }

    /*
      1) meta.tag with read‑access‑tag = ALL
      */
    /**
     * Checks whether the {@code meta.tag} element contains the expected DSF read-access tag.
     *
     * @param doc  the FHIR document
     * @param f    the source file
     * @param ref  a logical reference for reporting
     * @param out  the list to which lint items are appended
     */
    private void checkMeta(Document doc, File f, String ref, List<FhirElementLintItem> out)
    {
        String tagSys  = val(doc, CS_XP + "/*[local-name()='meta']/*[local-name()='tag']"
                +"/*[local-name()='system']/@value");
        String tagCode = val(doc, CS_XP + "/*[local-name()='meta']/*[local-name()='tag']"
                +"/*[local-name()='code']/@value");

        if (!SYSTEM_READ_TAG.equals(tagSys) || !CODE_READ_TAG.equals(tagCode))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.MISSING_READ_ACCESS_TAG,
                    f, ref, "CodeSystem is missing read-access tag (system='" + SYSTEM_READ_TAG + "', code='" + CODE_READ_TAG + "')."));
        else
            out.add(ok(f, ref, "meta.tag (read‑access‑tag=ALL) present"));
    }

    /*
      2) Presence & basic sanity of key elements
      */
    /**
     * Verifies that all required elements are present and have acceptable values.
     * Also checks that {@code status = unknown}.
     */
    private void checkMandatoryElements(Document doc, File f, String ref, List<FhirElementLintItem> out)
    {
        checkPresent(doc, f, ref, "url", CS_XP + "/*[local-name()='url']/@value", out);
        checkPresent(doc, f, ref, "name", CS_XP + "/*[local-name()='name']/@value", out);
        checkPresent(doc, f, ref, "title", CS_XP + "/*[local-name()='title']/@value", out);
        checkPresent(doc, f, ref, "publisher", CS_XP + "/*[local-name()='publisher']/@value", out);
        checkPresent(doc, f, ref, "content", CS_XP + "/*[local-name()='content']/@value", out);
        checkPresent(doc, f, ref, "caseSensitive", CS_XP + "/*[local-name()='caseSensitive']/@value", out);

        // status must be 'unknown' (BPE overrides later)
        String status = val(doc, CS_XP + "/*[local-name()='status']/@value");
        if (!"unknown".equals(status))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.CODE_SYSTEM_INVALID_STATUS,
                    f, ref, "status must be 'unknown' (found '"+status+"')"));
        else
            out.add(ok(f, ref, "status = unknown"));
    }

    /**
     * Checks whether a specific element is present and non-empty.
     *
     * @param doc   the document
     * @param f     the file
     * @param ref   a logical reference for reporting
     * @param name  the name of the element (for messages)
     * @param xp    the XPath to locate the element
     * @param out   the list to which lint items are appended
     */
    private void checkPresent(Document doc, File f, String ref,
                              String name, String xp, List<FhirElementLintItem> out)
    {
        if (blank(val(doc, xp)))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.CODE_SYSTEM_MISSING_ELEMENT,
                    f, ref, "CodeSystem is missing element: " + name));
        else
            out.add(ok(f, ref, name + " present"));
    }

    /*
      3) Placeholder checks (#{version}, #{date})
      */
    /**
     * lints that version and date fields contain their respective DSF placeholders.
     */
    private void checkPlaceholders(Document doc, File f, String ref, List<FhirElementLintItem> out)
    {
        String version = val(doc, CS_XP + "/*[local-name()='version']/@value");
        if (version != null && !version.equals("#{version}"))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.CODE_SYSTEM_VERSION_NO_PLACEHOLDER,
                    f, ref, "<version> must contain '#{version}'"));
        else
            out.add(ok(f, ref, "<version> placeholder OK"));

        String date = val(doc, CS_XP + "/*[local-name()='date']/@value");
        if (date != null && !date.equals("#{date}"))
            out.add(new FhirElementLintItem(LinterSeverity.WARN, LintingType.CODE_SYSTEM_DATE_NO_PLACEHOLDER,
                    f, ref, "<date> must contain '#{date}'"));
        else
            out.add(ok(f, ref, "<date> placeholder OK"));
    }

    /*
      4) Concept checks (code + display + uniqueness)
      */
    /**
     * Verifies that each concept entry includes a {@code code} and {@code display},
     * and that all {@code code} values are unique.
     */
    private void checkConcepts(Document doc, File f, String ref, List<FhirElementLintItem> out)
    {
        NodeList concepts = xp(doc, CONCEPT_XP);
        if (concepts == null || concepts.getLength() == 0)
        {
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.CODE_SYSTEM_MISSING_CONCEPT,
                    f, ref, "CodeSystem must contain at least one concept."));
            return;
        }

        Set<String> seenCodes = new HashSet<>();

        for (int i = 0; i < concepts.getLength(); i++)
        {
            Node c = concepts.item(i);
            String code    = val(c, "./*[local-name()='code']/@value");
            String display = val(c, "./*[local-name()='display']/@value");

            if (blank(code))
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.CODE_SYSTEM_CONCEPT_MISSING_CODE,
                        f, ref, "CodeSystem concept is missing code."));
            else if (seenCodes.contains(code))
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.CODE_SYSTEM_DUPLICATE_CODE,
                        f, ref, "CodeSystem has duplicate code: " + code));
            else
                seenCodes.add(code);

            if (blank(display))
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.CODE_SYSTEM_CONCEPT_MISSING_DISPLAY,
                        f, ref, "CodeSystem concept is missing display."));
        }

        if (seenCodes.size() == concepts.getLength())
            out.add(ok(f, ref, "all concept codes unique ("+seenCodes.size()+")"));
    }

    /*
      Helper: use url (or filename) as reference for result messages
      */
    /**
     * Computes a reference string used in linter messages, preferring {@code url} over file name.
     *
     * @param doc  the parsed XML document
     * @param file the file the resource was loaded from
     * @return a user-friendly reference (canonical URL or filename)
     */
    private String computeReference(Document doc, File file)
    {
        String url = val(doc, CS_XP + "/*[local-name()='url']/@value");
        return !blank(url) ? url : file.getName();
    }
}
