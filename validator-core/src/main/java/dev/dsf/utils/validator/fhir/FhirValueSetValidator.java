package dev.dsf.utils.validator.fhir;

import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.AbstractFhirInstanceValidator;
import dev.dsf.utils.validator.util.FhirAuthorizationCache;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.*;

/**
 * <h2>DSF ValueSet Validator (Profile: dsf-valueset-base 1.0.0)</h2>
 *
 * <p>Validates <code>ValueSet</code> resources that are part of Data Sharing Framework (DSF)
 * processes. The checks implemented here are aligned with the DSF template for ValueSets
 * that are loaded by the BPE server.</p>
 *
 * <p><b>Supported validation aspects</b></p>
 * <ul>
 *   <li><strong>Meta tag check</strong> – ensures <code>meta.tag</code> with system
 *       <code>http://dsf.dev/fhir/CodeSystem/read-access-tag</code> and code <code>ALL</code> or <code>LOCAL</code> is present.</li>
 *   <li><strong>Organization role code validation</strong> – checks that any parent-organization-role extension codes are valid according to the DSF CodeSystem.</li>
 *   <li><strong>Core elements</strong> – verifies presence of <code>url</code>, <code>name</code>,
 *       <code>title</code>, <code>publisher</code> and <code>description</code>.</li>
 *   <li><strong>Placeholder enforcement</strong> – enforces required template placeholders
 *       <code>#{version}</code> (in <code>version</code> and <code>compose.include.version</code>)
 *       and <code>#{date}</code> (in <code>date</code>).</li>
 *   <li><strong>Compose/include validation</strong> – checks that every
 *       <code>compose/include</code> element has a <code>system</code> attribute, an optional
 *       <code>version</code> placeholder, and – if <code>concept</code> children exist – that
 *       every <code>code</code> is non‑blank and known to DSF (using
 *       {@link FhirAuthorizationCache#isUnknown(String, String)}).</li>
 *   <li><strong>Duplicate concept detection</strong> – flags identical code occurrences
 *       within the same include.</li>
 * </ul>
 *
 * <p>The validator intentionally ignores the run‑time managed elements <code>status</code>,
 * <code>immutable</code> and <code>experimental</code>, since these are overwritten by the
 * BPE server during deployment.</p>
 *
 * <p>Each check results in one of the following validation items:
 * <ul>
 *   <li>{@link FhirElementValidationItemSuccess} for successful validations</li>
 *   <li>Various {@link FhirElementValidationItem} subclasses for validation errors</li>
 * </ul>
 * </p>
 *
 * @author Data Sharing Framework Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class FhirValueSetValidator extends AbstractFhirInstanceValidator
{
    /*  XPath shortcuts  */

    /** XPath expression to select the ValueSet root element. */
    private static final String VS_XP             = "/*[local-name()='ValueSet']";

    /** XPath expression to select all compose/include elements within a ValueSet. */
    private static final String COMPOSE_INCLUDE_XP           = VS_XP + "/*[local-name()='compose']/*[local-name()='include']";

    /** XPath expression to select the version attribute of an include element (relative to include context). */
    private static final String INCLUDE_VERSION_XP           = "./*[local-name()='version']/@value";

    /** XPath expression to select all concept elements within an include (relative to include context). */
    private static final String INCLUDE_CONCEPT_XP           = "./*[local-name()='concept']";

    /** XPath expression to select the code attribute of a concept element (relative to concept context). */
    private static final String CONCEPT_CODE_XP              = "./*[local-name()='code']/@value";

    /** The system URI for DSF read-access-tag CodeSystem. */
    private static final String TAG_SYSTEM_READ_ACCESS       = "http://dsf.dev/fhir/CodeSystem/read-access-tag";

    /** The URL for the DSF parent-organization-role extension. */
    private static final String EXT_PARENT_ORG_ROLE_URL      = "http://dsf.dev/fhir/StructureDefinition/extension-read-access-parent-organization-role";

    /** Shared XPath factory instance for creating XPath expressions. */
    private static final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();

    /* --- API  */

    /**
     * Checks if the given XML document represents a FHIR ValueSet resource.
     *
     * @param d the XML document to check, must not be null
     * @return true if the document's root element is "ValueSet", false otherwise
     */
    @Override
    public boolean canValidate(Document d)
    {
        return "ValueSet".equals(d.getDocumentElement().getLocalName());
    }

    /**
     * Validates the provided ValueSet resource according to DSF requirements.
     *
     * <p>This method performs comprehensive validation including:</p>
     * <ul>
     *   <li>Meta tags and core element validation</li>
     *   <li>Placeholder enforcement for version and date fields</li>
     *   <li>Compose/include structure and content validation</li>
     * </ul>
     *
     * @param doc the XML document representing the ValueSet, must not be null
     * @param resFile the file from which the document was loaded (for reference), must not be null
     * @return a list of validation items describing all found issues and successes, never null
     */
    @Override
    public List<FhirElementValidationItem> validate(Document doc, File resFile)
    {
        final String ref = computeReference(doc, resFile);
        final List<FhirElementValidationItem> issues = new ArrayList<>();

        checkMetaAndBasic(doc, resFile, ref, issues);
        checkPlaceholders(doc, resFile, ref, issues);
        validateComposeIncludes(doc, resFile, ref, issues);

        return issues;
    }

    /*  1) Meta & Basics  */

    /**
     * Checks for required meta tags, organization role codes, and core elements (url, name, title, publisher, description).
     *
     * <p>This method validates:</p>
     * <ul>
     *   <li>Presence of read-access-tag with code 'ALL' or 'LOCAL'</li>
     *   <li>Validity of organization role codes</li>
     *   <li>Presence of required core elements: url, name, title, publisher, description</li>
     * </ul>
     *
     * @param doc the ValueSet XML document to validate, must not be null
     * @param res the file reference for error reporting, must not be null
     * @param ref a human-readable reference for reporting, must not be null
     * @param out the list to which validation results are added, must not be null
     */
    private void checkMetaAndBasic(Document doc,
                                   File res,
                                   String ref,
                                   List<FhirElementValidationItem> out)
    {
        //  new: must have at least one read-access-tag with code ALL or LOCAL
        final String META_TAGS_XP = VS_XP
            + "/*[local-name()='meta']/*[local-name()='tag']";
        try {
            NodeList tagElements = (NodeList) XPATH_FACTORY.newXPath()
                .evaluate(META_TAGS_XP, doc, XPathConstants.NODESET);
            boolean hasAllOrLocal = false;
            for (int i = 0; i < tagElements.getLength(); i++)
            {
                Node tag = tagElements.item(i);
                String sys  = val(tag, "./*[local-name()='system']/@value");
                String code = val(tag, "./*[local-name()='code']/@value");
                if (TAG_SYSTEM_READ_ACCESS.equals(sys)
                    && ("ALL".equals(code) || "LOCAL".equals(code)))
                {
                    hasAllOrLocal = true;
                    break;
                }
            }
            if (!hasAllOrLocal)
                out.add(new FhirValueSetMissingReadAccessTagAllOrLocalValidationItem(res, ref,
                    "meta.tag must contain at least one read-access-tag with code 'ALL' or 'LOCAL'"));
            else
                out.add(ok(res, ref,
                    "meta.tag read-access-tag contains ALL or LOCAL – OK."));
        } catch (Exception e) {
            out.add(new FhirValueSetMissingReadAccessTagAllOrLocalValidationItem(res, ref,
                "Failed to evaluate meta.tag read-access validation: " + e.getMessage()));
        }

        // Validate organization role codes
        validateOrganizationRoleCodes(doc, res, ref, out);

        // url
        String url = val(doc, VS_XP + "/*[local-name()='url']/@value");
        if (blank(url))
            out.add(new FhirValueSetMissingUrlValidationItem(res, ref));
        else
            out.add(ok(res, ref, "url = '" + url + "'"));

        // name
        String name = val(doc, VS_XP + "/*[local-name()='name']/@value");
        if (blank(name))
            out.add(new FhirValueSetMissingNameValidationItem(res, ref));
        else
            out.add(ok(res, ref, "name OK"));

        // title
        String title = val(doc, VS_XP + "/*[local-name()='title']/@value");
        if (blank(title))
            out.add(new FhirValueSetMissingTitleValidationItem(res, ref));
        else
            out.add(ok(res, ref, "title OK"));

        // publisher
        String publisher = val(doc, VS_XP + "/*[local-name()='publisher']/@value");
        if (blank(publisher))
            out.add(new FhirValueSetMissingPublisherValidationItem(res, ref));
        else
            out.add(ok(res, ref, "publisher OK"));

        // description
        String desc = val(doc, VS_XP + "/*[local-name()='description']/@value");
        if (blank(desc))
            out.add(new FhirValueSetMissingDescriptionValidationItem(res, ref));
        else
            out.add(ok(res, ref, "description OK"));
    }

    /**
     * Validates any parent-organization-role codes against FhirAuthorizationCache.
     * Checks that organization-role extension codes are valid according to the CS_ORG_ROLE CodeSystem.
     *
     * <p>This method examines all parent-organization-role extensions in meta tags and
     * validates that the organization-role codes are known to the DSF authorization system.</p>
     *
     * @param doc the ValueSet XML document to validate, must not be null
     * @param res the file reference for error reporting, must not be null
     * @param ref a human-readable reference for reporting, must not be null
     * @param out the list to which validation results are added, must not be null
     */
    private void validateOrganizationRoleCodes(Document doc,
                                               File res,
                                               String ref,
                                               List<FhirElementValidationItem> out)
    {
        final String META_PARENT_ORG_ROLE_CODE_XP = VS_XP
            + "/*[local-name()='meta']/*[local-name()='tag']"
            + "/*[local-name()='extension' and @url='" + EXT_PARENT_ORG_ROLE_URL + "']"
            + "/*[local-name()='extension' and @url='organization-role']"
            + "/*[local-name()='valueCoding']/*[local-name()='code']/@value";

        try {
            NodeList orgRoleCodes = (NodeList) XPATH_FACTORY.newXPath()
                .evaluate(META_PARENT_ORG_ROLE_CODE_XP, doc, XPathConstants.NODESET);
            for (int i = 0; i < orgRoleCodes.getLength(); i++)
            {
                String roleCode = orgRoleCodes.item(i).getNodeValue();
                if (FhirAuthorizationCache.isUnknown(
                        FhirAuthorizationCache.CS_ORG_ROLE, roleCode))
                {
                    out.add(new FhirValueSetOrganizationRoleMissingValidCodeValueValidationItem(
                            res, ref,
                            "Invalid organization-role code '" + roleCode + "'"));
                }
                else
                {
                    out.add(ok(res, ref,
                            "meta.tag parent-organization-role code '" + roleCode + "' OK."));
                }
            }
        } catch (Exception e) {
            out.add(new FhirValueSetOrganizationRoleMissingValidCodeValueValidationItem(res, ref,
                "Failed to evaluate parent-organization-role validation: " + e.getMessage()));
        }
    }

    /*  2) Placeholder checks  */

    /**
     * Checks for required placeholders in version and date fields.
     *
     * <p>Validates that:</p>
     * <ul>
     *   <li>The version element contains exactly '#{version}'</li>
     *   <li>The date element contains exactly '#{date}'</li>
     * </ul>
     *
     * <p>These placeholders are required by the DSF template system and will be
     * replaced with actual values during deployment.</p>
     *
     * @param doc the ValueSet XML document to validate, must not be null
     * @param res the file reference for error reporting, must not be null
     * @param ref a human-readable reference for reporting, must not be null
     * @param out the list to which validation results are added, must not be null
     */
    private void checkPlaceholders(Document doc,
                                   File res,
                                   String ref,
                                   List<FhirElementValidationItem> out)
    {
        // version → #{version}
        String version = val(doc, VS_XP + "/*[local-name()='version']/@value");
        if (version == null || !version.equals("#{version}"))
            out.add(new FhirValueSetVersionNoPlaceholderValidationItem(res, ref,
                    "<version> must contain '#{version}'."));
        else
            out.add(ok(res, ref, "version placeholder OK." ));

        // date → #{date}
        String date = val(doc, VS_XP + "/*[local-name()='date']/@value");
        if (date == null || !date.equals("#{date}"))
            out.add(new FhirValueSetDateNoPlaceholderValidationItem(res, ref,
                    "<date> must contain '#{date}'."));
        else
            out.add(ok(res, ref, "date placeholder OK."));
    }

    /*  3) Compose/include  */

    /**
     * Validates all compose/include elements for required system, version placeholder, and concept codes.
     * Also checks for duplicate codes and unknown codes in the DSF terminology.
     *
     * <p>This method performs the following validations:</p>
     * <ul>
     *   <li>Ensures at least one compose/include element exists</li>
     *   <li>Validates that each include has a system attribute</li>
     *   <li>Checks that include version contains the '#{version}' placeholder</li>
     *   <li>For includes with concepts: validates code presence, uniqueness, and DSF knowledge</li>
     *   <li>Detects duplicate concept codes within the same include</li>
     * </ul>
     *
     * @param doc the ValueSet XML document to validate, must not be null
     * @param res the file reference for error reporting, must not be null
     * @param ref a human-readable reference for reporting, must not be null
     * @param out the list to which validation results are added, must not be null
     */
    private void validateComposeIncludes(Document doc,
                                         File res,
                                         String ref,
                                         List<FhirElementValidationItem> out)
    {
        NodeList includes = xp(doc, COMPOSE_INCLUDE_XP);
        if (includes == null || includes.getLength() == 0)
        {
            out.add(new FhirValueSetMissingComposeIncludeValidationItem(res, ref));
            return;
        }

        for (int i = 0; i < includes.getLength(); i++)
        {
            Node inc = includes.item(i);
            String system = val(inc, "./*[local-name()='system']/@value");
            if (blank(system))
            {
                out.add(new FhirValueSetIncludeMissingSystemValidationItem(res, ref,
                        "compose.include without system attribute"));
                continue;
            }
            else
                out.add(ok(res, ref, "include.system = '" + system + "'" ));

            // version placeholder
            String incVersion = val(inc, INCLUDE_VERSION_XP);
            if (incVersion == null || !incVersion.equals("#{version}"))
                out.add(new FhirValueSetIncludeVersionPlaceholderValidationItem(res, ref,
                        "include(version) should contain '#{version}'"));
            else
                out.add(ok(res, ref, "include.version placeholder OK" ));

            /*  concept validation  */
            NodeList concepts = xp(inc, INCLUDE_CONCEPT_XP);
            if (concepts == null || concepts.getLength() == 0)
            {
                // Include without concepts is allowed (means include all codes)
                continue;
            }

            Set<String> duplicateGuard = new HashSet<>();
            for (int c = 0; c < concepts.getLength(); c++)
            {
                Node concept = concepts.item(c);
                String code = val(concept, CONCEPT_CODE_XP);

                if (blank(code))
                {
                    out.add(new FhirValueSetConceptMissingCodeValidationItem(res, ref,
                            "include.concept without code"));
                    continue;
                }

                // duplicates
                if (!duplicateGuard.add(code))
                {
                    out.add(new FhirValueSetDuplicateConceptCodeValidationItem(res, ref,
                            "duplicate code '" + code + "' in the same include"));
                }

                // DSF‑Terminology‑Check
                if (FhirAuthorizationCache.isUnknown(system, code))
                {
                    out.add(new FhirValueSetUnknownCodeValidationItem(
                            res,
                            ref,
                            "unknown code '" + code + "' in system '" + system + "'"));
                }
                else
                {
                    out.add(ok(res, ref,
                            "concept.code '" + code + "' is known in '" + system + "'."));
                }

            }
        }
    }

    /*  helpers  */

    /**
     * Determines a human‑readable reference for logging/issue reporting. Prefers the
     * <code>url</code> element of the ValueSet; falls back to the file name.
     *
     * <p>This method extracts the ValueSet's canonical URL for use in validation
     * messages. If no URL is present, it uses the filename as a fallback identifier.</p>
     *
     * @param doc the ValueSet XML document, must not be null
     * @param file the file reference, must not be null
     * @return the ValueSet url or the file name if url is not present, never null
     */
    private String computeReference(Document doc, File file)
    {
        String url = val(doc, VS_XP + "/*[local-name()='url']/@value");
        return !blank(url) ? url : file.getName();
    }
}
