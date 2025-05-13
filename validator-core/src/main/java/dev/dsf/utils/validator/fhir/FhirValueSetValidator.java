
package dev.dsf.utils.validator.fhir;

import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.AbstractFhirInstanceValidator;
import dev.dsf.utils.validator.util.FhirAuthorizationCache;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

/**
 * <h2>DSF ValueSet Validator (Profile: dsf-valueset-base 1.0.0)</h2>
 *
 * <p>Validates <code>ValueSet</code> resources that are part of Digital Sample Framework (DSF)
 * processes. The checks implemented here are aligned with the DSF template for ValueSets
 * that are loaded by the BPE server.</p>
 *
 * <p><b>Supported validation aspects</b></p>
 * <ul>
 *   <li><strong>Meta tag check</strong> – ensures <code>meta.tag</code> with system
 *       <code>http://dsf.dev/fhir/CodeSystem/read-access-tag</code> and code <code>ALL</code> is present.</li>
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
 * <p>All problems are reported as subclasses of {@link FhirElementValidationItem}.
 *
 *
 */
public final class FhirValueSetValidator extends AbstractFhirInstanceValidator
{
    /*  XPath shortcuts  */

    private static final String VS_XP             = "/*[local-name()='ValueSet']";
    private static final String META_TAG_SYS_XP   = VS_XP + "/*[local-name()='meta']/*[local-name()='tag']" +
            "/*[local-name()='system']/@value";
    private static final String META_TAG_CODE_XP  = VS_XP + "/*[local-name()='meta']/*[local-name()='tag']" +
            "/*[local-name()='code']/@value";

    private static final String COMPOSE_INCLUDE_XP           = VS_XP + "/*[local-name()='compose']/*[local-name()='include']";
    private static final String INCLUDE_SYS_XP               = "./@value | ./@system"; // compatibility
    private static final String INCLUDE_VERSION_XP           = "./*[local-name()='version']/@value";
    private static final String INCLUDE_CONCEPT_XP           = "./*[local-name()='concept']";
    private static final String CONCEPT_CODE_XP              = "./*[local-name()='code']/@value";

    private static final String TAG_SYSTEM_READ_ACCESS       = "http://dsf.dev/fhir/CodeSystem/read-access-tag";

    /* --- API  */

    @Override
    public boolean canValidate(Document d)
    {
        return "ValueSet".equals(d.getDocumentElement().getLocalName());
    }

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

    private void checkMetaAndBasic(Document doc,
                                   File res,
                                   String ref,
                                   List<FhirElementValidationItem> out)
    {
        // meta.tag
        String tagSystem = val(doc, META_TAG_SYS_XP);
        String tagCode   = val(doc, META_TAG_CODE_XP);
        if (!TAG_SYSTEM_READ_ACCESS.equals(tagSystem) || !"ALL".equals(tagCode))
            out.add(new FhirValueSetMissingReadAccessTagValidationItem(res, ref,
                    "meta.tag must contain system='" + TAG_SYSTEM_READ_ACCESS + "', code='ALL'"));
        else
            out.add(ok(res, ref, "meta.tag read‑access‑tag OK."));

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

    /*  2) Placeholder checks  */

    private void checkPlaceholders(Document doc,
                                   File res,
                                   String ref,
                                   List<FhirElementValidationItem> out)
    {
        // version → #{version}
        String version = val(doc, VS_XP + "/*[local-name()='version']/@value");
        if (version == null || !version.contains("#{version}"))
            out.add(new FhirValueSetVersionNoPlaceholderValidationItem(res, ref,
                    "<version> must contain '#{version}'."));
        else
            out.add(ok(res, ref, "version placeholder OK." ));

        // date → #{date}
        String date = val(doc, VS_XP + "/*[local-name()='date']/@value");
        if (date == null || !date.contains("#{date}"))
            out.add(new FhirValueSetDateNoPlaceholderValidationItem(res, ref,
                    "<date> must contain '#{date}'."));
        else
            out.add(ok(res, ref, "date placeholder OK."));
    }

    /*  3) Compose/include  */

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
            if (incVersion == null || !incVersion.contains("#{version}"))
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
     */
    private String computeReference(Document doc, File file)
    {
        String url = val(doc, VS_XP + "/*[local-name()='url']/@value");
        return !blank(url) ? url : file.getName();
    }
}
