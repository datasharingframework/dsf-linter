package dev.dsf.utils.validator.fhir;

import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.AbstractFhirInstanceValidator;
import dev.dsf.utils.validator.util.FhirAuthorizationCache;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Validator for FHIR {@code ActivityDefinition} resources.
 *
 * <p>The validator performs the following checks:</p>
 * <ul>
 *     <li><strong>Mandatory elements</strong><br/>
 *         <ul>
 *             <li>{@code <url>} must be present and non‑blank.</li>
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
 *             <li>Each {@code requester} and {@code recipient} sub‑extension must contain a {@code valueCoding}
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
public class FhirActivityDefinitionValidator extends AbstractFhirInstanceValidator
{
    /** DSF CodeSystem for read‑access‑tag entries. */
    private static final String READ_ACCESS_TAG_SYSTEM = "http://dsf.dev/fhir/CodeSystem/read-access-tag";

    /** Allowed code for global read access. */
    private static final String READ_ACCESS_TAG_CODE_ALL = "ALL";

    /** DSF CodeSystem for process‑authorization requester / recipient codings. */
    private static final String PROCESS_AUTHORIZATION_SYSTEM = "http://dsf.dev/fhir/CodeSystem/process-authorization";

    @Override
    public boolean canValidate(Document document)
    {
        return "ActivityDefinition".equals(document.getDocumentElement().getLocalName());
    }

    @Override
    public List<FhirElementValidationItem> validate(Document document, File resourceFile)
    {
        List<FhirElementValidationItem> issues = new ArrayList<>();

        /*
         * (1) Mandatory elements: <url>
         */
        String resourceUrl = extractSingleNodeValue(document,
                "/*[local-name()='ActivityDefinition']/*[local-name()='url']/@value");
        if (resourceUrl == null || resourceUrl.isBlank())
        {
            issues.add(new InvalidFhirUrlValidationItem(resourceFile, null,
                    "ActivityDefinition is missing <url> or it is empty."));
        }
        else
        {
            issues.add(new FhirElementValidationItemSuccess(resourceFile, resourceUrl,
                    "Success: <url> found: '" + resourceUrl + "'."));
        }

        /*
         * (2) Mandatory elements: <status>
         */
        String statusVal = extractSingleNodeValue(document,
                "/*[local-name()='ActivityDefinition']/*[local-name()='status']/@value");
        if (statusVal == null || statusVal.isBlank())
        {
            issues.add(new InvalidFhirStatusValidationItem(resourceFile, resourceUrl,
                    "ActivityDefinition is missing <status> or it is empty."));
        }
        else if (!"unknown".equals(statusVal))
        {
            issues.add(new FhirStatusIsNotSetAsUnknown(resourceFile, resourceUrl,
                    "ActivityDefinition <status> must be 'unknown' (found '" + statusVal + "')."));
        }
        else
        {
            issues.add(new FhirElementValidationItemSuccess(resourceFile, resourceUrl,
                    "Success: <status> is set to 'unknown'."));
        }

        /*
         * (3) Mandatory elements: <kind>
         */
        String kindVal = extractSingleNodeValue(document,
                "/*[local-name()='ActivityDefinition']/*[local-name()='kind']/@value");
        if (kindVal == null || kindVal.isBlank())
        {
            issues.add(new FhirKindIsMissingOrEmptyValidationItem(resourceFile, resourceUrl));
        }
        else if (!"Task".equals(kindVal))
        {
            issues.add(new FhirKindNotSetAsTaskValidationItem(resourceFile, resourceUrl,
                    "ActivityDefinition <kind> must be 'Task' (found '" + kindVal + "')."));
        }
        else
        {
            issues.add(new FhirElementValidationItemSuccess(resourceFile, resourceUrl,
                    "Success: <kind> is 'Task'."));
        }

        /*
         * (4) Read‑Access Tag
         */
        String tagSystem = extractSingleNodeValue(document,
                "/*[local-name()='ActivityDefinition']/*[local-name()='meta']/*[local-name()='tag'][1]/*[local-name()='system'][1]/@value");
        String tagCode = extractSingleNodeValue(document,
                "/*[local-name()='ActivityDefinition']/*[local-name()='meta']/*[local-name()='tag'][1]/*[local-name()='code'][1]/@value");

        if (tagSystem == null || tagCode == null)
        {
            issues.add(new FhirMissingFhirAccessTagValidationItem(resourceFile, resourceUrl,
                    "ActivityDefinition is missing the mandatory read‑access tag (system + code)."));
        }
        else if (!READ_ACCESS_TAG_SYSTEM.equals(tagSystem) || !READ_ACCESS_TAG_CODE_ALL.equals(tagCode))
        {
            issues.add(new FhirInvalidFhirAccessTagValidationItem(resourceFile, resourceUrl,
                    "Read‑access tag must use system '" + READ_ACCESS_TAG_SYSTEM + "' and code '" +
                            READ_ACCESS_TAG_CODE_ALL + "' (found system='" + tagSystem + "', code='" + tagCode + "')."));
        }
        else
        {
            issues.add(new FhirElementValidationItemSuccess(resourceFile, resourceUrl,
                    "Success: read‑access tag system '" + tagSystem + "', code '" + tagCode + "' recognized."));
        }

        /*
         * (5) Process‑Authorization Extension – presence + content
         */
        String authExtXPath = "/*[local-name()='ActivityDefinition']" +
                "/*[local-name()='extension' and @url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization']";
        NodeList authExtensions = evaluateXPath(document, authExtXPath);

        if (authExtensions == null || authExtensions.getLength() == 0)
        {
            issues.add(new FhirNoExtensionProcessAuthorizationFoundValidationItem(resourceFile, resourceUrl));
        }
        else
        {
            issues.add(new FhirElementValidationItemSuccess(resourceFile, resourceUrl,
                    "Success: Found extension-process-authorization."));

            for (int i = 0; i < authExtensions.getLength(); i++)
            {
                Node authExt = authExtensions.item(i);

                NodeList requesterNodes = evaluateXPath(authExt,
                        "./*[local-name()='extension' and @url='requester']");
                if (requesterNodes.getLength() == 0)
                {
                    issues.add(new ActivityDefinitionEntryMissingRequesterValidationItem(resourceFile, resourceUrl,
                            "No <extension url='requester'> found in process-authorization."));
                }
                else
                {
                    issues.add(new FhirElementValidationItemSuccess(resourceFile, resourceUrl,
                            "Success: Found <extension url='requester'> in process-authorization."));
                    checkAuthorizationCodings(requesterNodes, resourceFile, resourceUrl, issues, true);
                }

                NodeList recipientNodes = evaluateXPath(authExt,
                        "./*[local-name()='extension' and @url='recipient']");
                if (recipientNodes.getLength() == 0)
                {
                    issues.add(new ActivityDefinitionEntryMissingRecipientValidationItem(resourceFile, resourceUrl,
                            "No <extension url='recipient'> found in process-authorization."));
                }
                else
                {
                    issues.add(new FhirElementValidationItemSuccess(resourceFile, resourceUrl,
                            "Success: Found <extension url='recipient'> in process-authorization."));
                    checkAuthorizationCodings(recipientNodes, resourceFile, resourceUrl, issues, false);
                }
            }
        }

        return issues;
    }

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
        for (int i = 0; i < nodes.getLength(); i++)
        {
            Node node = nodes.item(i);

            String systemVal = extractSingleNodeValue(
                    node,
                    "./*[local-name()='valueCoding'][1]/*[local-name()='system'][1]/@value");

            String codeVal = extractSingleNodeValue(
                    node,
                    "./*[local-name()='valueCoding'][1]/*[local-name()='code'][1]/@value");


            String elementName = requester ? "requester" : "recipient";

            // Missing system / code
            if (systemVal == null || codeVal == null)
            {
                issues.add(createAuthValidationError(requester, resourceFile, resourceUrl,
                        "Missing <system> or <code> in '" + elementName + "' valueCoding."));
                continue;
            }

            // Wrong system
            if (!PROCESS_AUTHORIZATION_SYSTEM.equals(systemVal))
            {
                issues.add(createAuthValidationError(requester, resourceFile, resourceUrl,
                        "'" + elementName + "' valueCoding.system must be '" + PROCESS_AUTHORIZATION_SYSTEM +
                                "' (found '" + systemVal + "')."));
                continue;
            }

            // Unknown code
            if (!FhirAuthorizationCache.isKnownAuthorizationCode(codeVal))
            {
                issues.add(createAuthValidationError(requester, resourceFile, resourceUrl,
                        "'" + elementName + "' code '" + codeVal + "' is not known in the process-authorization CodeSystem."));
            }
            else
            {
                issues.add(new FhirElementValidationItemSuccess(resourceFile, resourceUrl,
                        "Success: '" + elementName + "' coding system and code are valid (" + codeVal + ")."));
            }
        }
    }

    private FhirElementValidationItem createAuthValidationError(boolean requester,
                                                                File file,
                                                                String url,
                                                                String message)
    {
        return requester ? new ActivityDefinitionEntryInvalidRequesterValidationItem(file, url, message)
                : new ActivityDefinitionEntryInvalidRecipientValidationItem(file, url, message);
    }
}
