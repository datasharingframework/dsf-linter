package dev.dsf.linter.fhir;

import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.FhirElementLintItem;
import dev.dsf.linter.util.resource.FhirAuthorizationCache;
import dev.dsf.linter.util.resource.FhirResourceParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.File;
import java.nio.file.Path;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FhirTaskLinterInputCodingTerminologyTest
{
    private static final Path BASE_TASK = Path.of(
            "src/test/resources/fhir/examples/pingPongProcess/Task/dsf-task-start-ping.json");
    private static final File BASE_TASK_FILE = BASE_TASK.toFile();
    private static final String SECOND_INPUT_CODING_SYSTEM_XPATH =
            "/*[local-name()='Task']/*[local-name()='input'][2]/*[local-name()='type']/*[local-name()='coding']/*[local-name()='system']/@value";
    private static final String CODE_UNKNOWN_TASK_XML = """
            <Task xmlns="http://hl7.org/fhir">
              <input>
                <type>
                  <coding>
                    <system value="http://dsf.dev/fhir/CodeSystem/bpmn-message"/>
                    <code value="does-not-exist"/>
                  </coding>
                </type>
                <valueString value="payload"/>
              </input>
            </Task>
            """;

    private FhirTaskLinter linter;

    @BeforeEach
    void setUp()
    {
        linter = new FhirTaskLinter();
        FhirAuthorizationCache.setLogger(new SilentLogger());
        FhirAuthorizationCache.seedFromProjectAndClasspath(Path.of(".").toAbsolutePath().normalize().toFile());
    }

    @Test
    void shouldReportSystemUnknownForTaskInputCodingSystem() throws Exception
    {
        Document doc = FhirResourceParser.parseFhirFile(BASE_TASK);
        setSecondInputCodingSystemValue(doc, "http://example.org/fhir/CodeSystem/not-known");

        List<FhirElementLintItem> items = linter.lint(doc, BASE_TASK_FILE);

        assertTrue(containsType(items, LintingType.FHIR_TASK_INPUT_CODING_SYSTEM_UNKNOWN),
                "Expected FHIR_TASK_INPUT_CODING_SYSTEM_UNKNOWN");
    }

    @Test
    void shouldReportSystemNotInValueSetContextForTaskInputCodingSystem() throws Exception
    {
        Document doc = FhirResourceParser.parseFhirFile(BASE_TASK);
        setSecondInputCodingSystemValue(doc, FhirAuthorizationCache.CS_READ_ACCESS);

        List<FhirElementLintItem> items = linter.lint(doc, BASE_TASK_FILE);

        assertTrue(containsType(items, LintingType.FHIR_TASK_INPUT_CODING_SYSTEM_NOT_IN_VALUE_SET),
                "Expected FHIR_TASK_INPUT_CODING_SYSTEM_NOT_IN_VALUE_SET");
    }

    @Test
    void shouldReportCodeUnknownForSystemWhenBindingContextPasses() throws Exception
    {
        Document doc = parseXml();

        Class<?> sliceCardClass = Class.forName("dev.dsf.linter.fhir.FhirTaskLinter$SliceCard");
        Constructor<?> ctor = sliceCardClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object sliceCard = ctor.newInstance(0, 1,
                FhirAuthorizationCache.CS_BPMN_MESSAGE, null, null);

        Map<String, Object> cards = new HashMap<>();
        cards.put("does-not-exist", sliceCard);

        List<FhirElementLintItem> out = new ArrayList<>();
        Method method = FhirTaskLinter.class.getDeclaredMethod(
                "lintInputTypeCodingTerminology",
                Document.class, File.class, String.class, List.class, Map.class);
        method.setAccessible(true);
        method.invoke(linter, doc, new File("custom-task.xml"), "custom-ref", out, cards);

        String types = out.stream().map(i -> i.getType().name()).distinct().collect(Collectors.joining(", "));
        assertTrue(containsType(out, LintingType.FHIR_TASK_INPUT_CODING_CODE_UNKNOWN_FOR_SYSTEM),
                "Expected FHIR_TASK_INPUT_CODING_CODE_UNKNOWN_FOR_SYSTEM, got: " + types);
        assertFalse(containsType(out, LintingType.FHIR_TASK_INPUT_CODING_SYSTEM_UNKNOWN),
                "System is known in the cache");
        assertFalse(containsType(out, LintingType.FHIR_TASK_INPUT_CODING_SYSTEM_NOT_IN_VALUE_SET),
                "Binding context passes via fixed system in synthetic slice metadata");
    }

    private static boolean containsType(List<FhirElementLintItem> items, LintingType type)
    {
        return items.stream().anyMatch(i -> i.getType() == type);
    }

    private static void setSecondInputCodingSystemValue(Document doc, String value) throws Exception
    {
        Node node = (Node) XPathFactory.newInstance().newXPath()
                .compile(SECOND_INPUT_CODING_SYSTEM_XPATH)
                .evaluate(doc, XPathConstants.NODE);
        if (node == null)
            throw new IllegalStateException("XPath node not found: " + SECOND_INPUT_CODING_SYSTEM_XPATH);
        node.setNodeValue(value);
    }

    private static Document parseXml() throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(new InputSource(new StringReader(CODE_UNKNOWN_TASK_XML)));
    }

    private static final class SilentLogger implements Logger
    {
        @Override
        public void info(String message)
        {
        }

        @Override
        public void warn(String message)
        {
        }

        @Override
        public void error(String message)
        {
        }

        @Override
        public void error(String message, Throwable throwable)
        {
        }

        @Override
        public void debug(String message)
        {
        }

        @Override
        public boolean verbose()
        {
            return false;
        }

        @Override
        public boolean isVerbose()
        {
            return false;
        }
    }
}

