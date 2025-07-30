package dev.dsf.utils.validator.fhir;

import dev.dsf.utils.validator.item.FhirElementValidationItem;
import dev.dsf.utils.validator.item.FhirValueSetMissingReadAccessTagAllOrLocalValidationItem;
import dev.dsf.utils.validator.item.FhirValueSetOrganizationRoleMissingValidCodeValueValidationItem;
import dev.dsf.utils.validator.util.FhirValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FhirValueSetValidator}.
 * <p>
 * These tests verify that ValueSet resources are validated correctly for the presence and correctness
 * of the read-access-tag in the meta.tag section, specifically for the codes "ALL" or "LOCAL".
 * Various scenarios are covered, including valid and invalid tags, missing meta sections, and multiple tags.
 */
class FhirValueSetValidatorTest {

    private FhirValueSetValidator validator;
    private File testFile;

    /**
     * Initializes the validator and test file before each test.
     */
    @BeforeEach
    void setUp() {
        validator = new FhirValueSetValidator();
        testFile = new File("test-valueSet.json");
    }

    /**
     * Validates several example ValueSet resource files to ensure they contain a valid read-access-tag
     * with code "ALL" or "LOCAL" in the meta.tag section.
     *
     * @throws Exception if file parsing or validation fails
     */
    @Test
    void testValidateResourceFilesWithALLTag() throws Exception {
        // Use relative paths from test resources
        String[] resourcePaths = {
            "src/test/resources/fhir/examples/dashBoardReport/ValueSet/approve-parameters.json",
            "src/test/resources/fhir/examples/feasibility/ValueSet/feasibility.json",
            "src/test/resources/fhir/examples/pingPongProcess/ValueSet/dsf-ping.json",
            "src/test/resources/fhir/examples/pingPongProcess/ValueSet/dsf-ping-status.json"
        };

        for (String resourcePath : resourcePaths) {
            Path filePath = Paths.get(resourcePath);
            testResourceFile(filePath);
        }
    }

    /**
     * Helper method to test a single resource file for correct validation results.
     *
     * @param filePath the path to the ValueSet resource file
     * @throws Exception if file parsing or validation fails
     */
    private void testResourceFile(Path filePath) throws Exception {
        if (!filePath.toFile().exists()) {
            System.out.println("Skipping test - file not found: " + filePath);
            return;
        }

        Document doc = FhirValidator.parseFhirFile(filePath);
        assertNotNull(doc, "Document should be parseable");

        List<FhirElementValidationItem> results = validator.validate(doc, filePath.toFile());

        // Should contain at least one success item for meta.tag validation
        boolean hasMetaTagSuccess = results.stream()
                .anyMatch(item -> item.getDescription().contains("meta.tag read-access-tag contains ALL or LOCAL – OK"));

        assertTrue(hasMetaTagSuccess, "Should have successful meta.tag validation for file: " + filePath.getFileName());

        // Should not contain any missing read access tag errors
        boolean hasMissingTagError = results.stream()
                .anyMatch(item -> item instanceof FhirValueSetMissingReadAccessTagAllOrLocalValidationItem);

        assertFalse(hasMissingTagError, "Should not have missing read access tag errors for file: " + filePath.getFileName());
    }

    /**
     * Tests that a ValueSet with a valid "ALL" read-access-tag in the meta.tag section is validated successfully.
     *
     * @throws Exception if XML parsing or validation fails
     */
    @Test
    void testValueSetWithValidALLTag() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValueSet xmlns="http://hl7.org/fhir">
                <url value="http://example.org/ValueSet/test"/>
                <name value="TestValueSet"/>
                <title value="Test ValueSet"/>
                <publisher value="Test Publisher"/>
                <description value="Test Description"/>
                <version value="#{version}"/>
                <date value="#{date}"/>
                <meta>
                    <tag>
                        <system value="http://dsf.dev/fhir/CodeSystem/read-access-tag"/>
                        <code value="ALL"/>
                    </tag>
                </meta>
                <compose>
                    <include>
                        <system value="http://example.org/CodeSystem/test"/>
                        <version value="#{version}"/>
                    </include>
                </compose>
            </ValueSet>
            """;

        Document doc = parseXmlString(xmlContent);
        List<FhirElementValidationItem> results = validator.validate(doc, testFile);

        boolean hasSuccess = results.stream()
                .anyMatch(item -> item.getDescription().contains("meta.tag read-access-tag contains ALL or LOCAL – OK"));
        assertTrue(hasSuccess, "Should validate successfully with ALL tag");
    }

    /**
     * Tests that a ValueSet with a valid "LOCAL" read-access-tag in the meta.tag section is validated successfully.
     *
     * @throws Exception if XML parsing or validation fails
     */
    @Test
    void testValueSetWithValidLOCALTag() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValueSet xmlns="http://hl7.org/fhir">
                <url value="http://example.org/ValueSet/test"/>
                <name value="TestValueSet"/>
                <title value="Test ValueSet"/>
                <publisher value="Test Publisher"/>
                <description value="Test Description"/>
                <version value="#{version}"/>
                <date value="#{date}"/>
                <meta>
                    <tag>
                        <system value="http://dsf.dev/fhir/CodeSystem/read-access-tag"/>
                        <code value="LOCAL"/>
                    </tag>
                </meta>
                <compose>
                    <include>
                        <system value="http://example.org/CodeSystem/test"/>
                        <version value="#{version}"/>
                    </include>
                </compose>
            </ValueSet>
            """;

        Document doc = parseXmlString(xmlContent);
        List<FhirElementValidationItem> results = validator.validate(doc, testFile);

        boolean hasSuccess = results.stream()
                .anyMatch(item -> item.getDescription().contains("meta.tag read-access-tag contains ALL or LOCAL – OK"));
        assertTrue(hasSuccess, "Should validate successfully with LOCAL tag");
    }

    /**
     * Tests that a ValueSet missing a valid read-access-tag in the meta.tag section is reported as an error.
     *
     * @throws Exception if XML parsing or validation fails
     */
    @Test
    void testValueSetWithMissingReadAccessTag() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValueSet xmlns="http://hl7.org/fhir">
                <url value="http://example.org/ValueSet/test"/>
                <name value="TestValueSet"/>
                <title value="Test ValueSet"/>
                <publisher value="Test Publisher"/>
                <description value="Test Description"/>
                <version value="#{version}"/>
                <date value="#{date}"/>
                <meta>
                    <tag>
                        <system value="http://example.org/CodeSystem/other"/>
                        <code value="SOMETHING_ELSE"/>
                    </tag>
                </meta>
                <compose>
                    <include>
                        <system value="http://example.org/CodeSystem/test"/>
                        <version value="#{version}"/>
                    </include>
                </compose>
            </ValueSet>
            """;

        Document doc = parseXmlString(xmlContent);
        List<FhirElementValidationItem> results = validator.validate(doc, testFile);

        boolean hasError = results.stream()
                .anyMatch(item -> item instanceof FhirValueSetMissingReadAccessTagAllOrLocalValidationItem
                        && item.getDescription().contains("meta.tag must contain at least one read-access-tag with code 'ALL' or 'LOCAL'"));
        assertTrue(hasError, "Should report error for missing read access tag");
    }

    /**
     * Tests that a ValueSet with an incorrect code in the read-access-tag is reported as an error.
     *
     * @throws Exception if XML parsing or validation fails
     */
    @Test
    void testValueSetWithWrongCode() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValueSet xmlns="http://hl7.org/fhir">
                <url value="http://example.org/ValueSet/test"/>
                <name value="TestValueSet"/>
                <title value="Test ValueSet"/>
                <publisher value="Test Publisher"/>
                <description value="Test Description"/>
                <version value="#{version}"/>
                <date value="#{date}"/>
                <meta>
                    <tag>
                        <system value="http://dsf.dev/fhir/CodeSystem/read-access-tag"/>
                        <code value="WRONG_CODE"/>
                    </tag>
                </meta>
                <compose>
                    <include>
                        <system value="http://example.org/CodeSystem/test"/>
                        <version value="#{version}"/>
                    </include>
                </compose>
            </ValueSet>
            """;

        Document doc = parseXmlString(xmlContent);
        List<FhirElementValidationItem> results = validator.validate(doc, testFile);

        boolean hasError = results.stream()
                .anyMatch(item -> item instanceof FhirValueSetMissingReadAccessTagAllOrLocalValidationItem);
        assertTrue(hasError, "Should report error for wrong code in read access tag");
    }

    /**
     * Tests that a ValueSet with multiple tags, including at least one valid read-access-tag,
     * is validated successfully.
     *
     * @throws Exception if XML parsing or validation fails
     */
    @Test
    void testValueSetWithMultipleTags() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValueSet xmlns="http://hl7.org/fhir">
                <url value="http://example.org/ValueSet/test"/>
                <name value="TestValueSet"/>
                <title value="Test ValueSet"/>
                <publisher value="Test Publisher"/>
                <description value="Test Description"/>
                <version value="#{version}"/>
                <date value="#{date}"/>
                <meta>
                    <tag>
                        <system value="http://example.org/CodeSystem/other"/>
                        <code value="SOMETHING"/>
                    </tag>
                    <tag>
                        <system value="http://dsf.dev/fhir/CodeSystem/read-access-tag"/>
                        <code value="ALL"/>
                    </tag>
                    <tag>
                        <system value="http://dsf.dev/fhir/CodeSystem/read-access-tag"/>
                        <code value="LOCAL"/>
                    </tag>
                </meta>
                <compose>
                    <include>
                        <system value="http://example.org/CodeSystem/test"/>
                        <version value="#{version}"/>
                    </include>
                </compose>
            </ValueSet>
            """;

        Document doc = parseXmlString(xmlContent);
        List<FhirElementValidationItem> results = validator.validate(doc, testFile);

        boolean hasSuccess = results.stream()
                .anyMatch(item -> item.getDescription().contains("meta.tag read-access-tag contains ALL or LOCAL – OK"));
        assertTrue(hasSuccess, "Should validate successfully with multiple tags including valid read access tag");
    }

    /**
     * Tests that a ValueSet with no meta section is reported as an error.
     *
     * @throws Exception if XML parsing or validation fails
     */
    @Test
    void testValueSetWithNoMetaSection() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValueSet xmlns="http://hl7.org/fhir">
                <url value="http://example.org/ValueSet/test"/>
                <name value="TestValueSet"/>
                <title value="Test ValueSet"/>
                <publisher value="Test Publisher"/>
                <description value="Test Description"/>
                <version value="#{version}"/>
                <date value="#{date}"/>
                <compose>
                    <include>
                        <system value="http://example.org/CodeSystem/test"/>
                        <version value="#{version}"/>
                    </include>
                </compose>
            </ValueSet>
            """;

        Document doc = parseXmlString(xmlContent);
        List<FhirElementValidationItem> results = validator.validate(doc, testFile);

        boolean hasError = results.stream()
                .anyMatch(item -> item instanceof FhirValueSetMissingReadAccessTagAllOrLocalValidationItem);
        assertTrue(hasError, "Should report error when no meta section exists");
    }

    /**
     * Tests that a ValueSet with an empty meta section is reported as an error.
     *
     * @throws Exception if XML parsing or validation fails
     */
    @Test
    void testValueSetWithEmptyMetaSection() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValueSet xmlns="http://hl7.org/fhir">
                <url value="http://example.org/ValueSet/test"/>
                <name value="TestValueSet"/>
                <title value="Test ValueSet"/>
                <publisher value="Test Publisher"/>
                <description value="Test Description"/>
                <version value="#{version}"/>
                <date value="#{date}"/>
                <meta>
                </meta>
                <compose>
                    <include>
                        <system value="http://example.org/CodeSystem/test"/>
                        <version value="#{version}"/>
                    </include>
                </compose>
            </ValueSet>
            """;

        Document doc = parseXmlString(xmlContent);
        List<FhirElementValidationItem> results = validator.validate(doc, testFile);

        boolean hasError = results.stream()
                .anyMatch(item -> item instanceof FhirValueSetMissingReadAccessTagAllOrLocalValidationItem);
        assertTrue(hasError, "Should report error when meta section is empty");
    }

    /**
     * Tests that a ValueSet with valid organization role codes in parent-organization-role extensions
     * is validated successfully.
     *
     * @throws Exception if XML parsing or validation fails
     */
    @Test
    void testValueSetWithValidOrganizationRoleCode() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValueSet xmlns="http://hl7.org/fhir">
                <url value="http://example.org/ValueSet/test"/>
                <name value="TestValueSet"/>
                <title value="Test ValueSet"/>
                <publisher value="Test Publisher"/>
                <description value="Test Description"/>
                <version value="#{version}"/>
                <date value="#{date}"/>
                <meta>
                    <tag>
                        <system value="http://dsf.dev/fhir/CodeSystem/read-access-tag"/>
                        <code value="ALL"/>
                        <extension url="http://dsf.dev/fhir/StructureDefinition/extension-read-access-parent-organization-role">
                            <extension url="organization-role">
                                <valueCoding>
                                    <system value="http://dsf.dev/fhir/CodeSystem/organization-role"/>
                                    <code value="DIC"/>
                                </valueCoding>
                            </extension>
                        </extension>
                    </tag>
                </meta>
                <compose>
                    <include>
                        <system value="http://example.org/CodeSystem/test"/>
                        <version value="#{version}"/>
                    </include>
                </compose>
            </ValueSet>
            """;

        Document doc = parseXmlString(xmlContent);
        List<FhirElementValidationItem> results = validator.validate(doc, testFile);

        boolean hasSuccess = results.stream()
                .anyMatch(item -> item.getDescription().contains("meta.tag parent-organization-role code 'DIC' OK"));
        assertTrue(hasSuccess, "Should validate successfully with valid organization role code");
    }

    /**
     * Tests that a ValueSet with invalid organization role codes in parent-organization-role extensions
     * is reported as an error.
     *
     * @throws Exception if XML parsing or validation fails
     */
    @Test
    void testValueSetWithInvalidOrganizationRoleCode() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValueSet xmlns="http://hl7.org/fhir">
                <url value="http://example.org/ValueSet/test"/>
                <name value="TestValueSet"/>
                <title value="Test ValueSet"/>
                <publisher value="Test Publisher"/>
                <description value="Test Description"/>
                <version value="#{version}"/>
                <date value="#{date}"/>
                <meta>
                    <tag>
                        <system value="http://dsf.dev/fhir/CodeSystem/read-access-tag"/>
                        <code value="ALL"/>
                        <extension url="http://dsf.dev/fhir/StructureDefinition/extension-read-access-parent-organization-role">
                            <extension url="organization-role">
                                <valueCoding>
                                    <system value="http://dsf.dev/fhir/CodeSystem/organization-role"/>
                                    <code value="INVALID_ROLE"/>
                                </valueCoding>
                            </extension>
                        </extension>
                    </tag>
                </meta>
                <compose>
                    <include>
                        <system value="http://example.org/CodeSystem/test"/>
                        <version value="#{version}"/>
                    </include>
                </compose>
            </ValueSet>
            """;

        Document doc = parseXmlString(xmlContent);
        List<FhirElementValidationItem> results = validator.validate(doc, testFile);

        boolean hasError = results.stream()
                .anyMatch(item -> item instanceof FhirValueSetOrganizationRoleMissingValidCodeValueValidationItem
                        && item.getDescription().contains("Invalid organization-role code 'INVALID_ROLE'"));
        assertTrue(hasError, "Should report error for invalid organization role code");
    }

    /**
     * Tests that a ValueSet with multiple organization role codes validates each code correctly.
     *
     * @throws Exception if XML parsing or validation fails
     */
    @Test
    void testValueSetWithMultipleOrganizationRoleCodes() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValueSet xmlns="http://hl7.org/fhir">
                <url value="http://example.org/ValueSet/test"/>
                <name value="TestValueSet"/>
                <title value="Test ValueSet"/>
                <publisher value="Test Publisher"/>
                <description value="Test Description"/>
                <version value="#{version}"/>
                <date value="#{date}"/>
                <meta>
                    <tag>
                        <system value="http://dsf.dev/fhir/CodeSystem/read-access-tag"/>
                        <code value="ALL"/>
                        <extension url="http://dsf.dev/fhir/StructureDefinition/extension-read-access-parent-organization-role">
                            <extension url="organization-role">
                                <valueCoding>
                                    <system value="http://dsf.dev/fhir/CodeSystem/organization-role"/>
                                    <code value="DIC"/>
                                </valueCoding>
                            </extension>
                        </extension>
                        <extension url="http://dsf.dev/fhir/StructureDefinition/extension-read-access-parent-organization-role">
                            <extension url="organization-role">
                                <valueCoding>
                                    <system value="http://dsf.dev/fhir/CodeSystem/organization-role"/>
                                    <code value="INVALID_CODE"/>
                                </valueCoding>
                            </extension>
                        </extension>
                    </tag>
                </meta>
                <compose>
                    <include>
                        <system value="http://example.org/CodeSystem/test"/>
                        <version value="#{version}"/>
                    </include>
                </compose>
            </ValueSet>
            """;

        Document doc = parseXmlString(xmlContent);
        List<FhirElementValidationItem> results = validator.validate(doc, testFile);

        boolean hasValidSuccess = results.stream()
                .anyMatch(item -> item.getDescription().contains("meta.tag parent-organization-role code 'DIC' OK"));
        boolean hasInvalidError = results.stream()
                .anyMatch(item -> item instanceof FhirValueSetOrganizationRoleMissingValidCodeValueValidationItem
                        && item.getDescription().contains("Invalid organization-role code 'INVALID_CODE'"));

        assertTrue(hasValidSuccess, "Should validate successfully for valid organization role code");
        assertTrue(hasInvalidError, "Should report error for invalid organization role code");
    }

    /**
     * Tests that a ValueSet without organization role extensions doesn't produce organization role validation errors.
     *
     * @throws Exception if XML parsing or validation fails
     */
    @Test
    void testValueSetWithoutOrganizationRoleExtensions() throws Exception {
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ValueSet xmlns="http://hl7.org/fhir">
                <url value="http://example.org/ValueSet/test"/>
                <name value="TestValueSet"/>
                <title value="Test ValueSet"/>
                <publisher value="Test Publisher"/>
                <description value="Test Description"/>
                <version value="#{version}"/>
                <date value="#{date}"/>
                <meta>
                    <tag>
                        <system value="http://dsf.dev/fhir/CodeSystem/read-access-tag"/>
                        <code value="ALL"/>
                    </tag>
                </meta>
                <compose>
                    <include>
                        <system value="http://example.org/CodeSystem/test"/>
                        <version value="#{version}"/>
                    </include>
                </compose>
            </ValueSet>
            """;

        Document doc = parseXmlString(xmlContent);
        List<FhirElementValidationItem> results = validator.validate(doc, testFile);

        boolean hasOrgRoleError = results.stream()
                .anyMatch(item -> item instanceof FhirValueSetOrganizationRoleMissingValidCodeValueValidationItem);
        assertFalse(hasOrgRoleError, "Should not report organization role errors when no extensions are present");
    }

    /**
     * Parses an XML string into a {@link Document} object.
     *
     * @param xmlContent the XML content as a string
     * @return the parsed Document
     * @throws Exception if parsing fails
     */
    private Document parseXmlString(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new org.xml.sax.InputSource(new StringReader(xmlContent)));
    }
}
