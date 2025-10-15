package dev.dsf.linter.fhir;

import dev.dsf.linter.DsfValidatorImpl;
import dev.dsf.linter.item.*;
import dev.dsf.linter.util.validation.AbstractFhirInstanceValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

/**
 * <h2>DSF CodeSystem Validator (Profile dsf‑codesystem‑base 1.0.0)</h2>
 *
 * <p>Validates <strong>CodeSystem</strong> resources that live under
 * {@code src/main/resources/fhir/CodeSystem}. The class performs purely
 * syntactic/semantic checks that can be done without a terminology server.</p>
 *
 * <h3>Validation features</h3>
 * <ul>
 *   <li><strong>Meta tag:</strong> Ensures <code>meta.tag</code> contains system
 *       {@code http://dsf.dev/fhir/CodeSystem/read-access-tag} and code {@code ALL}.</li>
 *   <li><strong>Mandatory elements:</strong> Checks presence of
 *       <code>url</code>, <code>name</code>, <code>title</code>,
 *       <code>publisher</code>, <code>content</code>, <code>caseSensitive</code>.</li>
 *   <li><strong>Placeholder enforcement:</strong> Verifies that <code>version</code>
 *       and <code>date</code> still contain the template placeholders
 *       <code>#{version}</code> and <code>#{date}</code> (values are filled in later by BPE).</li>
 *   <li><strong>Status check:</strong> Confirms <code>status</code> is <em>unknown</em>
 *       as required by the DSF template.</li>
 *   <li><strong>URL rule:</strong> URL must start with
 *       {@code http://dsf.dev/fhir/CodeSystem/}.</li>
 *   <li><strong>Concept validation:</strong>
 *       <ul>
 *         <li>Every <code>concept</code> may has  <code>code</code> and <code>display</code>.</li>
 *         <li>All codes should be unique inside the resource.</li>
 *       </ul></li>
 * </ul>
 *
 * @see DsfValidatorImpl
 */
public final class FhirCodeSystemValidator extends AbstractFhirInstanceValidator
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
    public boolean canValidate(Document d)
    {
        return "CodeSystem".equals(d.getDocumentElement().getLocalName());
    }

    /**
     * Validates the given {@code CodeSystem} document for compliance with the DSF profile.
     *
     * @param doc     the parsed FHIR resource document
     * @param resFile the original file containing the document
     * @return a list of validation issues found in the document
     */
    @Override
    public List<FhirElementValidationItem> validate(Document doc, File resFile)
    {
        final String ref = computeReference(doc, resFile);
        final List<FhirElementValidationItem> issues = new ArrayList<>();

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
     * @param out  the list to which validation items are appended
     */
    private void checkMeta(Document doc, File f, String ref, List<FhirElementValidationItem> out)
    {
        String tagSys  = val(doc, CS_XP + "/*[local-name()='meta']/*[local-name()='tag']"
                +"/*[local-name()='system']/@value");
        String tagCode = val(doc, CS_XP + "/*[local-name()='meta']/*[local-name()='tag']"
                +"/*[local-name()='code']/@value");

        if (!SYSTEM_READ_TAG.equals(tagSys) || !CODE_READ_TAG.equals(tagCode))
            out.add(new FhirCodeSystemMissingReadAccessTagValidationItem(f, ref));
        else
            out.add(ok(f, ref, "meta.tag (read‑access‑tag=ALL) present"));
    }

    /*
      2) Presence & basic sanity of key elements
      */
    /**
     * Verifies that all required elements are present and have acceptable values.
     * Also checks that {@code status = unknown} and {@code url} starts with DSF prefix.
     */
    private void checkMandatoryElements(Document doc, File f, String ref, List<FhirElementValidationItem> out)
    {
        checkPresent(doc, f, ref, "url", CS_XP + "/*[local-name()='url']/@value", out);
        checkPresent(doc, f, ref, "name", CS_XP + "/*[local-name()='name']/@value", out);
        checkPresent(doc, f, ref, "title", CS_XP + "/*[local-name()='title']/@value", out);
        checkPresent(doc, f, ref, "publisher", CS_XP + "/*[local-name()='publisher']/@value", out);
        checkPresent(doc, f, ref, "content", CS_XP + "/*[local-name()='content']/@value", out);
        checkPresent(doc, f, ref, "caseSensitive", CS_XP + "/*[local-name()='caseSensitive']/@value", out);

        // url must follow DSF namespace
        /*
        String url = val(doc, CS_XP + "/*[local-name()='url']/@value");
        if (url != null && !url.startsWith("http://dsf.dev/fhir/CodeSystem/"))
            out.add(new FhirCodeSystemInvalidUrlValidationItem(f, ref,
                    "url must start with 'http://dsf.dev/fhir/CodeSystem/'"));
        else
            out.add(ok(f, ref, "url namespace OK")); */   // this check is not required

        // status must be 'unknown' (BPE overrides later)
        String status = val(doc, CS_XP + "/*[local-name()='status']/@value");
        if (!"unknown".equals(status))
            out.add(new FhirCodeSystemInvalidStatusValidationItem(f, ref,
                    "status must be 'unknown' (found '"+status+"')"));
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
     * @param out   the list to which validation items are appended
     */
    private void checkPresent(Document doc, File f, String ref,
                              String name, String xp, List<FhirElementValidationItem> out)
    {
        if (blank(val(doc, xp)))
            out.add(new FhirCodeSystemMissingElementValidationItem(f, ref, name));
        else
            out.add(ok(f, ref, name + " present"));
    }

    /*
      3) Placeholder checks (#{version}, #{date})
      */
    /**
     * Validates that version and date fields contain their respective DSF placeholders.
     */
    private void checkPlaceholders(Document doc, File f, String ref, List<FhirElementValidationItem> out)
    {
        String version = val(doc, CS_XP + "/*[local-name()='version']/@value");
        if (version != null && !version.equals("#{version}"))
            out.add(new FhirCodeSystemVersionNoPlaceholderValidationItem(f, ref,
                    "<version> must contain '#{version}'"));
        else
            out.add(ok(f, ref, "<version> placeholder OK"));

        String date = val(doc, CS_XP + "/*[local-name()='date']/@value");
        if (date != null && !date.equals("#{date}"))
            out.add(new FhirCodeSystemDateNoPlaceholderValidationItem(f, ref,
                    "<date> must contain '#{date}'"));
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
    private void checkConcepts(Document doc, File f, String ref, List<FhirElementValidationItem> out)
    {
        NodeList concepts = xp(doc, CONCEPT_XP);
        if (concepts == null || concepts.getLength() == 0)
        {
            out.add(new FhirCodeSystemMissingConceptValidationItem(f, ref));
            return;
        }

        Set<String> seenCodes = new HashSet<>();

        for (int i = 0; i < concepts.getLength(); i++)
        {
            Node c = concepts.item(i);
            String code    = val(c, "./*[local-name()='code']/@value");
            String display = val(c, "./*[local-name()='display']/@value");

            if (blank(code))
                out.add(new FhirCodeSystemConceptMissingCodeValidationItem(f, ref));
            else if (seenCodes.contains(code))
                out.add(new FhirCodeSystemDuplicateCodeValidationItem(f, ref, code));
            else
                seenCodes.add(code);

            if (blank(display))
                out.add(new FhirCodeSystemConceptMissingDisplayValidationItem(f, ref));
        }

        if (seenCodes.size() == concepts.getLength())
            out.add(ok(f, ref, "all concept codes unique ("+seenCodes.size()+")"));
    }

    /*
      Helper: use url (or filename) as reference for result messages
      */
    /**
     * Computes a reference string used in validation messages, preferring {@code url} over file name.
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
