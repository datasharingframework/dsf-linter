package dev.dsf.linter.fhir;

import dev.dsf.linter.DsfLinter;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.linting.LintingUtils;
import dev.dsf.linter.util.resource.FhirResourceLocator;
import dev.dsf.linter.util.resource.FhirResourceParser;
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
 * Specialized linter for FHIR {@code Task} resources conforming to the Data Sharing Framework (DSF)
 * base profile {@code http://dsf.dev/fhir/StructureDefinition/dsf-task-base}.
 *
 * <p>This linter ensures structural correctness, semantic consistency, profile conformance, and
 * cardinality compliance for DSF Task instances used in clinical data sharing workflows. It performs
 * comprehensive validation of Task metadata, input parameters, status transitions, authorization
 * references, and terminology bindings.</p>
 *
 * <h2>Linting Scope and Responsibilities</h2>
 *
 * <h3>1. Metadata and Profile Validation</h3>
 * <ul>
 *   <li>Verifies presence and correctness of {@code meta.profile} pointing to a DSF Task profile</li>
 *   <li>Validates {@code instantiatesCanonical} reference to a FHIR ActivityDefinition resource
 *       and ensures it ends with the required {@code |#{version}} placeholder</li>
 *   <li>Cross-checks that the referenced {@code ActivityDefinition} exists in the project structure</li>
 * </ul>
 *
 * <h3>2. Fixed Element Validation</h3>
 * <ul>
 *   <li>{@code Task.status} must be {@code "draft"} for template Task instances</li>
 *   <li>{@code Task.intent} must be {@code "order"}</li>
 *   <li>{@code Task.requester.identifier.system} must be {@code http://dsf.dev/sid/organization-identifier}</li>
 *   <li>{@code Task.restriction.recipient.identifier.system} must be {@code http://dsf.dev/sid/organization-identifier}</li>
 * </ul>
 *
 * <h3>3. Development Placeholder Validation</h3>
 * <p>Ensures that template variables are present in development-stage Task instances:</p>
 * <ul>
 *   <li>{@code authoredOn} must contain {@code #{date}}</li>
 *   <li>{@code requester.identifier.value} must be {@code #{organization}}</li>
 *   <li>{@code restriction.recipient.identifier.value} must be {@code #{organization}}</li>
 *   <li>{@code instantiatesCanonical} must end with {@code |#{version}}</li>
 * </ul>
 *
 * <h3>4. Task.input Slice Validation</h3>
 * <p>Performs comprehensive validation of {@code Task.input} elements based on the BPMN message
 * system ({@code http://dsf.dev/fhir/CodeSystem/bpmn-message}):</p>
 * <ul>
 *   <li><strong>message-name:</strong> Required in all cases (min=1, max=1)</li>
 *   <li><strong>business-key:</strong> Required when {@code status} is {@code "in-progress"},
 *       {@code "completed"}, or {@code "failed"}; must be absent when {@code status = "draft"}</li>
 *   <li><strong>correlation-key:</strong> Optional, validated against StructureDefinition cardinality</li>
 *   <li>Each {@code input} must contain {@code type.coding.system}, {@code type.coding.code}, and a {@code value[x]} element</li>
 *   <li>Duplicate detection: no two {@code input} elements may share the same {@code system#code} combination</li>
 * </ul>
 *
 * <h3>5. Cardinality Enforcement</h3>
 * <p>Dynamically loads the {@code StructureDefinition} corresponding to {@code meta.profile} and validates:</p>
 * <ul>
 *   <li>Total {@code Task.input} count against base element cardinality ({@code Task.input.min}..{@code Task.input.max})</li>
 *   <li>Per-slice occurrence counts against slice-specific cardinality (e.g., {@code Task.input:message-name.min}..{@code Task.input:message-name.max})</li>
 *   <li>Slice cardinality rules are loaded from {@code element[@id='Task.input:sliceName']} in the StructureDefinition XML/JSON</li>
 * </ul>
 * <p>See <a href="https://hl7.org/fhir/profiling.html#slice-cardinality" target="_blank">FHIR Profiling Rules §5.1.0.14</a>
 * for details on slice cardinality semantics.</p>
 *
 * <h3>6. Terminology Validation</h3>
 * <ul>
 *   <li>Cross-checks all {@code coding.system} and {@code coding.code} pairs against known DSF CodeSystems
 *       via {@link FhirAuthorizationCache}</li>
 *   <li>Reports unknown codes or systems as linting errors</li>
 *   <li>Validates {@code Task.status} against the HL7 FHIR {@code TaskStatus} ValueSet</li>
 * </ul>
 *
 * <h3>7. Authorization Reference Validation</h3>
 * <p>Validates that requester and recipient organization identifiers use the correct placeholder format
 * during development. In production environments, these placeholders are replaced with actual organization
 * identifiers defined in the corresponding {@code ActivityDefinition}'s authorization extension.</p>
 *
 * <h2>Integration and Usage</h2>
 * <p>This linter extends {@link AbstractFhirInstanceLinter} and is automatically registered with the
 * DSF linting framework. It is invoked by {@link DsfLinter} when processing FHIR resource directories.</p>
 *
 * <p><strong>Thread Safety:</strong> This class is stateless and thread-safe. Multiple threads may
 * safely invoke {@link #lint(Document, File)} concurrently on the same instance.</p>
 *
 * <h2>Project Root Discovery</h2>
 * <p>The linter requires access to the project root directory to resolve cross-references to
 * {@code ActivityDefinition} and {@code StructureDefinition} files. Discovery is performed in the following order:</p>
 * <ol>
 *   <li><strong>Explicit configuration:</strong> System property {@code dsf.projectRoot} or environment
 *       variable {@code DSF_PROJECT_ROOT}</li>
 *   <li><strong>Maven/Gradle layout:</strong> Walks up the directory tree until a folder containing
 *       {@code src/} is found (typical for IDE and local builds)</li>
 *   <li><strong>CI/exploded JAR layout:</strong> Walks up until a folder containing {@code fhir/} is found
 *       (used in CI pipelines where the plugin JAR is exploded)</li>
 * </ol>
 *
 * <h2>Lint Result Reporting</h2>
 * <p>All validation outcomes are reported as instances of {@link FhirElementLintItem}. Specific error types include:</p>
 * <ul>
 *   <li>{@link FhirTaskMissingProfileLintItem} – missing {@code meta.profile}</li>
 *   <li>{@link FhirTaskMissingInstantiatesCanonicalLintItem} – missing {@code instantiatesCanonical}</li>
 *   <li>{@link FhirTaskStatusNotDraftLintItem} – {@code status} is not {@code "draft"}</li>
 *   <li>{@link FhirTaskRequiredInputWithCodeMessageNameLintItem} – missing {@code message-name} input</li>
 *   <li>{@link FhirTaskInputDuplicateSliceLintItem} – duplicate {@code system#code} in {@code Task.input}</li>
 *   <li>{@link FhirTaskInputInstanceCountBelowMinLintItem} / {@link FhirTaskInputInstanceCountExceedsMaxLintItem} – cardinality violations</li>
 *   <li>{@link FhirTaskUnknownCodeLintItem} – unknown terminology code</li>
 *   <li>...and many others (see package {@code dev.dsf.linter.output.item})</li>
 * </ul>
 * <p>Success outcomes are reported via {@link FhirElementLintItemSuccess} for completeness and traceability.</p>
 *
 * <h2>Example Output</h2>
 * <pre>
 * ✓ Task[example-start-process]: meta.profile present.
 * ✓ Task[example-start-process]: instantiatesCanonical ends with '|#{version}' as expected.
 * ✓ Task[example-start-process]: status = 'draft'
 * ✓ Task[example-start-process]: mandatory slice 'message-name' present
 * ✗ Task[example-start-process]: business-key must not be present when status is 'draft'
 * </pre>
 *
 * @see DsfLinter
 * @see AbstractFhirInstanceLinter
 * @see FhirResourceLocator
 * @see FhirResourceParser
 * @see FhirAuthorizationCache
 * @see <a href="https://hl7.org/fhir/profiling.html#slice-cardinality">FHIR Slicing and Cardinality Rules</a>
 * @see <a href="https://dsf.dev/intro/info/process-plugin/starting-and-naming-processes/">DSF Process Plugin Documentation</a>
 *
 * @author DSF Development Team
 * @since 1.0
 */
public final class FhirTaskLinter extends AbstractFhirInstanceLinter
{
    // XPath constants
    private static final String TASK_XP           = "/*[local-name()='Task']";
    private static final String INPUT_XP          = TASK_XP + "/*[local-name()='input']";
    private static final String CODING_SYS_XP     = "./*[local-name()='type']/*[local-name()='coding']/*[local-name()='system']/@value";
    private static final String CODING_CODE_XP    = "./*[local-name()='type']/*[local-name()='coding']/*[local-name()='code']/@value";

    private static final String SYSTEM_BPMN_MSG   = "http://dsf.dev/fhir/CodeSystem/bpmn-message";
    private static final String SYSTEM_ORG_ID     = "http://dsf.dev/sid/organization-identifier";

    private static final Set<String> STATUSES_NEED_BIZKEY =
            Set.of("in-progress", "completed", "failed");



    /**
     * Determines whether this linter supports the given document.
     *
     * @param d the DOM document to lint
     * @return true if it is a FHIR Task resource, false otherwise
     */
    @Override
    public boolean canLint(Document d)
    {
        return "Task".equals(d.getDocumentElement().getLocalName());
    }

    /**
     * Performs linting of a FHIR Task resource.
     *
     * @param doc the DOM document representing the Task
     * @param resFile the file associated with the resource
     * @return a list of linting issues or confirmations
     */
    @Override
    public List<FhirElementLintItem> lint(Document doc, File resFile)
    {
        final String ref   = computeReference(doc, resFile);
        final List<FhirElementLintItem> issues = new ArrayList<>();

        checkMetaAndBasic(doc, resFile, ref, issues);
        checkPlaceholders(doc, resFile, ref, issues);

        lintInputs(doc, resFile, ref, issues);

        lintTerminology(doc, resFile, ref, issues);

        lintRequesterAuthorization(doc, resFile, ref, issues);
        lintRecipientAuthorization(doc, resFile, ref, issues);

        return issues;
    }

    /**
     * lints core metadata and basic elements of the Task.
     */
    private void checkMetaAndBasic(Document doc, File f, String ref, List<FhirElementLintItem> out)
    {
        //  meta.profile
        NodeList prof = xp(doc, TASK_XP + "/*[local-name()='meta']/*[local-name()='profile']/@value");
        if (prof == null || prof.getLength() == 0)
            out.add(new FhirTaskMissingProfileLintItem(f, ref));
        else
            out.add(ok(f, ref, "meta.profile present."));

        //  instantiatesCanonical
        String instCanon = val(doc, TASK_XP + "/*[local-name()='instantiatesCanonical']/@value");
        if (blank(instCanon))
            out.add(new FhirTaskMissingInstantiatesCanonicalLintItem(f, ref));
        else
        {
            out.add(ok(f, ref, "instantiatesCanonical found."));

            // Canonical URL with version suffix: http(s)://.../ActivityDefinition/...|#{version}
            int pipe = instCanon.lastIndexOf('|');
            String versionPart = pipe >= 0 ? instCanon.substring(pipe + 1) : "";

            // Check for placeholders - warn if NO placeholders found
            if ("#{version}".equals(versionPart) && instCanon.endsWith("#{version}")) {
                out.add(ok(f, ref, "instantiatesCanonical ends with '|#{version}' as expected."));
            } else {
                out.add(new FhirTaskInstantiatesCanonicalPlaceholderLintItem(f, ref,
                        "instantiatesCanonical must end with '|#{version}', got: '" + instCanon + "'"));
            }

            // Existence-Check
            File root = determineProjectRoot(f);
            FhirResourceLocator locator = FhirResourceLocator.create(root);
            boolean exists = locator.activityDefinitionExistsForInstantiatesCanonical(instCanon, root);
            if (!exists)
                out.add(new FhirTaskUnknownInstantiatesCanonicalLintItem(
                        f, ref,
                        "No ActivityDefinition '" + instCanon + "' under '" +
                                f.getName() + "'."));
            else
                out.add(ok(f, ref, "ActivityDefinition exists."));
        }

        //  status
        String status = val(doc, TASK_XP + "/*[local-name()='status']/@value");
        if (blank(status))
            out.add(new FhirTaskMissingStatusLintItem(f, ref));
        else
        if (!"draft".equals(status))
            out.add(new FhirTaskStatusNotDraftLintItem(f, ref,
                    "status must be 'draft' (found '" + status + "')"));
        else
            out.add(ok(f, ref, "status = 'draft'"));


        //  intent ('order')
        String intent = val(doc, TASK_XP + "/*[local-name()='intent']/@value");
        if (!"order".equals(intent))
            out.add(new FhirTaskValueIsNotSetAsOrderLintItem(f, ref,
                    "intent must be 'order' (found '" + intent + "')"));
        else
            out.add(ok(f, ref, "intent = order"));

        //  requester.identifier.system
        String reqSys = val(doc, TASK_XP +
                "/*[local-name()='requester']/*[local-name()='identifier']/*[local-name()='system']/@value");
        if(blank(reqSys))
            out.add(new FhirTaskMissingRequesterLintItem(f, ref));

        else if (!SYSTEM_ORG_ID.equals(reqSys))
            out.add(new FhirTaskInvalidRequesterLintItem(f, ref,
                    "requester.identifier.system must be '" + SYSTEM_ORG_ID + "'"));
        else
            out.add(ok(f, ref, "requester.identifier.system OK"));

        //  restriction.recipient.identifier.system
        String recSys = val(doc, TASK_XP +
                "/*[local-name()='restriction']/*[local-name()='recipient']" +
                "/*[local-name()='identifier']/*[local-name()='system']/@value");
        if(blank(recSys))
            out.add(new FhirTaskMissingRecipientLintItem(f, ref));

        else if (!SYSTEM_ORG_ID.equals(recSys))
            out.add(new FhirTaskInvalidRecipientLintItem(f, ref,
                    "restriction.recipient.identifier.system must be '" + SYSTEM_ORG_ID + "'"));
        else
            out.add(ok(f, ref, "restriction.recipient.identifier.system OK"));
    }

    /**
     * lints presence of required development placeholders such as #{date} and #{organization}.
     */
    private void checkPlaceholders(Document doc, File f, String ref, List<FhirElementLintItem> out)
    {
        String authoredOn = val(doc, TASK_XP + "/*[local-name()='authoredOn']/@value");
        if (authoredOn != null && !authoredOn.contains("#{date}"))
            out.add(new FhirTaskDateNoPlaceholderLintItem(f, ref,
                    "<authoredOn> must contain '#{date}'."));
        else
            out.add(ok(f, ref, "<authoredOn> placeholder OK."));

        String reqIdVal = val(doc,
                TASK_XP + "/*[local-name()='requester']/*[local-name()='identifier']" +
                        "/*[local-name()='value']/@value");
        if (reqIdVal == null || !reqIdVal.equals("#{organization}"))
            out.add(new FhirTaskRequesterOrganizationNoPlaceholderLintItem(f, ref,
                    "requester.identifier.value must contain '#{organization}'."));
        else
            out.add(ok(f, ref, "requester.identifier.value placeholder OK."));

        String recIdVal = val(doc,
                TASK_XP + "/*[local-name()='restriction']/*[local-name()='recipient']" +
                        "/*[local-name()='identifier']/*[local-name()='value']/@value");
        if (recIdVal == null || !recIdVal.equals("#{organization}"))
            out.add(new FhirTaskRecipientOrganizationNoPlaceholderLintItem(f, ref,
                    "restriction.recipient.identifier.value must contain '#{organization}'."));
        else
            out.add(ok(f, ref, "restriction.recipient.identifier.value placeholder OK."));
    }

    /**
     * lints the <code>Task.input</code> elements of a FHIR {@code Task} resource.
     * This method performs structural, semantic, and rule-based linting for all {@code Task.input} elements
     * based on the provided {@code StructureDefinition} and known business constraints.
     *
     * <p>Linter includes the following aspects:</p>
     *
     * <ul>
     *   <li><strong>Presence Check:</strong>
     *     <ul>
     *       <li>Fails if the {@code Task.input} list is empty or missing entirely.</li>
     *     </ul>
     *   </li>
     *
     *   <li><strong>Structural linting:</strong>
     *     <ul>
     *       <li>Each {@code Task.input} must contain a {@code coding.system} and {@code coding.code}.</li>
     *       <li>If missing or blank, an error is reported.</li>
     *       <li>Presence of a {@code value[x]} element (e.g., {@code valueString}, {@code valueReference}) is required.</li>
     *     </ul>
     *   </li>
     *
     *   <li><strong>Duplicate Detection:</strong>
     *     <ul>
     *       <li>Checks for duplicate {@code Task.input} entries using the combination {@code system#code}.</li>
     *       <li>Reports an error if the same combination appears more than once.</li>
     *       <li>Emits an INFO result if no duplicates are found.</li>
     *     </ul>
     *   </li>
     *
     *   <li><strong>BPMN Slice Checks:</strong> (for {@code system = http://dsf.dev/fhir/CodeSystem/bpmn-message})
     *     <ul>
     *       <li>{@code message-name} – required and must be present.</li>
     *       <li>{@code business-key} – required or forbidden depending on {@code Task.status}.</li>
     *       <li>{@code correlation-key} – permitted or prohibited based on slice cardinality.</li>
     *     </ul>
     *   </li>
     *
     *   <li><strong>Status-based Business Rules:</strong>
     *     <ul>
     *       <li>If {@code status} ∈ {"in-progress", "completed", "failed"}, {@code business-key} is required.</li>
     *       <li>If {@code status} = "draft", {@code business-key} must be absent.</li>
     *       <li>If {@code status} is valid but not in any rule set, a skip notice is emitted.</li>
     *     </ul>
     *   </li>
     *
     *   <li><strong>Status linting:</strong>
     *     <ul>
     *       <li>Ensures the {@code Task.status} value is among the known valid codes from the HL7 Task ValueSet.</li>
     *       <li>Unknown values result in an error; valid ones emit a confirmation.</li>
     *     </ul>
     *   </li>
     *
     *   <li><strong>Correlation Input linting:</strong>
     *     <ul>
     *       <li>If present, {@code correlation-key} is only allowed if cardinality permits.</li>
     *       <li>If absent but required (min > 0), an error is reported.</li>
     *     </ul>
     *   </li>
     *
     *   <li><strong>Cardinality Checks:</strong>
     *     <ul>
     *       <li>Loads base and slice-specific {@code min}/{@code max} values from the {@code StructureDefinition}.</li>
     *       <li>Checks total {@code Task.input} count against base cardinality.</li>
     *       <li>Checks individual slice occurrence counts against their defined cardinality.</li>
     *       <li>If profile cannot be loaded, these checks are skipped with a warning.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>All lint outcomes are reported as {@link FhirElementLintItem} instances and
     * appended to the provided {@code out} list.</p>
     *
     * @param doc  the XML DOM of the {@code Task} resource
     * @param f    the source file for the Task resource (used for context and reporting)
     * @param ref  a logical or canonical identifier (typically {@code instantiatesCanonical})
     * @param out  a mutable list that will be populated with linting results
     *
     * @see FhirTaskMissingInputLintItem
     * @see FhirTaskInputRequiredCodingSystemAndCodingCodeLintItem
     * @see FhirTaskInputMissingValueLintItem
     * @see FhirTaskInputDuplicateSliceLintItem
     * @see FhirTaskRequiredInputWithCodeMessageNameLintItem
     * @see FhirTaskStatusRequiredInputBusinessKeyLintItem
     * @see FhirTaskBusinessKeyExistsLintItem
     * @see FhirTaskBusinessKeyCheckIsSkippedLintItem
     * @see FhirTaskUnknownStatusLintItem
     * @see FhirTaskCorrelationExistsLintItem
     * @see FhirTaskCorrelationMissingButRequiredLintItem
     * @see FhirTaskInputInstanceCountBelowMinLintItem
     * @see FhirTaskInputInstanceCountExceedsMaxLintItem
     * @see FhirTaskInputSliceCountBelowSliceMinLintItem
     * @see FhirTaskInputSliceCountExceedsSliceMaxLintItem
     * @see FhirTaskCouldNotLoadProfileLintItem
     */
    private void lintInputs(Document doc, File f, String ref, List<FhirElementLintItem> out)
    {
        // Load cardinality definitions from StructureDefinition
        String profileUrl = val(doc, TASK_XP + "/*[local-name()='meta']/*[local-name()='profile']/@value");
        Map<String, SliceCard> cards = loadInputCardinality(determineProjectRoot(f), profileUrl);

        if (cards == null)
        {
            out.add(new FhirTaskCouldNotLoadProfileLintItem(
                    f, ref, "StructureDefinition for profile '" + profileUrl + "' not found → instance-level cardinality check skipped."));
        }

        NodeList ins = xp(doc, INPUT_XP);
        if (ins == null || ins.getLength() == 0)
        {
            out.add(new FhirTaskMissingInputLintItem(f, ref));
            return;
        }

        boolean messageName = false, businessKey = false;
        boolean correlation = false;

        Map<String, Integer> duplicates = new HashMap<>();
        Map<String, Integer> sliceCounter = new HashMap<>();
        int inputCount = 0;

        for (int i = 0; i < ins.getLength(); i++)
        {
            Node in = ins.item(i);
            String sys = val(in, CODING_SYS_XP);
            String code = val(in, CODING_CODE_XP);
            String v = extractValueX(in);
            inputCount++;

            // Duplicate counter
            if (!blank(sys) && !blank(code))
                duplicates.merge(sys + "#" + code, 1, Integer::sum);

            // Slice counter (by code)
            if (!blank(code))
                sliceCounter.merge(code, 1, Integer::sum);

            // Missing coding
            if (blank(sys) || blank(code))
            {
                out.add(new FhirTaskInputRequiredCodingSystemAndCodingCodeLintItem(f, ref,
                        "Task.input without system/code"));
                continue;
            }
            else
            {
                out.add(ok(f, ref, "Task.input has required system and code: " + sys + "#" + code));
            }

            // Value check
            if (blank(v))
                out.add(new FhirTaskInputMissingValueLintItem(f, ref,
                        "Task.input(" + code + ") missing value[x]"));
            else
                out.add(ok(f, ref,
                        "input '" + code + "' value='" + v + "'"));

            // Slice detection
            if (SYSTEM_BPMN_MSG.equals(sys))
            {
                switch (code)
                {
                    case "message-name" -> messageName = true;
                    case "business-key" -> businessKey = true;
                    case "correlation-key" -> correlation = true;
                }
            }
        }

        // Duplicates
        duplicates.forEach((k, v) -> {
            if (v > 1)
                out.add(new FhirTaskInputDuplicateSliceLintItem(f, ref,
                        "Duplicate slice '" + k + "' (" + v + "×)"));
        });

        // Add success case for no duplicates found
        if (duplicates.values().stream().allMatch(count -> count == 1))
        {
            out.add(ok(f, ref, "No duplicate Task.input slices detected"));
        }

        // Required presence
        if (messageName)
            out.add(ok(f, ref, "mandatory slice 'message-name' present"));
        else
            out.add(new FhirTaskRequiredInputWithCodeMessageNameLintItem(f, ref));

        // Status-dependent rule
        String status = val(doc, TASK_XP + "/*[local-name()='status']/@value");
        boolean statusIsDraft = "draft".equals(status);

        // Check if status is known
        if (FhirAuthorizationCache.isUnknown(FhirAuthorizationCache.CS_TASK_STATUS, status)) {
            out.add(new FhirTaskUnknownStatusLintItem(f, ref, status));
        } else {
            out.add(ok(f, ref, "Task status '" + status + "' is valid"));
        }

        if (STATUSES_NEED_BIZKEY.contains(status)) {
            if (!businessKey)
                out.add(new FhirTaskStatusRequiredInputBusinessKeyLintItem(
                        f, ref, "status='" + status + "' needs business-key"));
            else
                out.add(ok(f, ref, "status='" + status + "' → business-key present as required"));
        }
        else if (statusIsDraft) {
            if (businessKey) {
                out.add(new FhirTaskBusinessKeyExistsLintItem(
                        f, ref, "businessKey must not be present when status is 'draft'"));
            } else {
                out.add(ok(f, ref, "status=draft → business-key correctly absent"));
            }
        }
        else {
            // Status is known but doesn't require specific business key rules
            out.add(new FhirTaskBusinessKeyCheckIsSkippedLintItem(
                    f, ref, "Business key linting skipped for status '" + status + "'"));
        }

        boolean corrAllowed = isCorrelationAllowed(cards);

        if (correlation) {
            if (corrAllowed)
                out.add(ok(f, ref,
                        "correlation input present and permitted by StructureDefinition"));
            else
                out.add(new FhirTaskCorrelationExistsLintItem(
                        f, ref, "correlation input is not allowed by StructureDefinition"));
        } else {
            // missing ↔ only an error when the slice min > 0
            SliceCard corrCard = (cards != null) ? cards.get("correlation-key") : null;
            if (corrCard != null && corrCard.min() > 0)
                out.add(new FhirTaskCorrelationMissingButRequiredLintItem(
                        f, ref, "correlation input missing but slice min-cardinality is "
                        + corrCard.min()));
            else
                out.add(ok(f, ref, "correlation input absent as expected"));
        }

        // Cardinality check
        if (cards != null)
        {
            SliceCard baseCard = cards.get("__BASE__");
            if (inputCount < baseCard.min())
                out.add(new FhirTaskInputInstanceCountBelowMinLintItem(f, ref, inputCount, baseCard.min()));
            else if (inputCount > baseCard.max())
                out.add(new FhirTaskInputInstanceCountExceedsMaxLintItem(f, ref, inputCount, baseCard.max()));
            else
                out.add(ok(f, ref, "Task.input count " + inputCount +
                        " within " + baseCard.min() + "‥" + (baseCard.max() == Integer.MAX_VALUE ? "*" : baseCard.max())));

            // Per-slice cardinality
            cards.forEach((code, card) -> {
                if ("__BASE__".equals(code)) return;
                int cnt = sliceCounter.getOrDefault(code, 0);
                if (cnt < card.min())
                    out.add(new FhirTaskInputSliceCountBelowSliceMinLintItem(f, ref, code, cnt, card.min()));
                else if (cnt > card.max())
                    out.add(new FhirTaskInputSliceCountExceedsSliceMaxLintItem(f, ref, code, cnt, card.max()));
                else
                    out.add(ok(f, ref, "slice '" + code + "' count " + cnt + " OK"));
            });
        }
    }

    /*
      3) Terminology
       */
    /**
     * lints all coding elements against known DSF CodeSystems.
     */
    private void lintTerminology(Document doc, File f, String ref, List<FhirElementLintItem> out)
    {
        NodeList codings = xp(doc, "//coding");
        if (codings == null) return;

        for (int i = 0; i < codings.getLength(); i++)
        {
            Node c = codings.item(i);
            String sys = val(c, "./*[local-name()='system']/@value");
            String code = val(c, "./*[local-name()='code']/@value");

            if (FhirAuthorizationCache.isUnknown(sys, code))
                out.add(new FhirTaskUnknownCodeLintItem(f, ref,
                        "Unknown code '" + code + "' in '" + sys + "'"));
        }
    }

    /*
      Helper methods
      */

    /**
     * Extracts a reference identifier from instantiatesCanonical or identifier.value.
     */
    private String computeReference(Document doc, File file)
    {
        String canon = val(doc, TASK_XP + "/*[local-name()='instantiatesCanonical']/@value");
        if (!blank(canon))
            return canon.split("\\|")[0];

        String idVal = val(doc,
                TASK_XP + "/*[local-name()='identifier']/*[local-name()='value']/@value");
        return !blank(idVal) ? idVal : file.getName();
    }

    /**
     * Attempts to determine the root directory of the project that contains the given FHIR resource file.
     *
     * <p>This method supports multiple layout detection strategies to support local builds,
     * IDE projects, and CI pipelines:</p>
     *
     * <ol>
     *   <li><strong>Explicit configuration</strong>: Checks for system property {@code dsf.projectRoot}
     *       or environment variable {@code DSF_PROJECT_ROOT}. If either is set and points to a valid
     *       directory, that path is returned.</li>
     *
     *   <li><strong>Implicit discovery – Maven/Gradle</strong>: Walks up the directory tree and returns
     *       the first parent directory that contains a {@code src/} subdirectory. This layout is typical
     *       for local development environments and IDEs.</li>
     *
     *   <li><strong>Implicit discovery – CI or exploded JAR</strong>: If no {@code src/} folder is found,
     *       returns the first parent directory that contains a {@code fhir/} folder. This layout is used
     *       when the plugin JAR is exploded into a flat directory structure in CI environments.</li>
     * </ol>
     *
     * <p>If no valid root can be determined, {@code null} is returned.</p>
     *
     * @param res the resource file currently being linted (e.g., a Task XML file)
     * @return the project root directory, or {@code null} if no suitable folder is found
     */
    private File determineProjectRoot(File res)
    {
        return LintingUtils.getProjectRoot(res.toPath());
    }

    /*
      Requester/Recipient vs ActivityDefinition check
      */

    /**
     * lints that the {@code Task.requester.identifier.value} exists and contains the
     * required {@code #{organization}} placeholder for development contexts.
     *
     * <p>This method performs a two-step linting process for the {@code requester.identifier.value} field:</p>
     * <ul>
     *   <li><strong>Existence check:</strong> Fails if the value is missing or blank, using
     *       {@link FhirTaskRequesterIdNotExistLintItem}.</li>
     *   <li><strong>Placeholder check:</strong> If the value does not contain the required
     *       {@code #{organization}} placeholder, a
     *       {@link FhirTaskRequesterIdNoPlaceholderLintItem}
     *       is added. If the placeholder is present, a success confirmation is reported.</li>
     * </ul>
     *
     * <p>Note: This method lints placeholder presence for development contexts but does not
     * perform actual authorization checks against the {@code ActivityDefinition}. The method
     * returns early if the {@code instantiatesCanonical} is missing or if the corresponding
     * {@code ActivityDefinition} file cannot be found.</p>
     *
     * @param taskDoc   the XML DOM representation of the Task resource
     * @param taskFile  the file from which the Task was loaded (used for context and reporting)
     * @param ref       a canonical reference to the Task (typically extracted from {@code instantiatesCanonical})
     * @param out       the list of lint items to which results are appended
     *
     * @see FhirTaskRequesterIdNotExistLintItem
     * @see FhirTaskRequesterIdNoPlaceholderLintItem
     */
    private void lintRequesterAuthorization(Document taskDoc,
                                            File taskFile,
                                            String ref,
                                            List<FhirElementLintItem> out)
    {
        if (instCanonDetermine(taskDoc, taskFile)) return;

        String requesterId = val(taskDoc,
                TASK_XP + "/*[local-name()='requester']/*[local-name()='identifier']"
                        + "/*[local-name()='value']/@value");

        // Allow the dev placeholder
        if (requesterId == null || requesterId.isBlank())
        {
            out.add(new FhirTaskRequesterIdNotExistLintItem(
                    taskFile, ref));
        } else {
            if (!"#{organization}".equals(requesterId))
            {
                out.add(new FhirTaskRequesterIdNoPlaceholderLintItem(
                        taskFile, ref
                ));
            }
            else
            {
                out.add(ok(taskFile, ref, "Task.requester.identifier.value contains the '#{organization}' placeholder."));
            }
        }
    }

    /**
     * lints that the {@code Task.restriction.recipient.identifier.value} exists and contains the
     * required {@code #{organization}} placeholder for development contexts.
     *
     * <p>This method performs a two-step linting process for the {@code recipient.identifier.value} field:</p>
     * <ul>
     *   <li><strong>Existence check:</strong> Fails if the value is missing or blank, using
     *       {@link FhirTaskRecipientIdNotExistLintItem}.</li>
     *   <li><strong>Placeholder check:</strong> If the value does not contain the required
     *       {@code #{organization}} placeholder, a
     *       {@link FhirTaskRecipientIdNoPlaceholderLintItem}
     *       is added. If the placeholder is present, a success confirmation is reported.</li>
     * </ul>
     *
     * <p>Note: This method lints placeholder presence for development contexts but does not
     * perform actual authorization checks against the {@code ActivityDefinition}. The method
     * returns early if the {@code instantiatesCanonical} is missing or if the corresponding
     * {@code ActivityDefinition} file cannot be found.</p>
     *
     * @param taskDoc   the XML DOM representation of the Task resource
     * @param taskFile  the file from which the Task was loaded (used for context and reporting)
     * @param ref       a canonical reference to the Task (typically extracted from {@code instantiatesCanonical})
     * @param out       the list of lint items to which results are appended
     *
     * @see FhirTaskRecipientIdNotExistLintItem
     * @see FhirTaskRecipientIdNoPlaceholderLintItem
     */
    private void lintRecipientAuthorization(Document taskDoc,
                                            File taskFile,
                                            String ref,
                                            List<FhirElementLintItem> out)
    {
        if (instCanonDetermine(taskDoc, taskFile)) return;

        String recipientId = val(taskDoc,
                TASK_XP + "/*[local-name()='restriction']/*[local-name()='recipient']"
                        + "/*[local-name()='identifier']/*[local-name()='value']/@value");

        // Allow the dev placeholder
        if (recipientId == null || recipientId.isBlank())
        {
            out.add(new FhirTaskRecipientIdNotExistLintItem(taskFile, ref));
        }
        else
        {
            if (!"#{organization}".equals(recipientId))
            {
                out.add(new FhirTaskRecipientIdNoPlaceholderLintItem(taskFile, ref));
            }
            else
            {
                out.add(ok(taskFile, ref,
                        "Task.restriction.recipient.identifier.value contains the '#{organization}' placeholder."));
            }
        }
    }

    private boolean instCanonDetermine(Document taskDoc, File taskFile) {
        String instCanon = val(taskDoc,
                TASK_XP + "/*[local-name()='instantiatesCanonical']/@value");
        if (blank(instCanon))
            return true;

        File projectRoot = determineProjectRoot(taskFile);
        FhirResourceLocator locator = FhirResourceLocator.create(projectRoot);
        File actFile = locator.findActivityDefinitionForInstantiatesCanonical(
                instCanon, projectRoot);

        // The missing ActivityDefinition error is already reported, no need to double-report
        return actFile == null;
    }


    /* utils inside FhirTaskLinter ----------------------------------------- */
    private record SliceCard(int min, int max) {}

    /**
     * Loads the cardinality settings from the StructureDefinition for a given Task profile.
     * Tries parsing as XML first; if that fails, falls back to converting JSON to XML.
     *
     * @param projectRoot the root folder of the FHIR definitions
     * @param profileUrl  the canonical URL of the Task profile
     * @return a map of slice names to their cardinality, or null if no SD file is found or an error occurs
     */
    private Map<String, SliceCard> loadInputCardinality(File projectRoot, String profileUrl) {
        FhirResourceLocator locator = FhirResourceLocator.create(projectRoot);
        File sdFile = locator.findStructureDefinitionFile(profileUrl, projectRoot);
        if (sdFile == null) {
            return null;
        }

        try {
            // parseXml, or if that fails parseJsonToXml
            Document sd;
            try {
                sd = FhirResourceParser.parseXml(sdFile.toPath());
            } catch (Exception e) {
                sd = FhirResourceParser.parseJsonToXml(sdFile.toPath());
            }

            Map<String, SliceCard> map = new HashMap<>();

            // base element ----------------------------------------------------
            String minBase = AbstractFhirInstanceLinter.extractSingleNodeValue(
                    sd,
                    "//*[local-name()='element' and @id='Task.input']/*[local-name()='min']/@value"
            );
            String maxBase = AbstractFhirInstanceLinter.extractSingleNodeValue(
                    sd,
                    "//*[local-name()='element' and @id='Task.input']/*[local-name()='max']/@value"
            );
            int baseMin = (minBase != null) ? Integer.parseInt(minBase) : 0;
            int baseMax = (maxBase == null || "*".equals(maxBase))
                    ? Integer.MAX_VALUE
                    : Integer.parseInt(maxBase);
            map.put("__BASE__", new SliceCard(baseMin, baseMax));

            // direct slice roots ---------------------------------------------
            NodeList slices = (NodeList) XPathFactory.newInstance().newXPath()
                    .compile(
                            "//*[local-name()='element' and starts-with(@id,'Task.input:') " +
                                    "and not(contains(@id,'.'))]"
                    )
                    .evaluate(sd, XPathConstants.NODESET);

            for (int i = 0; i < slices.getLength(); i++) {
                Node n = slices.item(i);
                String sliceName = n.getAttributes()
                        .getNamedItem("id")
                        .getNodeValue()
                        .substring("Task.input:".length());

                String mi = AbstractFhirInstanceLinter.extractSingleNodeValue(
                        n,
                        "./*[local-name()='min']/@value"
                );
                String ma = AbstractFhirInstanceLinter.extractSingleNodeValue(
                        n,
                        "./*[local-name()='max']/@value"
                );
                int sMin = (mi != null) ? Integer.parseInt(mi) : 0;
                int sMax = (ma == null || "*".equals(ma)) ? baseMax : Integer.parseInt(ma);

                map.put(sliceName, new SliceCard(sMin, sMax));
            }

            return map;
        } catch (Exception e) {
            // fallback: profile could not be loaded or parsed
            return null;
        }
    }

    // helper to decide if correlation slice is allowed
    private boolean isCorrelationAllowed(Map<String, SliceCard> cards) {
        if (cards == null)                // profile could not be loaded
            return false;                 // fall back to old “forbidden” behaviour
        SliceCard c = cards.get("correlation-key");   // slice code == correlation-key
        return c != null && c.max() != 0;             // defined and max > 0 ⇒ allowed
    }


}
