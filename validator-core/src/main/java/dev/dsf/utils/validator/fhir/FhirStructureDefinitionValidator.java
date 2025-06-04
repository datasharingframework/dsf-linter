package dev.dsf.utils.validator.fhir;

import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.AbstractFhirInstanceValidator;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

/**
 * DSF StructureDefinition validator – validates FHIR StructureDefinition resources
 * according to DSF-specific authoring conventions. Each validation check produces
 * a dedicated {@link dev.dsf.utils.validator.item.FhirElementValidationItem} subclass
 * for fine-grained issue reporting.
 *
 * <p>Checks include:
 * <ul>
 *   <li>Presence and format of meta.profile and read-access tag</li>
 *   <li>Correct usage of placeholders in version and date fields</li>
 *   <li>Existence of a differential section and absence of a snapshot section</li>
 *   <li>Uniqueness of element IDs in the differential</li>
 * </ul>
 * </p>
 */
public final class FhirStructureDefinitionValidator extends AbstractFhirInstanceValidator
{
    /*  XPATH SHORTCUTS  */
    private static final String SD_XP           = "/*[local-name()='StructureDefinition']";
    private static final String DIFFERENTIAL_XP = SD_XP + "/*[local-name()='differential']";
    private static final String SNAPSHOT_XP     = SD_XP + "/*[local-name()='snapshot']";
    private static final String ELEMENTS_XP     = DIFFERENTIAL_XP + "/*[local-name()='element']";
    private static final String META_PROFILE_XP = SD_XP + "/*[local-name()='meta']/*[local-name()='profile']/@value";
    private static final String META_TAG_XP     = SD_XP + "/*[local-name()='meta']/*[local-name()='tag']";

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

        return issues;
    }

    /* CHECK 1: META & BASICS  */
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
        /* meta.profile */
        String profile = val(doc, META_PROFILE_XP);
        if (blank(profile))
            out.add(new FhirStructureDefinitionMissingMetaProfileItem(file, ref));
        else if (!profile.startsWith(URL_PREFIX))
            out.add(new FhirStructureDefinitionInvalidMetaProfileItem(file, ref, profile));
        else
            out.add(ok(file, ref, "meta.profile present and DSF-conformant"));

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
        if (version == null || !version.contains("#{version}"))
            out.add(new FhirStructureDefinitionVersionNoPlaceholderItem(file, ref, version));
        else
            out.add(ok(file, ref, "version placeholder present"));

        /* date */
        String date = val(doc, SD_XP + "/*[local-name()='date']/@value");
        if (date == null || !date.contains("#{date}"))
            out.add(new FhirStructureDefinitionDateNoPlaceholderItem(file, ref, date));
        else
            out.add(ok(file, ref, "date placeholder present"));
    }

    /* ===================== CHECK 3: DIFF / SNAPSHOT / IDs =================== */
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
}