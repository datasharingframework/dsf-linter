package dev.dsf.utils.validator.fhir;

import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.AbstractFhirInstanceValidator;
import dev.dsf.utils.validator.util.FhirAuthorizationCache;
import dev.dsf.utils.validator.util.FhirValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * <h2>DSF Task Validator (Profile: dsf-task-base 1.0.0)</h2>
 *
 * <p>This class implements a FHIR validator for Task resources used in the
 * Digital Sample Framework (DSF). It performs validation checks according to
 * the <code>dsf-task-base</code> profile (version 1.0.0), supporting both
 * structural and semantic validation.</p>
 *
 * <p>The following validation features are supported:</p>
 * <ul>
 *   <li><strong>Meta and core checks:</strong> Verifies that required fields such as
 *       <code>id</code>, <code>instantiatesCanonical</code>, <code>intent</code>, and
 *       <code>status</code> are present and valid.</li>
 *   <li><strong>Placeholder enforcement:</strong> Ensures that certain fields contain required
 *       template placeholders such as <code>#{date}</code> and <code>#{organization}</code>.</li>
 *   <li><strong>Input slice validation:</strong> Validates presence and uniqueness of required input slices
 *       like <code>message-name</code> and <code>business-key</code>. Optional slices like
 *       <code>correlation-key</code> are also detected.</li>
 *   <li><strong>Duplicate slice detection:</strong> Tracks input slice types using a
 *       <code>Map&lt;String,Integer&gt;</code> and flags duplicates (i.e., where count > 1).</li>
 *   <li><strong>Terminology checks:</strong> Cross-references all <code>coding</code> elements
 *       against the known DSF CodeSystems using {@link FhirAuthorizationCache}.</li>
 *   <li><strong>Canonical reference resolution:</strong> Automatically resolves the effective resource
 *       reference using <code>instantiatesCanonical</code> or <code>identifier.value</code>.</li>
 *   <li><strong>Project root discovery:</strong> Determines the root directory of the FHIR project
 *       either via a configuration option (system property or environment variable) or by walking
 *       the parent directory hierarchy.</li>
 * </ul>
 *
 * <p>Each check results in one of the following validation items:
 * <ul>
 *   <li>{@link FhirElementValidationItemSuccess} for successful validations</li>
 *   <li>Various {@link FhirElementValidationItem} subclasses for validation errors</li>
 * </ul>
 * </p>
 *
 * <h3>How to Use</h3>
 * The validator is automatically invoked when validating FHIR Task files using the
 * {@code DsfValidatorImpl} class. This class does not require instantiation and is managed by
 * the validation framework.
 *
 * <h3>Configuration</h3>
 * Optionally, the project root can be specified via:
 * <ul>
 *   <li>System property <code>dsf.projectRoot</code></li>
 *   <li>Environment variable <code>DSF_PROJECT_ROOT</code></li>
 * </ul>
 *
 * @see dev.dsf.utils.validator.DsfValidatorImpl
 * @see dev.dsf.utils.validator.util.FhirAuthorizationCache
 * @see dev.dsf.utils.validator.util.FhirValidator
 */
public final class FhirTaskValidator extends AbstractFhirInstanceValidator
{
    // XPath constants
    private static final String TASK_XP           = "/*[local-name()='Task']";
    private static final String INPUT_XP          = TASK_XP + "/*[local-name()='input']";
    private static final String OUTPUT_XP         = TASK_XP + "/*[local-name()='output']";
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
        // id
        String id = val(doc, TASK_XP + "/@id");
        if (blank(id))
            out.add(new FhirTaskMissingIdValidationItem(f, ref));
        else
            out.add(ok(f, ref, "Task.id present."));

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
     * Validates the <code>input</code> elements of a FHIR {@code Task} resource.
     *
     * <p>This method performs the following checks for each {@code Task.input}:</p>
     * <ul>
     *   <li><strong>Presence:</strong> Fails if no {@code input} elements are found.</li>
     *   <li><strong>Structure:</strong> Ensures each input has a non-blank coding system and code.</li>
     *   <li><strong>Value:</strong> Verifies that a value[x] element is present for each input.</li>
     *   <li><strong>Duplicate detection:</strong> Tracks duplicate combinations of coding system and code.</li>
     *   <li><strong>Slice type validation:</strong> Recognizes specific slices based on {@code system=...bpmn-message}
     *       and {@code code}:
     *     <ul>
     *       <li>{@code message-name} – mandatory</li>
     *       <li>{@code business-key} – conditionally required (based on {@code status})</li>
     *       <li>{@code correlation-key} – <strong>must not be present</strong>; its presence causes a validation error</li>
     *     </ul>
     *   </li>
     *   <li><strong>Status-dependent checks:</strong> Validates {@code business-key} depending on the Task's {@code status}:
     *     <ul>
     *       <li>Required for {@code in-progress}, {@code completed}, {@code failed}</li>
     *       <li>Prohibited for {@code draft}</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>Each condition results in one or more {@link FhirElementValidationItem}s being added to the provided output list.
     * Valid configurations are reported using {@code ok(...)}.</p>
     *
     * @param doc  the DOM representation of the Task resource being validated
     * @param f    the source file representing the FHIR Task XML file
     * @param ref  a canonical reference to the resource (typically derived from {@code instantiatesCanonical})
     * @param out  the list of validation items to which results are appended
     *
     * @see dev.dsf.utils.validator.item.FhirTaskInputRequiredCodingSystemAndCodingCodeValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskInputMissingValueValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskInputDuplicateSliceValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskRequiredInputWithCodeMessageNameValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskStatusRequiredInputBusinessKeyValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskBusinessKeyExistsValidationItem
     * @see dev.dsf.utils.validator.item.FhirTaskCorrelationExistsValidationItem
     */
    private void validateInputs(Document doc, File f, String ref, List<FhirElementValidationItem> out)
    {
        System.out.println(">>> validateInputs called on: " + f.getName());

        NodeList ins = xp(doc, INPUT_XP);
        if (ins == null || ins.getLength() == 0)
        {
            out.add(new FhirTaskMissingInputValidationItem(f, ref));
            return;
        }

        boolean messageName = false, businessKey = false;
        boolean correlation = false;

        Map<String,Integer> duplicates = new HashMap<>();

        for (int i = 0; i < ins.getLength(); i++)
        {
            Node in  = ins.item(i);
            String sys = val(in, CODING_SYS_XP);
            String code = val(in, CODING_CODE_XP);
            String v    = extractValueX(in);

            /*
             Duplicate counter
              */
            if (!blank(sys) && !blank(code))
                duplicates.merge(sys + "#" + code, 1, Integer::sum);

            /*
             Missing coding data
              */
            if (blank(sys) || blank(code))
            {
                out.add(new FhirTaskInputRequiredCodingSystemAndCodingCodeValidationItem(f, ref,
                        "Task.input without system/code"));
                continue;
            }

            /*
            Value present?
              */
            if (blank(v))
                out.add(new FhirTaskInputMissingValueValidationItem(f, ref,
                        "Task.input(" + code + ") missing value[x]"));
            else
                out.add(ok(f, ref,
                        "input '" + code + "' value='" + v + "'"));

            /*
            Slice types
              */
            if (SYSTEM_BPMN_MSG.equals(sys))
            {
                switch (code)
                {
                    case "message-name"    -> messageName = true;
                    case "business-key"    -> businessKey = true;
                    case "correlation-key" -> correlation  = true;
                }
            }
        }

        /*
        Duplicates
         */
        duplicates.forEach((k,v) -> {
            if (v > 1)
                out.add(new FhirTaskInputDuplicateSliceValidationItem(f, ref,
                        "Duplicate slice '" + k + "' (" + v + "×)"));
        });

        /*
        Presence checks
         */
        if (messageName)
            out.add(ok(f, ref, "mandatory slice 'message-name' present"));
        else
            out.add(new FhirTaskRequiredInputWithCodeMessageNameValidationItem(f, ref));

        /*
          Status-dependent rule
          */
        String status = val(doc, TASK_XP + "/*[local-name()='status']/@value");
        boolean statusActive = false;
        if (STATUSES_NEED_BIZKEY.contains(status)) {
            if (!businessKey)
                out.add(new FhirTaskStatusRequiredInputBusinessKeyValidationItem(
                        f, ref, "status='" + status + "' needs business-key"));
            else
                out.add(new FhirTaskBusinessKeyExistsAndStatusNotDraftValidationItem(
                        f, ref, "Task.status is not 'draft' and business-key: '" + businessKey +"' input is present as expected."
                        )
                );
        }
        else if ("draft".equals(status)) {
            out.add(ok(f, ref, "status=draft → business-key not required"));
            statusActive = true;
        }
        if (businessKey && statusActive)
            out.add(new FhirTaskBusinessKeyExistsValidationItem(
                    f, ref, "businessKey: "+ businessKey +" must not be present."));
        if (correlation)
            out.add(new FhirTaskCorrelationExistsValidationItem(
                    f, ref, "correlation: "+ correlation +" must not be present."));
        else
            out.add(ok(f, ref, "correlation input does not exist as expected."));
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
     * Validates that the {@code Task.requester.identifier.value} is authorized
     * based on the corresponding {@code ActivityDefinition}'s
     * {@code extension-process-authorization} declarations.
     *
     * <p>This method performs a cross-resource validation by locating the
     * {@code ActivityDefinition} referenced by the {@code Task.instantiatesCanonical}
     * element. It then verifies whether the requesting organization specified
     * in the Task is explicitly listed or generically allowed (e.g., via
     * {@code LOCAL_ALL} or {@code LOCAL_ALL_PRACTITIONER}) in the authorization
     * rules defined in the ActivityDefinition.</p>
     *
     * <p>If the requester identifier contains the development placeholder
     * {@code #{organization}}, the check is skipped and a successful validation
     * result is added. This allows developers to validate incomplete resources
     * during development.</p>
     *
     * <p>If the requester is not authorized according to the ActivityDefinition,
     * a {@link dev.dsf.utils.validator.item.FhirTaskRequesterNotAuthorisedValidationItem}
     * is added to the output list.</p>
     *
     * @param taskDoc   the XML DOM representation of the Task resource
     * @param taskFile  the source file from which the Task was loaded (used for reporting)
     * @param ref       a canonical reference to the Task (typically from {@code instantiatesCanonical})
     * @param out       the list of validation items to which results are appended
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
        if (requesterId != null && requesterId.contains("#{organization}"))
        {
            out.add(ok(taskFile, ref, "requester placeholder – skipped auth check"));
            return;
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
        if (recipientId != null && recipientId.contains("#{organization}"))
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

}
