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
 *   <li>{@link LintingType#FHIR_TASK_UNKNOWN_CODE} – unknown terminology code (non-input codings)</li>
 *   <li>{@link LintingType#FHIR_TASK_INPUT_CODING_SYSTEM_UNKNOWN} – {@code Task.input.type.coding.system} not a known CodeSystem URI</li>
 *   <li>{@link LintingType#FHIR_TASK_INPUT_CODING_SYSTEM_NOT_IN_VALUE_SET} – {@code Task.input.type.coding.system} not allowed by the expected ValueSet binding context</li>
 *   <li>{@link LintingType#FHIR_TASK_INPUT_CODING_CODE_UNKNOWN_FOR_SYSTEM} – {@code Task.input.type.coding.code} unknown in the specified CodeSystem</li>
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
    private static final String SYSTEM_BPMN_MSG = FhirAuthorizationCache.CS_BPMN_MESSAGE;
    private static final String SYSTEM_ORG_ID = "http://dsf.dev/sid/organization-identifier";
    private static final String TASK_IDENTIFIER_SID = "http://dsf.dev/sid/task-identifier";
    private static final Set<String> STATUSES_NEED_BIZKEY = Set.of("in-progress", "completed", "failed");

    /**
     * Pattern for validating Task Identifier format.
     * <p>
     * According to DSF NamingSystem definition, the identifier value must be in the form:
     * {@code {process-url}/{process-version}/{task-example-name}}
     * <p>
     * Example: {@code http://test.org/bpe/Process/someProcessName/1.0/someExampleName}
     * <p>
     * The pattern accepts both actual version numbers ({@code /\d+\.\d+/}) and placeholders
     * ({@code /#{version}/}) for development-time validation.
     *
     * @see <a href="https://github.com/datasharingframework/dsf">DSF Framework</a>
     */
    private static final String TASK_IDENTIFIER_PATTERN_STRING =
            "^https?://[^/]+/bpe/Process/[a-zA-Z0-9-]+/(?:\\d+\\.\\d+|#\\{version\\})/.+$";
    private static final java.util.regex.Pattern TASK_IDENTIFIER_PATTERN =
            java.util.regex.Pattern.compile(TASK_IDENTIFIER_PATTERN_STRING);

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
        lintTaskIdentifier(doc, resFile, ref, issues);

        // Load slice metadata once and reuse for structural + terminology checks
        String profileUrl = val(doc, TASK_XP + "/*[local-name()='meta']/*[local-name()='profile']/@value");
        Map<String, SliceCard> cards = loadInputCardinality(determineProjectRoot(resFile), profileUrl);

        lintInputs(doc, resFile, ref, issues, cards);
        lintTerminology(doc, resFile, ref, issues);
        lintInputTypeCodingTerminology(doc, resFile, ref, issues, cards);
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

    /**
     * Validates Task identifier with system 'http://dsf.dev/sid/task-identifier'.
     *
     * <p>
     * According to DSF NamingSystem definition, the identifier value must be in the form:
     * {@code {process-url}/{process-version}/{task-example-name}}
     * e.g., {@code http://test.org/bpe/Process/someProcessName/1.0/someExampleName}
     * </p>
     *
     * <p>
     * Additionally validates that the identifier system is correctly set to
     * {@code http://dsf.dev/sid/task-identifier}.
     * </p>
     *
     * @see <a href="https://github.com/datasharingframework/dsf">DSF Framework</a>
     */
    private void lintTaskIdentifier(Document doc, File f, String ref, List<FhirElementLintItem> out) {
        NodeList identifiers = xp(doc, TASK_XP + "/*[local-name()='identifier']");
        if (identifiers == null || identifiers.getLength() == 0) {
            return; // No identifiers present, nothing to validate
        }

        for (int i = 0; i < identifiers.getLength(); i++) {
            Node identifier = identifiers.item(i);
            String system = val(identifier, "./*[local-name()='system']/@value");
            String value = val(identifier, "./*[local-name()='value']/@value");

            // Check if system is missing or empty
            if (blank(system)) {
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_IDENTIFIER_MISSING_SYSTEM, f, ref,
                        "Task identifier is missing system element. Expected system: '" + TASK_IDENTIFIER_SID + "'"));
                continue;
            }

            // Check if system matches the expected DSF task identifier SID
            if (TASK_IDENTIFIER_SID.equals(system)) {
                out.add(ok(f, ref, "Task identifier system is correct: " + system));

                // Validate the identifier value format
                if (blank(value)) {
                    out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_IDENTIFIER_INVALID_FORMAT, f, ref,
                            "Task identifier with system '" + TASK_IDENTIFIER_SID + "' has empty value."));
                } else if (!TASK_IDENTIFIER_PATTERN.matcher(value).matches()) {
                    out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_IDENTIFIER_INVALID_FORMAT, f, ref,
                            String.format("Task identifier value '%s' does not match required format: " +
                                    "{process-url}/{process-version}/{task-example-name} " +
                                    "(e.g., http://test.org/bpe/Process/someProcessName/1.0/someExampleName)", value)));
                } else {
                    out.add(ok(f, ref, "Task identifier format is valid: " + value));
                }
            } else {
                // System is set but does not match expected DSF task identifier SID
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_IDENTIFIER_INVALID_SYSTEM, f, ref,
                        String.format("Task identifier has invalid system '%s'. Expected: '%s'", system, TASK_IDENTIFIER_SID)));
            }
        }
    }

    private void lintInputs(Document doc, File f, String ref, List<FhirElementLintItem> out, Map<String, SliceCard> cards) {
        if (cards == null) {
            String profileUrl = val(doc, TASK_XP + "/*[local-name()='meta']/*[local-name()='profile']/@value");
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

    /**
     * Generic terminology check for all {@code coding} nodes in the Task document
     * <em>except</em> {@code Task.input.type.coding} entries, which are validated
     * separately with granular error types in {@link #lintInputTypeCodingTerminology}.
     */
    private void lintTerminology(Document doc, File f, String ref, List<FhirElementLintItem> out) {
        NodeList codings = xp(doc, "//coding");
        if (codings == null) return;
        for (int i = 0; i < codings.getLength(); i++) {
            Node c = codings.item(i);
            // Skip Task.input.type.coding — handled by lintInputTypeCodingTerminology
            Node parent = c.getParentNode();
            if (parent != null && "type".equals(parent.getLocalName())) {
                Node grandParent = parent.getParentNode();
                if (grandParent != null && "input".equals(grandParent.getLocalName()))
                    continue;
            }
            String sys = val(c, "./*[local-name()='system']/@value");
            String code = val(c, "./*[local-name()='code']/@value");
            if (FhirAuthorizationCache.isUnknown(sys, code))
                out.add(new FhirElementLintItem(LinterSeverity.ERROR, LintingType.FHIR_TASK_UNKNOWN_CODE, f, ref,
                        "Unknown code '" + code + "' in '" + sys + "'"));
        }
    }

    /**
     * Granular terminology validation specifically for {@code Task.input.type.coding} entries.
     *
     * <p>Three distinct checks are performed per input. Later checks depend on earlier ones:</p>
     * <ol>
     *   <li><strong>System known</strong> – {@code coding.system} must be registered in
     *       {@link FhirAuthorizationCache} (i.e., correspond to a loaded CodeSystem resource).
     *       If unknown, {@link LintingType#FHIR_TASK_INPUT_CODING_SYSTEM_UNKNOWN} is emitted
     *       and further checks for that input are skipped.</li>
     *   <li><strong>System in expected ValueSet context</strong> – driven by the profile's
     *       StructureDefinition:
     *       <ul>
     *         <li>If the matching slice declares a {@code fixedUri} at
     *             {@code Task.input:sliceName.type.coding.system}, the input's system must
     *             equal it literally.</li>
     *         <li>Else, if the slice declares a {@code binding.valueSet} and that ValueSet is
     *             loaded, the input's system must appear in that ValueSet's
     *             {@code compose.include.system}.</li>
     *         <li>Else, if the binding context cannot be resolved, validation fails explicitly
     *             (no permissive fallback to unrelated ValueSets).</li>
     *       </ul>
     *       On mismatch, {@link LintingType#FHIR_TASK_INPUT_CODING_SYSTEM_NOT_IN_VALUE_SET} is emitted.</li>
     *   <li><strong>Code valid for system</strong> – {@code coding.code} must be a known code
     *       under the given {@code coding.system}. <em>Only executed if Check 2 passed.</em>
     *       If not, {@link LintingType#FHIR_TASK_INPUT_CODING_CODE_UNKNOWN_FOR_SYSTEM} is emitted.</li>
     * </ol>
     *
     * <p>Inputs that already failed structural validation (missing system or code) in
     * {@link #lintInputs} are skipped here to avoid duplicate reporting.</p>
     *
     * @param cards per-slice cardinality and binding metadata from the StructureDefinition
     *              (may be {@code null} if the profile could not be loaded)
     */
    private void lintInputTypeCodingTerminology(Document doc, File f, String ref,
                                                List<FhirElementLintItem> out,
                                                Map<String, SliceCard> cards) {
        NodeList inputs = xp(doc, INPUT_XP);
        if (inputs == null || inputs.getLength() == 0) return;

        for (int i = 0; i < inputs.getLength(); i++) {
            Node in = inputs.item(i);
            String sys  = val(in, CODING_SYS_XP);
            String code = val(in, CODING_CODE_XP);

            // Structural errors (missing system/code) are already reported by lintInputs
            if (blank(sys) || blank(code)) continue;

            // Check 1: coding.system must be a known CodeSystem
            if (!FhirAuthorizationCache.containsSystem(sys)) {
                out.add(new FhirElementLintItem(LinterSeverity.ERROR,
                        LintingType.FHIR_TASK_INPUT_CODING_SYSTEM_UNKNOWN, f, ref,
                        "Task.input.type.coding.system '" + sys + "' is not a known CodeSystem URI."));
                continue; // checks 2 and 3 require the system to be known
            }

            // Check 2: coding.system must match the expected ValueSet context for this slice
            SliceCard slice = findSliceByCode(cards, code);
            if (!isSystemAllowedByBinding(slice, sys, out, f, ref)) {
                // Check 3 is only executed when Check 2 passed.
                continue;
            }

            // Check 3: coding.code must be a valid code in the given system
            if (FhirAuthorizationCache.isUnknown(sys, code)) {
                out.add(new FhirElementLintItem(LinterSeverity.ERROR,
                        LintingType.FHIR_TASK_INPUT_CODING_CODE_UNKNOWN_FOR_SYSTEM, f, ref,
                        "Task.input.type.coding.code '" + code + "' is unknown in CodeSystem '" + sys + "'."));
                continue;
            }

            out.add(ok(f, ref, "Task.input.type.coding: system='" + sys + "' code='" + code + "' OK."));
        }
    }

    /**
     * Locates the slice metadata that matches the given input code.
     *
     * <p>Match strategy (first hit wins):</p>
     * <ol>
     *   <li>Slice whose {@code fixedCode} at
     *       {@code Task.input:sliceName.type.coding.code} equals {@code inputCode}.</li>
     *   <li>Slice whose map key (slice name) equals {@code inputCode} - DSF convention
     *       where slice names mirror the code value (e.g., {@code message-name}).</li>
     * </ol>
     *
     * @param cards map of slice metadata loaded from the StructureDefinition, may be {@code null}
     * @param inputCode value of {@code Task.input.type.coding.code} for the current input
     * @return the matching {@link SliceCard}, or {@code null} if no slice matches
     */
    private SliceCard findSliceByCode(Map<String, SliceCard> cards, String inputCode) {
        if (cards == null || inputCode == null) return null;
        for (Map.Entry<String, SliceCard> e : cards.entrySet()) {
            if ("__BASE__".equals(e.getKey())) continue;
            SliceCard c = e.getValue();
            if (inputCode.equals(c.fixedCode())) return c;
        }
        SliceCard byName = cards.get(inputCode);
        return (byName != null && !"__BASE__".equals(inputCode)) ? byName : null;
    }

    /**
     * Binding-driven evaluation of Check 2.
     *
     * <p>The decision order reflects how tightly the profile constrains the system:</p>
     * <ol>
     *   <li><strong>fixedUri</strong> on {@code .type.coding.system} - strict literal comparison.</li>
     *   <li><strong>binding.valueSet</strong> resolvable in the cache - input system must be
     *       listed in the ValueSet's {@code compose.include.system}.</li>
     *   <li><strong>binding.valueSet</strong> declared but not loaded - explicit validation
     *       failure because the expected context cannot be resolved.</li>
     *   <li>No binding info available - explicit validation failure because no expected
     *       ValueSet context is available.</li>
     * </ol>
     *
     * <p>On mismatch, a {@link LintingType#FHIR_TASK_INPUT_CODING_SYSTEM_NOT_IN_VALUE_SET}
     * error is appended to {@code out} and {@code false} is returned.</p>
     *
     * @return {@code true} if the system is accepted by the resolved binding context,
     *         {@code false} otherwise
     */
    private boolean isSystemAllowedByBinding(SliceCard slice, String sys,
                                             List<FhirElementLintItem> out, File f, String ref) {
        // 1. Strict fixedUri match on Task.input:slice.type.coding.system
        if (slice != null && slice.fixedSystem() != null && !slice.fixedSystem().isBlank()) {
            if (sys.equals(slice.fixedSystem())) return true;
            out.add(new FhirElementLintItem(LinterSeverity.ERROR,
                    LintingType.FHIR_TASK_INPUT_CODING_SYSTEM_NOT_IN_VALUE_SET, f, ref,
                    "Task.input.type.coding.system '" + sys +
                    "' does not match the slice's fixedUri '" + slice.fixedSystem() + "'."));
            return false;
        }

        // 2. binding.valueSet declared on the slice
        if (slice != null && slice.bindingValueSet() != null && !slice.bindingValueSet().isBlank()) {
            String vsUrl = slice.bindingValueSet();
            if (FhirAuthorizationCache.isValueSetLoaded(vsUrl)) {
                if (FhirAuthorizationCache.getSystemsInValueSet(vsUrl).contains(sys)) return true;
                out.add(new FhirElementLintItem(LinterSeverity.ERROR,
                        LintingType.FHIR_TASK_INPUT_CODING_SYSTEM_NOT_IN_VALUE_SET, f, ref,
                        "Task.input.type.coding.system '" + sys +
                        "' is not referenced by the bound ValueSet '" + vsUrl + "'."));
                return false;
            }
            out.add(new FhirElementLintItem(LinterSeverity.ERROR,
                    LintingType.FHIR_TASK_INPUT_CODING_SYSTEM_NOT_IN_VALUE_SET, f, ref,
                    "Task.input.type.coding.system '" + sys +
                    "' cannot be validated against binding ValueSet '" + vsUrl +
                    "' because that ValueSet is not loaded."));
            return false;
        }

        out.add(new FhirElementLintItem(LinterSeverity.ERROR,
                LintingType.FHIR_TASK_INPUT_CODING_SYSTEM_NOT_IN_VALUE_SET, f, ref,
                "Task.input.type.coding.system '" + sys +
                "' has no resolvable expected ValueSet context (missing fixedUri and binding.valueSet)."));
        return false;
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

    /**
     * Cardinality and binding metadata for a {@code Task.input} slice extracted from the
     * profile's StructureDefinition.
     *
     * @param min minimum occurrences of the slice ({@code element.min})
     * @param max maximum occurrences of the slice ({@code element.max}; {@code *} maps to {@link Integer#MAX_VALUE})
     * @param fixedSystem value of {@code fixedUri} at
     *                    {@code Task.input:sliceName.type.coding.system}, if present
     * @param fixedCode value of {@code fixedCode} at
     *                  {@code Task.input:sliceName.type.coding.code}, if present
     * @param bindingValueSet canonical URL of the ValueSet bound to the slice's
     *                        {@code Task.input:sliceName.type[.coding]}, if declared
     */
    private record SliceCard(int min, int max,
                             String fixedSystem, String fixedCode,
                             String bindingValueSet) {
        static SliceCard cardinalityOnly(int min, int max) {
            return new SliceCard(min, max, null, null, null);
        }
    }

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
            map.put("__BASE__", SliceCard.cardinalityOnly(baseMin, baseMax));

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

                String codingId = "Task.input:" + sliceName + ".type.coding";
                String typeId = "Task.input:" + sliceName + ".type";
                String codingSystemId = codingId + ".system";
                String codingCodeId = codingId + ".code";

                // fixed constraints on .type.coding.system / .type.coding.code
                String fixedSystem = AbstractFhirInstanceLinter.extractSingleNodeValue(sd,
                        "//*[local-name()='element' and @id='" + codingSystemId + "']" +
                        "/*[local-name()='fixedUri']/@value");
                String fixedCode = AbstractFhirInstanceLinter.extractSingleNodeValue(sd,
                        "//*[local-name()='element' and @id='" + codingCodeId + "']" +
                        "/*[local-name()='fixedCode']/@value");

                // binding.valueSet: prefer .type, fall back to .type.coding
                String binding = AbstractFhirInstanceLinter.extractSingleNodeValue(sd,
                        "//*[local-name()='element' and @id='" + typeId + "']" +
                        "/*[local-name()='binding']/*[local-name()='valueSet']/@value");
                if (binding == null || binding.isBlank()) {
                    binding = AbstractFhirInstanceLinter.extractSingleNodeValue(sd,
                            "//*[local-name()='element' and @id='" + codingId + "']" +
                            "/*[local-name()='binding']/*[local-name()='valueSet']/@value");
                }

                map.put(sliceName, new SliceCard(sMin, sMax, fixedSystem, fixedCode, binding));
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
