package dev.dsf.utils.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dsf.utils.validator.util.JsonXmlConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.DifferenceEvaluators;
import org.xmlunit.diff.ElementSelectors;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that report-autostart.json converts to the expected XML (golden file) representation.
 * Comparison is whitespace-insensitive and tolerant to child element order differences.
 */
class ReportAutostartGoldenTest
{
    // Golden XML (report-autostart.xml) provided by the user
    private static final String EXPECTED_XML = """
            <ActivityDefinition xmlns="http://hl7.org/fhir">
            	<meta>
            		<tag>
            			<system value="http://dsf.dev/fhir/CodeSystem/read-access-tag" />
            			<code value="ALL" />
            		</tag>
            	</meta>
            	<extension url="http://dsf.dev/fhir/StructureDefinition/extension-process-authorization">
            		<extension url="message-name">
            			<valueString value="dashboardReportAutostartStart" />
            		</extension>
            		<extension url="task-profile">
            			<valueCanonical value="http://netzwerk-universitaetsmedizin.de/fhir/StructureDefinition/task-report-autostart-start|#{version}" />
            		</extension>
            		<extension url="requester">
            			<valueCoding>
            				<extension url="http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-parent-organization-role">
            					<extension url="parent-organization">
            						<valueIdentifier>
            							<system value="http://dsf.dev/sid/organization-identifier" />
            							<value value="netzwerk-universitaetsmedizin.de" />
            						</valueIdentifier>
            					</extension>
            					<extension url="organization-role">
            						<valueCoding>
            							<system value="http://dsf.dev/fhir/CodeSystem/organization-role" />
            							<code value="DIC" />
            						</valueCoding>
            					</extension>
            				</extension>
            				<system value="http://dsf.dev/fhir/CodeSystem/process-authorization" />
            				<code value="LOCAL_ROLE" />
            			</valueCoding>
            		</extension>
            		<extension url="requester">
            			<valueCoding>
            				<extension url="http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-parent-organization-role-practitioner">
            					<extension url="parent-organization">
            						<valueIdentifier>
            							<system value="http://dsf.dev/sid/organization-identifier" />
            							<value value="netzwerk-universitaetsmedizin.de" />
            						</valueIdentifier>
            					</extension>
            					<extension url="organization-role">
            						<valueCoding>
            							<system value="http://dsf.dev/fhir/CodeSystem/organization-role" />
            							<code value="DIC" />
            						</valueCoding>
            					</extension>
            					<extension url="practitioner-role">
            						<valueCoding>
            							<system value="http://dsf.dev/fhir/CodeSystem/practitioner-role" />
            							<code value="DSF_ADMIN" />
            						</valueCoding>
            					</extension>
            				</extension>
            				<system value="http://dsf.dev/fhir/CodeSystem/process-authorization" />
            				<code value="LOCAL_ROLE_PRACTITIONER" />
            			</valueCoding>
            		</extension>
            		<extension url="recipient">
            			<valueCoding>
            				<extension url="http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-parent-organization-role">
            					<extension url="parent-organization">
            						<valueIdentifier>
            							<system value="http://dsf.dev/sid/organization-identifier" />
            							<value value="netzwerk-universitaetsmedizin.de" />
            						</valueIdentifier>
            					</extension>
            					<extension url="organization-role">
            						<valueCoding>
            							<system value="http://dsf.dev/fhir/CodeSystem/organization-role" />
            							<code value="DIC" />
            						</valueCoding>
            					</extension>
            				</extension>
            				<system value="http://dsf.dev/fhir/CodeSystem/process-authorization" />
            				<code value="LOCAL_ROLE" />
            			</valueCoding>
            		</extension>
            	</extension>
            	<extension url="http://dsf.dev/fhir/StructureDefinition/extension-process-authorization">
            		<extension url="message-name">
            			<valueString value="dashboardReportAutostartStop" />
            		</extension>
            		<extension url="task-profile">
            			<valueCanonical value="http://netzwerk-universitaetsmedizin.de/fhir/StructureDefinition/task-report-autostart-stop|#{version}" />
            		</extension>
            		<extension url="requester">
            			<valueCoding>
            				<extension url="http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-parent-organization-role">
            					<extension url="parent-organization">
            						<valueIdentifier>
            							<system value="http://dsf.dev/sid/organization-identifier" />
            							<value value="netzwerk-universitaetsmedizin.de" />
            						</valueIdentifier>
            					</extension>
            					<extension url="organization-role">
            						<valueCoding>
            							<system value="http://dsf.dev/fhir/CodeSystem/organization-role" />
            							<code value="DIC" />
            						</valueCoding>
            					</extension>
            				</extension>
            				<system value="http://dsf.dev/fhir/CodeSystem/process-authorization" />
            				<code value="LOCAL_ROLE" />
            			</valueCoding>
            		</extension>
            		<extension url="requester">
            			<valueCoding>
            				<extension url="http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-parent-organization-role-practitioner">
            					<extension url="parent-organization">
            						<valueIdentifier>
            							<system value="http://dsf.dev/sid/organization-identifier" />
            							<value value="netzwerk-universitaetsmedizin.de" />
            						</valueIdentifier>
            					</extension>
            					<extension url="organization-role">
            						<valueCoding>
            							<system value="http://dsf.dev/fhir/CodeSystem/organization-role" />
            							<code value="DIC" />
            						</valueCoding>
            					</extension>
            					<extension url="practitioner-role">
            						<valueCoding>
            							<system value="http://dsf.dev/fhir/CodeSystem/practitioner-role" />
            							<code value="DSF_ADMIN" />
            						</valueCoding>
            					</extension>
            				</extension>
            				<system value="http://dsf.dev/fhir/CodeSystem/process-authorization" />
            				<code value="LOCAL_ROLE_PRACTITIONER" />
            			</valueCoding>
            		</extension>
            		<extension url="recipient">
            			<valueCoding>
            				<extension url="http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-parent-organization-role">
            					<extension url="parent-organization">
            						<valueIdentifier>
            							<system value="http://dsf.dev/sid/organization-identifier" />
            							<value value="netzwerk-universitaetsmedizin.de" />
            						</valueIdentifier>
            					</extension>
            					<extension url="organization-role">
            						<valueCoding>
            							<system value="http://dsf.dev/fhir/CodeSystem/organization-role" />
            							<code value="DIC" />
            						</valueCoding>
            					</extension>
            				</extension>
            				<system value="http://dsf.dev/fhir/CodeSystem/process-authorization" />
            				<code value="LOCAL_ROLE" />
            			</valueCoding>
            		</extension>
            	</extension>
            	<url value="http://netzwerk-universitaetsmedizin.de/bpe/Process/reportAutostart" />
            	<!-- version managed by bpe -->
            	<version value="#{version}" />
            	<name value="Dashboard Report Autostart" />
            	<title value="Dashboard Report Autostart process" />
            	<subtitle value="Autostart Report Process" />
            	<!-- status managed by bpe -->
            	<status value="unknown" />
            	<experimental value="false" />
            	<!-- date managed by bpe -->
            	<date value="#{date}" />
            	<publisher value="Netzwerk-Universitaetsmedizin" />
            	<contact>
            		<name value="Netzwerk-Universitaetsmedizin" />
            		<telecom>
            			<system value="email" />
            			<value value="info@netzwerk-universitaetsmedizin.de" />
            		</telecom>
            	</contact>
            	<description value="Process to autostart extraction of current implementation progress and sending a report to the HRP in a predefined interval" />
            	<kind value="Task" />
            </ActivityDefinition>
        """;

    @Test
    @DisplayName("report-autostart.json → XML matches golden report-autostart.xml")
    void jsonToXml_matchesGoldenFile() throws Exception
    {
        // 1) Load JSON from classpath
        String resourcePath = "fhir/examples/dashBoardReport/ActivityDefinition/report-autostart.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Resource not found on classpath: " + resourcePath);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            // 2) Parse JSON and convert to XML (using the centralized converter)
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(json);
            String actualXml = JsonXmlConverter.convertJsonToXml(node);

            // DEBUG: Print converted XML to console to aid troubleshooting

            System.out.println("----- DEBUG: Converted XML from "
                    + resourcePath + " -----");
            System.out.println(actualXml);
            System.out.println("----- END DEBUG -----");

            // 3) Compare XMLs while ignoring whitespace and child order differences
            Diff diff = DiffBuilder
                    .compare(Input.fromString(EXPECTED_XML))
                    .withTest(Input.fromString(actualXml))
                    .ignoreWhitespace()
                    .ignoreComments() // comments in golden XML should not count as child nodes
                    .checkForSimilar()
                    // compare only element nodes, ignore text/comment nodes
                    .withNodeFilter(n -> n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE)
                    // allow reordering; match elements by name + all attributes
                    .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndAllAttributes))
                    .withDifferenceEvaluator(
                            DifferenceEvaluators.chain(
                                    DifferenceEvaluators.Default,
                                    DifferenceEvaluators.downgradeDifferencesToSimilar(ComparisonType.CHILD_NODELIST_SEQUENCE)
                            )
                    )
                    .build();

            assertFalse(diff.hasDifferences(), () -> "XML differs from golden file:\n" + diff.toString());
        }
    }
}
