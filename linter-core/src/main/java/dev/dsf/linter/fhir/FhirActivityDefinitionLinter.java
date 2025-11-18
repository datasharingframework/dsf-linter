package dev.dsf.linter.fhir;

import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.linting.AbstractFhirInstanceLinter;
import dev.dsf.linter.util.resource.FhirAuthorizationCache;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Linter for FHIR {@code ActivityDefinition} resources.
 *
 * <p>This linter validates DSF-specific ActivityDefinition resources to ensure they conform to
 * the required structure and authorization patterns used in the Data Sharing Framework.</p>
 *
 * <p><strong>Validation Checks Performed:</strong></p>
 * <ol>
 *     <li><strong>Resource URL</strong><br/>
 *         The {@code <url>} element must be present and non-blank.
 *         Reports {@link FhirActivityDefinitionInvalidFhirUrlLintItem} if missing or empty.
 *     </li>
 *     <li><strong>Status Element</strong><br/>
 *         The {@code <status>} element must be present and set to {@code "unknown"} (required by DSF).
 *         Reports {@link FhirActivityDefinitionInvalidFhirStatusLintItem} if missing or empty,
 *         or {@link FhirStatusIsNotSetAsUnknownLintItem} if set to a different value.
 *     </li>
 *     <li><strong>Kind Element</strong><br/>
 *         The {@code <kind>} element must be present and set to {@code "Task"}.
 *         Reports {@link FhirKindIsMissingOrEmptyLintItem} if missing or empty,
 *         or {@link FhirKindNotSetAsTaskLintItem} if set to a different value.
 *     </li>
 *     <li><strong>Profile Declaration</strong><br/>
 *         The {@code <meta><profile>} element should contain {@value #EXPECTED_PROFILE}.
 *         Reports {@link FhirActivityDefinitionMissingProfileLintItem} if missing or incorrect,
 *         or {@link FhirActivityDefinitionProfileHasVersionNumberLintItem} if a version suffix
 *         (e.g., {@code |1.0.0}) is present.
 *     </li>
 *     <li><strong>Read-Access Tag</strong><br/>
 *         The first {@code <meta><tag>} must specify:
 *         <ul>
 *             <li>{@code <system>} = {@value #READ_ACCESS_TAG_SYSTEM}</li>
 *             <li>{@code <code>} = {@value #READ_ACCESS_TAG_CODE_ALL}</li>
 *         </ul>
 *         This tag allows global read access to the ActivityDefinition.
 *         Reports {@link FhirMissingFhirAccessTagLintItem} if missing,
 *         or {@link FhirInvalidFhirAccessTagLintItem} if the values are incorrect.
 *     </li>
 *     <li><strong>Process-Authorization Extension</strong><br/>
 *         At least one {@code <extension url="http://dsf.dev/fhir/StructureDefinition/extension-process-authorization">}
 *         must be present. Reports {@link FhirNoExtensionProcessAuthorizationFoundLintItem} if missing.
 *         <br/>
 *         For each process-authorization extension:
 *         <ul>
 *             <li><strong>Requester sub-extension:</strong> Must contain at least one
 *                 {@code <extension url="requester">} with a valid {@code valueCoding}.
 *                 Reports {@link FhirActivityDefinitionEntryMissingRequesterLintItem} if missing,
 *                 or {@link FhirActivityDefinitionEntryInvalidRequesterLintItem} if the coding is invalid.
 *             </li>
 *             <li><strong>Recipient sub-extension:</strong> Must contain at least one
 *                 {@code <extension url="recipient">} with a valid {@code valueCoding}.
 *                 Reports {@link FhirActivityDefinitionEntryMissingRecipientLintItem} if missing,
 *                 or {@link FhirActivityDefinitionEntryInvalidRecipientLintItem} if the coding is invalid.
 *             </li>
 *             <li>Each {@code valueCoding} must have:
 *                 <ul>
 *                     <li>{@code <system>} = {@value #PROCESS_AUTHORIZATION_SYSTEM}</li>
 *                     <li>{@code <code>} = a code recognized by {@link FhirAuthorizationCache#isUnknown(String, String)}
 *                         (i.e., the code must NOT be unknown)</li>
 *                 </ul>
 *             </li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * <p><strong>Checks Intentionally Delegated to Other Linters:</strong></p>
 * <ul>
 *     <li>{@code <version>} element placeholder validation (expected value: {@code "#{version}"})</li>
 *     <li>{@code <date>} element placeholder validation (expected value: {@code "#{date}"})</li>
 *     <li>{@code <extension url="http://dsf.dev/fhir/StructureDefinition/task-profile">}</li>
 *     <li>{@code <extension url="http://dsf.dev/fhir/StructureDefinition/message-name">}</li>
 * </ul>
 *
 * <p><strong>Lint Item Types:</strong></p>
 * <p>For each validation step, the linter produces one of the following item types:</p>
 * <ul>
 *     <li><strong>Success items</strong> (neutral/info) when validation passes</li>
 *     <li><strong>Warning items</strong> for non-critical issues</li>
 *     <li><strong>Error items</strong> for validation failures that must be corrected</li>
 * </ul>
 *
 * <p><strong>References:</strong></p>
 * <ul>
 *   <li><a href="http://hl7.org/fhir/R4/activitydefinition.html">HL7 FHIR R4 – ActivityDefinition</a></li>
 *   <li><a href="https://dsf.dev/">DSF Developer Documentation</a></li>
 * </ul>
 *
 * @see AbstractFhirInstanceLinter
 * @see FhirAuthorizationCache
 */
public final class FhirActivityDefinitionLinter extends AbstractFhirInstanceLinter
{
    /*
      XPath constants
      */
    private static final String ACTIVITY_DEFINITION_XP = "/*[local-name()='ActivityDefinition']";
    private static final String URL_XP     = ACTIVITY_DEFINITION_XP + "/*[local-name()='url']/@value";
    private static final String STATUS_XP  = ACTIVITY_DEFINITION_XP + "/*[local-name()='status']/@value";
    private static final String KIND_XP    = ACTIVITY_DEFINITION_XP + "/*[local-name()='kind']/@value";

    private static final String PROFILE_XP = ACTIVITY_DEFINITION_XP +
            "/*[local-name()='meta']/*[local-name()='profile']/@value";

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
      DSF-specific CodeSystems / codes
      */
    private static final String READ_ACCESS_TAG_SYSTEM = "http://dsf.dev/fhir/CodeSystem/read-access-tag";
    private static final String READ_ACCESS_TAG_CODE_ALL = "ALL";

    private static final String PROCESS_AUTHORIZATION_SYSTEM = "http://dsf.dev/fhir/CodeSystem/process-authorization";

    private static final String EXPECTED_PROFILE = "http://dsf.dev/fhir/StructureDefinition/activity-definition";


    @Override
    public boolean canLint(Document document)
    {
        return "ActivityDefinition".equals(document.getDocumentElement().getLocalName());
    }

    @Override
    public List<FhirElementLintItem> lint(Document doc, File resourceFile)
    {
        final List<FhirElementLintItem> issues = new ArrayList<>();

        /* (1) <url>  */
        final String resourceUrl = val(doc, URL_XP);
        if (blank(resourceUrl))
            issues.add(new FhirActivityDefinitionInvalidFhirUrlLintItem(resourceFile, null,
                    "ActivityDefinition is missing <url> or it is empty."));
        else
            issues.add(ok(resourceFile, resourceUrl, "Found <url>: '" + resourceUrl + "'."));

        /* (2) <status> must be "unknown"  */
        final String statusVal = val(doc, STATUS_XP);
        if (blank(statusVal))
            issues.add(new FhirActivityDefinitionInvalidFhirStatusLintItem(resourceFile, resourceUrl,
                    "ActivityDefinition is missing <status> or it is empty."));
        else if (!"unknown".equals(statusVal))
            issues.add(new FhirStatusIsNotSetAsUnknownLintItem(resourceFile, resourceUrl,
                    "<status> must be 'unknown' (found '" + statusVal + "')."));
        else
            issues.add(ok(resourceFile, resourceUrl, "<status> is 'unknown'."));

        /* (3) <kind> must be "Task"  */
        final String kindVal = val(doc, KIND_XP);
        if (blank(kindVal))
            issues.add(new FhirKindIsMissingOrEmptyLintItem(resourceFile, resourceUrl));
        else if (!"Task".equals(kindVal))
            issues.add(new FhirKindNotSetAsTaskLintItem(resourceFile, resourceUrl,
                    "<kind> must be 'Task' (found '" + kindVal + "')."));
        else
            issues.add(ok(resourceFile, resourceUrl, "<kind> is 'Task'."));

        /* (4) <meta><profile> check  */
        final String profileVal = val(doc, PROFILE_XP);
        if (blank(profileVal))
        {
            issues.add(new FhirActivityDefinitionMissingProfileLintItem(resourceFile, resourceUrl,
                    "ActivityDefinition is missing <meta><profile> with value '" + EXPECTED_PROFILE + "'."));
        }
        else if (!profileVal.startsWith(EXPECTED_PROFILE))
        {
            issues.add(new FhirActivityDefinitionMissingProfileLintItem(resourceFile, resourceUrl,
                    "ActivityDefinition <meta><profile> should be '" + EXPECTED_PROFILE +
                            "' (found '" + profileVal + "')."));
        }
        else if (profileVal.contains("|"))
        {
            issues.add(new FhirActivityDefinitionProfileHasVersionNumberLintItem(resourceFile, resourceUrl,
                    "ActivityDefinition profile must not contain a version number (found '" + profileVal +
                            "'). Use '" + EXPECTED_PROFILE + "' without version suffix."));
        }
        else
        {
            issues.add(ok(resourceFile, resourceUrl,
                    "Profile '" + EXPECTED_PROFILE + "' is correctly specified without version."));
        }

        /* (5) Read-Access Tag  */
        final String tagSystem = val(doc, TAG_SYS_XP);
        final String tagCode   = val(doc, TAG_CODE_XP);

        if (blank(tagSystem) || blank(tagCode))
            issues.add(new FhirMissingFhirAccessTagLintItem(resourceFile, resourceUrl,
                    "Missing read-access tag (system + code)."));
        else if (!Objects.equals(READ_ACCESS_TAG_SYSTEM, tagSystem) ||
                !Objects.equals(READ_ACCESS_TAG_CODE_ALL, tagCode))
            issues.add(new FhirInvalidFhirAccessTagLintItem(resourceFile, resourceUrl,
                    "Read-access tag must be system='" + READ_ACCESS_TAG_SYSTEM + "', code='" + READ_ACCESS_TAG_CODE_ALL +
                            "' (found system='" + tagSystem + "', code='" + tagCode + "')."));
        else
            issues.add(ok(resourceFile, resourceUrl,
                    "Read-access tag ok (system '" + tagSystem + "', code '" + tagCode + "')."));

        /* (6) Process-Authorization Extension  */
        NodeList authExts = xp(doc, AUTH_EXT_BASE_XP);
        if (authExts == null || authExts.getLength() == 0)
        {
            issues.add(new FhirNoExtensionProcessAuthorizationFoundLintItem(resourceFile, resourceUrl));
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
                    issues.add(new FhirActivityDefinitionEntryMissingRequesterLintItem(resourceFile, resourceUrl,
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
                    issues.add(new FhirActivityDefinitionEntryMissingRecipientLintItem(resourceFile, resourceUrl,
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
      Helper: authorization coding linting
       */
    /**
     * lints {@code requester} or {@code recipient} sub-extensions.
     * <ul>
     *     <li>{@code system} must equal {@value #PROCESS_AUTHORIZATION_SYSTEM}</li>
     *     <li>{@code code} must be known to {@link FhirAuthorizationCache}</li>
     * </ul>
     */
    private void checkAuthorizationCodings(NodeList nodes,
                                           File resourceFile,
                                           String resourceUrl,
                                           List<FhirElementLintItem> issues,
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
                issues.add(createAuthLintError(requester, resourceFile, resourceUrl,
                        "Missing <system> or <code> in '" + elementName + "' valueCoding."));
                continue;
            }

            // (b) system must match DSF process-authorization system
            if (!PROCESS_AUTHORIZATION_SYSTEM.equals(systemVal))
            {
                issues.add(createAuthLintError(requester, resourceFile, resourceUrl,
                        "'" + elementName + "' valueCoding.system must be '" + PROCESS_AUTHORIZATION_SYSTEM +
                                "' (found '" + systemVal + "')."));
                continue;
            }

            // (c) code must be recognised
            if (FhirAuthorizationCache.isUnknown(PROCESS_AUTHORIZATION_SYSTEM, codeVal))
            {
                issues.add(createAuthLintError(
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

    private FhirElementLintItem createAuthLintError(boolean requester,
                                                    File file,
                                                    String url,
                                                    String message)
    {
        return requester
                ? new FhirActivityDefinitionEntryInvalidRequesterLintItem(file, url, message)
                : new FhirActivityDefinitionEntryInvalidRecipientLintItem(file, url, message);
    }
}