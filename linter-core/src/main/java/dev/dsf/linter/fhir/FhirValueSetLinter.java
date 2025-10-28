package dev.dsf.linter.fhir;

import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.linting.AbstractFhirInstanceLinter;
import dev.dsf.linter.util.resource.FhirAuthorizationCache;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.*;

/**
 * <h2>DSF ValueSet linter (Profile: dsf-valueset-base 1.0.0)</h2>
 *
 * <p>lints FHIR {@code ValueSet} resources that are part of the Data Sharing Framework (DSF)
 * processes. The linter checks implemented here ensure conformance with DSF-specific requirements
 * and the DSF ValueSet template structure that is processed by the BPE (Business Process Engine) server.</p>
 *
 * <h3>Supported linting Aspects</h3>
 * <ul>
 *   <li><strong>Meta tag linting</strong> – ensures presence of required {@code meta.tag} elements
 *       with system {@code http://dsf.dev/fhir/CodeSystem/read-access-tag} and code {@code ALL} or {@code LOCAL}</li>
 *   <li><strong>Organization role code linting</strong> – lints that parent-organization-role extension
 *       codes conform to the DSF organization role CodeSystem</li>
 *   <li><strong>Core element presence checks</strong> – verifies mandatory elements: {@code url}, {@code name},
 *       {@code title}, {@code publisher}, and {@code description}</li>
 *   <li><strong>Template placeholder enforcement</strong> – ensures proper use of required placeholders:
 *       <ul>
 *         <li>{@code #{version}} in {@code version} and {@code compose.include.version} elements</li>
 *         <li>{@code #{date}} in {@code date} element</li>
 *       </ul>
 *   </li>
 *   <li><strong>Compose/include structure linting</strong> – lints that:
 * <ul>
 *         <li>Each {@code compose/include} element has a {@code system} attribute</li>
 *         <li>Version placeholders are correctly formatted</li>
 *         <li>All {@code concept} elements have non-blank {@code code} values</li>
 *         <li>Referenced codes exist in DSF terminology cache (via {@link FhirAuthorizationCache})</li>
 *       </ul>
 *   </li>
 *   <li><strong>Duplicate concept detection</strong> – identifies and reports duplicate code values
 *       within the same include element</li>
 *   <li><strong>Terminology compliance</strong> – lints code/system combinations against the
 *       DSF authorization cache and provides suggestions for alternative systems when applicable</li>
 * </ul>
 *
 * <h3>Intentional Exclusions</h3>
 * <p>The linter intentionally ignores runtime-managed elements {@code status}, {@code immutable},
 * and {@code experimental} since these are automatically overwritten by the BPE server during
 * ValueSet deployment and activation.</p>
 *
 * <h3>linting Results</h3>
 * <p>Each linter check produces one of the following linting items:</p>
 * <ul>
 *   <li>{@link FhirElementLintItemSuccess} for successful lints</li>
 *   <li>Specific {@link FhirElementLintItem} subclasses for different types of lint errors:
 *       <ul>
 *         <li>{@link FhirValueSetMissingReadAccessTagAllOrLocalLintItem}</li>
 *         <li>{@link FhirValueSetOrganizationRoleMissingValidCodeValueLintItem}</li>
 *         <li>{@link FhirValueSetMissingUrlLintItem}, {@link FhirValueSetMissingNameLintItem}, etc.</li>
 *         <li>{@link FhirValueSetVersionNoPlaceholderLintItem}, {@link FhirValueSetDateNoPlaceholderLintItem}</li>
 *         <li>{@link FhirValueSetMissingComposeIncludeLintItem}</li>
 *         <li>{@link FhirValueSetUnknownCodeLintItem}, {@link FhirValueSetFalseUrlReferencedLintItem}</li>
 *         <li>{@link FhirValueSetDuplicateConceptCodeLintItem}</li>
 * </ul>
 *   </li>
 * </ul>
 *
 * <h3>Dependencies</h3>
 * <p>This linter depends on {@link FhirAuthorizationCache} for terminology linting
 * and code system lookups within the DSF environment.</p>
 *
 * @author Data Sharing Framework Team
 * @version 1.0.0
 * @since 1.0.0
 * @see AbstractFhirInstanceLinter
 * @see FhirAuthorizationCache
 */
public final class FhirValueSetLinter extends AbstractFhirInstanceLinter
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
    public boolean canLint(Document d)
    {
        return "ValueSet".equals(d.getDocumentElement().getLocalName());
    }

    /**
     * lints the provided ValueSet resource according to DSF requirements.
     *
     * <p>This method performs comprehensive linting including:</p>
     * <ul>
     *   <li>Meta tags and core element linting</li>
     *   <li>Placeholder enforcement for version and date fields</li>
     *   <li>Compose/include structure and content linting</li>
     * </ul>
     *
     * @param doc the XML document representing the ValueSet, must not be null
     * @param resFile the file from which the document was loaded (for reference), must not be null
     * @return a list of lint items describing all found issues and successes, never null
     */
    @Override
    public List<FhirElementLintItem> lint(Document doc, File resFile)
    {
        final String ref = computeReference(doc, resFile);
        final List<FhirElementLintItem> issues = new ArrayList<>();

        checkMetaAndBasic(doc, resFile, ref, issues);
        checkPlaceholders(doc, resFile, ref, issues);
        lintComposeIncludes(doc, resFile, ref, issues);

        return issues;
    }

    /*  1) Meta & Basics  */

    /**
     * Checks for required meta tags, organization role codes, and core elements (url, name, title, publisher, description).
     *
     * <p>This method lints:</p>
     * <ul>
     *   <li>Presence of read-access-tag with code 'ALL' or 'LOCAL'</li>
     *   <li>Validity of organization role codes</li>
     *   <li>Presence of required core elements: url, name, title, publisher, description</li>
     * </ul>
     *
     * @param doc the ValueSet XML document to lint, must not be null
     * @param res the file reference for error reporting, must not be null
     * @param ref a human-readable reference for reporting, must not be null
     * @param out the list to which linting results are added, must not be null
     */
    private void checkMetaAndBasic(Document doc,
                                   File res,
                                   String ref,
                                   List<FhirElementLintItem> out)
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
                out.add(new FhirValueSetMissingReadAccessTagAllOrLocalLintItem(res, ref,
                    "meta.tag must contain at least one read-access-tag with code 'ALL' or 'LOCAL'"));
            else
                out.add(ok(res, ref,
                    "meta.tag read-access-tag contains ALL or LOCAL – OK."));
        } catch (Exception e) {
            out.add(new FhirValueSetMissingReadAccessTagAllOrLocalLintItem(res, ref,
                "Failed to evaluate meta.tag read-access linting: " + e.getMessage()));
        }

        // lint organization role codes
        lintOrganizationRoleCodes(doc, res, ref, out);

        // url
        String url = val(doc, VS_XP + "/*[local-name()='url']/@value");
        if (blank(url))
            out.add(new FhirValueSetMissingUrlLintItem(res, ref));
        else
            out.add(ok(res, ref, "url = '" + url + "'"));

        // name
        String name = val(doc, VS_XP + "/*[local-name()='name']/@value");
        if (blank(name))
            out.add(new FhirValueSetMissingNameLintItem(res, ref));
        else
            out.add(ok(res, ref, "name OK"));

        // title
        String title = val(doc, VS_XP + "/*[local-name()='title']/@value");
        if (blank(title))
            out.add(new FhirValueSetMissingTitleLintItem(res, ref));
        else
            out.add(ok(res, ref, "title OK"));

        // publisher
        String publisher = val(doc, VS_XP + "/*[local-name()='publisher']/@value");
        if (blank(publisher))
            out.add(new FhirValueSetMissingPublisherLintItem(res, ref));
        else
            out.add(ok(res, ref, "publisher OK"));

        // description
        String desc = val(doc, VS_XP + "/*[local-name()='description']/@value");
        if (blank(desc))
            out.add(new FhirValueSetMissingDescriptionLintItem(res, ref));
        else
            out.add(ok(res, ref, "description OK"));
    }

    /**
     * lints any parent-organization-role codes against FhirAuthorizationCache.
     * Checks that organization-role extension codes are valid according to the CS_ORG_ROLE CodeSystem.
     *
     * <p>This method examines all parent-organization-role extensions in meta tags and
     * lints that the organization-role codes are known to the DSF authorization system.</p>
     *
     * @param doc the ValueSet XML document to lint, must not be null
     * @param res the file reference for error reporting, must not be null
     * @param ref a human-readable reference for reporting, must not be null
     * @param out the list to which linting results are added, must not be null
     */
    private void lintOrganizationRoleCodes(Document doc,
                                           File res,
                                           String ref,
                                           List<FhirElementLintItem> out)
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
                    out.add(new FhirValueSetOrganizationRoleMissingValidCodeValueLintItem(
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
            out.add(new FhirValueSetOrganizationRoleMissingValidCodeValueLintItem(res, ref,
                "Failed to evaluate parent-organization-role linting: " + e.getMessage()));
        }
    }

    /*  2) Placeholder checks  */

    /**
     * Checks for required placeholders in version and date fields.
     *
     * <p>lints that:</p>
     * <ul>
     *   <li>The version element contains exactly '#{version}'</li>
     *   <li>The date element contains exactly '#{date}'</li>
     * </ul>
     *
     * <p>These placeholders are required by the DSF template system and will be
     * replaced with actual values during deployment.</p>
     *
     * @param doc the ValueSet XML document to lint, must not be null
     * @param res the file reference for error reporting, must not be null
     * @param ref a human-readable reference for reporting, must not be null
     * @param out the list to which linting results are added, must not be null
     */
    private void checkPlaceholders(Document doc,
                                   File res,
                                   String ref,
                                   List<FhirElementLintItem> out)
    {
        // version → #{version}
        String version = val(doc, VS_XP + "/*[local-name()='version']/@value");
        if (version == null || !version.equals("#{version}"))
            out.add(new FhirValueSetVersionNoPlaceholderLintItem(res, ref,
                    "<version> must contain '#{version}'."));
        else
            out.add(ok(res, ref, "version placeholder OK." ));

        // date → #{date}
        String date = val(doc, VS_XP + "/*[local-name()='date']/@value");
        if (date == null || !date.equals("#{date}"))
            out.add(new FhirValueSetDateNoPlaceholderLintItem(res, ref,
                    "<date> must contain '#{date}'."));
        else
            out.add(ok(res, ref, "date placeholder OK."));
    }

    /*  3) Compose/include  */

    /**
     * lints all compose/include elements within the ValueSet for structural integrity and terminology compliance.
     *
     * <p>This method performs comprehensive linting of the ValueSet's compose/include structure,
     * ensuring compliance with DSF requirements and FHIR terminology standards. It lints both
     * the structural elements and the semantic correctness of referenced codes.</p>
     *
     * <p><b>Linter checks performed:</b></p>
     * <ul>
     *   <li><strong>Structure linting:</strong>
     *       <ul>
     *         <li>Verifies at least one compose/include element exists</li>
     *         <li>Ensures each include has a required system attribute</li>
     *         <li>lints version placeholder contains exactly '#{version}'</li>
     *       </ul>
     *   </li>
     *   <li><strong>Concept linting:</strong>
     *       <ul>
     *         <li>Checks all concept elements have non-blank code attributes</li>
     *         <li>Detects and reports duplicate concept codes within the same include</li>
     *         <li>lints code existence against DSF terminology cache</li>
     *       </ul>
     *   </li>
     *   <li><strong>Terminology compliance:</strong>
     * <ul>
     *         <li>Enforces canonical CodeSystem URL matching</li>
     *         <li>Provides diagnostic feedback for incorrect system references</li>
     *         <li>Reports unknown codes with alternative system suggestions when available</li>
     * </ul>
     *   </li>
     * </ul>
     *
     * <p><b>linting logic for code/system combinations:</b></p>
     * <ol>
     *   <li>If system URL is not loaded in DSF cache → search code across all known systems</li>
     *   <li>If system exists but doesn't contain the code → search code in alternative systems</li>
     *   <li>If system exists and contains the code → linting passes</li>
     *   <li>For cases 1 & 2: Report specific lint items based on search results</li>
     * </ol>
     *
     * <p><b>Note:</b> Include elements without concept children are valid and indicate
     * inclusion of all codes from the referenced CodeSystem.</p>
     *
     * @param doc the ValueSet XML document to lint, must not be null
     * @param res the source file reference for error reporting and traceability, must not be null
     * @param ref a human-readable identifier (preferably ValueSet URL) for linter messages, must not be null
     * @param out the mutable list to which all linting results are appended, must not be null
     * @see FhirAuthorizationCache#containsSystem(String)
     * @see FhirAuthorizationCache#isKnown(String, String)
     * @see FhirAuthorizationCache#findSystemsContainingCode(String)
     */
    private void lintComposeIncludes(Document doc,
                                     File res,
                                     String ref,
                                     List<FhirElementLintItem> out)
    {
        NodeList includes = xp(doc, COMPOSE_INCLUDE_XP);
        if (includes == null || includes.getLength() == 0)
        {
            out.add(new FhirValueSetMissingComposeIncludeLintItem(res, ref));
            return;
        }

        for (int i = 0; i < includes.getLength(); i++)
        {
            Node inc = includes.item(i);
            String system = val(inc, "./*[local-name()='system']/@value");
            if (blank(system))
            {
                out.add(new FhirValueSetIncludeMissingSystemLintItem(res, ref,
                        "compose.include without system attribute"));
                continue;
            }
            else
                out.add(ok(res, ref, "include.system = '" + system + "'" ));

            // version placeholder
            String incVersion = val(inc, INCLUDE_VERSION_XP);
            if (incVersion == null || !incVersion.equals("#{version}"))
                out.add(new FhirValueSetIncludeVersionPlaceholderLintItem(res, ref,
                        "include(version) should contain '#{version}'"));
            else
                out.add(ok(res, ref, "include.version placeholder OK" ));

            /*  concept linting  */
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
                    out.add(new FhirValueSetConceptMissingCodeLintItem(res, ref,
                            "include.concept without code"));
                    continue;
                }

                // duplicates
                if (!duplicateGuard.add(code))
                {
                    out.add(new FhirValueSetDuplicateConceptCodeLintItem(res, ref,
                            "duplicate code '" + code + "' in the same include"));
                }

                // DSF‑Terminology‑Check
                if (system.isBlank() || code.isBlank()) {
                    out.add(new FhirValueSetUnknownCodeLintItem(
                            res, ref,
                            "missing system/code for ValueSet include; cannot lint '" + code + "' in system '" + system + "'"));
                }
                else if (!FhirAuthorizationCache.containsSystem(system)
                        || !FhirAuthorizationCache.isKnown(system, code)) {
                    lintCodeInAlternateSystems(res, ref, out, system, code);
                } else {
                    out.add(ok(res, ref, "concept.code '" + code + "' is known in '" + system + "'."));
                }
            }
        }
    }

    private void lintCodeInAlternateSystems(File res, String ref, List<FhirElementLintItem> out, String system, String code) {
        Set<String> hits = FhirAuthorizationCache.findSystemsContainingCode(code);
        if (!hits.isEmpty()) {
            out.add(new FhirValueSetFalseUrlReferencedLintItem(
                    res, ref,
                    "code '" + code + "' exists in system(s) " + hits + " but ValueSet references '" + system + "'."));
        } else {
            out.add(new FhirValueSetUnknownCodeLintItem(
                    res, ref,
                    "unknown code '" + code + "' in system '" + system + "'"));
        }
    }

    /*  helpers  */

    /**
     * Determines a human‑readable reference for logging/issue reporting. Prefers the
     * <code>url</code> element of the ValueSet; falls back to the file name.
     *
     * <p>This method extracts the ValueSet's canonical URL for use in linter
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
