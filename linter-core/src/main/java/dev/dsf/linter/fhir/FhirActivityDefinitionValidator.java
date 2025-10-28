package dev.dsf.linter.fhir;

import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.validation.AbstractFhirInstanceValidator;
import dev.dsf.linter.util.resource.FhirAuthorizationCache;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validator for FHIR {@code ActivityDefinition} resources.
 *
 * <p>The validator performs the following checks:</p>
 * <ul>
 *     <li><strong>Mandatory elements</strong><br/>
 *         <ul>
 *             <li>{@code <url>} must be present and non-blank.</li>
 *             <li>{@code <status>} must be present and set to {@code "unknown"} (required by DSF).</li>
 *             <li>{@code <kind>} must be present and set to {@code "Task"}.</li>
 *         </ul>
 *     </li>
 *     <li><strong>Read‑Access Tag</strong><br/>
 *         The first {@code <meta><tag>} must use the CodeSystem
 *         {@value #READ_ACCESS_TAG_SYSTEM} and the code {@value #READ_ACCESS_TAG_CODE_ALL} (allowing global read access).</li>
 *     <li><strong>Process‑Authorization Extension</strong><br/>
 *         <ul>
 *             <li>{@code extension-process-authorization} must exist.</li>
 *             <li>Each {@code requester} and {@code recipient} sub-extension must contain a {@code valueCoding}
 *                 with {@code system} equal to {@value #PROCESS_AUTHORIZATION_SYSTEM} and a {@code code} that is
 *                 known to {@link FhirAuthorizationCache#isKnownAuthorizationCode(String)}.</li>
 *         </ul>
 *     </li>
 *     <li>For every step the validator records <em>success</em>, <em>warning</em> or <em>error</em> items.</li>
 * </ul>
 *
 * <p><strong>Checks intentionally delegated to other validators:</strong></p>
 * <ul>
 *     <li>{@code <version>} placeholder (value {@code "#{version}"}).</li>
 *     <li>{@code <date>} placeholder (value {@code "#{date}"}).</li>
 *     <li>{@code extension url="task-profile"}.</li>
 *     <li>{@code extension url="message-name"}.</li>
 * </ul>
 *
 * <p>
 * References:
 * <ul>
 *   <li><a href="http://hl7.org/fhir/R4/activitydefinition.html">HL7 FHIR R4 – ActivityDefinition</a></li>
 *   <li><a href="https://dsf.dev/">DSF Developer Documentation</a></li>
 * </ul>
 * </p>
 */
public final class FhirActivityDefinitionValidator extends AbstractFhirInstanceValidator
{
    /*
      XPath constants
      */
    private static final String ACTIVITY_DEFINITION_XP = "/*[local-name()='ActivityDefinition']";
    private static final String URL_XP     = ACTIVITY_DEFINITION_XP + "/*[local-name()='url']/@value";
    private static final String STATUS_XP  = ACTIVITY_DEFINITION_XP + "/*[local-name()='status']/@value";
    private static final String KIND_XP    = ACTIVITY_DEFINITION_XP + "/*[local-name()='kind']/@value";

    private static final String TAG_SYS_XP  = ACTIVITY_DEFINITION_XP +
            "/*[local-name()='meta']/*[local-name()='tag'][1]/*[local-name()='system'][1]/@value";
    private static final String TAG_CODE_XP = ACTIVITY_DEFINITION_XP +
            "/*[local-name()='meta']/*[local-name()='tag'][1]/*[local-name()='code'][1]/@value";

    private static final String AUTH_EXT_BASE_XP = ACTIVITY_DEFINITION_XP +
            "/*[local-name()='extension' and @url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization']";
    private static final String AUTH_EXT_REQUESTER_XP  = "./*[local-name()='extension' and @url='requester']";
    private static final String AUTH_EXT_RECIPIENT_XP  = "./*[local-name()='extension' and @url='recipient']";
    private static final String AUTH_CODING_SYSTEM_XP  = "./*[local-name()='valueCoding'][1]/*[local-name()='system'][1]/@value";
    private static final String AUTH_CODING_CODE_XP    = "./*[local-name()='valueCoding'][1]/*[local-name()='code'][1]/@value";

    /*
      DSF‑specific CodeSystems / codes
      */
    private static final String READ_ACCESS_TAG_SYSTEM = "http://dsf.dev/fhir/CodeSystem/read-access-tag";
    private static final String READ_ACCESS_TAG_CODE_ALL = "ALL";

    private static final String PROCESS_AUTHORIZATION_SYSTEM = "http://dsf.dev/fhir/CodeSystem/process-authorization";


    @Override
    public boolean canValidate(Document document)
    {
        return "ActivityDefinition".equals(document.getDocumentElement().getLocalName());
    }

    @Override
    public List<FhirElementValidationItem> validate(Document doc, File resourceFile)
    {
        final List<FhirElementValidationItem> issues = new ArrayList<>();

        /* (1) <url>  */
        final String resourceUrl = val(doc, URL_XP);
        if (blank(resourceUrl))
            issues.add(new FhirActivityDefinitionInvalidFhirUrlValidationItem(resourceFile, null,
                    "ActivityDefinition is missing <url> or it is empty."));
        else
            issues.add(ok(resourceFile, resourceUrl, "Found <url>: '" + resourceUrl + "'."));

        /* (2) <status> must be "unknown"  */
        final String statusVal = val(doc, STATUS_XP);
        if (blank(statusVal))
            issues.add(new FhirActivityDefinitionInvalidFhirStatusValidationItem(resourceFile, resourceUrl,
                    "ActivityDefinition is missing <status> or it is empty."));
        else if (!"unknown".equals(statusVal))
            issues.add(new FhirStatusIsNotSetAsUnknown(resourceFile, resourceUrl,
                    "<status> must be 'unknown' (found '" + statusVal + "')."));
        else
            issues.add(ok(resourceFile, resourceUrl, "<status> is 'unknown'."));

        /* (3) <kind> must be "Task"  */
        final String kindVal = val(doc, KIND_XP);
        if (blank(kindVal))
            issues.add(new FhirKindIsMissingOrEmptyValidationItem(resourceFile, resourceUrl));
        else if (!"Task".equals(kindVal))
            issues.add(new FhirKindNotSetAsTaskValidationItem(resourceFile, resourceUrl,
                    "<kind> must be 'Task' (found '" + kindVal + "')."));
        else
            issues.add(ok(resourceFile, resourceUrl, "<kind> is 'Task'."));

        /* (4) Read‑Access‑Tag  */
        final String tagSystem = val(doc, TAG_SYS_XP);
        final String tagCode   = val(doc, TAG_CODE_XP);

        if (blank(tagSystem) || blank(tagCode))
            issues.add(new FhirMissingFhirAccessTagValidationItem(resourceFile, resourceUrl,
                    "Missing read‑access tag (system + code)."));
        else if (!Objects.equals(READ_ACCESS_TAG_SYSTEM, tagSystem) ||
                !Objects.equals(READ_ACCESS_TAG_CODE_ALL, tagCode))
            issues.add(new FhirInvalidFhirAccessTagValidationItem(resourceFile, resourceUrl,
                    "Read‑access tag must be system='" + READ_ACCESS_TAG_SYSTEM + "', code='" + READ_ACCESS_TAG_CODE_ALL +
                            "' (found system='" + tagSystem + "', code='" + tagCode + "')."));
        else
            issues.add(ok(resourceFile, resourceUrl,
                    "Read‑access tag ok (system '" + tagSystem + "', code '" + tagCode + "')."));

        /* (5) Process‑Authorization‑Extension  */
        NodeList authExts = xp(doc, AUTH_EXT_BASE_XP);
        if (authExts == null || authExts.getLength() == 0)
        {
            issues.add(new FhirNoExtensionProcessAuthorizationFoundValidationItem(resourceFile, resourceUrl));
        }
        else
        {
            issues.add(ok(resourceFile, resourceUrl,
                    "Found extension-process-authorization (" + authExts.getLength() + ")."));

            for (int i = 0; i < authExts.getLength(); i++)
            {
                Node authExt = authExts.item(i);

                /* requester  */
                NodeList requesterNodes = xp(authExt, AUTH_EXT_REQUESTER_XP);
                if (requesterNodes.getLength() == 0)
                    issues.add(new FhirActivityDefinitionEntryMissingRequesterValidationItem(resourceFile, resourceUrl,
                            "No <extension url='requester'> found in process-authorization."));
                else
                {
                    issues.add(ok(resourceFile, resourceUrl,
                            "Found <extension url='requester'> (" + requesterNodes.getLength() + ")."));
                    checkAuthorizationCodings(requesterNodes, resourceFile, resourceUrl, issues, true);
                }

                /* recipient  */
                NodeList recipientNodes = xp(authExt, AUTH_EXT_RECIPIENT_XP);
                if (recipientNodes.getLength() == 0)
                    issues.add(new FhirActivityDefinitionEntryMissingRecipientValidationItem(resourceFile, resourceUrl,
                            "No <extension url='recipient'> found in process-authorization."));
                else
                {
                    issues.add(ok(resourceFile, resourceUrl,
                            "Found <extension url='recipient'> (" + recipientNodes.getLength() + ")."));
                    checkAuthorizationCodings(recipientNodes, resourceFile, resourceUrl, issues, false);
                }
            }
        }

        return issues;
    }

    /*
      Helper: authorization coding validation
       */
    /**
     * Validates {@code requester} or {@code recipient} sub‑extensions.
     * <ul>
     *     <li>{@code system} must equal {@value #PROCESS_AUTHORIZATION_SYSTEM}</li>
     *     <li>{@code code} must be known to {@link FhirAuthorizationCache}</li>
     * </ul>
     */
    private void checkAuthorizationCodings(NodeList nodes,
                                           File resourceFile,
                                           String resourceUrl,
                                           List<FhirElementValidationItem> issues,
                                           boolean requester)
    {
        final String elementName = requester ? "requester" : "recipient";

        for (int i = 0; i < nodes.getLength(); i++)
        {
            Node node = nodes.item(i);

            final String systemVal = val(node, AUTH_CODING_SYSTEM_XP);
            final String codeVal   = val(node, AUTH_CODING_CODE_XP);

            // (a) system & code must be present
            if (blank(systemVal) || blank(codeVal))
            {
                issues.add(createAuthValidationError(requester, resourceFile, resourceUrl,
                        "Missing <system> or <code> in '" + elementName + "' valueCoding."));
                continue;
            }

            // (b) system must match DSF process‑authorization system
            if (!PROCESS_AUTHORIZATION_SYSTEM.equals(systemVal))
            {
                issues.add(createAuthValidationError(requester, resourceFile, resourceUrl,
                        "'" + elementName + "' valueCoding.system must be '" + PROCESS_AUTHORIZATION_SYSTEM +
                                "' (found '" + systemVal + "')."));
                continue;
            }

            // (c) code must be recognised
            if (!FhirAuthorizationCache.isKnown(PROCESS_AUTHORIZATION_SYSTEM, codeVal))
            {
                issues.add(createAuthValidationError(
                        requester,
                        resourceFile,
                        resourceUrl,
                        "'" + elementName + "' code '" + codeVal +
                                "' is not known in CodeSystem '" + PROCESS_AUTHORIZATION_SYSTEM + "'."));
                continue;
            }

            // success
            issues.add(ok(resourceFile, resourceUrl,
                    "'" + elementName + "' coding system and code are valid (" + codeVal + ")."));
        }
    }

    private FhirElementValidationItem createAuthValidationError(boolean requester,
                                                                File file,
                                                                String url,
                                                                String message)
    {
        return requester
                ? new FhirActivityDefinitionEntryInvalidRequesterValidationItem(file, url, message)
                : new FhirActivityDefinitionEntryInvalidRecipientValidationItem(file, url, message);
    }
}
