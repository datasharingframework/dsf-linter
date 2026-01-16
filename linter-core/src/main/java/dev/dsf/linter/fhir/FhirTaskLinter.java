package dev.dsf.linter.fhir;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.FhirElementLintItem;
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
 * DSF linting framework. It is invoked when processing FHIR resource directories.</p>
 *
 * <p><strong>Thread Safety:</strong> This class is stateless and thread-safe. Multiple threads may
 * safely invoke {@link #lint(Document, File)} concurrently on the same instance.</p>
 *
 * <h2>Project Root Discovery</h2>
 * <p>The linter requires access to the project root directory to resolve cross-references to
 * {@code ActivityDefinition} and {@code StructureDefinition} files. Discovery is performed via
 * {@link LintingUtils#getProjectRoot(java.nio.file.Path)} in the following order:</p>
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
 * <p>All validation outcomes are reported as instances of {@link FhirElementLintItem} with different
 * {@link LintingType} values. The following error types may be reported:</p>
 * <ul>
 *   <li>{@link LintingType#FHIR_TASK_MISSING_PROFILE} – missing {@code meta.profile}</li>
 *   <li>{@link LintingType#FHIR_TASK_MISSING_INSTANTIATES_CANONICAL} – missing {@code instantiatesCanonical}</li>
 *   <li>{@link LintingType#FHIR_TASK_INSTANTIATES_CANONICAL_PLACEHOLDER} – {@code instantiatesCanonical} placeholder issue</li>
 *   <li>{@link LintingType#FHIR_TASK_UNKNOWN_INSTANTIATES_CANONICAL} – referenced ActivityDefinition not found</li>
 *   <li>{@link LintingType#FHIR_TASK_MISSING_STATUS} – missing {@code status} element</li>
 *   <li>{@link LintingType#FHIR_TASK_STATUS_NOT_DRAFT} – {@code status} is not {@code "draft"}</li>
 *   <li>{@link LintingType#FHIR_TASK_UNKNOWN_STATUS} – unknown {@code status} value</li>
 *   <li>{@link LintingType#FHIR_TASK_VALUE_IS_NOT_SET_AS_ORDER} – {@code intent} is not {@code "order"}</li>
 *   <li>{@link LintingType#FHIR_TASK_MISSING_REQUESTER} – missing {@code requester} element</li>
 *   <li>{@link LintingType#FHIR_TASK_INVALID_REQUESTER} – invalid {@code requester.identifier.system}</li>
 *   <li>{@link LintingType#FHIR_TASK_REQUESTER_ID_NOT_EXIST} – missing {@code requester.identifier.value}</li>
 *   <li>{@link LintingType#FHIR_TASK_REQUESTER_ID_NO_PLACEHOLDER} – requester ID missing placeholder</li>
 *   <li>{@link LintingType#FHIR_TASK_REQUESTER_ORGANIZATION_NO_PLACEHOLDER} – requester organization placeholder issue</li>
 *   <li>{@link LintingType#FHIR_TASK_MISSING_RECIPIENT} – missing {@code restriction.recipient} element</li>
 *   <li>{@link LintingType#FHIR_TASK_INVALID_RECIPIENT} – invalid {@code restriction.recipient.identifier.system}</li>
 *   <li>{@link LintingType#FHIR_TASK_RECIPIENT_ID_NOT_EXIST} – missing {@code restriction.recipient.identifier.value}</li>
 *   <li>{@link LintingType#FHIR_TASK_RECIPIENT_ID_NO_PLACEHOLDER} – recipient ID missing placeholder</li>
 *   <li>{@link LintingType#FHIR_TASK_RECIPIENT_ORGANIZATION_NO_PLACEHOLDER} – recipient organization placeholder issue</li>
 *   <li>{@link LintingType#FHIR_TASK_DATE_NO_PLACEHOLDER} – {@code authoredOn} missing {@code #{date}} placeholder</li>
 *   <li>{@link LintingType#FHIR_TASK_MISSING_INPUT} – no {@code Task.input} elements present</li>
 *   <li>{@link LintingType#FHIR_TASK_INPUT_REQUIRED_CODING_SYSTEM_AND_CODING_CODE} – input missing system or code</li>
 *   <li>{@link LintingType#FHIR_TASK_INPUT_MISSING_VALUE} – input missing {@code value[x]}</li>
 *   <li>{@link LintingType#FHIR_TASK_INPUT_DUPLICATE_SLICE} – duplicate {@code system#code} in {@code Task.input}</li>
 *   <li>{@link LintingType#FHIR_TASK_REQUIRED_INPUT_WITH_CODE_MESSAGE_NAME} – missing mandatory {@code message-name} input</li>
 *   <li>{@link LintingType#FHIR_TASK_STATUS_REQUIRED_INPUT_BUSINESS_KEY} – {@code business-key} required but missing</li>
 *   <li>{@link LintingType#FHIR_TASK_BUSINESS_KEY_EXISTS} – {@code business-key} present when status is {@code "draft"}</li>
 *   <li>{@link LintingType#FHIR_TASK_BUSINESS_KEY_CHECK_IS_SKIPPED} – business-key validation skipped (informational)</li>
 *   <li>{@link LintingType#FHIR_TASK_CORRELATION_EXISTS} – {@code correlation-key} present but not allowed</li>
 *   <li>{@link LintingType#FHIR_TASK_CORRELATION_MISSING_BUT_REQUIRED} – {@code correlation-key} required but missing</li>
 *   <li>{@link LintingType#FHIR_TASK_INPUT_INSTANCE_COUNT_BELOW_MIN} – too few {@code Task.input} elements</li>
 *   <li>{@link LintingType#FHIR_TASK_INPUT_INSTANCE_COUNT_EXCEEDS_MAX} – too many {@code Task.input} elements</li>
 *   <li>{@link LintingType#FHIR_TASK_INPUT_SLICE_COUNT_BELOW_SLICE_MIN} – slice occurrence below minimum</li>
 *   <li>{@link LintingType#FHIR_TASK_INPUT_SLICE_COUNT_EXCEEDS_SLICE_MAX} – slice occurrence exceeds maximum</li>
 *   <li>{@link LintingType#FHIR_TASK_UNKNOWN_CODE} – unknown terminology code</li>
 *   <li>{@link LintingType#FHIR_TASK_COULD_NOT_LOAD_PROFILE} – StructureDefinition could not be loaded (warning)</li>
 * </ul>
 * <p>Successful validations are reported with {@link LinterSeverity#INFO} for completeness and traceability.</p>
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
 * @see AbstractFhirInstanceLinter
 * @see FhirResourceLocator
 * @see FhirResourceParser
 * @see FhirAuthorizationCache
 * @see FhirElementLintItem
 * @see LintingType
 * @see LinterSeverity
 * @see LintingUtils#getProjectRoot(java.nio.file.Path)
 * @see <a href="https://hl7.org/fhir/profiling.html#slice-cardinality">FHIR Slicing and Cardinality Rules</a>
 * @see <a href="https://dsf.dev/intro/info/process-plugin/starting-and-naming-processes/">DSF Process Plugin Documentation</a>
 *
 * @author DSF Development Team
 * @since 1.0
 */
public final class FhirTaskLinter extends AbstractFhirInstanceLinter {
    private static final String TASK_XP = "/*[local-name()='Task']";
    private static final String INPUT_XP = TASK_XP + "/*[local-name()='input']";
    private static final String CODING_SYS_XP = "./*[local-name()='type']/*[local-name()='coding']/*[local-name()='system']/@value";
    private static final String CODING_CODE_XP = "./*[local-name()='type']/*[local-name()='coding']/*[local-name()='code']/@value";
    private static final String SYSTEM_BPMN_MSG = "http://dsf.dev/fhir/CodeSystem/bpmn-message";
    private static final String SYSTEM_ORG_ID = "http://dsf.dev/sid/organization-identifier";
    private static final Set<String> STATUSES_NEED_BIZKEY = Set.of("in-progress", "completed", "failed");

    @Override
    public boolean canLint(Document d) {
        return "Task".equals(d.getDocumentElement().getLocalName());
    }

    @Override
    public List<FhirElementLintItem> lint(Document doc, File resFile) {
        final String ref = computeReference(doc, resFile);
        final List<FhirElementLintItem> issues = new ArrayList<>();

        checkMetaAndBasic(doc, resFile, ref, issues);
        checkPlaceholders(doc, resFile, ref, issues);
        lintInputs(doc, resFile, ref, issues);
        lintTerminology(doc, resFile, ref, issues);
        lintRequesterAuthorization(doc, resFile, ref, issues);
        lintRecipientAuthorization(doc, resFile, ref, issues);

        return issues;
    }

    private void checkMetaAndBasic(Document doc, File f, String ref, List<FhirElementLintItem> out) {
        NodeList prof = xp(doc, TASK_XP + "/*[local-name()='meta']/*[local-name()='profile']/@value");
        if (prof == null || prof.getLength() == 0)
            out.add(FhirElementLintItem.of(LinterSeverity.ERROR, LintingType.FHIR_TASK_MISSING_PROFILE, f, ref));
        else
            out.add(ok(f, ref, "meta.profile present."));

        String instCanon = val(doc, TASK_XP + "/*[local-name()='instantiatesCanonical']/@value");
        if (blank(instCanon))
            out.add(FhirElementLintItem.of(LinterSeverity.ERROR, LintingType.FHIR_TASK_MISSING_INSTANTIATES_CANONICAL, f, ref));
        else {
            out.add(ok(f, ref, "instantiatesCanonical found."));

            int pipe = instCanon.lastIndexOf('|');
            String versionPart = pipe >= 0 ? instCanon.substring(pipe + 1) : "";

            if ("#{version}".equals(versionPart) && instCanon.endsWith("#{version}")) {
                out.add(ok(f, ref, "instantiatesCanonical ends with '|#{version}' as expected."));
            } else {
                out.add(new FhirElementLintItem(LinterSeverity.WARN, LintingType.FHIR_TASK_INSTANTIATES_CANONICAL_PLACEHOLDER, f, ref,
                        "instantiatesCanonical must end with '|#{version}', got: '" + instCanon + "'"));
            }

            File root = determineProjectRoot(f);
            FhirResourceLocator locator = FhirResourceLocator.create(root);
            boolean exists = locator.activityDefinitionExistsForInstantiatesCanonical(instCanon, root);
            if (!exists)
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_UNKNOWN_INSTANTIATES_CANONICAL, f, ref,
                        "No ActivityDefinition '" + instCanon + "' under '" + f.getName() + "'."));
            else
                out.add(ok(f, ref, "ActivityDefinition exists."));
        }

        String status = val(doc, TASK_XP + "/*[local-name()='status']/@value");
        if (blank(status))
            out.add(FhirElementLintItem.of(LinterSeverity.ERROR, LintingType.FHIR_TASK_MISSING_STATUS, f, ref));
        else if (!"draft".equals(status))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_STATUS_NOT_DRAFT, f, ref,
                    "status must be 'draft' (found '" + status + "')"));
        else
            out.add(ok(f, ref, "status = 'draft'"));

        String intent = val(doc, TASK_XP + "/*[local-name()='intent']/@value");
        if (!"order".equals(intent))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_VALUE_IS_NOT_SET_AS_ORDER, f, ref,
                    "intent must be 'order' (found '" + intent + "')"));
        else
            out.add(ok(f, ref, "intent = order"));

        String reqSys = val(doc, TASK_XP + "/*[local-name()='requester']/*[local-name()='identifier']/*[local-name()='system']/@value");
        if (blank(reqSys))
            out.add(FhirElementLintItem.of(LinterSeverity.ERROR, LintingType.FHIR_TASK_MISSING_REQUESTER, f, ref));
        else if (!SYSTEM_ORG_ID.equals(reqSys))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_INVALID_REQUESTER, f, ref,
                    "requester.identifier.system must be '" + SYSTEM_ORG_ID + "'"));
        else
            out.add(ok(f, ref, "requester.identifier.system OK"));

        String recSys = val(doc, TASK_XP + "/*[local-name()='restriction']/*[local-name()='recipient']/*[local-name()='identifier']/*[local-name()='system']/@value");
        if (blank(recSys))
            out.add(FhirElementLintItem.of(LinterSeverity.ERROR, LintingType.FHIR_TASK_MISSING_RECIPIENT, f, ref));
        else if (!SYSTEM_ORG_ID.equals(recSys))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_INVALID_RECIPIENT, f, ref,
                    "restriction.recipient.identifier.system must be '" + SYSTEM_ORG_ID + "'"));
        else
            out.add(ok(f, ref, "restriction.recipient.identifier.system OK"));
    }

    private void checkPlaceholders(Document doc, File f, String ref, List<FhirElementLintItem> out) {
        String authoredOn = val(doc, TASK_XP + "/*[local-name()='authoredOn']/@value");
        if (authoredOn != null && !authoredOn.contains("#{date}"))
            out.add(new FhirElementLintItem(LinterSeverity.WARN, LintingType.FHIR_TASK_DATE_NO_PLACEHOLDER, f, ref,
                    "<authoredOn> must contain '#{date}'."));
        else
            out.add(ok(f, ref, "<authoredOn> placeholder OK."));

        String reqIdVal = val(doc, TASK_XP + "/*[local-name()='requester']/*[local-name()='identifier']/*[local-name()='value']/@value");
        if (reqIdVal == null || !reqIdVal.equals("#{organization}"))
            out.add(new FhirElementLintItem(LinterSeverity.WARN, LintingType.FHIR_TASK_REQUESTER_ORGANIZATION_NO_PLACEHOLDER, f, ref,
                    "requester.identifier.value must contain '#{organization}'."));
        else
            out.add(ok(f, ref, "requester.identifier.value placeholder OK."));

        String recIdVal = val(doc, TASK_XP + "/*[local-name()='restriction']/*[local-name()='recipient']/*[local-name()='identifier']/*[local-name()='value']/@value");
        if (recIdVal == null || !recIdVal.equals("#{organization}"))
            out.add(new FhirElementLintItem(LinterSeverity.WARN, LintingType.FHIR_TASK_RECIPIENT_ORGANIZATION_NO_PLACEHOLDER, f, ref,
                    "restriction.recipient.identifier.value must contain '#{organization}'."));
        else
            out.add(ok(f, ref, "restriction.recipient.identifier.value placeholder OK."));
    }

    private void lintInputs(Document doc, File f, String ref, List<FhirElementLintItem> out) {
        String profileUrl = val(doc, TASK_XP + "/*[local-name()='meta']/*[local-name()='profile']/@value");
        Map<String, SliceCard> cards = loadInputCardinality(determineProjectRoot(f), profileUrl);

        if (cards == null) {
            out.add(new FhirElementLintItem(LinterSeverity.WARN, LintingType.FHIR_TASK_COULD_NOT_LOAD_PROFILE, f, ref,
                    "StructureDefinition for profile '" + profileUrl + "' not found → cardinality check skipped."));
        }

        NodeList ins = xp(doc, INPUT_XP);
        if (ins == null || ins.getLength() == 0) {
            out.add(FhirElementLintItem.of(LinterSeverity.ERROR, LintingType.FHIR_TASK_MISSING_INPUT, f, ref));
            return;
        }

        boolean messageName = false, businessKey = false, correlation = false;
        Map<String, Integer> duplicates = new HashMap<>();
        Map<String, Integer> sliceCounter = new HashMap<>();
        int inputCount = 0;

        for (int i = 0; i < ins.getLength(); i++) {
            Node in = ins.item(i);
            String sys = val(in, CODING_SYS_XP);
            String code = val(in, CODING_CODE_XP);
            String v = extractValueX(in);
            inputCount++;

            if (!blank(sys) && !blank(code))
                duplicates.merge(sys + "#" + code, 1, Integer::sum);
            if (!blank(code))
                sliceCounter.merge(code, 1, Integer::sum);

            if (blank(sys) || blank(code)) {
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_INPUT_REQUIRED_CODING_SYSTEM_AND_CODING_CODE, f, ref,
                        "Task.input without system/code"));
                continue;
            }
            out.add(ok(f, ref, "Task.input has required system and code: " + sys + "#" + code));

            if (blank(v))
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_INPUT_MISSING_VALUE, f, ref,
                        "Task.input(" + code + ") missing value[x]"));
            else
                out.add(ok(f, ref, "input '" + code + "' value='" + v + "'"));

            if (SYSTEM_BPMN_MSG.equals(sys)) {
                switch (code) {
                    case "message-name" -> messageName = true;
                    case "business-key" -> businessKey = true;
                    case "correlation-key" -> correlation = true;
                }
            }
        }

        duplicates.forEach((k, v) -> {
            if (v > 1)
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_INPUT_DUPLICATE_SLICE, f, ref,
                        "Duplicate slice '" + k + "' (" + v + "×)"));
        });
        if (duplicates.values().stream().allMatch(count -> count == 1))
            out.add(ok(f, ref, "No duplicate Task.input slices detected"));

        if (messageName)
            out.add(ok(f, ref, "mandatory slice 'message-name' present"));
        else
            out.add(FhirElementLintItem.of(LinterSeverity.ERROR, LintingType.FHIR_TASK_REQUIRED_INPUT_WITH_CODE_MESSAGE_NAME, f, ref));

        String status = val(doc, TASK_XP + "/*[local-name()='status']/@value");
        boolean statusIsDraft = "draft".equals(status);

        if (FhirAuthorizationCache.isUnknown(FhirAuthorizationCache.CS_TASK_STATUS, status))
            out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_UNKNOWN_STATUS, f, ref, "Unknown status: " + status));
        else
            out.add(ok(f, ref, "Task status '" + status + "' is valid"));

        if (STATUSES_NEED_BIZKEY.contains(status)) {
            if (!businessKey)
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_STATUS_REQUIRED_INPUT_BUSINESS_KEY, f, ref,
                        "status='" + status + "' needs business-key"));
            else
                out.add(ok(f, ref, "status='" + status + "' → business-key present"));
        } else if (statusIsDraft) {
            if (businessKey)
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_BUSINESS_KEY_EXISTS, f, ref,
                        "businessKey must not be present when status is 'draft'"));
            else
                out.add(ok(f, ref, "status=draft → business-key correctly absent"));
        } else {
            out.add(new FhirElementLintItem(LinterSeverity.INFO, LintingType.FHIR_TASK_BUSINESS_KEY_CHECK_IS_SKIPPED, f, ref,
                    "Business key linting skipped for status '" + status + "'"));
        }

        boolean corrAllowed = isCorrelationAllowed(cards);
        if (correlation) {
            if (corrAllowed)
                out.add(ok(f, ref, "correlation input present and permitted"));
            else
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_CORRELATION_EXISTS, f, ref,
                        "correlation input not allowed by StructureDefinition"));
        } else {
            SliceCard corrCard = (cards != null) ? cards.get("correlation-key") : null;
            if (corrCard != null && corrCard.min() > 0)
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_CORRELATION_MISSING_BUT_REQUIRED, f, ref,
                        "correlation input missing but slice min=" + corrCard.min()));
            else
                out.add(ok(f, ref, "correlation input absent as expected"));
        }

        if (cards != null) {
            SliceCard baseCard = cards.get("__BASE__");
            if (inputCount < baseCard.min())
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_INPUT_INSTANCE_COUNT_BELOW_MIN, f, ref,
                        "Task.input count " + inputCount + " below min " + baseCard.min()));
            else if (inputCount > baseCard.max())
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_INPUT_INSTANCE_COUNT_EXCEEDS_MAX, f, ref,
                        "Task.input count " + inputCount + " exceeds max " + baseCard.max()));
            else
                out.add(ok(f, ref, "Task.input count " + inputCount + " OK"));

            cards.forEach((code, card) -> {
                if ("__BASE__".equals(code)) return;
                int cnt = sliceCounter.getOrDefault(code, 0);
                if (cnt < card.min())
                    out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_INPUT_SLICE_COUNT_BELOW_SLICE_MIN, f, ref,
                            "slice '" + code + "' count " + cnt + " below min " + card.min()));
                else if (cnt > card.max())
                    out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_INPUT_SLICE_COUNT_EXCEEDS_SLICE_MAX, f, ref,
                            "slice '" + code + "' count " + cnt + " exceeds max " + card.max()));
                else
                    out.add(ok(f, ref, "slice '" + code + "' count " + cnt + " OK"));
            });
        }
    }

    private void lintTerminology(Document doc, File f, String ref, List<FhirElementLintItem> out) {
        NodeList codings = xp(doc, "//coding");
        if (codings == null) return;
        for (int i = 0; i < codings.getLength(); i++) {
            Node c = codings.item(i);
            String sys = val(c, "./*[local-name()='system']/@value");
            String code = val(c, "./*[local-name()='code']/@value");
            if (FhirAuthorizationCache.isUnknown(sys, code))
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_UNKNOWN_CODE, f, ref,
                        "Unknown code '" + code + "' in '" + sys + "'"));
        }
    }

    private String computeReference(Document doc, File file) {
        String canon = val(doc, TASK_XP + "/*[local-name()='instantiatesCanonical']/@value");
        if (!blank(canon)) return canon.split("\\|")[0];
        String idVal = val(doc, TASK_XP + "/*[local-name()='identifier']/*[local-name()='value']/@value");
        return !blank(idVal) ? idVal : file.getName();
    }

    private File determineProjectRoot(File res) {
        return LintingUtils.getProjectRoot(res.toPath());
    }

    private void lintRequesterAuthorization(Document taskDoc, File taskFile, String ref, List<FhirElementLintItem> out) {
        if (instCanonDetermine(taskDoc, taskFile)) return;
        String requesterId = val(taskDoc, TASK_XP + "/*[local-name()='requester']/*[local-name()='identifier']/*[local-name()='value']/@value");
        if (requesterId == null || requesterId.isBlank())
            out.add(FhirElementLintItem.of(LinterSeverity.ERROR, LintingType.FHIR_TASK_REQUESTER_ID_NOT_EXIST, taskFile, ref));
        else if (!"#{organization}".equals(requesterId))
            out.add(FhirElementLintItem.of(LinterSeverity.WARN, LintingType.FHIR_TASK_REQUESTER_ID_NO_PLACEHOLDER, taskFile, ref));
        else
            out.add(ok(taskFile, ref, "Task.requester.identifier.value contains '#{organization}' placeholder."));
    }

    private void lintRecipientAuthorization(Document taskDoc, File taskFile, String ref, List<FhirElementLintItem> out) {
        if (instCanonDetermine(taskDoc, taskFile)) return;
        String recipientId = val(taskDoc, TASK_XP + "/*[local-name()='restriction']/*[local-name()='recipient']/*[local-name()='identifier']/*[local-name()='value']/@value");
        if (recipientId == null || recipientId.isBlank())
            out.add(FhirElementLintItem.of(LinterSeverity.ERROR, LintingType.FHIR_TASK_RECIPIENT_ID_NOT_EXIST, taskFile, ref));
        else if (!"#{organization}".equals(recipientId))
            out.add(FhirElementLintItem.of(LinterSeverity.WARN, LintingType.FHIR_TASK_RECIPIENT_ID_NO_PLACEHOLDER, taskFile, ref));
        else
            out.add(ok(taskFile, ref, "Task.restriction.recipient.identifier.value contains '#{organization}' placeholder."));
    }

    private boolean instCanonDetermine(Document taskDoc, File taskFile) {
        String instCanon = val(taskDoc, TASK_XP + "/*[local-name()='instantiatesCanonical']/@value");
        if (blank(instCanon)) return true;
        File projectRoot = determineProjectRoot(taskFile);
        FhirResourceLocator locator = FhirResourceLocator.create(projectRoot);
        File actFile = locator.findActivityDefinitionForInstantiatesCanonical(instCanon, projectRoot);
        return actFile == null;
    }

    private record SliceCard(int min, int max) {}

    private Map<String, SliceCard> loadInputCardinality(File projectRoot, String profileUrl) {
        FhirResourceLocator locator = FhirResourceLocator.create(projectRoot);
        File sdFile = locator.findStructureDefinitionFile(profileUrl, projectRoot);
        if (sdFile == null) return null;
        try {
            Document sd;
            try { sd = FhirResourceParser.parseXml(sdFile.toPath()); }
            catch (Exception e) { sd = FhirResourceParser.parseJsonToXml(sdFile.toPath()); }

            Map<String, SliceCard> map = new HashMap<>();
            String minBase = AbstractFhirInstanceLinter.extractSingleNodeValue(sd, "//*[local-name()='element' and @id='Task.input']/*[local-name()='min']/@value");
            String maxBase = AbstractFhirInstanceLinter.extractSingleNodeValue(sd, "//*[local-name()='element' and @id='Task.input']/*[local-name()='max']/@value");
            int baseMin = (minBase != null) ? Integer.parseInt(minBase) : 0;
            int baseMax = (maxBase == null || "*".equals(maxBase)) ? Integer.MAX_VALUE : Integer.parseInt(maxBase);
            map.put("__BASE__", new SliceCard(baseMin, baseMax));

            NodeList slices = (NodeList) XPathFactory.newInstance().newXPath()
                    .compile("//*[local-name()='element' and starts-with(@id,'Task.input:') and not(contains(@id,'.'))]")
                    .evaluate(sd, XPathConstants.NODESET);
            for (int i = 0; i < slices.getLength(); i++) {
                Node n = slices.item(i);
                String sliceName = n.getAttributes().getNamedItem("id").getNodeValue().substring("Task.input:".length());
                String mi = AbstractFhirInstanceLinter.extractSingleNodeValue(n, "./*[local-name()='min']/@value");
                String ma = AbstractFhirInstanceLinter.extractSingleNodeValue(n, "./*[local-name()='max']/@value");
                int sMin = (mi != null) ? Integer.parseInt(mi) : 0;
                int sMax = (ma == null || "*".equals(ma)) ? baseMax : Integer.parseInt(ma);
                map.put(sliceName, new SliceCard(sMin, sMax));
            }
            return map;
        } catch (Exception e) { return null; }
    }

    private boolean isCorrelationAllowed(Map<String, SliceCard> cards) {
        if (cards == null) return false;
        SliceCard c = cards.get("correlation-key");
        return c != null && c.max() != 0;
    }
}
