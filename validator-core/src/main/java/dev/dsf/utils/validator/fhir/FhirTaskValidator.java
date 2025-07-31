package dev.dsf.utils.validator.fhir;

import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.AbstractFhirInstanceValidator;
import dev.dsf.utils.validator.util.FhirAuthorizationCache;
import dev.dsf.utils.validator.util.FhirValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * <h2>FHIR Task Validator for DSF – Profile: <code>dsf-task-base</code></h2>
 *
 * <p>This class implements a validator for FHIR {@code Task} resources as used in the
 * Digital Sample Framework (DSF). It is responsible for ensuring structural correctness,
 * semantic consistency, and conformance to cardinality rules defined in the DSF base profile
 * <code>http://dsf.dev/fhir/StructureDefinition/dsf-task-base</code>.</p>
 *
 * <p>The validator extends {@link AbstractFhirInstanceValidator} and is automatically invoked
 * during DSF plugin validation, typically from {@link dev.dsf.utils.validator.DsfValidatorImpl}.</p>
 *
 * <h3>Supported Validation Features</h3>
 * <ul>
 *   <li><strong>Meta validation:</strong>
 *     Verifies presence of required meta.profile and instantiatesCanonical elements.</li>
 *   <li><strong>Status and intent checks:</strong>
 *     Validates the {@code status} must be {@code draft}, and {@code intent} must be {@code order}.</li>
 *   <li><strong>Input slice validation:</strong>
 *     Enforces presence and multiplicity rules for {@code Task.input} slices such as:
 *     {@code message-name}, {@code business-key}, and {@code correlation-key}.</li>
 *   <li><strong>Cardinality enforcement:</strong>
 *     Loads {@code min} and {@code max} cardinalities from {@code StructureDefinition} and
 *     validates both the base and sliced input elements. See
 *     <a href="https://hl7.org/fhir/profiling.html#slice-cardinality" target="_blank">
 *     FHIR Profiling Rules §5.1.0.14</a> for background.</li>
 *   <li><strong>Placeholder checks:</strong>
 *     Ensures that elements such as {@code authoredOn} and
 *     {@code requester/restriction.identifier.value} contain expected template variables
 *     (e.g., {@code #{organization}}, {@code #{date}}).</li>
 *   <li><strong>Terminology validation:</strong>
 *     Cross-checks all {@code coding} elements against known value sets and
 *     {@link FhirAuthorizationCache}.</li>
 *   <li><strong>Authorization logic:</strong>
 *     Verifies requester and recipient organizations are authorized for the referenced
 *     {@code ActivityDefinition}.</li>
 * </ul>
 *
 * <h3>StructureDefinition Integration</h3>
 * <p>The validator dynamically loads the corresponding {@code StructureDefinition} to evaluate
 * slice cardinalities. This is used to verify that each required {@code input} slice is present
 * the correct number of times and that overall input count falls within the base cardinality
 * limits.</p>
 *
 * <h3>Development & CI Support</h3>
 * <p>Project root discovery is supported using either:</p>
 * <ul>
 *   <li>System property: {@code dsf.projectRoot}</li>
 *   <li>Environment variable: {@code DSF_PROJECT_ROOT}</li>
 * </ul>
 * <p>Or by implicit detection of standard folder structures (e.g., {@code src/}, {@code fhir/}).</p>
 *
 * @see dev.dsf.utils.validator.DsfValidatorImpl
 * @see AbstractFhirInstanceValidator
 * @see FhirValidator
 * @see FhirAuthorizationCache
 * @see <a href="https://hl7.org/fhir/profiling.html#slice-cardinality">FHIR Slicing and Cardinality Rules §5.1.0.14</a>
 */
public final class FhirTaskValidator extends AbstractFhirInstanceValidator
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
     * Determines whether this validator supports the given document.
     *
     * @param d the DOM document to validate
     * @return true if it is a FHIR Task resource, false otherwise
     */
    @Override
    public boolean canValidate(Document d)
    {
        return "Task".equals(d.getDocumentElement().getLocalName());
    }

    /**
     * Performs validation of a FHIR Task resource.
     *
     * @param doc the DOM document representing the Task
     * @param resFile the file associated with the resource
     * @return a list of validation issues or confirmations
     */
    @Override
    public List<FhirElementValidationItem> validate(Document doc, File resFile)
    {
        final String ref   = computeReference(doc, resFile);
        final List<FhirElementValidationItem> issues = new ArrayList<>();

        checkMetaAndBasic(doc, resFile, ref, issues);
        checkPlaceholders(doc, resFile, ref, issues);

        validateInputs(doc, resFile, ref, issues);

        validateTerminology(doc, resFile, ref, issues);

        validateRequesterAuthorization(doc, resFile, ref, issues);
        validateRecipientAuthorization(doc, resFile, ref, issues);

        return issues;
    }

    /**
     * Validates core metadata and basic elements of the Task.
     */
    private void checkMetaAndBasic(Document doc, File f, String ref, List<FhirElementValidationItem> out)
    {
        //  meta.profile
        NodeList prof = xp(doc, TASK_XP + "/*[local-name()='meta']/*[local-name()='profile']/@value");
        if (prof == null || prof.getLength() == 0)
            out.add(new FhirTaskMissingProfileValidationItem(f, ref));
        else
            out.add(ok(f, ref, "meta.profile present."));

        //  instantiatesCanonical
        String instCanon = val(doc, TASK_XP + "/*[local-name()='instantiatesCanonical']/@value");
        if (blank(instCanon))
            out.add(new FhirTaskMissingInstantiatesCanonicalValidationItem(f, ref));
        else
        {
            out.add(ok(f, ref, "instantiatesCanonical found."));
            // Existence-Check
            File root = determineProjectRoot(f);
            if (root != null)
            {
                boolean exists = FhirValidator.activityDefinitionExistsForInstantiatesCanonical(instCanon, root);
                if (!exists)
                    out.add(new FhirTaskUnknownInstantiatesCanonicalValidationItem(
                            f, ref,
                            "No ActivityDefinition '" + instCanon + "' under '" +
                                    f.getName() + "'."));
                else
                    out.add(ok(f, ref, "ActivityDefinition exists."));
            }
        }

        //  status
        String status = val(doc, TASK_XP + "/*[local-name()='status']/@value");
        if (blank(status))
            out.add(new FhirTaskMissingStatusValidationItem(f, ref));
        else
        if (!"draft".equals(status))
            out.add(new FhirTaskStatusNotDraftValidationItem(f, ref,
                    "status must be 'draft' (found '" + status + "')"));
        else
            out.add(ok(f, ref, "status = 'draft'"));


        //  intent ('order')
        String intent = val(doc, TASK_XP + "/*[local-name()='intent']/@value");
        if (!"order".equals(intent))
            out.add(new FhirTaskValueIsNotSetAsOrderValidationItem(f, ref,
                    "intent must be 'order' (found '" + intent + "')"));
        else
            out.add(ok(f, ref, "intent = order"));

        //  requester.identifier.system
        String reqSys = val(doc, TASK_XP +
                "/*[local-name()='requester']/*[local-name()='identifier']/*[local-name()='system']/@value");
        if(blank(reqSys))
            out.add(new FhirTaskMissingRequesterValidationItem(f, ref));

        else if (!SYSTEM_ORG_ID.equals(reqSys))
            out.add(new FhirTaskInvalidRequesterValidationItem(f, ref,
                    "requester.identifier.system must be '" + SYSTEM_ORG_ID + "'"));
        else
            out.add(ok(f, ref, "requester.identifier.system OK"));

        //  restriction.recipient.identifier.system
        String recSys = val(doc, TASK_XP +
                "/*[local-name()='restriction']/*[local-name()='recipient']" +
                "/*[local-name()='identifier']/*[local-name()='system']/@value");
        if(blank(recSys))
            out.add(new FhirTaskMissingRecipientValidationItem(f, ref));

        else if (!SYSTEM_ORG_ID.equals(recSys))
            out.add(new FhirTaskInvalidRecipientValidationItem(f, ref,
                    "restriction.recipient.identifier.system must be '" + SYSTEM_ORG_ID + "'"));
        else
            out.add(ok(f, ref, "restriction.recipient.identifier.system OK"));
    }

    /**
     * Validates presence of required development placeholders such as #{date} and #{organization}.
     */
    private void checkPlaceholders(Document doc, File f, String ref, List<FhirElementValidationItem> out)
    {
        String authoredOn = val(doc, TASK_XP + "/*[local-name()='authoredOn']/@value");
        if (authoredOn != null && !authoredOn.contains("#{date}"))
            out.add(new FhirTaskDateNoPlaceholderValidationItem(f, ref,
                    "<authoredOn> must contain '#{date}'."));
        else
            out.add(ok(f, ref, "<authoredOn> placeholder OK."));

        String reqIdVal = val(doc,
                TASK_XP + "/*[local-name()='requester']/*[local-name()='identifier']" +
                        "/*[local-name()='value']/@value");
        if (reqIdVal == null || !reqIdVal.contains("#{organization}"))
            out.add(new FhirTaskRequesterOrganizationNoPlaceholderValidationItem(f, ref,
                    "requester.identifier.value must contain '#{organization}'."));
        else
            out.add(ok(f, ref, "requester.identifier.value placeholder OK."));

        String recIdVal = val(doc,
                TASK_XP + "/*[local-name()='restriction']/*[local-name()='recipient']" +
                        "/*[local-name()='identifier']/*[local-name()='value']/@value");
        if (recIdVal == null || !recIdVal.contains("#{organization}"))
            out.add(new FhirTaskRecipientOrganizationNoPlaceholderValidationItem(f, ref,
                    "restriction.recipient.identifier.value must contain '#{organization}'."));
        else
            out.add(ok(f, ref, "restriction.recipient.identifier.value placeholder OK."));
    }

    /**
     * Validates the <code>Task.input</code> elements of a FHIR {@code Task} resource.
     * This method performs structural, semantic, and rule-based validation for all {@code Task.input} elements
     * based on the provided {@code StructureDefinition} and known business constraints.
     *
     * <p>Validation includes the following aspects:</p>
     *
     * <ul>
     *   <li><strong>Presence Check:</strong>
     *     <ul>
     *       <li>Fails if the {@code Task.input} list is empty or missing entirely.</li>
     *     </ul>
     *   </li>
     *
     *   <li><strong>Structural Validation:</strong>
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
     *   <li><strong>Status Validation:</strong>
     *     <ul>
     *       <li>Ensures the {@code Task.status} value is among the known valid codes from the HL7 Task ValueSet.</li>
     *       <li>Unknown values result in an error; valid ones emit a confirmation.</li>
     *     </ul>
     *   </li>
     *
     *   <li><strong>Correlation Input Validation:</strong>
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
     * <p>All validation outcomes are reported as {@link FhirElementValidationItem} instances and
     * appended to the provided {@code out} list.</p>
     *
     * @param doc  the XML DOM of the {@code Task} resource
     * @param f    the source file for the Task resource (used for context and reporting)
     * @param ref  a logical or canonical identifier (typically {@code instantiatesCanonical})
     * @param out  a mutable list that will be populated with validation results
     *
     * @see dev.dsf.utils.validator.item.FhirTaskMissingInputValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskInputRequiredCodingSystemAndCodingCodeValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskInputMissingValueValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskInputDuplicateSliceValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskRequiredInputWithCodeMessageNameValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskStatusRequiredInputBusinessKeyValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskBusinessKeyExistsValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskBusinessKeyCheckIsSkippedValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskUnknownStatusValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskCorrelationExistsValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskCorrelationMissingButRequiredValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskInputInstanceCountBelowMinItem
     * @see dev.dsf.utils.validator.item.FhirTaskInputInstanceCountExceedsMaxItem
     * @see dev.dsf.utils.validator.item.FhirTaskInputSliceCountBelowSliceMinItem
     * @see dev.dsf.utils.validator.item.FhirTaskInputSliceCountExceedsSliceMaxItem
     * @see dev.dsf.utils.validator.item.FhirTaskCouldNotLoadProfileValidationItem
     */
    private void validateInputs(Document doc, File f, String ref, List<FhirElementValidationItem> out)
    {
        System.out.println(">>> validateInputs called on: " + f.getName());

        // Load cardinality definitions from StructureDefinition
        String profileUrl = val(doc, TASK_XP + "/*[local-name()='meta']/*[local-name()='profile']/@value");
        Map<String, SliceCard> cards = loadInputCardinality(determineProjectRoot(f), profileUrl);

        if (cards == null)
        {
            out.add(new FhirTaskCouldNotLoadProfileValidationItem(
                    f, ref, "StructureDefinition for profile '" + profileUrl + "' not found → instance-level cardinality check skipped."));
        }

        NodeList ins = xp(doc, INPUT_XP);
        if (ins == null || ins.getLength() == 0)
        {
            out.add(new FhirTaskMissingInputValidationItem(f, ref));
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
                out.add(new FhirTaskInputRequiredCodingSystemAndCodingCodeValidationItem(f, ref,
                        "Task.input without system/code"));
                continue;
            }
            else
            {
                out.add(ok(f, ref, "Task.input has required system and code: " + sys + "#" + code));
            }

            // Value check
            if (blank(v))
                out.add(new FhirTaskInputMissingValueValidationItem(f, ref,
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
                out.add(new FhirTaskInputDuplicateSliceValidationItem(f, ref,
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
            out.add(new FhirTaskRequiredInputWithCodeMessageNameValidationItem(f, ref));

        // Status-dependent rule
        String status = val(doc, TASK_XP + "/*[local-name()='status']/@value");
        boolean statusIsDraft = "draft".equals(status);

        // Check if status is known
        if (FhirAuthorizationCache.isUnknown(FhirAuthorizationCache.CS_TASK_STATUS, status)) {
            out.add(new FhirTaskUnknownStatusValidationItem(f, ref, status));
        } else {
            out.add(ok(f, ref, "Task status '" + status + "' is valid"));
        }

        if (STATUSES_NEED_BIZKEY.contains(status)) {
            if (!businessKey)
                out.add(new FhirTaskStatusRequiredInputBusinessKeyValidationItem(
                        f, ref, "status='" + status + "' needs business-key"));
            else
                out.add(ok(f, ref, "status='" + status + "' → business-key present as required"));
        }
        else if (statusIsDraft) {
            if (businessKey) {
                out.add(new FhirTaskBusinessKeyExistsValidationItem(
                        f, ref, "businessKey must not be present when status is 'draft'"));
            } else {
                out.add(ok(f, ref, "status=draft → business-key correctly absent"));
            }
        }
        else {
            // Status is known but doesn't require specific business key rules
            out.add(new FhirTaskBusinessKeyCheckIsSkippedValidationItem(
                    f, ref, "Business key validation skipped for status '" + status + "'"));
        }

        boolean corrAllowed = isCorrelationAllowed(cards);

        if (correlation) {
            if (corrAllowed)
                out.add(ok(f, ref,
                        "correlation input present and permitted by StructureDefinition"));
            else
                out.add(new FhirTaskCorrelationExistsValidationItem(
                        f, ref, "correlation input is not allowed by StructureDefinition"));
        } else {
            // missing ↔ only an error when the slice min > 0
            SliceCard corrCard = (cards != null) ? cards.get("correlation-key") : null;
            if (corrCard != null && corrCard.min() > 0)
                out.add(new FhirTaskCorrelationMissingButRequiredValidationItem(
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
                out.add(new FhirTaskInputInstanceCountBelowMinItem(f, ref, inputCount, baseCard.min()));
            else if (inputCount > baseCard.max())
                out.add(new FhirTaskInputInstanceCountExceedsMaxItem(f, ref, inputCount, baseCard.max()));
            else
                out.add(ok(f, ref, "Task.input count " + inputCount +
                        " within " + baseCard.min() + "‥" + (baseCard.max() == Integer.MAX_VALUE ? "*" : baseCard.max())));

            // Per-slice cardinality
            cards.forEach((code, card) -> {
                if ("__BASE__".equals(code)) return;
                int cnt = sliceCounter.getOrDefault(code, 0);
                if (cnt < card.min())
                    out.add(new FhirTaskInputSliceCountBelowSliceMinItem(f, ref, code, cnt, card.min()));
                else if (cnt > card.max())
                    out.add(new FhirTaskInputSliceCountExceedsSliceMaxItem(f, ref, code, cnt, card.max()));
                else
                    out.add(ok(f, ref, "slice '" + code + "' count " + cnt + " OK"));
            });
        }
    }

    /*
      3) Terminology
       */
    /**
     * Validates all coding elements against known DSF CodeSystems.
     */
    private void validateTerminology(Document doc, File f, String ref, List<FhirElementValidationItem> out)
    {
        NodeList codings = xp(doc, "//coding");
        if (codings == null) return;

        for (int i = 0; i < codings.getLength(); i++)
        {
            Node c = codings.item(i);
            String sys = val(c, "./*[local-name()='system']/@value");
            String code = val(c, "./*[local-name()='code']/@value");

            if (FhirAuthorizationCache.isUnknown(sys, code))
                out.add(new FhirTaskUnknownCodeValidationItem(f, ref,
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
     * @param res the resource file currently being validated (e.g., a Task XML file)
     * @return the project root directory, or {@code null} if no suitable folder is found
     */
    private File determineProjectRoot(File res)

    {
        // ① explicit configuration
        String cfg = Optional.ofNullable(System.getProperty("dsf.projectRoot"))
                .orElse(System.getenv("DSF_PROJECT_ROOT"));
        if (cfg != null && !cfg.isBlank())
        {
            File dir = new File(cfg);
            if (dir.isDirectory())
                return dir;
        }

        // ② / ③ implicit discovery
        for (Path p = res.toPath().getParent(); p != null; p = p.getParent())
        {
            if (Files.isDirectory(p.resolve("src")))   // classic workspace
                return p.toFile();

            if (Files.isDirectory(p.resolve("fhir")))  // exploded JAR / CI layout
                return p.toFile();
        }

        return null;                                   // couldn’t determine
    }

    /*
      Requester/Recipient vs ActivityDefinition check
      */

    /**
     * Validates that the {@code Task.requester.identifier.value} exists, contains the
     * {@code #{organization}} placeholder, and is authorized according to the referenced
     * {@code ActivityDefinition}'s {@code extension-process-authorization} declarations.
     *
     * <p>This method performs a multi-step validation process for the {@code requester.identifier.value} field:</p>
     * <ul>
     *   <li><strong>Existence check:</strong> Fails if the value is missing or blank, using
     *       {@link dev.dsf.utils.validator.item.FhirTaskRequesterIdNotExistValidationItem}.</li>
     *   <li><strong>Placeholder check:</strong> If the value does not contain the required
     *       {@code #{organization}} placeholder, a
     *       {@link dev.dsf.utils.validator.item.FhirTaskRequesterIdNoPlaceholderValidationItem}
     *       is added.</li>
     *   <li><strong>Authorization check:</strong> If the value is present and contains the placeholder,
     *       the method skips authorization (assumes development context) and reports success.
     *       Otherwise, it verifies that the requester is authorized according to the
     *       {@code ActivityDefinition} referred to via {@code instantiatesCanonical}.
     *       If not authorized, a
     *       {@link dev.dsf.utils.validator.item.FhirTaskRequesterNotAuthorisedValidationItem}
     *       is reported.</li>
     * </ul>
     *
     * @param taskDoc   the XML DOM representation of the Task resource
     * @param taskFile  the file from which the Task was loaded (used for context and reporting)
     * @param ref       a canonical reference to the Task (typically extracted from {@code instantiatesCanonical})
     * @param out       the list of validation items to which results are appended
     *
     * @see dev.dsf.utils.validator.item.FhirTaskRequesterIdNotExistValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskRequesterIdNoPlaceholderValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskRequesterNotAuthorisedValidationItem
     */
    private void validateRequesterAuthorization(Document taskDoc,
                                                File taskFile,
                                                String ref,
                                                List<FhirElementValidationItem> out)
    {
        String instCanon = val(taskDoc,
                TASK_XP + "/*[local-name()='instantiatesCanonical']/@value");
        if (blank(instCanon))
            return; // already handled elsewhere

        File projectRoot = determineProjectRoot(taskFile);
        File actFile = FhirValidator.findActivityDefinitionForInstantiatesCanonical(
                instCanon, projectRoot);

        if (actFile == null)
        {
            // The missing ActivityDefinition error is already reported, no need to double-report
            return;
        }

        String requesterId = val(taskDoc,
                TASK_XP + "/*[local-name()='requester']/*[local-name()='identifier']"
                        + "/*[local-name()='value']/@value");

        // Allow the dev placeholder
        if (requesterId == null || requesterId.isBlank())
        {
            out.add(new FhirTaskRequesterIdNotExistValidationItem(
                    taskFile, ref));
            return;
        } else {
            if (!"#{organization}".equals(requesterId))
            {
                out.add(new FhirTaskRequesterIdNoPlaceholderValidationItem (
                        taskFile, ref, "Task.requester.identifier.value: '" + requesterId +"' does not contain the '#{organization}' placeholder, continue to auth check."
                ));
            }
            else
            {
                out.add(ok(taskFile, ref, "requester placeholder – skipped auth check"));
                return;
            }
        }

        boolean authorised = FhirValidator.isRequesterAuthorised(
                actFile, requesterId);

        if (authorised)
            out.add(ok(taskFile, ref, "requester organisation authorised by ActivityDefinition"));
        else
            out.add(new FhirTaskRequesterNotAuthorisedValidationItem(
                    taskFile, ref,
                    "Organisation '" + requesterId
                            + "' is not authorised according to ActivityDefinition "
                            + actFile.getName()));
    }

    /**
     * Validates that the {@code Task.restriction.recipient.identifier.value} is authorized
     * according to the corresponding {@code ActivityDefinition}'s
     * {@code extension-process-authorization} block.
     *
     * <p>This method performs a cross-resource check by locating the
     * {@code ActivityDefinition} referenced by {@code Task.instantiatesCanonical}.
     * It then verifies whether the recipient organization specified in the Task
     * is explicitly listed or generically permitted (e.g., {@code LOCAL_ALL})
     * in the ActivityDefinition's recipient authorization extensions.</p>
     *
     * <p>If the recipient identifier contains the development placeholder
     * {@code #{organization}}, the check is skipped and a success entry is added.
     * This allows local development processes to pass validation.</p>
     *
     * <p>If the recipient organization is not authorized, a
     * {@link dev.dsf.utils.validator.item.FhirTaskRecipientNotAuthorisedValidationItem}
     * is added to the output.</p>
     *
     * @param taskDoc   the DOM representation of the FHIR Task resource
     * @param taskFile  the source file from which the Task was loaded (used for reporting)
     * @param ref       a canonical reference string (e.g., from {@code instantiatesCanonical})
     * @param out       the validation output list to which validation results are appended
     */
    private void validateRecipientAuthorization(Document taskDoc,
                                                File taskFile,
                                                String ref,
                                                List<FhirElementValidationItem> out)
    {
        String instCanon = val(taskDoc,
                TASK_XP + "/*[local-name()='instantiatesCanonical']/@value");
        if (blank(instCanon))
            return;                                     // already reported elsewhere

        File projectRoot = determineProjectRoot(taskFile);
        File actFile = FhirValidator.findActivityDefinitionForInstantiatesCanonical(
                instCanon, projectRoot);
        if (actFile == null)
            return;                                     // ActivityDefinition missing

        String recipientId = val(taskDoc,
                TASK_XP + "/*[local-name()='restriction']/*[local-name()='recipient']"
                        + "/*[local-name()='identifier']/*[local-name()='value']/@value");

        /* Allow development placeholder */
        if (recipientId != null && recipientId.equals("#{organization}"))
        {
            out.add(ok(taskFile, ref, "recipient placeholder – skipped auth check"));
            return;
        }

        boolean authorised = FhirValidator.isRecipientAuthorised(actFile, recipientId);

        if (authorised)
            out.add(ok(taskFile, ref, "recipient organisation authorised by ActivityDefinition"));
        else
            out.add(new FhirTaskRecipientNotAuthorisedValidationItem(
                    taskFile, ref,
                    "Organisation '" + recipientId
                            + "' is not authorised according to ActivityDefinition "
                            + actFile.getName()));
    }

    /* utils inside FhirTaskValidator ----------------------------------------- */
    private static record SliceCard(int min, int max) {}

    /**
     * Loads the cardinality settings from the StructureDefinition for a given Task profile.
     * Tries parsing as XML first; if that fails, falls back to converting JSON to XML.
     *
     * @param projectRoot the root folder of the FHIR definitions
     * @param profileUrl  the canonical URL of the Task profile
     * @return a map of slice names to their cardinality, or null if no SD file is found or an error occurs
     */
    private Map<String, SliceCard> loadInputCardinality(File projectRoot, String profileUrl) {
        File sdFile = FhirValidator.findStructureDefinitionFile(profileUrl, projectRoot);
        if (sdFile == null) {
            return null;
        }

        try {
            // parseXml, or if that fails parseJsonToXml
            Document sd;
            try {
                sd = FhirValidator.parseXml(sdFile.toPath());
            } catch (Exception e) {
                sd = FhirValidator.parseJsonToXml(sdFile.toPath());
            }

            Map<String, SliceCard> map = new HashMap<>();

            // base element ----------------------------------------------------
            String minBase = AbstractFhirInstanceValidator.extractSingleNodeValue(
                    sd,
                    "//*[local-name()='element' and @id='Task.input']/*[local-name()='min']/@value"
            );
            String maxBase = AbstractFhirInstanceValidator.extractSingleNodeValue(
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

                String mi = AbstractFhirInstanceValidator.extractSingleNodeValue(
                        n,
                        "./*[local-name()='min']/@value"
                );
                String ma = AbstractFhirInstanceValidator.extractSingleNodeValue(
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
